package com.prgramed.eprayer.feature.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.prgramed.eprayer.data.widget.PrayerWidgetWorker
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class PrayerWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val nextPrayerInfo = getNextPrayerInfo(context)

        provideContent {
            GlanceTheme {
                Column(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(GlanceTheme.colors.primaryContainer)
                        .padding(12.dp)
                        .cornerRadius(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = nextPrayerInfo.name,
                        style = TextStyle(
                            fontWeight = FontWeight.Bold,
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 16.sp,
                        ),
                    )
                    Text(
                        text = nextPrayerInfo.time,
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 14.sp,
                        ),
                    )
                    if (nextPrayerInfo.countdown.isNotEmpty()) {
                        Text(
                            text = nextPrayerInfo.countdown,
                            style = TextStyle(
                                color = GlanceTheme.colors.secondary,
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun getNextPrayerInfo(context: Context): NextPrayerInfo {
        val prefs = context.getSharedPreferences(
            PrayerWidgetWorker.PREFS_NAME, Context.MODE_PRIVATE,
        )

        val name = prefs.getString(PrayerWidgetWorker.KEY_PRAYER_NAME, null)
        val timeMillis = prefs.getLong(PrayerWidgetWorker.KEY_PRAYER_TIME_MILLIS, 0L)

        if (name == null || timeMillis == 0L) {
            return NextPrayerInfo("ePrayer", "Updating...", "")
        }

        val nowMillis = System.currentTimeMillis()
        val formatter = DateTimeFormatter.ofPattern("hh:mm a")
        val formattedTime = java.time.Instant.ofEpochMilli(timeMillis)
            .atZone(ZoneId.systemDefault()).format(formatter)

        val remainingMillis = timeMillis - nowMillis
        val countdown = if (remainingMillis > 0) {
            val hours = remainingMillis / 3_600_000
            val minutes = (remainingMillis % 3_600_000) / 60_000
            "%dh %dm".format(hours, minutes)
        } else {
            ""
        }

        return NextPrayerInfo(name, formattedTime, countdown)
    }

    private data class NextPrayerInfo(val name: String, val time: String, val countdown: String)
}
