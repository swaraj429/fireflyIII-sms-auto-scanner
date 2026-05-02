package com.swaraj429.firefly3smsscanner.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.model.SmsMessage
import com.swaraj429.firefly3smsscanner.viewmodel.SmsViewModel
import java.text.SimpleDateFormat
import java.util.*

import com.swaraj429.firefly3smsscanner.viewmodel.FireflyDataViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsListScreen(
    viewModel: SmsViewModel,
    fireflyDataViewModel: FireflyDataViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateToParsed: () -> Unit
) {
    var selectedSms by remember { mutableStateOf<SmsMessage?>(null) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    // Date picker states
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "📱 SMS Messages",
            style = MaterialTheme.typography.headlineMedium
        )

        // Date range selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "📅 Date Range",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // From date
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showFromPicker = true }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "From",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dateFormat.format(Date(viewModel.fromDate)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // To date
                    OutlinedCard(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showToPicker = true }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "To",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dateFormat.format(Date(viewModel.toDate)),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Quick range buttons
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        "Today" to 0,
                        "3 Days" to 3,
                        "7 Days" to 7,
                        "30 Days" to 30,
                        "90 Days" to 90
                    ).forEach { (label, days) ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val cal = Calendar.getInstance()
                                viewModel.toDate = cal.timeInMillis
                                if (days == 0) {
                                    cal.set(Calendar.HOUR_OF_DAY, 0)
                                    cal.set(Calendar.MINUTE, 0)
                                    cal.set(Calendar.SECOND, 0)
                                } else {
                                    cal.add(Calendar.DAY_OF_YEAR, -days)
                                }
                                viewModel.fromDate = cal.timeInMillis
                            },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (hasPermission) {
                        viewModel.loadSmsByDateRange()
                    } else {
                        onRequestPermission()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !viewModel.isLoading
            ) {
                Text(if (hasPermission) "📥 Scan SMS" else "🔑 Grant Permission")
            }

            OutlinedButton(
                onClick = { viewModel.loadSampleSms() },
                modifier = Modifier.weight(1f)
            ) {
                Text("🧪 Sample Data")
            }
        }

        // Parse + Navigate button
        if (viewModel.smsMessages.isNotEmpty()) {
            Button(
                onClick = {
                    val accounts = fireflyDataViewModel.assetAccounts + fireflyDataViewModel.expenseAccounts + fireflyDataViewModel.revenueAccounts
                    viewModel.parseMessages(accounts)
                    onNavigateToParsed()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("⚡ Parse & View Transactions →")
            }
        }

        // Status
        if (viewModel.statusMessage.isNotBlank()) {
            Text(
                text = viewModel.statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (viewModel.usingSampleData) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Text(
                    text = "📋 Using sample data for testing",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        HorizontalDivider()

        // SMS List
        if (viewModel.isLoading) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (viewModel.smsMessages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No messages loaded.\nSelect a date range and tap 'Scan SMS' to start.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(viewModel.smsMessages) { sms ->
                    SmsCard(sms = sms, onClick = { selectedSms = sms })
                }
            }
        }
    }

    // From date picker dialog
    if (showFromPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.fromDate
        )
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.fromDate = it }
                    showFromPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showFromPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // To date picker dialog
    if (showToPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = viewModel.toDate
        )
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { viewModel.toDate = it }
                    showToPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showToPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }

    // Full message dialog
    selectedSms?.let { sms ->
        AlertDialog(
            onDismissRequest = { selectedSms = null },
            title = { Text("From: ${sms.sender}") },
            text = {
                Column {
                    Text(
                        text = sms.dateString,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(text = sms.body, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedSms = null }) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun SmsCard(sms: SmsMessage, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = sms.sender,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = sms.dateString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = sms.body,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
