package com.prgramed.econtacts.data.groups

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.ContactGroup
import com.prgramed.econtacts.domain.repository.ContactRepository
import com.prgramed.econtacts.domain.repository.GroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val contactRepository: ContactRepository,
) : GroupRepository {

    override fun getAll(): Flow<List<ContactGroup>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(
            ContactsContract.Groups.CONTENT_URI, true, observer,
        )
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.debounce(300).map { loadGroups() }.flowOn(Dispatchers.IO)

    override suspend fun create(title: String): Long = withContext(Dispatchers.IO) {
        try {
            val values = ContentValues().apply {
                put(ContactsContract.Groups.TITLE, title)
                put(ContactsContract.Groups.GROUP_VISIBLE, 1)
            }
            val uri = contentResolver.insert(ContactsContract.Groups.CONTENT_URI, values)
            uri?.let { ContentUris.parseId(it) } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        try {
            val uri = ContentUris.withAppendedId(
                ContactsContract.Groups.CONTENT_URI, id,
            ).buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build()
            contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
            // ignored
        }
        Unit
    }

    override suspend fun addMember(groupId: Long, contactId: Long) = withContext(Dispatchers.IO) {
        try {
            val rawContactId = getRawContactId(contactId) ?: return@withContext
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValue(Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(Data.MIMETYPE, CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
                    .build(),
            )
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (_: Exception) {
            // ignored
        }
    }

    override suspend fun removeMember(groupId: Long, contactId: Long) = withContext(Dispatchers.IO) {
        try {
            val rawContactId = getRawContactId(contactId) ?: return@withContext
            contentResolver.delete(
                Data.CONTENT_URI,
                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ? AND ${CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
                arrayOf(
                    rawContactId.toString(),
                    CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE,
                    groupId.toString(),
                ),
            )
        } catch (_: Exception) {
            // ignored
        }
    }

    override fun getMembers(groupId: Long): Flow<List<Contact>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(Data.CONTENT_URI, true, observer)
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.debounce(300).map { loadGroupMembers(groupId) }.flowOn(Dispatchers.IO)

    private fun loadGroups(): List<ContactGroup> = try {
        val groups = mutableListOf<ContactGroup>()
        contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(
                ContactsContract.Groups._ID,
                ContactsContract.Groups.TITLE,
                ContactsContract.Groups.SUMMARY_COUNT,
            ),
            "${ContactsContract.Groups.DELETED} = 0",
            null,
            "${ContactsContract.Groups.TITLE} ASC",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID)
            val titleIdx = cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE)
            val countIdx = cursor.getColumnIndexOrThrow(ContactsContract.Groups.SUMMARY_COUNT)

            while (cursor.moveToNext()) {
                groups.add(
                    ContactGroup(
                        id = cursor.getLong(idIdx),
                        title = cursor.getString(titleIdx) ?: "",
                        memberCount = cursor.getInt(countIdx),
                    ),
                )
            }
        }
        groups
    } catch (_: Exception) {
        emptyList()
    }

    private suspend fun loadGroupMembers(groupId: Long): List<Contact> = try {
        val contactIds = mutableSetOf<Long>()
        contentResolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID),
            "${Data.MIMETYPE} = ? AND ${CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ?",
            arrayOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE, groupId.toString()),
            null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Data.CONTACT_ID)
            while (cursor.moveToNext()) {
                contactIds.add(cursor.getLong(idIdx))
            }
        }
        if (contactIds.isEmpty()) emptyList()
        else contactRepository.getAll().first().filter { it.id in contactIds }
    } catch (_: Exception) {
        emptyList()
    }

    private fun getRawContactId(contactId: Long): Long? {
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?",
            arrayOf(contactId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
            }
        }
        return null
    }
}
