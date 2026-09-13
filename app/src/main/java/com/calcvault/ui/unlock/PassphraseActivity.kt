package com.calcvault.ui.unlock

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.inputmethod.EditorInfo
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calcvault.auth.BiometricHelper
import com.calcvault.auth.UnlockManager
import com.calcvault.auth.lockout.PersistentLockoutManager
import com.calcvault.crypto.E2EKeyManager
import com.calcvault.databinding.ActivityPassphraseBinding
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.memory.MemoryGuard
import com.calcvault.security.hooks.AntiHookMonitor
import com.calcvault.storage.USBStorageEngine
import com.calcvault.storage.provider.StorageManager
import com.calcvault.ui.ambient.AmbientAnimationView
import com.calcvault.ui.calculator.CalculatorActivity
import com.calcvault.ui.main.DecoyActivity
import com.calcvault.ui.main.MainVaultActivity
import com.calcvault.utils.SessionManager
import com.calcvault.sync.PermanentPairManager
import kotlinx.coroutines.*
import java.io.File
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

import android.app.ProgressDialog
class PassphraseActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPassphraseBinding
    private lateinit var unlockManager: UnlockManager
    private lateinit var bioHelper: BiometricHelper
    private lateinit var lockoutMgr: PersistentLockoutManager
    private var lockoutTimer: CountDownTimer? = null
    private var fp: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MemoryGuard.secureWindow(this)
        if (AntiHookMonitor.scan().critical) { finish(); return }

        binding = ActivityPassphraseBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fix: Apply theme AFTER setContentView
        ThemeApplicator.applyActive(this)

        unlockManager = UnlockManager.getInstance(this)
        bioHelper = BiometricHelper(this)

        if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) {
            AmbientAnimationView.applyPersistedScene(binding.ambientView, unlockManager.getAmbientScene())
        } else {
            binding.ambientView.visibility = android.view.View.GONE
        }

        val usbRoot = findUsbRoot()
        lockoutMgr = PersistentLockoutManager(
            usbRoot = usbRoot ?: File(filesDir, "lockout"),
            wipeCallback = { triggerWipe() }
        )

        fp = lockoutMgr.deviceFingerprint()
        when (val s = lockoutMgr.checkState(fp)) {
            is PersistentLockoutManager.LockState.WipeTriggered -> { showDestroyed(); return }
            is PersistentLockoutManager.LockState.Locked -> { showCountdown(s.remainingMs); return }
            else -> {}
        }

        if (unlockManager.isVaultDestroyed()) { showDestroyed(); return }
        if (unlockManager.isLockedOut()) { showLockout(); return }

        setupUI()
    }

    private fun setupUI() {
        binding.etPassphrase.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { attemptUnlock(); true } else false
        }
        binding.btnUnlock.setOnClickListener { attemptUnlock() }
        binding.btnBiometric.apply {
            isEnabled = bioHelper.isAvailable()
            setOnClickListener { launchBio { proceedToVault() } }
        }
        binding.btnCancel.setOnClickListener {
            startActivity(Intent(this, CalculatorActivity::class.java))
            overridePendingTransition(0, 0); finish()
        }
    }

    private fun attemptUnlock() {
        val input = binding.etPassphrase.text.toString().trim()
        if (input.isBlank()) return
        clearField()

        // Disable UI to prevent multiple taps while verification is running
        binding.btnUnlock.isEnabled = false
        binding.etPassphrase.isEnabled = false
        binding.btnBiometric.isEnabled = false
        
        // Show lightweight loading indicator (avoids ProgressDialog overhead)
        binding.tvStatus.text = "Verifying passphrase..."

        // Run verification on background thread to prevent ANR
        Thread {
            val result = unlockManager.attemptUnlock(input)
            runOnUiThread {
                // Re-enable UI
                binding.btnUnlock.isEnabled = true
                binding.etPassphrase.isEnabled = true
                binding.btnBiometric.isEnabled = bioHelper.isAvailable()
                handleUnlockResult(result, input)
            }
        }.start()
    }

    private fun handleUnlockResult(result: UnlockManager.UnlockResult, passphrase: String) {
        when (result) {
            is UnlockManager.UnlockResult.RealUnlock -> {
                lockoutMgr.recordSuccess(fp)
                initStorageAndProceed(passphrase)
            }
            is UnlockManager.UnlockResult.DecoyUnlock -> {
                lockoutMgr.recordSuccess(fp)
                launchDecoy()
            }
            is UnlockManager.UnlockResult.Failed -> {
                if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) {
                    binding.ambientView.triggerErrorEffect()
                }

                when (val ls = lockoutMgr.recordFailure(fp)) {
                    is PersistentLockoutManager.LockState.Locked -> showCountdown(ls.remainingMs)
                    is PersistentLockoutManager.LockState.WipeTriggered -> showDestroyed()
                    else -> {
                        if (result.lockedOut) {
                            showLockout()
                        } else {
                            binding.tvStatus.text = "Incorrect. ${result.attemptsRemaining} attempt(s) remaining."
                        }
                    }
                }
            }
            is UnlockManager.UnlockResult.VaultDestroyed -> showDestroyed()
            is UnlockManager.UnlockResult.LockedOut -> showLockout()
        }
    }

    private fun initStorageAndProceed(passphrase: String) {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val usbRoot = findUsbRoot()
                    if (usbRoot != null) {
                        val engine = USBStorageEngine.getInstance(this@PassphraseActivity)
                        engine.onUsbAttached(usbRoot)
                        val salt = unlockManager.getOrCreateSalt()
                        if (!engine.unlock(passphrase.toCharArray(), salt)) {
                            throw Exception("USB unlock failed")
                        }
                    }

                    val salt = unlockManager.getOrCreateSalt()
                    val spec = PBEKeySpec(passphrase.toCharArray(), salt, unlockManager.getPbkdf2Iterations(), 512)
                    val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                        .generateSecret(spec).encoded
                    spec.clearPassword()

                    val masterKey = raw.copyOf(32)
                    val macKey = raw.copyOfRange(32, 64)
                    raw.fill(0)

                    StorageManager.init(
                        context = this@PassphraseActivity,
                        masterKey = masterKey,
                        macKey = macKey,
                        usbRoot = usbRoot,
                        preferUSB = true
                    )

                    StorageManager.onModeChanged = { mode ->
                        SessionManager.usbMountPath = if (mode.name == "USB") "USB_ACTIVE" else ""
                    }

                    try { E2EKeyManager.getInstance().generateIdentityKeypair() } catch (e: Exception) {}

                    SessionManager.isVaultOpen = true
                    // Auto-restore permanent pair identity
                    PermanentPairManager.init(this@PassphraseActivity)
                    if (PermanentPairManager.isPaired) {
                        PermanentPairManager.restoreSession()
                    } else {
                        if (SessionManager.localUserId.isBlank()) SessionManager.localUserId = "USER_A"
                        if (SessionManager.partnerUserId.isBlank()) SessionManager.partnerUserId = "USER_B"
                    }
                    masterKey.fill(0); macKey.fill(0)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { binding.tvStatus.text = "Init failed: ${e.message}" }
                    return@withContext
                }
            }
            proceedToVault()
        }
    }

    private fun launchBio(onSuccess: () -> Unit) {
        bioHelper.authenticate(
            onSuccess = { onSuccess() },
            onFailed = { binding.tvStatus.text = "Biometric not recognized" },
            onError = { _, msg -> binding.tvStatus.text = msg }
        )
    }

    private fun proceedToVault() {
        startActivity(
            Intent(this, MainVaultActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        overridePendingTransition(0, 0); finish()
    }

    private fun launchDecoy() {
        startActivity(
            Intent(this, DecoyActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
        overridePendingTransition(0, 0); finish()
    }

    private fun showLockout() {
        binding.btnUnlock.isEnabled = false
        binding.btnBiometric.isEnabled = false
        binding.etPassphrase.isEnabled = false
        val remaining = unlockManager.getRemainingLockoutMs()
        lockoutTimer?.cancel()
        lockoutTimer = object : CountDownTimer(remaining, 1_000) {
            override fun onTick(left: Long) {
                val h = TimeUnit.MILLISECONDS.toHours(left)
                val m = TimeUnit.MILLISECONDS.toMinutes(left) % 60
                val s = TimeUnit.MILLISECONDS.toSeconds(left) % 60
                binding.tvStatus.text = "Locked: %02d:%02d:%02d".format(h, m, s)
            }
            override fun onFinish() {
                binding.btnUnlock.isEnabled = true
                binding.btnBiometric.isEnabled = bioHelper.isAvailable()
                binding.etPassphrase.isEnabled = true
                binding.tvStatus.text = ""
            }
        }.start()
    }

    private fun showCountdown(remainingMs: Long) {
        binding.btnUnlock.isEnabled = false
        binding.btnBiometric.isEnabled = false
        binding.etPassphrase.isEnabled = false
        lockoutTimer?.cancel()
        lockoutTimer = object : CountDownTimer(remainingMs, 1_000) {
            override fun onTick(left: Long) {
                val h = TimeUnit.MILLISECONDS.toHours(left)
                val m = TimeUnit.MILLISECONDS.toMinutes(left) % 60
                val s = TimeUnit.MILLISECONDS.toSeconds(left) % 60
                binding.tvStatus.text = "Locked: %02d:%02d:%02d".format(h, m, s)
            }
            override fun onFinish() {
                binding.btnUnlock.isEnabled = true
                binding.btnBiometric.isEnabled = bioHelper.isAvailable()
                binding.etPassphrase.isEnabled = true
                binding.tvStatus.text = ""
            }
        }.start()
    }

    private fun triggerWipe() {
        lifecycleScope.launch {
            withContext(Dispatchers.IO) { StorageManager.destroy() }
            SessionManager.clear()
            showDestroyed()
        }
    }

    private fun showDestroyed() {
        binding.tvStatus.text = "System unavailable."
        binding.btnUnlock.isEnabled = false
        binding.btnBiometric.isEnabled = false
        binding.etPassphrase.isEnabled = false
    }

    private fun clearField() {
        val t = binding.etPassphrase.text
        t?.replace(0, t.length, "0".repeat(t.length))
        binding.etPassphrase.text?.clear()
    }

    private fun findUsbRoot(): File? {
        val paths = listOf(
            "/storage/usb0",
            "/storage/usb1",
            "/mnt/usb_storage",
            "/mnt/media_rw/usb0",
            "/storage/UsbDriveA",
            "/storage/UsbDriveB"
        )
        return paths.map(::File).firstOrNull { it.exists() && it.isDirectory }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        MemoryGuard.onFocusChanged(this, hasFocus)
    }

    override fun onResume() {
        super.onResume()
        ThemeApplicator.applyActive(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        lockoutTimer?.cancel()
        ThemeApplicator.detach(this)
    }
}
