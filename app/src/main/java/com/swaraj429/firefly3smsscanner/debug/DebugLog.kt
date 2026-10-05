package com.swaraj429.firefly3smsscanner.debug

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
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

    private val mainHandler: Handler? = try {
        val looper = Looper.getMainLooper()
        if (looper != null) Handler(looper) else null
    } catch (_: Throwable) {
        null
    }

    private fun threadSafeDateFormat(): SimpleDateFormat =
        SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    fun log(tag: String, message: String) {
        val timestamp = threadSafeDateFormat().format(Date())
        val entry = Entry(timestamp, tag, message)

        try {
            Log.d("FF_$tag", message) // Always log to Logcat too
        } catch (_: Throwable) {}

        _entries.add(0, entry) // newest first
        while (_entries.size > MAX_ENTRIES) {
            _entries.removeAt(_entries.size - 1)
        }

        // Sync to Compose state on the main thread
        postToMain {
            try {
                entries.clear()
                entries.addAll(_entries)
            } catch (_: Throwable) {}
        }
    }

    fun clear() {
        _entries.clear()
        postToMain {
            try {
                entries.clear()
            } catch (_: Throwable) {}
        }
        try {
            Log.d(TAG, "Debug log cleared")
        } catch (_: Throwable) {}
    }

    private fun postToMain(block: () -> Unit) {
        val handler = mainHandler
        if (handler == null) {
            try { block() } catch (_: Throwable) {}
            return
        }
        try {
            if (Looper.myLooper() == Looper.getMainLooper()) {
                block()
            } else {
                handler.post(block)
            }
        } catch (_: Throwable) {
            try { block() } catch (_: Throwable) {}
        }
    }
}
