package com.swaraj429.firefly3smsscanner.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.swaraj429.firefly3smsscanner.db.FireflyDatabase
import com.swaraj429.firefly3smsscanner.db.SmsRecordEntity
import com.swaraj429.firefly3smsscanner.debug.DebugLog
import com.swaraj429.firefly3smsscanner.ui.theme.*
import com.swaraj429.firefly3smsscanner.viewmodel.SetupViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.SmsHistoryViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Settings screen with Connection config, Live Detection status,
 * Debug & Database section (logs, DB tables, clear).
 */
@Composable
fun SettingsScreen(
    viewModel: SetupViewModel,
    smsHistoryViewModel: SmsHistoryViewModel? = null
) {
    var showToken by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val hasReceiveSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val hasNotifications = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    val liveActive = hasReceiveSms && hasNotifications

    // Debug & DB state
    var showDebugSection by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var showDbViewer by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // DB stats
    var accountCount by remember { mutableStateOf(0) }
    var categoryCount by remember { mutableStateOf(0) }
    var tagCount by remember { mutableStateOf(0) }
    var budgetCount by remember { mutableStateOf(0) }
    var smsRecordCount by remember { mutableStateOf(0) }
    var smsRecords by remember { mutableStateOf<List<SmsRecordEntity>>(emptyList()) }

    val db = remember { FireflyDatabase.getDatabase(context) }

    // Load DB stats when Debug section opens
    LaunchedEffect(showDebugSection) {
        if (showDebugSection) {
            try {
                accountCount = db.fireflyDao().countAccounts()
                categoryCount = db.fireflyDao().countCategories()
                tagCount = db.fireflyDao().countTags()
                budgetCount = db.fireflyDao().countBudgets()
                smsRecordCount = db.smsRecordDao().totalCount()
            } catch (_: Exception) {}
        }
    }

    // Load SMS records when DB viewer opens
    LaunchedEffect(showDbViewer) {
        if (showDbViewer) {
            try {
                smsRecords = db.smsRecordDao().getAllRecords()
            } catch (_: Exception) {}
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // ─── Live Detection Status ───
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (liveActive) SuccessGreen.copy(alpha = 0.1f) else ErrorCrimson.copy(alpha = 0.1f)
                )
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (liveActive) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                        null, Modifier.size(24.dp),
                        if (liveActive) SuccessGreen else ErrorCrimson
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (liveActive) "Live Detection Active" else "Live Detection Off",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            color = if (liveActive) SuccessGreen else ErrorCrimson
                        )
                        Text(
                            if (liveActive) "SMS transactions trigger instant notifications"
                            else {
                                val missing = buildList {
                                    if (!hasReceiveSms) add("RECEIVE_SMS")
                                    if (!hasNotifications) add("Notifications")
                                }.joinToString(", ")
                                "Missing: $missing"
                            },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!liveActive) {
                        FilledTonalButton(onClick = {
                            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            })
                        }, shape = RoundedCornerShape(10.dp)) { Text("Fix") }
                    }
                }
            }
        }

        // ─── Connection Section ───
        item { SectionHeader("Connection") }
        item {
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = viewModel.baseUrl, onValueChange = { viewModel.baseUrl = it },
                        label = { Text("Server URL") }, placeholder = { Text("https://firefly.example.com") },
                        leadingIcon = { Icon(Icons.Filled.Link, null, Modifier.size(20.dp)) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = viewModel.accessToken, onValueChange = { viewModel.accessToken = it },
                        label = { Text("Access Token") }, placeholder = { Text("eyJ0eX...") },
                        leadingIcon = { Icon(Icons.Filled.Key, null, Modifier.size(20.dp)) },
                        singleLine = true, shape = RoundedCornerShape(12.dp),
                        visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showToken = !showToken }) {
                                Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle")
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.testConnection() }, enabled = !viewModel.isTesting,
                            shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Primary)
                        ) {
                            if (viewModel.isTesting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnPrimary)
                            else Icon(Icons.Filled.Wifi, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (viewModel.isTesting) "Testing..." else "Test", fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(onClick = { viewModel.saveConfig() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ─── Connection Status ───
        if (viewModel.connectionStatus.isNotBlank()) {
            item {
                Card(
                    Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            viewModel.connectionStatus.startsWith("✅") -> SuccessGreen.copy(alpha = 0.1f)
                            viewModel.connectionStatus.startsWith("❌") -> ErrorCrimson.copy(alpha = 0.1f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Text(viewModel.connectionStatus, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // ─── Debug & Database Section ───
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        item { SectionHeader("Debug & Database") }

        // ── Database Stats ──
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { showDebugSection = !showDebugSection },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Storage, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Database", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Icon(
                            if (showDebugSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            "Toggle", Modifier.size(20.dp)
                        )
                    }

                    if (showDebugSection) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        // Table row stats
                        DbTableRow("accounts", accountCount)
                        DbTableRow("categories", categoryCount)
                        DbTableRow("tags", tagCount)
                        DbTableRow("budgets", budgetCount)
                        DbTableRow("sms_records", smsRecordCount)

                        Spacer(Modifier.height(4.dp))

                        // Action buttons
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showDbViewer = true },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.TableChart, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("View Records", style = MaterialTheme.typography.labelMedium)
                            }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        accountCount = db.fireflyDao().countAccounts()
                                        categoryCount = db.fireflyDao().countCategories()
                                        tagCount = db.fireflyDao().countTags()
                                        budgetCount = db.fireflyDao().countBudgets()
                                        smsRecordCount = db.smsRecordDao().totalCount()
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Refresh, null, Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Refresh", style = MaterialTheme.typography.labelMedium)
                            }
                        }

                        // Clear database button
                        Button(
                            onClick = { showClearConfirm = true },
                            colors = ButtonDefaults.buttonColors(containerColor = ErrorCrimson),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.DeleteForever, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Clear All Database", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // ── Debug Logs ──
        item {
            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth().clickable { showLogs = !showLogs },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.BugReport, null, Modifier.size(20.dp), MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Debug Logs", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Text(
                            "${DebugLog.entries.size} entries",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            if (showLogs) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            "Toggle", Modifier.size(20.dp)
                        )
                    }

                    if (showLogs) {
                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        // Last Request
                        Text("Last Request", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(
                            DebugLog.lastRequest,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )

                        Spacer(Modifier.height(4.dp))

                        // Last Response
                        Text("Last Response", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(
                            DebugLog.lastResponse,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )

                        HorizontalDivider(Modifier.padding(vertical = 4.dp))

                        // Clear logs
                        TextButton(onClick = { DebugLog.clear() }) {
                            Icon(Icons.Filled.Delete, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear Logs")
                        }
                    }
                }
            }
        }

        // ── Log entries (shown when expanded) ──
        if (showLogs) {
            items(DebugLog.entries) { entry ->
                Text(
                    text = "${entry.timestamp} [${entry.tag}] ${entry.message}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .horizontalScroll(rememberScrollState())
                )
            }
        }

        item { Spacer(Modifier.height(32.dp)) }
    }

    // ─── Clear Database Confirmation Dialog ───
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            icon = { Icon(Icons.Filled.Warning, null, tint = ErrorCrimson) },
            title = { Text("Clear All Database?") },
            text = {
                Text("This will delete all cached accounts, categories, tags, budgets, AND all SMS transaction history. This cannot be undone.\n\nFirefly data will re-sync on the next app launch.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                db.fireflyDao().clearAll()
                                db.smsRecordDao().deleteAll()

                                // Reset stats
                                accountCount = 0
                                categoryCount = 0
                                tagCount = 0
                                budgetCount = 0
                                smsRecordCount = 0

                                // Reload history
                                smsHistoryViewModel?.loadHistory()

                                DebugLog.log("Settings", "Database cleared by user")
                            } catch (e: Exception) {
                                DebugLog.log("Settings", "Error clearing DB: ${e.message}")
                            }
                        }
                        showClearConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ErrorCrimson)
                ) {
                    Text("Clear Everything")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // ─── SMS Records Viewer Dialog ───
    if (showDbViewer) {
        AlertDialog(
            onDismissRequest = { showDbViewer = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.TableChart, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SMS Records (${smsRecords.size})")
                }
            },
            text = {
                if (smsRecords.isEmpty()) {
                    Text("No SMS records in the database.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(smsRecords) { record ->
                            SmsRecordRow(record)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDbViewer = false }) {
                    Text("Close")
                }
            }
        )
    }
}

/**
 * Row showing a database table name and its record count.
 */
@Composable
private fun DbTableRow(tableName: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            tableName,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Text(
                "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}

/**
 * Compact card showing a single SMS record from the database.
 */
@Composable
private fun SmsRecordRow(record: SmsRecordEntity) {
    val dateFormat = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    val statusColor = when (record.syncStatus) {
        "SENT" -> SuccessGreen
        "FAILED" -> ErrorCrimson
        else -> WarningAmber
    }

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    record.sender,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                    Text(
                        record.syncStatus,
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "₹${String.format(Locale.US, "%.2f", record.amount)} · ${record.transactionType}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    dateFormat.format(Date(record.smsTimestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                record.body.take(80) + if (record.body.length > 80) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 2
            )
            if (record.smsHash.isNotEmpty()) {
                Text(
                    "hash: ${record.smsHash.take(16)}…",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
            if (!record.fireflyTransactionId.isNullOrEmpty()) {
                Text(
                    "firefly #${record.fireflyTransactionId}",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = SuccessGreen.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}
