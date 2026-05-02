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
import com.swaraj429.firefly3smsscanner.model.FireflyCategory

/**
 * Searchable category selector as a tappable card that opens a bottom sheet picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySelector(
    categories: List<FireflyCategory>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
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
            Icon(Icons.Filled.Category, null, Modifier.size(20.dp), MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    selectedCategory ?: "Select category",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedCategory != null) FontWeight.Medium else FontWeight.Normal,
                    color = if (selectedCategory != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                Text("Select Category", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it },
                    placeholder = { Text("Search categories...") },
                    leadingIcon = { Icon(Icons.Filled.Search, null, Modifier.size(20.dp)) },
                    singleLine = true, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                ListItem(
                    headlineContent = { Text("— None —", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onCategorySelected(null); showSheet = false; searchQuery = "" }
                )
                val filtered = categories.filter { searchQuery.isBlank() || it.name.contains(searchQuery, true) }
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(filtered) { cat ->
                        ListItem(
                            headlineContent = { Text(cat.name, fontWeight = if (selectedCategory == cat.name) FontWeight.SemiBold else FontWeight.Normal) },
                            trailingContent = {
                                if (selectedCategory == cat.name) Icon(Icons.Filled.CheckCircle, "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            },
                            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { onCategorySelected(cat.name); showSheet = false; searchQuery = "" }
                        )
                    }
                }
            }
        }
    }
}
