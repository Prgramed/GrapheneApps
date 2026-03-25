package com.prgramed.emessages.domain.repository

import com.prgramed.emessages.domain.model.Recipient

interface ContactLookupRepository {
    fun lookupContact(phoneNumber: String): Recipient
    fun lookupCached(phoneNumber: String): Recipient?
    fun searchContacts(query: String): List<Recipient>
}
