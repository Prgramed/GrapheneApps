package dev.eweather.ui.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.room.Room
import dev.eweather.data.db.AppDatabase
import dev.eweather.domain.model.WeatherData
import dev.eweather.ui.weather.components.deriveSkyCategory
import dev.eweather.util.SkyCategory
import dev.eweather.util.WmoCode
import kotlinx.serialization.json.Json
import java.time.LocalTime
import kotlin.math.roundToInt

private val COMPACT = DpSize(110.dp, 110.dp)
private val STANDARD = DpSize(250.dp, 110.dp)

class WeatherWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, STANDARD))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        val launchIntent = context.packageManager
            .getLaunchIntentForPackage(context.packageName)
            ?: Intent()

        provideContent {
            val size = LocalSize.current
            val isWide = size.width >= 250.dp
            val bgColor = data?.bgColor ?: Color(0xFF1C2951)

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(16.dp)
                    .background(bgColor)
                    .clickable(actionStartActivity(launchIntent))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (data == null) {
                    Text(
                        text = "Tap to refresh",
                        style = TextStyle(
                            color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                            fontSize = 14.sp,
                        ),
                    )
                } else if (isWide) {
                    WideLayout(data)
                } else {
                    CompactLayout(data)
                }
            }
        }
    }
}

@Composable
private fun CompactLayout(data: WidgetData) {
    Column(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = data.cityName,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 15.sp,
            ),
            maxLines = 1,
        )
        Text(
            text = "${data.temp.roundToInt()}\u00B0",
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 48.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
        Text(
            text = "${data.emoji} ${data.description}",
            style = TextStyle(
                color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                fontSize = 13.sp,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun WideLayout(data: WidgetData) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left column
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = data.cityName,
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 15.sp,
                ),
                maxLines = 1,
            )
            Text(
                text = "${data.temp.roundToInt()}\u00B0",
                style = TextStyle(
                    color = ColorProvider(Color.White),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
            Text(
                text = "${data.emoji} ${data.description}",
                style = TextStyle(
                    color = ColorProvider(Color.White.copy(alpha = 0.7f)),
                    fontSize = 13.sp,
                ),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.width(12.dp))

        // Right column — details
        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailRow("H/L", "${data.tempMax.roundToInt()}\u00B0 / ${data.tempMin.roundToInt()}\u00B0")
            Spacer(GlanceModifier.height(4.dp))
            DetailRow("Feels", "${data.feelsLike.roundToInt()}\u00B0")
            Spacer(GlanceModifier.height(4.dp))
            DetailRow("Humidity", "${data.humidity}%")
            Spacer(GlanceModifier.height(4.dp))
            DetailRow("Wind", "${data.windSpeed.roundToInt()} km/h")
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(Color.White.copy(alpha = 0.5f)),
                fontSize = 13.sp,
            ),
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(Color.White),
                fontSize = 13.sp,
            ),
        )
    }
}

private data class WidgetData(
    val cityName: String,
    val temp: Float,
    val feelsLike: Float,
    val tempMax: Float,
    val tempMin: Float,
    val humidity: Int,
    val windSpeed: Float,
    val description: String,
    val emoji: String,
    val bgColor: Color,
)

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

private suspend fun loadWidgetData(context: Context): WidgetData? {
    return try {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, "eweather.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

        // Get active location ID from DataStore preferences file
        val prefs = context.getSharedPreferences("eweather_widget", Context.MODE_PRIVATE)

        // Read location from Room
        val locationDao = db.locationDao()
        val locations = locationDao.getAll()
        val location = locations.firstOrNull() ?: return null

        // Read cached weather
        val cache = db.weatherDao().getCacheForLocation(location.id, "forecast") ?: return null
        val weatherData = json.decodeFromString<WeatherData>(cache.json)

        val current = weatherData.current
        val today = weatherData.daily.firstOrNull()
        val hour = LocalTime.now().hour
        val wmo = WmoCode.describe(current.weatherCode, current.isDay)
        val skyCategory = deriveSkyCategory(current.weatherCode, current.isDay, hour)
        val bgColor = widgetBgColor(skyCategory)

        WidgetData(
            cityName = location.name,
            temp = current.temp,
            feelsLike = current.feelsLike,
            tempMax = today?.tempMax ?: current.temp,
            tempMin = today?.tempMin ?: current.temp,
            humidity = current.humidity,
            windSpeed = current.windSpeed,
            description = wmo.label,
            emoji = weatherEmoji(wmo.iconCategory),
            bgColor = bgColor,
        )
    } catch (_: Exception) {
        null
    }
}

private fun widgetBgColor(sky: SkyCategory): Color = when (sky) {
    SkyCategory.CLEAR_DAY -> Color(0xFF4FC3F7)
    SkyCategory.CLEAR_NIGHT -> Color(0xFF1C2951)
    SkyCategory.PARTLY_CLOUDY_DAY -> Color(0xFF81D4FA)
    SkyCategory.PARTLY_CLOUDY_NIGHT -> Color(0xFF263238)
    SkyCategory.OVERCAST -> Color(0xFF546E7A)
    SkyCategory.RAIN -> Color(0xFF37474F)
    SkyCategory.SNOW -> Color(0xFF78909C)
    SkyCategory.STORM -> Color(0xFF37474F)
    SkyCategory.FOG -> Color(0xFF607D8B)
    SkyCategory.PRE_DAWN -> Color(0xFF1A237E)
    SkyCategory.SUNRISE -> Color(0xFFFF8A65)
    SkyCategory.SUNSET -> Color(0xFFE57373)
}

private fun weatherEmoji(icon: dev.eweather.util.IconCategory): String = when (icon) {
    dev.eweather.util.IconCategory.CLEAR -> "\u2600\uFE0F"
    dev.eweather.util.IconCategory.PARTLY_CLOUDY -> "\u26C5"
    dev.eweather.util.IconCategory.OVERCAST -> "\u2601\uFE0F"
    dev.eweather.util.IconCategory.FOG -> "\uD83C\uDF2B\uFE0F"
    dev.eweather.util.IconCategory.DRIZZLE -> "\uD83C\uDF26\uFE0F"
    dev.eweather.util.IconCategory.RAIN -> "\uD83C\uDF27\uFE0F"
    dev.eweather.util.IconCategory.SNOW -> "\u2744\uFE0F"
    dev.eweather.util.IconCategory.STORM -> "\u26C8\uFE0F"
    dev.eweather.util.IconCategory.HAIL -> "\uD83C\uDF28\uFE0F"
}
