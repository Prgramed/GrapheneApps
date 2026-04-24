package com.prgramed.edoist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.grapheneapps.core.designsystem.theme.GrapheneAppsTheme
import com.prgramed.edoist.domain.repository.UserPreferencesRepository
import com.prgramed.edoist.navigation.EDoistNavHost
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var prefsRepo: UserPreferencesRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by prefsRepo.getPreferences().collectAsState(
                initial = com.prgramed.edoist.domain.repository.EDoistPreferences(),
            )
            val darkTheme = when (prefs.themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            GrapheneAppsTheme(darkTheme = darkTheme) {
                EDoistNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
