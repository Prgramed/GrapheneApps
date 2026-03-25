package com.prgramed.econtacts.data.calllog

import android.content.ContentResolver
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.ContactsContract
import com.prgramed.econtacts.domain.model.CallType
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

    // Cache name lookups to avoid repeated ContentResolver queries
    private val nameCache = mutableMapOf<String, String?>()

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
    }.debounce(300).map { nameCache.clear(); loadRecentCalls(limit) }.flowOn(Dispatchers.IO)

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
            val nameIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.CACHED_NAME)
            val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)

            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIdx) ?: ""
                var name = cursor.getString(nameIdx)

                // If system didn't cache the name, look it up ourselves
                if (name.isNullOrBlank() && number.isNotBlank()) {
                    name = lookupContactName(number)
                }

                calls.add(
                    RecentCall(
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

    private fun lookupContactName(phoneNumber: String): String? {
        if (phoneNumber in nameCache) return nameCache[phoneNumber]
        val result = try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber),
            )
            contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } catch (_: Exception) {
            null
        }
        nameCache[phoneNumber] = result
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
