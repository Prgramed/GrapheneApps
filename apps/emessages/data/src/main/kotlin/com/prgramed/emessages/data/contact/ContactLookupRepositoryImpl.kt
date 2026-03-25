package com.prgramed.emessages.data.contact

import android.content.ContentResolver
import android.net.Uri
import android.provider.ContactsContract
import com.prgramed.emessages.domain.model.Recipient
import com.prgramed.emessages.domain.repository.ContactLookupRepository
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactLookupRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
) : ContactLookupRepository {

    private val cache = ConcurrentHashMap<String, Recipient>()
    private val maxCacheSize = 500

    override fun lookupCached(phoneNumber: String): Recipient? = cache[phoneNumber]

    override fun lookupContact(phoneNumber: String): Recipient {
        cache[phoneNumber]?.let { return it }
        // Evict oldest entries if cache is too large
        if (cache.size > maxCacheSize) {
            val keysToRemove = cache.keys.take(cache.size - maxCacheSize + 50)
            keysToRemove.forEach { cache.remove(it) }
        }

        val recipient = try {
            var contactName: String? = null
            var contactPhotoUri: String? = null
            var contactId: Long? = null

            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber),
            )
            contentResolver.query(
                lookupUri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID,
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                ),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID)
                    val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.PHOTO_URI)
                    contactId = cursor.getLong(idIdx)
                    contactName = cursor.getString(nameIdx)
                    contactPhotoUri = cursor.getString(photoIdx)
                }
            }

            Recipient(
                address = phoneNumber,
                contactName = contactName,
                contactPhotoUri = contactPhotoUri,
                contactId = contactId,
            )
        } catch (_: Exception) {
            Recipient(address = phoneNumber)
        }

        cache[phoneNumber] = recipient
        return recipient
    }

    override fun searchContacts(query: String): List<Recipient> {
        if (query.isBlank()) return emptyList()
        val results = mutableListOf<Recipient>()
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                ),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR ${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
                arrayOf("%$query%", "%$query%"),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)
                val seen = mutableSetOf<String>()
                while (cursor.moveToNext() && results.size < 20) {
                    val number = if (numberIdx >= 0) cursor.getString(numberIdx) ?: "" else ""
                    if (number.isBlank() || !seen.add(number)) continue
                    results.add(
                        Recipient(
                            address = number,
                            contactName = if (nameIdx >= 0) cursor.getString(nameIdx) else null,
                            contactPhotoUri = if (photoIdx >= 0) cursor.getString(photoIdx) else null,
                            contactId = if (idIdx >= 0) cursor.getLong(idIdx) else null,
                        ),
                    )
                }
            }
        } catch (_: Exception) {
        }
        return results
    }
}
