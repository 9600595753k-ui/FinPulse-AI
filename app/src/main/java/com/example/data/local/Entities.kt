package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_messages",
    indices = [Index(value = ["dedupHash"], unique = true)]
)
data class SmsMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val encryptedBody: String,
    val receivedAtMillis: Long,
    val folder: String,
    val customFolderName: String? = null,
    val confidenceScore: Float = 0.85f,
    val isScamSuspicious: Boolean = false,
    val scamReason: String? = null,
    val extractedAmount: Double? = null,
    val extractedType: String? = null,
    val extractedVendor: String? = null,
    val extractedPaymentMethod: String? = null,
    val encryptedRefId: String? = null,
    val userCorrectedFolder: String? = null,
    val isRead: Boolean = false,
    val dedupHash: String
)

@Entity(
    tableName = "custom_folders",
    indices = [Index(value = ["name"], unique = true)]
)
data class CustomFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val keywordRulesCsv: String,
    val senderPattern: String,
    val colorHex: Long = 0xFF0B5D4B,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "learned_token_weights",
    indices = [Index(value = ["token", "folder"], unique = true)]
)
data class LearnedTokenWeightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val token: String,
    val folder: String,
    val weight: Float
)

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["dedupKey"], unique = true)]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val vendor: String,
    val amount: Double,
    val type: String,
    val category: String,
    val paymentMethod: String,
    val encryptedRefId: String,
    val encryptedAccountMask: String,
    val timestampMillis: Long,
    val sourceSmsId: Long? = null,
    val isManual: Boolean = false,
    val notes: String = "",
    val dedupKey: String
)

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["category"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String, // "OVERALL" or category name
    val monthlyLimit: Double,
    val carryForward: Boolean = false,
    val lastResetMonth: String,
    val alertAt50: Boolean = true,
    val alertAt75: Boolean = true,
    val alertAt90: Boolean = true,
    val alertAt100: Boolean = true
)

@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val vendor: String,
    val amount: Double,
    val previousAmount: Double? = null,
    val billingCycle: String,
    val nextBillingMillis: Long,
    val category: String,
    val isPaused: Boolean = false,
    val isAutoDetected: Boolean = false,
    val reminderDaysBefore: Int = 3,
    val lastPaidMillis: Long? = null
)

@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val message: String,
    val severity: String,
    val type: String,
    val timestampMillis: Long = System.currentTimeMillis(),
    val isRead: Boolean = false
)
