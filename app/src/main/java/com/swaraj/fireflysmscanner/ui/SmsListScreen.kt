package com.swaraj.fireflysmscanner.ui

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
import com.swaraj.fireflysmscanner.model.SmsMessage
import com.swaraj.fireflysmscanner.viewmodel.SmsViewModel

@Composable
fun SmsListScreen(
    viewModel: SmsViewModel,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onNavigateToParsed: () -> Unit
) {
    var selectedSms by remember { mutableStateOf<SmsMessage?>(null) }

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

        // Action buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (hasPermission) {
                        viewModel.loadSms()
                    } else {
                        onRequestPermission()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !viewModel.isLoading
            ) {
                Text(if (hasPermission) "📥 Scan Last 50 SMS" else "🔑 Grant SMS Permission")
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
                    viewModel.parseMessages()
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
                    text = "No messages loaded.\nTap 'Scan' or 'Sample Data' to start.",
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
