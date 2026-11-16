package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.AlertEntity
import com.example.domain.model.DashboardTimeframe
import com.example.domain.model.TransactionType
import com.example.domain.model.TransactionUi
import com.example.domain.model.VendorSummary
import com.example.domain.util.IndianCurrencyFormatter
import com.example.ui.components.CashInflowOutflowBar
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.IncomeVsExpenseBarChart
import com.example.ui.components.SavingsTrendChart
import com.example.ui.components.SpendingTrendLineChart
import com.example.ui.theme.AmberWarm
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.EmeraldDeep
import com.example.ui.theme.EmeraldVibrant
import com.example.ui.viewmodel.FinanceDashboardState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    state: FinanceDashboardState,
    allTransactions: List<TransactionUi>,
    alerts: List<AlertEntity>,
    onSelectMonth: (String) -> Unit,
    onSelectTimeframe: (DashboardTimeframe) -> Unit,
    onMarkAlertsRead: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToAssistant: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedVendor by remember { mutableStateOf<VendorSummary?>(null) }
    var showAlertsSheet by remember { mutableStateOf(false) }
    var monthDropdownExpanded by remember { mutableStateOf(false) }

    val summary = state.currentSummary
    val prev = state.previousSummary
    val unreadAlerts = alerts.count { !it.isRead }
    val income = summary?.totalIncome ?: 0.0
    val expenses = summary?.totalExpenses ?: 0.0
    val remainingBalance = income - expenses
    val savings = summary?.totalSavings ?: 0.0
    val budgetLimit = state.overallBudgetLimit
    val budgetPct = if (budgetLimit > 0) ((expenses / budgetLimit) * 100.0).toInt() else 0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header Row with Month Selector & Alerts Bell
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Financial Pulse",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Encrypted on-device ledger",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        OutlinedButton(
                            onClick = { monthDropdownExpanded = true },
                            modifier = Modifier.testTag("month_selector_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "Select month",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = IndianCurrencyFormatter.monthLabel(state.selectedMonthKey),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        DropdownMenu(
                            expanded = monthDropdownExpanded,
                            onDismissRequest = { monthDropdownExpanded = false }
                        ) {
                            state.availableMonths.forEach { mKey ->
                                DropdownMenuItem(
                                    text = { Text(IndianCurrencyFormatter.monthLabel(mKey)) },
                                    onClick = {
                                        onSelectMonth(mKey)
                                        monthDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    IconButton(
                        onClick = { showAlertsSheet = true },
                        modifier = Modifier.testTag("alerts_bell_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadAlerts > 0) {
                                    Badge { Text(unreadAlerts.toString()) }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Notifications,
                                contentDescription = "View spending alerts"
                            )
                        }
                    }
                }
            }
        }

        // Hero Net Balance Card with Generated Art Backdrop
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = EmeraldDeep)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_finance_hero),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize()
                            .alpha(0.32f)
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xCC06372C),
                                        Color(0x990B5D4B)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "REMAINING BALANCE • ${IndianCurrencyFormatter.monthLabel(state.selectedMonthKey).uppercase()}",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                            Surface(
                                color = Color.White.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Shield,
                                        contentDescription = null,
                                        tint = Color(0xFF6EE7B7),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        "AES-256",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = IndianCurrencyFormatter.format(remainingBalance),
                            style = MaterialTheme.typography.displaySmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    "Monthly Budget Used",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.78f)
                                )
                                Text(
                                    "$budgetPct% of ${IndianCurrencyFormatter.formatCompact(budgetLimit)}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (budgetPct >= 90) Color(0xFFFCA5A5) else Color(0xFFA7F3D0)
                                )
                            }
                            TextButton(
                                onClick = onNavigateToAssistant,
                                modifier = Modifier.testTag("hero_ask_ai_button")
                            ) {
                                Text("Ask AI Analyst", color = Color(0xFFA7F3D0))
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFFA7F3D0),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top 6 Financial KPI Cards (2x3 grid)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Income This Month",
                        value = IndianCurrencyFormatter.format(income),
                        icon = Icons.AutoMirrored.Filled.TrendingUp,
                        accentColor = EmeraldVibrant,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Expenses This Month",
                        value = IndianCurrencyFormatter.format(expenses),
                        icon = Icons.AutoMirrored.Filled.TrendingDown,
                        accentColor = CrimsonAlert,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Remaining Balance",
                        value = IndianCurrencyFormatter.format(remainingBalance),
                        icon = Icons.Default.AccountBalanceWallet,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    KpiCard(
                        title = "Total Savings",
                        value = IndianCurrencyFormatter.format(savings),
                        icon = Icons.Default.Savings,
                        accentColor = Color(0xFF0D9488),
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    KpiCard(
                        title = "Monthly Budget",
                        value = IndianCurrencyFormatter.format(budgetLimit),
                        icon = Icons.Default.Shield,
                        accentColor = Color(0xFF4F46E5),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToBudgets)
                    )
                    KpiCard(
                        title = "Budget Used %",
                        value = "$budgetPct%",
                        icon = Icons.Default.Warning,
                        accentColor = when {
                            budgetPct >= 90 -> CrimsonAlert
                            budgetPct >= 75 -> AmberWarm
                            else -> EmeraldVibrant
                        },
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = onNavigateToBudgets)
                    )
                }
            }
        }

        // Unread Alert Banner if any
        if (unreadAlerts > 0) {
            val latest = alerts.first()
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAlertsSheet = true }
                        .testTag("dashboard_alert_banner"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = latest.title,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                text = latest.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = "View ($unreadAlerts)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Timeframe Filter Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardTimeframe.entries.forEach { tf ->
                    FilterChip(
                        selected = state.selectedTimeframe == tf,
                        onClick = { onSelectTimeframe(tf) },
                        label = { Text(tf.label) },
                        modifier = Modifier.testTag("timeframe_chip_${tf.name.lowercase()}")
                    )
                }
            }
        }

        // Previous Month vs Current Month Comparison + Monthly Summary Metrics
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Monthly Summary & Comparison",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (prev != null && summary != null) {
                        val spendDelta = summary.totalExpenses - prev.totalExpenses
                        val deltaSign = if (spendDelta >= 0) "+" else "-"
                        val deltaColor = if (spendDelta > 0) CrimsonAlert else EmeraldVibrant
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "vs ${prev.monthLabel}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "Prev Expenses: ${IndianCurrencyFormatter.format(prev.totalExpenses)}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Text(
                                    text = "$deltaSign${IndianCurrencyFormatter.format(kotlin.math.abs(spendDelta))}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = deltaColor
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStat("Transactions", "${summary?.transactionCount ?: 0}")
                        SummaryStat(
                            "Avg Daily Spend",
                            IndianCurrencyFormatter.formatCompact(summary?.averageDailySpend ?: 0.0)
                        )
                        SummaryStat(
                            "Top Category",
                            summary?.highestCategory ?: "None"
                        )
                    }
                }
            }
        }

        // Cash Inflow vs Outflow Chart
        item {
            DashboardChartCard(title = "Cash Inflow vs. Cash Outflow") {
                CashInflowOutflowBar(inflow = income, outflow = expenses)
            }
        }

        // Monthly Income vs Expenses Grouped Bar Chart
        item {
            DashboardChartCard(title = "Monthly Income vs. Expenses") {
                IncomeVsExpenseBarChart(series = state.monthlySeries)
            }
        }

        // Spending Trend Line Chart
        item {
            DashboardChartCard(title = "${state.selectedTimeframe.label} Spending Trend") {
                SpendingTrendLineChart(points = state.trendPoints)
            }
        }

        // Category-wise Expense Donut Chart + Top Categories
        item {
            DashboardChartCard(title = "Category Expense Breakdown") {
                CategoryDonutChart(categories = state.categoryTotals)
                if (state.categoryTotals.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        "Top Spending Categories",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val maxCat = state.categoryTotals.maxOf { it.second }.coerceAtLeast(1.0)
                    state.categoryTotals.take(5).forEach { (cat, amt) ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cat, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    IndianCurrencyFormatter.format(amt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { (amt / maxCat).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp))
                            )
                        }
                    }
                }
            }
        }

        // Monthly Savings Trend
        item {
            DashboardChartCard(title = "Monthly Savings Trend") {
                SavingsTrendChart(series = state.monthlySeries)
            }
        }

        // Top Vendors & Merchants (Tappable!)
        item {
            DashboardChartCard(title = "Top Vendors & Merchants (Tap for details)") {
                if (state.vendorSummaries.isEmpty()) {
                    Text(
                        "No vendor spend recorded in this view.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    state.vendorSummaries.take(8).forEachIndexed { idx, vendor ->
                        if (idx > 0) HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedVendor = vendor }
                                .padding(vertical = 6.dp)
                                .testTag("vendor_row_${vendor.vendorName}"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Storefront,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        vendor.vendorName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${vendor.transactionCount} txns • Avg ${IndianCurrencyFormatter.formatCompact(vendor.averageAmount)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = IndianCurrencyFormatter.format(vendor.totalSpent),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Vendor Detail Bottom Sheet
    selectedVendor?.let { vendor ->
        val vendorTxns = allTransactions.filter {
            it.vendor.equals(vendor.vendorName, ignoreCase = true) && it.type == TransactionType.EXPENSE
        }
        ModalBottomSheet(onDismissRequest = { selectedVendor = null }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = vendor.vendorName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${vendor.primaryCategory} • Last visit ${IndianCurrencyFormatter.formatDate(vendor.latestTimestamp)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SummaryStat("Total Spent", IndianCurrencyFormatter.format(vendor.totalSpent))
                    SummaryStat("Transactions", "${vendor.transactionCount}")
                    SummaryStat("Average", IndianCurrencyFormatter.format(vendor.averageAmount))
                }
                if (vendor.monthlyTrend.isNotEmpty()) {
                    Text("Monthly Vendor Trend", style = MaterialTheme.typography.titleSmall)
                    SpendingTrendLineChart(points = vendor.monthlyTrend)
                }
                HorizontalDivider()
                Text("Associated Transactions", style = MaterialTheme.typography.titleSmall)
                LazyColumn(
                    modifier = Modifier.height(200.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(vendorTxns) { txn ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(txn.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(
                                    "${IndianCurrencyFormatter.formatDateTime(txn.timestampMillis)} • ${txn.paymentMethod}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                IndianCurrencyFormatter.format(txn.amount),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }

    // Alerts Sheet
    if (showAlertsSheet) {
        ModalBottomSheet(onDismissRequest = { showAlertsSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Spending & Security Alerts", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onMarkAlertsRead) {
                        Text("Mark all read")
                    }
                }
                if (alerts.isEmpty()) {
                    Text(
                        "No alerts triggered. Your budgets and inbox look healthy!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.height(320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(alerts) { alert ->
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(alert.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            IndianCurrencyFormatter.formatDate(alert.timestampMillis),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(alert.message, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardChartCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SummaryStat(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
