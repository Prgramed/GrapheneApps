package com.prgramed.econtacts.data.carddav

import com.prgramed.econtacts.domain.model.CardDavAccount
import com.prgramed.econtacts.domain.model.Contact
import com.prgramed.econtacts.domain.model.SyncResult
import com.prgramed.econtacts.domain.repository.ContactRepository
import com.prgramed.econtacts.data.sync.SyncStateDao
import com.prgramed.econtacts.data.sync.SyncStateEntity
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardDavSyncEngine @Inject constructor(
    private val cardDavClient: CardDavClient,
    private val contactRepository: ContactRepository,
    private val syncStateDao: SyncStateDao,
) {

    suspend fun sync(account: CardDavAccount, password: String): SyncResult {
        val errors = mutableListOf<String>()
        var added = 0
        var updated = 0
        var deleted = 0

        try {
            val addressBookUrl = buildAddressBookUrl(account)

            // Step 1: Get remote contact list (hrefs + ETags)
            // Try REPORT first, then PROPFIND — use whichever returns contacts
            var responseXml = cardDavClient.propfind(
                addressBookUrl,
                CardDavXmlBuilder.propfindAddressBook(),
                account.username,
                password,
            )
            var allResourcesTmp = try {
                CardDavXmlParser.parseMultistatus(responseXml)
            } catch (_: Exception) { emptyList() }

            // If PROPFIND only returned the collection itself, try REPORT
            if (allResourcesTmp.size <= 1) {
                try {
                    val reportXml = cardDavClient.report(
                        addressBookUrl,
                        CardDavXmlBuilder.addressBookQuery(),
                        account.username,
                        password,
                    )
                    val reportResources = CardDavXmlParser.parseMultistatus(reportXml)
                    if (reportResources.size > allResourcesTmp.size) {
                        responseXml = reportXml
                        allResourcesTmp = reportResources
                    }
                } catch (_: Exception) { }
            }
            val allResources = allResourcesTmp

            // Filter to actual contact resources (have etag, not the collection itself)
            val addressBookHref = try { java.net.URI(addressBookUrl).path } catch (_: Exception) { "" }
            val remoteResources = allResources.filter { res ->
                res.etag.isNotBlank() && res.href.trimEnd('/') != addressBookHref.trimEnd('/')
            }
            val remoteByHref = remoteResources.associateBy { it.href }

            // Step 2: Load local sync state
            val localSyncStates = syncStateDao.getAll()
            val localByHref = localSyncStates.associateBy { it.remoteHref }
            val syncedContactIds = localSyncStates.map { it.contactId }.toSet()

            // Step 3a: Upload locally dirty contacts (previously synced, now changed)
            val dirtyStates = syncStateDao.getDirty()
            for (state in dirtyStates) {
                try {
                    val contact = contactRepository.getById(state.contactId) ?: continue
                    val vcard = VCardBuilder.build(contact)
                    val uid = state.uid.ifBlank { contact.uid ?: UUID.randomUUID().toString() }
                    val contactUrl = if (state.remoteHref.isNotBlank()) {
                        resolveUrl(account.serverUrl, state.remoteHref)
                    } else {
                        "$addressBookUrl$uid.vcf"
                    }
                    val newEtag = cardDavClient.put(
                        contactUrl, vcard, account.username, password,
                        etag = state.etag.ifBlank { null },
                    )
                    val href = extractHref(contactUrl, account.serverUrl)
                    syncStateDao.update(
                        state.copy(
                            remoteHref = href,
                            etag = newEtag,
                            uid = uid,
                            isDirty = false,
                            lastSynced = System.currentTimeMillis(),
                        ),
                    )
                    updated++
                } catch (e: Exception) {
                    errors.add("Upload failed for contact ${state.contactId}: ${e.message}")
                }
            }

            // Step 3b: Upload local contacts that have never been synced
            val allLocalContacts = contactRepository.getAll().first()
            val unsyncedContacts = allLocalContacts.filter { it.id !in syncedContactIds }
            for (contact in unsyncedContacts) {
                try {
                    val uid = contact.uid ?: UUID.randomUUID().toString()
                    val vcard = VCardBuilder.build(contact.copy(uid = uid))
                    val contactUrl = "$addressBookUrl$uid.vcf"
                    val newEtag = cardDavClient.put(
                        contactUrl, vcard, account.username, password,
                        etag = null,
                    )
                    val href = extractHref(contactUrl, account.serverUrl)
                    syncStateDao.insert(
                        SyncStateEntity(
                            contactId = contact.id,
                            remoteHref = href,
                            etag = newEtag,
                            uid = uid,
                            lastSynced = System.currentTimeMillis(),
                        ),
                    )
                    added++
                } catch (e: Exception) {
                    errors.add("Initial upload failed for ${contact.displayName}: ${e.message}")
                }
            }

            // Step 4: Delete remotely contacts deleted locally
            val deletedStates = syncStateDao.getDeletedLocally()
            for (state in deletedStates) {
                try {
                    if (state.remoteHref.isNotBlank()) {
                        val deleteUrl = resolveUrl(account.serverUrl, state.remoteHref)
                        cardDavClient.delete(deleteUrl, account.username, password, state.etag.ifBlank { null })
                    }
                    syncStateDao.deleteById(state.id)
                    deleted++
                } catch (e: Exception) {
                    errors.add("Remote delete failed: ${e.message}")
                }
            }

            // Step 5: Process remote changes
            // Find new or changed remote contacts
            val hrefsToFetch = mutableListOf<String>()
            for ((href, remote) in remoteByHref) {
                val local = localByHref[href]
                if (local == null || local.etag != remote.etag) {
                    hrefsToFetch.add(href)
                }
            }

            // Fetch changed vCards via REPORT
            if (hrefsToFetch.isNotEmpty()) {
                val fullHrefs = hrefsToFetch.map { href ->
                    if (href.startsWith("/")) href
                    else "/${href}"
                }
                val reportXml = cardDavClient.report(
                    addressBookUrl,
                    CardDavXmlBuilder.addressBookMultiget(fullHrefs),
                    account.username,
                    password,
                )
                val fetchedResources = CardDavXmlParser.parseMultistatus(reportXml)

                for (resource in fetchedResources) {
                    val vcardData = resource.vcardData ?: continue
                    try {
                        val parsedContact = VCardParser.parse(vcardData)
                        val existingState = localByHref[resource.href]
                            ?: syncStateDao.getByUid(parsedContact.uid ?: "")

                        if (existingState != null) {
                            val existingContact = contactRepository.getById(existingState.contactId)
                            if (existingContact != null) {
                                if (existingState.isDirty) {
                                    // Conflict: locally modified AND remotely changed
                                    // Keep local version, save remote as a new "[Conflict]" contact
                                    val conflictContact = parsedContact.copy(
                                        id = 0,
                                        displayName = "[Conflict] ${parsedContact.displayName}",
                                    )
                                    contactRepository.insert(conflictContact)
                                    // Mark local as no longer dirty (user keeps their version)
                                    syncStateDao.update(
                                        existingState.copy(
                                            isDirty = true, // still needs to push local version
                                            lastSynced = System.currentTimeMillis(),
                                        ),
                                    )
                                    errors.add("Conflict: ${existingContact.displayName} changed both locally and remotely")
                                } else {
                                    // No conflict: update local from remote
                                    contactRepository.update(
                                        existingContact.copy(
                                            displayName = parsedContact.displayName,
                                            phoneNumbers = parsedContact.phoneNumbers,
                                            emails = parsedContact.emails,
                                            organization = parsedContact.organization,
                                            title = parsedContact.title,
                                            birthday = parsedContact.birthday,
                                            addresses = parsedContact.addresses,
                                            websites = parsedContact.websites,
                                            note = parsedContact.note,
                                        ),
                                    )
                                    syncStateDao.update(
                                        existingState.copy(
                                            remoteHref = resource.href,
                                            etag = resource.etag,
                                            isDirty = false,
                                            lastSynced = System.currentTimeMillis(),
                                        ),
                                    )
                                    updated++
                                }
                            }
                        } else {
                            // Insert new contact
                            val newId = contactRepository.insert(parsedContact)
                            syncStateDao.insert(
                                SyncStateEntity(
                                    contactId = newId,
                                    remoteHref = resource.href,
                                    etag = resource.etag,
                                    uid = parsedContact.uid ?: "",
                                    lastSynced = System.currentTimeMillis(),
                                ),
                            )
                            added++
                        }
                    } catch (e: Exception) {
                        errors.add("Import failed for ${resource.href}: ${e.message}")
                    }
                }
            }

            // Step 6: Delete local contacts removed from server
            val remoteHrefs = remoteByHref.keys
            for (state in localSyncStates) {
                if (state.remoteHref.isNotBlank() && state.remoteHref !in remoteHrefs && !state.isDirty) {
                    try {
                        contactRepository.delete(listOf(state.contactId))
                        syncStateDao.deleteById(state.id)
                        deleted++
                    } catch (e: Exception) {
                        errors.add("Local delete failed: ${e.message}")
                    }
                }
            }

        } catch (e: Exception) {
            errors.add("Sync failed: ${e.message}")
        }

        return SyncResult(added = added, updated = updated, deleted = deleted, errors = errors)
    }

    private fun buildAddressBookUrl(account: CardDavAccount): String {
        val base = account.serverUrl.trimEnd('/')
        val path = account.addressBookPath.trim('/')
        return if (path.isNotBlank()) "$base/$path/" else "$base/"
    }

    private fun resolveUrl(serverUrl: String, href: String): String {
        if (href.startsWith("http")) return href
        val base = serverUrl.trimEnd('/')
        val scheme = if (base.contains("://")) base.substringBefore("://") + "://" else "https://"
        val host = base.removePrefix(scheme).substringBefore("/")
        return "$scheme$host$href"
    }

    private fun extractHref(fullUrl: String, serverUrl: String): String {
        val scheme = if (serverUrl.contains("://")) serverUrl.substringBefore("://") + "://" else "https://"
        val host = serverUrl.removePrefix(scheme).trimEnd('/').substringBefore("/")
        return fullUrl.removePrefix("$scheme$host")
    }
}
