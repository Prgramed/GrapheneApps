package com.prgramed.edoist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.grapheneapps.core.designsystem.theme.GrapheneAppsTheme
import com.prgramed.edoist.navigation.EDoistNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrapheneAppsTheme {
                EDoistNavHost(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
