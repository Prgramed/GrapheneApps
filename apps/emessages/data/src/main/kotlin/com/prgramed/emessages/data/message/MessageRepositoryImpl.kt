package com.prgramed.emessages.data.message

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentValues
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.prgramed.emessages.domain.model.Attachment
import com.prgramed.emessages.domain.model.Message
import com.prgramed.emessages.domain.model.MessageStatus
import com.prgramed.emessages.domain.model.MessageType
import com.prgramed.emessages.domain.repository.MessageRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageRepositoryImpl @Inject constructor(
    private val contentResolver: ContentResolver,
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>,
) : MessageRepository {

    override fun getMessages(threadId: Long): Flow<List<Message>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        // Single observer on mms-sms covers both SMS and MMS
        contentResolver.registerContentObserver(
            android.net.Uri.parse("content://mms-sms/"), true, observer,
        )
        trySend(Unit)
        awaitClose { contentResolver.unregisterContentObserver(observer) }
    }.debounce(500).map {
        val smsMessages = loadSmsMessages(threadId, limit = PAGE_SIZE)
        val mmsMessages = loadMmsMessages(threadId, limit = PAGE_SIZE)
        (smsMessages + mmsMessages)
            .sortedByDescending { it.timestamp }
            .take(PAGE_SIZE)
            .reversed()
    }.flowOn(Dispatchers.IO)

    override suspend fun loadOlderMessages(
        threadId: Long,
        beforeTimestamp: Long,
        limit: Int,
    ): List<Message> = withContext(Dispatchers.IO) {
        val sms = loadSmsMessages(threadId, limit = limit, beforeTimestamp = beforeTimestamp)
        val mms = loadMmsMessages(threadId, limit = limit, beforeTimestamp = beforeTimestamp)
        (sms + mms)
            .sortedByDescending { it.timestamp }
            .take(limit)
            .reversed()
    }

    override suspend fun sendSms(
        address: String,
        body: String,
        subscriptionId: Int?,
    ): Long = withContext(Dispatchers.IO) {
        try {
            val smsManager = if (subscriptionId != null) {
                context.getSystemService(SmsManager::class.java)
                    .createForSubscriptionId(subscriptionId)
            } else {
                context.getSystemService(SmsManager::class.java)
            }

            // Check if delivery reports are enabled
            val deliveryReports = try {
                val prefs = dataStore.data.first()
                prefs[KEY_DELIVERY_REPORTS] ?: false
            } catch (_: Exception) {
                false
            }

            // Write to provider first with PENDING status to get the message ID
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.STATUS, 32) // PENDING
                if (subscriptionId != null) {
                    put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
                }
            }
            val uri = contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            val messageId = uri?.lastPathSegment?.toLongOrNull() ?: 0L

            // Create PendingIntents for sent and delivery reports
            val sentIntent = createSentPendingIntent(messageId)
            val deliveryIntent = if (deliveryReports) {
                createDeliveryPendingIntent(messageId)
            } else null

            try {
                val parts = smsManager.divideMessage(body)
                if (parts.size > 1) {
                    val sentIntents = ArrayList(parts.map { sentIntent })
                    val deliveryIntents = if (deliveryIntent != null) {
                        ArrayList(parts.map { deliveryIntent })
                    } else null
                    smsManager.sendMultipartTextMessage(
                        address, null, parts, sentIntents, deliveryIntents,
                    )
                } else {
                    smsManager.sendTextMessage(address, null, body, sentIntent, deliveryIntent)
                }
            } catch (_: Exception) {
                // Mark as failed if sending throws
                val failValues = ContentValues().apply {
                    put(Telephony.Sms.STATUS, 64) // Failed
                }
                val msgUri = Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, messageId.toString())
                contentResolver.update(msgUri, failValues, null, null)
            }

            messageId
        } catch (_: Exception) {
            0L
        }
    }

    override suspend fun sendMms(
        addresses: List<String>,
        body: String,
        attachmentUris: List<Uri>,
    ) = withContext(Dispatchers.IO) {
        try {
            val address = addresses.firstOrNull() ?: return@withContext

            // Read and compress attachment data to fit MMS size limits
            val attachmentData = attachmentUris.firstOrNull()?.let { uri ->
                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                if (mimeType.startsWith("image/")) {
                    compressImageForMms(uri)
                } else {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) Pair(mimeType, bytes) else null
                }
            }

            // Build MMS PDU
            val pdu = buildMmsPdu(address, body, attachmentData)

            // Write PDU to temp file
            val pduFile = File(context.cacheDir, "mms_send_${System.currentTimeMillis()}.dat")
            pduFile.writeBytes(pdu)

            // Get content URI via FileProvider
            val pduUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                pduFile,
            )

            // Send MMS with result callback
            val sentIntent = android.app.PendingIntent.getBroadcast(
                context,
                pduFile.name.hashCode(),
                android.content.Intent("dev.emusic.MMS_SENT").setPackage(context.packageName),
                android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            val smsManager = context.getSystemService(SmsManager::class.java)
            android.util.Log.d("eMessages", "sendMms: sending to $address, pdu=${pdu.size} bytes")
            smsManager.sendMultimediaMessage(context, pduUri, null, null, sentIntent)

            // Write sent MMS to content provider so it shows in conversation
            val threadId = Telephony.Threads.getOrCreateThreadId(context, setOf(address))
            writeSentMms(threadId, address, body, attachmentData)

            // Clean up temp file after a delay
            pduFile.deleteOnExit()
        } catch (e: Exception) {
            android.util.Log.e("eMessages", "sendMms failed", e)
        }
        Unit
    }

    private fun writeSentMms(threadId: Long, address: String, body: String, attachmentData: Pair<String, ByteArray>?) {
        try {
            // Insert MMS header
            val mmsValues = ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, threadId)
                put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
                put(Telephony.Mms.DATE_SENT, System.currentTimeMillis() / 1000)
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.SEEN, 1)
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_SENT)
                put(Telephony.Mms.MESSAGE_TYPE, 128) // MESSAGE_TYPE_SEND_REQ
                put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
            }
            val mmsUri = contentResolver.insert(Telephony.Mms.CONTENT_URI, mmsValues) ?: return
            val mmsId = android.content.ContentUris.parseId(mmsUri)

            // Insert address (sender = self)
            val addrValues = ContentValues().apply {
                put(Telephony.Mms.Addr.ADDRESS, address)
                put(Telephony.Mms.Addr.TYPE, 151) // TO
                put(Telephony.Mms.Addr.CHARSET, 106) // UTF-8
            }
            contentResolver.insert(
                Uri.parse("content://mms/$mmsId/addr"), addrValues,
            )

            // Insert text part if present
            if (body.isNotBlank()) {
                val textValues = ContentValues().apply {
                    put(Telephony.Mms.Part.MSG_ID, mmsId)
                    put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                    put(Telephony.Mms.Part.TEXT, body)
                    put(Telephony.Mms.Part.CHARSET, 106)
                }
                contentResolver.insert(
                    Uri.parse("content://mms/$mmsId/part"), textValues,
                )
            }

            // Insert media part
            if (attachmentData != null) {
                val (mimeType, bytes) = attachmentData
                val ext = when {
                    mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                    mimeType.contains("png") -> "png"
                    mimeType.contains("gif") -> "gif"
                    mimeType.contains("mp4") -> "mp4"
                    else -> "bin"
                }
                val fileName = "sent_${System.currentTimeMillis()}.$ext"
                val partValues = ContentValues().apply {
                    put(Telephony.Mms.Part.MSG_ID, mmsId)
                    put(Telephony.Mms.Part.CONTENT_TYPE, mimeType)
                    put(Telephony.Mms.Part.NAME, fileName)
                    put(Telephony.Mms.Part.CONTENT_DISPOSITION, "attachment")
                    put(Telephony.Mms.Part.CONTENT_ID, "<$fileName>")
                    put(Telephony.Mms.Part.CONTENT_LOCATION, fileName)
                }
                val partUri = contentResolver.insert(
                    Uri.parse("content://mms/$mmsId/part"), partValues,
                )
                if (partUri != null) {
                    contentResolver.openOutputStream(partUri)?.use { it.write(bytes) }
                    android.util.Log.d("eMessages", "writeSentMms: wrote ${bytes.size} bytes to $partUri, mime=$mimeType")
                }
            }
            android.util.Log.d("eMessages", "writeSentMms: wrote MMS to thread=$threadId")
        } catch (e: Exception) {
            android.util.Log.e("eMessages", "writeSentMms failed", e)
        }
    }

    override suspend fun deleteMessage(id: Long, isMms: Boolean) = withContext(Dispatchers.IO) {
        try {
            val uri = if (isMms) {
                Uri.withAppendedPath(Telephony.Mms.CONTENT_URI, id.toString())
            } else {
                Uri.withAppendedPath(Telephony.Sms.CONTENT_URI, id.toString())
            }
            contentResolver.delete(uri, null, null)
        } catch (_: Exception) {
        }
        Unit
    }

    override suspend fun getOrCreateThreadId(addresses: List<String>): Long =
        withContext(Dispatchers.IO) {
            try {
                val addressSet = addresses.toSet()
                Telephony.Threads.getOrCreateThreadId(context, addressSet)
            } catch (_: Exception) {
                0L
            }
        }

    private fun createSentPendingIntent(messageId: Long): PendingIntent {
        val intent = Intent().apply {
            component = ComponentName(
                context.packageName,
                "com.prgramed.emessages.receiver.SmsSentReceiver",
            )
            putExtra("message_id", messageId)
        }
        return PendingIntent.getBroadcast(
            context,
            messageId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun createDeliveryPendingIntent(messageId: Long): PendingIntent {
        val intent = Intent().apply {
            component = ComponentName(
                context.packageName,
                "com.prgramed.emessages.receiver.SmsDeliveredReceiver",
            )
            putExtra("message_id", messageId)
        }
        return PendingIntent.getBroadcast(
            context,
            (messageId + 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun compressImageForMms(uri: Uri): Pair<String, ByteArray>? {
        val maxBytes = 550_000 // ~550KB leaving room for headers/SMIL within 600KB carrier limit
        val input = context.contentResolver.openInputStream(uri) ?: return null
        val original = android.graphics.BitmapFactory.decodeStream(input)
        input.close()
        if (original == null) return null

        // Scale down to max 1920px on longest side (high quality but reasonable)
        val maxDim = 1920
        val longestSide = maxOf(original.width, original.height)
        val scale = if (longestSide > maxDim) maxDim.toFloat() / longestSide else 1f
        val scaled = if (scale < 1f) {
            android.graphics.Bitmap.createScaledBitmap(
                original,
                (original.width * scale).toInt(),
                (original.height * scale).toInt(),
                true,
            )
        } else original

        // Try high quality first, step down only if needed
        for (quality in listOf(92, 85, 75, 60, 45)) {
            val baos = java.io.ByteArrayOutputStream()
            scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, quality, baos)
            val bytes = baos.toByteArray()
            android.util.Log.d("eMessages", "compressImageForMms: ${scaled.width}x${scaled.height} quality=$quality size=${bytes.size}")
            if (bytes.size <= maxBytes) {
                if (scaled !== original) scaled.recycle()
                original.recycle()
                return Pair("image/jpeg", bytes)
            }
        }

        // Still too big — scale down further and try again
        val smallScale = 0.5f
        val smaller = android.graphics.Bitmap.createScaledBitmap(
            scaled,
            (scaled.width * smallScale).toInt(),
            (scaled.height * smallScale).toInt(),
            true,
        )
        if (scaled !== original) scaled.recycle()
        original.recycle()

        val baos = java.io.ByteArrayOutputStream()
        smaller.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, baos)
        smaller.recycle()
        val bytes = baos.toByteArray()
        android.util.Log.d("eMessages", "compressImageForMms: fallback size=${bytes.size}")
        return Pair("image/jpeg", bytes)
    }

    private fun buildMmsPdu(
        to: String,
        body: String?,
        attachment: Pair<String, ByteArray>?,
    ): ByteArray {
        val baos = ByteArrayOutputStream()

        // X-Mms-Message-Type: m-send-req
        baos.write(0x8C)
        baos.write(0x80)

        // X-Mms-Transaction-Id
        val transId = "T${System.currentTimeMillis()}"
        baos.write(0x98)
        baos.write(transId.toByteArray())
        baos.write(0x00)

        // X-Mms-Version: 1.3
        baos.write(0x8D)
        baos.write(0x93)

        // From: Insert-address-token
        baos.write(0x89)
        baos.write(0x01)
        baos.write(0x81)

        // To
        baos.write(0x97)
        val toBytes = "$to/TYPE=PLMN".toByteArray()
        baos.write(toBytes)
        baos.write(0x00)

        // Content-Type: application/vnd.wap.multipart.related
        val ctString = "application/vnd.wap.multipart.related"
        baos.write(0x84)
        // Content-Type as text string (not well-known)
        baos.write(ctString.toByteArray())
        baos.write(0x00)

        // Build SMIL
        val smilParts = mutableListOf<String>()
        if (!body.isNullOrBlank()) smilParts.add("<text src=\"text.txt\" region=\"Text\"/>")
        if (attachment != null) smilParts.add("<img src=\"media\" region=\"Image\"/>")
        val smil = """<smil><head><layout><root-layout/><region id="Image" fit="meet" top="0" left="0" height="100%" width="100%"/><region id="Text" top="70%" left="0" height="30%" width="100%"/></layout></head><body><par>${smilParts.joinToString("")}</par></body></smil>"""
        val smilBytes = smil.toByteArray(Charsets.UTF_8)

        // Number of parts: SMIL + text (optional) + attachment (optional)
        val numParts = 1 + (if (!body.isNullOrBlank()) 1 else 0) + (if (attachment != null) 1 else 0)
        writeUintVar(baos, numParts)

        // SMIL part
        val smilCt = "application/smil".toByteArray()
        writeUintVar(baos, smilCt.size + 1)
        writeUintVar(baos, smilBytes.size)
        baos.write(smilCt)
        baos.write(0x00)
        baos.write(smilBytes)

        // Text part
        if (!body.isNullOrBlank()) {
            val textBytes = body.toByteArray(Charsets.UTF_8)
            val contentType = "text/plain; charset=utf-8".toByteArray()
            val headerLength = contentType.size + 1
            writeUintVar(baos, headerLength)
            writeUintVar(baos, textBytes.size)
            baos.write(contentType)
            baos.write(0x00)
            baos.write(textBytes)
        }

        // Attachment part
        if (attachment != null) {
            val (mimeType, data) = attachment
            val contentTypeBytes = mimeType.toByteArray()
            val headerLength = contentTypeBytes.size + 1
            writeUintVar(baos, headerLength)
            writeUintVar(baos, data.size)
            baos.write(contentTypeBytes)
            baos.write(0x00)
            baos.write(data)
        }

        return baos.toByteArray()
    }

    private fun writeUintVar(stream: ByteArrayOutputStream, value: Int) {
        if (value < 0x80) {
            stream.write(value)
        } else {
            val bytes = mutableListOf<Int>()
            var v = value
            bytes.add(v and 0x7F)
            v = v shr 7
            while (v > 0) {
                bytes.add((v and 0x7F) or 0x80)
                v = v shr 7
            }
            for (b in bytes.reversed()) {
                stream.write(b)
            }
        }
    }

    private fun loadSmsMessages(
        threadId: Long,
        limit: Int? = null,
        beforeTimestamp: Long? = null,
    ): List<Message> = try {
        val messages = mutableListOf<Message>()
        val selection = buildString {
            append("${Telephony.Sms.THREAD_ID} = ?")
            if (beforeTimestamp != null) append(" AND ${Telephony.Sms.DATE} < ?")
        }
        val selectionArgs = buildList {
            add(threadId.toString())
            if (beforeTimestamp != null) add(beforeTimestamp.toString())
        }.toTypedArray()
        val sortOrder = buildString {
            append("${Telephony.Sms.DATE} DESC")
            if (limit != null) append(" LIMIT $limit")
        }
        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.THREAD_ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.TYPE,
                Telephony.Sms.READ,
                Telephony.Sms.STATUS,
                Telephony.Sms.SUBSCRIPTION_ID,
            ),
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
            val threadIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
            val addressIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val typeIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.TYPE)
            val readIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.READ)
            val statusIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.STATUS)
            val subIdIdx = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)

            while (cursor.moveToNext()) {
                val type = cursor.getInt(typeIdx)
                val status = cursor.getInt(statusIdx)
                messages.add(
                    Message(
                        id = cursor.getLong(idIdx),
                        threadId = cursor.getLong(threadIdx),
                        address = cursor.getString(addressIdx) ?: "",
                        body = cursor.getString(bodyIdx) ?: "",
                        timestamp = cursor.getLong(dateIdx),
                        type = type.toMessageType(),
                        isRead = cursor.getInt(readIdx) == 1,
                        isMms = false,
                        status = mapSmsStatus(type, status),
                        subscriptionId = if (subIdIdx >= 0) cursor.getInt(subIdIdx) else -1,
                    ),
                )
            }
        }
        messages
    } catch (_: Exception) {
        emptyList()
    }

    private fun loadMmsMessages(
        threadId: Long,
        limit: Int? = null,
        beforeTimestamp: Long? = null,
    ): List<Message> = try {
        val messages = mutableListOf<Message>()
        val selection = buildString {
            append("${Telephony.Mms.THREAD_ID} = ?")
            // MMS dates are stored in seconds
            if (beforeTimestamp != null) append(" AND ${Telephony.Mms.DATE} < ?")
        }
        val selectionArgs = buildList {
            add(threadId.toString())
            if (beforeTimestamp != null) add((beforeTimestamp / 1000).toString())
        }.toTypedArray()
        val sortOrder = buildString {
            append("${Telephony.Mms.DATE} DESC")
            if (limit != null) append(" LIMIT $limit")
        }
        contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID,
                Telephony.Mms.THREAD_ID,
                Telephony.Mms.DATE,
                Telephony.Mms.READ,
                Telephony.Mms.MESSAGE_BOX,
                Telephony.Mms.SUBSCRIPTION_ID,
            ),
            selection,
            selectionArgs,
            sortOrder,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(Telephony.Mms._ID)
            val threadIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.DATE)
            val readIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.READ)
            val boxIdx = cursor.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
            val mmsSubIdIdx = cursor.getColumnIndex(Telephony.Mms.SUBSCRIPTION_ID)

            while (cursor.moveToNext()) {
                val mmsId = cursor.getLong(idIdx)
                val messageBox = cursor.getInt(boxIdx)
                // MMS date is in seconds, not milliseconds
                val date = cursor.getLong(dateIdx) * 1000

                val body = loadMmsBody(mmsId)
                val attachments = loadMmsAttachments(mmsId)
                val address = loadMmsAddress(mmsId)

                messages.add(
                    Message(
                        id = mmsId,
                        threadId = cursor.getLong(threadIdx),
                        address = address,
                        body = body,
                        timestamp = date,
                        type = messageBox.toMmsMessageType(),
                        isRead = cursor.getInt(readIdx) == 1,
                        isMms = true,
                        attachments = attachments,
                        status = MessageStatus.NONE,
                        subscriptionId = if (mmsSubIdIdx >= 0) cursor.getInt(mmsSubIdIdx) else -1,
                    ),
                )
            }
        }
        messages
    } catch (_: Exception) {
        emptyList()
    }

    private fun loadMmsBody(mmsId: Long): String = try {
        val sb = StringBuilder()
        val partUri = Uri.parse("content://mms/$mmsId/part")
        contentResolver.query(
            partUri,
            arrayOf("_id", "ct", "text"),
            "ct = ?",
            arrayOf("text/plain"),
            null,
        )?.use { cursor ->
            val textIdx = cursor.getColumnIndexOrThrow("text")
            while (cursor.moveToNext()) {
                val text = cursor.getString(textIdx)
                if (!text.isNullOrBlank()) sb.append(text)
            }
        }
        sb.toString()
    } catch (_: Exception) {
        ""
    }

    private fun loadMmsAttachments(mmsId: Long): List<Attachment> = try {
        val attachments = mutableListOf<Attachment>()
        val partUri = Uri.parse("content://mms/$mmsId/part")
        contentResolver.query(
            partUri,
            arrayOf("_id", "ct", "name", "cl"),
            null, null, null,
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("_id")
            val ctIdx = cursor.getColumnIndexOrThrow("ct")
            val nameIdx = cursor.getColumnIndexOrThrow("name")
            val clIdx = cursor.getColumnIndexOrThrow("cl")

            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(ctIdx) ?: continue
                // Include any attachment type except SMIL (presentation) and plain text (handled by loadMmsBody)
                if (mimeType != "application/smil" && mimeType != "text/plain") {
                    val partId = cursor.getLong(idIdx)
                    val name = cursor.getString(nameIdx) ?: cursor.getString(clIdx)
                    attachments.add(
                        Attachment(
                            uri = "content://mms/part/$partId",
                            mimeType = mimeType,
                            fileName = name,
                        ),
                    )
                }
            }
        }
        attachments
    } catch (_: Exception) {
        emptyList()
    }

    private fun loadMmsAddress(mmsId: Long): String = try {
        var fromAddress = ""
        var toAddress = ""
        val addrUri = Uri.parse("content://mms/$mmsId/addr")
        contentResolver.query(
            addrUri,
            arrayOf("address", "type"),
            null, null, null,
        )?.use { cursor ->
            val addrIdx = cursor.getColumnIndexOrThrow("address")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                val addr = cursor.getString(addrIdx) ?: continue
                if (addr == "insert-address-token" || addr.isBlank()) continue
                when (cursor.getInt(typeIdx)) {
                    137 -> fromAddress = addr // FROM
                    151 -> toAddress = addr   // TO
                }
            }
        }
        // For received MMS use FROM, for sent MMS use TO
        fromAddress.ifBlank { toAddress }
    } catch (_: Exception) {
        ""
    }

    companion object {
        private const val PAGE_SIZE = 50
        private val KEY_DELIVERY_REPORTS = booleanPreferencesKey("delivery_reports_enabled")
    }
}

private fun Int.toMessageType(): MessageType = when (this) {
    Telephony.Sms.MESSAGE_TYPE_SENT -> MessageType.SENT
    Telephony.Sms.MESSAGE_TYPE_DRAFT -> MessageType.DRAFT
    else -> MessageType.RECEIVED
}

private fun Int.toMmsMessageType(): MessageType = when (this) {
    Telephony.Mms.MESSAGE_BOX_SENT -> MessageType.SENT
    Telephony.Mms.MESSAGE_BOX_DRAFTS -> MessageType.DRAFT
    else -> MessageType.RECEIVED
}

private fun mapSmsStatus(type: Int, status: Int): MessageStatus = when {
    status == 32 -> MessageStatus.PENDING
    status == 64 -> MessageStatus.FAILED
    status == 0 && type == Telephony.Sms.MESSAGE_TYPE_SENT -> MessageStatus.SENT
    status == 1 -> MessageStatus.DELIVERED
    else -> MessageStatus.NONE
}
