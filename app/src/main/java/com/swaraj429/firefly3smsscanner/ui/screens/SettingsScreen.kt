package com.swaraj429.firefly3smsscanner.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.swaraj429.firefly3smsscanner.ui.theme.*
import com.swaraj429.firefly3smsscanner.viewmodel.SetupViewModel

/**
 * Redesigned Settings screen with grouped sections.
 */
@Composable
fun SettingsScreen(viewModel: SetupViewModel) {
    var showToken by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val hasReceiveSms = ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
    val hasNotifications = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    } else true
    val liveActive = hasReceiveSms && hasNotifications

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // ─── Live Detection Status ───
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (liveActive) SuccessGreen.copy(alpha = 0.1f) else ErrorCrimson.copy(alpha = 0.1f)
            )
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (liveActive) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff,
                    null, Modifier.size(24.dp),
                    if (liveActive) SuccessGreen else ErrorCrimson
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        if (liveActive) "Live Detection Active" else "Live Detection Off",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        color = if (liveActive) SuccessGreen else ErrorCrimson
                    )
                    Text(
                        if (liveActive) "SMS transactions trigger instant notifications"
                        else {
                            val missing = buildList {
                                if (!hasReceiveSms) add("RECEIVE_SMS")
                                if (!hasNotifications) add("Notifications")
                            }.joinToString(", ")
                            "Missing: $missing"
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!liveActive) {
                    FilledTonalButton(onClick = {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        })
                    }, shape = RoundedCornerShape(10.dp)) { Text("Fix") }
                }
            }
        }

        // ─── Connection Section ───
        SectionHeader("Connection")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = viewModel.baseUrl, onValueChange = { viewModel.baseUrl = it },
                    label = { Text("Server URL") }, placeholder = { Text("https://firefly.example.com") },
                    leadingIcon = { Icon(Icons.Filled.Link, null, Modifier.size(20.dp)) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = viewModel.accessToken, onValueChange = { viewModel.accessToken = it },
                    label = { Text("Access Token") }, placeholder = { Text("eyJ0eX...") },
                    leadingIcon = { Icon(Icons.Filled.Key, null, Modifier.size(20.dp)) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showToken = !showToken }) {
                            Icon(if (showToken) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Toggle")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.testConnection() }, enabled = !viewModel.isTesting,
                        shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary)
                    ) {
                        if (viewModel.isTesting) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnPrimary)
                        else Icon(Icons.Filled.Wifi, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (viewModel.isTesting) "Testing..." else "Test", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = { viewModel.saveConfig() }, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Save, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // ─── Connection Status ───
        if (viewModel.connectionStatus.isNotBlank()) {
            Card(
                Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        viewModel.connectionStatus.startsWith("✅") -> SuccessGreen.copy(alpha = 0.1f)
                        viewModel.connectionStatus.startsWith("❌") -> ErrorCrimson.copy(alpha = 0.1f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Text(viewModel.connectionStatus, Modifier.padding(14.dp), style = MaterialTheme.typography.bodySmall)
            }
        }

        // ─── Defaults Section ───
        SectionHeader("Defaults")
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = viewModel.accountId, onValueChange = { viewModel.accountId = it },
                    label = { Text("Default Account ID") }, placeholder = { Text("1") },
                    leadingIcon = { Icon(Icons.Filled.AccountBalance, null, Modifier.size(20.dp)) },
                    singleLine = true, shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("Firefly asset account ID for transactions") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}
