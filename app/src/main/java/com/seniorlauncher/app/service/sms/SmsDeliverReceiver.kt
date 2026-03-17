package com.seniorlauncher.app.service.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val extras = intent.extras ?: return
        val rawPdus = extras.get("pdus") as? Array<*> ?: return
        if (rawPdus.isEmpty()) return

        val format = extras.getString("format")
        val parts = rawPdus.mapNotNull { pdu ->
            val pduBytes = pdu as? ByteArray ?: return@mapNotNull null
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    SmsMessage.createFromPdu(pduBytes, format)
                } else {
                    @Suppress("DEPRECATION")
                    SmsMessage.createFromPdu(pduBytes)
                }
            }.getOrNull()
        }

        if (parts.isEmpty()) return

        val address = parts.firstOrNull()?.originatingAddress.orEmpty().trim()
        val body = parts.joinToString(separator = "") { msg ->
            msg.displayMessageBody ?: msg.messageBody.orEmpty()
        }.trim()
        val date = parts.minOfOrNull { it.timestampMillis } ?: System.currentTimeMillis()

        if (address.isBlank() || body.isBlank()) return
        if (isAlreadyStored(context, address, body, date)) return

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, date)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_COMPLETE)
            put(Telephony.Sms.PROTOCOL, 0)
        }

        runCatching {
            context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        }.onFailure { err ->
            Log.e("SmsDeliverReceiver", "Could not send incoming SMS", err)
        }
    }

    private fun isAlreadyStored(
        context: Context,
        address: String,
        body: String,
        date: Long
    ): Boolean {
        val projection = arrayOf(Telephony.Sms._ID)
        val selection = "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.BODY} = ? AND ${Telephony.Sms.DATE} = ?"
        val args = arrayOf(address, body, date.toString())
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.Inbox.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }
}
