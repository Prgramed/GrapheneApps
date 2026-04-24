package dev.ecalendar.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.ecalendar.data.db.AppDatabase
import dev.ecalendar.ui.MainActivity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class CalendarWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Query upcoming events directly (widget runs outside Hilt scope)
        val db = AppDatabase.getInstance(context)
        val now = System.currentTimeMillis()
        val weekFromNow = now + 7L * 24 * 60 * 60 * 1000
        val events = db.eventDao().getFutureEvents(now, weekFromNow)
            .take(5)

        provideContent {
            GlanceTheme {
                WidgetContent(events = events.map { WidgetEvent(it.title, it.instanceStart, it.isAllDay) })
            }
        }
    }
}

private data class WidgetEvent(
    val title: String,
    val startMillis: Long,
    val isAllDay: Boolean,
)

@Composable
private fun WidgetContent(events: List<WidgetEvent>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now()
    val dateFormat = DateTimeFormatter.ofPattern("EEE d · HH:mm", Locale.getDefault())
    val dateFormatAllDay = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.surface)
            .clickable(actionStartActivity<MainActivity>())
            .padding(12.dp),
    ) {
        Text(
            text = "Upcoming",
            style = TextStyle(
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = GlanceTheme.colors.onSurface,
            ),
        )
        Spacer(GlanceModifier.height(8.dp))

        if (events.isEmpty()) {
            Text(
                text = "No upcoming events",
                style = TextStyle(
                    fontSize = 12.sp,
                    color = GlanceTheme.colors.onSurfaceVariant,
                ),
            )
        } else {
            events.forEach { event ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val timeText = if (event.isAllDay) {
                        Instant.ofEpochMilli(event.startMillis).atZone(zone).format(dateFormatAllDay)
                    } else {
                        Instant.ofEpochMilli(event.startMillis).atZone(zone).format(dateFormat)
                    }
                    Text(
                        text = timeText,
                        style = TextStyle(
                            fontSize = 11.sp,
                            color = GlanceTheme.colors.onSurfaceVariant,
                        ),
                        modifier = GlanceModifier.width(72.dp),
                    )
                    Text(
                        text = event.title,
                        style = TextStyle(
                            fontSize = 12.sp,
                            color = GlanceTheme.colors.onSurface,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

class CalendarWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CalendarWidget()
}
