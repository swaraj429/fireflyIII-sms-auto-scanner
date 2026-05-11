package com.swaraj429.firefly3smsscanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.SendStatus
import com.swaraj429.firefly3smsscanner.model.TransactionType
import com.swaraj429.firefly3smsscanner.ui.components.*
import com.swaraj429.firefly3smsscanner.ui.sheets.TransactionEditorSheet
import com.swaraj429.firefly3smsscanner.ui.theme.*
import com.swaraj429.firefly3smsscanner.viewmodel.FireflyDataViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.SmsHistoryViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.SmsViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.TransactionViewModel

/**
 * Home/Transactions screen that shows all SMS transaction records from the
 * last 30 days with their sync status (Pending / Sent / Failed).
 *
 * On first composition it loads the persisted history from Room and merges
 * in any freshly-parsed transactions from SmsViewModel.
 */
@Composable
fun HomeScreen(
    smsViewModel: SmsViewModel,
    transactionViewModel: TransactionViewModel,
    fireflyDataViewModel: FireflyDataViewModel,
    smsHistoryViewModel: SmsHistoryViewModel
) {
    var selectedTransaction by remember { mutableStateOf<ParsedTransaction?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }

    val filters = listOf("All", "Pending", "Sent", "Failed")

    // Decide data source: prefer the persisted history; fall back to in-memory parsed
    val historyList = smsHistoryViewModel.historyTransactions
    val inMemoryList = smsViewModel.parsedTransactions
    val transactions = if (historyList.isNotEmpty()) historyList else inMemoryList

    val filtered = transactions.filter { txn ->
        when (selectedFilter) {
            "Pending" -> txn.status == SendStatus.PENDING
            "Sent" -> txn.status == SendStatus.SENT
            "Failed" -> txn.status == SendStatus.FAILED
            else -> true
        }
    }

    val pendingCount = transactions.count { it.status == SendStatus.PENDING }
    val sentCount = transactions.count { it.status == SendStatus.SENT }
    val failedCount = transactions.count { it.status == SendStatus.FAILED }
    val totalSpend = transactions
        .filter { it.effectiveType == TransactionType.WITHDRAWAL && it.status != SendStatus.FAILED }
        .sumOf { it.effectiveAmount }
    val totalIncome = transactions
        .filter { it.effectiveType == TransactionType.DEPOSIT && it.status != SendStatus.FAILED }
        .sumOf { it.effectiveAmount }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Summary Banner ───
        if (transactions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(
                        "Last 30 Days",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Spend column
                        Column {
                            Text("Total Spend", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatCurrency(totalSpend),
                                style = AmountMediumStyle,
                                fontWeight = FontWeight.Bold,
                                color = if (totalSpend > 0) DebitRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        // Income column
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                formatCurrency(totalIncome),
                                style = AmountMediumStyle,
                                fontWeight = FontWeight.Bold,
                                color = if (totalIncome > 0) CreditGreen else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // ── Status pill row ──
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (pendingCount > 0) {
                            StatusPill(
                                count = pendingCount,
                                label = "Pending",
                                color = WarningAmber,
                                icon = Icons.Filled.Schedule,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (sentCount > 0) {
                            StatusPill(
                                count = sentCount,
                                label = "Sent",
                                color = SuccessGreen,
                                icon = Icons.Filled.CheckCircle,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (failedCount > 0) {
                            StatusPill(
                                count = failedCount,
                                label = "Failed",
                                color = ErrorCrimson,
                                icon = Icons.Filled.Error,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // ─── Sync status ───
        if (!fireflyDataViewModel.hasSynced) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Sync, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sync Firefly data for categories & budgets", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Button(onClick = { fireflyDataViewModel.refreshAll() }, enabled = !fireflyDataViewModel.isLoading) { Text("Sync") }
                }
            }
        }

        // ─── Last result toast ───
        AnimatedVisibility(visible = transactionViewModel.lastResult.isNotBlank(), enter = slideInVertically() + fadeIn()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (transactionViewModel.lastResult.startsWith("✅")) SuccessGreen.copy(alpha = 0.15f)
                    else ErrorCrimson.copy(alpha = 0.15f)
                )
            ) {
                Text(transactionViewModel.lastResult, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        // ─── Filter chips ───
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filters) { filter ->
                val count = when (filter) {
                    "Pending" -> pendingCount
                    "Sent" -> sentCount
                    "Failed" -> failedCount
                    else -> transactions.size
                }
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text("$filter ($count)", style = MaterialTheme.typography.labelSmall)
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.15f),
                        selectedLabelColor = Primary
                    )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ─── Bulk approve ───
        if (pendingCount > 1 && (selectedFilter == "All" || selectedFilter == "Pending")) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    filtered.filter { it.status == SendStatus.PENDING }.forEach { txn ->
                        transactionViewModel.sendTransaction(txn, smsHistoryViewModel) { _ -> }
                    }
                }) {
                    Icon(Icons.Filled.DoneAll, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Send All ($pendingCount)", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ─── Timeline ───
        if (transactions.isEmpty()) {
            if (smsHistoryViewModel.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(Modifier.height(12.dp))
                        Text("Loading history…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Receipt, null, Modifier.size(64.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(16.dp))
                        Text("No transactions yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Go to SMS tab to scan and parse messages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.FilterList, null, Modifier.size(48.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(12.dp))
                    Text("No $selectedFilter transactions", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            val grouped = groupTransactionsByDate(filtered)
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                grouped.forEach { (dateLabel, txns) ->
                    item { DateSectionHeader(dateLabel) }
                    itemsIndexed(txns) { _, txn ->
                        TransactionCard(
                            transaction = txn,
                            onClick = { selectedTransaction = txn }
                        )
                    }
                }
                item { Spacer(Modifier.height(80.dp)) } // FAB clearance
            }
        }
    }

    // ─── Transaction Editor Sheet ───
    selectedTransaction?.let { txn ->
        TransactionEditorSheet(
            transaction = txn,
            fireflyData = fireflyDataViewModel,
            onSave = {
                transactionViewModel.sendTransaction(txn, smsHistoryViewModel) { _ -> }
                selectedTransaction = null
            },
            onDismiss = { selectedTransaction = null }
        )
    }
}

/**
 * Compact status pill for the summary banner.
 */
@Composable
private fun StatusPill(
    count: Int,
    label: String,
    color: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(4.dp))
            Text(
                "$count $label",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}
