package com.example.domain.ml

import com.example.data.local.CustomFolderEntity
import com.example.data.local.LearnedTokenWeightEntity
import com.example.domain.model.SmsFolder
import java.util.Locale
import kotlin.math.exp

data class ClassificationResult(
    val folder: SmsFolder,
    val customFolderName: String?,
    val confidence: Float,
    val isScam: Boolean,
    val scamReason: String?,
    val parsedTransaction: ParsedTransactionResult?
)

/**
 * Hybrid On-Device ML & NLP SMS Classifier:
 * 1. Evaluates user-defined Custom Folder rules first.
 * 2. Detects OTPs and Financial Phishing/Scams with zero external network calls.
 * 3. Scores folders using TF-weighted domain keywords + Multinomial Naive Bayes-style user correction weights.
 */
object SmsClassifierEngine {

    private val folderKeywords: Map<SmsFolder, List<String>> = mapOf(
        SmsFolder.INCOME_SALARY to listOf(
            "salary", "credited", "payroll", "freelance", "refund", "cashback",
            "interest credited", "dividend", "deposited", "neft cr", "imps cr"
        ),
        SmsFolder.PAYMENTS_TRANSACTIONS to listOf(
            "debited", "upi", "utr", "paid", "sent", "vpa", "imps", "neft",
            "rtgs", "spent", "withdrawn", "txn", "transfer"
        ),
        SmsFolder.BILLS_UTILITIES to listOf(
            "electricity", "bill", "due", "bescom", "tata power", "recharge",
            "broadband", "fibernet", "gas", "water", "postpaid", "prepaid", "emi", "insurance"
        ),
        SmsFolder.SHOPPING to listOf(
            "order", "shipped", "delivered", "amazon", "flipkart", "myntra",
            "nykaa", "ajio", "tracking", "dispatched", "package"
        ),
        SmsFolder.BANKING_FINANCE to listOf(
            "a/c", "balance", "avl bal", "statement", "credit card", "debit card",
            "hdfc", "icici", "sbi", "axis", "kotak", "mutual fund", "sip", "fd"
        ),
        SmsFolder.OFFERS_PROMOTIONS to listOf(
            "offer", "discount", "cashback up to", "sale", "coupon", "promo",
            "pre-approved", "apply now", "flat", "% off", "limited time"
        ),
        SmsFolder.OTP_VERIFICATION to listOf(
            "otp", "one time password", "verification code", "do not share",
            "valid for", "cvv", "login code", "passcode"
        ),
        SmsFolder.WORK to listOf(
            "meeting", "standup", "sprint", "office", "hr", "team", "project",
            "deadline", "client", "zoom", "calendar", "invoice"
        ),
        SmsFolder.PERSONAL to listOf(
            "hey", "dinner", "home", "weekend", "family", "mom", "dad",
            "call me", "reach", "birthday", "see you"
        )
    )

    fun tokenize(sender: String, body: String): List<String> {
        val combined = "$sender $body".lowercase(Locale.ENGLISH)
        return combined.split(Regex("""[^a-z0-9@]+"""))
            .filter { it.length >= 3 && !it.all { c -> c.isDigit() } }
            .distinct()
    }

    fun classify(
        sender: String,
        body: String,
        timestampMillis: Long,
        customFolders: List<CustomFolderEntity>,
        learnedWeights: List<LearnedTokenWeightEntity>
    ): ClassificationResult {
        val lowerBody = body.lowercase(Locale.ENGLISH)
        val lowerSender = sender.lowercase(Locale.ENGLISH)

        // 1. Check Custom Folder Rules first
        for (custom in customFolders) {
            val keywords = custom.keywordRulesCsv.split(",")
                .map { it.trim().lowercase(Locale.ENGLISH) }
                .filter { it.isNotEmpty() }
            val senderMatch = custom.senderPattern.isNotBlank() &&
                lowerSender.contains(custom.senderPattern.trim().lowercase(Locale.ENGLISH))
            val keywordMatch = keywords.any { lowerBody.contains(it) }
            if (senderMatch || keywordMatch) {
                val parsed = IndianFinancialSmsParser.parseTransaction(sender, body, timestampMillis)
                return ClassificationResult(
                    folder = SmsFolder.CUSTOM,
                    customFolderName = custom.name,
                    confidence = 0.96f,
                    isScam = false,
                    scamReason = null,
                    parsedTransaction = parsed
                )
            }
        }

        // 2. High-priority OTP Check
        if (IndianFinancialSmsParser.isOtpMessage(sender, body)) {
            return ClassificationResult(
                folder = SmsFolder.OTP_VERIFICATION,
                customFolderName = null,
                confidence = 0.98f,
                isScam = false,
                scamReason = null,
                parsedTransaction = null
            )
        }

        // 3. Scam & Suspicious Message Check
        val (isScam, scamReason) = IndianFinancialSmsParser.detectScam(sender, body)
        if (isScam) {
            return ClassificationResult(
                folder = SmsFolder.SPAM_SUSPICIOUS,
                customFolderName = null,
                confidence = 0.95f,
                isScam = true,
                scamReason = scamReason,
                parsedTransaction = null
            )
        }

        // 4. Parse financial metadata
        val parsedTxn = IndianFinancialSmsParser.parseTransaction(sender, body, timestampMillis)

        // 5. Score every candidate folder using Domain NLP + Learned Feedback Weights
        val tokens = tokenize(sender, body)
        val weightLookup = learnedWeights.groupBy { it.folder }
        val scores = mutableMapOf<SmsFolder, Double>()

        for ((folder, keywords) in folderKeywords) {
            var score = 0.1
            for (kw in keywords) {
                if (lowerBody.contains(kw) || lowerSender.contains(kw)) {
                    score += 1.6
                }
            }
            // Add user-learned token weights
            val folderWeights = weightLookup[folder.name].orEmpty().associateBy { it.token }
            for (token in tokens) {
                score += (folderWeights[token]?.weight ?: 0f).toDouble()
            }
            scores[folder] = score
        }

        // Boost financial transaction folders when structured transaction extracted
        if (parsedTxn != null) {
            if (parsedTxn.type.name == "INCOME") {
                scores[SmsFolder.INCOME_SALARY] = (scores[SmsFolder.INCOME_SALARY] ?: 0.0) + 2.8
            } else if (parsedTxn.category == "Electricity & Utilities") {
                scores[SmsFolder.BILLS_UTILITIES] = (scores[SmsFolder.BILLS_UTILITIES] ?: 0.0) + 2.4
            } else if (parsedTxn.category.contains("Shopping")) {
                scores[SmsFolder.SHOPPING] = (scores[SmsFolder.SHOPPING] ?: 0.0) + 2.0
                scores[SmsFolder.PAYMENTS_TRANSACTIONS] = (scores[SmsFolder.PAYMENTS_TRANSACTIONS] ?: 0.0) + 1.8
            } else {
                scores[SmsFolder.PAYMENTS_TRANSACTIONS] = (scores[SmsFolder.PAYMENTS_TRANSACTIONS] ?: 0.0) + 2.5
            }
        }

        val bestEntry = scores.maxByOrNull { it.value }
        val bestFolder = bestEntry?.key ?: SmsFolder.PERSONAL
        val maxScore = bestEntry?.value ?: 1.0
        val sumExp = scores.values.sumOf { exp(it - maxScore) }
        val softmaxConfidence = (1.0 / sumExp).toFloat().coerceIn(0.55f, 0.99f)

        return ClassificationResult(
            folder = bestFolder,
            customFolderName = null,
            confidence = softmaxConfidence,
            isScam = false,
            scamReason = null,
            parsedTransaction = parsedTxn
        )
    }
}
