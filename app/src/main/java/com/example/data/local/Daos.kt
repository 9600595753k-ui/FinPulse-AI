package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SmsDao {
    @Query("SELECT * FROM sms_messages ORDER BY receivedAtMillis DESC")
    fun observeAllSms(): Flow<List<SmsMessageEntity>>

    @Query("SELECT * FROM sms_messages ORDER BY receivedAtMillis DESC")
    suspend fun getAllSmsOnce(): List<SmsMessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSms(sms: SmsMessageEntity): Long

    @Update
    suspend fun updateSms(sms: SmsMessageEntity)

    @Query("UPDATE sms_messages SET folder = :folder, customFolderName = :customName, userCorrectedFolder = :folder, isRead = 1 WHERE id = :smsId")
    suspend fun updateSmsFolder(smsId: Long, folder: String, customName: String?)

    @Query("UPDATE sms_messages SET isRead = 1 WHERE id = :smsId")
    suspend fun markSmsRead(smsId: Long)

    @Query("DELETE FROM sms_messages WHERE id = :smsId")
    suspend fun deleteSmsById(smsId: Long)

    @Query("DELETE FROM sms_messages")
    suspend fun deleteAllSms()
}

@Dao
interface CustomFolderDao {
    @Query("SELECT * FROM custom_folders ORDER BY createdAtMillis DESC")
    fun observeCustomFolders(): Flow<List<CustomFolderEntity>>

    @Query("SELECT * FROM custom_folders")
    suspend fun getCustomFoldersOnce(): List<CustomFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomFolder(folder: CustomFolderEntity): Long

    @Query("DELETE FROM custom_folders WHERE id = :folderId")
    suspend fun deleteCustomFolder(folderId: Long)
}

@Dao
interface LearnedWeightDao {
    @Query("SELECT * FROM learned_token_weights")
    suspend fun getAllWeights(): List<LearnedTokenWeightEntity>

    @Query("SELECT * FROM learned_token_weights WHERE token = :token AND folder = :folder LIMIT 1")
    suspend fun getWeight(token: String, folder: String): LearnedTokenWeightEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWeight(entity: LearnedTokenWeightEntity)

    @Query("DELETE FROM learned_token_weights")
    suspend fun clearAllWeights()
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC")
    fun observeAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestampMillis DESC")
    suspend fun getAllTransactionsOnce(): List<TransactionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE dedupKey = :dedupKey)")
    suspend fun existsByDedupKey(dedupKey: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(txn: TransactionEntity): Long

    @Update
    suspend fun updateTransaction(txn: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :txnId")
    suspend fun deleteTransactionById(txnId: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets ORDER BY CASE WHEN category = 'OVERALL' THEN 0 ELSE 1 END, category ASC")
    fun observeAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAllBudgetsOnce(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: BudgetEntity): Long

    @Query("DELETE FROM budgets WHERE id = :budgetId")
    suspend fun deleteBudgetById(budgetId: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()
}

@Dao
interface SubscriptionDao {
    @Query("SELECT * FROM subscriptions ORDER BY nextBillingMillis ASC")
    fun observeAllSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT * FROM subscriptions")
    suspend fun getAllSubscriptionsOnce(): List<SubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSubscription(sub: SubscriptionEntity): Long

    @Update
    suspend fun updateSubscription(sub: SubscriptionEntity)

    @Query("DELETE FROM subscriptions WHERE id = :subId")
    suspend fun deleteSubscriptionById(subId: Long)

    @Query("DELETE FROM subscriptions")
    suspend fun deleteAllSubscriptions()
}

@Dao
interface AlertDao {
    @Query("SELECT * FROM alerts ORDER BY timestampMillis DESC")
    fun observeAllAlerts(): Flow<List<AlertEntity>>

    @Query("SELECT * FROM alerts ORDER BY timestampMillis DESC")
    suspend fun getAllAlertsOnce(): List<AlertEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlert(alert: AlertEntity): Long

    @Query("UPDATE alerts SET isRead = 1")
    suspend fun markAllRead()

    @Query("DELETE FROM alerts WHERE id = :alertId")
    suspend fun deleteAlertById(alertId: Long)

    @Query("DELETE FROM alerts")
    suspend fun deleteAllAlerts()
}
