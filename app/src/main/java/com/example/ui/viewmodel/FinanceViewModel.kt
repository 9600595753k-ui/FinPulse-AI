package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.ai.AiResponseChunk
import com.example.data.ai.FinancialAiRepository
import com.example.data.local.AlertEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.CustomFolderEntity
import com.example.data.local.FinPulseDatabase
import com.example.data.local.SettingsDataStore
import com.example.data.local.SubscriptionEntity
import com.example.data.local.UserSettings
import com.example.data.repository.FinanceRepository
import com.example.domain.export.CsvExporter
import com.example.domain.ml.SubscriptionDetector
import com.example.domain.model.AlertFrequency
import com.example.domain.model.BillingCycle
import com.example.domain.model.BudgetStatusUi
import com.example.domain.model.DashboardTimeframe
import com.example.domain.model.MonthlySummary
import com.example.domain.model.ReportDateRange
import com.example.domain.model.ReportType
import com.example.domain.model.SmsFolder
import com.example.domain.model.SmsMessageUi
import com.example.domain.model.ThemePreference
import com.example.domain.model.TransactionType
import com.example.domain.model.TransactionUi
import com.example.domain.model.VendorSummary
import com.example.domain.util.IndianCurrencyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AiChatMessage(
    val id: Long,
    val isUser: Boolean,
    val text: String,
    val isStreaming: Boolean = false,
    val isError: Boolean = false
)

data class FinanceDashboardState(
    val isLoading: Boolean = true,
    val selectedMonthKey: String = IndianCurrencyFormatter.currentMonthKey(),
    val availableMonths: List<String> = listOf(IndianCurrencyFormatter.currentMonthKey()),
    val selectedTimeframe: DashboardTimeframe = DashboardTimeframe.MONTHLY,
    val currentSummary: MonthlySummary? = null,
    val previousSummary: MonthlySummary? = null,
    val monthlySeries: List<MonthlySummary> = emptyList(),
    val trendPoints: List<Pair<String, Double>> = emptyList(),
    val categoryTotals: List<Pair<String, Double>> = emptyList(),
    val vendorSummaries: List<VendorSummary> = emptyList(),
    val budgetStatuses: List<BudgetStatusUi> = emptyList(),
    val overallBudgetLimit: Double = 45000.0,
    val overallBudgetSpent: Double = 0.0
)

class FinanceViewModel(application: Application) : AndroidViewModel(application) {

    private val database = FinPulseDatabase.getInstance(application)
    private val settingsStore = SettingsDataStore(application)
    private val repository = FinanceRepository(application, database, settingsStore)
    private val aiRepository = FinancialAiRepository()

    private val _selectedMonthKey = MutableStateFlow(IndianCurrencyFormatter.currentMonthKey())
    private val _selectedTimeframe = MutableStateFlow(DashboardTimeframe.MONTHLY)
    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    val smsMessages: StateFlow<List<SmsMessageUi>> = repository.smsMessagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val transactions: StateFlow<List<TransactionUi>> = repository.transactionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val customFolders: StateFlow<List<CustomFolderEntity>> = repository.customFoldersFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val budgets: StateFlow<List<BudgetEntity>> = repository.budgetsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val subscriptions: StateFlow<List<SubscriptionEntity>> = repository.subscriptionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val alerts: StateFlow<List<AlertEntity>> = repository.alertsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val settings: StateFlow<UserSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UserSettings())

    private val _aiMessages = MutableStateFlow<List<AiChatMessage>>(
        listOf(
            AiChatMessage(
                id = 1L,
                isUser = false,
                text = "Hello! I am FinPulse AI. Ask me anything about your monthly spending, savings rate, top categories, vendor patterns, or upcoming subscriptions. All analysis uses aggregated totals only — your OTPs and personal SMS never leave your device."
            )
        )
    )
    val aiMessages: StateFlow<List<AiChatMessage>> = _aiMessages.asStateFlow()

    private val _isAiGenerating = MutableStateFlow(false)
    val isAiGenerating: StateFlow<Boolean> = _isAiGenerating.asStateFlow()

    val dashboardState: StateFlow<FinanceDashboardState> = combine(
        transactions,
        budgets,
        settings,
        _selectedMonthKey,
        _selectedTimeframe
    ) { allTxns, allBudgets, userSettings, activeMonth, timeframe ->
        buildDashboardState(allTxns, allBudgets, userSettings, activeMonth, timeframe)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), FinanceDashboardState())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.ensureDemoDataSeeded()
        }
    }

    fun selectMonth(monthKey: String) {
        _selectedMonthKey.value = monthKey
    }

    fun selectTimeframe(timeframe: DashboardTimeframe) {
        _selectedTimeframe.value = timeframe
    }

    fun clearStatusBanner() {
        _statusBanner.value = null
    }

    fun scanDeviceSms() {
        viewModelScope.launch {
            val count = repository.scanDeviceInbox()
            _statusBanner.value = if (count > 0) {
                "Scanned & organized $count new SMS messages"
            } else {
                "SMS inbox is up to date (no new unread messages found)"
            }
        }
    }

    fun importManualSms(sender: String, body: String) {
        viewModelScope.launch {
            val added = repository.ingestSingleSms(sender.ifBlank { "VM-HDFCBK" }, body)
            _statusBanner.value = if (added) {
                "SMS classified & processed automatically"
            } else {
                "Duplicate SMS skipped"
            }
        }
    }

    fun correctSmsFolder(smsId: Long, folder: SmsFolder, customName: String? = null) {
        viewModelScope.launch {
            repository.correctSmsFolder(smsId, folder, customName)
            _statusBanner.value = "Moved to ${customName ?: folder.displayName} & ML model updated"
        }
    }

    fun markSmsRead(smsId: Long) {
        viewModelScope.launch { repository.markSmsRead(smsId) }
    }

    fun deleteSms(smsId: Long) {
        viewModelScope.launch { repository.deleteSms(smsId) }
    }

    fun createCustomFolder(name: String, keywordsCsv: String, senderPattern: String) {
        viewModelScope.launch {
            repository.addCustomFolder(name, keywordsCsv, senderPattern)
            _statusBanner.value = "Custom folder \"$name\" created & rules applied"
        }
    }

    fun deleteCustomFolder(id: Long) {
        viewModelScope.launch { repository.deleteCustomFolder(id) }
    }

    fun addManualTransaction(
        title: String,
        vendor: String,
        amount: Double,
        type: TransactionType,
        category: String,
        paymentMethod: String,
        referenceId: String,
        notes: String
    ) {
        viewModelScope.launch {
            repository.addManualTransaction(title, vendor, amount, type, category, paymentMethod, referenceId, notes)
            _statusBanner.value = "Transaction saved"
        }
    }

    fun updateTransaction(txn: TransactionUi) {
        viewModelScope.launch {
            repository.updateTransaction(txn)
            _statusBanner.value = "Transaction updated"
        }
    }

    fun deleteTransaction(txnId: Long) {
        viewModelScope.launch {
            repository.deleteTransaction(txnId)
            _statusBanner.value = "Transaction deleted"
        }
    }

    fun saveBudget(category: String, limit: Double, carryForward: Boolean) {
        viewModelScope.launch {
            repository.upsertBudget(
                category = category,
                limit = limit,
                carryForward = carryForward,
                alert50 = settings.value.alertAt50,
                alert75 = settings.value.alertAt75,
                alert90 = settings.value.alertAt90,
                alert100 = settings.value.alertAt100
            )
            _statusBanner.value = "Budget updated for $category"
        }
    }

    fun deleteBudget(budgetId: Long) {
        viewModelScope.launch { repository.deleteBudget(budgetId) }
    }

    fun saveSubscription(
        existingId: Long = 0L,
        name: String,
        vendor: String,
        amount: Double,
        cycle: BillingCycle,
        category: String,
        reminderDays: Int
    ) {
        viewModelScope.launch {
            val nextBilling = System.currentTimeMillis() + cycle.daysInterval * 24L * 60 * 60 * 1000L
            repository.upsertSubscription(
                SubscriptionEntity(
                    id = existingId,
                    name = name,
                    vendor = vendor,
                    amount = amount,
                    billingCycle = cycle.name,
                    nextBillingMillis = nextBilling,
                    category = category,
                    isPaused = false,
                    isAutoDetected = false,
                    reminderDaysBefore = reminderDays
                )
            )
            _statusBanner.value = "Subscription \"$name\" saved"
        }
    }

    fun toggleSubscriptionPause(sub: SubscriptionEntity) {
        viewModelScope.launch { repository.toggleSubscriptionPause(sub) }
    }

    fun deleteSubscription(id: Long) {
        viewModelScope.launch { repository.deleteSubscription(id) }
    }

    fun markAllAlertsRead() {
        viewModelScope.launch { repository.markAllAlertsRead() }
    }

    fun generateCsvReport(reportType: ReportType, range: ReportDateRange): String {
        val filteredTxns = CsvExporter.filterByDateRange(transactions.value, range)
        return CsvExporter.generateCsv(
            reportType = reportType,
            transactions = filteredTxns,
            budgets = dashboardState.value.budgetStatuses,
            subscriptions = subscriptions.value
        )
    }

    fun importCsvTransactions(csvText: String) {
        viewModelScope.launch {
            val count = repository.importCsvTransactions(csvText)
            _statusBanner.value = if (count > 0) "Imported $count transactions from CSV" else "No valid rows found in CSV"
        }
    }

    fun setThemePreference(pref: ThemePreference) {
        viewModelScope.launch { settingsStore.setThemePreference(pref) }
    }

    fun setLargeTransactionThreshold(amount: Double) {
        viewModelScope.launch { settingsStore.setLargeTransactionThreshold(amount) }
    }

    fun setDeviceNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDeviceNotifications(enabled) }
    }

    fun setAlertFrequency(freq: AlertFrequency) {
        viewModelScope.launch { settingsStore.setAlertFrequency(freq) }
    }

    fun setCarryForwardBudgets(carryForward: Boolean) {
        viewModelScope.launch { settingsStore.setCarryForwardBudgets(carryForward) }
    }

    fun markSmsPermissionRequested() {
        viewModelScope.launch { settingsStore.markSmsPermissionRequested() }
    }

    fun postStatusBanner(message: String) {
        _statusBanner.value = message
    }

    fun setThresholdAlerts(at50: Boolean, at75: Boolean, at90: Boolean, at100: Boolean) {
        viewModelScope.launch { settingsStore.setThresholdAlerts(at50, at75, at90, at100) }
    }

    fun setAllowAiAggregatedInsights(allowed: Boolean) {
        viewModelScope.launch { settingsStore.setAllowAiAggregatedInsights(allowed) }
    }

    fun deleteAllSmsData() {
        viewModelScope.launch {
            repository.deleteAllSmsData()
            _statusBanner.value = "All stored SMS messages and ML tokens deleted"
        }
    }

    fun deleteAllTransactionData() {
        viewModelScope.launch {
            repository.deleteAllTransactionData()
            _statusBanner.value = "All transaction records and alerts deleted"
        }
    }

    fun resetDemoData() {
        viewModelScope.launch {
            repository.resetAndReseedDemoData()
            _statusBanner.value = "Demo dataset restored across 3 months"
        }
    }

    fun askAssistant(question: String) {
        if (question.isBlank() || _isAiGenerating.value) return
        val userMsg = AiChatMessage(id = System.nanoTime(), isUser = true, text = question.trim())

        if (!settings.value.allowAiAggregatedInsights) {
            _aiMessages.value = _aiMessages.value + userMsg + AiChatMessage(
                id = System.nanoTime() + 1,
                isUser = false,
                text = "AI Aggregated Insights are currently turned off in Settings > Privacy & Security. Enable \"Share Anonymized Totals with Gemini\" to ask questions, or review your local Dashboard metrics."
            )
            return
        }

        val state = dashboardState.value
        val currentSummary = state.currentSummary ?: return
        val digest = aiRepository.buildPrivacySafeFinancialContext(
            currentSummary = currentSummary,
            previousSummary = state.previousSummary,
            transactions = transactions.value,
            budgets = state.budgetStatuses,
            subscriptions = subscriptions.value
        )

        val assistantId = System.nanoTime() + 1
        _aiMessages.value = _aiMessages.value + userMsg + AiChatMessage(
            id = assistantId,
            isUser = false,
            text = "Analyzing your financial summary...",
            isStreaming = true
        )

        viewModelScope.launch {
            _isAiGenerating.value = true
            aiRepository.askFinancialAssistant(question.trim(), digest).collect { chunk ->
                when (chunk) {
                    is AiResponseChunk.Loading -> Unit
                    is AiResponseChunk.Streaming -> {
                        updateAssistantMessage(assistantId, chunk.text, isStreaming = true, isError = false)
                    }
                    is AiResponseChunk.Completed -> {
                        updateAssistantMessage(assistantId, chunk.text, isStreaming = false, isError = false)
                        _isAiGenerating.value = false
                    }
                    is AiResponseChunk.Error -> {
                        updateAssistantMessage(assistantId, "Error: ${chunk.message}", isStreaming = false, isError = true)
                        _isAiGenerating.value = false
                    }
                }
            }
        }
    }

    private fun updateAssistantMessage(id: Long, text: String, isStreaming: Boolean, isError: Boolean) {
        _aiMessages.value = _aiMessages.value.map { msg ->
            if (msg.id == id) msg.copy(text = text, isStreaming = isStreaming, isError = isError) else msg
        }
    }

    fun getMonthlySubscriptionCost(): Double = SubscriptionDetector.calculateMonthlyEquivalent(subscriptions.value)
    fun getAnnualSubscriptionCost(): Double = SubscriptionDetector.calculateAnnualCost(subscriptions.value)

    private fun buildDashboardState(
        allTxns: List<TransactionUi>,
        allBudgets: List<BudgetEntity>,
        userSettings: UserSettings,
        activeMonth: String,
        timeframe: DashboardTimeframe
    ): FinanceDashboardState {
        val currentMonth = IndianCurrencyFormatter.currentMonthKey()
        val monthKeys = (allTxns.map { IndianCurrencyFormatter.monthKey(it.timestampMillis) } + currentMonth)
            .distinct()
            .sortedDescending()

        val overallBudget = allBudgets.firstOrNull { it.category == "OVERALL" }?.monthlyLimit
            ?: userSettings.overallMonthlyBudget

        fun summarizeMonth(monthKey: String): MonthlySummary {
            val mTxns = allTxns.filter { IndianCurrencyFormatter.monthKey(it.timestampMillis) == monthKey }
            val income = mTxns.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
            val expenses = mTxns.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
            val net = income - expenses
            val byCategory = mTxns.filter { it.type == TransactionType.EXPENSE }
                .groupBy { it.category }
                .mapValues { (_, list) -> list.sumOf { it.amount } }
            val highestCat = byCategory.maxByOrNull { it.value }
            val vendors = buildVendorSummaries(mTxns, allTxns)
            val days = IndianCurrencyFormatter.daysInMonth(monthKey).coerceAtLeast(1)
            val utilPct = if (overallBudget > 0) (expenses / overallBudget * 100.0).toInt() else 0
            return MonthlySummary(
                monthKey = monthKey,
                monthLabel = IndianCurrencyFormatter.monthLabel(monthKey),
                totalIncome = income,
                totalExpenses = expenses,
                netCashFlow = net,
                totalSavings = net.coerceAtLeast(0.0),
                transactionCount = mTxns.size,
                highestCategory = highestCat?.key ?: "None",
                highestCategoryAmount = highestCat?.value ?: 0.0,
                topVendors = vendors,
                averageDailySpend = expenses / days,
                budgetUtilizationPercent = utilPct
            )
        }

        val monthlySeries = monthKeys.sorted().map { summarizeMonth(it) }
        val activeSummary = summarizeMonth(activeMonth)
        val prevKey = IndianCurrencyFormatter.previousMonthKey(activeMonth)
        val prevSummary = if (monthKeys.contains(prevKey)) summarizeMonth(prevKey) else null

        val timeframeTxns = filterTransactionsByTimeframe(allTxns, activeMonth, timeframe)
        val expensesInScope = timeframeTxns.filter { it.type == TransactionType.EXPENSE }

        val categoryTotals = expensesInScope
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        val trendPoints = buildTrendPoints(expensesInScope, timeframe)
        val vendorSummaries = buildVendorSummaries(expensesInScope, allTxns)

        // Budgets always evaluate against the active month's spending
        val activeMonthExpenses = allTxns.filter {
            it.type == TransactionType.EXPENSE && IndianCurrencyFormatter.monthKey(it.timestampMillis) == activeMonth
        }
        val activeMonthSpent = activeMonthExpenses.sumOf { it.amount }
        val budgetStatuses = allBudgets.map { budget ->
            val spent = if (budget.category == "OVERALL") {
                activeMonthSpent
            } else {
                activeMonthExpenses.filter { it.category == budget.category }.sumOf { it.amount }
            }
            BudgetStatusUi(
                id = budget.id,
                category = budget.category,
                monthlyLimit = budget.monthlyLimit,
                spent = spent,
                carryForward = budget.carryForward,
                alertAt50 = budget.alertAt50,
                alertAt75 = budget.alertAt75,
                alertAt90 = budget.alertAt90,
                alertAt100 = budget.alertAt100
            )
        }

        return FinanceDashboardState(
            isLoading = false,
            selectedMonthKey = activeMonth,
            availableMonths = monthKeys,
            selectedTimeframe = timeframe,
            currentSummary = activeSummary,
            previousSummary = prevSummary,
            monthlySeries = monthlySeries,
            trendPoints = trendPoints,
            categoryTotals = categoryTotals,
            vendorSummaries = vendorSummaries,
            budgetStatuses = budgetStatuses,
            overallBudgetLimit = overallBudget,
            overallBudgetSpent = activeMonthSpent
        )
    }

    private fun filterTransactionsByTimeframe(
        txns: List<TransactionUi>,
        activeMonth: String,
        timeframe: DashboardTimeframe
    ): List<TransactionUi> {
        val now = System.currentTimeMillis()
        return when (timeframe) {
            DashboardTimeframe.DAILY -> {
                val cutoff = now - 7L * 24 * 60 * 60 * 1000L
                txns.filter { it.timestampMillis >= cutoff }
            }
            DashboardTimeframe.WEEKLY -> {
                val cutoff = now - 28L * 24 * 60 * 60 * 1000L
                txns.filter { it.timestampMillis >= cutoff }
            }
            DashboardTimeframe.MONTHLY -> {
                txns.filter { IndianCurrencyFormatter.monthKey(it.timestampMillis) == activeMonth }
            }
            DashboardTimeframe.QUARTERLY -> {
                val cutoff = now - 92L * 24 * 60 * 60 * 1000L
                txns.filter { it.timestampMillis >= cutoff }
            }
            DashboardTimeframe.YEARLY -> {
                val cutoff = now - 365L * 24 * 60 * 60 * 1000L
                txns.filter { it.timestampMillis >= cutoff }
            }
        }
    }

    private fun buildTrendPoints(
        expenses: List<TransactionUi>,
        timeframe: DashboardTimeframe
    ): List<Pair<String, Double>> {
        if (expenses.isEmpty()) return emptyList()
        return when (timeframe) {
            DashboardTimeframe.DAILY, DashboardTimeframe.WEEKLY -> {
                expenses.groupBy { IndianCurrencyFormatter.formatDate(it.timestampMillis).take(6) }
                    .entries
                    .sortedBy { it.value.minOf { t -> t.timestampMillis } }
                    .takeLast(7)
                    .map { it.key to it.value.sumOf { t -> t.amount } }
            }
            DashboardTimeframe.MONTHLY -> {
                expenses.groupBy {
                    val day = IndianCurrencyFormatter.formatDate(it.timestampMillis).take(2).toIntOrNull() ?: 1
                    when {
                        day <= 7 -> "W1"
                        day <= 14 -> "W2"
                        day <= 21 -> "W3"
                        else -> "W4"
                    }
                }.let { map ->
                    listOf("W1", "W2", "W3", "W4").map { w -> w to (map[w]?.sumOf { it.amount } ?: 0.0) }
                }
            }
            DashboardTimeframe.QUARTERLY, DashboardTimeframe.YEARLY -> {
                expenses.groupBy { IndianCurrencyFormatter.monthKey(it.timestampMillis) }
                    .toSortedMap()
                    .entries
                    .map { IndianCurrencyFormatter.monthLabel(it.key).take(3) to it.value.sumOf { t -> t.amount } }
            }
        }
    }

    private fun buildVendorSummaries(
        scopedTxns: List<TransactionUi>,
        allTxns: List<TransactionUi>
    ): List<VendorSummary> {
        val expenses = scopedTxns.filter { it.type == TransactionType.EXPENSE }
        return expenses.groupBy { it.vendor }
            .map { (vendor, list) ->
                val total = list.sumOf { it.amount }
                val avg = total / list.size
                val latest = list.maxOf { it.timestampMillis }
                val primaryCat = list.groupingBy { it.category }.eachCount().maxByOrNull { it.value }?.key ?: "Other Expenses"
                val vendorHistory = allTxns
                    .filter { it.vendor == vendor && it.type == TransactionType.EXPENSE }
                    .groupBy { IndianCurrencyFormatter.monthKey(it.timestampMillis) }
                    .toSortedMap()
                    .entries
                    .map { IndianCurrencyFormatter.monthLabel(it.key).take(3) to it.value.sumOf { t -> t.amount } }
                VendorSummary(
                    vendorName = vendor,
                    totalSpent = total,
                    transactionCount = list.size,
                    averageAmount = avg,
                    latestTimestamp = latest,
                    primaryCategory = primaryCat,
                    monthlyTrend = vendorHistory
                )
            }
            .sortedByDescending { it.totalSpent }
    }
}
