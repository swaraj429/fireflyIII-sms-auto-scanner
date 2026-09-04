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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.model.DismissReason
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.SendStatus
import com.swaraj429.firefly3smsscanner.model.TransactionType
import com.swaraj429.firefly3smsscanner.ui.components.*
import com.swaraj429.firefly3smsscanner.ui.sheets.DismissReasonSheet
import com.swaraj429.firefly3smsscanner.ui.sheets.TransactionEditorSheet
import com.swaraj429.firefly3smsscanner.ui.theme.*
import com.swaraj429.firefly3smsscanner.viewmodel.FireflyDataViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.SmsHistoryViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.SmsViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.TransactionViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.RulesViewModel
import com.swaraj429.firefly3smsscanner.parser.RuleEngine
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Home/Transactions screen that shows all SMS transaction records from the
 * last 30 days with their sync status (Pending / Sent / Failed / Dismissed).
 *
 * On first composition it loads the persisted history from Room and merges
 * in any freshly-parsed transactions from SmsViewModel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    smsViewModel: SmsViewModel,
    transactionViewModel: TransactionViewModel,
    fireflyDataViewModel: FireflyDataViewModel,
    smsHistoryViewModel: SmsHistoryViewModel,
    rulesViewModel: RulesViewModel = viewModel(),
    hasSmsPermission: Boolean = false,
    onRequestPermission: () -> Unit = {}
) {
    var selectedTransaction by remember { mutableStateOf<ParsedTransaction?>(null) }
    var transactionToDismiss by remember { mutableStateOf<ParsedTransaction?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }

    data class DateRangeOption(val label: String, val days: Int?, val thisMonth: Boolean = false)

    val dateRangeOptions = remember {
        listOf(
            DateRangeOption("This Month", null, thisMonth = true),
            DateRangeOption("Today", 0),
            DateRangeOption("7 Days", 7),
            DateRangeOption("30 Days", 30),
            DateRangeOption("90 Days", 90),
        )
    }

    var selectedDateRange by remember { mutableStateOf("This Month") }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val filters = listOf("All", "Pending", "Sent", "Failed", "Dismissed")

    // Decide data source: prefer the persisted history; fall back to in-memory parsed
    val historyList = smsHistoryViewModel.historyTransactions
    val inMemoryList = smsViewModel.parsedTransactions
    val transactions = if (historyList.isNotEmpty()) historyList else inMemoryList

    // Filter by selected date range (client-side timestamp filter)
    val rangeFilteredTransactions = transactions.filter { txn ->
        txn.timestamp >= smsViewModel.fromDate && txn.timestamp <= smsViewModel.toDate
    }

    val filtered = rangeFilteredTransactions.filter { txn ->
        when (selectedFilter) {
            "Pending" -> txn.status == SendStatus.PENDING
            "Sent" -> txn.status == SendStatus.SENT
            "Failed" -> txn.status == SendStatus.FAILED
            "Dismissed" -> txn.status == SendStatus.DISMISSED
            else -> txn.status != SendStatus.DISMISSED // "All" excludes dismissed
        }
    }

    val pendingCount = rangeFilteredTransactions.count { it.status == SendStatus.PENDING }
    val sentCount = rangeFilteredTransactions.count { it.status == SendStatus.SENT }
    val failedCount = rangeFilteredTransactions.count { it.status == SendStatus.FAILED }
    val dismissedCount = rangeFilteredTransactions.count { it.status == SendStatus.DISMISSED }
    val activeCount = rangeFilteredTransactions.count { it.status != SendStatus.DISMISSED }

    // Dismissed transactions are excluded from totals
    val totalSpend = rangeFilteredTransactions
        .filter { it.effectiveType == TransactionType.WITHDRAWAL && it.status != SendStatus.FAILED && it.status != SendStatus.DISMISSED }
        .sumOf { it.effectiveAmount }
    val totalIncome = rangeFilteredTransactions
        .filter { it.effectiveType == TransactionType.DEPOSIT && it.status != SendStatus.FAILED && it.status != SendStatus.DISMISSED }
        .sumOf { it.effectiveAmount }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ─── Summary Banner with Date Range Filter ───
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    // Date range filter inside the summary tile
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(dateRangeOptions) { option ->
                            FilterChip(
                                selected = selectedDateRange == option.label,
                                onClick = {
                                    selectedDateRange = option.label
                                    val cal = Calendar.getInstance()
                                    smsViewModel.toDate = cal.timeInMillis
                                    when {
                                        option.thisMonth -> {
                                            cal.set(Calendar.DAY_OF_MONTH, 1)
                                            cal.set(Calendar.HOUR_OF_DAY, 0)
                                            cal.set(Calendar.MINUTE, 0)
                                            cal.set(Calendar.SECOND, 0)
                                            cal.set(Calendar.MILLISECOND, 0)
                                        }
                                        option.days == 0 -> {
                                            cal.set(Calendar.HOUR_OF_DAY, 0)
                                            cal.set(Calendar.MINUTE, 0)
                                            cal.set(Calendar.SECOND, 0)
                                            cal.set(Calendar.MILLISECOND, 0)
                                        }
                                        option.days != null -> {
                                            cal.add(Calendar.DAY_OF_YEAR, -option.days)
                                        }
                                    }
                                    smsViewModel.fromDate = cal.timeInMillis
                                    // Auto-scan: triggers LaunchedEffect in MainActivity via smsMessages.size change
                                    if (hasSmsPermission) {
                                        smsViewModel.loadSmsByDateRange()
                                    } else {
                                        onRequestPermission()
                                    }
                                },
                                label = { Text(option.label, style = MaterialTheme.typography.labelSmall) },
                                shape = RoundedCornerShape(20.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.15f),
                                    selectedLabelColor = Primary
                                )
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

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

                    // ── Status pill row ──
                    if (pendingCount > 0 || sentCount > 0 || failedCount > 0 || dismissedCount > 0) {
                        Spacer(Modifier.height(10.dp))
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
                            if (dismissedCount > 0) {
                                StatusPill(
                                    count = dismissedCount,
                                    label = "Dismissed",
                                    color = MaterialTheme.colorScheme.outline,
                                    icon = Icons.Filled.Block,
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
                    "Dismissed" -> dismissedCount
                    else -> activeCount
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
        if (rangeFilteredTransactions.isEmpty()) {
            if (smsHistoryViewModel.isLoading || smsViewModel.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Primary)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (smsViewModel.isLoading) "Scanning SMS messages…" else "Loading history…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.Receipt, null, Modifier.size(64.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (transactions.isEmpty()) "No transactions yet" else "No transactions for $selectedDateRange",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Select a date range above to scan messages",
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
                    item(key = "header_$dateLabel") { DateSectionHeader(dateLabel) }
                    itemsIndexed(txns, key = { _, txn -> "${txn.sender}_${txn.timestamp}_${txn.effectiveAmount}" }) { _, txn ->
                        if (txn.status == SendStatus.DISMISSED) {
                            TransactionCard(
                                transaction = txn,
                                onClick = { selectedTransaction = txn },
                                onRestore = {
                                    smsHistoryViewModel.restoreTransaction(txn)
                                    smsViewModel.restoreTransaction(txn)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            message = "Restored to Pending",
                                            duration = SnackbarDuration.Short
                                        )
                                    }
                                }
                            )
                        } else {
                            val dismissBoxState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { dismissValue ->
                                    if (dismissValue == SwipeToDismissBoxValue.EndToStart || dismissValue == SwipeToDismissBoxValue.StartToEnd) {
                                        transactionToDismiss = txn
                                        false
                                    } else false
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissBoxState,
                                enableDismissFromStartToEnd = true,
                                enableDismissFromEndToStart = true,
                                backgroundContent = {
                                    val direction = dismissBoxState.dismissDirection
                                    val alignment = when (direction) {
                                        SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                                        SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                                        else -> Alignment.CenterEnd
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(ErrorCrimson.copy(alpha = 0.16f))
                                            .padding(horizontal = 20.dp),
                                        contentAlignment = alignment
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Filled.DeleteSweep,
                                                contentDescription = "Dismiss",
                                                tint = ErrorCrimson,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "Dismiss",
                                                color = ErrorCrimson,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            ) {
                                TransactionCard(
                                    transaction = txn,
                                    onClick = { selectedTransaction = txn }
                                )
                            }
                        }
                    }
                }
                item(key = "footer_spacer") { Spacer(Modifier.height(80.dp)) } // FAB clearance
            }
        }
    }

        // ─── Snackbar Host for Undo ───
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 76.dp)
        )
    }

    // ─── Transaction Editor Sheet ───
    selectedTransaction?.let { txn ->
        // Apply rules to fill empty fields when the editor opens
        LaunchedEffect(txn) {
            RuleEngine.applyRules(txn, rulesViewModel.rules)
        }

        TransactionEditorSheet(
            transaction = txn,
            fireflyData = fireflyDataViewModel,
            onSave = {
                transactionViewModel.sendTransaction(txn, smsHistoryViewModel) { _ -> }
                selectedTransaction = null
            },
            onDismiss = { selectedTransaction = null },
            onDismissTransaction = { reason ->
                smsHistoryViewModel.dismissTransaction(txn, reason)
                smsViewModel.dismissTransaction(txn, reason)
                selectedTransaction = null
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = "Dismissed as ${reason.badgeLabel}",
                        actionLabel = "UNDO",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        smsHistoryViewModel.restoreTransaction(txn)
                        smsViewModel.restoreTransaction(txn)
                    }
                }
            },
            onRestoreTransaction = {
                smsHistoryViewModel.restoreTransaction(txn)
                smsViewModel.restoreTransaction(txn)
                selectedTransaction = null
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "Restored to Pending",
                        duration = SnackbarDuration.Short
                    )
                }
            }
        )
    }

    // ─── Dismiss Reason Sheet (triggered by swipe) ───
    transactionToDismiss?.let { txn ->
        DismissReasonSheet(
            transaction = txn,
            onConfirm = { reason ->
                smsHistoryViewModel.dismissTransaction(txn, reason)
                smsViewModel.dismissTransaction(txn, reason)
                transactionToDismiss = null
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    val result = snackbarHostState.showSnackbar(
                        message = "Dismissed as ${reason.badgeLabel}",
                        actionLabel = "UNDO",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        smsHistoryViewModel.restoreTransaction(txn)
                        smsViewModel.restoreTransaction(txn)
                    }
                }
            },
            onDismiss = { transactionToDismiss = null }
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
