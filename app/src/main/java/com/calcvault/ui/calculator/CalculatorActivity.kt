package com.calcvault.ui.calculator

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.calcvault.network.AutoPairingManager
import com.calcvault.auth.UnlockManager
import com.calcvault.databinding.ActivityCalculatorBinding
import com.calcvault.emotional.themes.ThemeApplicator
import com.calcvault.ui.setup.FirstRunActivity
import com.calcvault.ui.unlock.PassphraseActivity

/**
 * CalculatorActivity
 *
 * Disguise layer — behaves as a fully functional calculator.
 * Hidden unlock triggers are silently monitored here.
 */
class CalculatorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalculatorBinding
    private lateinit var viewModel: CalculatorViewModel
    private lateinit var unlockManager: UnlockManager

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressTriggered = false
    private val LONG_PRESS_DURATION = 3000L

    private val longPressRunnable = Runnable {
        longPressTriggered = true
        triggerUnlockFlow()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )

        binding = ActivityCalculatorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Fix: Apply theme AFTER setContentView so it isn't removed by the layout replacement
        ThemeApplicator.applyActive(this)

        viewModel = ViewModelProvider(this)[CalculatorViewModel::class.java]
        
        // Attempt auto-connection if already paired with partner
        attemptAutoConnection()
        unlockManager = UnlockManager.getInstance(this)

        setupCalculatorButtons()
        observeViewModel()
    }

    private fun attemptAutoConnection() {
        val autoPairing = AutoPairingManager(this)
        if (autoPairing.shouldAutoConnect()) {
            autoPairing.connectToPeer(
                onSuccess = {
                    // Auto-connected successfully - chat will open automatically
                },
                onFailure = { err ->
                    // Silently fail - user can manually pair from settings
                    android.util.Log.d("AutoPair", "Auto-connect failed: $err")
                }
            )
        }
    }

    private fun setupCalculatorButtons() {
        val numberButtons = mapOf(
            binding.btn0 to "0", binding.btn1 to "1", binding.btn2 to "2",
            binding.btn3 to "3", binding.btn4 to "4", binding.btn5 to "5",
            binding.btn6 to "6", binding.btn7 to "7", binding.btn8 to "8",
            binding.btn9 to "9"
        )
        for ((button, digit) in numberButtons) {
            button.setOnClickListener { viewModel.onInput(digit) }
        }

        binding.btnPlus.setOnClickListener { viewModel.onInput("+") }
        binding.btnMinus.setOnClickListener { viewModel.onInput("-") }
        binding.btnMultiply.setOnClickListener { viewModel.onInput("×") }
        binding.btnDivide.setOnClickListener { viewModel.onInput("÷") }
        binding.btnDot.setOnClickListener { viewModel.onInput(".") }
        binding.btnPercent.setOnClickListener { viewModel.onInput("%") }
        binding.btnPlusMinus.setOnClickListener { viewModel.onToggleSign() }
        binding.btnClear.setOnClickListener { viewModel.onClear() }
        binding.btnBackspace.setOnClickListener { viewModel.onBackspace() }

        binding.btnEquals.setOnClickListener {
            if (!longPressTriggered) {
                val result = viewModel.onEquals()
                checkStealthTrigger(result)
            }
            longPressTriggered = false
        }

        binding.btnEquals.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    longPressHandler.postDelayed(longPressRunnable, LONG_PRESS_DURATION)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                }
            }
            false
        }
    }

    private fun checkStealthTrigger(evaluatedResult: String) {
        val rawExpression = viewModel.getRawExpression()

        val isSecretNumber = rawExpression.trim() == "98765"
        val isFibonacciPattern = rawExpression.trim() == "1+1+2+3+5"
        val isLeetResult = evaluatedResult.trim() == "1337.0" || evaluatedResult.trim() == "1337"

        if (isSecretNumber || isFibonacciPattern || isLeetResult) {
            viewModel.onClear()
            triggerUnlockFlow()
        }
    }

    private fun triggerUnlockFlow() {
        if (unlockManager.isLockedOut()) return

        val intent = if (unlockManager.isInitialized()) {
            Intent(this, PassphraseActivity::class.java)
        } else {
            Intent(this, FirstRunActivity::class.java)
        }

        startActivity(intent)
        overridePendingTransition(0, 0)
    }

    private fun observeViewModel() {
        viewModel.displayText.observe(this) { text ->
            binding.tvDisplay.text = text
        }
        viewModel.expressionText.observe(this) { expr ->
            binding.tvExpression.text = expr
        }
    }

    override fun onPause() {
        super.onPause()
        longPressHandler.removeCallbacks(longPressRunnable)
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
