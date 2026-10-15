package com.swaraj429.firefly3smsscanner.auth

import android.content.Context
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.prefs.AppPrefs

/**
 * One-time migration: copies existing PAT from plain SharedPreferences
 * into EncryptedSharedPreferences (TokenStore).
 */
object TokenMigration {
    private const val TAG = "TokenMigration"
    private const val MIGRATION_KEY = "token_migration_v1_done"

    fun migrateIfNeeded(context: Context) {
        val regularPrefs = context.getSharedPreferences("firefly_config", Context.MODE_PRIVATE)

        if (regularPrefs.getBoolean(MIGRATION_KEY, false)) return

        try {
            val prefs = AppPrefs(context)
            val existingToken = prefs.accessToken
            if (existingToken.isNotBlank()) {
                val tokenStore = TokenStore(context)
                tokenStore.patAccessToken = existingToken
                tokenStore.authMethod = AuthMethod.PERSONAL_ACCESS_TOKEN
                DebugLog.log(TAG, "Migrated existing PAT to secure TokenStore")
            }
            regularPrefs.edit().putBoolean(MIGRATION_KEY, true).apply()
            DebugLog.log(TAG, "Token migration completed successfully")
        } catch (e: Exception) {
            DebugLog.log(TAG, "Token migration failed: ${e.message}")
        }
    }
}
