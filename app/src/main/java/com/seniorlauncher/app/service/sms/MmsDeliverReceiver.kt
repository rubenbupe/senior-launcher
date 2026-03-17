package com.seniorlauncher.app.service.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log

class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return

        val contentType = intent.getStringExtra("mimeType").orEmpty()
        if (!contentType.contains("application/vnd.wap.mms-message", ignoreCase = true) &&
            !contentType.contains("application/vnd.wap.multipart", ignoreCase = true)
        ) {
            Log.d("MmsDeliverReceiver", "WAP Push ignored: $contentType")
            return
        }

        val pushData = intent.getByteArrayExtra("data") ?: run {
            Log.w("MmsDeliverReceiver", "WAP Push without data")
            return
        }

        val location = extractMmsLocation(pushData) ?: run {
            Log.w("MmsDeliverReceiver", "Could not extract MMS URL")
            return
        }

        if (isAlreadyDownloaded(context, location)) {
            Log.d("MmsDeliverReceiver", "MMS already downloaded: $location")
            return
        }

        val smsManager = getSmsManager(context) ?: run {
            Log.e("MmsDeliverReceiver", "SmsManager not available")
            return
        }

        runCatching {
            smsManager.downloadMultimediaMessage(
                context,
                location,
                null,   // null → Android chooses destination uri - content://mms/inbox
                null,
                null
            )
            Log.i("MmsDeliverReceiver", "MMS download started: $location")
        }.onFailure { err ->
            Log.e("MmsDeliverReceiver", "Could not download MMS", err)
        }
    }

    /**
     * Extracts MMS download URL (X-Mms-Content-Location) from binary PDU WAP Push.
     */
    private fun extractMmsLocation(pushData: ByteArray): String? {
        return runCatching {
            val raw = String(pushData, Charsets.ISO_8859_1)
            Regex("https?://[\\w./?=&%+:@#-]+").find(raw)?.value
        }.getOrNull()
    }

    /**
     * Checks if and MMS with an origin URL from content://mms
     * to avoid duplicates.
     */
    private fun isAlreadyDownloaded(context: Context, location: String): Boolean {
        val projection = arrayOf(Telephony.Mms._ID)
        val selection = "${Telephony.Mms.CONTENT_LOCATION} = ?"
        val args = arrayOf(location)
        return runCatching {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                projection,
                selection,
                args,
                null
            )?.use { cursor ->
                cursor.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    private fun getSmsManager(context: Context): SmsManager? {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
        }.getOrNull()
    }
}
