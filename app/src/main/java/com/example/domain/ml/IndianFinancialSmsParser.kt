package com.example.domain.ml

import com.example.domain.model.TransactionType
import java.security.MessageDigest
import java.util.Locale

data class ParsedTransactionResult(
    val amount: Double,
    val type: TransactionType,
    val vendor: String,
    val title: String,
    val category: String,
    val paymentMethod: String,
    val referenceId: String,
    val accountMask: String,
    val dedupKey: String
)

object IndianFinancialSmsParser {

    private val amountRegex = Regex(
        """(?:INR|Rs\.?|₹)\s*([0-9]{1,3}(?:,[0-9]{2,3})*(?:\.[0-9]{1,2})?|[0-9]+(?:\.[0-9]{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    private val refIdRegex = Regex(
        """(?:UTR|Ref(?:\s*No\.?)?|Txn\s*(?:ID|No\.?)|UPI\s*Ref(?:\s*No\.?)?|IMPS\s*Ref)\s*[:#-]?\s*([A-Z0-9]{6,22})""",
        RegexOption.IGNORE_CASE
    )

    private val accountMaskRegex = Regex(
        """(?:A/c|Acct|Account|Card)\s*(?:No\.?)?\s*(?:ending\s*(?:in|with)?\s*)?([X*]{1,6}[0-9]{3,4}|[0-9]{4})""",
        RegexOption.IGNORE_CASE
    )

    private val otpRegex = Regex(
        """\b(?:OTP|one[\s-]?time\s*password|verification\s*code|passcode|security\s*code)\b""",
        RegexOption.IGNORE_CASE
    )

    private val otpDigitsRegex = Regex("""\b\d{4,8}\b""")

    private val debitKeywords = listOf(
        "debited", "spent", "paid", "sent", "withdrawn", "charged",
        "purchase", "auto-debited", "autopay", "deducted", "txn of", "payment of"
    )

    private val creditKeywords = listOf(
        "credited", "received", "deposited", "salary", "refund",
        "cashback", "reversed", "interest", "dividend", "payout"
    )

    private val vendorCatalog = listOf(
        "Swiggy" to "Food & Restaurants",
        "Zomato" to "Food & Restaurants",
        "Starbucks" to "Food & Restaurants",
        "Dominos" to "Food & Restaurants",
        "McDonalds" to "Food & Restaurants",
        "Blinkit" to "Groceries",
        "Zepto" to "Groceries",
        "BigBasket" to "Groceries",
        "DMart" to "Groceries",
        "Reliance Fresh" to "Groceries",
        "Instamart" to "Groceries",
        "Amazon" to "Shopping & Online Orders",
        "Flipkart" to "Shopping & Online Orders",
        "Myntra" to "Shopping",
        "Ajio" to "Shopping",
        "Nykaa" to "Shopping",
        "Uber" to "Transportation",
        "Ola" to "Transportation",
        "Rapido" to "Transportation",
        "Metro" to "Transportation",
        "IRCTC" to "Travel",
        "MakeMyTrip" to "Travel",
        "IndiGo" to "Travel",
        "Air India" to "Travel",
        "IndianOil" to "Fuel",
        "HPCL" to "Fuel",
        "BPCL" to "Fuel",
        "Shell" to "Fuel",
        "BESCOM" to "Electricity & Utilities",
        "Tata Power" to "Electricity & Utilities",
        "Adani Electricity" to "Electricity & Utilities",
        "Airtel" to "Electricity & Utilities",
        "Jio" to "Electricity & Utilities",
        "ACT Fibernet" to "Electricity & Utilities",
        "Netflix" to "Subscriptions",
        "Spotify" to "Subscriptions",
        "YouTube Premium" to "Subscriptions",
        "Amazon Prime" to "Subscriptions",
        "Hotstar" to "Subscriptions",
        "Apollo Pharmacy" to "Healthcare",
        "PharmEasy" to "Healthcare",
        "1mg" to "Healthcare",
        "Practo" to "Healthcare",
        "Udemy" to "Education",
        "Coursera" to "Education",
        "BookMyShow" to "Entertainment",
        "PVR" to "Entertainment",
        "INOX" to "Entertainment",
        "Nobroker" to "Rent",
        "Rent" to "Rent",
        "LIC" to "Subscriptions",
        "EMI" to "Transfers"
    )

    fun isOtpMessage(sender: String, body: String): Boolean {
        val lower = body.lowercase(Locale.ENGLISH)
        val isOtpKeyword = otpRegex.containsMatchIn(body)
        val hasDigits = otpDigitsRegex.containsMatchIn(body)
        val hasFinancialVerb = debitKeywords.any { lower.contains(it) } ||
            creditKeywords.any { lower.contains(it) }
        if (isOtpKeyword && hasDigits && !lower.contains("debited") && !lower.contains("credited")) {
            return true
        }
        return (lower.contains("do not share") || lower.contains("valid for")) &&
            hasDigits && !hasFinancialVerb
    }

    fun detectScam(sender: String, body: String): Pair<Boolean, String?> {
        val lower = body.lowercase(Locale.ENGLISH)
        val suspiciousUrls = listOf("bit.ly", "tinyurl.com", ".xyz", ".tk", ".top", "kyc-update", "verify-pan")
        val hasShortLink = suspiciousUrls.any { lower.contains(it) }
        val urgencyPhishing = listOf(
            "account will be blocked", "account suspended", "update kyc immediately",
            "pan card expired", "won a lottery", "claim your prize",
            "click link to refund", "share your otp", "upi pin to receive"
        ).firstOrNull { lower.contains(it) }

        val numericPersonalSender = sender.replace("+", "").all { it.isDigit() } && sender.length >= 10
        if (urgencyPhishing != null && (hasShortLink || numericPersonalSender)) {
            return true to "High-risk phishing pattern: \"$urgencyPhishing\" with suspicious link/sender"
        }
        if (hasShortLink && (lower.contains("bank") || lower.contains("kyc") || lower.contains("upi") || lower.contains("prize"))) {
            return true to "Suspicious shortened/unverified URL in financial message"
        }
        if (lower.contains("share otp") || lower.contains("enter upi pin to receive")) {
            return true to "Social engineering scam asking for OTP or UPI PIN"
        }
        return false to null
    }

    fun parseTransaction(sender: String, body: String, timestampMillis: Long): ParsedTransactionResult? {
        if (isOtpMessage(sender, body)) return null
        val (isScam, _) = detectScam(sender, body)
        if (isScam) return null

        val lower = body.lowercase(Locale.ENGLISH)
        // Skip promotional offers like "Get Rs 500 off" or "Loan approved upto Rs 5,00,000"
        val isPromotional = listOf(
            "upto", "up to", "pre-approved", "preapproved", "apply now",
            "use code", "flat rs", "off on", "win rs", "hurry", "offer valid"
        ).any { lower.contains(it) } &&
            !debitKeywords.any { lower.contains(it) } &&
            !creditKeywords.any { lower.contains(it) }
        if (isPromotional) return null

        val amountMatch = amountRegex.find(body) ?: return null
        val rawAmount = amountMatch.groupValues[1].replace(",", "")
        val amount = rawAmount.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val isCredit = creditKeywords.any { lower.contains(it) } &&
            !lower.contains("credit card spent") &&
            !lower.contains("spent on your credit card") &&
            !lower.contains("debited")
        val isDebit = debitKeywords.any { lower.contains(it) } ||
            lower.contains("spent on") ||
            lower.contains("credit card")

        if (!isCredit && !isDebit) return null

        val type = if (isCredit && !lower.contains("debited")) TransactionType.INCOME else TransactionType.EXPENSE
        val paymentMethod = detectPaymentMethod(lower)
        val refId = refIdRegex.find(body)?.groupValues?.getOrNull(1)?.uppercase(Locale.ENGLISH) ?: ""
        val accountMask = accountMaskRegex.find(body)?.groupValues?.getOrNull(1)?.uppercase(Locale.ENGLISH) ?: ""

        val (vendor, guessedCategory) = detectVendorAndCategory(sender, body, type)
        val title = if (type == TransactionType.INCOME) {
            "$vendor ($guessedCategory)"
        } else {
            vendor
        }

        val dedupKey = buildDedupKey(amount, type, vendor, refId, timestampMillis)

        return ParsedTransactionResult(
            amount = amount,
            type = type,
            vendor = vendor,
            title = title,
            category = guessedCategory,
            paymentMethod = paymentMethod,
            referenceId = refId,
            accountMask = accountMask,
            dedupKey = dedupKey
        )
    }

    private fun detectPaymentMethod(lower: String): String {
        return when {
            lower.contains("upi") || lower.contains("vpa") || lower.contains("@") -> "UPI"
            lower.contains("credit card") || lower.contains("cc ") -> "Credit Card"
            lower.contains("debit card") || lower.contains("atm") || lower.contains("pos") -> "Debit Card"
            lower.contains("imps") -> "IMPS"
            lower.contains("neft") -> "NEFT"
            lower.contains("rtgs") -> "RTGS"
            lower.contains("netbanking") || lower.contains("net banking") -> "NetBanking"
            lower.contains("wallet") || lower.contains("paytm wallet") -> "Wallet"
            else -> "UPI"
        }
    }

    private fun detectVendorAndCategory(
        sender: String,
        body: String,
        type: TransactionType
    ): Pair<String, String> {
        val lower = body.lowercase(Locale.ENGLISH)

        if (type == TransactionType.INCOME) {
            val incomeCat = when {
                lower.contains("salary") || lower.contains("payroll") -> "Monthly Salary"
                lower.contains("refund") || lower.contains("reversal") -> "Refunds"
                lower.contains("interest") -> "Interest"
                lower.contains("freelance") || lower.contains("consulting") || lower.contains("invoice") -> "Freelance Income"
                lower.contains("business") || lower.contains("settlement") -> "Business Income"
                else -> "Other Income"
            }
            val matchedMerchant = vendorCatalog.firstOrNull { lower.contains(it.first.lowercase(Locale.ENGLISH)) }?.first
            val vendorName = matchedMerchant ?: extractMerchantAfterPreposition(body) ?: cleanSenderName(sender)
            return vendorName to incomeCat
        }

        for ((vendor, category) in vendorCatalog) {
            if (lower.contains(vendor.lowercase(Locale.ENGLISH))) {
                return vendor to category
            }
        }

        val extracted = extractMerchantAfterPreposition(body)
        val vendor = extracted ?: cleanSenderName(sender)
        val fallbackCategory = when {
            lower.contains("restaurant") || lower.contains("cafe") || lower.contains("bakery") || lower.contains("food") -> "Food & Restaurants"
            lower.contains("mart") || lower.contains("grocery") || lower.contains("supermarket") -> "Groceries"
            lower.contains("petrol") || lower.contains("diesel") || lower.contains("fuel") -> "Fuel"
            lower.contains("electricity") || lower.contains("water bill") || lower.contains("gas bill") || lower.contains("broadband") || lower.contains("recharge") -> "Electricity & Utilities"
            lower.contains("hospital") || lower.contains("clinic") || lower.contains("pharmacy") || lower.contains("medical") -> "Healthcare"
            lower.contains("school") || lower.contains("tuition") || lower.contains("college") || lower.contains("course") -> "Education"
            lower.contains("flight") || lower.contains("hotel") || lower.contains("railway") -> "Travel"
            lower.contains("rent") || lower.contains("landlord") -> "Rent"
            lower.contains("movie") || lower.contains("cinema") -> "Entertainment"
            lower.contains("subscription") || lower.contains("autopay") -> "Subscriptions"
            lower.contains("transfer") || lower.contains("sent to") -> "Transfers"
            else -> "Other Expenses"
        }
        return vendor to fallbackCategory
    }

    private fun extractMerchantAfterPreposition(body: String): String? {
        val pattern = Regex(
            """(?:at|to|towards|from|info[:\s]+UPI/|VPA\s+)([A-Za-z0-9][A-Za-z0-9 &._-]{2,24})""",
            RegexOption.IGNORE_CASE
        )
        val match = pattern.find(body)?.groupValues?.getOrNull(1)?.trim() ?: return null
        val cleaned = match
            .split(Regex("""\s+(?:on|via|ref|utr|avl|bal|using)\b""", RegexOption.IGNORE_CASE))
            .firstOrNull()
            ?.trim()
            ?.trimEnd('.', ',', '-', ':')
        return cleaned?.takeIf { it.length >= 2 && !it.all { ch -> ch.isDigit() } }
    }

    private fun cleanSenderName(sender: String): String {
        val cleaned = sender.replace(Regex("""^[A-Z]{2}-"""), "")
        return cleaned.ifBlank { "Bank Transfer" }
    }

    fun buildDedupKey(
        amount: Double,
        type: TransactionType,
        vendor: String,
        referenceId: String,
        timestampMillis: Long
    ): String {
        val raw = if (referenceId.isNotBlank()) {
            "REF:${referenceId.uppercase(Locale.ENGLISH)}:${amount}:${type.name}"
        } else {
            // 10-minute bucket prevents duplicate SMS notifications from bank + UPI app
            val bucket = timestampMillis / (10 * 60 * 1000L)
            "TXN:${amount}:${type.name}:${vendor.lowercase(Locale.ENGLISH).trim()}:$bucket"
        }
        return sha256(raw)
    }

    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
