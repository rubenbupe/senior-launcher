package com.seniorlauncher.app.data.repository

import android.content.ContentProviderOperation
import android.content.Context
import android.content.ContentValues
import android.content.ContentUris
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import com.seniorlauncher.app.data.model.Contact
import androidx.core.net.toUri

object ContactsRepository {

    private data class ContactSeed(
        val id: Long,
        val name: String,
        val photoUri: Uri?
    )

    fun setFavorite(context: Context, contactId: Long, favorite: Boolean): Boolean {
        if (contactId <= 0L) return false
        val values = ContentValues().apply {
            put(ContactsContract.Contacts.STARRED, if (favorite) 1 else 0)
        }
        return runCatching {
            val rows = context.contentResolver.update(
                ContactsContract.Contacts.CONTENT_URI,
                values,
                "${ContactsContract.Contacts._ID} = ?",
                arrayOf(contactId.toString())
            )
            rows > 0
        }.getOrDefault(false)
    }

    fun delete(context: Context, contactId: Long): Boolean {
        if (contactId <= 0L) return false
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        return runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
    }

    fun loadAll(context: Context): List<Contact> {
        return loadAllAndFavorites(context).second
    }

    fun loadFavorites(context: Context): List<Contact> {
        return loadAllAndFavorites(context).first
    }

    fun loadAllAndFavorites(context: Context): Pair<List<Contact>, List<Contact>> {
        val seeds = ArrayList<ContactSeed>()
        val favoriteIds = mutableSetOf<Long>()
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.STARRED
        )
        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIdx = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val starredIdx = cursor.getColumnIndex(ContactsContract.Contacts.STARRED)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                val name = cursor.getString(nameIdx) ?: continue
                if (name.isBlank()) continue
                val photoUri = cursor.getString(photoIdx)?.toUri()
                seeds.add(ContactSeed(id = id, name = name, photoUri = photoUri))
                if (cursor.getInt(starredIdx) == 1) favoriteIds.add(id)
            }
        }

        val phoneMap = loadPhoneMap(context, seeds.asSequence().map { it.id }.toSet())
        val all = seeds.map { seed ->
            Contact(seed.id, seed.name, seed.photoUri, phoneMap[seed.id])
        }
        val favorites = all.filter { it.id in favoriteIds }
        return favorites to all
    }

    fun loadMissedCallsByContact(
        context: Context,
        contacts: List<Contact>
    ): Pair<Map<Long, Int>, Int> {
        if (contacts.isEmpty()) return emptyMap<Long, Int>() to 0
        val phoneToContactId = contacts
            .filter { !it.phone.isNullOrBlank() }
            .associate { normalizePhone(it.phone!!) to it.id }
        if (phoneToContactId.isEmpty()) return emptyMap<Long, Int>() to 0

        val missedMap = mutableMapOf<Long, Int>()
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE)
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.NEW} = 1",
            arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
            "${CallLog.Calls.DATE} DESC"
        )?.use { cursor ->
            val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
            while (cursor.moveToNext()) {
                val rawNum = cursor.getString(numIdx).orEmpty()
                val normalized = normalizePhone(rawNum)
                val contactId = phoneToContactId[normalized] ?: continue
                missedMap[contactId] = (missedMap[contactId] ?: 0) + 1
            }
        }
        val total = missedMap.values.sum()
        return missedMap to total
    }

    fun clearMissedCallsForContact(context: Context, contact: Contact) {
        val phone = contact.phone?.replace(Regex("[^0-9+]"), "")?.trim() ?: return
        if (phone.isBlank()) return
        runCatching {
            context.contentResolver.delete(
                CallLog.Calls.CONTENT_URI,
                "${CallLog.Calls.NUMBER} = ? AND ${CallLog.Calls.TYPE} = ?",
                arrayOf(phone, CallLog.Calls.MISSED_TYPE.toString())
            )
        }
    }

    fun insert(
        context: Context,
        name: String,
        phone: String,
        photoBytes: ByteArray?
    ): Boolean {
        val operations = ArrayList<ContentProviderOperation>()
        val googleAccount = resolveDefaultGoogleAccount(context)

        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, googleAccount?.second)
                .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, googleAccount?.first)
                .build()
        )
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, name)
                .build()
        )
        operations.add(
            ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                .withValue(ContactsContract.Data.MIMETYPE,
                    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                .withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                .withValue(ContactsContract.CommonDataKinds.Phone.TYPE,
                    ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE)
                .build()
        )
        if (photoBytes != null) {
            operations.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE,
                        ContactsContract.CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                    .withValue(ContactsContract.CommonDataKinds.Photo.PHOTO, photoBytes)
                    .build()
            )
        }
        return runCatching {
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, operations)
            true
        }.getOrDefault(false)
    }

    fun normalizePhone(raw: String): String =
        raw.replace(" ", "").replace("-", "").replace("(", "").replace(")", "")

    private fun loadPhoneMap(context: Context, contactIds: Set<Long>): Map<Long, String> {
        if (contactIds.isEmpty()) return emptyMap()
        val map = mutableMapOf<Long, String>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection, null, null, null
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val numIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idIdx)
                if (id in contactIds && id !in map) {
                    map[id] = cursor.getString(numIdx) ?: ""
                }
            }
        }
        return map
    }

    private fun resolveDefaultGoogleAccount(context: Context): Pair<String, String>? {
        val projection = arrayOf(
            ContactsContract.Settings.ACCOUNT_NAME,
            ContactsContract.Settings.ACCOUNT_TYPE,
            ContactsContract.Settings.SHOULD_SYNC
        )
        context.contentResolver.query(
            ContactsContract.Settings.CONTENT_URI, projection,
            "${ContactsContract.Settings.ACCOUNT_TYPE} = ? AND ${ContactsContract.Settings.SHOULD_SYNC} = 1",
            arrayOf("com.google"), null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val name = cursor.getString(0).orEmpty()
                val type = cursor.getString(1).orEmpty()
                if (name.isNotBlank() && type == "com.google") return name to type
            }
        }
        return null
    }
}
