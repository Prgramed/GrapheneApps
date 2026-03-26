package com.prgramed.emessages.data.conversation

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.prgramed.emessages.domain.model.Conversation
import com.prgramed.emessages.domain.model.Recipient
import com.prgramed.emessages.domain.repository.ContactLookupRepository
import com.prgramed.emessages.domain.repository.ConversationRepository
import com.prgramed.emessages.domain.repository.DeletedConversation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    private val contactLookup: ContactLookupRepository,
    private val notificationManager: com.prgramed.emessages.data.notification.MessageNotificationManager,
    private val deletedManager: DeletedConversationsManager,
) : ConversationRepository {

    init {
        // Purge expired deleted conversations on startup
        purgeExpired()
    }

    override fun getAll(): Flow<List<Conversation>> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        trySend(Unit)
        // Single observer on mms-sms covers both SMS and MMS changes
        contentResolver.registerContentObserver(
            Uri.parse("content://mms-sms/"), true, observer,
        )
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.debounce(500).map {
        loadConversations().filter { !deletedManager.isDeleted(it.threadId) }
    }.flowOn(Dispatchers.IO)

    override fun search(query: String): Flow<List<Conversation>> = flow {
        val lowerQuery = query.lowercase()
        emit(
            loadConversations()
                .filter { !deletedManager.isDeleted(it.threadId) }
                .filter { conversation ->
                    conversation.snippet.lowercase().contains(lowerQuery) ||
                        conversation.recipients.any { recipient ->
                            recipient.contactName?.lowercase()?.contains(lowerQuery) == true ||
                                recipient.address.contains(lowerQuery)
                        }
                },
        )
    }.flowOn(Dispatchers.IO)

    override suspend fun delete(threadIds: List<Long>) = withContext(Dispatchers.IO) {
        threadIds.forEach { threadId ->
            deletedManager.markDeleted(threadId)
            notificationManager.cancelNotification(threadId)
        }
        Unit
    }

    override suspend fun markAsRead(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            val smsValues = ContentValues().apply { put(Telephony.Sms.READ, 1) }
            contentResolver.update(
                Telephony.Sms.CONTENT_URI,
                smsValues,
                "${Telephony.Sms.THREAD_ID} = ? AND ${Telephony.Sms.READ} = 0",
                arrayOf(threadId.toString()),
            )
            val mmsValues = ContentValues().apply { put(Telephony.Mms.READ, 1) }
            contentResolver.update(
                Telephony.Mms.CONTENT_URI,
                mmsValues,
                "${Telephony.Mms.THREAD_ID} = ? AND ${Telephony.Mms.READ} = 0",
                arrayOf(threadId.toString()),
            )
        } catch (_: Exception) {
        }
        notificationManager.cancelNotification(threadId)
        Unit
    }

    override suspend fun markAsUnread(threadId: Long) = withContext(Dispatchers.IO) {
        try {
            // Mark most recent SMS as unread
            contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID),
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT 1",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val values = ContentValues().apply { put(Telephony.Sms.READ, 0) }
                    contentResolver.update(
                        ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id),
                        values, null, null,
                    )
                }
            }
            // Also mark most recent MMS as unread
            contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID),
                "${Telephony.Mms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Mms.DATE} DESC LIMIT 1",
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    val values = ContentValues().apply { put(Telephony.Mms.READ, 0) }
                    contentResolver.update(
                        ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id),
                        values, null, null,
                    )
                }
            }
        } catch (_: Exception) {
        }
        Unit
    }

    override fun getDeletedConversations(): Flow<List<DeletedConversation>> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        trySend(Unit)
        contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI, true, observer,
        )
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.map {
        val deletedEntries = deletedManager.getDeletedEntries()
        val now = System.currentTimeMillis()
        loadConversations()
            .filter { it.threadId in deletedEntries }
            .map { conv ->
                val deletedAt = deletedEntries[conv.threadId] ?: now
                val msRemaining = (deletedAt + DeletedConversationsManager.RETENTION_MS) - now
                val daysRemaining = (msRemaining / (24 * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
                DeletedConversation(
                    conversation = conv,
                    deletedAt = deletedAt,
                    daysRemaining = daysRemaining,
                )
            }
            .sortedByDescending { it.deletedAt }
    }.flowOn(Dispatchers.IO)

    override suspend fun restoreConversation(threadId: Long) = withContext(Dispatchers.IO) {
        deletedManager.restore(threadId)
        Unit
    }

    override suspend fun permanentlyDelete(threadIds: List<Long>) = withContext(Dispatchers.IO) {
        threadIds.forEach { threadId ->
            try {
                contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(threadId.toString()),
                )
                contentResolver.delete(
                    Telephony.Mms.CONTENT_URI,
                    "${Telephony.Mms.THREAD_ID} = ?",
                    arrayOf(threadId.toString()),
                )
            } catch (_: Exception) {
            }
            deletedManager.remove(threadId)
        }
        Unit
    }

    private fun purgeExpired() {
        val expired = deletedManager.getExpiredThreadIds()
        if (expired.isEmpty()) return
        expired.forEach { threadId ->
            try {
                contentResolver.delete(
                    Telephony.Sms.CONTENT_URI,
                    "${Telephony.Sms.THREAD_ID} = ?",
                    arrayOf(threadId.toString()),
                )
                contentResolver.delete(
                    Telephony.Mms.CONTENT_URI,
                    "${Telephony.Mms.THREAD_ID} = ?",
                    arrayOf(threadId.toString()),
                )
            } catch (_: Exception) {
            }
            deletedManager.remove(threadId)
        }
    }

    private fun loadConversations(): List<Conversation> {
        data class ThreadInfo(
            var snippet: String = "",
            var timestamp: Long = 0,
            var address: String = "",
            var unreadCount: Int = 0,
        )

        val threads = linkedMapOf<Long, ThreadInfo>()

        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ,
            ),
            "${Telephony.Sms.THREAD_ID} IS NOT NULL",
            null,
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val threadIdx = cursor.getColumnIndex(Telephony.Sms.THREAD_ID)
            val addrIdx = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndex(Telephony.Sms.DATE)
            val readIdx = cursor.getColumnIndex(Telephony.Sms.READ)

            if (threadIdx < 0) return@use

            while (cursor.moveToNext()) {
                val threadId = cursor.getLong(threadIdx)
                val isRead = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true

                val info = threads.getOrPut(threadId) {
                    ThreadInfo(
                        snippet = if (bodyIdx >= 0) (cursor.getString(bodyIdx) ?: "").replace("\uFFFC", "").trim() else "",
                        timestamp = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L,
                        address = if (addrIdx >= 0) cursor.getString(addrIdx) ?: "" else "",
                    )
                }
                if (!isRead) info.unreadCount++
            }
        }

        try {
            contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(
                    Telephony.Mms.THREAD_ID,
                    Telephony.Mms.DATE,
                    Telephony.Mms.READ,
                ),
                "${Telephony.Mms.THREAD_ID} IS NOT NULL", null,
                "${Telephony.Mms.DATE} DESC LIMIT 200",
            )?.use { cursor ->
                val threadIdx = cursor.getColumnIndex(Telephony.Mms.THREAD_ID)
                val dateIdx = cursor.getColumnIndex(Telephony.Mms.DATE)
                val readIdx = cursor.getColumnIndex(Telephony.Mms.READ)

                if (threadIdx >= 0) {
                    while (cursor.moveToNext()) {
                        val threadId = cursor.getLong(threadIdx)
                        val date = if (dateIdx >= 0) cursor.getLong(dateIdx) * 1000 else 0L
                        val isRead = if (readIdx >= 0) cursor.getInt(readIdx) == 1 else true

                        val info = threads.getOrPut(threadId) {
                            ThreadInfo(
                                snippet = "(MMS)",
                                timestamp = date,
                            )
                        }
                        if (date > info.timestamp) {
                            info.timestamp = date
                            info.snippet = "(MMS)"
                        }
                        if (!isRead) info.unreadCount++
                    }
                }
            }
        } catch (_: Exception) {
        }

        return threads.entries.sortedByDescending { it.value.timestamp }.map { (threadId, info) ->
            val recipient = if (info.address.isNotBlank()) {
                contactLookup.lookupCached(info.address) ?: Recipient(address = info.address)
            } else {
                Recipient(address = "Unknown")
            }

            Conversation(
                threadId = threadId,
                recipients = listOf(recipient),
                snippet = info.snippet,
                timestamp = info.timestamp,
                unreadCount = info.unreadCount,
            )
        }
    }
}
