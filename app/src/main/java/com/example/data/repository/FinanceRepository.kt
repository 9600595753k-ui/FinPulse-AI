package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.local.AlertEntity
import com.example.data.local.BudgetEntity
import com.example.data.local.CustomFolderEntity
import com.example.data.local.FinPulseDatabase
import com.example.data.local.LearnedTokenWeightEntity
import com.example.data.local.SettingsDataStore
import com.example.data.local.SmsMessageEntity
import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionEntity
import com.example.data.local.UserSettings
import com.example.domain.ml.IndianFinancialSmsParser
import com.example.domain.ml.SmsClassifierEngine
import com.example.domain.ml.SubscriptionDetector
import com.example.domain.model.AlertSeverity
import com.example.domain.model.AlertType
import com.example.domain.model.SmsFolder
import com.example.domain.model.SmsMessageUi
import com.example.domain.model.TransactionType
import com.example.domain.model.TransactionUi
import com.example.domain.notifications.NotificationHelper
import com.example.domain.util.IndianCurrencyFormatter
import com.example.security.CryptoVault
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class FinanceRepository(
    private val context: Context,
    private val db: FinPulseDatabase,
    val settingsStore: SettingsDataStore
) {
    private val smsDao = db.smsDao()
    private val customFolderDao = db.customFolderDao()
    private val learnedWeightDao = db.learnedWeightDao()
    private val txnDao = db.transactionDao()
    private val budgetDao = db.budgetDao()
    private val subDao = db.subscriptionDao()
    private val alertDao = db.alertDao()

    val smsMessagesFlow: Flow<List<SmsMessageUi>> = smsDao.observeAllSms()
        .map { list ->
            list.map { entity ->
                SmsMessageUi(
                    id = entity.id,
                    sender = entity.sender,
                    body = CryptoVault.decrypt(entity.encryptedBody),
                    receivedAtMillis = entity.receivedAtMillis,
                    folder = runCatching { SmsFolder.valueOf(entity.folder) }.getOrDefault(SmsFolder.PERSONAL),
                    customFolderName = entity.customFolderName,
                    confidenceScore = entity.confidenceScore,
                    isScamSuspicious = entity.isScamSuspicious,
                    scamReason = entity.scamReason,
                    extractedAmount = entity.extractedAmount,
                    extractedType = entity.extractedType,
                    extractedVendor = entity.extractedVendor,
                    extractedPaymentMethod = entity.extractedPaymentMethod,
                    extractedRefId = entity.encryptedRefId?.let { CryptoVault.decrypt(it) },
                    userCorrectedFolder = entity.userCorrectedFolder,
                    isRead = entity.isRead
                )
            }
        }
        .flowOn(Dispatchers.Default)

    val transactionsFlow: Flow<List<TransactionUi>> = txnDao.observeAllTransactions()
        .map { list ->
            list.map { entity ->
                TransactionUi(
                    id = entity.id,
                    title = entity.title,
                    vendor = entity.vendor,
                    amount = entity.amount,
                    type = runCatching { TransactionType.valueOf(entity.type) }.getOrDefault(TransactionType.EXPENSE),
                    category = entity.category,
                    paymentMethod = entity.paymentMethod,
                    referenceId = CryptoVault.decrypt(entity.encryptedRefId),
                    accountMask = CryptoVault.decrypt(entity.encryptedAccountMask),
                    timestampMillis = entity.timestampMillis,
                    sourceSmsId = entity.sourceSmsId,
                    isManual = entity.isManual,
                    notes = entity.notes
                )
            }
        }
        .flowOn(Dispatchers.Default)

    val customFoldersFlow: Flow<List<CustomFolderEntity>> = customFolderDao.observeCustomFolders()
    val budgetsFlow: Flow<List<BudgetEntity>> = budgetDao.observeAllBudgets()
    val subscriptionsFlow: Flow<List<SubscriptionEntity>> = subDao.observeAllSubscriptions()
    val alertsFlow: Flow<List<AlertEntity>> = alertDao.observeAllAlerts()
    val settingsFlow: Flow<UserSettings> = settingsStore.settingsFlow

    suspend fun ensureDemoDataSeeded() = withContext(Dispatchers.IO) {
        val settings = settingsStore.settingsFlow.first()
        if (!settings.hasSeededDemoData && txnDao.getAllTransactionsOnce().isEmpty() && smsDao.getAllSmsOnce().isEmpty()) {
            seedSampleData()
        }
    }

    suspend fun seedSampleData() = withContext(Dispatchers.IO) {
        db.withTransaction {
            SampleDataSeeder.getDefaultCustomFolders().forEach {
                customFolderDao.insertCustomFolder(it)
            }
            SampleDataSeeder.getDefaultBudgets().forEach {
                budgetDao.upsertBudget(it)
            }
            val customFolders = customFolderDao.getCustomFoldersOnce()
            val weights = learnedWeightDao.getAllWeights()
            val existingDedupKeys = txnDao.getAllTransactionsOnce().map { it.dedupKey }.toMutableSet()

            SampleDataSeeder.getSampleSmsList().forEach { spec ->
                ingestSmsInternal(
                    sender = spec.sender,
                    body = spec.body,
                    receivedAtMillis = spec.timestampMillis,
                    triggerNotifications = false,
                    cachedCustomFolders = customFolders,
                    cachedWeights = weights,
                    cachedTxnDedupKeys = existingDedupKeys
                )
            }

            val existingSubVendors = subDao.getAllSubscriptionsOnce().map { it.vendor.lowercase() }.toSet()
            SampleDataSeeder.getExtraSubscriptions().forEach { sub ->
                if (sub.vendor.lowercase() !in existingSubVendors) {
                    subDao.upsertSubscription(sub)
                }
            }
            refreshSubscriptionDetectionsInternal()
            evaluateAllAlertsInternal(triggerDeviceNotification = false)
        }
        settingsStore.markDemoDataSeeded(true)
    }

    suspend fun scanDeviceInbox(): Int = withContext(Dispatchers.IO) {
        val rawMessages = DeviceSmsReader.readLatestInboxSms(context)
        if (rawMessages.isEmpty()) return@withContext 0
        var importedCount = 0
        db.withTransaction {
            val customFolders = customFolderDao.getCustomFoldersOnce()
            val weights = learnedWeightDao.getAllWeights()
            val existingDedupKeys = txnDao.getAllTransactionsOnce().map { it.dedupKey }.toMutableSet()

            for (raw in rawMessages) {
                val inserted = ingestSmsInternal(
                    sender = raw.sender,
                    body = raw.body,
                    receivedAtMillis = raw.receivedAtMillis,
                    triggerNotifications = true,
                    cachedCustomFolders = customFolders,
                    cachedWeights = weights,
                    cachedTxnDedupKeys = existingDedupKeys
                )
                if (inserted) importedCount++
            }
            if (importedCount > 0) {
                refreshSubscriptionDetectionsInternal()
                evaluateAllAlertsInternal(triggerDeviceNotification = true)
            }
        }
        importedCount
    }

    suspend fun ingestSingleSms(
        sender: String,
        body: String,
        receivedAtMillis: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        var added = false
        db.withTransaction {
            added = ingestSmsInternal(sender, body, receivedAtMillis, triggerNotifications = true)
            refreshSubscriptionDetectionsInternal()
            evaluateAllAlertsInternal(triggerDeviceNotification = true)
        }
        added
    }

    private suspend fun ingestSmsInternal(
        sender: String,
        body: String,
        receivedAtMillis: Long,
        triggerNotifications: Boolean,
        cachedCustomFolders: List<CustomFolderEntity>? = null,
        cachedWeights: List<LearnedTokenWeightEntity>? = null,
        cachedTxnDedupKeys: MutableSet<String>? = null
    ): Boolean {
        val dedupHash = IndianFinancialSmsParser.sha256("${sender.trim()}|${body.trim()}|${receivedAtMillis / 60_000L}")
        val customFolders = cachedCustomFolders ?: customFolderDao.getCustomFoldersOnce()
        val weights = cachedWeights ?: learnedWeightDao.getAllWeights()
        val classification = SmsClassifierEngine.classify(
            sender = sender,
            body = body,
            timestampMillis = receivedAtMillis,
            customFolders = customFolders,
            learnedWeights = weights
        )

        val parsed = classification.parsedTransaction
        val smsEntity = SmsMessageEntity(
            sender = sender,
            encryptedBody = CryptoVault.encrypt(body),
            receivedAtMillis = receivedAtMillis,
            folder = classification.folder.name,
            customFolderName = classification.customFolderName,
            confidenceScore = classification.confidence,
            isScamSuspicious = classification.isScam,
            scamReason = classification.scamReason,
            extractedAmount = parsed?.amount,
            extractedType = parsed?.type?.name,
            extractedVendor = parsed?.vendor,
            extractedPaymentMethod = parsed?.paymentMethod,
            encryptedRefId = parsed?.referenceId?.takeIf { it.isNotBlank() }?.let { CryptoVault.encrypt(it) },
            isRead = false,
            dedupHash = dedupHash
        )

        val smsRowId = smsDao.insertSms(smsEntity)
        if (smsRowId == -1L) return false

        if (classification.isScam) {
            createAlert(
                title = "Suspicious SMS Blocked",
                message = "Message from $sender flagged: ${classification.scamReason ?: "Potential phishing"}",
                severity = AlertSeverity.CRITICAL,
                type = AlertType.SCAM_SMS,
                notifyDevice = triggerNotifications
            )
        }

        val isDuplicateTxn = if (parsed == null) {
            true
        } else if (cachedTxnDedupKeys != null) {
            !cachedTxnDedupKeys.add(parsed.dedupKey)
        } else {
            txnDao.existsByDedupKey(parsed.dedupKey)
        }

        if (parsed != null && !isDuplicateTxn) {
            val txnEntity = TransactionEntity(
                title = parsed.title,
                vendor = parsed.vendor,
                amount = parsed.amount,
                type = parsed.type.name,
                category = parsed.category,
                paymentMethod = parsed.paymentMethod,
                encryptedRefId = CryptoVault.encrypt(parsed.referenceId),
                encryptedAccountMask = CryptoVault.encrypt(parsed.accountMask),
                timestampMillis = receivedAtMillis,
                sourceSmsId = smsRowId,
                isManual = false,
                notes = "Auto-extracted from $sender",
                dedupKey = parsed.dedupKey
            )
            txnDao.insertTransaction(txnEntity)
            if (triggerNotifications) {
                checkTransactionSpecificAlerts(txnEntity, true)
            }
        }
        return true
    }

    suspend fun correctSmsFolder(
        smsId: Long,
        newFolder: SmsFolder,
        customFolderName: String? = null
    ) = withContext(Dispatchers.IO) {
        val smsList = smsDao.getAllSmsOnce()
        val target = smsList.firstOrNull { it.id == smsId } ?: return@withContext
        val plainBody = CryptoVault.decrypt(target.encryptedBody)
        val tokens = SmsClassifierEngine.tokenize(target.sender, plainBody)

        db.withTransaction {
            for (token in tokens.take(20)) {
                val existingNew = learnedWeightDao.getWeight(token, newFolder.name)
                val newWeight = ((existingNew?.weight ?: 0f) + 1.2f).coerceAtMost(8.0f)
                learnedWeightDao.upsertWeight(
                    LearnedTokenWeightEntity(
                        id = existingNew?.id ?: 0,
                        token = token,
                        folder = newFolder.name,
                        weight = newWeight
                    )
                )
                if (target.folder != newFolder.name) {
                    val existingOld = learnedWeightDao.getWeight(token, target.folder)
                    if (existingOld != null) {
                        learnedWeightDao.upsertWeight(
                            existingOld.copy(weight = (existingOld.weight - 0.6f).coerceAtLeast(-3.0f))
                        )
                    }
                }
            }
            smsDao.updateSmsFolder(smsId, newFolder.name, customFolderName)
        }
    }

    suspend fun markSmsRead(smsId: Long) = withContext(Dispatchers.IO) { smsDao.markSmsRead(smsId) }

    suspend fun deleteSms(smsId: Long) = withContext(Dispatchers.IO) { smsDao.deleteSmsById(smsId) }

    suspend fun addCustomFolder(
        name: String,
        keywordsCsv: String,
        senderPattern: String
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            customFolderDao.insertCustomFolder(
                CustomFolderEntity(
                    name = name.trim(),
                    keywordRulesCsv = keywordsCsv.trim(),
                    senderPattern = senderPattern.trim()
                )
            )
            val allSms = smsDao.getAllSmsOnce()
            val customFolders = customFolderDao.getCustomFoldersOnce()
            val weights = learnedWeightDao.getAllWeights()
            for (sms in allSms) {
                if (sms.userCorrectedFolder == null) {
                    val plain = CryptoVault.decrypt(sms.encryptedBody)
                    val reclassified = SmsClassifierEngine.classify(
                        sms.sender, plain, sms.receivedAtMillis, customFolders, weights
                    )
                    if (reclassified.folder == SmsFolder.CUSTOM) {
                        smsDao.updateSms(
                            sms.copy(
                                folder = SmsFolder.CUSTOM.name,
                                customFolderName = reclassified.customFolderName,
                                confidenceScore = reclassified.confidence
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun deleteCustomFolder(id: Long) = withContext(Dispatchers.IO) { customFolderDao.deleteCustomFolder(id) }

    suspend fun addManualTransaction(
        title: String,
        vendor: String,
        amount: Double,
        type: TransactionType,
        category: String,
        paymentMethod: String,
        referenceId: String,
        notes: String,
        timestampMillis: Long = System.currentTimeMillis()
    ) = withContext(Dispatchers.IO) {
        db.withTransaction {
            val dedupKey = IndianFinancialSmsParser.sha256("MANUAL|${System.nanoTime()}|$vendor|$amount")
            val entity = TransactionEntity(
                title = title.ifBlank { vendor },
                vendor = vendor.trim(),
                amount = amount,
                type = type.name,
                category = category,
                paymentMethod = paymentMethod,
                encryptedRefId = CryptoVault.encrypt(referenceId.ifBlank { "MAN-${(100000..999999).random()}" }),
                encryptedAccountMask = CryptoVault.encrypt("Manual"),
                timestampMillis = timestampMillis,
                sourceSmsId = null,
                isManual = true,
                notes = notes,
                dedupKey = dedupKey
            )
            txnDao.insertTransaction(entity)
            checkTransactionSpecificAlerts(entity, triggerNotifications = true)
            refreshSubscriptionDetectionsInternal()
            evaluateAllAlertsInternal(triggerDeviceNotification = true)
        }
    }

    suspend fun updateTransaction(txn: TransactionUi) = withContext(Dispatchers.IO) {
        val existing = txnDao.getAllTransactionsOnce().firstOrNull { it.id == txn.id } ?: return@withContext
        txnDao.updateTransaction(
            existing.copy(
                title = txn.title,
                vendor = txn.vendor,
                amount = txn.amount,
                type = txn.type.name,
                category = txn.category,
                paymentMethod = txn.paymentMethod,
                notes = txn.notes
            )
        )
        evaluateAllAlertsInternal(triggerDeviceNotification = false)
    }

    suspend fun deleteTransaction(txnId: Long) = withContext(Dispatchers.IO) { txnDao.deleteTransactionById(txnId) }

    suspend fun upsertBudget(
        category: String,
        limit: Double,
        carryForward: Boolean,
        alert50: Boolean = true,
        alert75: Boolean = true,
        alert90: Boolean = true,
        alert100: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val currentMonth = IndianCurrencyFormatter.currentMonthKey()
        val existing = budgetDao.getAllBudgetsOnce().firstOrNull { it.category == category }
        budgetDao.upsertBudget(
            BudgetEntity(
                id = existing?.id ?: 0,
                category = category,
                monthlyLimit = limit,
                carryForward = carryForward,
                lastResetMonth = currentMonth,
                alertAt50 = alert50,
                alertAt75 = alert75,
                alertAt90 = alert90,
                alertAt100 = alert100
            )
        )
        if (category == "OVERALL") {
            settingsStore.setOverallMonthlyBudget(limit)
        }
        evaluateAllAlertsInternal(triggerDeviceNotification = false)
    }

    suspend fun deleteBudget(budgetId: Long) = withContext(Dispatchers.IO) { budgetDao.deleteBudgetById(budgetId) }

    suspend fun upsertSubscription(sub: SubscriptionEntity) = withContext(Dispatchers.IO) {
        subDao.upsertSubscription(sub)
        evaluateAllAlertsInternal(triggerDeviceNotification = false)
    }

    suspend fun toggleSubscriptionPause(sub: SubscriptionEntity) = withContext(Dispatchers.IO) {
        subDao.updateSubscription(sub.copy(isPaused = !sub.isPaused))
    }

    suspend fun deleteSubscription(id: Long) = withContext(Dispatchers.IO) { subDao.deleteSubscriptionById(id) }

    private suspend fun refreshSubscriptionDetectionsInternal() {
        val txns = txnDao.getAllTransactionsOnce()
        val existing = subDao.getAllSubscriptionsOnce()
        val detected = SubscriptionDetector.detectFromTransactions(txns, existing)
        for (sub in detected) {
            subDao.upsertSubscription(sub)
            if (sub.previousAmount != null && sub.amount > sub.previousAmount) {
                createAlert(
                    title = "Subscription Price Increase",
                    message = "${sub.name} changed from ${IndianCurrencyFormatter.format(sub.previousAmount)} to ${IndianCurrencyFormatter.format(sub.amount)}.",
                    severity = AlertSeverity.WARNING,
                    type = AlertType.SUBSCRIPTION_PRICE_CHANGE,
                    notifyDevice = true
                )
            }
        }
    }

    private suspend fun checkTransactionSpecificAlerts(txn: TransactionEntity, triggerNotifications: Boolean) {
        if (txn.type != TransactionType.EXPENSE.name) return
        val settings = settingsStore.settingsFlow.first()
        if (txn.amount >= settings.largeTransactionThreshold) {
            createAlert(
                title = "Large Transaction Detected",
                message = "${IndianCurrencyFormatter.format(txn.amount)} spent at ${txn.vendor} via ${txn.paymentMethod}.",
                severity = AlertSeverity.WARNING,
                type = AlertType.LARGE_TXN,
                notifyDevice = triggerNotifications && settings.enableDeviceNotifications
            )
        }

        val sameCategory = txnDao.getAllTransactionsOnce()
            .filter { it.category == txn.category && it.type == TransactionType.EXPENSE.name && it.id != txn.id }
        if (sameCategory.size >= 2) {
            val avg = sameCategory.map { it.amount }.average()
            if (avg > 0 && txn.amount > avg * 2.5 && txn.amount > 1000.0) {
                createAlert(
                    title = "Unusual Spend in ${txn.category}",
                    message = "${IndianCurrencyFormatter.format(txn.amount)} at ${txn.vendor} is over 2.5x your typical ${IndianCurrencyFormatter.format(avg)} average.",
                    severity = AlertSeverity.WARNING,
                    type = AlertType.UNUSUAL_TXN,
                    notifyDevice = triggerNotifications && settings.enableDeviceNotifications
                )
            }
        }
    }

    private suspend fun evaluateAllAlertsInternal(triggerDeviceNotification: Boolean) {
        val settings = settingsStore.settingsFlow.first()
        val currentMonth = IndianCurrencyFormatter.currentMonthKey()
        val txns = txnDao.getAllTransactionsOnce().filter {
            it.type == TransactionType.EXPENSE.name &&
                IndianCurrencyFormatter.monthKey(it.timestampMillis) == currentMonth
        }
        val totalSpent = txns.sumOf { it.amount }
        val budgets = budgetDao.getAllBudgetsOnce()
        val existingAlertTitles = alertDao.getAllAlertsOnce().map { it.title to it.message }.toSet()

        for (budget in budgets) {
            val spent = if (budget.category == "OVERALL") totalSpent else txns.filter { it.category == budget.category }.sumOf { it.amount }
            if (budget.monthlyLimit <= 0.0) continue
            val pct = (spent / budget.monthlyLimit * 100.0).toInt()
            val scopeLabel = if (budget.category == "OVERALL") "Monthly Budget" else "${budget.category} Budget"

            val (thresholdType, severity, thresholdLabel) = when {
                pct >= 100 && budget.alertAt100 && settings.alertAt100 -> Triple(
                    if (budget.category == "OVERALL") AlertType.BUDGET_EXCEEDED else AlertType.CATEGORY_EXCEEDED,
                    AlertSeverity.CRITICAL,
                    "exceeded 100%"
                )
                pct >= 90 && budget.alertAt90 && settings.alertAt90 -> Triple(AlertType.BUDGET_90, AlertSeverity.CRITICAL, "reached 90%")
                pct >= 75 && budget.alertAt75 && settings.alertAt75 -> Triple(AlertType.BUDGET_75, AlertSeverity.WARNING, "reached 75%")
                pct >= 50 && budget.alertAt50 && settings.alertAt50 -> Triple(AlertType.BUDGET_50, AlertSeverity.INFO, "reached 50%")
                else -> continue
            }

            val title = "$scopeLabel Alert ($pct%)"
            val message = "You have spent ${IndianCurrencyFormatter.format(spent)} of your ${IndianCurrencyFormatter.format(budget.monthlyLimit)} limit ($thresholdLabel)."
            if ((title to message) !in existingAlertTitles) {
                createAlert(
                    title = title,
                    message = message,
                    severity = severity,
                    type = thresholdType,
                    notifyDevice = triggerDeviceNotification && settings.enableDeviceNotifications
                )
            }
        }

        val now = System.currentTimeMillis()
        val subs = subDao.getAllSubscriptionsOnce().filter { !it.isPaused }
        for (sub in subs) {
            val daysUntil = ((sub.nextBillingMillis - now) / (24L * 60 * 60 * 1000L)).toInt()
            if (daysUntil in 0..sub.reminderDaysBefore) {
                val title = "Upcoming Renewal: ${sub.name}"
                val message = "${IndianCurrencyFormatter.format(sub.amount)} due in ${if (daysUntil == 0) "today" else "$daysUntil day(s)"}."
                if ((title to message) !in existingAlertTitles) {
                    createAlert(
                        title = title,
                        message = message,
                        severity = AlertSeverity.INFO,
                        type = AlertType.SUBSCRIPTION_DUE,
                        notifyDevice = triggerDeviceNotification && settings.enableDeviceNotifications
                    )
                }
            }
        }
    }

    private suspend fun createAlert(
        title: String,
        message: String,
        severity: AlertSeverity,
        type: AlertType,
        notifyDevice: Boolean
    ) {
        alertDao.insertAlert(
            AlertEntity(
                title = title,
                message = message,
                severity = severity.name,
                type = type.name
            )
        )
        if (notifyDevice) {
            NotificationHelper.postAlertNotification(
                context = context,
                title = title,
                message = message,
                notificationId = (title.hashCode() and 0x7FFFFFFF)
            )
        }
    }

    suspend fun markAllAlertsRead() = withContext(Dispatchers.IO) { alertDao.markAllRead() }

    suspend fun importCsvTransactions(csvText: String): Int = withContext(Dispatchers.IO) {
        val lines = csvText.lines().filter { it.isNotBlank() }
        if (lines.size <= 1) return@withContext 0
        var count = 0
        db.withTransaction {
            for (line in lines.drop(1)) {
                val cols = line.split(",").map { it.trim().trim('"') }
                if (cols.size < 4) continue
                val vendor = cols.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "CSV Entry"
                val category = cols.getOrNull(1)?.takeIf { it.isNotBlank() } ?: "Other Expenses"
                val amount = cols.getOrNull(2)?.replace(",", "")?.toDoubleOrNull() ?: continue
                val typeStr = cols.getOrNull(3)?.uppercase() ?: "EXPENSE"
                val type = if (typeStr.contains("INCOME") || typeStr.contains("CREDIT")) TransactionType.INCOME else TransactionType.EXPENSE
                val payment = cols.getOrNull(4)?.takeIf { it.isNotBlank() } ?: "UPI"
                val dedupKey = IndianFinancialSmsParser.sha256("CSV|${System.nanoTime()}|$vendor|$amount")
                txnDao.insertTransaction(
                    TransactionEntity(
                        title = vendor,
                        vendor = vendor,
                        amount = amount,
                        type = type.name,
                        category = category,
                        paymentMethod = payment,
                        encryptedRefId = CryptoVault.encrypt("CSV-${(10000..99999).random()}"),
                        encryptedAccountMask = CryptoVault.encrypt("CSV"),
                        timestampMillis = System.currentTimeMillis(),
                        sourceSmsId = null,
                        isManual = true,
                        notes = "Imported via CSV",
                        dedupKey = dedupKey
                    )
                )
                count++
            }
            if (count > 0) {
                refreshSubscriptionDetectionsInternal()
                evaluateAllAlertsInternal(triggerDeviceNotification = false)
            }
        }
        count
    }

    suspend fun deleteAllSmsData() = withContext(Dispatchers.IO) {
        db.withTransaction {
            smsDao.deleteAllSms()
            learnedWeightDao.clearAllWeights()
        }
    }

    suspend fun deleteAllTransactionData() = withContext(Dispatchers.IO) {
        db.withTransaction {
            txnDao.deleteAllTransactions()
            alertDao.deleteAllAlerts()
        }
    }

    suspend fun resetAndReseedDemoData() = withContext(Dispatchers.IO) {
        db.withTransaction {
            smsDao.deleteAllSms()
            txnDao.deleteAllTransactions()
            budgetDao.deleteAllBudgets()
            subDao.deleteAllSubscriptions()
            alertDao.deleteAllAlerts()
            learnedWeightDao.clearAllWeights()
        }
        settingsStore.markDemoDataSeeded(false)
        seedSampleData()
    }
}
