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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.local.UserSettings
import com.example.domain.model.BudgetStatusUi
import com.example.domain.model.FinanceCategories
import com.example.domain.util.IndianCurrencyFormatter
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldVibrant

@Composable
fun BudgetsScreen(
    budgets: List<BudgetStatusUi>,
    overallLimit: Double,
    overallSpent: Double,
    settings: UserSettings,
    onSaveBudget: (category: String, limit: Double, carryForward: Boolean) -> Unit,
    onDeleteBudget: (Long) -> Unit,
    onToggleCarryForward: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var editingCategory by remember { mutableStateOf<String?>(null) }
    var editingLimit by remember { mutableStateOf("") }

    val overallBudget = budgets.firstOrNull { it.category == "OVERALL" }
    val categoryBudgets = budgets.filter { it.category != "OVERALL" }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingCategory = null
                    editingLimit = ""
                    showBudgetDialog = true
                },
                modifier = Modifier.testTag("add_budget_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add category budget")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 88.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Text(
                    "Smart Budget Management",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Automatic real-time tracking against SMS & manual expenses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Overall Monthly Budget Card
            item {
                val ratio = if (overallLimit > 0) (overallSpent / overallLimit).toFloat().coerceIn(0f, 1f) else 0f
                val pct = if (overallLimit > 0) ((overallSpent / overallLimit) * 100.0).toInt() else 0
                val barColor = when {
                    pct >= 90 -> CrimsonAlert
                    pct >= 75 -> AmberWarm
                    else -> EmeraldVibrant
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Overall Monthly Spending Limit",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "${IndianCurrencyFormatter.format(overallSpent)} spent of ${IndianCurrencyFormatter.format(overallLimit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    editingCategory = "OVERALL"
                                    editingLimit = overallLimit.toInt().toString()
                                    showBudgetDialog = true
                                },
                                modifier = Modifier.testTag("edit_overall_budget_button")
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit overall budget")
                            }
                        }

                        LinearProgressIndicator(
                            progress = { ratio },
                            color = barColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Remaining: ${IndianCurrencyFormatter.format((overallLimit - overallSpent).coerceAtLeast(0.0))}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "$pct% used",
                                style = MaterialTheme.typography.labelLarge,
                                color = barColor,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Carry Forward Unused Budget", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "Roll remaining balance into next month instead of resetting",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = overallBudget?.carryForward ?: settings.carryForwardBudgets,
                                onCheckedChange = { checked ->
                                    onToggleCarryForward(checked)
                                    onSaveBudget("OVERALL", overallLimit, checked)
                                }
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Category Spending Limits",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(categoryBudgets, key = { it.id }) { item ->
                val pct = item.utilizationPercent
                val statusColor = when {
                    pct >= 100 -> CrimsonAlert
                    pct >= 75 -> AmberWarm
                    else -> EmeraldVibrant
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("budget_card_${item.category}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.category, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Text(
                                    "${IndianCurrencyFormatter.format(item.spent)} / ${IndianCurrencyFormatter.format(item.monthlyLimit)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = statusColor.copy(alpha = 0.14f),
                                    shape = RoundedCornerShape(999.dp)
                                ) {
                                    Text(
                                        text = "$pct%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        editingCategory = item.category
                                        editingLimit = item.monthlyLimit.toInt().toString()
                                        showBudgetDialog = true
                                    },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit category budget", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = { onDeleteBudget(item.id) },
                                    modifier = Modifier.size(34.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete category budget", tint = CrimsonAlert, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        LinearProgressIndicator(
                            progress = { item.utilizationRatio.coerceIn(0f, 1f) },
                            color = statusColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (item.spent > item.monthlyLimit) {
                                    "Exceeded by ${IndianCurrencyFormatter.format(item.spent - item.monthlyLimit)}"
                                } else {
                                    "Left: ${IndianCurrencyFormatter.format(item.remaining)}"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (item.spent > item.monthlyLimit) CrimsonAlert else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Alerts: 50/75/90/100%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (showBudgetDialog) {
        var selectedCat by remember {
            mutableStateOf(editingCategory ?: FinanceCategories.expenseCategories.first())
        }
        var limitText by remember { mutableStateOf(editingLimit) }
        var dropdownOpen by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text(if (editingCategory == "OVERALL") "Edit Overall Monthly Budget" else "Set Category Budget") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (editingCategory != "OVERALL") {
                        Box {
                            OutlinedButton(
                                onClick = { dropdownOpen = true },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(selectedCat)
                            }
                            DropdownMenu(expanded = dropdownOpen, onDismissRequest = { dropdownOpen = false }) {
                                FinanceCategories.expenseCategories.forEach { cat ->
                                    DropdownMenuItem(
                                        text = { Text(cat) },
                                        onClick = {
                                            selectedCat = cat
                                            dropdownOpen = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = limitText,
                        onValueChange = { limitText = it },
                        label = { Text("Monthly Limit (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("budget_limit_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = limitText.toDoubleOrNull()
                        if (parsed != null && parsed > 0.0) {
                            onSaveBudget(selectedCat, parsed, settings.carryForwardBudgets)
                            showBudgetDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_budget_button")
                ) {
                    Text("Save Budget")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) { Text("Cancel") }
            }
        )
    }
}
