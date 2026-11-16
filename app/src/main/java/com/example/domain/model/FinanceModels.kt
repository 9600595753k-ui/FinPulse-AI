package com.example.domain.model

enum class SmsFolder(val displayName: String) {
    PERSONAL("Personal"),
    WORK("Work"),
    BANKING_FINANCE("Banking & Finance"),
    SHOPPING("Shopping"),
    BILLS_UTILITIES("Bills & Utilities"),
    PAYMENTS_TRANSACTIONS("Payments & Transactions"),
    INCOME_SALARY("Income & Salary"),
    OFFERS_PROMOTIONS("Offers & Promotions"),
    OTP_VERIFICATION("OTP & Verification"),
    SPAM_SUSPICIOUS("Spam & Suspicious"),
    CUSTOM("Custom Folders")
}

enum class TransactionType(val label: String) {
    EXPENSE("Expense"),
    INCOME("Income")
}

object FinanceCategories {
    val expenseCategories = listOf(
        "Food & Restaurants",
        "Groceries",
        "Shopping",
        "Transportation",
        "Fuel",
        "Rent",
        "Electricity & Utilities",
        "Entertainment",
        "Healthcare",
        "Education",
        "Travel",
        "Subscriptions",
        "Shopping & Online Orders",
        "Transfers",
        "Other Expenses"
    )

    val incomeCategories = listOf(
        "Monthly Salary",
        "Freelance Income",
        "Business Income",
        "Refunds",
        "Interest",
        "Other Income"
    )

    val paymentMethods = listOf(
        "UPI",
        "IMPS",
        "NEFT",
        "RTGS",
        "Credit Card",
        "Debit Card",
        "NetBanking",
        "Wallet",
        "Cash"
    )
}

enum class DashboardTimeframe(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    QUARTERLY("Quarterly"),
    YEARLY("Yearly")
}

enum class BillingCycle(val label: String, val annualMultiplier: Double, val daysInterval: Int) {
    WEEKLY("Weekly", 52.0, 7),
    MONTHLY("Monthly", 12.0, 30),
    QUARTERLY("Quarterly", 4.0, 90),
    YEARLY("Yearly", 1.0, 365)
}

enum class AlertSeverity {
    INFO,
    WARNING,
    CRITICAL
}

enum class AlertType {
    BUDGET_50,
    BUDGET_75,
    BUDGET_90,
    BUDGET_EXCEEDED,
    CATEGORY_EXCEEDED,
    LARGE_TXN,
    UNUSUAL_TXN,
    SUBSCRIPTION_DUE,
    SUBSCRIPTION_PRICE_CHANGE,
    SCAM_SMS
}

enum class ReportType(val title: String, val description: String) {
    MONTHLY_EXPENSE("Monthly Expense Report", "All expenses with categories, vendors & payment modes"),
    MONTHLY_INCOME("Monthly Income Report", "All salary, freelance, refund & interest inflows"),
    INCOME_VS_EXPENSE("Income vs. Expense Report", "Monthly net cash flow, savings & savings rate"),
    CATEGORY_SPENDING("Category-wise Spending", "Breakdown of total spend & share across categories"),
    VENDOR_SPENDING("Vendor-wise Spending", "Merchant totals, frequency & average ticket size"),
    TRANSACTION_HISTORY("Transaction History", "Complete itemized ledger with masked reference IDs"),
    BUDGET_PERFORMANCE("Budget Performance", "Budget targets vs. actuals & utilization percentage"),
    SUBSCRIPTION_REPORT("Subscription Report", "Active & paused recurring bills with annual burn")
}

enum class ReportDateRange(val label: String) {
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    THIS_YEAR("This Year"),
    ALL_TIME("All Time")
}

enum class ThemePreference(val label: String) {
    SYSTEM("System Default"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class AlertFrequency(val label: String) {
    INSTANT("Instant Alerts"),
    DAILY_DIGEST("Daily Digest")
}

data class SmsMessageUi(
    val id: Long,
    val sender: String,
    val body: String,
    val receivedAtMillis: Long,
    val folder: SmsFolder,
    val customFolderName: String?,
    val confidenceScore: Float,
    val isScamSuspicious: Boolean,
    val scamReason: String?,
    val extractedAmount: Double?,
    val extractedType: String?,
    val extractedVendor: String?,
    val extractedPaymentMethod: String?,
    val extractedRefId: String?,
    val userCorrectedFolder: String?,
    val isRead: Boolean
)

data class TransactionUi(
    val id: Long,
    val title: String,
    val vendor: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val paymentMethod: String,
    val referenceId: String,
    val accountMask: String,
    val timestampMillis: Long,
    val sourceSmsId: Long?,
    val isManual: Boolean,
    val notes: String
)

data class VendorSummary(
    val vendorName: String,
    val totalSpent: Double,
    val transactionCount: Int,
    val averageAmount: Double,
    val latestTimestamp: Long,
    val primaryCategory: String,
    val monthlyTrend: List<Pair<String, Double>>
)

data class BudgetStatusUi(
    val id: Long,
    val category: String,
    val monthlyLimit: Double,
    val spent: Double,
    val carryForward: Boolean,
    val alertAt50: Boolean,
    val alertAt75: Boolean,
    val alertAt90: Boolean,
    val alertAt100: Boolean
) {
    val remaining: Double get() = (monthlyLimit - spent).coerceAtLeast(0.0)
    val utilizationRatio: Float get() = if (monthlyLimit <= 0.0) 0f else (spent / monthlyLimit).toFloat()
    val utilizationPercent: Int get() = (utilizationRatio * 100f).toInt()
}

data class MonthlySummary(
    val monthKey: String,
    val monthLabel: String,
    val totalIncome: Double,
    val totalExpenses: Double,
    val netCashFlow: Double,
    val totalSavings: Double,
    val transactionCount: Int,
    val highestCategory: String,
    val highestCategoryAmount: Double,
    val topVendors: List<VendorSummary>,
    val averageDailySpend: Double,
    val budgetUtilizationPercent: Int
)
