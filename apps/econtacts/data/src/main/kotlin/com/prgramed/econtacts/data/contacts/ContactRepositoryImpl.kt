package com.prgramed.econtacts.data.contacts

import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.provider.ContactsContract.Data
import timber.log.Timber
import com.prgramed.econtacts.domain.model.ContactGroup
import com.prgramed.econtacts.data.sync.SyncStateDao
import com.prgramed.econtacts.data.sync.SyncStateEntity
import com.prgramed.econtacts.domain.model.Address
import com.prgramed.econtacts.domain.model.AddressType
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.Email
import com.prgramed.econtacts.domain.model.EmailType
import com.prgramed.econtacts.domain.model.PhoneNumber
import com.prgramed.econtacts.domain.model.PhoneType
import com.prgramed.econtacts.domain.repository.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ContactRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val syncStateDao: SyncStateDao,
) : ContactRepository {

    override fun getAll(): Flow<List<Contact>> =
        observeContacts().map { loadAllContacts() }.flowOn(Dispatchers.IO)

    override suspend fun getById(id: Long): Contact? = withContext(Dispatchers.IO) {
        loadContactById(id)
    }

    override fun search(query: String): Flow<List<Contact>> =
        observeContacts().map { searchContacts(query) }.flowOn(Dispatchers.IO)

    override suspend fun insert(contact: Contact): Long = withContext(Dispatchers.IO) {
        try {
            val ops = ArrayList<ContentProviderOperation>()

            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build(),
            )
            ops.add(
                ContentProviderOperation.newInsert(Data.CONTENT_URI)
                    .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                    .withValue(Data.MIMETYPE, CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                    .build(),
            )

            contact.phoneNumbers.forEach { phone ->
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                        .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Phone.NUMBER, phone.number)
                        .withValue(CommonDataKinds.Phone.TYPE, phone.type.toContactsContractType())
                        .build(),
                )
            }
            contact.emails.forEach { email ->
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                        .withValue(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Email.ADDRESS, email.address)
                        .withValue(CommonDataKinds.Email.TYPE, email.type.toContactsContractType())
                        .build(),
                )
            }
            val note = contact.note
            if (!note.isNullOrBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                        .withValue(Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Note.NOTE, note)
                        .build(),
                )
            }
            val photoUri = contact.photoUri
            if (!photoUri.isNullOrBlank()) {
                try {
                    val photoBytes = contentResolver.openInputStream(Uri.parse(photoUri))?.use { it.readBytes() }
                    if (photoBytes != null) {
                        ops.add(
                            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                                .withValueBackReference(Data.RAW_CONTACT_ID, 0)
                                .withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                .withValue(CommonDataKinds.Photo.PHOTO, photoBytes)
                                .build(),
                        )
                    }
                } catch (e: Exception) { Timber.w(e, "ContactRepository")
                    // Photo read failed, skip
                }
            }
            addExtraFieldInsertOps(ops, contact, backRef = true, backRefIndex = 0)

            val results = contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            val rawContactUri = results[0].uri ?: return@withContext 0L
            val rawContactId = ContentUris.parseId(rawContactUri)

            if (contact.starred) {
                val starOps = ArrayList<ContentProviderOperation>()
                starOps.add(
                    ContentProviderOperation.newUpdate(ContactsContract.RawContacts.CONTENT_URI)
                        .withSelection("${ContactsContract.RawContacts._ID} = ?", arrayOf(rawContactId.toString()))
                        .withValue(ContactsContract.RawContacts.STARRED, 1)
                        .build(),
                )
                contentResolver.applyBatch(ContactsContract.AUTHORITY, starOps)
            }
            // Resolve aggregate contact ID from raw contact ID
            contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts.CONTACT_ID),
                "${ContactsContract.RawContacts._ID} = ?",
                arrayOf(rawContactId.toString()), null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            } ?: rawContactId
        } catch (e: Exception) {
            Timber.w(e, "ContactRepository")
            throw e
        }
    }

    override suspend fun update(contact: Contact) = withContext(Dispatchers.IO) {
        try {
            val rawContactId = getRawContactId(contact.id) ?: return@withContext

            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newUpdate(Data.CONTENT_URI)
                    .withSelection(
                        "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                        arrayOf(rawContactId.toString(), CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
                    )
                    .withValue(CommonDataKinds.StructuredName.DISPLAY_NAME, contact.displayName)
                    .build(),
            )

            // Delete existing data rows from ALL raw contacts for this contact
            val allRawIds = getAllRawContactIds(contact.id)
            val mimeTypes = listOf(
                CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                CommonDataKinds.Note.CONTENT_ITEM_TYPE,
                CommonDataKinds.Photo.CONTENT_ITEM_TYPE,
                CommonDataKinds.Organization.CONTENT_ITEM_TYPE,
                CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE,
                CommonDataKinds.Website.CONTENT_ITEM_TYPE,
            )
            for (rid in allRawIds) {
                mimeTypes.forEach { mime ->
                    ops.add(
                        ContentProviderOperation.newDelete(Data.CONTENT_URI)
                            .withSelection(
                                "${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?",
                                arrayOf(rid.toString(), mime),
                            ).build(),
                    )
                }
            }

            contact.phoneNumbers.forEach { phone ->
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Phone.NUMBER, phone.number)
                        .withValue(CommonDataKinds.Phone.TYPE, phone.type.toContactsContractType())
                        .build(),
                )
            }
            contact.emails.forEach { email ->
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Email.ADDRESS, email.address)
                        .withValue(CommonDataKinds.Email.TYPE, email.type.toContactsContractType())
                        .build(),
                )
            }
            val note = contact.note
            if (!note.isNullOrBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(Data.CONTENT_URI)
                        .withValue(Data.RAW_CONTACT_ID, rawContactId)
                        .withValue(Data.MIMETYPE, CommonDataKinds.Note.CONTENT_ITEM_TYPE)
                        .withValue(CommonDataKinds.Note.NOTE, note)
                        .build(),
                )
            }
            val photoUri = contact.photoUri
            if (!photoUri.isNullOrBlank()) {
                try {
                    val photoBytes = contentResolver.openInputStream(Uri.parse(photoUri))?.use { it.readBytes() }
                    if (photoBytes != null) {
                        ops.add(
                            ContentProviderOperation.newInsert(Data.CONTENT_URI)
                                .withValue(Data.RAW_CONTACT_ID, rawContactId)
                                .withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
                                .withValue(CommonDataKinds.Photo.PHOTO, photoBytes)
                                .build(),
                        )
                    }
                } catch (e: Exception) { Timber.w(e, "ContactRepository")
                    // Photo read failed, skip
                }
            }
            addExtraFieldInsertOps(ops, contact, backRef = false, rawContactId = rawContactId)

            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.Contacts.CONTENT_URI)
                    .withSelection("${ContactsContract.Contacts._ID} = ?", arrayOf(contact.id.toString()))
                    .withValue(ContactsContract.Contacts.STARRED, if (contact.starred) 1 else 0)
                    .build(),
            )
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)

            // Mark as dirty for CardDAV sync
            val syncState = syncStateDao.getByContactId(contact.id)
            if (syncState != null) {
                syncStateDao.update(syncState.copy(isDirty = true))
            }
        } catch (e: Exception) {
            Timber.w(e, "ContactRepository")
            throw e
        }
    }

    override suspend fun delete(ids: List<Long>) = withContext(Dispatchers.IO) {
        try {
            // Mark as deleted-locally for CardDAV sync before removing
            ids.forEach { id ->
                val syncState = syncStateDao.getByContactId(id)
                if (syncState != null) {
                    syncStateDao.update(syncState.copy(isDeletedLocally = true))
                }
            }

            val ops = ArrayList<ContentProviderOperation>()
            ids.forEach { id ->
                val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
                ops.add(ContentProviderOperation.newDelete(uri).build())
            }
            contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            Timber.w(e, "ContactRepository")
            throw e
        }
        Unit
    }

    override fun getStarred(): Flow<List<Contact>> =
        observeContacts().map { loadContactsWhere("${ContactsContract.Contacts.STARRED} = 1") }
            .flowOn(Dispatchers.IO)

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun observeContacts(): Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI, true, observer,
        )
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.debounce(300) // Batch rapid changes (e.g. during sync)

    private fun loadContactById(id: Long): Contact? = try {
        var contact: Contact? = null
        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, CONTACT_PROJECTION,
            "${ContactsContract.Contacts._ID} = ?", arrayOf(id.toString()), null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) contact = cursorToContact(cursor)
        }
        val c = contact ?: return null
        c.copy(
            phoneNumbers = loadPhonesForContact(id),
            emails = loadEmailsForContact(id),
            note = loadNoteForContact(id),
            organization = loadOrgForContact(id),
            title = loadTitleForContact(id),
            birthday = loadBirthdayForContact(id),
            addresses = loadAddressesForContact(id),
            websites = loadWebsitesForContact(id),
            groups = loadGroupsForContact(id),
        )
    } catch (e: Exception) { Timber.w(e, "ContactRepository")
        null
    }

    private fun loadAllContacts(): List<Contact> = loadContactsWhere(null)

    private fun loadContactsWhere(selection: String?): List<Contact> = try {
        val contactIds = mutableListOf<Long>()
        val contactsMap = mutableMapOf<Long, Contact>()

        contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI, CONTACT_PROJECTION, selection, null,
            "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val c = cursorToContact(cursor)
                contactsMap[c.id] = c
                contactIds.add(c.id)
            }
        }
        if (contactIds.isEmpty()) return emptyList()

        val phonesMap = loadPhonesForContacts(contactIds)
        val emailsMap = loadEmailsForContacts(contactIds)
        val notesMap = loadNotesForContacts(contactIds)
        val orgsMap = loadOrgsForContacts(contactIds)
        val titlesMap = loadTitlesForContacts(contactIds)
        val birthdaysMap = loadBirthdaysForContacts(contactIds)
        val addressesMap = loadAddressesForContacts(contactIds)
        val websitesMap = loadWebsitesForContacts(contactIds)

        contactIds.mapNotNull { id ->
            contactsMap[id]?.copy(
                phoneNumbers = phonesMap[id] ?: emptyList(),
                emails = emailsMap[id] ?: emptyList(),
                note = notesMap[id],
                organization = orgsMap[id],
                title = titlesMap[id],
                birthday = birthdaysMap[id],
                addresses = addressesMap[id] ?: emptyList(),
                websites = websitesMap[id] ?: emptyList(),
            )
        }
    } catch (e: Exception) { Timber.w(e, "ContactRepository")
        emptyList()
    }

    private fun searchContacts(query: String): List<Contact> = try {
        val matchingIds = mutableListOf<Long>()
        val filterUri = Uri.withAppendedPath(
            ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query),
        )
        contentResolver.query(filterUri, arrayOf(ContactsContract.Contacts._ID), null, null, null)
            ?.use { cursor ->
                val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
                while (cursor.moveToNext()) matchingIds.add(cursor.getLong(idIdx))
            }
        if (matchingIds.isEmpty()) return emptyList()
        matchingIds.chunked(BATCH_SIZE).flatMap { chunk ->
            loadContactsWhere("${ContactsContract.Contacts._ID} IN (${chunk.joinToString(",")})")
        }
    } catch (e: Exception) { Timber.w(e, "ContactRepository")
        emptyList()
    }

    // --- Extra field insert ops (ORG, TITLE, BDAY, ADR, URL) ---

    private fun addExtraFieldInsertOps(
        ops: ArrayList<ContentProviderOperation>,
        contact: Contact,
        backRef: Boolean,
        backRefIndex: Int = 0,
        rawContactId: Long = 0,
    ) {
        fun newInsert(): ContentProviderOperation.Builder {
            val b = ContentProviderOperation.newInsert(Data.CONTENT_URI)
            if (backRef) b.withValueBackReference(Data.RAW_CONTACT_ID, backRefIndex)
            else b.withValue(Data.RAW_CONTACT_ID, rawContactId)
            return b
        }

        val org = contact.organization
        if (!org.isNullOrBlank()) {
            ops.add(
                newInsert()
                    .withValue(Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Organization.COMPANY, org)
                    .withValue(CommonDataKinds.Organization.TITLE, contact.title ?: "")
                    .build(),
            )
        } else if (!contact.title.isNullOrBlank()) {
            ops.add(
                newInsert()
                    .withValue(Data.MIMETYPE, CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Organization.TITLE, contact.title)
                    .build(),
            )
        }
        val bday = contact.birthday
        if (!bday.isNullOrBlank()) {
            ops.add(
                newInsert()
                    .withValue(Data.MIMETYPE, CommonDataKinds.Event.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Event.START_DATE, bday)
                    .withValue(CommonDataKinds.Event.TYPE, CommonDataKinds.Event.TYPE_BIRTHDAY)
                    .build(),
            )
        }
        contact.addresses.forEach { addr ->
            ops.add(
                newInsert()
                    .withValue(Data.MIMETYPE, CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.StructuredPostal.STREET, addr.street)
                    .withValue(CommonDataKinds.StructuredPostal.CITY, addr.city)
                    .withValue(CommonDataKinds.StructuredPostal.REGION, addr.region)
                    .withValue(CommonDataKinds.StructuredPostal.POSTCODE, addr.postalCode)
                    .withValue(CommonDataKinds.StructuredPostal.COUNTRY, addr.country)
                    .withValue(CommonDataKinds.StructuredPostal.TYPE, addr.type.toContactsContractType())
                    .build(),
            )
        }
        contact.websites.forEach { url ->
            ops.add(
                newInsert()
                    .withValue(Data.MIMETYPE, CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                    .withValue(CommonDataKinds.Website.URL, url)
                    .build(),
            )
        }
    }

    // --- Batch loaders ---

    private fun loadPhonesForContacts(ids: List<Long>): Map<Long, List<PhoneNumber>> = batchQueryMap(ids) { chunk ->
        safeQueryMap(
            CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(CommonDataKinds.Phone.CONTACT_ID, CommonDataKinds.Phone.NUMBER, CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.LABEL),
            "${CommonDataKinds.Phone.CONTACT_ID} IN (${chunk.joinToString(",")})",
        ) { cursor ->
            val number = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.NUMBER)) ?: return@safeQueryMap null
            val contactId = cursor.getLong(cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.CONTACT_ID))
            contactId to PhoneNumber(number, cursor.getInt(cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.TYPE)).toPhoneType(), cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.Phone.LABEL)))
        }
    }

    private fun loadEmailsForContacts(ids: List<Long>): Map<Long, List<Email>> = batchQueryMap(ids) { chunk ->
        safeQueryMap(
            CommonDataKinds.Email.CONTENT_URI,
            arrayOf(CommonDataKinds.Email.CONTACT_ID, CommonDataKinds.Email.ADDRESS, CommonDataKinds.Email.TYPE, CommonDataKinds.Email.LABEL),
            "${CommonDataKinds.Email.CONTACT_ID} IN (${chunk.joinToString(",")})",
        ) { cursor ->
            val address = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.Email.ADDRESS)) ?: return@safeQueryMap null
            val contactId = cursor.getLong(cursor.getColumnIndexOrThrow(CommonDataKinds.Email.CONTACT_ID))
            contactId to Email(address, cursor.getInt(cursor.getColumnIndexOrThrow(CommonDataKinds.Email.TYPE)).toEmailType(), cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.Email.LABEL)))
        }
    }

    private fun loadNotesForContacts(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val map = mutableMapOf<Long, String>()
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            try {
                contentResolver.query(
                    Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, CommonDataKinds.Note.NOTE),
                    "${Data.MIMETYPE} = ? AND ${Data.CONTACT_ID} IN (${chunk.joinToString(",")})",
                    arrayOf(CommonDataKinds.Note.CONTENT_ITEM_TYPE), null,
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(Data.CONTACT_ID)
                    val noteIdx = cursor.getColumnIndexOrThrow(CommonDataKinds.Note.NOTE)
                    while (cursor.moveToNext()) {
                        val note = cursor.getString(noteIdx)
                        if (!note.isNullOrBlank()) map[cursor.getLong(idIdx)] = note
                    }
                }
            } catch (e: Exception) { Timber.w(e, "ContactRepository") }
        }
        return map
    }

    private fun loadOrgsForContacts(ids: List<Long>): Map<Long, String> = safeQuerySingle(ids, CommonDataKinds.Organization.CONTENT_ITEM_TYPE, CommonDataKinds.Organization.COMPANY)
    private fun loadTitlesForContacts(ids: List<Long>): Map<Long, String> = safeQuerySingle(ids, CommonDataKinds.Organization.CONTENT_ITEM_TYPE, CommonDataKinds.Organization.TITLE)

    private fun loadBirthdaysForContacts(ids: List<Long>): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val map = mutableMapOf<Long, String>()
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            try {
                contentResolver.query(
                    Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, CommonDataKinds.Event.START_DATE),
                    "${Data.MIMETYPE} = ? AND ${CommonDataKinds.Event.TYPE} = ? AND ${Data.CONTACT_ID} IN (${chunk.joinToString(",")})",
                    arrayOf(CommonDataKinds.Event.CONTENT_ITEM_TYPE, CommonDataKinds.Event.TYPE_BIRTHDAY.toString()), null,
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(Data.CONTACT_ID)
                    val dateIdx = cursor.getColumnIndexOrThrow(CommonDataKinds.Event.START_DATE)
                    while (cursor.moveToNext()) {
                        val date = cursor.getString(dateIdx)
                        if (!date.isNullOrBlank()) map[cursor.getLong(idIdx)] = date
                    }
                }
            } catch (e: Exception) { Timber.w(e, "ContactRepository") }
        }
        return map
    }

    private fun loadAddressesForContacts(ids: List<Long>): Map<Long, List<Address>> = batchQueryMap(ids) { chunk ->
        safeQueryMap(
            Data.CONTENT_URI,
            arrayOf(Data.CONTACT_ID, CommonDataKinds.StructuredPostal.STREET, CommonDataKinds.StructuredPostal.CITY, CommonDataKinds.StructuredPostal.REGION, CommonDataKinds.StructuredPostal.POSTCODE, CommonDataKinds.StructuredPostal.COUNTRY, CommonDataKinds.StructuredPostal.TYPE),
            "${Data.MIMETYPE} = ? AND ${Data.CONTACT_ID} IN (${chunk.joinToString(",")})",
            selectionArgs = arrayOf(CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE),
        ) { cursor ->
            val contactId = cursor.getLong(cursor.getColumnIndexOrThrow(Data.CONTACT_ID))
            contactId to Address(
                street = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.STREET)) ?: "",
                city = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.CITY)) ?: "",
                region = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.REGION)) ?: "",
                postalCode = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.POSTCODE)) ?: "",
                country = cursor.getString(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.COUNTRY)) ?: "",
                type = cursor.getInt(cursor.getColumnIndexOrThrow(CommonDataKinds.StructuredPostal.TYPE)).toAddressType(),
            )
        }
    }

    private fun loadWebsitesForContacts(ids: List<Long>): Map<Long, List<String>> {
        if (ids.isEmpty()) return emptyMap()
        val map = mutableMapOf<Long, MutableList<String>>()
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            try {
                contentResolver.query(
                    Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, CommonDataKinds.Website.URL),
                    "${Data.MIMETYPE} = ? AND ${Data.CONTACT_ID} IN (${chunk.joinToString(",")})",
                    arrayOf(CommonDataKinds.Website.CONTENT_ITEM_TYPE), null,
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(Data.CONTACT_ID)
                    val urlIdx = cursor.getColumnIndexOrThrow(CommonDataKinds.Website.URL)
                    while (cursor.moveToNext()) {
                        val url = cursor.getString(urlIdx) ?: continue
                        map.getOrPut(cursor.getLong(idIdx)) { mutableListOf() }.add(url)
                    }
                }
            } catch (e: Exception) { Timber.w(e, "ContactRepository") }
        }
        return map
    }

    // --- Single contact loaders ---

    private fun loadGroupsForContact(id: Long): List<ContactGroup> = loadGroupsForContacts(listOf(id))[id] ?: emptyList()

    private fun loadGroupsForContacts(ids: List<Long>): Map<Long, List<ContactGroup>> {
        if (ids.isEmpty()) return emptyMap()
        val map = mutableMapOf<Long, MutableList<ContactGroup>>()
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            try {
                contentResolver.query(
                    Data.CONTENT_URI,
                    arrayOf(Data.CONTACT_ID, CommonDataKinds.GroupMembership.GROUP_ROW_ID),
                    "${Data.MIMETYPE} = ? AND ${Data.CONTACT_ID} IN (${chunk.joinToString(",")})",
                    arrayOf(CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE), null,
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(Data.CONTACT_ID)
                    val groupIdx = cursor.getColumnIndexOrThrow(CommonDataKinds.GroupMembership.GROUP_ROW_ID)
                    while (cursor.moveToNext()) {
                        val contactId = cursor.getLong(idIdx)
                        val groupId = cursor.getLong(groupIdx)
                        val groupName = resolveGroupName(groupId) ?: continue
                        map.getOrPut(contactId) { mutableListOf() }.add(ContactGroup(id = groupId, title = groupName))
                    }
                }
            } catch (e: Exception) { Timber.w(e, "ContactRepository") }
        }
        return map
    }

    private fun resolveGroupName(groupId: Long): String? = try {
        var name: String? = null
        contentResolver.query(
            ContactsContract.Groups.CONTENT_URI,
            arrayOf(ContactsContract.Groups.TITLE),
            "${ContactsContract.Groups._ID} = ?",
            arrayOf(groupId.toString()), null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) name = cursor.getString(0)
        }
        name
    } catch (e: Exception) { Timber.w(e, "ContactRepository"); null }

    private fun loadPhonesForContact(id: Long): List<PhoneNumber> = loadPhonesForContacts(listOf(id))[id] ?: emptyList()
    private fun loadEmailsForContact(id: Long): List<Email> = loadEmailsForContacts(listOf(id))[id] ?: emptyList()
    private fun loadNoteForContact(id: Long): String? = loadNotesForContacts(listOf(id))[id]
    private fun loadOrgForContact(id: Long): String? = loadOrgsForContacts(listOf(id))[id]
    private fun loadTitleForContact(id: Long): String? = loadTitlesForContacts(listOf(id))[id]
    private fun loadBirthdayForContact(id: Long): String? = loadBirthdaysForContacts(listOf(id))[id]
    private fun loadAddressesForContact(id: Long): List<Address> = loadAddressesForContacts(listOf(id))[id] ?: emptyList()
    private fun loadWebsitesForContact(id: Long): List<String> = loadWebsitesForContacts(listOf(id))[id] ?: emptyList()

    // --- Helpers ---

    private fun <T> safeQueryMap(
        uri: Uri,
        projection: Array<String>,
        selection: String,
        selectionArgs: Array<String>? = null,
        mapper: (Cursor) -> Pair<Long, T>?,
    ): Map<Long, List<T>> {
        val map = mutableMapOf<Long, MutableList<T>>()
        try {
            contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val pair = mapper(cursor) ?: continue
                    map.getOrPut(pair.first) { mutableListOf() }.add(pair.second)
                }
            }
        } catch (e: Exception) { Timber.w(e, "ContactRepository") }
        return map
    }

    private fun safeQuerySingle(ids: List<Long>, mimeType: String, column: String): Map<Long, String> {
        if (ids.isEmpty()) return emptyMap()
        val map = mutableMapOf<Long, String>()
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            try {
                contentResolver.query(
                    Data.CONTENT_URI, arrayOf(Data.CONTACT_ID, column),
                    "${Data.MIMETYPE} = ? AND ${Data.CONTACT_ID} IN (${chunk.joinToString(",")})",
                    arrayOf(mimeType), null,
                )?.use { cursor ->
                    val idIdx = cursor.getColumnIndexOrThrow(Data.CONTACT_ID)
                    val valIdx = cursor.getColumnIndexOrThrow(column)
                    while (cursor.moveToNext()) {
                        val v = cursor.getString(valIdx)
                        if (!v.isNullOrBlank()) map[cursor.getLong(idIdx)] = v
                    }
                }
            } catch (e: Exception) { Timber.w(e, "ContactRepository") }
        }
        return map
    }

    private fun cursorToContact(cursor: Cursor): Contact {
        val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
        val lookupIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.LOOKUP_KEY)
        val nameIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
        val photoIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.PHOTO_URI)
        val starredIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED)
        return Contact(
            id = cursor.getLong(idIdx),
            lookupKey = cursor.getString(lookupIdx) ?: "",
            displayName = cursor.getString(nameIdx) ?: "",
            photoUri = cursor.getString(photoIdx),
            starred = cursor.getInt(starredIdx) == 1,
        )
    }

    private fun getAllRawContactIds(contactId: Long): List<Long> = try {
        val ids = mutableListOf<Long>()
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?", arrayOf(contactId.toString()), null,
        )?.use { cursor ->
            while (cursor.moveToNext()) ids.add(cursor.getLong(0))
        }
        ids
    } catch (e: Exception) { Timber.w(e, "ContactRepository")
        emptyList()
    }

    private fun getRawContactId(contactId: Long): Long? = try {
        var result: Long? = null
        contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI, arrayOf(ContactsContract.RawContacts._ID),
            "${ContactsContract.RawContacts.CONTACT_ID} = ?", arrayOf(contactId.toString()), null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) result = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.RawContacts._ID))
        }
        result
    } catch (e: Exception) { Timber.w(e, "ContactRepository")
        null
    }

    private fun <T> batchQueryMap(
        ids: List<Long>,
        query: (List<Long>) -> Map<Long, List<T>>,
    ): Map<Long, List<T>> {
        if (ids.isEmpty()) return emptyMap()
        val result = mutableMapOf<Long, MutableList<T>>()
        ids.chunked(BATCH_SIZE).forEach { chunk ->
            query(chunk).forEach { (key, values) ->
                result.getOrPut(key) { mutableListOf() }.addAll(values)
            }
        }
        return result
    }

    companion object {
        private const val BATCH_SIZE = 500
        private val CONTACT_PROJECTION = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.STARRED,
        )
    }
}

private fun PhoneType.toContactsContractType(): Int = when (this) {
    PhoneType.MOBILE -> CommonDataKinds.Phone.TYPE_MOBILE
    PhoneType.HOME -> CommonDataKinds.Phone.TYPE_HOME
    PhoneType.WORK -> CommonDataKinds.Phone.TYPE_WORK
    PhoneType.OTHER -> CommonDataKinds.Phone.TYPE_OTHER
}

private fun Int.toPhoneType(): PhoneType = when (this) {
    CommonDataKinds.Phone.TYPE_MOBILE -> PhoneType.MOBILE
    CommonDataKinds.Phone.TYPE_HOME -> PhoneType.HOME
    CommonDataKinds.Phone.TYPE_WORK -> PhoneType.WORK
    else -> PhoneType.OTHER
}

private fun EmailType.toContactsContractType(): Int = when (this) {
    EmailType.HOME -> CommonDataKinds.Email.TYPE_HOME
    EmailType.WORK -> CommonDataKinds.Email.TYPE_WORK
    EmailType.OTHER -> CommonDataKinds.Email.TYPE_OTHER
}

private fun Int.toEmailType(): EmailType = when (this) {
    CommonDataKinds.Email.TYPE_HOME -> EmailType.HOME
    CommonDataKinds.Email.TYPE_WORK -> EmailType.WORK
    else -> EmailType.OTHER
}

private fun AddressType.toContactsContractType(): Int = when (this) {
    AddressType.HOME -> CommonDataKinds.StructuredPostal.TYPE_HOME
    AddressType.WORK -> CommonDataKinds.StructuredPostal.TYPE_WORK
    AddressType.OTHER -> CommonDataKinds.StructuredPostal.TYPE_OTHER
}

private fun Int.toAddressType(): AddressType = when (this) {
    CommonDataKinds.StructuredPostal.TYPE_HOME -> AddressType.HOME
    CommonDataKinds.StructuredPostal.TYPE_WORK -> AddressType.WORK
    else -> AddressType.OTHER
}
