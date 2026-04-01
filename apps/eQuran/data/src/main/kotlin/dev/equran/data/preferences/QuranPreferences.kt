package dev.equran.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "equran_prefs")

data class QuranSettings(
    val arabicScript: String = "ar.quran-uthmani",
    val enabledTranslations: List<String> = listOf("en.sahih"),
    val fontSize: Float = 26f,
    val selectedTafsir: String = "en.ibn-kathir",
    val quranIndexServerUrl: String = "",
)

@Singleton
class QuranPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dataStore = context.dataStore

    val settings: Flow<QuranSettings> = dataStore.data.map { prefs ->
        QuranSettings(
            arabicScript = prefs[SCRIPT_KEY] ?: "ar.quran-uthmani",
            enabledTranslations = (prefs[TRANSLATIONS_KEY] ?: "en.sahih").split(",").filter { it.isNotBlank() },
            fontSize = prefs[FONT_SIZE_KEY] ?: 26f,
            selectedTafsir = prefs[TAFSIR_KEY] ?: "en.ibn-kathir",
            quranIndexServerUrl = prefs[SERVER_URL_KEY] ?: "",
        )
    }

    suspend fun setArabicScript(script: String) {
        dataStore.edit { it[SCRIPT_KEY] = script }
    }

    suspend fun setEnabledTranslations(translations: List<String>) {
        dataStore.edit { it[TRANSLATIONS_KEY] = translations.joinToString(",") }
    }

    suspend fun setFontSize(size: Float) {
        dataStore.edit { it[FONT_SIZE_KEY] = size }
    }

    suspend fun setSelectedTafsir(tafsir: String) {
        dataStore.edit { it[TAFSIR_KEY] = tafsir }
    }

    suspend fun setQuranIndexServerUrl(url: String) {
        dataStore.edit { it[SERVER_URL_KEY] = url }
    }

    companion object {
        private val SCRIPT_KEY = stringPreferencesKey("arabic_script")
        private val TRANSLATIONS_KEY = stringPreferencesKey("enabled_translations")
        private val FONT_SIZE_KEY = floatPreferencesKey("font_size")
        private val TAFSIR_KEY = stringPreferencesKey("selected_tafsir")
        private val SERVER_URL_KEY = stringPreferencesKey("quranindex_server_url")
    }
}
