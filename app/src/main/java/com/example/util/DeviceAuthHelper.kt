package com.example.util

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object DeviceAuthHelper {
    private const val ALLOWED_AUTHENTICATORS =
        Authenticators.BIOMETRIC_STRONG or
            Authenticators.BIOMETRIC_WEAK or
            Authenticators.DEVICE_CREDENTIAL

    fun isDeviceAuthAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(ALLOWED_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun getAvailabilityDescription(context: Context): String {
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(ALLOWED_AUTHENTICATORS)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                "Ready (Face, Fingerprint, or Screen Lock PIN/Pattern supported)"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "Please set a Screen Lock (PIN, Pattern, Password, Fingerprint, or Face) in Android Device Settings first."
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Device screen lock / biometric hardware unavailable."
            else ->
                "Device authentication is currently unavailable."
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isDeviceAuthAvailable(activity)) {
            onError(getAvailabilityDescription(activity))
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onError(errString.toString().ifBlank { "Device authentication cancelled or failed." })
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                }
            }
        )
        // NOTE: Never call setNegativeButtonText when DEVICE_CREDENTIAL is included in allowed authenticators.
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(ALLOWED_AUTHENTICATORS)
            .build()
        prompt.authenticate(info)
    }
}
