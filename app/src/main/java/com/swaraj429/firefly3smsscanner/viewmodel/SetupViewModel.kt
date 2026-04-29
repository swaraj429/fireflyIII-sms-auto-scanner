package com.swaraj429.firefly3smsscanner.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.network.RetrofitClient
import com.swaraj429.firefly3smsscanner.prefs.AppPrefs
import kotlinx.coroutines.launch

class SetupViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SetupVM"
    private val prefs = AppPrefs(application)

    var baseUrl by mutableStateOf(prefs.baseUrl)
    var accessToken by mutableStateOf(prefs.accessToken)
    var accountId by mutableStateOf(prefs.accountId)
    var connectionStatus by mutableStateOf("")
    var isTesting by mutableStateOf(false)

    fun saveConfig() {
        prefs.baseUrl = baseUrl
        prefs.accessToken = accessToken
        prefs.accountId = accountId
        DebugLog.log(TAG, "Config saved: url=$baseUrl, accountId=$accountId")
    }

    fun testConnection() {
        if (baseUrl.isBlank() || accessToken.isBlank()) {
            connectionStatus = "❌ Please fill in Base URL and Access Token"
            return
        }

        isTesting = true
        connectionStatus = "⏳ Testing connection..."
        DebugLog.log(TAG, "Testing connection to: $baseUrl")

        viewModelScope.launch {
            try {
                val api = RetrofitClient.create(baseUrl, accessToken)
                val response = api.getAbout()

                if (response.isSuccessful) {
                    val about = response.body()
                    connectionStatus = "✅ Connected! Firefly v${about?.data?.version ?: "?"}"
                    DebugLog.log(TAG, "Connection SUCCESS: ${about?.data?.version}")
                    saveConfig()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown error"
                    connectionStatus = "❌ HTTP ${response.code()}: $errorBody"
                    DebugLog.log(TAG, "Connection FAILED: ${response.code()} - $errorBody")
                }
            } catch (e: Exception) {
                connectionStatus = "❌ Error: ${e.message}"
                DebugLog.log(TAG, "Connection ERROR: ${e.message}")
                Log.e(TAG, "Connection test failed", e)
            } finally {
                isTesting = false
            }
        }
    }
}
