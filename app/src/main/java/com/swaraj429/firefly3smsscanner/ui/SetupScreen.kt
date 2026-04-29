package com.swaraj429.firefly3smsscanner.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.swaraj429.firefly3smsscanner.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: SetupViewModel) {
    var showToken by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val hasReceiveSms = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECEIVE_SMS
    ) == PackageManager.PERMISSION_GRANTED
    val hasNotifications = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else true
    val liveDetectionActive = hasReceiveSms && hasNotifications

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🔧 Firefly III Setup",
            style = MaterialTheme.typography.headlineMedium
        )

        // Live detection status
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (liveDetectionActive)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (liveDetectionActive) Icons.Default.NotificationsActive else Icons.Default.Warning,
                    contentDescription = null
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (liveDetectionActive) "Live SMS Detection Active" else "Live Detection Inactive",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = if (liveDetectionActive)
                            "Transaction SMS will trigger instant notifications"
                        else {
                            val missing = buildList {
                                if (!hasReceiveSms) add("RECEIVE_SMS")
                                if (!hasNotifications) add("Notifications")
                            }.joinToString(", ")
                            "Missing: $missing"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (!liveDetectionActive) {
                    TextButton(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                        )
                    }) { Text("Grant") }
                }
            }
        }

        Text(
            text = "Configure your Firefly III connection",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        HorizontalDivider()

        // Base URL
        OutlinedTextField(
            value = viewModel.baseUrl,
            onValueChange = { viewModel.baseUrl = it },
            label = { Text("Base URL") },
            placeholder = { Text("https://firefly.example.com") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )

        // Access Token
        OutlinedTextField(
            value = viewModel.accessToken,
            onValueChange = { viewModel.accessToken = it },
            label = { Text("Personal Access Token") },
            placeholder = { Text("eyJ0eX...") },
            singleLine = true,
            visualTransformation = if (showToken)
                VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showToken = !showToken }) {
                    Icon(
                        if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        "Toggle visibility"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        // Account ID
        OutlinedTextField(
            value = viewModel.accountId,
            onValueChange = { viewModel.accountId = it },
            label = { Text("Default Account ID") },
            placeholder = { Text("1") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = { Text("Firefly asset account ID for transactions") },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        // Test Connection button
        Button(
            onClick = { viewModel.testConnection() },
            enabled = !viewModel.isTesting,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (viewModel.isTesting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (viewModel.isTesting) "Testing..." else "🔌 Test Connection")
        }

        // Save button
        OutlinedButton(
            onClick = { viewModel.saveConfig() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("💾 Save Config")
        }

        // Status
        if (viewModel.connectionStatus.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        viewModel.connectionStatus.startsWith("✅") ->
                            MaterialTheme.colorScheme.primaryContainer
                        viewModel.connectionStatus.startsWith("❌") ->
                            MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(
                    text = viewModel.connectionStatus,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
