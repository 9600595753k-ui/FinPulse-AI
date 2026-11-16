package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.CustomFolderEntity
import com.example.domain.model.SmsFolder
import com.example.domain.model.SmsMessageUi
import com.example.domain.util.IndianCurrencyFormatter
import com.example.ui.components.SmsPermissionBanner
import com.example.ui.components.SmsPermissionDialogs
import com.example.ui.components.rememberSmsPermissionController
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldVibrant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsInboxScreen(
    messages: List<SmsMessageUi>,
    customFolders: List<CustomFolderEntity>,
    hasRequestedSmsPermission: Boolean,
    onMarkSmsPermissionRequested: () -> Unit,
    onPermissionDeniedMessage: (String) -> Unit,
    onScanDeviceSms: () -> Unit,
    onImportManualSms: (sender: String, body: String) -> Unit,
    onCorrectFolder: (smsId: Long, folder: SmsFolder, customName: String?) -> Unit,
    onMarkRead: (Long) -> Unit,
    onDeleteSms: (Long) -> Unit,
    onCreateCustomFolder: (name: String, keywords: String, senderPattern: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFolderFilter by remember { mutableStateOf<SmsFolder?>(null) }
    var selectedCustomFilter by remember { mutableStateOf<String?>(null) }
    var sortNewestFirst by remember { mutableStateOf(true) }

    var inspectingSms by remember { mutableStateOf<SmsMessageUi?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showCustomFolderDialog by remember { mutableStateOf(false) }

    val smsPermissionController = rememberSmsPermissionController(
        hasRequestedBefore = hasRequestedSmsPermission,
        onMarkRequested = onMarkSmsPermissionRequested,
        onPermissionGranted = onScanDeviceSms,
        onPermissionDeniedFeedback = { permanentlyDenied ->
            onPermissionDeniedMessage(
                if (permanentlyDenied) {
                    "SMS access blocked. Enable it in App Settings or import SMS manually."
                } else {
                    "SMS permission declined. You can grant it anytime or paste SMS receipts."
                }
            )
        }
    )

    val filtered = remember(messages, searchQuery, selectedFolderFilter, selectedCustomFilter, sortNewestFirst) {
        messages.filter { msg ->
            val matchesSearch = searchQuery.isBlank() ||
                msg.sender.contains(searchQuery, ignoreCase = true) ||
                msg.body.contains(searchQuery, ignoreCase = true) ||
                (msg.extractedVendor?.contains(searchQuery, ignoreCase = true) == true)
            val matchesFolder = when {
                selectedCustomFilter != null -> msg.customFolderName == selectedCustomFilter
                selectedFolderFilter != null -> msg.folder == selectedFolderFilter
                else -> true
            }
            matchesSearch && matchesFolder
        }.let { list ->
            if (sortNewestFirst) list.sortedByDescending { it.receivedAtMillis }
            else list.sortedBy { it.receivedAtMillis }
        }
    }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showImportDialog = true },
                modifier = Modifier.testTag("import_sms_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import or test SMS")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Top Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Smart SMS Organizer",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${messages.size} messages • On-device ML",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = { showCustomFolderDialog = true },
                        modifier = Modifier.testTag("add_custom_folder_button"),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Rule", style = MaterialTheme.typography.labelMedium)
                    }
                    Button(
                        onClick = { smsPermissionController.requestOrScan() },
                        modifier = Modifier.testTag("scan_sms_button"),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan SMS", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            SmsPermissionBanner(
                controller = smsPermissionController,
                onImportManualClick = { showImportDialog = true }
            )

            // Search + Sort
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search sender, merchant, keyword...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("sms_search_input")
                )
                OutlinedButton(onClick = { sortNewestFirst = !sortNewestFirst }) {
                    Text(if (sortNewestFirst) "Newest" else "Oldest")
                }
            }

            // Folder Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFolderFilter == null && selectedCustomFilter == null,
                    onClick = {
                        selectedFolderFilter = null
                        selectedCustomFilter = null
                    },
                    label = { Text("All (${messages.size})") }
                )
                customFolders.forEach { cf ->
                    val count = messages.count { it.customFolderName == cf.name }
                    FilterChip(
                        selected = selectedCustomFilter == cf.name,
                        onClick = {
                            selectedCustomFilter = cf.name
                            selectedFolderFilter = null
                        },
                        label = { Text("${cf.name} ($count)") }
                    )
                }
                SmsFolder.entries.filter { it != SmsFolder.CUSTOM }.forEach { folder ->
                    val count = messages.count { it.folder == folder }
                    FilterChip(
                        selected = selectedFolderFilter == folder,
                        onClick = {
                            selectedFolderFilter = folder
                            selectedCustomFilter = null
                        },
                        label = { Text("${folder.displayName} ($count)") },
                        modifier = Modifier.testTag("folder_chip_${folder.name.lowercase()}")
                    )
                }
            }

            // Messages List
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.Sms,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No messages in this folder", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tap + to test SMS parsing or Scan SMS to import from device",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filtered, key = { it.id }) { sms ->
                        SmsMessageCard(
                            sms = sms,
                            onClick = {
                                onMarkRead(sms.id)
                                inspectingSms = sms
                            }
                        )
                    }
                }
            }
        }
    }

    // SMS Inspection & ML Folder Correction Sheet
    inspectingSms?.let { sms ->
        ModalBottomSheet(onDismissRequest = { inspectingSms = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(sms.sender, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(
                            IndianCurrencyFormatter.formatDateTime(sms.receivedAtMillis),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = {
                        onDeleteSms(sms.id)
                        inspectingSms = null
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete SMS", tint = CrimsonAlert)
                    }
                }

                if (sms.isScamSuspicious) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = CrimsonAlert)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                sms.scamReason ?: "Suspicious financial phishing detected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = sms.body,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                if (sms.extractedAmount != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text("Extracted Transaction Details", style = MaterialTheme.typography.titleSmall)
                            Text("Amount: ${IndianCurrencyFormatter.format(sms.extractedAmount)} (${sms.extractedType})")
                            if (!sms.extractedVendor.isNullOrBlank()) Text("Merchant: ${sms.extractedVendor}")
                            if (!sms.extractedPaymentMethod.isNullOrBlank()) Text("Method: ${sms.extractedPaymentMethod}")
                            if (!sms.extractedRefId.isNullOrBlank()) Text("Ref/UTR: ${sms.extractedRefId}")
                        }
                    }
                }

                HorizontalDivider()
                Text(
                    "Folder Classification (${(sms.confidenceScore * 100).toInt()}% confidence) — Tap to correct & train ML:",
                    style = MaterialTheme.typography.labelLarge
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    customFolders.forEach { cf ->
                        FilterChip(
                            selected = sms.customFolderName == cf.name,
                            onClick = {
                                onCorrectFolder(sms.id, SmsFolder.CUSTOM, cf.name)
                                inspectingSms = null
                            },
                            label = { Text(cf.name) }
                        )
                    }
                    SmsFolder.entries.filter { it != SmsFolder.CUSTOM }.forEach { folder ->
                        FilterChip(
                            selected = sms.folder == folder && sms.customFolderName == null,
                            onClick = {
                                onCorrectFolder(sms.id, folder, null)
                                inspectingSms = null
                            },
                            label = { Text(folder.displayName) }
                        )
                    }
                }
            }
        }
    }

    // Paste / Import SMS Dialog
    if (showImportDialog) {
        var senderInput by remember { mutableStateOf("AD-HDFCBK") }
        var bodyInput by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Import / Parse SMS Receipt") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Paste any bank, UPI, card, or promotional SMS to test local ML classification & extraction:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = senderInput,
                        onValueChange = { senderInput = it },
                        label = { Text("Sender ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = bodyInput,
                        onValueChange = { bodyInput = it },
                        label = { Text("SMS Message Body") },
                        placeholder = { Text("e.g. Rs 540.00 debited via UPI to Swiggy Ref 44120099") },
                        minLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("manual_sms_body_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (bodyInput.isNotBlank()) {
                            onImportManualSms(senderInput, bodyInput)
                            showImportDialog = false
                        }
                    },
                    modifier = Modifier.testTag("confirm_import_sms_button")
                ) {
                    Text("Classify & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Custom Folder Rule Dialog
    if (showCustomFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        var keywords by remember { mutableStateOf("") }
        var senderPat by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustomFolderDialog = false },
            title = { Text("Create Custom Folder & Rule") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        label = { Text("Folder Name (e.g. Investments)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = keywords,
                        onValueChange = { keywords = it },
                        label = { Text("Keywords (comma-separated)") },
                        placeholder = { Text("zerodha, groww, mutual fund, sip") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = senderPat,
                        onValueChange = { senderPat = it },
                        label = { Text("Sender Filter (optional)") },
                        placeholder = { Text("ZERODHA") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (folderName.isNotBlank() && (keywords.isNotBlank() || senderPat.isNotBlank())) {
                        onCreateCustomFolder(folderName, keywords, senderPat)
                        showCustomFolderDialog = false
                    }
                }) { Text("Create Rule") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomFolderDialog = false }) { Text("Cancel") }
            }
        )
    }

    SmsPermissionDialogs(
        controller = smsPermissionController,
        onFallbackToManualImport = { showImportDialog = true }
    )
}

@Composable
private fun SmsMessageCard(
    sms: SmsMessageUi,
    onClick: () -> Unit
) {
    val folderLabel = sms.customFolderName ?: sms.folder.displayName
    val badgeColor = when {
        sms.isScamSuspicious -> CrimsonAlert
        sms.folder == SmsFolder.OTP_VERIFICATION -> Color(0xFF6366F1)
        sms.folder == SmsFolder.INCOME_SALARY -> EmeraldVibrant
        sms.folder == SmsFolder.PAYMENTS_TRANSACTIONS -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("sms_card_${sms.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = sms.sender,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    if (!sms.isRead) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(EmeraldVibrant)
                        )
                    }
                    if (sms.userCorrectedFolder != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            Icons.Default.MarkEmailRead,
                            contentDescription = "User verified",
                            tint = EmeraldVibrant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Text(
                    text = IndianCurrencyFormatter.formatDateTime(sms.receivedAtMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = sms.body,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = badgeColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (sms.isScamSuspicious) Icons.Default.Warning else Icons.Default.Folder,
                            contentDescription = null,
                            tint = badgeColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = folderLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeColor
                        )
                    }
                }

                if (sms.extractedAmount != null) {
                    val isInc = sms.extractedType == "INCOME"
                    Text(
                        text = "${if (isInc) "+" else "-"}${IndianCurrencyFormatter.format(sms.extractedAmount)}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isInc) EmeraldVibrant else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}
