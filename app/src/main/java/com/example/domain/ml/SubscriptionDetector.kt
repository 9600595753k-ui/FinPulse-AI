package com.example.domain.ml

import com.example.data.local.SubscriptionEntity
import com.example.data.local.TransactionEntity
import com.example.domain.model.BillingCycle
import com.example.domain.model.TransactionType
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

object SubscriptionDetector {

    private val knownRecurringMerchants = mapOf(
        "netflix" to BillingCycle.MONTHLY,
        "amazon prime" to BillingCycle.YEARLY,
        "spotify" to BillingCycle.MONTHLY,
        "youtube premium" to BillingCycle.MONTHLY,
        "hotstar" to BillingCycle.YEARLY,
        "jio" to BillingCycle.MONTHLY,
        "airtel" to BillingCycle.MONTHLY,
        "act fibernet" to BillingCycle.MONTHLY,
        "lic" to BillingCycle.QUARTERLY,
        "emi" to BillingCycle.MONTHLY
    )

    fun detectFromTransactions(
        transactions: List<TransactionEntity>,
        existingSubscriptions: List<SubscriptionEntity>
    ): List<SubscriptionEntity> {
        val expenses = transactions.filter { it.type == TransactionType.EXPENSE.name }
        val byVendor = expenses.groupBy { it.vendor.trim() }
        val existingByVendor = existingSubscriptions.associateBy { it.vendor.lowercase(Locale.ENGLISH) }
        val detected = mutableListOf<SubscriptionEntity>()

        for ((vendor, txns) in byVendor) {
            if (vendor.isBlank()) continue
            val lowerVendor = vendor.lowercase(Locale.ENGLISH)
            val sorted = txns.sortedBy { it.timestampMillis }
            val latest = sorted.last()

            // Check interval recurrence if >= 2 transactions exist
            val inferredCycle = if (sorted.size >= 2) {
                val gapsDays = sorted.zipWithNext { a, b ->
                    ((b.timestampMillis - a.timestampMillis) / (24 * 60 * 60 * 1000L)).toInt()
                }.filter { it >= 5 }

                val avgGap = if (gapsDays.isNotEmpty()) gapsDays.average() else 0.0
                when {
                    avgGap in 5.0..9.0 -> BillingCycle.WEEKLY
                    avgGap in 24.0..35.0 -> BillingCycle.MONTHLY
                    avgGap in 80.0..100.0 -> BillingCycle.QUARTERLY
                    avgGap in 340.0..380.0 -> BillingCycle.YEARLY
                    else -> knownRecurringMerchants.entries.firstOrNull { lowerVendor.contains(it.key) }?.value
                }
            } else {
                knownRecurringMerchants.entries.firstOrNull { lowerVendor.contains(it.key) }?.value
            } ?: continue

            val previousAmount = if (sorted.size >= 2) {
                val prev = sorted[sorted.size - 2].amount
                if (abs(prev - latest.amount) >= 1.0) prev else null
            } else null

            val nextBilling = latest.timestampMillis + inferredCycle.daysInterval * 24L * 60 * 60 * 1000L
            val existing = existingByVendor[lowerVendor]
            if (existing == null) {
                detected.add(
                    SubscriptionEntity(
                        name = "$vendor ${inferredCycle.label}",
                        vendor = vendor,
                        amount = latest.amount,
                        previousAmount = previousAmount,
                        billingCycle = inferredCycle.name,
                        nextBillingMillis = nextBilling,
                        category = latest.category,
                        isPaused = false,
                        isAutoDetected = true,
                        reminderDaysBefore = 3,
                        lastPaidMillis = latest.timestampMillis
                    )
                )
            } else if (existing.isAutoDetected && existing.lastPaidMillis != latest.timestampMillis) {
                val priceChanged = abs(existing.amount - latest.amount) >= 1.0
                detected.add(
                    existing.copy(
                        previousAmount = if (priceChanged) existing.amount else existing.previousAmount,
                        amount = latest.amount,
                        nextBillingMillis = nextBilling,
                        lastPaidMillis = latest.timestampMillis
                    )
                )
            }
        }
        return detected
    }

    fun calculateAnnualCost(subscriptions: List<SubscriptionEntity>): Double {
        return subscriptions.filter { !it.isPaused }.sumOf { sub ->
            val cycle = runCatching { BillingCycle.valueOf(sub.billingCycle) }.getOrDefault(BillingCycle.MONTHLY)
            sub.amount * cycle.annualMultiplier
        }
    }

    fun calculateMonthlyEquivalent(subscriptions: List<SubscriptionEntity>): Double {
        return (calculateAnnualCost(subscriptions) / 12.0 * 100.0).roundToInt() / 100.0
    }
}
