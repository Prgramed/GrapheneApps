package com.prgramed.econtacts.domain.repository

import android.net.Uri

interface VCardRepository {
    suspend fun exportContacts(ids: List<Long>): Uri
    suspend fun importContacts(uri: Uri): Int
}
