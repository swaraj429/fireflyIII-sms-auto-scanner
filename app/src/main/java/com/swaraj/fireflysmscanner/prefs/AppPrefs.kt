package com.swaraj.fireflysmscanner.prefs

import android.content.Context
import android.content.SharedPreferences
import com.swaraj.fireflysmscanner.debug.DebugLog

/**
 * Simple SharedPreferences wrapper for app config.
 */
class AppPrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("firefly_config", Context.MODE_PRIVATE)

    var baseUrl: String
        get() = prefs.getString("base_url", "") ?: ""
        set(value) {
            prefs.edit().putString("base_url", value.trimEnd('/')).apply()
            DebugLog.log("PREFS", "Base URL saved: $value")
        }

    var accessToken: String
        get() = prefs.getString("access_token", "") ?: ""
        set(value) {
            prefs.edit().putString("access_token", value.trim()).apply()
            DebugLog.log("PREFS", "Access token saved: ${value.take(8)}...")
        }

    var accountId: String
        get() = prefs.getString("account_id", "1") ?: "1"
        set(value) {
            prefs.edit().putString("account_id", value.trim()).apply()
            DebugLog.log("PREFS", "Account ID saved: $value")
        }

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && accessToken.isNotBlank()

    fun clear() {
        prefs.edit().clear().apply()
        DebugLog.log("PREFS", "All preferences cleared")
    }
}
