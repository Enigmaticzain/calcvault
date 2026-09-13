package com.calcvault.ui.calculator

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * CalculatorViewModel
 *
 * Handles all calculator logic. Fully functional — indistinguishable
 * from a real calculator app.
 *
 * Supports: +, -, ×, ÷, %, +/-, decimal, chained operations, error handling
 */
class CalculatorViewModel : ViewModel() {

    private val _displayText = MutableLiveData("0")
    val displayText: LiveData<String> = _displayText

    private val _expressionText = MutableLiveData("")
    val expressionText: LiveData<String> = _expressionText

    // Internal state
    private var currentInput = StringBuilder()
    private var rawExpression = StringBuilder() // used for stealth trigger check
    private var firstOperand: BigDecimal? = null
    private var pendingOperator: String? = null
    private var justEvaluated = false
    private var hasError = false

    fun onInput(value: String) {
        if (hasError) onClear()

        if (justEvaluated && value !in listOf("+", "-", "×", "÷", "%")) {
            // Start fresh number after evaluation
            currentInput.clear()
            rawExpression.clear()
            justEvaluated = false
        }

        when {
            value in listOf("+", "-", "×", "÷") -> handleOperator(value)
            value == "." -> handleDecimal()
            value == "%" -> handlePercent()
            else -> handleDigit(value)
        }
    }

    private fun handleDigit(digit: String) {
        if (currentInput.toString() == "0") currentInput.clear()
        currentInput.append(digit)
        rawExpression.append(digit)
        updateDisplay()
    }

    private fun handleDecimal() {
        if (!currentInput.contains('.')) {
            if (currentInput.isEmpty()) currentInput.append("0")
            currentInput.append(".")
            rawExpression.append(".")
        }
        updateDisplay()
    }

    private fun handleOperator(op: String) {
        if (currentInput.isNotEmpty()) {
            if (firstOperand != null && pendingOperator != null) {
                // Chain calculation
                val result = calculate(firstOperand!!, toBigDecimal(currentInput.toString()), pendingOperator!!)
                if (hasError) return
                firstOperand = result
                _expressionText.value = "${formatResult(result)} $op"
            } else {
                firstOperand = toBigDecimal(currentInput.toString())
                _expressionText.value = "$currentInput $op"
            }
            rawExpression.append(op)
            currentInput.clear()
        } else if (firstOperand != null) {
            // Change operator
            pendingOperator = op
            _expressionText.value = "${formatResult(firstOperand!!)} $op"
            return
        }
        pendingOperator = op
        justEvaluated = false
    }

    private fun handlePercent() {
        if (currentInput.isNotEmpty()) {
            val value = toBigDecimal(currentInput.toString())
            val percent = value.divide(BigDecimal("100"), MathContext.DECIMAL64)
            currentInput = StringBuilder(formatResult(percent))
            updateDisplay()
        }
    }

    fun onEquals(): String {
        if (hasError) return "Error"
        if (firstOperand == null || pendingOperator == null || currentInput.isEmpty()) {
            return _displayText.value ?: "0"
        }

        val secondOperand = toBigDecimal(currentInput.toString())
        val firstOpFormatted = formatResult(firstOperand!!)
        _expressionText.value = "$firstOpFormatted $pendingOperator $currentInput ="

        val result = calculate(firstOperand!!, secondOperand, pendingOperator!!)
        if (hasError) return "Error"

        val resultStr = formatResult(result)

        _displayText.value = resultStr
        currentInput = StringBuilder(resultStr)

        firstOperand = null
        pendingOperator = null
        justEvaluated = true

        return resultStr
    }

    fun onClear() {
        currentInput.clear()
        rawExpression.clear()
        firstOperand = null
        pendingOperator = null
        justEvaluated = false
        hasError = false
        _displayText.value = "0"
        _expressionText.value = ""
    }

    fun onBackspace() {
        if (hasError || justEvaluated) { onClear(); return }
        if (currentInput.isNotEmpty()) {
            currentInput.deleteCharAt(currentInput.length - 1)
            if (rawExpression.isNotEmpty()) rawExpression.deleteCharAt(rawExpression.length - 1)
        }
        _displayText.value = if (currentInput.isEmpty()) "0" else currentInput.toString()
    }

    fun onToggleSign() {
        if (hasError) return
        if (currentInput.isNotEmpty() && currentInput.toString() != "0") {
            if (currentInput.startsWith("-")) {
                currentInput.deleteCharAt(0)
            } else {
                currentInput.insert(0, "-")
            }
            updateDisplay()
        }
    }

    /** Returns raw expression string for stealth trigger detection */
    fun getRawExpression(): String = rawExpression.toString()

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private fun calculate(a: BigDecimal, b: BigDecimal, op: String): BigDecimal {
        return try {
            when (op) {
                "+" -> a.add(b)
                "-" -> a.subtract(b)
                "×" -> a.multiply(b)
                "÷" -> if (b.compareTo(BigDecimal.ZERO) == 0) {
                    throw ArithmeticException("Division by zero")
                } else {
                    a.divide(b, 10, RoundingMode.HALF_UP)
                }
                else -> b
            }
        } catch (e: Exception) {
            hasError = true
            _displayText.value = "Error"
            BigDecimal.ZERO
        }
    }

    private fun toBigDecimal(input: String): BigDecimal {
        return try { BigDecimal(input) } catch (e: Exception) { BigDecimal.ZERO }
    }

    private fun formatResult(value: BigDecimal): String {
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() <= 0) {
            stripped.toBigInteger().toString()
        } else {
            // Cap at 10 decimal places for display
            value.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        }
    }

    private fun updateDisplay() {
        _displayText.value = if (currentInput.isEmpty()) "0" else currentInput.toString()
    }
}
