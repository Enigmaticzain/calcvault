package com.calcvault.ui.setup

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.calcvault.auth.UnlockManager
import com.calcvault.crypto.E2EKeyManager
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.ui.calculator.CalculatorActivity
import kotlinx.coroutines.*
import java.security.SecureRandom
import java.util.Base64

class FirstRunActivity : AppCompatActivity() {

    private lateinit var unlockManager: UnlockManager
    private var currentStep = 1
    private var realPass = ""
    private var decoyPass = ""
    private var recoveryKey = ""
    private var userIdentity = ""

    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvStatus: TextView
    private lateinit var etInput1: EditText
    private lateinit var etInput2: EditText
    private lateinit var tvRecovery: TextView
    private lateinit var btnNext: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        unlockManager = UnlockManager.getInstance(this)

        if (unlockManager.isInitialized()) {
            startActivity(Intent(this, CalculatorActivity::class.java))
            finish()
            return
        }

        buildLayout()
        // Fix: Apply theme AFTER buildLayout (which calls setContentView)
        ThemeApplicator.applyActive(this)
        showStep(1)
    }

    private fun buildLayout() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(60, 100, 60, 60)
            setBackgroundColor(if (ThemeApplicator.activeTheme == ThemeApplicator.ThemeChoice.NONE) 0xFF000000.toInt() else android.graphics.Color.TRANSPARENT)
        }

        tvTitle = TextView(this).apply {
            textSize = 24f; setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 0, 0, 8); root.addView(this)
        }
        tvSubtitle = TextView(this).apply {
            textSize = 14f; setTextColor(0xFF888888.toInt())
            setPadding(0, 0, 0, 32); root.addView(this)
        }
        etInput1 = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF555555.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = null; setPadding(0, 16, 0, 16); textSize = 18f
            root.addView(this)
        }
        etInput2 = EditText(this).apply {
            setTextColor(0xFFFFFFFF.toInt()); setHintTextColor(0xFF555555.toInt())
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = null; setPadding(0, 16, 0, 16); textSize = 18f
            root.addView(this)
        }
        tvRecovery = TextView(this).apply {
            textSize = 13f; setTextColor(0xFFFFAA00.toInt())
            setPadding(0, 16, 0, 16); visibility = android.view.View.GONE
            root.addView(this)
        }
        tvStatus = TextView(this).apply {
            textSize = 13f; setTextColor(0xFFFF5555.toInt())
            setPadding(0, 8, 0, 8); root.addView(this)
        }
        btnNext = Button(this).apply {
            text = "Continue"
            setBackgroundColor(0xFF222222.toInt()); setTextColor(0xFFFFFFFF.toInt())
            setPadding(0, 32, 0, 32); setOnClickListener { handleNext() }
            root.addView(this)
        }

        setContentView(root)
    }

    private fun showStep(step: Int) {
        currentStep = step
        tvStatus.text = ""
        etInput1.text.clear(); etInput2.text.clear()
        tvRecovery.visibility = android.view.View.GONE
        etInput1.visibility = android.view.View.VISIBLE
        etInput2.visibility = android.view.View.VISIBLE

        when (step) {
            1 -> {
                tvTitle.text = "Create Passphrase"
                tvSubtitle.text = "Choose a strong passphrase (8+ characters)"
                etInput1.hint = "Passphrase"
                etInput2.hint = "Confirm passphrase"
                btnNext.text = "Continue"
            }
            2 -> {
                tvTitle.text = "Decoy Passphrase (Optional)"
                tvSubtitle.text = "If someone forces you to unlock, enter this instead"
                etInput1.hint = "Decoy passphrase (or leave blank)"
                etInput2.visibility = android.view.View.GONE
                btnNext.text = "Continue"
            }
            3 -> {
                recoveryKey = generateRecoveryKey()
                tvTitle.text = "Recovery Key"
                tvSubtitle.text = "Write this down NOW. You cannot recover it later."
                etInput1.visibility = android.view.View.GONE
                etInput2.visibility = android.view.View.GONE
                tvRecovery.visibility = android.view.View.VISIBLE
                tvRecovery.text = recoveryKey
                btnNext.text = "I wrote it down"
            }
            4 -> {
                tvTitle.text = "Choose Identity"
                tvSubtitle.text = "Are you Zain or Sanu?\nBoth people must choose different roles."
                etInput1.visibility = android.view.View.GONE
                etInput2.visibility = android.view.View.GONE
                btnNext.text = "Choose Identity"
            }
            5 -> {
                tvTitle.text = "Setting Up Vault..."
                tvSubtitle.text = "Generating private keys and initializing..."
                etInput1.visibility = android.view.View.GONE
                etInput2.visibility = android.view.View.GONE
                btnNext.isEnabled = false
                initializeSystem()
            }
        }
    }

    private fun handleNext() {
        when (currentStep) {
            1 -> {
                val pass = etInput1.text.toString()
                val confirm = etInput2.text.toString()
                if (pass.length < 8) { tvStatus.text = "Passphrase must be 8+ characters"; return }
                if (pass != confirm) { tvStatus.text = "Passphrases don't match"; return }
                realPass = pass; showStep(2)
            }
            2 -> {
                decoyPass = etInput1.text.toString()
                showStep(3)
            }
            3 -> showStep(4)
            4 -> {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Choose Your Role")
                    .setItems(arrayOf("I am Zain", "I am Sanu")) { _, which ->
                        userIdentity = if (which == 0) "ZAIN" else "SANU"
                        showStep(5)
                    }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    private fun initializeSystem() {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    E2EKeyManager.getInstance().generateIdentityKeypair()
                    unlockManager.setupPassphrase(realPass)
                    if (decoyPass.isNotBlank()) unlockManager.setupDecoyPassphrase(decoyPass)
                    unlockManager.setupRecoveryKey(recoveryKey)
                    unlockManager.setUserIdentity(userIdentity)
                }

                realPass = ""; decoyPass = ""; recoveryKey = ""

                runOnUiThread {
                    tvTitle.text = "✅ Setup Complete!"
                    tvSubtitle.text = "Your vault is ready.\nYour Device ID: ${E2EKeyManager.getInstance().getDeviceId().take(12)}..."
                    btnNext.isEnabled = true
                    btnNext.text = "Go to Calculator"
                    btnNext.setOnClickListener {
                        startActivity(
                            Intent(this@FirstRunActivity, CalculatorActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            }
                        )
                        finish()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    tvSubtitle.text = "Error: ${e.message}"
                    btnNext.isEnabled = true
                    btnNext.text = "Retry"
                    btnNext.setOnClickListener { showStep(5) }
                }
            }
        }
    }

    private fun generateRecoveryKey(): String {
        val bytes = ByteArray(18).also { SecureRandom().nextBytes(it) }
        return Base64.getEncoder().encodeToString(bytes)
            .replace("+", "A").replace("/", "B").replace("=", "")
            .chunked(6).take(4).joinToString("-")
    }

    override fun onResume() {
        super.onResume()
        ThemeApplicator.applyActive(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        ThemeApplicator.detach(this)
    }
}
