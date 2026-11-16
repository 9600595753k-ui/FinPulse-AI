package com.example.data.ai

import android.util.Log
import com.example.data.local.SubscriptionEntity
import com.example.domain.model.BudgetStatusUi
import com.example.domain.model.MonthlySummary
import com.example.domain.model.TransactionType
import com.example.domain.model.TransactionUi
import com.example.domain.util.IndianCurrencyFormatter
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "FinancialAiRepository"

sealed interface AiResponseChunk {
    data object Loading : AiResponseChunk
    data class Streaming(val text: String) : AiResponseChunk
    data class Completed(val text: String) : AiResponseChunk
    data class Error(val message: String) : AiResponseChunk
}

/**
 * Privacy-preserving Firebase AI Logic repository.
 * Sends ONLY aggregated numerical financial summaries — NEVER OTPs, raw SMS text, phone numbers, or account numbers.
 */
class FinancialAiRepository {

    private val model by lazy {
        Firebase.ai.generativeModel("gemini-3.8-flash")
    }

    fun buildPrivacySafeFinancialContext(
        currentSummary: MonthlySummary,
        previousSummary: MonthlySummary?,
        transactions: List<TransactionUi>,
        budgets: List<BudgetStatusUi>,
        subscriptions: List<SubscriptionEntity>
    ): String {
        val currentMonthTxns = transactions.filter {
            IndianCurrencyFormatter.monthKey(it.timestampMillis) == currentSummary.monthKey
        }
        val categoryBreakdown = currentMonthTxns
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }
            .entries
            .sortedByDescending { it.value }
            .joinToString("; ") { "${it.key}: ${IndianCurrencyFormatter.format(it.value)}" }

        val topVendors = currentSummary.topVendors.take(6)
            .joinToString("; ") { "${it.vendorName}: ${IndianCurrencyFormatter.format(it.totalSpent)} (${it.transactionCount} txns)" }

        val budgetLines = budgets.joinToString("; ") {
            "${it.category}: spent ${IndianCurrencyFormatter.format(it.spent)} / limit ${IndianCurrencyFormatter.format(it.monthlyLimit)} (${it.utilizationPercent}%)"
        }

        val subLines = subscriptions.filter { !it.isPaused }
            .joinToString("; ") { "${it.name}: ${IndianCurrencyFormatter.format(it.amount)} (${it.billingCycle})" }

        val prevText = if (previousSummary != null) {
            "Previous month (${previousSummary.monthLabel}): Income=${IndianCurrencyFormatter.format(previousSummary.totalIncome)}, Expenses=${IndianCurrencyFormatter.format(previousSummary.totalExpenses)}, Savings=${IndianCurrencyFormatter.format(previousSummary.totalSavings)}."
        } else {
            "Previous month data unavailable."
        }

        return """
            Aggregated Financial Summary (OTP & raw SMS excluded):
            - Active Month: ${currentSummary.monthLabel}
            - Total Income: ${IndianCurrencyFormatter.format(currentSummary.totalIncome)}
            - Total Expenses: ${IndianCurrencyFormatter.format(currentSummary.totalExpenses)}
            - Net Cash Flow / Savings: ${IndianCurrencyFormatter.format(currentSummary.totalSavings)}
            - Average Daily Spending: ${IndianCurrencyFormatter.format(currentSummary.averageDailySpend)}
            - Overall Budget Utilization: ${currentSummary.budgetUtilizationPercent}%
            - $prevText
            - Category Expenses: ${categoryBreakdown.ifBlank { "None recorded" }}
            - Top Vendors: ${topVendors.ifBlank { "None recorded" }}
            - Budgets: ${budgetLines.ifBlank { "No budgets configured" }}
            - Active Subscriptions: ${subLines.ifBlank { "No active subscriptions" }}
        """.trimIndent()
    }

    fun askFinancialAssistant(
        question: String,
        anonymizedFinancialDigest: String
    ): Flow<AiResponseChunk> = flow {
        emit(AiResponseChunk.Loading)
        val prompt = """
            You are FinPulse's personal finance analyst for an Indian user (currency: INR ₹).
            Answer the user's question concisely, accurately, and practically using ONLY the anonymized financial metrics below.
            Do not invent transactions or numbers that are not in the digest. Highlight exact ₹ figures and give 1-2 actionable recommendations.

            $anonymizedFinancialDigest

            User Question: $question
        """.trimIndent()

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val piece = chunk.text.orEmpty()
                    if (piece.isNotEmpty()) {
                        accumulated.append(piece)
                        emit(AiResponseChunk.Streaming(accumulated.toString()))
                    }
                }
            }
            val finalText = accumulated.toString().trim()
            if (finalText.isNotEmpty()) {
                emit(AiResponseChunk.Completed(finalText))
            } else {
                emit(AiResponseChunk.Error("Gemini returned an empty response. Please try again."))
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI assistant timed out", e)
            emit(AiResponseChunk.Error("Request timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI generation failed", e)
            emit(AiResponseChunk.Error(e.localizedMessage ?: "Unable to reach Firebase AI Logic."))
        }
    }
}
