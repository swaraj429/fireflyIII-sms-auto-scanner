package com.swaraj429.firefly3smsscanner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.swaraj429.firefly3smsscanner.model.ParsingRule
import com.swaraj429.firefly3smsscanner.ui.theme.*
import com.swaraj429.firefly3smsscanner.viewmodel.FireflyDataViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.RulesViewModel

/**
 * Rule Engine UI for creating IF/THEN rules that auto-categorize transactions.
 * Now backed by RulesViewModel (persisted) and uses Firefly data for dropdowns.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen(
    rulesViewModel: RulesViewModel,
    fireflyDataViewModel: FireflyDataViewModel
) {
    val rules = rulesViewModel.rules
    var showEditor by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<ParsingRule?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Header ───
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Smart Rules", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "${rules.count { it.isEnabled }} of ${rules.size} rules active",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalButton(
                onClick = { editingRule = ParsingRule(); showEditor = true },
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add Rule", fontWeight = FontWeight.SemiBold)
            }
        }

        // ─── Info card ───
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Primary.copy(alpha = 0.08f))
        ) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp), Primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    "Rules auto-fill category, destination account & tags when editing a transaction whose SMS matches a keyword",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── Rules List ───
        if (rules.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Rule, null, Modifier.size(64.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("No rules yet", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Add rules to automate categorization", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(rules, key = { it.id }) { rule ->
                    RuleCard(
                        rule = rule,
                        onEdit = { editingRule = rule; showEditor = true },
                        onDelete = { rulesViewModel.deleteRule(rule.id) },
                        onToggle = { enabled -> rulesViewModel.toggleRule(rule.id, enabled) }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // ─── Rule Editor Dialog ───
    if (showEditor && editingRule != null) {
        RuleEditorDialog(
            rule = editingRule!!,
            fireflyData = fireflyDataViewModel,
            onSave = { savedRule ->
                if (rules.any { it.id == savedRule.id }) {
                    rulesViewModel.updateRule(savedRule)
                } else {
                    rulesViewModel.addRule(savedRule)
                }
                showEditor = false; editingRule = null
            },
            onDismiss = { showEditor = false; editingRule = null }
        )
    }
}

@Composable
private fun RuleCard(
    rule: ParsingRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (rule.isEnabled) MaterialTheme.colorScheme.surface
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            // IF row
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Primary.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text("IF", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("SMS contains ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(6.dp)) {
                    Text("\"${rule.keyword}\"", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // THEN row
            Row(verticalAlignment = Alignment.Top) {
                Surface(color = SuccessGreen.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
                    Text("THEN", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = SuccessGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                }
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (rule.categoryName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Category, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(rule.categoryName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (rule.destinationAccountName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountBalance, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(rule.destinationAccountName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (rule.tags.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.LocalOffer, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(rule.tags.joinToString(", "), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // Actions row
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = rule.isEnabled, onCheckedChange = onToggle, modifier = Modifier.height(24.dp))
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, "Delete", Modifier.size(18.dp), ErrorCrimson)
                }
            }
        }
    }
}

/**
 * Rule editor dialog with Firefly data-backed dropdowns for
 * category, destination account, and tags.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleEditorDialog(
    rule: ParsingRule,
    fireflyData: FireflyDataViewModel,
    onSave: (ParsingRule) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf(rule.keyword) }
    var selectedCategory by remember { mutableStateOf(rule.categoryName) }
    var selectedDestId by remember { mutableStateOf(rule.destinationAccountId) }
    var selectedDestName by remember { mutableStateOf(rule.destinationAccountName) }
    var selectedTags by remember { mutableStateOf(rule.tags.toList()) }

    // Dropdown expanded state
    var categoryExpanded by remember { mutableStateOf(false) }
    var accountExpanded by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule.keyword.isBlank()) "New Rule" else "Edit Rule", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Keyword ──
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it },
                    label = { Text("IF SMS contains") }, placeholder = { Text("e.g., SWIGGY") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(18.dp)) },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // ── Category dropdown ──
                Text("THEN assign:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = SuccessGreen)

                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = { selectedCategory = it },
                        readOnly = fireflyData.categories.isNotEmpty(),
                        label = { Text("Category") },
                        leadingIcon = { Icon(Icons.Filled.Category, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (fireflyData.categories.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    if (fireflyData.categories.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = categoryExpanded,
                            onDismissRequest = { categoryExpanded = false }
                        ) {
                            // "None" option
                            DropdownMenuItem(
                                text = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { selectedCategory = ""; categoryExpanded = false }
                            )
                            fireflyData.categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            cat.name,
                                            fontWeight = if (cat.name == selectedCategory) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = { selectedCategory = cat.name; categoryExpanded = false },
                                    trailingIcon = {
                                        if (cat.name == selectedCategory) {
                                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), Primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Destination Account dropdown ──
                ExposedDropdownMenuBox(
                    expanded = accountExpanded,
                    onExpandedChange = { accountExpanded = !accountExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedDestName,
                        onValueChange = { selectedDestName = it },
                        readOnly = fireflyData.expenseAccounts.isNotEmpty(),
                        label = { Text("Destination Account") },
                        leadingIcon = { Icon(Icons.Filled.AccountBalance, null, Modifier.size(18.dp)) },
                        trailingIcon = {
                            if (fireflyData.expenseAccounts.isNotEmpty()) {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountExpanded)
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().menuAnchor()
                    )
                    if (fireflyData.expenseAccounts.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = accountExpanded,
                            onDismissRequest = { accountExpanded = false }
                        ) {
                            // "None" option
                            DropdownMenuItem(
                                text = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { selectedDestId = null; selectedDestName = ""; accountExpanded = false }
                            )
                            fireflyData.expenseAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            acc.name,
                                            fontWeight = if (acc.id == selectedDestId) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    onClick = {
                                        selectedDestId = acc.id
                                        selectedDestName = acc.name
                                        accountExpanded = false
                                    },
                                    trailingIcon = {
                                        if (acc.id == selectedDestId) {
                                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp), Primary)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Tags ──
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { showTagPicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocalOffer, null, Modifier.size(18.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tags", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (selectedTags.isEmpty()) "Select tags" else selectedTags.joinToString(", "),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedTags.isNotEmpty()) FontWeight.Medium else FontWeight.Normal
                            )
                        }
                        Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp))
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(rule.copy(
                        keyword = keyword.trim(),
                        categoryName = selectedCategory.trim(),
                        destinationAccountId = selectedDestId,
                        destinationAccountName = selectedDestName.trim(),
                        tags = selectedTags
                    ))
                },
                enabled = keyword.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )

    // Tag picker sub-dialog
    if (showTagPicker) {
        AlertDialog(
            onDismissRequest = { showTagPicker = false },
            title = { Text("Select Tags") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (fireflyData.tags.isEmpty()) {
                        Text("No tags synced. Sync Firefly data first.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        fireflyData.tags.forEach { tag ->
                            val checked = tag.name in selectedTags
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    selectedTags = if (checked) selectedTags - tag.name else selectedTags + tag.name
                                }.padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = checked, onCheckedChange = {
                                    selectedTags = if (it) selectedTags + tag.name else selectedTags - tag.name
                                })
                                Spacer(Modifier.width(8.dp))
                                Text(tag.name)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTagPicker = false }) { Text("Done") } }
        )
    }
}
