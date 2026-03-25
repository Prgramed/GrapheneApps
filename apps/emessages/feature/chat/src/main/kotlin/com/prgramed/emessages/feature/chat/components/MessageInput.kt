package com.prgramed.emessages.feature.chat.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.prgramed.emessages.domain.model.SimInfo
import com.prgramed.emessages.feature.chat.SegmentInfo

@Composable
fun MessageInput(
    messageText: String,
    onMessageTextChanged: (String) -> Unit,
    onSendClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeSim: SimInfo? = null,
    availableSims: List<SimInfo> = emptyList(),
    onSimSelected: (SimInfo) -> Unit = {},
    onAttachmentClick: () -> Unit = {},
    attachmentUri: Uri? = null,
    onClearAttachment: () -> Unit = {},
    segmentInfo: SegmentInfo? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        // Attachment preview
        if (attachmentUri != null) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                AsyncImage(
                    model = attachmentUri,
                    contentDescription = "Attachment",
                    modifier = Modifier
                        .heightIn(max = 120.dp)
                        .fillMaxWidth(0.5f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
                IconButton(
                    onClick = onClearAttachment,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(28.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            CircleShape,
                        ),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Attachment button
            IconButton(onClick = onAttachmentClick) {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Text field
            OutlinedTextField(
                value = messageText,
                onValueChange = onMessageTextChanged,
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                ),
                maxLines = 4,
            )

            // SIM selector (only for dual-SIM)
            if (activeSim != null && availableSims.size >= 2) {
                AssistChip(
                    onClick = {
                        val currentIdx = availableSims.indexOf(activeSim)
                        val nextSim = availableSims[(currentIdx + 1) % availableSims.size]
                        onSimSelected(nextSim)
                    },
                    label = {
                        Text(
                            text = activeSim.displayName,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    modifier = Modifier.padding(start = 4.dp),
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (activeSim.slotIndex == 0) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        },
                    ),
                )
            }

            // Send button
            AnimatedVisibility(
                visible = messageText.isNotBlank() || attachmentUri != null,
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut(),
            ) {
                FilledIconButton(
                    onClick = onSendClick,
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .size(40.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Send",
                    )
                }
            }
        }

        // Character counter
        if (segmentInfo != null) {
            val counterText = if (segmentInfo.segments > 1) {
                "${segmentInfo.segments} segments · ${segmentInfo.remaining}"
            } else {
                "${segmentInfo.remaining} / ${segmentInfo.limit}"
            }
            Text(
                text = counterText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 64.dp, bottom = 4.dp),
            )
        }
    }
}
