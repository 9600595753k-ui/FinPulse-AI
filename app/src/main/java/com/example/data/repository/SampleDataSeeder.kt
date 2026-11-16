package com.example.data.repository

import com.example.data.local.BudgetEntity
import com.example.data.local.CustomFolderEntity
import com.example.data.local.SubscriptionEntity
import com.example.domain.model.BillingCycle
import com.example.domain.util.IndianCurrencyFormatter
import java.util.Calendar

data class SeedSmsSpec(
    val sender: String,
    val body: String,
    val timestampMillis: Long
)

object SampleDataSeeder {

    private fun millisAgo(daysAgo: Int, hour: Int = 11, minute: Int = 30): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysAgo)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }

    private fun dayInCurrentMonth(dayOfMonth: Int, hour: Int = 12, minute: Int = 15): Long {
        val cal = Calendar.getInstance()
        val maxDay = cal.get(Calendar.DAY_OF_MONTH).coerceAtLeast(1)
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceAtMost(maxDay))
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }

    private fun dayInPreviousMonth(monthsBack: Int, dayOfMonth: Int, hour: Int = 14): Long {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -monthsBack)
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth.coerceAtMost(maxDay))
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 20)
        cal.set(Calendar.SECOND, 0)
        return cal.timeInMillis
    }

    fun getSampleSmsList(): List<SeedSmsSpec> = listOf(
        // --- Current Month Income & Salary ---
        SeedSmsSpec(
            "AD-HDFCBK",
            "INR 1,18,500.00 credited to A/c XX4821 on 01-Nov via NEFT from ACME TECH SALARY PAYROLL. Ref No NEFT99482104. Avl Bal INR 1,46,240.00.",
            dayInCurrentMonth(1, 9, 30)
        ),
        SeedSmsSpec(
            "VM-ICICIB",
            "Rs 14,500.00 credited to ICICI Acct XX9012 via IMPS Freelance Design Invoice #204. IMPS Ref 6308112299.",
            dayInCurrentMonth(5, 16, 40)
        ),
        SeedSmsSpec(
            "AD-SBIINB",
            "Dear Customer, Rs 1,249.00 refund credited to A/c XX3310 from Amazon Pay. Ref No REF77810234.",
            dayInCurrentMonth(8, 13, 10)
        ),

        // --- Current Month Expenses across Indian vendors & payment rails ---
        SeedSmsSpec(
            "AD-HDFCBK",
            "Sent Rs 18,500.00 from HDFC Bank A/c XX4821 via UPI to NoBroker Landlord Rent. UPI Ref 431298765401.",
            dayInCurrentMonth(2, 10, 5)
        ),
        SeedSmsSpec(
            "JM-HDFCBK",
            "Rs 485.00 debited from A/c XX4821 via UPI to Swiggy on order #88219. UTR 431312349876.",
            dayInCurrentMonth(3, 20, 15)
        ),
        SeedSmsSpec(
            "AX-ICICIB",
            "INR 2,340.00 spent on ICICI Credit Card XX7714 at BigBasket Groceries. Ref No TXN88210441.",
            dayInCurrentMonth(4, 18, 45)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 649.00 auto-debited via UPI AutoPay towards Netflix India subscription. UPI Ref 431409812233.",
            dayInCurrentMonth(5, 8, 0)
        ),
        SeedSmsSpec(
            "VM-SBIINB",
            "Rs 1,890.00 paid via UPI to BESCOM Electricity Bill for Oct cycle. Ref No 431522910088.",
            dayInCurrentMonth(6, 11, 20)
        ),
        SeedSmsSpec(
            "AD-AXISBK",
            "Rs 342.00 debited from Axis Debit Card XX2209 at Uber India Trip. Txn ID AX88392011.",
            dayInCurrentMonth(7, 9, 45)
        ),
        SeedSmsSpec(
            "JM-HDFCBK",
            "INR 3,499.00 spent on HDFC Credit Card XX5510 at Amazon India for Wireless Earbuds. Ref No AMZ441920.",
            dayInCurrentMonth(9, 15, 10)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 1,450.00 paid via UPI to HPCL Fuel Station Koramangala. UPI Ref 431655442211.",
            dayInCurrentMonth(10, 17, 35)
        ),
        SeedSmsSpec(
            "VM-ICICIB",
            "Rs 720.00 debited via UPI to Zomato Order. Ref No 431711002299.",
            dayInCurrentMonth(11, 21, 5)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 1,199.00 paid via UPI AutoPay to Jio Fiber Broadband Recharge. UPI Ref 431888112233.",
            dayInCurrentMonth(12, 10, 15)
        ),
        SeedSmsSpec(
            "AX-SBIINB",
            "INR 1,680.00 paid at Blinkit Groceries via UPI. Ref No 431977665544.",
            dayInCurrentMonth(13, 19, 0)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 139.00 auto-debited via UPI AutoPay towards Spotify Premium. Ref No 432011223344.",
            dayInCurrentMonth(14, 7, 30)
        ),
        SeedSmsSpec(
            "VM-AXISBK",
            "Rs 940.00 spent at Apollo Pharmacy via Debit Card XX2209. Txn ID AP77661122.",
            dayInCurrentMonth(15, 16, 10)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 680.00 paid via UPI to BookMyShow PVR Tickets. UPI Ref 432199887766.",
            dayInCurrentMonth(15, 19, 40)
        ),

        // --- Non-transaction Folders (OTP, Banking Info, Shopping, Offers, Work, Personal, Scam) ---
        SeedSmsSpec(
            "JD-HDFCBK",
            "649281 is your OTP for HDFC NetBanking login. Valid for 5 mins. Do not share OTP with anyone.",
            millisAgo(0, 8, 55)
        ),
        SeedSmsSpec(
            "AD-AMAZON",
            "Your Amazon order #408-992184 has been shipped and will be delivered tomorrow by 8 PM.",
            millisAgo(1, 14, 20)
        ),
        SeedSmsSpec(
            "VK-SWIGGY",
            "FLAT Rs 150 OFF on your next 3 orders! Use code HUNGRY150. Offer valid today only.",
            millisAgo(2, 12, 0)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Your HDFC Bank Credit Card XX5510 monthly statement is ready. Total due INR 3,499.00 by 25-Nov.",
            millisAgo(3, 10, 30)
        ),
        SeedSmsSpec(
            "+919811204491",
            "URGENT: Your bank account will be blocked today. Update KYC immediately at http://bit.ly/kyc-verify-now and share OTP.",
            millisAgo(1, 16, 45)
        ),
        SeedSmsSpec(
            "TM-MYNTRA",
            "Congratulations! You won a lottery prize of Rs 25,00,000. Claim your prize now at http://claim-win.xyz",
            millisAgo(4, 15, 10)
        ),
        SeedSmsSpec(
            "HR-ACME",
            "Reminder: Sprint review meeting and Q4 project invoice sign-off with client team at 4:00 PM.",
            millisAgo(2, 11, 10)
        ),
        SeedSmsSpec(
            "+919845011223",
            "Hey! Are we still on for family dinner at home this weekend? Call me when free.",
            millisAgo(1, 19, 15)
        ),

        // --- Previous Month (Month - 1) History for Charts & Subscription Detection ---
        SeedSmsSpec(
            "AD-HDFCBK",
            "INR 1,18,500.00 credited to A/c XX4821 via NEFT from ACME TECH SALARY PAYROLL. Ref No NEFT88410222.",
            dayInPreviousMonth(1, 1, 9)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 18,500.00 paid via UPI to NoBroker Landlord Rent. UPI Ref 391298765401.",
            dayInPreviousMonth(1, 2, 10)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 499.00 auto-debited via UPI AutoPay towards Netflix India subscription. UPI Ref 391409812233.",
            dayInPreviousMonth(1, 5, 8)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 139.00 auto-debited via UPI AutoPay towards Spotify Premium. Ref No 392011223344.",
            dayInPreviousMonth(1, 14, 7)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 1,199.00 paid via UPI AutoPay to Jio Fiber Broadband Recharge. UPI Ref 391888112233.",
            dayInPreviousMonth(1, 12, 10)
        ),
        SeedSmsSpec(
            "AX-ICICIB",
            "INR 4,200.00 spent on ICICI Credit Card XX7714 at Flipkart India. Ref No FLP391002.",
            dayInPreviousMonth(1, 16, 15)
        ),
        SeedSmsSpec(
            "JM-HDFCBK",
            "Rs 3,150.00 spent at BigBasket Groceries via UPI. Ref No 3916002211.",
            dayInPreviousMonth(1, 19, 18)
        ),
        SeedSmsSpec(
            "VM-SBIINB",
            "Rs 2,140.00 paid via UPI to BESCOM Electricity Bill. Ref No 391522910088.",
            dayInPreviousMonth(1, 22, 11)
        ),
        SeedSmsSpec(
            "AD-AXISBK",
            "Rs 2,650.00 debited at Swiggy via UPI. Ref No 3919223344.",
            dayInPreviousMonth(1, 25, 20)
        ),

        // --- Two Months Ago (Month - 2) History for Quarterly/Trend Charts ---
        SeedSmsSpec(
            "AD-HDFCBK",
            "INR 1,15,000.00 credited to A/c XX4821 via NEFT from ACME TECH SALARY PAYROLL. Ref No NEFT77410222.",
            dayInPreviousMonth(2, 1, 9)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 18,500.00 paid via UPI to NoBroker Landlord Rent. UPI Ref 291298765401.",
            dayInPreviousMonth(2, 2, 10)
        ),
        SeedSmsSpec(
            "AX-ICICIB",
            "INR 5,800.00 spent on ICICI Credit Card XX7714 at Amazon India. Ref No AMZ291002.",
            dayInPreviousMonth(2, 10, 14)
        ),
        SeedSmsSpec(
            "JM-HDFCBK",
            "Rs 2,890.00 spent at BigBasket Groceries via UPI. Ref No 2916002211.",
            dayInPreviousMonth(2, 18, 17)
        ),
        SeedSmsSpec(
            "AD-HDFCBK",
            "Rs 2,200.00 paid via UPI to HPCL Fuel Station. Ref No 2916554422.",
            dayInPreviousMonth(2, 23, 16)
        )
    )

    fun getDefaultBudgets(): List<BudgetEntity> {
        val month = IndianCurrencyFormatter.currentMonthKey()
        return listOf(
            BudgetEntity(category = "OVERALL", monthlyLimit = 45000.0, lastResetMonth = month),
            BudgetEntity(category = "Food & Restaurants", monthlyLimit = 5000.0, lastResetMonth = month),
            BudgetEntity(category = "Groceries", monthlyLimit = 6000.0, lastResetMonth = month),
            BudgetEntity(category = "Shopping", monthlyLimit = 3000.0, lastResetMonth = month),
            BudgetEntity(category = "Shopping & Online Orders", monthlyLimit = 4000.0, lastResetMonth = month),
            BudgetEntity(category = "Transportation", monthlyLimit = 2000.0, lastResetMonth = month),
            BudgetEntity(category = "Entertainment", monthlyLimit = 1500.0, lastResetMonth = month),
            BudgetEntity(category = "Electricity & Utilities", monthlyLimit = 3500.0, lastResetMonth = month)
        )
    }

    fun getDefaultCustomFolders(): List<CustomFolderEntity> = listOf(
        CustomFolderEntity(
            name = "Rent & Housing",
            keywordRulesCsv = "nobroker, rent, landlord, society",
            senderPattern = "",
            colorHex = 0xFF0D9488
        )
    )

    fun getExtraSubscriptions(): List<SubscriptionEntity> {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 2)
        val youtubeNext = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 6)
        val primeNext = cal.timeInMillis
        return listOf(
            SubscriptionEntity(
                name = "YouTube Premium",
                vendor = "YouTube Premium",
                amount = 149.0,
                previousAmount = 129.0,
                billingCycle = BillingCycle.MONTHLY.name,
                nextBillingMillis = youtubeNext,
                category = "Subscriptions",
                isPaused = false,
                isAutoDetected = true,
                reminderDaysBefore = 3,
                lastPaidMillis = millisAgo(28)
            ),
            SubscriptionEntity(
                name = "Amazon Prime Annual",
                vendor = "Amazon Prime",
                amount = 1499.0,
                previousAmount = null,
                billingCycle = BillingCycle.YEARLY.name,
                nextBillingMillis = primeNext,
                category = "Subscriptions",
                isPaused = false,
                isAutoDetected = true,
                reminderDaysBefore = 7,
                lastPaidMillis = millisAgo(357)
            )
        )
    }
}
