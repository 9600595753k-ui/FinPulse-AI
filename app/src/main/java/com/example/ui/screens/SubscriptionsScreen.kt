package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.SubscriptionEntity
import com.example.domain.model.BillingCycle
import com.example.domain.util.IndianCurrencyFormatter
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldVibrant
import kotlin.math.abs

@Composable
fun SubscriptionsScreen(
    subscriptions: List<SubscriptionEntity>,
    monthlyEquivalent: Double,
    annualCost: Double,
    onSaveSubscription: (
        existingId: Long,
        name: String,
        vendor: String,
        amount: Double,
        cycle: BillingCycle,
        category: String,
        reminderDays: Int
    ) -> Unit,
    onTogglePause: (SubscriptionEntity) -> Unit,
    onDeleteSubscription: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingSub by remember { mutableStateOf<SubscriptionEntity?>(null) }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingSub = null
                    showAddDialog = true
                },
                modifier = Modifier.testTag("add_subscription_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add recurring payment")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    "Recurring & Subscriptions",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Auto-detected from UPI AutoPay, card & biller SMS history",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = EmeraldDeep)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                "Monthly Equivalent",
                                style = MaterialTheme.typography.labelMedium,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                IndianCurrencyFormatter.format(monthlyEquivalent),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color.White
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                "Total Annual Burn",
                                style = MaterialTheme.typography.labelMedium,
                                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                IndianCurrencyFormatter.format(annualCost),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = androidx.compose.ui.graphics.Color(0xFFA7F3D0)
                            )
                        }
                    }
                }
            }

            if (subscriptions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(44.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("No recurring subscriptions detected yet", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            } else {
                items(subscriptions, key = { it.id }) { sub ->
                    val cycle = runCatching { BillingCycle.valueOf(sub.billingCycle) }.getOrDefault(BillingCycle.MONTHLY)
                    val daysLeft = ((sub.nextBillingMillis - System.currentTimeMillis()) / (24L * 60 * 60 * 1000L)).toInt()
                    val priceChanged = sub.previousAmount != null && abs(sub.amount - sub.previousAmount) >= 1.0

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("subscription_card_${sub.id}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (sub.isPaused) {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(
                                            text = sub.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (sub.isAutoDetected) {
                                            Surface(
                                                color = EmeraldVibrant.copy(alpha = 0.14f),
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Text(
                                                    "Auto",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = EmeraldVibrant,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        if (sub.isPaused) {
                                            Surface(
                                                color = AmberWarm.copy(alpha = 0.15f),
                                                shape = RoundedCornerShape(999.dp)
                                            ) {
                                                Text(
                                                    "Paused",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = AmberWarm,
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Text(
                                        text = "${cycle.label} • Next: ${IndianCurrencyFormatter.formatDate(sub.nextBillingMillis)} (${if (daysLeft <= 0) "Due now" else "in ${daysLeft}d"})",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = IndianCurrencyFormatter.format(sub.amount),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (priceChanged && sub.previousAmount != null) {
                                        Text(
                                            text = "↑ was ${IndianCurrencyFormatter.format(sub.previousAmount)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = CrimsonAlert
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { onTogglePause(sub) }) {
                                    Icon(
                                        if (sub.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (sub.isPaused) "Resume" else "Pause")
                                }
                                IconButton(onClick = {
                                    editingSub = sub
                                    showAddDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit subscription", modifier = Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onDeleteSubscription(sub.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove subscription", tint = CrimsonAlert, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf(editingSub?.name ?: "") }
        var vendor by remember { mutableStateOf(editingSub?.vendor ?: "") }
        var amountStr by remember { mutableStateOf(editingSub?.amount?.toString() ?: "") }
        var cycle by remember {
            mutableStateOf(
                editingSub?.billingCycle?.let { runCatching { BillingCycle.valueOf(it) }.getOrNull() }
                    ?: BillingCycle.MONTHLY
            )
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(if (editingSub == null) "Add Recurring Payment" else "Edit Subscription") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Subscription Name (e.g. Netflix)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = vendor,
                        onValueChange = { vendor = it },
                        label = { Text("Merchant / Biller") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = amountStr,
                        onValueChange = { amountStr = it },
                        label = { Text("Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        BillingCycle.entries.forEach { c ->
                            FilterChip(
                                selected = cycle == c,
                                onClick = { cycle = c },
                                label = { Text(c.label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (name.isNotBlank() && amt != null && amt > 0.0) {
                        onSaveSubscription(
                            editingSub?.id ?: 0L,
                            name.trim(),
                            vendor.ifBlank { name }.trim(),
                            amt,
                            cycle,
                            "Subscriptions",
                            3
                        )
                        showAddDialog = false
                    }
                }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
