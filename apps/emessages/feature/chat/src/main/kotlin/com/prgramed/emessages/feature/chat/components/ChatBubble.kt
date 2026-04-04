package com.prgramed.emessages.feature.chat.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ContactPage
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.aspectRatio
import coil3.compose.AsyncImage
import com.prgramed.emessages.domain.model.Attachment
import com.prgramed.emessages.domain.model.LinkPreview
import com.prgramed.emessages.domain.model.Message
import com.prgramed.emessages.domain.model.MessageStatus
import com.prgramed.emessages.domain.model.MessageType
import java.util.Date
import java.util.regex.Pattern

private val URL_PATTERN = Pattern.compile(
    "(https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+)",
    Pattern.CASE_INSENSITIVE,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: Message,
    isSent: Boolean,
    isLastInGroup: Boolean,
    modifier: Modifier = Modifier,
    simLabel: String? = null,
    onLongPress: (Message) -> Unit = {},
    onAttachmentClick: (Attachment) -> Unit = {},
    onAttachmentLongPress: (Attachment) -> Unit = {},
    linkPreview: LinkPreview? = null,
    onRetryMmsDownload: ((Long, String) -> Unit)? = null,
) {
    val bubbleShape = if (isSent) {
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = 18.dp,
            bottomEnd = if (isLastInGroup) 4.dp else 18.dp,
        )
    } else {
        RoundedCornerShape(
            topStart = 18.dp,
            topEnd = 18.dp,
            bottomStart = if (isLastInGroup) 4.dp else 18.dp,
            bottomEnd = 18.dp,
        )
    }

    val backgroundColor = if (isSent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val linkColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
    } else {
        MaterialTheme.colorScheme.primary
    }

    val alignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = if (isSent) 64.dp else 8.dp,
                end = if (isSent) 8.dp else 64.dp,
                top = if (isLastInGroup) 4.dp else 1.dp,
                bottom = 1.dp,
            ),
        contentAlignment = alignment,
    ) {
        Column(
            horizontalAlignment = if (isSent) Alignment.End else Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(bubbleShape)
                    .background(backgroundColor)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPress(message) },
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Column {
                    // Attachments
                    message.attachments.forEach { attachment ->
                        when {
                            attachment.mimeType.startsWith("image/") -> {
                                val context = androidx.compose.ui.platform.LocalContext.current
                                val imageModel = remember(message.id, attachment.uri) {
                                    com.prgramed.emessages.data.message.ThumbnailCache
                                        .getThumbnail(context, message.id)
                                        ?: attachment.uri
                                }
                                AsyncImage(
                                    model = imageModel,
                                    contentDescription = "Image",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .combinedClickable(
                                            onClick = { onAttachmentClick(attachment) },
                                            onLongClick = { onAttachmentLongPress(attachment) },
                                        )
                                        .padding(bottom = if (message.body.isNotBlank()) 4.dp else 0.dp),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            attachment.mimeType.startsWith("video/") -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(120.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.3f))
                                        .clickable { onAttachmentClick(attachment) }
                                        .padding(bottom = if (message.body.isNotBlank()) 4.dp else 0.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.PlayCircle,
                                        contentDescription = "Play video",
                                        modifier = Modifier.size(48.dp),
                                        tint = if (isSent) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            attachment.mimeType.startsWith("audio/") -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onAttachmentClick(attachment) }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.AudioFile,
                                        contentDescription = "Audio",
                                        modifier = Modifier.size(32.dp),
                                        tint = if (isSent) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.padding(start = 8.dp))
                                    Text(
                                        text = attachment.fileName ?: "Audio",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = textColor,
                                    )
                                }
                            }
                            // Generic file attachment (vCard, PDF, etc.)
                            else -> {
                                val icon = when {
                                    attachment.mimeType.contains("vcard", ignoreCase = true) -> Icons.Default.ContactPage
                                    else -> Icons.Default.AttachFile
                                }
                                val label = when {
                                    attachment.mimeType.contains("vcard", ignoreCase = true) -> "Contact card"
                                    attachment.mimeType.startsWith("application/pdf") -> "PDF"
                                    else -> attachment.fileName ?: "File"
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onAttachmentClick(attachment) }
                                        .padding(vertical = 8.dp),
                                ) {
                                    Icon(
                                        icon,
                                        contentDescription = label,
                                        modifier = Modifier.size(32.dp),
                                        tint = if (isSent) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.padding(start = 8.dp))
                                    Column {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = textColor,
                                        )
                                        Text(
                                            text = "Tap to open",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = textColor.copy(alpha = 0.6f),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Empty MMS fallback — tap to retry if content_location available
                    if (message.body.isBlank() && message.isMms && message.attachments.isEmpty()) {
                        val hasRetry = !message.contentLocation.isNullOrBlank()
                        Text(
                            text = if (hasRetry) "Tap to download MMS" else "MMS not available",
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = if (hasRetry) MaterialTheme.colorScheme.primary
                            else if (isSent) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = if (hasRetry) Modifier.clickable {
                                onRetryMmsDownload?.invoke(message.id, message.contentLocation!!)
                            } else Modifier,
                        )
                    }

                    // Quoted reply rendering
                    val (quotedText, replyBody) = remember(message.body) {
                        if (message.body.startsWith("> ")) {
                            val parts = message.body.split("\n\n", limit = 2)
                            val quoted = parts[0].removePrefix("> ").trim()
                            val body = parts.getOrNull(1)?.trim() ?: ""
                            quoted to body
                        } else null to message.body
                    }
                    if (quotedText != null) {
                        Row(modifier = Modifier.padding(bottom = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(16.dp)
                                    .background(
                                        textColor.copy(alpha = 0.4f),
                                        androidx.compose.foundation.shape.RoundedCornerShape(1.dp),
                                    ),
                            )
                            Text(
                                text = quotedText,
                                style = MaterialTheme.typography.bodySmall,
                                color = textColor.copy(alpha = 0.6f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }

                    // Message text with clickable links
                    val displayBody = replyBody ?: message.body
                    if (displayBody.isNotBlank()) {
                        val linkStyles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                            ),
                        )
                        val annotated = buildAnnotatedString {
                            append(displayBody)
                            val matcher = URL_PATTERN.matcher(displayBody)
                            while (matcher.find()) {
                                val start = matcher.start()
                                val end = matcher.end()
                                val url = matcher.group() ?: continue
                                addLink(
                                    LinkAnnotation.Url(url, linkStyles),
                                    start, end,
                                )
                            }
                        }

                        Text(
                            text = annotated,
                            style = MaterialTheme.typography.bodyLarge.copy(color = textColor),
                        )
                    }

                    // Link preview card
                    if (linkPreview != null) {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        Spacer(Modifier.height(4.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    else MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                                )
                                .clickable {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.content.Intent.ACTION_VIEW,
                                            android.net.Uri.parse(linkPreview.url),
                                        ),
                                    )
                                },
                        ) {
                            if (linkPreview.imageUrl != null) {
                                AsyncImage(
                                    model = linkPreview.imageUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1.91f)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                            Column(modifier = Modifier.padding(8.dp)) {
                                val previewTitle = linkPreview.title
                                if (previewTitle != null) {
                                    Text(
                                        text = previewTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = textColor,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Text(
                                    text = linkPreview.domain,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSent) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // Timestamp and status
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                if (simLabel != null) {
                    Text(
                        text = simLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                    Text(
                        text = " \u00b7 ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    )
                }
                Text(
                    text = formatMessageTime(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
                if (isSent) {
                    MessageStatusIndicator(status = message.status)
                    if (message.status == MessageStatus.DELIVERED) {
                        Text(
                            text = "Delivered",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
    }
}

private fun formatMessageTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    return DateFormat.format("h:mm a", Date(timestamp)).toString()
}
