package com.swaraj429.firefly3smsscanner.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.swaraj429.firefly3smsscanner.model.SmsMessage
import com.swaraj429.firefly3smsscanner.model.TransactionType
import com.swaraj429.firefly3smsscanner.ui.components.SMSCard
import com.swaraj429.firefly3smsscanner.ui.components.SmsCardInfo
import com.swaraj429.firefly3smsscanner.ui.theme.*
import com.swaraj429.firefly3smsscanner.viewmodel.FireflyDataViewModel
import com.swaraj429.firefly3smsscanner.viewmodel.SmsViewModel
import java.util.Calendar

/**
 * Redesigned SMS screen with smart cards, filter chips, and quick-scan.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(
    viewModel: SmsViewModel,
    fireflyDataViewModel: FireflyDataViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateToParsed: () -> Unit
) {
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedRange by remember { mutableStateOf("7 Days") }
    var selectedSms by remember { mutableStateOf<SmsMessage?>(null) }

    // Date picker states
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker by remember { mutableStateOf(false) }

    val filters = listOf("All", "Debits", "Credits", "UPI")
    val ranges = listOf("Today" to 0, "3 Days" to 3, "7 Days" to 7, "30 Days" to 30, "90 Days" to 90)

    val filteredMessages = viewModel.smsMessages.filter { sms ->
        when (selectedFilter) {
            "Debits" -> SmsCardInfo.extract(sms).type == TransactionType.DEBIT
            "Credits" -> SmsCardInfo.extract(sms).type == TransactionType.CREDIT
            "UPI" -> sms.body.lowercase().contains("upi")
            else -> true
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ─── Header ───
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("SMS Scanner", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                if (viewModel.smsMessages.isNotEmpty()) {
                    Text("${viewModel.smsMessages.size} messages loaded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Sample data button
                IconButton(onClick = { viewModel.loadSampleSms() }) {
                    Icon(Icons.Filled.Science, "Sample data", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Scan button
                FilledTonalButton(
                    onClick = { if (hasPermission) viewModel.loadSmsByDateRange() else onRequestPermission() },
                    enabled = !viewModel.isLoading,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (viewModel.isLoading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Radar, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(if (hasPermission) "Scan" else "Grant SMS", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ─── Date range chips ───
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(ranges) { (label, days) ->
                FilterChip(
                    selected = selectedRange == label,
                    onClick = {
                        selectedRange = label
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
                        if (hasPermission) viewModel.loadSmsByDateRange()
                    },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(20.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.15f),
                        selectedLabelColor = Primary
                    )
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ─── Type filter chips ───
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(filters) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter, style = MaterialTheme.typography.labelSmall) },
                    shape = RoundedCornerShape(20.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // ─── Parse & Navigate ───
        if (viewModel.smsMessages.isNotEmpty()) {
            Button(
                onClick = {
                    val accounts = fireflyDataViewModel.assetAccounts + fireflyDataViewModel.expenseAccounts + fireflyDataViewModel.revenueAccounts
                    viewModel.parseMessages(accounts)
                    onNavigateToParsed()
                },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Parse & View Transactions", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(Modifier.height(8.dp))

        // ─── Sample data indicator ───
        if (viewModel.usingSampleData) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                color = WarningAmber.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Science, null, Modifier.size(14.dp), WarningAmber)
                    Spacer(Modifier.width(6.dp))
                    Text("Using sample data", style = MaterialTheme.typography.labelSmall, color = WarningAmber)
                }
            }
            Spacer(Modifier.height(4.dp))
        }

        // ─── SMS List ───
        if (viewModel.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
        } else if (filteredMessages.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Sms, null, Modifier.size(64.dp), MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("No SMS messages", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Text("Select a date range and tap Scan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredMessages) { sms ->
                    SMSCard(sms = sms, onClick = { selectedSms = sms })
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    // ─── Full SMS dialog ───
    selectedSms?.let { sms ->
        AlertDialog(
            onDismissRequest = { selectedSms = null },
            title = { Text("From: ${sms.sender}") },
            text = {
                Column {
                    Text(sms.dateString, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text(sms.body, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { selectedSms = null }) { Text("Close") } }
        )
    }

    // Date pickers
    if (showFromPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = viewModel.fromDate)
        DatePickerDialog(
            onDismissRequest = { showFromPicker = false },
            confirmButton = { TextButton(onClick = { pickerState.selectedDateMillis?.let { viewModel.fromDate = it }; showFromPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showFromPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
    if (showToPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = viewModel.toDate)
        DatePickerDialog(
            onDismissRequest = { showToPicker = false },
            confirmButton = { TextButton(onClick = { pickerState.selectedDateMillis?.let { viewModel.toDate = it }; showToPicker = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showToPicker = false }) { Text("Cancel") } }
        ) { DatePicker(state = pickerState) }
    }
}
