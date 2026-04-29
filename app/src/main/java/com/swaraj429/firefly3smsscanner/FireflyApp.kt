package com.swaraj429.firefly3smsscanner

import android.app.Application
import android.util.Log
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.notification.NotificationHelper

class FireflyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("FireflyApp", "=== App Started ===")
        DebugLog.log("APP", "FireflySmsScanner started")

        // Create notification channel once at startup (safe to call repeatedly)
        NotificationHelper.createChannel(this)
        DebugLog.log("APP", "Notification channel registered")
    }
}
