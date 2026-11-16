package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SmsMessageEntity::class,
        CustomFolderEntity::class,
        LearnedTokenWeightEntity::class,
        TransactionEntity::class,
        BudgetEntity::class,
        SubscriptionEntity::class,
        AlertEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FinPulseDatabase : RoomDatabase() {
    abstract fun smsDao(): SmsDao
    abstract fun customFolderDao(): CustomFolderDao
    abstract fun learnedWeightDao(): LearnedWeightDao
    abstract fun transactionDao(): TransactionDao
    abstract fun budgetDao(): BudgetDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun alertDao(): AlertDao

    companion object {
        @Volatile
        private var INSTANCE: FinPulseDatabase? = null

        fun getInstance(context: Context): FinPulseDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FinPulseDatabase::class.java,
                    "finpulse_encrypted.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
