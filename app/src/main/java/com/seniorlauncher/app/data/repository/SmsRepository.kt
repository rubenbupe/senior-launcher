package com.seniorlauncher.app.data.repository

import android.app.role.RoleManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import com.seniorlauncher.app.data.model.SmsConversation
import com.seniorlauncher.app.data.model.SmsItem

object SmsRepository {


    fun loadConversations(
        context: Context,
        limit: Int = Int.MAX_VALUE,
        resolveDisplayNames: Boolean = true
    ): List<SmsConversation> {
        val map = LinkedHashMap<Long, SmsConversation>()
        val displayNameCache = mutableMapOf<String, String>()
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ
        )
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )?.use { cursor ->
            val threadIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addressIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx   = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx   = cursor.getColumnIndex(Telephony.Sms.DATE)
            val readIdx   = cursor.getColumnIndex(Telephony.Sms.READ)

            while (cursor.moveToNext()) {
                if (threadIdx < 0 || addressIdx < 0 || bodyIdx < 0 || dateIdx < 0 || readIdx < 0) continue

                val threadId   = cursor.getLong(threadIdx)
                val rawAddress = cursor.getString(addressIdx).orEmpty().ifBlank { "Desconocido" }
                val address = if (resolveDisplayNames) {
                    displayNameCache.getOrPut(rawAddress) { resolveDisplayName(context, rawAddress) }
                } else {
                    rawAddress
                }
                val body     = cursor.getString(bodyIdx).orEmpty()
                val date     = cursor.getLong(dateIdx)
                val isUnread = cursor.getInt(readIdx) == 0

                val existing = map[threadId]
                if (existing == null) {
                    map[threadId] = SmsConversation(
                        threadId   = threadId,
                        address    = address,
                        preview    = body,
                        timestamp  = date,
                        unreadCount = if (isUnread) 1 else 0
                    )
                } else if (isUnread) {
                    map[threadId] = existing.copy(unreadCount = existing.unreadCount + 1)
                }

                if (map.size >= limit) break
            }
        }
        return map.values.toList().sortedByDescending { it.timestamp }
    }

    fun loadThreadMessages(
        context: Context,
        threadId: Long,
        limit: Int = Int.MAX_VALUE
    ): List<SmsItem> {
        val result = mutableListOf<SmsItem>()
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            projection,
            "${Telephony.Sms.THREAD_ID} = ?",
            arrayOf(threadId.toString()),
            "${Telephony.Sms.DATE} ASC"
        )?.use { cursor ->
            val idIdx   = cursor.getColumnIndex(Telephony.Sms._ID)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndex(Telephony.Sms.TYPE)

            while (cursor.moveToNext()) {
                if (idIdx < 0 || bodyIdx < 0 || dateIdx < 0 || typeIdx < 0) continue
                if (result.size >= limit) break
                result.add(
                    SmsItem(
                        id         = cursor.getLong(idIdx),
                        body       = cursor.getString(bodyIdx).orEmpty(),
                        timestamp  = cursor.getLong(dateIdx),
                        isSentByMe = cursor.getInt(typeIdx) == Telephony.Sms.MESSAGE_TYPE_SENT
                    )
                )
            }
        }
        return result
    }

    fun getUnreadCount(context: Context): Int {
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.READ} = 0",
                null,
                null
            )?.use { it.count } ?: 0
        }.getOrDefault(0)
    }

    fun markAllAsRead(context: Context): Int {
        return runCatching {
            val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.READ} = 0",
                null
            )
        }.getOrDefault(0)
    }

    fun markThreadAsRead(context: Context, threadId: Long): Int {
        return runCatching {
            val values = ContentValues().apply { put(Telephony.Sms.READ, 1) }
            context.contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                values,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString())
            )
        }.getOrDefault(0)
    }

    fun deleteConversation(context: Context, threadId: Long): Int {
        return runCatching {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString())
            )
        }.getOrDefault(0)
    }

    fun deleteAll(context: Context): Int {
        return runCatching {
            context.contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                null,
                null
            )
        }.getOrDefault(0)
    }


    private fun resolveDisplayName(context: Context, rawAddress: String): String {
        val normalized = rawAddress.trim()
        if (normalized.isBlank()) return "Desconocido"
        return runCatching {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(normalized)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0).orEmpty().ifBlank { normalized }
                } else {
                    normalized
                }
            } ?: normalized
        }.getOrDefault(normalized)
    }

}
