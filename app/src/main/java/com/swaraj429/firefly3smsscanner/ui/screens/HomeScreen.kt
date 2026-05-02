package com.swaraj429.firefly3smsscanner.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.swaraj429.firefly3smsscanner.viewmodel.SmsViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.TransactionViewModel

/**
 * Home/Transactions screen with timeline layout, summary banner, and bulk approve.
 */
@Composable
fun HomeScreen(
    smsViewModel: SmsViewModel,
    transactionViewModel: TransactionViewModel,
    fireflyDataViewModel: FireflyDataViewModel
) {
    var selectedTransaction by remember { mutableStateOf<ParsedTransaction?>(null) }

    val transactions = smsViewModel.parsedTransactions
    val pendingCount = transactions.count { it.status == SendStatus.PENDING }
    val todaySpend = transactions
        .filter { it.effectiveType == TransactionType.DEBIT && it.status != SendStatus.FAILED }
        .sumOf { it.effectiveAmount }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Summary Banner ───
        if (transactions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Today's Spend", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            formatCurrency(todaySpend),
                            style = AmountMediumStyle,
                            fontWeight = FontWeight.Bold,
                            color = if (todaySpend > 0) DebitRed else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (pendingCount > 0) {
                        Surface(color = WarningAmber.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Schedule, null, Modifier.size(16.dp), WarningAmber)
                                Spacer(Modifier.width(6.dp))
                                Text("$pendingCount pending", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = WarningAmber)
                            }
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

        // ─── Bulk approve ───
        if (pendingCount > 1) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    transactions.filter { it.status == SendStatus.PENDING }.forEach { txn ->
                        transactionViewModel.sendTransaction(txn) { _ -> }
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Receipt, null, Modifier.size(64.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("No transactions yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Go to SMS tab to scan and parse messages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            val grouped = groupTransactionsByDate(transactions)
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
                transactionViewModel.sendTransaction(txn) { _ -> }
                selectedTransaction = null
            },
            onDismiss = { selectedTransaction = null }
        )
    }
}
