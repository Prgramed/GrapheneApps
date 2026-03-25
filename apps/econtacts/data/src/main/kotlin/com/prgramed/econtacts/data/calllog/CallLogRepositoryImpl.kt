package com.prgramed.econtacts.data.calllog

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import com.prgramed.econtacts.domain.model.CallType
import java.util.concurrent.ConcurrentHashMap
import com.prgramed.econtacts.domain.model.RecentCall
import com.prgramed.econtacts.domain.repository.CallLogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CallLogRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
) : CallLogRepository {

    // Cache contact lookups (name + contactId) to avoid repeated ContentResolver queries
    private val contactCache = ConcurrentHashMap<String, Pair<String?, Long?>>()

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    override fun getRecentCalls(limit: Int): Flow<List<RecentCall>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI, true, observer,
        )
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.debounce(300).map { contactCache.clear(); loadRecentCalls(limit) }.flowOn(Dispatchers.IO)

    private fun loadRecentCalls(limit: Int): List<RecentCall> = try {
        val calls = mutableListOf<RecentCall>()
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.NUMBER,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
            ),
            null, null,
            "${CallLog.Calls.DATE} DESC LIMIT $limit",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(CallLog.Calls._ID)
            val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIdx) ?: ""
                var name = cursor.getString(nameIdx)
                var contactId: Long? = null

                // If system didn't cache the name, look it up ourselves
                if (name.isNullOrBlank() && number.isNotBlank()) {
                    val lookup = lookupContact(number)
                    name = lookup?.first
                    contactId = lookup?.second
                } else if (number.isNotBlank()) {
                    contactId = lookupContact(number)?.second
                }

                calls.add(
                    RecentCall(
                        id = cursor.getLong(idIdx),
                        contactId = contactId,
                        name = name,
                        number = number,
                        type = cursor.getInt(typeIdx).toCallType(),
                        timestamp = cursor.getLong(dateIdx),
                        duration = cursor.getLong(durationIdx),
                    ),
                )
            }
        }
        calls
    } catch (e: Exception) {
        android.util.Log.e("CallLog", "Failed to load call log", e)
        emptyList()
    }

    /** Returns (displayName, contactId) or null if no matching contact found. */
    private fun lookupContact(phoneNumber: String): Pair<String?, Long?>? {
        if (contactCache.containsKey(phoneNumber)) return contactCache[phoneNumber]
        val result = try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber),
            )
            contentResolver.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup._ID,
                ),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    val id = cursor.getLong(1)
                    Pair(name, id)
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
        contactCache[phoneNumber] = result ?: Pair(null, null)
        return result
    }
}

private fun Int.toCallType(): CallType = when (this) {
    CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
    CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
    CallLog.Calls.MISSED_TYPE -> CallType.MISSED
    CallLog.Calls.REJECTED_TYPE -> CallType.MISSED
    CallLog.Calls.BLOCKED_TYPE -> CallType.MISSED
    else -> CallType.INCOMING
}
