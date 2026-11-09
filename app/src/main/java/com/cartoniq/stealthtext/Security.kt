package com.cartoniq.stealthtext

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppState {
    var started = false
    var onboarded by mutableStateOf(false)
    var locked by mutableStateOf(false)
    var crashed by mutableStateOf(false)
    var lang by mutableStateOf("en")
}

object Auth {
    fun authenticate(activity: Activity, title: String, onResult: (Boolean, String) -> Unit) {
        val exec = activity.mainExecutor
        val builder = BiometricPrompt.Builder(activity).setTitle(title)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
        } else {
            builder.setNegativeButton(tr("Cancel"), exec) { _, _ -> onResult(false, "Cancelled") }
        }
        try {
            builder.build().authenticate(CancellationSignal(), exec, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) {
                    onResult(true, "")
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) {
                    onResult(false, errString?.toString() ?: "Authentication failed")
                }
            })
        } catch (e: Exception) {
            onResult(false, tr("Set up a fingerprint or screen lock first."))
        }
    }
}

object Disguise {
    private const val DEFAULT_ALIAS = "com.cartoniq.stealthtext.AliasDefault"
    private const val CALC_ALIAS = "com.cartoniq.stealthtext.AliasCalc"

    fun apply(ctx: Context, on: Boolean) {
        val pm = ctx.packageManager
        val enable = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val disable = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(ComponentName(ctx, CALC_ALIAS), if (on) enable else disable, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(ComponentName(ctx, DEFAULT_ALIAS), if (on) disable else enable, PackageManager.DONT_KILL_APP)
    }
}
