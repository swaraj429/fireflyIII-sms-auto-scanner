package com.swaraj.fireflysmscanner.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.swaraj.fireflysmscanner.viewmodel.SetupViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(viewModel: SetupViewModel) {
    var showToken by remember { mutableStateOf(false) }

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
