package dev.eweather.ui.radar

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val DarkMapTileSource = XYTileSource(
    "CartoDB-DarkMatter",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
    ),
)

private class RainViewerTileSource(
    val frameUrl: String,
    name: String = "RainViewer",
) : OnlineTileSourceBase(
    name, 0, 12, 512, ".png", arrayOf(""),
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return frameUrl
            .replace("{z}", z.toString())
            .replace("{x}", x.toString())
            .replace("{y}", y.toString())
    }
}

// RainViewer dBZ color scale
private val radarGradientColors = listOf(
    Color(0xFF73C2FB), // Light blue - light rain
    Color(0xFF00B4D8), // Cyan
    Color(0xFF48BB78), // Green - moderate
    Color(0xFFECC94B), // Yellow
    Color(0xFFED8936), // Orange - heavy
    Color(0xFFE53E3E), // Red
    Color(0xFF9B2C2C), // Dark red - extreme
)

@Composable
fun RadarScreen(
    onBack: () -> Unit,
    viewModel: RadarViewModel = hiltViewModel(),
) {
    val frames by viewModel.frames.collectAsStateWithLifecycle()
    val currentFrameIndex by viewModel.currentFrameIndex.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val activeLocation by viewModel.activeLocation.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Configure osmdroid
    DisposableEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidTileCache = context.cacheDir
        }
        onDispose { }
    }

    // Auto-play when frames first load
    LaunchedEffect(frames.size) {
        if (frames.isNotEmpty() && !isPlaying) {
            viewModel.play()
        }
    }

    val centerPoint = remember(activeLocation) {
        activeLocation?.let { GeoPoint(it.lat, it.lon) } ?: GeoPoint(59.33, 18.07)
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Box(Modifier.fillMaxSize()) {
        if (error != null && frames.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "Error", color = MaterialTheme.colorScheme.error)
            }
        } else {
            val currentFrame = frames.getOrNull(currentFrameIndex)

            // Map
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(DarkMapTileSource)
                        setMultiTouchControls(true)
                        zoomController.setVisibility(
                            org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER,
                        )
                        controller.setZoom(7.0)
                        controller.setCenter(centerPoint)
                        minZoomLevel = 3.0
                        maxZoomLevel = 12.0

                        val marker = Marker(this).apply {
                            position = centerPoint
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            title = activeLocation?.name ?: "Location"
                            icon = ctx.resources.getDrawable(
                                android.R.drawable.presence_online, null,
                            )
                        }
                        overlays.add(marker)

                        if (currentFrame != null) {
                            val radarSource = RainViewerTileSource(
                                frameUrl = currentFrame.tileUrlTemplate,
                                name = "RainViewer-${currentFrame.timestamp}",
                            )
                            val radarProvider = MapTileProviderBasic(ctx, radarSource)
                            val radarOverlay = TilesOverlay(radarProvider, ctx).apply {
                                loadingBackgroundColor = AndroidColor.TRANSPARENT
                                loadingLineColor = AndroidColor.TRANSPARENT
                            }
                            overlays.add(radarOverlay)
                            tag = radarProvider
                        }
                    }
                },
                update = { mapView ->
                    val frame = frames.getOrNull(currentFrameIndex) ?: return@AndroidView
                    val provider = mapView.tag as? MapTileProviderBasic

                    if (provider != null) {
                        val newSource = RainViewerTileSource(
                            frameUrl = frame.tileUrlTemplate,
                            name = "RainViewer-${frame.timestamp}",
                        )
                        provider.tileSource = newSource
                        provider.clearTileCache()
                        mapView.invalidate()
                    } else {
                        val radarSource = RainViewerTileSource(
                            frameUrl = frame.tileUrlTemplate,
                            name = "RainViewer-${frame.timestamp}",
                        )
                        val newProvider = MapTileProviderBasic(mapView.context, radarSource)
                        val radarOverlay = TilesOverlay(newProvider, mapView.context).apply {
                            loadingBackgroundColor = AndroidColor.TRANSPARENT
                            loadingLineColor = AndroidColor.TRANSPARENT
                        }
                        mapView.overlays.add(radarOverlay)
                        mapView.tag = newProvider
                        mapView.invalidate()
                    }
                },
            )

            // Timestamp overlay (top-center)
            if (currentFrame != null) {
                val now = System.currentTimeMillis()
                val timestampLabel = if (currentFrame.timestamp > now) {
                    val minsAhead = ((currentFrame.timestamp - now) / 60000).toInt()
                    "Now +${minsAhead}min"
                } else {
                    timeFormat.format(Date(currentFrame.timestamp))
                }

                Text(
                    text = timestampLabel,
                    style = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.7f),
                            offset = Offset(0f, 2f),
                            blurRadius = 8f,
                        ),
                    ),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 52.dp),
                )
            }
        }

        // Loading indicator
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // Back button
        FloatingActionButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = 48.dp)
                .size(40.dp),
            shape = CircleShape,
            containerColor = Color.Black.copy(alpha = 0.5f),
            contentColor = Color.White,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                modifier = Modifier.size(20.dp),
            )
        }

        // Bottom controls
        if (frames.size > 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                // Scrub slider
                Slider(
                    value = currentFrameIndex.toFloat(),
                    onValueChange = { viewModel.seekTo(it.roundToInt()) },
                    valueRange = 0f..(frames.size - 1).toFloat(),
                    steps = frames.size - 2,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Play/Pause + Now button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::togglePlayPause) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    TextButton(onClick = viewModel::seekToNow) {
                        Text("Now", color = Color.White, fontSize = 14.sp)
                    }
                }

                // Color legend
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Light", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                    Text("Heavy", fontSize = 10.sp, color = Color.White.copy(alpha = 0.6f))
                }
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp),
                ) {
                    drawRect(
                        brush = Brush.horizontalGradient(radarGradientColors),
                        size = size,
                    )
                }
            }
        }
    }
}
