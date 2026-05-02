package com.swaraj429.firefly3smsscanner.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.swaraj429.firefly3smsscanner.model.FireflyAccount
import com.swaraj429.firefly3smsscanner.parser.ConfidenceScore
import com.swaraj429.firefly3smsscanner.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSelector(
    accounts: List<FireflyAccount>,
    selectedAccount: FireflyAccount?,
    autoMatchedAccount: FireflyAccount? = null,
    autoMatchConfidence: ConfidenceScore? = null,
    label: String = "Account",
    onAccountSelected: (FireflyAccount?) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Card(
        modifier = modifier.fillMaxWidth().clickable { showSheet = true },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AccountBalance, null, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selectedAccount?.name ?: "Select account",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedAccount != null) FontWeight.Medium else FontWeight.Normal,
                    color = if (selectedAccount != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (selectedAccount == autoMatchedAccount && autoMatchConfidence != null) {
                ConfidenceBadge(autoMatchConfidence)
                Spacer(Modifier.width(8.dp))
            }
            Icon(Icons.Filled.ChevronRight, "Select", Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false; searchQuery = "" },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
                Text("Select $label", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search accounts...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(20.dp)) },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("— Default —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAccountSelected(null); showSheet = false; searchQuery = "" }
                )
                val filtered = accounts.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(filtered) { account ->
                        ListItem(
                            headlineContent = { Text(account.name, fontWeight = if (selectedAccount?.id == account.id) FontWeight.SemiBold else FontWeight.Normal) },
                            supportingContent = {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    account.accountNumber?.let { Text("••${it.takeLast(4)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    Text(account.type.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                }
                            },
                            trailingContent = {
                                if (selectedAccount?.id == account.id) Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            },
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onAccountSelected(account); showSheet = false; searchQuery = "" }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfidenceBadge(confidence: ConfidenceScore, modifier: Modifier = Modifier) {
    val (color, text) = when (confidence) {
        ConfidenceScore.HIGH -> ConfidenceHigh to "95%"
        ConfidenceScore.MEDIUM -> ConfidenceMedium to "70%"
        ConfidenceScore.LOW -> ConfidenceLow to "40%"
        ConfidenceScore.NONE -> DarkTextMuted to "—"
    }
    Surface(modifier = modifier, color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(6.dp)) {
        Text(text, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
    }
}
