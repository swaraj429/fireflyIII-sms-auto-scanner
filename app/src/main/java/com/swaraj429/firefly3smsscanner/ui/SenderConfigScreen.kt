package com.swaraj429.firefly3smsscanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swaraj429.firefly3smsscanner.model.SenderConfig
import com.swaraj429.firefly3smsscanner.viewmodel.SenderConfigViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderConfigScreen(
    viewModel: SenderConfigViewModel,
    availableSenders: List<String>,
    onNavigateBack: () -> Unit
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    var showDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<SenderConfig?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sender Mappings") },
                navigationIcon = {
                    TextButton(onClick = onNavigateBack) {
                        Text("Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingConfig = null
                showDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Mapping")
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(configs) { config ->
                SenderConfigItem(
                    config = config,
                    onEdit = {
                        editingConfig = config
                        showDialog = true
                    },
                    onDelete = { viewModel.deleteConfig(config) },
                    onToggleActive = { viewModel.toggleConfigActive(config) }
                )
            }
        }
    }

    if (showDialog) {
        SenderConfigDialog(
            initialConfig = editingConfig,
            availableSenders = availableSenders,
            onDismiss = { showDialog = false },
            onSave = {
                viewModel.saveConfig(it)
                showDialog = false
            }
        )
    }
}

@Composable
fun SenderConfigItem(
    config: SenderConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleActive: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = config.senderPattern, style = MaterialTheme.typography.titleMedium)
                Text(text = "Account ID: ${config.accountId}", style = MaterialTheme.typography.bodyMedium)
            }
            Switch(
                checked = config.isActive,
                onCheckedChange = { onToggleActive() }
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SenderConfigDialog(
    initialConfig: SenderConfig?,
    availableSenders: List<String>,
    onDismiss: () -> Unit,
    onSave: (SenderConfig) -> Unit
) {
    var senderPattern by remember { mutableStateOf(initialConfig?.senderPattern ?: "") }
    var accountId by remember { mutableStateOf(initialConfig?.accountId ?: "") }
    
    var senderExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialConfig == null) "Add Mapping" else "Edit Mapping") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExposedDropdownMenuBox(
                    expanded = senderExpanded,
                    onExpandedChange = { senderExpanded = it }
                ) {
                    OutlinedTextField(
                        value = senderPattern,
                        onValueChange = { senderPattern = it },
                        label = { Text("Sender Name (or Pattern)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = senderExpanded) }
                    )
                    
                    val filteredSenders = availableSenders.filter { 
                        senderPattern.isBlank() || it.contains(senderPattern, ignoreCase = true) 
                    }
                    if (filteredSenders.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = senderExpanded,
                            onDismissRequest = { senderExpanded = false }
                        ) {
                            filteredSenders.forEach { sender ->
                                DropdownMenuItem(
                                    text = { Text(sender) },
                                    onClick = {
                                        senderPattern = sender
                                        senderExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = accountId,
                    onValueChange = { accountId = it },
                    label = { Text("Firefly Account ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val config = initialConfig?.copy(
                        senderPattern = senderPattern,
                        accountId = accountId
                    ) ?: SenderConfig(
                        senderPattern = senderPattern,
                        accountId = accountId,
                        transactionType = "withdrawal", // Default since UI is hidden
                        category = "",
                        tags = emptyList(),
                        descriptionTemplate = "",
                        currency = ""
                    )
                    onSave(config)
                },
                enabled = senderPattern.isNotBlank() && accountId.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
