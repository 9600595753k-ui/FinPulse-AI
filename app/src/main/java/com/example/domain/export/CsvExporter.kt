package com.example.domain.export

import com.example.data.local.SubscriptionEntity
import com.example.domain.model.BillingCycle
import com.example.domain.model.BudgetStatusUi
import com.example.domain.model.ReportDateRange
import com.example.domain.model.ReportType
import com.example.domain.model.TransactionType
import com.example.domain.model.TransactionUi
import com.example.domain.util.IndianCurrencyFormatter
import java.util.Calendar
import java.util.Locale

object CsvExporter {

    private fun escape(value: String): String {
        val needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n")
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    fun filterByDateRange(
        transactions: List<TransactionUi>,
        range: ReportDateRange
    ): List<TransactionUi> {
        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()
        val startMillis = when (range) {
            ReportDateRange.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            ReportDateRange.LAST_MONTH -> {
                cal.add(Calendar.MONTH, -1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.timeInMillis
            }
            ReportDateRange.LAST_3_MONTHS -> {
                cal.add(Calendar.MONTH, -3)
                cal.timeInMillis
            }
            ReportDateRange.THIS_YEAR -> {
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.timeInMillis
            }
            ReportDateRange.ALL_TIME -> 0L
        }

        val endMillis = if (range == ReportDateRange.LAST_MONTH) {
            val endCal = Calendar.getInstance()
            endCal.set(Calendar.DAY_OF_MONTH, 1)
            endCal.set(Calendar.HOUR_OF_DAY, 0)
            endCal.set(Calendar.MINUTE, 0)
            endCal.set(Calendar.SECOND, 0)
            endCal.timeInMillis - 1L
        } else {
            now + 86_400_000L
        }

        return transactions.filter { it.timestampMillis in startMillis..endMillis }
    }

    fun generateCsv(
        reportType: ReportType,
        transactions: List<TransactionUi>,
        budgets: List<BudgetStatusUi>,
        subscriptions: List<SubscriptionEntity>
    ): String {
        val sb = StringBuilder()
        when (reportType) {
            ReportType.MONTHLY_EXPENSE -> {
                sb.appendLine("Date,Time,Vendor,Category,Payment Method,Amount (INR),Reference ID,Account")
                transactions.filter { it.type == TransactionType.EXPENSE }.forEach { txn ->
                    sb.appendLine(
                        listOf(
                            IndianCurrencyFormatter.formatDate(txn.timestampMillis),
                            IndianCurrencyFormatter.formatTime(txn.timestampMillis),
                            txn.vendor,
                            txn.category,
                            txn.paymentMethod,
                            String.format(Locale.US, "%.2f", txn.amount),
                            txn.referenceId,
                            txn.accountMask
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            ReportType.MONTHLY_INCOME -> {
                sb.appendLine("Date,Time,Source,Category,Payment Method,Amount (INR),Reference ID")
                transactions.filter { it.type == TransactionType.INCOME }.forEach { txn ->
                    sb.appendLine(
                        listOf(
                            IndianCurrencyFormatter.formatDate(txn.timestampMillis),
                            IndianCurrencyFormatter.formatTime(txn.timestampMillis),
                            txn.vendor,
                            txn.category,
                            txn.paymentMethod,
                            String.format(Locale.US, "%.2f", txn.amount),
                            txn.referenceId
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            ReportType.INCOME_VS_EXPENSE -> {
                sb.appendLine("Month,Total Income (INR),Total Expenses (INR),Net Savings (INR),Savings Rate (%)")
                val grouped = transactions.groupBy { IndianCurrencyFormatter.monthKey(it.timestampMillis) }
                    .toSortedMap(compareByDescending { it })
                grouped.forEach { (monthKey, txns) ->
                    val inc = txns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
                    val exp = txns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
                    val net = inc - exp
                    val rate = if (inc > 0) (net / inc * 100.0) else 0.0
                    sb.appendLine(
                        listOf(
                            IndianCurrencyFormatter.monthLabel(monthKey),
                            String.format(Locale.US, "%.2f", inc),
                            String.format(Locale.US, "%.2f", exp),
                            String.format(Locale.US, "%.2f", net),
                            String.format(Locale.US, "%.1f", rate)
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            ReportType.CATEGORY_SPENDING -> {
                sb.appendLine("Category,Total Spent (INR),Transaction Count,Share (%)")
                val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
                val total = expenses.sumOf { it.amount }.coerceAtLeast(1.0)
                expenses.groupBy { it.category }
                    .mapValues { (_, list) -> list.sumOf { it.amount } to list.size }
                    .entries
                    .sortedByDescending { it.value.first }
                    .forEach { (cat, pair) ->
                        sb.appendLine(
                            listOf(
                                cat,
                                String.format(Locale.US, "%.2f", pair.first),
                                pair.second.toString(),
                                String.format(Locale.US, "%.1f", pair.first / total * 100.0)
                            ).joinToString(",") { escape(it) }
                        )
                    }
            }
            ReportType.VENDOR_SPENDING -> {
                sb.appendLine("Vendor,Total Spent (INR),Transactions,Average Ticket (INR),Primary Category")
                val expenses = transactions.filter { it.type == TransactionType.EXPENSE }
                expenses.groupBy { it.vendor }
                    .entries
                    .sortedByDescending { entry -> entry.value.sumOf { it.amount } }
                    .forEach { (vendor, list) ->
                        val sum = list.sumOf { it.amount }
                        val avg = sum / list.size
                        val primaryCat = list.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: ""
                        sb.appendLine(
                            listOf(
                                vendor,
                                String.format(Locale.US, "%.2f", sum),
                                list.size.toString(),
                                String.format(Locale.US, "%.2f", avg),
                                primaryCat
                            ).joinToString(",") { escape(it) }
                        )
                    }
            }
            ReportType.TRANSACTION_HISTORY -> {
                sb.appendLine("Date,Time,Title,Vendor,Type,Category,Payment Method,Amount (INR),Reference ID,Account,Source")
                transactions.forEach { txn ->
                    sb.appendLine(
                        listOf(
                            IndianCurrencyFormatter.formatDate(txn.timestampMillis),
                            IndianCurrencyFormatter.formatTime(txn.timestampMillis),
                            txn.title,
                            txn.vendor,
                            txn.type.label,
                            txn.category,
                            txn.paymentMethod,
                            String.format(Locale.US, "%.2f", txn.amount),
                            txn.referenceId,
                            txn.accountMask,
                            if (txn.isManual) "Manual" else "SMS Auto"
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            ReportType.BUDGET_PERFORMANCE -> {
                sb.appendLine("Budget Scope,Monthly Limit (INR),Actual Spent (INR),Remaining (INR),Utilization (%)")
                budgets.forEach { b ->
                    sb.appendLine(
                        listOf(
                            b.category,
                            String.format(Locale.US, "%.2f", b.monthlyLimit),
                            String.format(Locale.US, "%.2f", b.spent),
                            String.format(Locale.US, "%.2f", b.remaining),
                            b.utilizationPercent.toString()
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
            ReportType.SUBSCRIPTION_REPORT -> {
                sb.appendLine("Subscription,Vendor,Amount (INR),Billing Cycle,Annual Cost (INR),Next Payment,Status")
                subscriptions.forEach { sub ->
                    val cycle = runCatching { BillingCycle.valueOf(sub.billingCycle) }.getOrDefault(BillingCycle.MONTHLY)
                    val annual = sub.amount * cycle.annualMultiplier
                    sb.appendLine(
                        listOf(
                            sub.name,
                            sub.vendor,
                            String.format(Locale.US, "%.2f", sub.amount),
                            cycle.label,
                            String.format(Locale.US, "%.2f", annual),
                            IndianCurrencyFormatter.formatDate(sub.nextBillingMillis),
                            if (sub.isPaused) "Paused" else "Active"
                        ).joinToString(",") { escape(it) }
                    )
                }
            }
        }
        return sb.toString()
    }
}
