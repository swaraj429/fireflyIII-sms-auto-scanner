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

    fun clear() {
        _entries.clear()
        postToMain {
            entries.clear()
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
