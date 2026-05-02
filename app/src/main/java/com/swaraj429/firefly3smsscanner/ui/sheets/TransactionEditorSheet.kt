package com.swaraj429.firefly3smsscanner.ui.sheets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.model.*
import com.swaraj429.firefly3smsscanner.ui.components.*
import com.swaraj429.firefly3smsscanner.ui.theme.*
import com.swaraj429.firefly3smsscanner.viewmodel.FireflyDataViewModel

/**
 * Transaction Editor as a Modal Bottom Sheet.
 * Auto-fills fields from parsed SMS and allows quick corrections.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorSheet(
    transaction: ParsedTransaction,
    fireflyData: FireflyDataViewModel,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editAmount by remember(transaction) { mutableStateOf(transaction.effectiveAmount.toString()) }
    var editType by remember(transaction) { mutableStateOf(transaction.effectiveType) }
    var editDescription by remember(transaction) { mutableStateOf(transaction.description.ifBlank { "SMS: ${transaction.rawMessage.take(60)}" }) }
    var selectedCategory by remember(transaction) { mutableStateOf(transaction.categoryName) }
    var selectedBudget by remember(transaction) { mutableStateOf(transaction.budgetName) }
    var selectedBudgetId by remember(transaction) { mutableStateOf(transaction.budgetId) }
    var selectedSourceAccount by remember(transaction) {
        mutableStateOf(
            if (transaction.sourceAccountId != null) {
                fireflyData.assetAccounts.find { it.id == transaction.sourceAccountId }
            } else null
        )
    }
    var selectedDestAccount by remember(transaction) {
        mutableStateOf(
            if (transaction.destinationAccountId != null) {
                (fireflyData.expenseAccounts + fireflyData.assetAccounts).find { it.id == transaction.destinationAccountId }
            } else null
        )
    }
    var selectedTags by remember(transaction) { mutableStateOf(transaction.selectedTags.toList()) }
    var showRawSms by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showBudgetPicker by remember { mutableStateOf(false) }

    val isDebit = editType == TransactionType.DEBIT
    val amountColor by animateColorAsState(
        targetValue = if (isDebit) DebitRed else CreditGreen,
        animationSpec = tween(200), label = "amt_color"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Amount + Type ───
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = editAmount,
                    onValueChange = { editAmount = it; it.toDoubleOrNull()?.let { a -> transaction.correctedAmount = a } },
                    textStyle = AmountLargeStyle.copy(color = amountColor),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    prefix = { Text("₹", style = AmountLargeStyle.copy(color = amountColor)) },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = amountColor,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(Modifier.height(8.dp))
                // Type toggle
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isDebit,
                        onClick = {
                            editType = TransactionType.DEBIT
                            transaction.correctedType = TransactionType.DEBIT
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        label = { Text("Withdrawal", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Filled.ArrowUpward, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = DebitRed.copy(alpha = 0.15f),
                            selectedLabelColor = DebitRed,
                            selectedLeadingIconColor = DebitRed
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                    FilterChip(
                        selected = !isDebit,
                        onClick = {
                            editType = TransactionType.CREDIT
                            transaction.correctedType = TransactionType.CREDIT
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        label = { Text("Deposit", fontWeight = FontWeight.SemiBold) },
                        leadingIcon = { Icon(Icons.Filled.ArrowDownward, null, Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CreditGreen.copy(alpha = 0.15f),
                            selectedLabelColor = CreditGreen,
                            selectedLeadingIconColor = CreditGreen
                        ),
                        shape = RoundedCornerShape(20.dp)
                    )
                }
            }

            // ─── Description ───
            OutlinedTextField(
                value = editDescription,
                onValueChange = { editDescription = it; transaction.description = it },
                label = { Text("Description") },
                leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(20.dp)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // ─── Account Selectors ───
            if (fireflyData.hasSynced) {
                AccountSelector(
                    accounts = fireflyData.assetAccounts,
                    selectedAccount = selectedSourceAccount,
                    label = "Source Account",
                    onAccountSelected = { acc ->
                        selectedSourceAccount = acc
                        transaction.sourceAccountId = acc?.id
                        transaction.sourceAccountName = acc?.name
                    }
                )
                AccountSelector(
                    accounts = fireflyData.expenseAccounts + fireflyData.revenueAccounts,
                    selectedAccount = selectedDestAccount,
                    label = "Destination",
                    onAccountSelected = { acc ->
                        selectedDestAccount = acc
                        transaction.destinationAccountId = acc?.id
                        transaction.destinationAccountName = acc?.name
                    }
                )
            }

            // ─── Category ───
            if (fireflyData.hasSynced && fireflyData.categories.isNotEmpty()) {
                CategorySelector(
                    categories = fireflyData.categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = { cat ->
                        selectedCategory = cat
                        transaction.categoryName = cat
                    }
                )
            }

            // ─── Budget ───
            if (fireflyData.hasSynced && fireflyData.budgets.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showBudgetPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AccountBalanceWallet, null, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Budget", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(selectedBudget ?: "Select budget", style = MaterialTheme.typography.bodyMedium, fontWeight = if (selectedBudget != null) FontWeight.Medium else FontWeight.Normal)
                        }
                        Icon(Icons.Filled.ChevronRight, "Select", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // ─── Tags ───
            if (fireflyData.hasSynced && fireflyData.tags.isNotEmpty()) {
                Column {
                    Text("Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        selectedTags.forEach { tag ->
                            InputChip(
                                selected = true, onClick = {
                                    selectedTags = selectedTags - tag
                                    transaction.selectedTags.remove(tag)
                                },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = { Icon(Icons.Default.Close, "Remove", Modifier.size(14.dp)) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        AssistChip(
                            onClick = { showTagPicker = true },
                            label = { Text("Add", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Add, null, Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // ─── Raw SMS ───
            Column {
                Row(Modifier.fillMaxWidth().clickable { showRawSms = !showRawSms }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (showRawSms) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text("Raw SMS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showRawSms) {
                    Text(transaction.rawMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 4.dp))
                } else {
                    Text(transaction.rawMessage, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp))
                }
            }

            // ─── Save CTA ───
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSave()
                },
                enabled = transaction.status != SendStatus.SENDING && transaction.status != SendStatus.SENT,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (transaction.status) {
                        SendStatus.SENT -> SuccessGreen
                        SendStatus.FAILED -> ErrorCrimson
                        else -> Primary
                    }
                )
            ) {
                Icon(
                    when (transaction.status) {
                        SendStatus.SENT -> Icons.Filled.CheckCircle
                        SendStatus.SENDING -> Icons.Filled.Sync
                        else -> Icons.Filled.Save
                    }, null, Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    when (transaction.status) {
                        SendStatus.PENDING -> "Save Transaction"
                        SendStatus.SENDING -> "Saving..."
                        SendStatus.SENT -> "Saved ✓"
                        SendStatus.FAILED -> "Retry"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // Tag picker dialog
    if (showTagPicker) {
        AlertDialog(
            onDismissRequest = { showTagPicker = false },
            title = { Text("Select Tags") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    fireflyData.tags.forEach { tag ->
                        val checked = tag.name in selectedTags
                        Row(Modifier.fillMaxWidth().clickable {
                            selectedTags = if (checked) selectedTags - tag.name else selectedTags + tag.name
                            transaction.selectedTags.clear(); transaction.selectedTags.addAll(selectedTags)
                        }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = checked, onCheckedChange = {
                                selectedTags = if (it) selectedTags + tag.name else selectedTags - tag.name
                                transaction.selectedTags.clear(); transaction.selectedTags.addAll(selectedTags)
                            })
                            Spacer(Modifier.width(8.dp))
                            Text(tag.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTagPicker = false }) { Text("Done") } }
        )
    }

    // Budget picker dialog
    if (showBudgetPicker) {
        AlertDialog(
            onDismissRequest = { showBudgetPicker = false },
            title = { Text("Select Budget") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ListItem(
                        headlineContent = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable {
                            selectedBudget = null; selectedBudgetId = null
                            transaction.budgetId = null; transaction.budgetName = null
                            showBudgetPicker = false
                        }
                    )
                    fireflyData.budgets.forEach { budget ->
                        ListItem(
                            headlineContent = { Text(budget.name, fontWeight = if (selectedBudget == budget.name) FontWeight.SemiBold else FontWeight.Normal) },
                            trailingContent = { if (selectedBudget == budget.name) Icon(Icons.Filled.CheckCircle, null, tint = Primary, modifier = Modifier.size(20.dp)) },
                            modifier = Modifier.clickable {
                                selectedBudget = budget.name; selectedBudgetId = budget.id
                                transaction.budgetId = budget.id; transaction.budgetName = budget.name
                                showBudgetPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showBudgetPicker = false }) { Text("Done") } }
        )
    }
}
