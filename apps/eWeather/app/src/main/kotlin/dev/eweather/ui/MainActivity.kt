package dev.eweather.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.lifecycle.lifecycleScope
import com.grapheneapps.core.designsystem.theme.GrapheneAppsTheme
import dev.eweather.ui.navigation.EWeatherNavHost
import dev.eweather.ui.widget.WeatherWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GrapheneAppsTheme {
                EWeatherNavHost()
            }
        }
    }

    private var lastWidgetUpdate = 0L

    override fun onResume() {
        super.onResume()
        val now = System.currentTimeMillis()
        if (now - lastWidgetUpdate < 60_000) return // Skip if updated < 1 minute ago
        lastWidgetUpdate = now
        lifecycleScope.launch {
            try {
                val manager = GlanceAppWidgetManager(this@MainActivity)
                val ids = manager.getGlanceIds(WeatherWidget::class.java)
                ids.forEach { WeatherWidget().update(this@MainActivity, it) }
            } catch (_: Exception) { }
        }
    }
}
