package com.prgramed.emessages.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.FileProvider
import java.io.File

class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return

        val pduData = intent.getByteArrayExtra("data") ?: return
        val subscriptionId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.getDefaultSmsSubscriptionId(),
        )

        val contentLocation = extractContentLocation(pduData) ?: return

        // Create temp file for download output
        val downloadFile = File(context.cacheDir, "mms_${System.currentTimeMillis()}.pdu")
        if (!downloadFile.createNewFile()) return

        val downloadUri = FileProvider.getUriForFile(
            context, "${context.packageName}.provider", downloadFile,
        )

        // PendingIntent for download completion
        val completionIntent = Intent(context, MmsDownloadedReceiver::class.java).apply {
            putExtra("file_path", downloadFile.absolutePath)
            putExtra("sub_id", subscriptionId)
            putExtra("content_location", contentLocation)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            downloadFile.name.hashCode(),
            completionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Download via SmsManager
        val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        try {
            smsManager?.downloadMultimediaMessage(
                context, contentLocation, downloadUri, null, pendingIntent,
            )
        } catch (e: Exception) {
            downloadFile.delete()
        }
    }

    private fun extractContentLocation(pdu: ByteArray): String? {
        var i = 0
        while (i < pdu.size - 1) {
            if (pdu[i].toInt() and 0xFF == 0x83) {
                val start = i + 1
                var end = start
                while (end < pdu.size && pdu[end].toInt() != 0) end++
                if (end > start) {
                    return String(pdu, start, end - start, Charsets.US_ASCII)
                }
            }
            i++
        }
        return null
    }
}
