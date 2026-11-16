package com.example.domain.util

import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object IndianCurrencyFormatter {
    private val indianFormatTL = ThreadLocal.withInitial { DecimalFormat("##,##,##0.##") }
    private val indianWholeFormatTL = ThreadLocal.withInitial { DecimalFormat("##,##,##0") }
    private val dateFmtTL = ThreadLocal.withInitial { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }
    private val dateTimeFmtTL = ThreadLocal.withInitial { SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH) }
    private val timeFmtTL = ThreadLocal.withInitial { SimpleDateFormat("hh:mm a", Locale.ENGLISH) }
    private val monthKeyFmtTL = ThreadLocal.withInitial { SimpleDateFormat("yyyy-MM", Locale.US) }
    private val monthLabelFmtTL = ThreadLocal.withInitial { SimpleDateFormat("MMM yyyy", Locale.ENGLISH) }

    fun format(amount: Double, includeDecimals: Boolean = true): String {
        val prefix = if (amount < 0) "-₹" else "₹"
        val absVal = abs(amount)
        val formatted = if (includeDecimals && absVal % 1.0 != 0.0) {
            indianFormatTL.get()!!.format(absVal)
        } else {
            indianWholeFormatTL.get()!!.format(absVal)
        }
        return "$prefix$formatted"
    }

    fun formatCompact(amount: Double): String {
        val prefix = if (amount < 0) "-₹" else "₹"
        val absVal = abs(amount)
        return when {
            absVal >= 1_00_00_000 -> "${prefix}${String.format(Locale.US, "%.2fCr", absVal / 1_00_00_000)}"
            absVal >= 1_00_000 -> "${prefix}${String.format(Locale.US, "%.2fL", absVal / 1_00_000)}"
            absVal >= 1_000 -> "${prefix}${String.format(Locale.US, "%.1fK", absVal / 1_000)}"
            else -> format(amount, includeDecimals = false)
        }
    }

    fun formatDate(millis: Long): String = dateFmtTL.get()!!.format(Date(millis))

    fun formatDateTime(millis: Long): String = dateTimeFmtTL.get()!!.format(Date(millis))

    fun formatTime(millis: Long): String = timeFmtTL.get()!!.format(Date(millis))

    fun monthKey(millis: Long): String = monthKeyFmtTL.get()!!.format(Date(millis))

    fun monthLabel(monthKey: String): String {
        return try {
            val date = monthKeyFmtTL.get()!!.parse(monthKey) ?: return monthKey
            monthLabelFmtTL.get()!!.format(date)
        } catch (_: Exception) {
            monthKey
        }
    }

    fun currentMonthKey(): String = monthKey(System.currentTimeMillis())

    fun previousMonthKey(monthKey: String): String {
        return try {
            val parser = monthKeyFmtTL.get()!!
            val cal = Calendar.getInstance()
            cal.time = parser.parse(monthKey) ?: Date()
            cal.add(Calendar.MONTH, -1)
            parser.format(cal.time)
        } catch (_: Exception) {
            monthKey
        }
    }

    fun daysInMonth(monthKey: String): Int {
        return try {
            val cal = Calendar.getInstance()
            cal.time = monthKeyFmtTL.get()!!.parse(monthKey) ?: Date()
            cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        } catch (_: Exception) {
            30
        }
    }
}
