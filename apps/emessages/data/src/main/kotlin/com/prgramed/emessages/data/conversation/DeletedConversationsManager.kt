package com.prgramed.emessages.data.conversation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeletedConversationsManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("deleted_conversations", Context.MODE_PRIVATE)

    fun markDeleted(threadId: Long) {
        prefs.edit().putLong("thread_$threadId", System.currentTimeMillis()).apply()
    }

    fun restore(threadId: Long) {
        prefs.edit().remove("thread_$threadId").apply()
    }

    fun isDeleted(threadId: Long): Boolean {
        return prefs.contains("thread_$threadId")
    }

    fun getDeletedEntries(): Map<Long, Long> {
        return prefs.all.entries
            .filter { it.key.startsWith("thread_") }
            .mapNotNull { entry ->
                val threadId = entry.key.removePrefix("thread_").toLongOrNull() ?: return@mapNotNull null
                val deletedAt = (entry.value as? Long) ?: return@mapNotNull null
                threadId to deletedAt
            }
            .toMap()
    }

    fun getExpiredThreadIds(): List<Long> {
        val thirtyDaysAgo = System.currentTimeMillis() - RETENTION_MS
        return prefs.all.entries
            .filter { it.key.startsWith("thread_") }
            .mapNotNull { entry ->
                val threadId = entry.key.removePrefix("thread_").toLongOrNull() ?: return@mapNotNull null
                val deletedAt = (entry.value as? Long) ?: return@mapNotNull null
                if (deletedAt < thirtyDaysAgo) threadId else null
            }
    }

    fun remove(threadId: Long) {
        prefs.edit().remove("thread_$threadId").apply()
    }

    companion object {
        const val RETENTION_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
    }
}
