package com.prgramed.emessages.feature.chat.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prgramed.emessages.domain.model.MessageStatus

@Composable
fun MessageStatusIndicator(
    status: MessageStatus,
    modifier: Modifier = Modifier,
) {
    val iconSize = Modifier.size(12.dp)

    when (status) {
        MessageStatus.PENDING -> {
            Icon(
                Icons.Default.AccessTime,
                contentDescription = "Pending",
                modifier = modifier.then(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        MessageStatus.SENT -> {
            Icon(
                Icons.Default.Check,
                contentDescription = "Sent",
                modifier = modifier.then(iconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            )
        }
        MessageStatus.DELIVERED -> {
            Icon(
                Icons.Default.DoneAll,
                contentDescription = "Delivered",
                modifier = modifier.then(iconSize),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        MessageStatus.FAILED -> {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = "Failed",
                modifier = modifier.then(iconSize),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        MessageStatus.NONE -> {
            // No indicator
        }
    }
}
