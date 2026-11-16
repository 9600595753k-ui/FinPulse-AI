package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.PermissionState
import com.google.accompanist.permissions.PermissionStatus
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale

enum class SmsPermissionDialogMode {
    NONE,
    INITIAL_PRIVACY_DISCLOSURE,
    RATIONALE,
    PERMANENTLY_DENIED_SETTINGS,
    TEMPORARY_DENIAL_FEEDBACK
}

@Stable
class SmsPermissionController @OptIn(ExperimentalPermissionsApi::class) constructor(
    private val permissionStateHolder: MutableState<PermissionState?>,
    private val dialogModeState: MutableState<SmsPermissionDialogMode>,
    private val pendingCallbackFromPrompt: MutableState<Boolean>,
    private val hasRequestedBeforeState: State<Boolean>,
    private val onMarkRequestedState: State<() -> Unit>,
    private val onPermissionGrantedState: State<() -> Unit>
) {
    val dialogMode: SmsPermissionDialogMode
        get() = dialogModeState.value

    @OptIn(ExperimentalPermissionsApi::class)
    private val currentPermissionState: PermissionState
        get() = requireNotNull(permissionStateHolder.value) {
            "PermissionState has not been attached yet"
        }

    @OptIn(ExperimentalPermissionsApi::class)
    val isGranted: Boolean
        get() = currentPermissionState.status.isGranted

    @OptIn(ExperimentalPermissionsApi::class)
    val shouldShowRationale: Boolean
        get() = currentPermissionState.status.shouldShowRationale

    @OptIn(ExperimentalPermissionsApi::class)
    val isPermanentlyDenied: Boolean
        get() = !isGranted && !shouldShowRationale && hasRequestedBeforeState.value

    @OptIn(ExperimentalPermissionsApi::class)
    fun requestOrScan() {
        when (val status = currentPermissionState.status) {
            is PermissionStatus.Granted -> onPermissionGrantedState.value.invoke()
            is PermissionStatus.Denied -> {
                dialogModeState.value = when {
                    status.shouldShowRationale -> SmsPermissionDialogMode.RATIONALE
                    hasRequestedBeforeState.value -> SmsPermissionDialogMode.PERMANENTLY_DENIED_SETTINGS
                    else -> SmsPermissionDialogMode.INITIAL_PRIVACY_DISCLOSURE
                }
            }
        }
    }

    @OptIn(ExperimentalPermissionsApi::class)
    fun launchSystemRequest() {
        dialogModeState.value = SmsPermissionDialogMode.NONE
        pendingCallbackFromPrompt.value = true
        onMarkRequestedState.value.invoke()
        currentPermissionState.launchPermissionRequest()
    }

    fun dismissDialog() {
        dialogModeState.value = SmsPermissionDialogMode.NONE
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun rememberSmsPermissionController(
    hasRequestedBefore: Boolean,
    onMarkRequested: () -> Unit,
    onPermissionGranted: () -> Unit,
    onPermissionDeniedFeedback: (permanentlyDenied: Boolean) -> Unit = {}
): SmsPermissionController {
    val dialogModeState = remember { mutableStateOf(SmsPermissionDialogMode.NONE) }
    val pendingCallbackFromPrompt = remember { mutableStateOf(false) }
    val permissionStateHolder = remember { mutableStateOf<PermissionState?>(null) }

    val hasRequestedBeforeState = rememberUpdatedState(hasRequestedBefore)
    val onMarkRequestedState = rememberUpdatedState(onMarkRequested)
    val onPermissionGrantedState = rememberUpdatedState(onPermissionGranted)
    val onPermissionDeniedFeedbackState = rememberUpdatedState(onPermissionDeniedFeedback)

    val permissionState = rememberPermissionState(
        permission = Manifest.permission.READ_SMS
    ) { granted ->
        if (granted) {
            pendingCallbackFromPrompt.value = false
            dialogModeState.value = SmsPermissionDialogMode.NONE
            onPermissionGrantedState.value.invoke()
        } else if (pendingCallbackFromPrompt.value) {
            pendingCallbackFromPrompt.value = false
            val needsRationale = permissionStateHolder.value?.status?.shouldShowRationale == true
            dialogModeState.value = if (needsRationale) {
                SmsPermissionDialogMode.TEMPORARY_DENIAL_FEEDBACK
            } else {
                SmsPermissionDialogMode.PERMANENTLY_DENIED_SETTINGS
            }
            onPermissionDeniedFeedbackState.value.invoke(!needsRationale)
        }
    }

    SideEffect {
        permissionStateHolder.value = permissionState
    }
    if (permissionStateHolder.value == null) {
        permissionStateHolder.value = permissionState
    }

    return remember {
        SmsPermissionController(
            permissionStateHolder = permissionStateHolder,
            dialogModeState = dialogModeState,
            pendingCallbackFromPrompt = pendingCallbackFromPrompt,
            hasRequestedBeforeState = hasRequestedBeforeState,
            onMarkRequestedState = onMarkRequestedState,
            onPermissionGrantedState = onPermissionGrantedState
        )
    }
}

@Composable
fun SmsPermissionBanner(
    controller: SmsPermissionController,
    onImportManualClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    if (controller.isGranted) return
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("sms_permission_banner"),
        colors = CardDefaults.cardColors(
            containerColor = if (controller.isPermanentlyDenied) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
            } else {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (controller.isPermanentlyDenied) Icons.Default.Warning else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (controller.isPermanentlyDenied) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    },
                    modifier = Modifier.size(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = when {
                            controller.isPermanentlyDenied -> "SMS permission disabled in Settings"
                            controller.shouldShowRationale -> "SMS permission needed for inbox scan"
                            else -> "Auto-scan SMS or import manually"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (controller.isPermanentlyDenied) {
                            "Enable SMS in App Settings to auto-read bank alerts, or paste/import receipts manually."
                        } else {
                            "Processed 100% locally. OTPs & personal SMS never leave your phone."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onImportManualClick != null) {
                    TextButton(
                        onClick = onImportManualClick,
                        modifier = Modifier.testTag("banner_manual_import_button")
                    ) {
                        Text("Paste SMS")
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }
                if (controller.isPermanentlyDenied) {
                    OutlinedButton(
                        onClick = { openAppPermissionSettings(context) },
                        modifier = Modifier.testTag("banner_open_settings_button")
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Settings")
                    }
                } else {
                    Button(
                        onClick = { controller.requestOrScan() },
                        modifier = Modifier.testTag("banner_grant_sms_button")
                    ) {
                        Text(if (controller.shouldShowRationale) "Review & Grant" else "Enable SMS")
                    }
                }
            }
        }
    }
}

@Composable
fun SmsPermissionDialogs(
    controller: SmsPermissionController,
    onFallbackToManualImport: (() -> Unit)? = null
) {
    val context = LocalContext.current
    when (controller.dialogMode) {
        SmsPermissionDialogMode.NONE -> Unit

        SmsPermissionDialogMode.INITIAL_PRIVACY_DISCLOSURE -> {
            AlertDialog(
                onDismissRequest = { controller.dismissDialog() },
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                title = { Text("On-Device SMS Access") },
                text = {
                    Text(
                        "FinPulse reads your SMS inbox locally on this device to organize messages into folders and extract bank/UPI transactions.\n\n" +
                            "• OTPs and personal SMS are NEVER sent to any external server or AI service.\n" +
                            "• Stored messages are encrypted with AES-256-GCM.\n" +
                            "• You can deny permission and still paste SMS or import CSV files manually."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { controller.launchSystemRequest() },
                        modifier = Modifier.testTag("sms_disclosure_allow_button")
                    ) {
                        Text("Allow & Scan")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { controller.dismissDialog() },
                        modifier = Modifier.testTag("sms_disclosure_dismiss_button")
                    ) {
                        Text("Not Now")
                    }
                }
            )
        }

        SmsPermissionDialogMode.RATIONALE -> {
            AlertDialog(
                onDismissRequest = { controller.dismissDialog() },
                icon = { Icon(Icons.Default.Security, contentDescription = null) },
                title = { Text("Why FinPulse Needs SMS Access") },
                text = {
                    Text(
                        "You previously declined SMS access. Granting READ_SMS lets FinPulse automatically classify bank, UPI, bill, and subscription alerts into folders and keep your budgets updated without manual typing.\n\n" +
                            "Your OTPs and private conversations remain strictly on your device."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { controller.launchSystemRequest() },
                        modifier = Modifier.testTag("sms_rationale_grant_button")
                    ) {
                        Text("Grant Permission")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            controller.dismissDialog()
                            onFallbackToManualImport?.invoke()
                        },
                        modifier = Modifier.testTag("sms_rationale_manual_button")
                    ) {
                        Text(if (onFallbackToManualImport != null) "Paste SMS Instead" else "Dismiss")
                    }
                }
            )
        }

        SmsPermissionDialogMode.TEMPORARY_DENIAL_FEEDBACK -> {
            AlertDialog(
                onDismissRequest = { controller.dismissDialog() },
                icon = { Icon(Icons.Default.Warning, contentDescription = null) },
                title = { Text("SMS Permission Declined") },
                text = {
                    Text(
                        "FinPulse won't scan your device SMS inbox right now. You can retry anytime using Scan SMS, or paste individual receipts and import CSVs manually."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { controller.launchSystemRequest() },
                        modifier = Modifier.testTag("sms_retry_permission_button")
                    ) {
                        Text("Try Again")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            controller.dismissDialog()
                            onFallbackToManualImport?.invoke()
                        },
                        modifier = Modifier.testTag("sms_denied_manual_button")
                    ) {
                        Text(if (onFallbackToManualImport != null) "Paste SMS" else "Continue Without SMS")
                    }
                }
            )
        }

        SmsPermissionDialogMode.PERMANENTLY_DENIED_SETTINGS -> {
            AlertDialog(
                onDismissRequest = { controller.dismissDialog() },
                icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                title = { Text("Enable SMS in App Settings") },
                text = {
                    Text(
                        "Android requires enabling SMS permission manually from App Settings because permission was permanently denied.\n\n" +
                            "Go to Permissions > SMS > Allow, or continue using FinPulse with manual SMS paste and CSV import."
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            controller.dismissDialog()
                            openAppPermissionSettings(context)
                        },
                        modifier = Modifier.testTag("sms_open_settings_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open App Settings")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            controller.dismissDialog()
                            onFallbackToManualImport?.invoke()
                        },
                        modifier = Modifier.testTag("sms_settings_manual_button")
                    ) {
                        Text(if (onFallbackToManualImport != null) "Use Manual Import" else "Dismiss")
                    }
                }
            )
        }
    }
}

fun openAppPermissionSettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}
