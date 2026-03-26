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

    enum class RestoreType { ALL, SMS_ONLY, MMS_ONLY }

    suspend fun restore(
        webDavUrl: String,
        username: String,
        password: String,
        onProgress: ((phase: String, current: Int, total: Int) -> Unit)? = null,
        force: Boolean = false,
        type: RestoreType = RestoreType.ALL,
    ): Result<Int> =
        withContext(Dispatchers.IO) {
            try {
                val baseUrl = webDavUrl.trimEnd('/')
                onProgress?.invoke("Downloading backup...", 0, 0)
                val jsonBytes = webDavClient.download("$baseUrl/messages.json", username, password)
                onProgress?.invoke("Parsing backup...", 0, 0)
                val backup = json.decodeFromString<BackupData>(String(jsonBytes))

                var restored = 0
                val doSms = type == RestoreType.ALL || type == RestoreType.SMS_ONLY
                val doMms = type == RestoreType.ALL || type == RestoreType.MMS_ONLY
                val totalSms = if (doSms) backup.smsMessages.size else 0
                val totalMms = if (doMms) backup.mmsMessages.size else 0

                // Pre-load existing keys (skip if force — we insert everything)
                onProgress?.invoke("Loading existing messages...", 0, totalSms + totalMms)
                val existingSmsKeys = if (!force && doSms) loadExistingSmsKeys() else HashSet()
                val existingMmsTimestamps = if (!force && doMms) loadExistingMmsTimestamps() else HashSet()
                Log.d("BackupManager", "Restore: force=$force type=$type sms=$totalSms mms=$totalMms existing_sms=${existingSmsKeys.size} existing_mms=${existingMmsTimestamps.size}")

                // Force MMS: delete all existing MMS first so they get re-inserted with proper charset
                if (force && doMms) {
                    onProgress?.invoke("Deleting existing MMS for re-import...", 0, totalSms + totalMms)
                    try {
                        contentResolver.delete(Telephony.Mms.CONTENT_URI, null, null)
                    } catch (_: Exception) {}
                }

                // Restore SMS
                if (doSms) {
                var smsProcessed = 0
                for (sms in backup.smsMessages) {
                    val key = "${sms.address}|${sms.timestamp}"
                    if (force || key !in existingSmsKeys) {
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
                        existingSmsKeys.add(key)
                        restored++
                    }
                    smsProcessed++
                    if (smsProcessed % 100 == 0) {
                        onProgress?.invoke("SMS: $restored new / $smsProcessed of $totalSms", smsProcessed, totalSms + totalMms)
                    }
                }
                onProgress?.invoke("SMS done: $restored restored. Starting MMS...", totalSms, totalSms + totalMms)
                Log.d("BackupManager", "SMS restore done: $restored inserted")
                } // end doSms

                // Restore MMS
                if (doMms) {
                var mmsProcessed = 0
                for (mms in backup.mmsMessages) {
                    val tsSeconds = mms.timestamp / 1000
                    if (force || tsSeconds !in existingMmsTimestamps) {
                        // Get or create thread_id from address
                        val threadId = if (mms.address.isNotBlank()) {
                            try {
                                Telephony.Threads.getOrCreateThreadId(context, mms.address)
                            } catch (_: Exception) { 0L }
                        } else 0L

                        val mmsValues = android.content.ContentValues().apply {
                            put(Telephony.Mms.DATE, tsSeconds)
                            put(Telephony.Mms.READ, mms.read)
                            put(Telephony.Mms.MESSAGE_BOX, mms.type)
                            put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.mms-message")
                            if (threadId > 0) put(Telephony.Mms.THREAD_ID, threadId)
                        }
                        val mmsUri = contentResolver.insert(Telephony.Mms.CONTENT_URI, mmsValues)
                        Log.d("BackupManager", "MMS insert URI=$mmsUri for ts=${mms.timestamp} addr=${mms.address} parts=${mms.parts.size}")
                        if (mmsUri != null) {
                            val mmsId = mmsUri.lastPathSegment
                            Log.d("BackupManager", "MMS id=$mmsId, inserting addr + ${mms.parts.size} parts")

                            // Add address
                            val addrValues = android.content.ContentValues().apply {
                                put(Telephony.Mms.Addr.ADDRESS, mms.address)
                                put(Telephony.Mms.Addr.TYPE, if (mms.type == 1) 137 else 151)
                                put(Telephony.Mms.Addr.CHARSET, 106)
                            }
                            contentResolver.insert(
                                Uri.parse("content://mms/$mmsId/addr"),
                                addrValues,
                            )

                            // Add text part (strip U+FFFC object replacement characters from backup)
                            val cleanBody = mms.body.replace("\uFFFC", "").trim()
                            if (cleanBody.isNotBlank()) {
                                val textPartValues = android.content.ContentValues().apply {
                                    put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                                    put(Telephony.Mms.Part.CHARSET, 106) // UTF-8
                                    put(Telephony.Mms.Part.TEXT, cleanBody)
                                }
                                contentResolver.insert(
                                    Uri.parse("content://mms/$mmsId/part"),
                                    textPartValues,
                                )
                            }

                            // Add media parts
                            for (part in mms.parts) {
                                val bytes = when {
                                    part.mediaPath != null -> {
                                        try {
                                            webDavClient.download("$baseUrl/${part.mediaPath}", username, password)
                                        } catch (_: Exception) { null }
                                    }
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
                            existingMmsTimestamps.add(tsSeconds)
                            restored++
                        }
                    }
                    mmsProcessed++
                    if (mmsProcessed % 10 == 0) {
                        onProgress?.invoke("MMS: $mmsProcessed of $totalMms", totalSms + mmsProcessed, totalSms + totalMms)
                    }
                }
                } // end doMms
                onProgress?.invoke("Done! Restored $restored messages", totalSms + totalMms, totalSms + totalMms)
                Log.d("BackupManager", "Restore complete: $restored total messages restored")

                Result.success(restored)
            } catch (e: Exception) {
                Log.e("BackupManager", "Restore failed", e)
                Result.failure(e)
            }
        }

    suspend fun deleteOldSms(beforeMs: Long): Int = withContext(Dispatchers.IO) {
        try {
            val deleted = contentResolver.delete(
                Telephony.Sms.CONTENT_URI,
                "${Telephony.Sms.DATE} < ?",
                arrayOf(beforeMs.toString()),
            )
            Log.d("BackupManager", "Deleted $deleted old SMS before $beforeMs")
            deleted
        } catch (e: Exception) {
            Log.e("BackupManager", "Failed to delete old SMS", e)
            0
        }
    }

    /**
     * Loads all existing SMS address+timestamp keys into a HashSet for O(1) lookup.
     * One query instead of per-message queries.
     */
    private fun loadExistingSmsKeys(): HashSet<String> {
        val keys = HashSet<String>()
        contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.DATE),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val address = cursor.getString(0) ?: ""
                val timestamp = cursor.getLong(1)
                keys.add("$address|$timestamp")
            }
        }
        return keys
    }

    /**
     * Loads all existing MMS timestamps (in seconds) into a HashSet for O(1) lookup.
     */
    private fun loadExistingMmsTimestamps(): HashSet<Long> {
        val timestamps = HashSet<Long>()
        contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms.DATE),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                timestamps.add(cursor.getLong(0))
            }
        }
        return timestamps
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
                val rawBody = cursor.getString(1) ?: ""
                // Skip entries that are only OBJ replacement chars (MMS attachment placeholders in SMS table)
                if (rawBody.replace("\uFFFC", "").trim().isBlank() && rawBody.contains("\uFFFC")) continue
                messages.add(
                    SmsBackup(
                        address = cursor.getString(0) ?: "",
                        body = rawBody,
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
        var fromAddr = ""
        var toAddr = ""
        contentResolver.query(
            Uri.parse("content://mms/$mmsId/addr"),
            arrayOf("address", "type"),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val addr = cursor.getString(0) ?: continue
                if (addr.contains("/") || addr == "insert-address-token") continue
                val addrType = cursor.getInt(1)
                when (addrType) {
                    137 -> fromAddr = addr  // FROM (received MMS — sender)
                    151 -> toAddr = addr    // TO (sent MMS — recipient)
                }
            }
        }
        // Prefer FROM for received, TO for sent
        return fromAddr.ifBlank { toAddr }
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

}
