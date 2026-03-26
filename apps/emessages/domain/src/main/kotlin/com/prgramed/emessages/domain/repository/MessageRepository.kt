package com.prgramed.emessages.domain.repository

import android.net.Uri
import com.prgramed.emessages.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    fun getMessages(threadId: Long): Flow<List<Message>>
    suspend fun loadOlderMessages(threadId: Long, beforeTimestamp: Long, limit: Int = 50): List<Message>
    suspend fun sendSms(address: String, body: String, subscriptionId: Int? = null): Long
    suspend fun sendMms(addresses: List<String>, body: String, attachmentUris: List<Uri>)
    suspend fun deleteMessage(id: Long, isMms: Boolean)
    suspend fun getOrCreateThreadId(addresses: List<String>): Long
    suspend fun retryMmsDownload(mmsId: Long, contentLocation: String)
}
