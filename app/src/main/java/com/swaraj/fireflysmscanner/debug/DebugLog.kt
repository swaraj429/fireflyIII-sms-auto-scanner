package com.swaraj.fireflysmscanner.debug

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList

/**
 * In-memory debug log buffer. Thread-safe — all Compose state
 * mutations are posted to the main thread so interceptors running
 * on OkHttp threads don't crash the snapshot system.
 * Keeps last 200 entries.
 */
object DebugLog {
    private const val TAG = "DebugLog"
    private const val MAX_ENTRIES = 200

    data class Entry(
        val timestamp: String,
        val tag: String,
        val message: String
    )

    // Thread-safe backing list
    private val _entries = CopyOnWriteArrayList<Entry>()

    // Observable list for Compose UI — only mutated on main thread
    val entries = mutableStateListOf<Entry>()

    // Last HTTP request/response for debug panel
    var lastRequest: String by mutableStateOf("(none)")
        private set
    var lastResponse: String by mutableStateOf("(none)")
        private set

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun threadSafeDateFormat(): SimpleDateFormat =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        val timestamp = threadSafeDateFormat().format(Date())
        val entry = Entry(timestamp, tag, message)

        Log.d("FF_$tag", message) // Always log to Logcat too

        _entries.add(0, entry) // newest first
        while (_entries.size > MAX_ENTRIES) {
            _entries.removeAt(_entries.size - 1)
        }

        // Sync to Compose state on the main thread
        postToMain {
            entries.clear()
            entries.addAll(_entries)
        }
    }

    fun logRequest(url: String, method: String, body: String?) {
        val msg = buildString {
            appendLine("→ $method $url")
            if (body != null) appendLine("Body: $body")
        }
        postToMain { lastRequest = msg }
        log("HTTP", msg)
    }

    fun logResponse(code: Int, url: String, body: String?) {
        val msg = buildString {
            appendLine("← $code $url")
            if (body != null) {
                appendLine("Body: ${body.take(2000)}") // truncate huge responses
            }
        }
        postToMain { lastResponse = msg }
        log("HTTP", msg)
    }

    fun clear() {
        _entries.clear()
        postToMain {
            entries.clear()
            lastRequest = "(none)"
            lastResponse = "(none)"
        }
        Log.d(TAG, "Debug log cleared")
    }

    private fun postToMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }
}
