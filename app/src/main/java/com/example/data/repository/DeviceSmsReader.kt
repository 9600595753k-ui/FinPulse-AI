package com.example.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat

data class RawDeviceSms(
    val sender: String,
    val body: String,
    val receivedAtMillis: Long
)

object DeviceSmsReader {

    fun hasSmsPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun readLatestInboxSms(context: Context, maxCount: Int = 200): List<RawDeviceSms> {
        if (!hasSmsPermission(context)) return emptyList()
        val results = mutableListOf<RawDeviceSms>()
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                null,
                null,
                "${Telephony.Sms.DATE} DESC"
            )?.use { cursor ->
                val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
                while (cursor.moveToNext() && results.size < maxCount) {
                    val sender = if (addrIdx >= 0) cursor.getString(addrIdx).orEmpty() else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx).orEmpty() else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
                    if (body.isNotBlank()) {
                        results.add(
                            RawDeviceSms(
                                sender = sender.ifBlank { "Unknown" },
                                body = body,
                                receivedAtMillis = date
                            )
                        )
                    }
                }
            }
        }
        return results
    }
}
