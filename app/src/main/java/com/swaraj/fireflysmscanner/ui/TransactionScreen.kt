package com.swaraj.fireflysmscanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swaraj.fireflysmscanner.model.ParsedTransaction
import com.swaraj.fireflysmscanner.model.SendStatus
import com.swaraj.fireflysmscanner.model.TransactionType
import com.swaraj.fireflysmscanner.viewmodel.SmsViewModel
import com.swaraj.fireflysmscanner.viewmodel.TransactionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    smsViewModel: SmsViewModel,
    transactionViewModel: TransactionViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "💰 Parsed Transactions",
            style = MaterialTheme.typography.headlineMedium
        )

        val total = smsViewModel.parsedTransactions.size
        Text(
            text = "$total transactions parsed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Last result
        if (transactionViewModel.lastResult.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (transactionViewModel.lastResult.startsWith("✅"))
                        MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = transactionViewModel.lastResult,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider()

        if (smsViewModel.parsedTransactions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No parsed transactions.\nGo to SMS tab and scan + parse messages.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(smsViewModel.parsedTransactions) { index, transaction ->
                    TransactionCard(
                        index = index,
                        transaction = transaction,
                        onSend = {
                            transactionViewModel.sendTransaction(transaction) { _ ->
                                // Force recomposition by updating status (already done in VM)
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionCard(
    index: Int,
    transaction: ParsedTransaction,
    onSend: () -> Unit
) {
    var editAmount by remember(transaction) {
        mutableStateOf(transaction.effectiveAmount.toString())
    }
    var editType by remember(transaction) {
        mutableStateOf(transaction.effectiveType)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Header: index + status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${index + 1}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                StatusChip(transaction.status)
            }

            // Amount input
            OutlinedTextField(
                value = editAmount,
                onValueChange = {
                    editAmount = it
                    it.toDoubleOrNull()?.let { amount ->
                        transaction.correctedAmount = amount
                    }
                },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Type toggle
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = editType == TransactionType.DEBIT,
                    onClick = {
                        editType = TransactionType.DEBIT
                        transaction.correctedType = TransactionType.DEBIT
                    },
                    label = { Text("🔴 Debit") }
                )
                FilterChip(
                    selected = editType == TransactionType.CREDIT,
                    onClick = {
                        editType = TransactionType.CREDIT
                        transaction.correctedType = TransactionType.CREDIT
                    },
                    label = { Text("🟢 Credit") }
                )
            }

            // Raw message preview
            Text(
                text = transaction.rawMessage,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Send button
            Button(
                onClick = onSend,
                enabled = transaction.status != SendStatus.SENDING &&
                        transaction.status != SendStatus.SENT,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (transaction.status) {
                        SendStatus.SENT -> Color(0xFF4CAF50)
                        SendStatus.FAILED -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    when (transaction.status) {
                        SendStatus.PENDING -> "Send to Firefly"
                        SendStatus.SENDING -> "Sending..."
                        SendStatus.SENT -> "Sent ✓"
                        SendStatus.FAILED -> "Retry"
                    }
                )
            }
        }
    }
}

@Composable
private fun StatusChip(status: SendStatus) {
    val (color, text, icon) = when (status) {
        SendStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            "Pending",
            null
        )
        SendStatus.SENDING -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            "Sending",
            null
        )
        SendStatus.SENT -> Triple(
            Color(0xFF4CAF50).copy(alpha = 0.2f),
            "Sent",
            Icons.Default.CheckCircle
        )
        SendStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            "Failed",
            Icons.Default.Error
        )
    }

    Surface(
        color = color,
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon?.let {
                Icon(it, null, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(text, style = MaterialTheme.typography.labelSmall)
        }
    }
}
