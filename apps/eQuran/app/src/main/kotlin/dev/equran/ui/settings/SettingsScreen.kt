package dev.equran.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val ARABIC_SCRIPTS = listOf(
    "ar.quran-uthmani" to "Uthmani",
    "ar.quran-simple-enhanced" to "Simple Enhanced",
    "ar.quran-simple" to "Simple",
)

private val TRANSLATIONS = listOf(
    "en.sahih" to "Sahih International",
    "en.pickthall" to "Pickthall",
    "en.yusufali" to "Yusuf Ali",
    "en.asad" to "Muhammad Asad",
)

private val TAFSIRS = listOf(
    "en.ibn-kathir" to "Ibn Kathir (English)",
    "en.maarif" to "Ma'ariful Quran (English)",
    "ar.muyassar" to "Al-Muyassar (Arabic)",
    "ar.jalalayn" to "Al-Jalalayn (Arabic)",
    "ar.qurtubi" to "Al-Qurtubi (Arabic)",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // Arabic Script
            SectionHeader("Arabic Script")
            ARABIC_SCRIPTS.forEach { (id, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.arabicScript == id,
                        onClick = { viewModel.setArabicScript(id) },
                    )
                    Text(text = label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Translations
            SectionHeader("English Translations")
            TRANSLATIONS.forEach { (id, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = id in settings.enabledTranslations,
                        onCheckedChange = { viewModel.toggleTranslation(id) },
                    )
                    Text(text = label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Font Size
            SectionHeader("Arabic Font Size")
            Text(
                text = "${settings.fontSize.toInt()}sp",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Slider(
                value = settings.fontSize,
                onValueChange = { viewModel.setFontSize(it) },
                valueRange = 20f..48f,
                steps = 27, // 1sp increments from 20 to 48
                modifier = Modifier.fillMaxWidth(),
            )
            // Preview
            Text(
                text = "\u0628\u0650\u0633\u0652\u0645\u0650 \u0627\u0644\u0644\u0651\u064E\u0647\u0650",
                fontSize = settings.fontSize.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Tafsir
            SectionHeader("Tafsir (Commentary)")
            TAFSIRS.forEach { (id, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = settings.selectedTafsir == id,
                        onClick = { viewModel.setSelectedTafsir(id) },
                    )
                    Text(text = label, modifier = Modifier.padding(start = 8.dp))
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Smart Search
            SectionHeader("Smart Search (AI)")
            Text(
                text = "Enter your QuranIndex server URL to enable AI-powered semantic search",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            OutlinedTextField(
                value = settings.quranIndexServerUrl,
                onValueChange = { viewModel.setQuranIndexServerUrl(it) },
                label = { Text("Server URL") },
                placeholder = { Text("https://your-server.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}
