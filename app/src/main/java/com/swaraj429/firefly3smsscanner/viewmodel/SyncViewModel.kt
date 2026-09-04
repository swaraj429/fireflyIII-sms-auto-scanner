package com.swaraj429.firefly3smsscanner.viewmodel

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.swaraj429.firefly3smsscanner.db.FireflyDatabase
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.network.RetrofitClient
import com.swaraj429.firefly3smsscanner.prefs.AppPrefs
import com.swaraj429.firefly3smsscanner.sync.FireflySyncEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * ViewModel managing Firefly III reconciliation state and operations.
 * Auto-triggers sync on launch if last sync is > 12 hours stale (or fresh install).
 */
class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "SyncViewModel"
    private val dao = FireflyDatabase.getDatabase(application).smsRecordDao()
    private val prefs = AppPrefs(application)

    var isSyncing by mutableStateOf(false)
    var lastSyncResult by mutableStateOf<FireflySyncEngine.SyncResult?>(null)
    var lastSyncTime by mutableStateOf<Long?>(null)
    var syncStatusMessage by mutableStateOf("")

    init {
        viewModelScope.launch {
            try {
                lastSyncTime = dao.getLastSyncTimestamp()
                if (isSyncStale()) {
                    DebugLog.log(TAG, "Last sync is stale (>12h or never), auto-triggering reconciliation...")
                    runSync()
                } else {
                    val ago = formatTimeAgo(lastSyncTime)
                    syncStatusMessage = "Last synced: $ago"
                }
            } catch (e: Exception) {
                DebugLog.log(TAG, "Error initializing sync state: ${e.message}")
            }
        }
    }

    /**
     * Check if sync is needed (>12 hours since last sync, or never synced).
     */
    fun isSyncStale(): Boolean {
        val lastSync = lastSyncTime ?: return true
        val twelveHoursMs = 12 * 60 * 60 * 1000L
        return (System.currentTimeMillis() - lastSync) > twelveHoursMs
    }

    /**
     * Run reconciliation. Uses AppPrefs.syncRangeDays (default 30) for the date window.
     */
    fun runSync(onComplete: (FireflySyncEngine.SyncResult?) -> Unit = {}) {
        if (!prefs.isConfigured) {
            syncStatusMessage = "Firefly not configured"
            DebugLog.log(TAG, "Sync skipped: Firefly connection not configured")
            onComplete(null)
            return
        }

        if (isSyncing) {
            DebugLog.log(TAG, "Sync already in progress, skipping")
            return
        }

        viewModelScope.launch {
            isSyncing = true
            syncStatusMessage = "Syncing with Firefly..."
            try {
                val rangeDays = prefs.syncRangeDays
                val now = Calendar.getInstance()
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val endDate = dateFormat.format(now.time)

                val startCal = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -rangeDays)
                }
                val startDate = dateFormat.format(startCal.time)
                val cutoffMillis = startCal.timeInMillis

                val api = RetrofitClient.create(prefs.baseUrl, prefs.accessToken)
                val engine = FireflySyncEngine(dao, api)

                val result = engine.reconcile(startDate, endDate, cutoffMillis)
                lastSyncResult = result
                lastSyncTime = System.currentTimeMillis()

                syncStatusMessage = if (result.errors.isNotEmpty() && result.matched == 0) {
                    "Sync failed: ${result.errors.first()}"
                } else {
                    "Synced: ${result.matched} matched (${result.newlyReconciled} newly reconciled, ${result.updated} updated)"
                }

                onComplete(result)
            } catch (e: Exception) {
                val err = "Sync error: ${e.message}"
                syncStatusMessage = err
                DebugLog.log(TAG, err)
                onComplete(null)
            } finally {
                isSyncing = false
            }
        }
    }

    private fun formatTimeAgo(timestamp: Long?): String {
        if (timestamp == null) return "never"
        val diffMs = System.currentTimeMillis() - timestamp
        val minutes = diffMs / (60 * 1000)
        val hours = diffMs / (60 * 60 * 1000)
        val days = diffMs / (24 * 60 * 60 * 1000)

        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            else -> "${days}d ago"
        }
    }
}
