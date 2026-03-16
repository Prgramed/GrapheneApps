package com.prgramed.eprayer.feature.settings.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.prgramed.eprayer.domain.model.AdhanSound

private val adhanDisplayNames = mapOf(
    AdhanSound.MOHAMMED_REFAAT to "Sheikh Mohammed Refaat",
    AdhanSound.ABDEL_BASSET to "Sheikh Abdel Basset",
    AdhanSound.AL_HUSARY to "Sheikh Al-Husary",
    AdhanSound.DEVICE_DEFAULT to "Device Default Sound",
    AdhanSound.SILENT to "Silent",
)

@Composable
fun AdhanSoundSection(
    selectedSound: AdhanSound,
    onSoundSelected: (AdhanSound) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(16.dp)) {
        Text(
            text = "Adhan Sound",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        AdhanSound.entries.forEach { sound ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selectedSound == sound,
                    onClick = { onSoundSelected(sound) },
                )
                Text(
                    text = adhanDisplayNames[sound] ?: sound.name,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
