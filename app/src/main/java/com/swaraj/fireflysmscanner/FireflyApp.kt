package com.swaraj.fireflysmscanner

import android.app.Application
import android.util.Log
import com.swaraj.fireflysmscanner.debug.DebugLog

class FireflyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.d("FireflyApp", "=== App Started ===")
        DebugLog.log("APP", "FireflySmsScanner started")
    }
}
