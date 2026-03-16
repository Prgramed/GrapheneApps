package com.prgramed.eprayer.feature.settings.components

import android.media.MediaPlayer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.prgramed.eprayer.domain.model.AdhanSound

private val adhanDisplayNames = mapOf(
    AdhanSound.MOHAMMED_REFAAT to "Sheikh Mohammed Refaat",
    AdhanSound.ABDEL_BASSET to "Sheikh Abdel Basset",
    AdhanSound.AL_HUSARY to "Sheikh Al-Husary",
    AdhanSound.DEVICE_DEFAULT to "Device Default Sound",
    AdhanSound.SILENT to "Silent",
)

private fun adhanRawResName(sound: AdhanSound): String? = when (sound) {
    AdhanSound.MOHAMMED_REFAAT -> "adhan_refaat"
    AdhanSound.ABDEL_BASSET -> "adhan_abdel_basset"
    AdhanSound.AL_HUSARY -> "adhan_husary"
    AdhanSound.DEVICE_DEFAULT -> null
    AdhanSound.SILENT -> null
}

@Composable
fun AdhanSoundSection(
    selectedSound: AdhanSound,
    onSoundSelected: (AdhanSound) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var playingSound by remember { mutableStateOf<AdhanSound?>(null) }
    var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            mediaPlayer?.release()
        }
    }

    fun playPreview(sound: AdhanSound) {
        mediaPlayer?.release()
        val resName = adhanRawResName(sound) ?: return
        val resId = context.resources.getIdentifier(resName, "raw", context.packageName)
        if (resId == 0) return
        val player = MediaPlayer.create(context, resId)
        player.setOnCompletionListener {
            playingSound = null
            it.release()
        }
        player.start()
        mediaPlayer = player
        playingSound = sound
    }

    fun stopPreview() {
        mediaPlayer?.release()
        mediaPlayer = null
        playingSound = null
    }

    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Adhan Sound",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        AdhanSound.entries.forEach { sound ->
            val hasPreview = adhanRawResName(sound) != null
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedSound == sound,
                    onClick = {
                        onSoundSelected(sound)
                        if (hasPreview) playPreview(sound) else stopPreview()
                    },
                )
                Text(
                    text = adhanDisplayNames[sound] ?: sound.name,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 8.dp),
                )
                if (hasPreview) {
                    IconButton(
                        onClick = {
                            if (playingSound == sound) stopPreview() else playPreview(sound)
                        },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = if (playingSound == sound) Icons.Default.Stop
                            else Icons.Default.PlayArrow,
                            contentDescription = "Preview",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(36.dp))
                }
            }
        }
    }
}
