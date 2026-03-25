package com.prgramed.emessages.navigation

object MessagesDestinations {
    const val CONVERSATIONS = "conversations"
    const val CHAT = "chat/{threadId}"
    const val NEW_MESSAGE = "new_message"
    const val SETTINGS = "settings"
    const val BLOCKED_NUMBERS = "blocked_numbers"
    const val RECENTLY_DELETED = "recently_deleted"

    fun chat(threadId: Long) = "chat/$threadId"
}
