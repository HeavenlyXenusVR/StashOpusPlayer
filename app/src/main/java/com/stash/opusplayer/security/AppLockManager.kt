package com.stash.opusplayer.security

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import com.stash.opusplayer.utils.PrefsKeys

/**
 * Backs `Settings -> Privacy -> App Lock`, ported from iOS's
 * `SettingsView+AppLockSection.swift`. Unlike the iOS original (which can
 * gate on Face ID/Touch ID alone), Android's `BiometricPrompt` is asked for
 * [BiometricManager.Authenticators.BIOMETRIC_STRONG] only -- no device
 * credential (PIN/pattern) fallback -- since a PIN fallback would just be
 * the device's own lock screen wrapped a second time, adding nothing.
 */
object AppLockManager {

    fun isEnabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(PrefsKeys.APP_LOCK_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        PreferenceManager.getDefaultSharedPreferences(context)
            .edit()
            .putBoolean(PrefsKeys.APP_LOCK_ENABLED, enabled)
            .apply()
    }

    /** Whether this device can actually satisfy a strong-biometric prompt right now. */
    fun isBiometricAvailable(context: Context): Boolean {
        val manager = BiometricManager.from(context)
        return manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) ==
            BiometricManager.BIOMETRIC_SUCCESS
    }

    fun showPrompt(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: () -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                onFailure()
            }

            override fun onAuthenticationFailed() {
                // A single failed attempt (wrong finger, etc.) -- the prompt
                // stays open and lets the user retry, so this is deliberately
                // not forwarded to [onFailure].
            }
        }

        val prompt = BiometricPrompt(activity, executor, callback)
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock StashOpusPlayer")
            .setSubtitle("Authenticate to continue")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText("Cancel")
            .build()

        prompt.authenticate(info)
    }
}
