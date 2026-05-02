package com.swaraj429.firefly3smsscanner.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.ui.theme.*

/**
 * Represents a parsing rule: IF SMS contains [keyword] THEN apply [category, account, tags].
 */
data class ParsingRule(
    val id: String = java.util.UUID.randomUUID().toString(),
    var keyword: String = "",
    var category: String = "",
    var accountName: String = "",
    var tags: List<String> = emptyList(),
    var isEnabled: Boolean = true
)

/**
 * Rule Engine UI for creating IF/THEN rules that auto-categorize transactions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesScreen() {
    // In-memory rules for now (would persist via SharedPreferences/Room in production)
    var rules by remember { mutableStateOf(listOf(
        ParsingRule(keyword = "SWIGGY", category = "Food & Dining", tags = listOf("food-delivery")),
        ParsingRule(keyword = "AMAZON", category = "Shopping", tags = listOf("online")),
        ParsingRule(keyword = "UBER", category = "Transport", tags = listOf("ride")),
        ParsingRule(keyword = "NETFLIX", category = "Entertainment", tags = listOf("subscription")),
    )) }
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
                Text("${rules.size} rules active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                Text("Rules auto-assign category, tags & account when SMS matches a keyword", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                items(rules) { rule ->
                    RuleCard(
                        rule = rule,
                        onEdit = { editingRule = rule; showEditor = true },
                        onDelete = { rules = rules.filter { it.id != rule.id } },
                        onToggle = { enabled ->
                            rules = rules.map { if (it.id == rule.id) it.copy(isEnabled = enabled) else it }
                        }
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
            onSave = { savedRule ->
                rules = if (rules.any { it.id == savedRule.id }) {
                    rules.map { if (it.id == savedRule.id) savedRule else it }
                } else {
                    rules + savedRule
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
                    if (rule.category.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Category, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(rule.category, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (rule.accountName.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AccountBalance, null, Modifier.size(14.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(4.dp))
                            Text(rule.accountName, style = MaterialTheme.typography.bodySmall)
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

@Composable
private fun RuleEditorDialog(
    rule: ParsingRule,
    onSave: (ParsingRule) -> Unit,
    onDismiss: () -> Unit
) {
    var keyword by remember { mutableStateOf(rule.keyword) }
    var category by remember { mutableStateOf(rule.category) }
    var accountName by remember { mutableStateOf(rule.accountName) }
    var tagsText by remember { mutableStateOf(rule.tags.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (rule.keyword.isBlank()) "New Rule" else "Edit Rule", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = keyword, onValueChange = { keyword = it },
                    label = { Text("IF SMS contains") }, placeholder = { Text("e.g., SWIGGY") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = category, onValueChange = { category = it },
                    label = { Text("Category") }, placeholder = { Text("e.g., Food & Dining") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = accountName, onValueChange = { accountName = it },
                    label = { Text("Account (optional)") }, placeholder = { Text("e.g., HDFC Savings") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagsText, onValueChange = { tagsText = it },
                    label = { Text("Tags (comma-separated)") }, placeholder = { Text("e.g., food, delivery") },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(rule.copy(
                        keyword = keyword.trim(),
                        category = category.trim(),
                        accountName = accountName.trim(),
                        tags = tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    ))
                },
                enabled = keyword.isNotBlank(),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
