package com.swaraj429.firefly3smsscanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.model.*
import com.swaraj429.firefly3smsscanner.viewmodel.FireflyDataViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.SmsViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.TransactionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionScreen(
    smsViewModel: SmsViewModel,
    transactionViewModel: TransactionViewModel,
    fireflyDataViewModel: FireflyDataViewModel
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

        // Sync status & button
        if (!fireflyDataViewModel.hasSynced) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Text(
                        text = "Sync Firefly data to enable categories, tags & budgets",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { fireflyDataViewModel.refreshAll() },
                        enabled = !fireflyDataViewModel.isLoading
                    ) {
                        Text("Sync")
                    }
                }
            }
        } else {
            // Compact re-sync button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = fireflyDataViewModel.lastSyncStatus,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = { fireflyDataViewModel.refreshAll() },
                    enabled = !fireflyDataViewModel.isLoading
                ) {
                    if (fireflyDataViewModel.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                    Text("Re-sync")
                }
            }
        }

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
                        fireflyData = fireflyDataViewModel,
                        onSend = {
                            transactionViewModel.sendTransaction(transaction) { _ -> }
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
    fireflyData: FireflyDataViewModel,
    onSend: () -> Unit
) {
    var editAmount by remember(transaction) {
        mutableStateOf(transaction.effectiveAmount.toString())
    }
    var editType by remember(transaction) {
        mutableStateOf(transaction.effectiveType)
    }
    var editDescription by remember(transaction) {
        mutableStateOf(transaction.description.ifBlank {
            "SMS: ${transaction.rawMessage.take(60)}"
        })
    }

    // Dropdown expansion states
    var categoryExpanded by remember { mutableStateOf(false) }
    var budgetExpanded by remember { mutableStateOf(false) }
    var sourceExpanded by remember { mutableStateOf(false) }
    var destExpanded by remember { mutableStateOf(false) }
    var showTagSheet by remember { mutableStateOf(false) }
    var showRawMessage by remember { mutableStateOf(false) }

    // Local state for selections
    var selectedCategory by remember(transaction) { mutableStateOf(transaction.categoryName ?: "") }
    var selectedBudget by remember(transaction) { mutableStateOf(transaction.budgetName ?: "") }
    var selectedBudgetId by remember(transaction) { mutableStateOf(transaction.budgetId) }
    var selectedSourceName by remember(transaction) { mutableStateOf(transaction.sourceAccountName ?: "") }
    var selectedSourceId by remember(transaction) { mutableStateOf(transaction.sourceAccountId) }
    var selectedDestName by remember(transaction) { mutableStateOf(transaction.destinationAccountName ?: "") }
    var selectedDestId by remember(transaction) { mutableStateOf(transaction.destinationAccountId) }
    var selectedTags by remember(transaction) { mutableStateOf(transaction.selectedTags.toList()) }

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

            // Description field
            OutlinedTextField(
                value = editDescription,
                onValueChange = {
                    editDescription = it
                    transaction.description = it
                },
                label = { Text("Description") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.bodyMedium
            )

            // Amount + Type row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                    modifier = Modifier.weight(1f)
                )

                // Type toggle chips
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    FilterChip(
                        selected = editType == TransactionType.DEBIT,
                        onClick = {
                            editType = TransactionType.DEBIT
                            transaction.correctedType = TransactionType.DEBIT
                        },
                        label = { Text("🔴 Debit") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    FilterChip(
                        selected = editType == TransactionType.CREDIT,
                        onClick = {
                            editType = TransactionType.CREDIT
                            transaction.correctedType = TransactionType.CREDIT
                        },
                        label = { Text("🟢 Credit") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Category selector
            if (fireflyData.hasSynced && fireflyData.categories.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {
                            selectedCategory = it
                            transaction.categoryName = it.ifBlank { null }
                        },
                        label = { Text("📁 Category") },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    val filtered = fireflyData.categories.filter {
                        selectedCategory.isBlank() || it.name.contains(selectedCategory, ignoreCase = true)
                    }
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        // "None" option
                        DropdownMenuItem(
                            text = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                selectedCategory = ""
                                transaction.categoryName = null
                                categoryExpanded = false
                            }
                        )
                        filtered.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategory = cat.name
                                    transaction.categoryName = cat.name
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Budget selector
            if (fireflyData.hasSynced && fireflyData.budgets.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = budgetExpanded,
                    onExpandedChange = { budgetExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedBudget,
                        onValueChange = {},
                        label = { Text("💼 Budget") },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = budgetExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = budgetExpanded,
                        onDismissRequest = { budgetExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                selectedBudget = ""
                                selectedBudgetId = null
                                transaction.budgetId = null
                                transaction.budgetName = null
                                budgetExpanded = false
                            }
                        )
                        fireflyData.budgets.forEach { budget ->
                            DropdownMenuItem(
                                text = { Text(budget.name) },
                                onClick = {
                                    selectedBudget = budget.name
                                    selectedBudgetId = budget.id
                                    transaction.budgetId = budget.id
                                    transaction.budgetName = budget.name
                                    budgetExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Detected Account Suggestions
            if (transaction.possibleAccountMatches.size > 1) {
                Column {
                    Text(
                        text = "✨ Suggested Accounts (Detected in SMS):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        transaction.possibleAccountMatches.forEach { acc ->
                            SuggestionChip(
                                onClick = {
                                    if (editType == TransactionType.DEBIT) {
                                        selectedSourceName = acc.name
                                        selectedSourceId = acc.accountId
                                        transaction.sourceAccountId = acc.accountId
                                        transaction.sourceAccountName = acc.name
                                    } else {
                                        selectedDestName = acc.name
                                        selectedDestId = acc.accountId
                                        transaction.destinationAccountId = acc.accountId
                                        transaction.destinationAccountName = acc.name
                                    }
                                },
                                label = { Text("${acc.name} (*${acc.lastDigits})") }
                            )
                        }
                    }
                }
            }

            // Source account selector
            if (fireflyData.hasSynced && fireflyData.assetAccounts.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = sourceExpanded,
                    onExpandedChange = { sourceExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedSourceName,
                        onValueChange = {},
                        label = { Text("🏦 Source Account") },
                        readOnly = true,
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = sourceExpanded,
                        onDismissRequest = { sourceExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("— Default —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                selectedSourceName = ""
                                selectedSourceId = null
                                transaction.sourceAccountId = null
                                transaction.sourceAccountName = null
                                sourceExpanded = false
                            }
                        )
                        fireflyData.assetAccounts.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedSourceName = account.name
                                    selectedSourceId = account.id
                                    transaction.sourceAccountId = account.id
                                    transaction.sourceAccountName = account.name
                                    sourceExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Destination account selector (expense accounts for withdrawals)
            if (fireflyData.hasSynced && fireflyData.expenseAccounts.isNotEmpty()) {
                ExposedDropdownMenuBox(
                    expanded = destExpanded,
                    onExpandedChange = { destExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedDestName,
                        onValueChange = {
                            selectedDestName = it
                            transaction.destinationAccountName = it.ifBlank { null }
                            transaction.destinationAccountId = null
                        },
                        label = { Text("🏪 Destination Account") },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = destExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    val filtered = fireflyData.expenseAccounts.filter {
                        selectedDestName.isBlank() || it.name.contains(selectedDestName, ignoreCase = true)
                    }
                    ExposedDropdownMenu(
                        expanded = destExpanded,
                        onDismissRequest = { destExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("— Auto —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            onClick = {
                                selectedDestName = ""
                                selectedDestId = null
                                transaction.destinationAccountId = null
                                transaction.destinationAccountName = null
                                destExpanded = false
                            }
                        )
                        filtered.forEach { account ->
                            DropdownMenuItem(
                                text = { Text(account.name) },
                                onClick = {
                                    selectedDestName = account.name
                                    selectedDestId = account.id
                                    transaction.destinationAccountId = account.id
                                    transaction.destinationAccountName = account.name
                                    destExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Tags selector
            if (fireflyData.hasSynced && fireflyData.tags.isNotEmpty()) {
                Column {
                    Text(
                        text = "🏷️ Tags",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))

                    // Show selected tags as chips
                    if (selectedTags.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            selectedTags.forEach { tag ->
                                InputChip(
                                    selected = true,
                                    onClick = {
                                        selectedTags = selectedTags - tag
                                        transaction.selectedTags.remove(tag)
                                    },
                                    label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, "Remove", modifier = Modifier.size(14.dp))
                                    }
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    // Tag selection button
                    OutlinedButton(
                        onClick = { showTagSheet = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (selectedTags.isEmpty()) "Select Tags" else "Edit Tags (${selectedTags.size})")
                    }
                }
            }

            // Raw message preview (collapsible)
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRawMessage = !showRawMessage },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (showRawMessage) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "Raw SMS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showRawMessage) {
                    Text(
                        text = transaction.rawMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp, top = 4.dp)
                    )
                } else {
                    Text(
                        text = transaction.rawMessage,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 20.dp)
                    )
                }
            }

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

    // Tag selection dialog
    if (showTagSheet) {
        AlertDialog(
            onDismissRequest = { showTagSheet = false },
            title = { Text("🏷️ Select Tags") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    fireflyData.tags.forEach { tag ->
                        val isSelected = tag.name in selectedTags
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedTags = if (isSelected) {
                                        selectedTags - tag.name
                                    } else {
                                        selectedTags + tag.name
                                    }
                                    transaction.selectedTags.clear()
                                    transaction.selectedTags.addAll(selectedTags)
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { checked ->
                                    selectedTags = if (checked) {
                                        selectedTags + tag.name
                                    } else {
                                        selectedTags - tag.name
                                    }
                                    transaction.selectedTags.clear()
                                    transaction.selectedTags.addAll(selectedTags)
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(tag.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagSheet = false }) {
                    Text("Done")
                }
            }
        )
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
