package com.calcvault.auth

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

class BiometricHelper(private val activity: FragmentActivity) {

    private val biometricAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.BIOMETRIC_WEAK

    /**
     * Checks if actual biometric hardware is available AND enrolled (Fingerprint/Face).
     * This strictly excludes Device PIN/Pattern/Password as "Biometric".
     */
    fun isAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        val result = biometricManager.canAuthenticate(biometricAuthenticators)
        android.util.Log.d("BiometricHelper", "canAuthenticate result: $result")
        return result == BiometricManager.BIOMETRIC_SUCCESS || result == BiometricManager.BIOMETRIC_STATUS_UNKNOWN
    }

    /**
     * Checks if the device has ANY form of secure lock (Biometric OR PIN/Pattern/Pass).
     */
    fun isAnySecureLockAvailable(): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(biometricAuthenticators or BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        onSuccess: () -> Unit,
        onFailed: () -> Unit,
        onError: (Int, String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val biometricPrompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    // If user cancels or biometrics aren't available, we don't treat it as "Failed"
                    // (which might trigger lockouts), but as an error to handle.
                    onError(errorCode, errString.toString())
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    onFailed()
                }
            }
        )

        // We strictly use biometricAuthenticators (Strong or Weak) to avoid falling back
        // to the device PIN/Pattern if the user specifically requested Face/Fingerprint.
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("CalcVault Biometric")
            .setSubtitle("Use Face or Fingerprint to unlock")
            .setAllowedAuthenticators(biometricAuthenticators)
            .setNegativeButtonText("Use Passphrase")
            .setConfirmationRequired(false)
            .build()

        biometricPrompt.authenticate(promptInfo)
    }
}
