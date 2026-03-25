package com.prgramed.emessages.domain.model

object DraftStore {
    private val drafts = mutableMapOf<Long, String>()

    fun save(threadId: Long, text: String) {
        if (text.isNotBlank()) drafts[threadId] = text
        else drafts.remove(threadId)
    }

    fun get(threadId: Long): String? = drafts[threadId]

    fun remove(threadId: Long) { drafts.remove(threadId) }

    fun hasDraft(threadId: Long): Boolean = drafts.containsKey(threadId)
}
