package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.UserSettings
import com.example.domain.model.AlertFrequency
import com.example.domain.model.ThemePreference
import com.example.domain.util.IndianCurrencyFormatter
import com.example.ui.components.SmsPermissionDialogs
import com.example.ui.components.rememberSmsPermissionController
import com.example.ui.theme.CrimsonAlert

@Composable
fun SettingsScreen(
    settings: UserSettings,
    onMarkSmsPermissionRequested: () -> Unit,
    onScanDeviceSms: () -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onSetLargeTxnThreshold: (Double) -> Unit,
    onSetDeviceNotifications: (Boolean) -> Unit,
    onSetAlertFrequency: (AlertFrequency) -> Unit,
    onSetThresholdAlerts: (Boolean, Boolean, Boolean, Boolean) -> Unit,
    onSetAllowAiInsights: (Boolean) -> Unit,
    onImportCsvText: (String) -> Unit,
    onResetDemoData: () -> Unit,
    onDeleteAllSms: () -> Unit,
    onDeleteAllTransactions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showThresholdDialog by remember { mutableStateOf(false) }
    var confirmAction by remember { mutableStateOf<String?>(null) }

    val smsPermissionController = rememberSmsPermissionController(
        hasRequestedBefore = settings.hasRequestedSmsPermission,
        onMarkRequested = onMarkSmsPermissionRequested,
        onPermissionGranted = onScanDeviceSms
    )

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onSetDeviceNotifications(granted)
    }

    val csvOpenLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let { text ->
                onImportCsvText(text)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                "Settings & Privacy Vault",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Control alerts, appearance, encryption, and data permissions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Theme Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Palette, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Appearance", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemePreference.entries.forEach { pref ->
                            FilterChip(
                                selected = settings.themePreference == pref,
                                onClick = { onSetTheme(pref) },
                                label = { Text(pref.label) },
                                modifier = Modifier.testTag("theme_chip_${pref.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }

        // Overspending Alerts & Notifications
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.NotificationsActive, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Overspending Alerts & Thresholds", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Device Push Notifications", style = MaterialTheme.typography.titleSmall)
                            Text("Show system alerts for budget & bill warnings", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.enableDeviceNotifications,
                            onCheckedChange = { wantEnable ->
                                if (wantEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onSetDeviceNotifications(wantEnable)
                                }
                            }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AlertFrequency.entries.forEach { freq ->
                            FilterChip(
                                selected = settings.alertFrequency == freq,
                                onClick = { onSetAlertFrequency(freq) },
                                label = { Text(freq.label) }
                            )
                        }
                    }

                    HorizontalDivider()

                    ThresholdSwitchRow("Alert at 50% budget used", settings.alertAt50) {
                        onSetThresholdAlerts(it, settings.alertAt75, settings.alertAt90, settings.alertAt100)
                    }
                    ThresholdSwitchRow("Warning at 75% budget used", settings.alertAt75) {
                        onSetThresholdAlerts(settings.alertAt50, it, settings.alertAt90, settings.alertAt100)
                    }
                    ThresholdSwitchRow("Critical alert at 90% budget used", settings.alertAt90) {
                        onSetThresholdAlerts(settings.alertAt50, settings.alertAt75, it, settings.alertAt100)
                    }
                    ThresholdSwitchRow("Alert when budget exceeded (100%+)", settings.alertAt100) {
                        onSetThresholdAlerts(settings.alertAt50, settings.alertAt75, settings.alertAt90, it)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Large Transaction Alert", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Notify on single txn ≥ ${IndianCurrencyFormatter.format(settings.largeTransactionThreshold)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { showThresholdDialog = true }) {
                            Text("Edit")
                        }
                    }
                }
            }
        }

        // Data Security & Privacy Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Security & Data Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        text = stringResource(R.string.privacy_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val smsStatusLabel = when {
                        smsPermissionController.isGranted -> "Granted"
                        smsPermissionController.isPermanentlyDenied -> "Blocked in Settings (Manual/CSV active)"
                        smsPermissionController.shouldShowRationale -> "Declined (Rationale available)"
                        else -> "Not Granted (Manual/CSV mode active)"
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("SMS Read Permission", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "$smsStatusLabel\nCredentials & PINs are never collected.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = { smsPermissionController.requestOrScan() },
                            modifier = Modifier.testTag("settings_sms_permission_button")
                        ) {
                            Text(if (smsPermissionController.isGranted) "Scan Now" else "Manage")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Financial Insights", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Allow sharing anonymized ₹ category totals with Gemini AI (OTPs & SMS excluded)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.allowAiAggregatedInsights,
                            onCheckedChange = onSetAllowAiInsights
                        )
                    }
                }
            }
        }

        // Import & Data Controls
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Data Management", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                    OutlinedButton(
                        onClick = { csvOpenLauncher.launch(arrayOf("text/*", "text/csv")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import Transactions from CSV")
                    }

                    OutlinedButton(
                        onClick = onResetDemoData,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_demo_button")
                    ) {
                        Icon(Icons.Default.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restore Sample Demo Data")
                    }

                    OutlinedButton(
                        onClick = { confirmAction = "SMS" },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_all_sms_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All SMS Messages")
                    }

                    OutlinedButton(
                        onClick = { confirmAction = "TXN" },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CrimsonAlert),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("delete_all_txns_button")
                    ) {
                        Icon(Icons.Default.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete All Transactions")
                    }
                }
            }
        }
    }

    if (showThresholdDialog) {
        var text by remember { mutableStateOf(settings.largeTransactionThreshold.toInt().toString()) }
        AlertDialog(
            onDismissRequest = { showThresholdDialog = false },
            title = { Text("Large Transaction Alert Threshold") },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    text.toDoubleOrNull()?.takeIf { it > 0 }?.let { onSetLargeTxnThreshold(it) }
                    showThresholdDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showThresholdDialog = false }) { Text("Cancel") }
            }
        )
    }

    confirmAction?.let { action ->
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("Confirm Permanent Deletion") },
            text = {
                Text(
                    if (action == "SMS") "Delete all encrypted SMS messages and learned ML folder rules?"
                    else "Delete all extracted and manual transaction records?"
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                    onClick = {
                        if (action == "SMS") onDeleteAllSms() else onDeleteAllTransactions()
                        confirmAction = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmAction = null }) { Text("Cancel") }
            }
        )
    }

    SmsPermissionDialogs(
        controller = smsPermissionController,
        onFallbackToManualImport = null
    )
}

@Composable
private fun ThresholdSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
