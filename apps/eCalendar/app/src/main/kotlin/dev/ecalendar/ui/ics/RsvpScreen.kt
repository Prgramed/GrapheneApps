package dev.ecalendar.ui.ics

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.ecalendar.ical.PartStat
import dev.ecalendar.ical.RsvpGenerator
import timber.log.Timber
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy 'at' HH:mm", Locale.getDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RsvpScreen(
    originalIcs: String,
    organizer: String?,
    title: String,
    startMillis: Long,
    myEmail: String,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val dateText = Instant.ofEpochMilli(startMillis).atZone(zone).format(DATE_FORMAT)

    fun sendRsvp(status: PartStat) {
        try {
            // Generate reply ICS
            val replyIcs = RsvpGenerator.generateRsvpReply(originalIcs, myEmail, status)

            // Write to cache file
            val rsvpDir = File(context.cacheDir, "rsvp").apply { mkdirs() }
            val replyFile = File(rsvpDir, "reply-${System.currentTimeMillis()}.ics")
            replyFile.writeText(replyIcs)

            // Get FileProvider URI
            val uri = FileProvider.getUriForFile(context, "dev.ecalendar.fileprovider", replyFile)

            // Build reply text
            val statusText = when (status) {
                PartStat.ACCEPTED -> "accepted"
                PartStat.TENTATIVE -> "tentatively accepted"
                PartStat.DECLINED -> "declined"
            }
            val body = "I have $statusText the invitation to \"$title\" on $dateText."

            // Launch Thunderbird via ACTION_SEND
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                if (!organizer.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(organizer))
                }
                putExtra(Intent.EXTRA_SUBJECT, "Re: $title")
                putExtra(Intent.EXTRA_TEXT, body)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Send reply via"))
            Timber.d("RSVP: launched send intent for $status reply")
        } catch (e: Exception) {
            Timber.w(e, "RSVP: failed to send reply")
        }
        onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("RSVP") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = dateText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!organizer.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Your reply to $organizer",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(Modifier.height(32.dp))
            HorizontalDivider()
            Spacer(Modifier.height(32.dp))

            Text(
                text = "How would you like to respond?",
                style = MaterialTheme.typography.bodyLarge,
            )

            Spacer(Modifier.height(24.dp))

            // Accept / Tentative / Decline buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { sendRsvp(PartStat.ACCEPTED) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) {
                    Text("Accept")
                }
                OutlinedButton(
                    onClick = { sendRsvp(PartStat.TENTATIVE) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Tentative")
                }
                OutlinedButton(
                    onClick = { sendRsvp(PartStat.DECLINED) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Decline")
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Your reply will be sent via your email app",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}
