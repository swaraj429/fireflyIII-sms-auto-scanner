package com.swaraj429.firefly3smsscanner.ui.sheets

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.model.ParsedTransaction
import com.swaraj429.firefly3smsscanner.model.ParsingRule
import com.swaraj429.firefly3smsscanner.model.TransactionType
import com.swaraj429.firefly3smsscanner.parser.RuleEngine
import com.swaraj429.firefly3smsscanner.ui.theme.Primary
import com.swaraj429.firefly3smsscanner.ui.theme.SuccessGreen
import com.swaraj429.firefly3smsscanner.viewmodel.FireflyDataViewModel

/**
 * Modal Bottom Sheet for creating and editing transaction automation rules.
 * Provides rich live preview, template helpers, category/budget/account selectors,
 * tag management, and an auto-send toggle.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RuleEditorSheet(
    rule: ParsingRule,
    fireflyData: FireflyDataViewModel,
    onSave: (ParsingRule) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var keyword by remember { mutableStateOf(rule.keyword) }
    var descriptionTemplate by remember { mutableStateOf(rule.descriptionTemplate) }
    var autoSendToFirefly by remember { mutableStateOf(rule.autoSendToFirefly) }
    var isEnabled by remember { mutableStateOf(rule.isEnabled) }
    var selectedCategory by remember { mutableStateOf(rule.categoryName) }
    var selectedDestId by remember { mutableStateOf(rule.destinationAccountId) }
    var selectedDestName by remember { mutableStateOf(rule.destinationAccountName) }
    var selectedBudgetId by remember { mutableStateOf(rule.budgetId) }
    var selectedBudgetName by remember { mutableStateOf(rule.budgetName) }
    var selectedTags by remember { mutableStateOf(rule.tags.toList()) }

    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }
    var showBudgetPicker by remember { mutableStateOf(false) }

    // Live preview calculation with realistic mock transaction
    val sampleTxn = remember(keyword, selectedDestName, selectedBudgetName) {
        ParsedTransaction(
            amount = 450.0,
            type = TransactionType.WITHDRAWAL,
            rawMessage = "Spent INR 450.00 at ${keyword.ifBlank { "Merchant" }} on Card XX1234",
            vendor = keyword.ifBlank { "Merchant" },
            destinationAccountName = selectedDestName.ifBlank { null },
            budgetName = selectedBudgetName.ifBlank { null }
        )
    }
    val sampleRule = remember(keyword, selectedCategory, selectedDestName, selectedBudgetName) {
        rule.copy(
            keyword = keyword,
            categoryName = selectedCategory,
            destinationAccountName = selectedDestName,
            budgetId = selectedBudgetId,
            budgetName = selectedBudgetName
        )
    }
    val livePreview = remember(descriptionTemplate, keyword, selectedCategory, selectedDestName, selectedBudgetName) {
        if (descriptionTemplate.isNotBlank()) {
            RuleEngine.interpolateTemplate(descriptionTemplate, sampleTxn, sampleRule)
        } else {
            ""
        }
    }

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
            // ─── Section 1: Sheet Header ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (rule.keyword.isBlank()) "New Rule" else "Edit Rule",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Define triggers and automated actions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (isEnabled) "Active" else "Inactive",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it }
                    )
                }
            }

            // ─── Section 2: Condition (IF) ───
            Surface(
                color = Primary.copy(alpha = 0.06f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "IF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SMS message contains keyword",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedTextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = { Text("Keyword") },
                        placeholder = { Text("e.g., SWIGGY, AMAZON, IRCTC") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ─── Section 3: THEN Divider ───
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f))
                Surface(
                    color = SuccessGreen.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "THEN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = SuccessGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                HorizontalDivider(modifier = Modifier.weight(1f))
            }

            // ─── Section 4: Description Template ───
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Description Template",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = descriptionTemplate,
                    onValueChange = { descriptionTemplate = it },
                    label = { Text("Template (Optional)") },
                    placeholder = { Text("e.g. {vendor} · ₹{amount}") },
                    leadingIcon = { Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 1,
                    maxLines = 3
                )

                // Horizontally scrollable chip row for variables
                val templateVariables = listOf(
                    "{vendor}", "{amount}", "{account}", "{category}", "{budget}", "{date}", "{time}", "{mode}", "{keyword}"
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(templateVariables) { variable ->
                        SuggestionChip(
                            onClick = {
                                descriptionTemplate = if (descriptionTemplate.isBlank()) {
                                    variable
                                } else {
                                    "$descriptionTemplate $variable"
                                }
                            },
                            label = { Text(variable, style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }

                // Dynamic live preview card
                AnimatedVisibility(visible = livePreview.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(18.dp), tint = Primary)
                            Column {
                                Text(
                                    text = "Live Preview:",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = livePreview,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ─── Section 5: Auto-Send Toggle ───
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (autoSendToFirefly) Primary.copy(alpha = 0.10f)
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
                border = if (autoSendToFirefly) BorderStroke(1.dp, Primary.copy(alpha = 0.30f)) else null
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = if (autoSendToFirefly) Primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                text = "Auto-send to Firefly",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (autoSendToFirefly) "Transaction will be posted automatically"
                                else "You'll review before sending",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = autoSendToFirefly,
                        onCheckedChange = { autoSendToFirefly = it }
                    )
                }
            }

            // ─── Section 6: Category Selector ───
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (fireflyData.categories.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = categoryExpanded,
                        onExpandedChange = { categoryExpanded = !categoryExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedCategory,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Category") },
                            placeholder = { Text("Select category (optional)") },
                            leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = {
                                    selectedCategory = ""
                                    categoryExpanded = false
                                }
                            )
                            fireflyData.categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            cat.name,
                                            fontWeight = if (selectedCategory == cat.name) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    trailingIcon = {
                                        if (selectedCategory == cat.name) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Primary)
                                        }
                                    },
                                    onClick = {
                                        selectedCategory = cat.name
                                        categoryExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = { selectedCategory = it },
                        label = { Text("Category") },
                        placeholder = { Text("e.g., Groceries, Food") },
                        leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ─── Section 7: Budget Selector ───
            if (fireflyData.budgets.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Budget", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showBudgetPicker = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Budget",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = if (selectedBudgetName.isNotBlank()) selectedBudgetName else "Select budget (optional)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (selectedBudgetName.isNotBlank()) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                            if (selectedBudgetName.isNotBlank()) {
                                IconButton(
                                    modifier = Modifier.size(32.dp),
                                    onClick = {
                                        selectedBudgetId = null
                                        selectedBudgetName = ""
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Clear budget",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = "Select",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ─── Section 8: Destination Account Selector ───
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Destination Account", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (fireflyData.expenseAccounts.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = accountExpanded,
                        onExpandedChange = { accountExpanded = !accountExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedDestName,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Destination Account") },
                            placeholder = { Text("Select destination account (optional)") },
                            leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded) },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = {
                                    selectedDestId = null
                                    selectedDestName = ""
                                    accountExpanded = false
                                }
                            )
                            fireflyData.expenseAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            acc.name,
                                            fontWeight = if (acc.id == selectedDestId) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    trailingIcon = {
                                        if (acc.id == selectedDestId) {
                                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp), tint = Primary)
                                        }
                                    },
                                    onClick = {
                                        selectedDestId = acc.id
                                        selectedDestName = acc.name
                                        accountExpanded = false
                                    }
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = selectedDestName,
                        onValueChange = { selectedDestName = it },
                        label = { Text("Destination Account") },
                        placeholder = { Text("e.g., Swiggy, Amazon") },
                        leadingIcon = { Icon(Icons.Filled.AccountBalance, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ─── Section 9: Tags ───
            if (fireflyData.tags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Tags", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        selectedTags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { selectedTags = selectedTags - tag },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                trailingIcon = { Icon(Icons.Default.Close, contentDescription = "Remove tag", modifier = Modifier.size(14.dp)) },
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        AssistChip(
                            onClick = { showTagPicker = true },
                            label = { Text("Add Tag", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp)) },
                            shape = RoundedCornerShape(8.dp)
                        )
                    }
                }
            }

            // ─── Section 10: Save CTA Button ───
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = {
                    onSave(
                        rule.copy(
                            keyword = keyword.trim(),
                            descriptionTemplate = descriptionTemplate.trim(),
                            autoSendToFirefly = autoSendToFirefly,
                            isEnabled = isEnabled,
                            categoryName = selectedCategory.trim(),
                            destinationAccountId = selectedDestId,
                            destinationAccountName = selectedDestName.trim(),
                            budgetId = selectedBudgetId,
                            budgetName = selectedBudgetName.trim(),
                            tags = selectedTags
                        )
                    )
                },
                enabled = keyword.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (rule.keyword.isBlank()) "Create Rule" else "Save Changes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }

    // ─── Sub-Dialog: Budget Picker ───
    if (showBudgetPicker) {
        AlertDialog(
            onDismissRequest = { showBudgetPicker = false },
            title = { Text("Select Budget", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ListItem(
                        headlineContent = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        modifier = Modifier.clickable {
                            selectedBudgetId = null
                            selectedBudgetName = ""
                            showBudgetPicker = false
                        }
                    )
                    fireflyData.budgets.forEach { budget ->
                        ListItem(
                            headlineContent = {
                                Text(
                                    budget.name,
                                    fontWeight = if (selectedBudgetName == budget.name) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            trailingContent = {
                                if (selectedBudgetName == budget.name) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp))
                                }
                            },
                            modifier = Modifier.clickable {
                                selectedBudgetId = budget.id
                                selectedBudgetName = budget.name
                                showBudgetPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBudgetPicker = false }) {
                    Text("Done")
                }
            }
        )
    }

    // ─── Sub-Dialog: Tag Picker ───
    if (showTagPicker) {
        AlertDialog(
            onDismissRequest = { showTagPicker = false },
            title = { Text("Select Tags", fontWeight = FontWeight.SemiBold) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (fireflyData.tags.isEmpty()) {
                        Text("No tags synced. Sync Firefly data first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        fireflyData.tags.forEach { tag ->
                            val checked = tag.name in selectedTags
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedTags = if (checked) selectedTags - tag.name else selectedTags + tag.name
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selectedTags = if (it) selectedTags + tag.name else selectedTags - tag.name
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(tag.name)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTagPicker = false }) {
                    Text("Done")
                }
            }
        )
    }
}
