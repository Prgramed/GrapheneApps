package dev.ecalendar.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.grapheneapps.core.designsystem.theme.GrapheneAppsTheme
import dagger.hilt.android.AndroidEntryPoint
import dev.ecalendar.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val _pendingIcsContent = MutableStateFlow<String?>(null)
    val pendingIcsContent: StateFlow<String?> = _pendingIcsContent.asStateFlow()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIcsIntent(intent)
        enableEdgeToEdge()
        setContent {
            val viewModel: CalendarViewModel = hiltViewModel()
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()
            val isDark = when (prefs.themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            GrapheneAppsTheme(darkTheme = isDark) {
                CalendarScaffold(
                    viewModel = viewModel,
                    pendingIcsContent = pendingIcsContent,
                    onIcsConsumed = { _pendingIcsContent.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIcsIntent(intent)
    }

    private fun handleIcsIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        try {
            val icsContent = contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
            if (icsContent != null && icsContent.contains("VCALENDAR")) {
                _pendingIcsContent.value = icsContent
                Timber.d("ICS intent: read ${icsContent.length} chars from $uri")
            }
        } catch (e: Exception) {
            Timber.w(e, "Failed to read ICS from intent URI: $uri")
        }
    }
}
