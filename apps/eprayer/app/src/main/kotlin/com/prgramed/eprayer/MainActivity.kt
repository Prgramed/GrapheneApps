package com.prgramed.eprayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.grapheneapps.core.designsystem.theme.PrayerTheme
import com.prgramed.eprayer.navigation.PrayerNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrayerTheme {
                PrayerNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
