package com.prgramed.eprayer.feature.settings.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Peach = Color(0xFFE8B98A)
private val TextMuted = Color(0xFF8899AA)
private val NavyCard = Color(0xFF162135)

@Composable
fun NotificationSettingsSection(
    notificationsEnabled: Boolean,
    hasNotificationPermission: Boolean,
    onNotificationsEnabledChanged: (Boolean) -> Unit,
    onNotificationPermissionResult: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        onNotificationPermissionResult(granted)
        if (granted) onNotificationsEnabledChanged(true)
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Notifications",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Prayer Notifications",
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = notificationsEnabled,
                onCheckedChange = { enabled ->
                    if (enabled && !hasNotificationPermission && Build.VERSION.SDK_INT >= 33) {
                        notificationPermissionLauncher.launch(
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    } else {
                        onNotificationsEnabledChanged(enabled)
                    }
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Peach,
                    checkedTrackColor = Peach.copy(alpha = 0.3f),
                    uncheckedThumbColor = TextMuted,
                    uncheckedTrackColor = NavyCard,
                ),
            )
        }
    }
}
