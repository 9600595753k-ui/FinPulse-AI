package com.example

import com.example.data.local.CustomFolderEntity
import com.example.data.local.LearnedTokenWeightEntity
import com.example.domain.export.CsvExporter
import com.example.domain.ml.IndianFinancialSmsParser
import com.example.domain.ml.SmsClassifierEngine
import com.example.domain.model.ReportType
import com.example.domain.model.SmsFolder
import com.example.domain.model.TransactionType
import com.example.domain.model.TransactionUi
import com.example.security.CryptoVault
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinPulseUnitTest {

    @Test
    fun parsesIndianUpiExpenseSmsAccurately() {
        val body = "INR 1,249.50 debited from A/c XX4821 via UPI to Swiggy. UTR 431298765401."
        val parsed = IndianFinancialSmsParser.parseTransaction("AD-HDFCBK", body, 1_700_000_000_000L)
        assertNotNull(parsed)
        assertEquals(1249.50, parsed!!.amount, 0.01)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals("Swiggy", parsed.vendor)
        assertEquals("Food & Restaurants", parsed.category)
        assertEquals("UPI", parsed.paymentMethod)
        assertEquals("431298765401", parsed.referenceId)
    }

    @Test
    fun parsesIndianSalaryCreditSms() {
        val body = "INR 1,18,500.00 credited to A/c XX4821 via NEFT from ACME TECH SALARY PAYROLL. Ref No NEFT99482104."
        val parsed = IndianFinancialSmsParser.parseTransaction("AD-HDFCBK", body, 1_700_000_000_000L)
        assertNotNull(parsed)
        assertEquals(118500.0, parsed!!.amount, 0.01)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("Monthly Salary", parsed.category)
        assertEquals("NEFT", parsed.paymentMethod)
    }

    @Test
    fun isolatesOtpMessagesWithoutCreatingTransactions() {
        val body = "649281 is your OTP for HDFC NetBanking login. Valid for 5 mins. Do not share OTP with anyone."
        assertTrue(IndianFinancialSmsParser.isOtpMessage("JD-HDFCBK", body))
        val parsed = IndianFinancialSmsParser.parseTransaction("JD-HDFCBK", body, 1_700_000_000_000L)
        assertNull(parsed)
        val classified = SmsClassifierEngine.classify("JD-HDFCBK", body, 1_700_000_000_000L, emptyList(), emptyList())
        assertEquals(SmsFolder.OTP_VERIFICATION, classified.folder)
    }

    @Test
    fun detectsFinancialScamSms() {
        val body = "URGENT: Your bank account will be blocked today. Update KYC immediately at http://bit.ly/kyc-now"
        val classified = SmsClassifierEngine.classify("+919811204491", body, 1_700_000_000_000L, emptyList(), emptyList())
        assertTrue(classified.isScam)
        assertEquals(SmsFolder.SPAM_SUSPICIOUS, classified.folder)
        assertNull(classified.parsedTransaction)
    }

    @Test
    fun appliesCustomFolderRulesAndLearnedWeights() {
        val custom = CustomFolderEntity(
            name = "Investments",
            keywordRulesCsv = "zerodha, mutual fund",
            senderPattern = ""
        )
        val classifiedCustom = SmsClassifierEngine.classify(
            "AD-ZERODHA",
            "Your mutual fund SIP order of Rs 5000 on Zerodha is executed",
            1_700_000_000_000L,
            listOf(custom),
            emptyList()
        )
        assertEquals(SmsFolder.CUSTOM, classifiedCustom.folder)
        assertEquals("Investments", classifiedCustom.customFolderName)

        val learned = listOf(
            LearnedTokenWeightEntity(token = "clubhouse", folder = SmsFolder.WORK.name, weight = 5.0f)
        )
        val classifiedLearned = SmsClassifierEngine.classify(
            "VM-OFFICE",
            "Join the clubhouse sync at 5pm",
            1_700_000_000_000L,
            emptyList(),
            learned
        )
        assertEquals(SmsFolder.WORK, classifiedLearned.folder)
    }

    @Test
    fun encryptsAndDecryptsVaultStrings() {
        val secret = "UTR431298765401"
        val encrypted = CryptoVault.encrypt(secret)
        assertFalse(encrypted == secret)
        assertEquals(secret, CryptoVault.decrypt(encrypted))
    }

    @Test
    fun exportsCleanCsvForExpenses() {
        val txns = listOf(
            TransactionUi(
                id = 1,
                title = "Swiggy",
                vendor = "Swiggy",
                amount = 485.0,
                type = TransactionType.EXPENSE,
                category = "Food & Restaurants",
                paymentMethod = "UPI",
                referenceId = "431312349876",
                accountMask = "XX4821",
                timestampMillis = System.currentTimeMillis(),
                sourceSmsId = 1,
                isManual = false,
                notes = ""
            )
        )
        val csv = CsvExporter.generateCsv(ReportType.MONTHLY_EXPENSE, txns, emptyList(), emptyList())
        assertTrue(csv.contains("Date,Time,Vendor,Category,Payment Method,Amount (INR),Reference ID,Account"))
        assertTrue(csv.contains("Swiggy,Food & Restaurants,UPI,485.00"))
    }
}
