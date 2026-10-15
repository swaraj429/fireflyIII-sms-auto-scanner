package com.swaraj429.firefly3smsscanner.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.swaraj429.firefly3smsscanner.MainActivity
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.prefs.AppPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the OAuth redirect callback from Firefly III.
 * Intercepts: firefly3sms://oauth/callback?code=...&state=...
 */
class OAuthCallbackActivity : ComponentActivity() {

    companion object {
        private const val TAG = "OAuthCallback"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = intent?.data
        if (uri == null) {
            DebugLog.log(TAG, "No URI in intent — finishing callback activity")
            finish()
            return
        }

        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        val error = uri.getQueryParameter("error")
        val errorDescription = uri.getQueryParameter("error_description")

        DebugLog.log(TAG, "Received callback: code=${code?.take(8)}..., state=${state?.take(8)}..., error=$error")

        if (!error.isNullOrBlank()) {
            val msg = errorDescription ?: error
            Toast.makeText(this, "Authorization failed: $msg", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (code.isNullOrBlank() || state.isNullOrBlank()) {
            Toast.makeText(this, "Invalid OAuth callback: missing code or state", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val prefs = AppPrefs(this)
        val oauthManager = OAuthManager(this)

        val effectiveRedirectUri = if (uri.scheme == "https") {
            "https://${uri.host}${uri.path}"
        } else {
            OAuthManager.REDIRECT_URI_CUSTOM
        }

        CoroutineScope(Dispatchers.IO).launch {
            val result = oauthManager.handleAuthorizationResponse(
                code = code,
                state = state,
                baseUrl = prefs.baseUrl,
                redirectUri = effectiveRedirectUri
            )

            runOnUiThread {
                result.fold(
                    onSuccess = {
                        prefs.authMethod = "OAUTH"
                        Toast.makeText(this@OAuthCallbackActivity, "✅ Connected to Firefly III via OAuth!", Toast.LENGTH_SHORT).show()
                        DebugLog.log(TAG, "OAuth flow complete, navigating to MainActivity")

                        val intent = Intent(this@OAuthCallbackActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            putExtra("oauth_success", true)
                        }
                        startActivity(intent)
                        finish()
                    },
                    onFailure = { e ->
                        Toast.makeText(this@OAuthCallbackActivity, "❌ OAuth error: ${e.message}", Toast.LENGTH_LONG).show()
                        DebugLog.log(TAG, "OAuth flow failure: ${e.message}")
                        finish()
                    }
                )
            }
        }
    }
}
