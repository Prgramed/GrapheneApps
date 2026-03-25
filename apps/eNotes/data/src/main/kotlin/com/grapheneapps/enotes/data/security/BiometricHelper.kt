package com.grapheneapps.enotes.data.security

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BiometricHelper @Inject constructor() {

    private var lastAuthTime: Long = 0

    fun isAuthValid(timeoutMinutes: Int): Boolean {
        if (timeoutMinutes <= 0) return false // 0 = always require
        val elapsed = System.currentTimeMillis() - lastAuthTime
        return elapsed < timeoutMinutes * 60_000L
    }

    fun invalidateAuth() {
        lastAuthTime = 0
    }

    fun onAuthSuccess() {
        lastAuthTime = System.currentTimeMillis()
    }

    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Note",
        subtitle: String = "Authenticate to view this note",
        onSuccess: () -> Unit,
        onFailed: () -> Unit,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                lastAuthTime = System.currentTimeMillis()
                onSuccess()
            }

            override fun onAuthenticationFailed() {
                // Called on each failed attempt, not final
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailed()
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL,
            )
            .build()

        prompt.authenticate(info)
    }
}
