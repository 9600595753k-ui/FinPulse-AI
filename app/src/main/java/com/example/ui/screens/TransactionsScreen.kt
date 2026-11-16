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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.FinanceCategories
import com.example.domain.model.TransactionType
import com.example.domain.model.TransactionUi
import com.example.domain.util.IndianCurrencyFormatter
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldVibrant

@Composable
fun TransactionsScreen(
    transactions: List<TransactionUi>,
    availableMonths: List<String>,
    onAddTransaction: (
        title: String,
        vendor: String,
        amount: Double,
        type: TransactionType,
        category: String,
        paymentMethod: String,
        referenceId: String,
        notes: String
    ) -> Unit,
    onUpdateTransaction: (TransactionUi) -> Unit,
    onDeleteTransaction: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<TransactionType?>(null) }
    var categoryFilter by remember { mutableStateOf<String?>(null) }
    var monthFilter by remember { mutableStateOf<String?>(null) }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTxn by remember { mutableStateOf<TransactionUi?>(null) }

    val filtered = remember(transactions, searchQuery, typeFilter, categoryFilter, monthFilter) {
        transactions.filter { txn ->
            val matchQuery = searchQuery.isBlank() ||
                txn.vendor.contains(searchQuery, ignoreCase = true) ||
                txn.title.contains(searchQuery, ignoreCase = true) ||
                txn.category.contains(searchQuery, ignoreCase = true) ||
                txn.referenceId.contains(searchQuery, ignoreCase = true)
            val matchType = typeFilter == null || txn.type == typeFilter
            val matchCat = categoryFilter == null || txn.category == categoryFilter
            val matchMonth = monthFilter == null || IndianCurrencyFormatter.monthKey(txn.timestampMillis) == monthFilter
            matchQuery && matchType && matchCat && matchMonth
        }
    }

    val totalIn = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val totalOut = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_transaction_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add manual transaction")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Transactions Ledger",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${filtered.size} items • In ${IndianCurrencyFormatter.formatCompact(totalIn)} • Out ${IndianCurrencyFormatter.formatCompact(totalOut)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search vendor, category, Ref/UTR...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("txn_search_input")
            )

            // Type + Month Filters
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = typeFilter == null,
                    onClick = { typeFilter = null },
                    label = { Text("All") }
                )
                FilterChip(
                    selected = typeFilter == TransactionType.EXPENSE,
                    onClick = { typeFilter = TransactionType.EXPENSE },
                    label = { Text("Expenses") }
                )
                FilterChip(
                    selected = typeFilter == TransactionType.INCOME,
                    onClick = { typeFilter = TransactionType.INCOME },
                    label = { Text("Income") }
                )
                FilterChip(
                    selected = monthFilter == null,
                    onClick = { monthFilter = null },
                    label = { Text("All Months") }
                )
                availableMonths.forEach { mKey ->
                    FilterChip(
                        selected = monthFilter == mKey,
                        onClick = { monthFilter = mKey },
                        label = { Text(IndianCurrencyFormatter.monthLabel(mKey)) }
                    )
                }
            }

            // Category Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = categoryFilter == null,
                    onClick = { categoryFilter = null },
                    label = { Text("All Categories") }
                )
                val usedCategories = transactions.map { it.category }.distinct().sorted()
                usedCategories.forEach { cat ->
                    FilterChip(
                        selected = categoryFilter == cat,
                        onClick = { categoryFilter = if (categoryFilter == cat) null else cat },
                        label = { Text(cat) }
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No matching transactions", style = MaterialTheme.typography.titleMedium)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 84.dp)
                ) {
                    items(filtered, key = { it.id }) { txn ->
                        TransactionRowCard(
                            txn = txn,
                            onEdit = { editingTxn = txn },
                            onDelete = { onDeleteTransaction(txn.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        TransactionEditorDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { title, vendor, amount, type, category, paymentMethod, refId, notes ->
                onAddTransaction(title, vendor, amount, type, category, paymentMethod, refId, notes)
                showAddDialog = false
            }
        )
    }

    editingTxn?.let { txn ->
        TransactionEditorDialog(
            initial = txn,
            onDismiss = { editingTxn = null },
            onSave = { title, vendor, amount, type, category, paymentMethod, _, notes ->
                onUpdateTransaction(
                    txn.copy(
                        title = title,
                        vendor = vendor,
                        amount = amount,
                        type = type,
                        category = category,
                        paymentMethod = paymentMethod,
                        notes = notes
                    )
                )
                editingTxn = null
            }
        )
    }
}

@Composable
private fun TransactionRowCard(
    txn: TransactionUi,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val isIncome = txn.type == TransactionType.INCOME
    val amountColor = if (isIncome) EmeraldVibrant else MaterialTheme.colorScheme.onSurface
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .testTag("txn_card_${txn.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        if (isIncome) EmeraldVibrant.copy(alpha = 0.14f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = txn.vendor.take(2).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (isIncome) EmeraldVibrant else MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = txn.vendor,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${txn.category} • ${txn.paymentMethod}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = IndianCurrencyFormatter.formatDateTime(txn.timestampMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (txn.referenceId.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.extraSmall
                        ) {
                            Text(
                                text = "Ref ${txn.referenceId.takeLast(6)}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${if (isIncome) "+" else "-"}${IndianCurrencyFormatter.format(txn.amount)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = amountColor
                )
                Row {
                    IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit transaction", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete transaction", tint = CrimsonAlert, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionEditorDialog(
    initial: TransactionUi?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        vendor: String,
        amount: Double,
        type: TransactionType,
        category: String,
        paymentMethod: String,
        referenceId: String,
        notes: String
    ) -> Unit
) {
    var type by remember { mutableStateOf(initial?.type ?: TransactionType.EXPENSE) }
    var vendor by remember { mutableStateOf(initial?.vendor ?: "") }
    var amountStr by remember { mutableStateOf(initial?.amount?.toString() ?: "") }
    var category by remember {
        mutableStateOf(
            initial?.category ?: FinanceCategories.expenseCategories.first()
        )
    }
    var paymentMethod by remember { mutableStateOf(initial?.paymentMethod ?: "UPI") }
    var refId by remember { mutableStateOf(initial?.referenceId ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }

    var catExpanded by remember { mutableStateOf(false) }
    var payExpanded by remember { mutableStateOf(false) }

    val categoriesForType = if (type == TransactionType.EXPENSE) {
        FinanceCategories.expenseCategories
    } else {
        FinanceCategories.incomeCategories
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add Manual Transaction" else "Edit Transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = type == TransactionType.EXPENSE,
                        onClick = {
                            type = TransactionType.EXPENSE
                            if (category !in FinanceCategories.expenseCategories) {
                                category = FinanceCategories.expenseCategories.first()
                            }
                        },
                        label = { Text("Expense") }
                    )
                    FilterChip(
                        selected = type == TransactionType.INCOME,
                        onClick = {
                            type = TransactionType.INCOME
                            if (category !in FinanceCategories.incomeCategories) {
                                category = FinanceCategories.incomeCategories.first()
                            }
                        },
                        label = { Text("Income") }
                    )
                }

                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Merchant / Source") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("txn_vendor_input")
                )

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Amount (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("txn_amount_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { catExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                            categoriesForType.forEach { c ->
                                DropdownMenuItem(
                                    text = { Text(c) },
                                    onClick = {
                                        category = c
                                        catExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { payExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(paymentMethod, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(expanded = payExpanded, onDismissRequest = { payExpanded = false }) {
                            FinanceCategories.paymentMethods.forEach { pm ->
                                DropdownMenuItem(
                                    text = { Text(pm) },
                                    onClick = {
                                        paymentMethod = pm
                                        payExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                if (initial == null) {
                    OutlinedTextField(
                        value = refId,
                        onValueChange = { refId = it },
                        label = { Text("Reference / UTR ID (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountStr.toDoubleOrNull()
                    if (vendor.isNotBlank() && amt != null && amt > 0.0) {
                        onSave(vendor.trim(), vendor.trim(), amt, type, category, paymentMethod, refId.trim(), notes.trim())
                    }
                },
                modifier = Modifier.testTag("save_transaction_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
