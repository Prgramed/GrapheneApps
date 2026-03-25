package com.prgramed.emessages.data.backup

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.util.Log
import android.provider.Telephony
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val smsMessages: List<SmsBackup> = emptyList(),
    val mmsMessages: List<MmsBackup> = emptyList(),
)

@Serializable
data class SmsBackup(
    val address: String,
    val body: String,
    val timestamp: Long,
    val type: Int, // 1=received, 2=sent
    val read: Int,
    val subscriptionId: Int = -1,
)

@Serializable
data class MmsBackup(
    val address: String,
    val body: String,
    val timestamp: Long,
    val type: Int,
    val read: Int,
    val parts: List<MmsPartBackup> = emptyList(),
)

@Serializable
data class MmsPartBackup(
    val mimeType: String,
    val fileName: String? = null,
    val dataBase64: String? = null, // Legacy: binary content base64-encoded
    val mediaPath: String? = null, // Separate file on WebDAV: media/{mmsId}_{partId}.ext
)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val webDavClient: WebDavClient,
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true }

    suspend fun createBackup(webDavUrl: String, username: String, password: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = webDavUrl.trimEnd('/')

                // Create folders
                webDavClient.createFolder(baseUrl, username, password)
                webDavClient.createFolder("$baseUrl/media", username, password)

                val smsMessages = exportSms()
                val mmsMessages = exportMmsWithMedia(baseUrl, username, password)

                val backup = BackupData(
                    smsMessages = smsMessages,
                    mmsMessages = mmsMessages,
                )

                val jsonBytes = json.encodeToString(backup).toByteArray()
                val totalMessages = smsMessages.size + mmsMessages.size

                // Upload JSON
                webDavClient.upload(
                    "$baseUrl/messages.json",
                    username,
                    password,
                    jsonBytes,
                )

                Result.success(totalMessages)
            } catch (e: Exception) {
                Log.e("BackupManager", "Backup failed", e)
                Result.failure(e)
            }
        }

    suspend fun restore(webDavUrl: String, username: String, password: String): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = webDavUrl.trimEnd('/')
                val jsonBytes = webDavClient.download("$baseUrl/messages.json", username, password)
                val backup = json.decodeFromString<BackupData>(String(jsonBytes))

                var restored = 0

                // Restore SMS
                for (sms in backup.smsMessages) {
                    if (!smsExists(sms.address, sms.timestamp)) {
                        val values = android.content.ContentValues().apply {
                            put(Telephony.Sms.ADDRESS, sms.address)
                            put(Telephony.Sms.BODY, sms.body)
                            put(Telephony.Sms.DATE, sms.timestamp)
                            put(Telephony.Sms.TYPE, sms.type)
                            put(Telephony.Sms.READ, sms.read)
                            if (sms.subscriptionId >= 0) {
                                put(Telephony.Sms.SUBSCRIPTION_ID, sms.subscriptionId)
                            }
                        }
                        contentResolver.insert(Telephony.Sms.CONTENT_URI, values)
                        restored++
                    }
                }

                // Restore MMS
                for (mms in backup.mmsMessages) {
                    if (!mmsExists(mms.address, mms.timestamp)) {
                        val mmsValues = android.content.ContentValues().apply {
                            put(Telephony.Mms.DATE, mms.timestamp / 1000) // MMS stores seconds
                            put(Telephony.Mms.READ, mms.read)
                            put(Telephony.Mms.MESSAGE_BOX, mms.type)
                            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.mms-message")
                        }
                        val mmsUri = contentResolver.insert(Telephony.Mms.CONTENT_URI, mmsValues)
                        if (mmsUri != null) {
                            val mmsId = mmsUri.lastPathSegment

                            // Add address
                            val addrValues = android.content.ContentValues().apply {
                                put(Telephony.Mms.Addr.ADDRESS, mms.address)
                                put(Telephony.Mms.Addr.TYPE, if (mms.type == 1) 137 else 151) // FROM or TO
                                put(Telephony.Mms.Addr.CHARSET, 106)
                            }
                            contentResolver.insert(
                                Uri.parse("content://mms/$mmsId/addr"),
                                addrValues,
                            )

                            // Add text part
                            if (mms.body.isNotBlank()) {
                                val textPartValues = android.content.ContentValues().apply {
                                    put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                                    put(Telephony.Mms.Part.TEXT, mms.body)
                                }
                                contentResolver.insert(
                                    Uri.parse("content://mms/$mmsId/part"),
                                    textPartValues,
                                )
                            }

                            // Add media parts
                            for (part in mms.parts) {
                                val bytes = when {
                                    // New format: download from WebDAV media path
                                    part.mediaPath != null -> {
                                        try {
                                            webDavClient.download("$baseUrl/${part.mediaPath}", username, password)
                                        } catch (_: Exception) { null }
                                    }
                                    // Legacy format: base64 in JSON
                                    part.dataBase64 != null -> Base64.decode(part.dataBase64, Base64.DEFAULT)
                                    else -> null
                                }
                                if (bytes != null) {
                                    val partValues = android.content.ContentValues().apply {
                                        put(Telephony.Mms.Part.CONTENT_TYPE, part.mimeType)
                                        part.fileName?.let { put(Telephony.Mms.Part.NAME, it) }
                                    }
                                    val partUri = contentResolver.insert(
                                        Uri.parse("content://mms/$mmsId/part"),
                                        partValues,
                                    )
                                    if (partUri != null) {
                                        contentResolver.openOutputStream(partUri)?.use { out ->
                                            out.write(bytes)
                                        }
                                    }
                                }
                            }
                            restored++
                        }
                    }
                }

                Result.success(restored)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun exportSms(): List<SmsBackup> {
        val messages = mutableListOf<SmsBackup>()
        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
                Telephony.Sms.TYPE, Telephony.Sms.READ, Telephony.Sms.SUBSCRIPTION_ID,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                messages.add(
                    SmsBackup(
                        address = cursor.getString(0) ?: "",
                        body = cursor.getString(1) ?: "",
                        timestamp = cursor.getLong(2),
                        type = cursor.getInt(3),
                        read = cursor.getInt(4),
                        subscriptionId = cursor.getInt(5),
                    ),
                )
            }
        }
        return messages
    }

    private fun exportMmsWithMedia(
        baseUrl: String,
        username: String,
        password: String,
    ): List<MmsBackup> {
        val messages = mutableListOf<MmsBackup>()
        contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, Telephony.Mms.DATE, Telephony.Mms.READ, Telephony.Mms.MESSAGE_BOX),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val mmsId = cursor.getLong(0)
                val timestamp = cursor.getLong(1) * 1000 // MMS stores seconds
                val read = cursor.getInt(2)
                val type = cursor.getInt(3)
                val address = loadMmsAddress(mmsId)
                val body = loadMmsBody(mmsId)
                val parts = loadMmsPartsWithUpload(mmsId, baseUrl, username, password)

                messages.add(
                    MmsBackup(
                        address = address,
                        body = body,
                        timestamp = timestamp,
                        type = type,
                        read = read,
                        parts = parts,
                    ),
                )
            }
        }
        return messages
    }

    private fun loadMmsAddress(mmsId: Long): String {
        var address = ""
        contentResolver.query(
            Uri.parse("content://mms/$mmsId/addr"),
            arrayOf("address", "type"),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val addr = cursor.getString(0) ?: continue
                val addrType = cursor.getInt(1)
                if (addrType == 137 && !addr.contains("/")) { // FROM type
                    address = addr
                    break
                }
            }
        }
        return address
    }

    private fun loadMmsBody(mmsId: Long): String {
        var body = ""
        contentResolver.query(
            Uri.parse("content://mms/$mmsId/part"),
            arrayOf("_id", "ct", "text"),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val mimeType = cursor.getString(1) ?: continue
                if (mimeType == "text/plain") {
                    body = cursor.getString(2) ?: ""
                    break
                }
            }
        }
        return body
    }

    private fun loadMmsPartsWithUpload(
        mmsId: Long,
        baseUrl: String,
        username: String,
        password: String,
    ): List<MmsPartBackup> {
        val parts = mutableListOf<MmsPartBackup>()
        contentResolver.query(
            Uri.parse("content://mms/$mmsId/part"),
            arrayOf("_id", "ct", "name", "cl"),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val partId = cursor.getLong(0)
                val mimeType = cursor.getString(1) ?: continue
                val name = cursor.getString(2) ?: cursor.getString(3)

                if (mimeType.startsWith("image/") || mimeType.startsWith("video/") || mimeType.startsWith("audio/")) {
                    val ext = when {
                        mimeType.contains("jpeg") || mimeType.contains("jpg") -> "jpg"
                        mimeType.contains("png") -> "png"
                        mimeType.contains("gif") -> "gif"
                        mimeType.contains("mp4") -> "mp4"
                        mimeType.contains("3gp") -> "3gp"
                        mimeType.startsWith("audio/") -> "m4a"
                        else -> "bin"
                    }
                    val mediaFileName = "${mmsId}_${partId}.$ext"
                    val mediaPath = "media/$mediaFileName"

                    // Upload the media file
                    try {
                        val uri = Uri.parse("content://mms/part/$partId")
                        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            webDavClient.upload(
                                "$baseUrl/$mediaPath",
                                username,
                                password,
                                bytes,
                                mimeType,
                            )
                        }
                    } catch (e: Exception) {
                        Log.w("BackupManager", "Failed to upload media $mediaPath", e)
                    }

                    parts.add(
                        MmsPartBackup(
                            mimeType = mimeType,
                            fileName = name,
                            mediaPath = mediaPath,
                        ),
                    )
                }
            }
        }
        return parts
    }

    private fun smsExists(address: String, timestamp: Long): Boolean {
        return contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} = ?",
            arrayOf(address, timestamp.toString()),
            null,
        )?.use { it.count > 0 } ?: false
    }

    private fun mmsExists(address: String, timestamp: Long): Boolean {
        // Check by timestamp (in seconds for MMS)
        val tsSeconds = timestamp / 1000
        return contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID),
            "${Telephony.Mms.DATE} = ?",
            arrayOf(tsSeconds.toString()),
            null,
        )?.use { it.count > 0 } ?: false
    }
}
