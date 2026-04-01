package dev.egallery.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

private val DarkMapTileSource = XYTileSource(
    "CartoDB-DarkMatter",
    0, 19, 256, ".png",
    arrayOf(
        "https://a.basemaps.cartocdn.com/dark_all/",
        "https://b.basemaps.cartocdn.com/dark_all/",
        "https://c.basemaps.cartocdn.com/dark_all/",
    ),
)

@Composable
fun MapScreen(
    onPhotoClick: (String) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val allMarkers by viewModel.markers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mapViewRef = remember { androidx.compose.runtime.mutableStateOf<MapView?>(null) }

    DisposableEffect(Unit) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidTileCache = context.cacheDir
        }
        onDispose { }
    }

    // Location permission launcher
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            goToMyLocation(context, mapViewRef.value)
        }
    }

    if (allMarkers.isEmpty() && !isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Map,
                    contentDescription = "No geotagged photos",
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "No geotagged photos",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        return
    }

    val validMarkers = remember(allMarkers) {
        allMarkers.filter { it.lat != 0.0 && it.lon != 0.0 }
    }
    val centerPoint = remember(validMarkers) {
        if (validMarkers.isNotEmpty()) {
            GeoPoint(validMarkers.first().lat, validMarkers.first().lon)
        } else {
            GeoPoint(51.5, -0.1)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    mapViewRef.value = this
                    setTileSource(DarkMapTileSource)
                    setMultiTouchControls(true)
                    zoomController.setVisibility(
                        org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER,
                    )
                    controller.setZoom(6.0)
                    controller.setCenter(centerPoint)
                    minZoomLevel = 2.0
                    maxZoomLevel = 19.0

                    // Initial markers at default zoom
                    scope.launch {
                        addMarkersAsync(this@apply, viewModel, validMarkers, onPhotoClick, scope)
                    }

                    // Rebuild markers on zoom change (throttled)
                    var lastZoomInt = zoomLevelDouble.toInt()
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean = false
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            val newZoomInt = zoomLevelDouble.toInt()
                            if (newZoomInt != lastZoomInt) {
                                lastZoomInt = newZoomInt
                                scope.launch {
                                    addMarkersAsync(this@apply, viewModel, validMarkers, onPhotoClick, scope)
                                }
                            }
                            return false
                        }
                    })
                }
            },
            update = { mapView ->
                mapViewRef.value = mapView
            },
        )

        // My location FAB
        FloatingActionButton(
            onClick = {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION,
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPermission) {
                    goToMyLocation(context, mapViewRef.value)
                } else {
                    locationPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Default.MyLocation, "My location")
        }
    }
}

private fun goToMyLocation(context: android.content.Context, mapView: MapView?) {
    if (mapView == null) return
    try {
        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as LocationManager
        @Suppress("MissingPermission")
        val loc = lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        if (loc != null) {
            mapView.controller.animateTo(GeoPoint(loc.latitude, loc.longitude))
            mapView.controller.setZoom(14.0)
        }
    } catch (_: SecurityException) { }
}

private fun clusterPrecisionForZoom(zoom: Double): Int = when {
    zoom >= 16 -> 6
    zoom >= 14 -> 5
    zoom >= 11 -> 4
    zoom >= 8 -> 3
    else -> 2
}

private suspend fun addMarkersAsync(
    mapView: MapView,
    viewModel: MapViewModel,
    items: List<dev.egallery.api.dto.ImmichMapMarker>,
    onPhotoClick: (String) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val precision = clusterPrecisionForZoom(mapView.zoomLevelDouble)

    // Cluster off main thread
    val clusters = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        viewModel.cluster(items, precision)
    }

    // Cap rendered markers to prevent UI freeze
    val maxMarkers = 500
    val visibleClusters = if (clusters.size > maxMarkers) {
        clusters.sortedByDescending { it.count }.take(maxMarkers)
    } else {
        clusters
    }

    mapView.overlays.removeAll { it is Marker }

    for (cluster in visibleClusters) {
        val marker = Marker(mapView).apply {
            position = GeoPoint(cluster.lat, cluster.lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)

            if (cluster.isSingle) {
                val item = cluster.markers.first()
                title = item.city ?: item.country ?: item.id.take(8)
                snippet = "${item.lat}, ${item.lon}"
                setOnMarkerClickListener { _, _ ->
                    onPhotoClick(item.id)
                    true
                }

                val thumbUrl = viewModel.thumbnailUrl(item.id)
                icon = createClusterDrawable(mapView.context, 1)
                scope.launch {
                    try {
                        val imageLoader = SingletonImageLoader.get(mapView.context)
                        val request = ImageRequest.Builder(mapView.context)
                            .data(thumbUrl)
                            .size(96)
                            .build()
                        val result = imageLoader.execute(request)
                        if (result is SuccessResult) {
                            val bmp = result.image.toBitmap(96, 96)
                            val circular = createCircularBitmap(bmp)
                            this@apply.icon = BitmapDrawable(mapView.context.resources, circular)
                            mapView.postInvalidate()
                        }
                    } catch (_: Exception) { }
                }
            } else {
                title = "${cluster.count} photos"
                setOnMarkerClickListener { _, _ ->
                    // Small clusters or high zoom: open first photo
                    if (cluster.count <= 5 || mapView.zoomLevelDouble >= 14) {
                        onPhotoClick(cluster.markers.first().id)
                    } else {
                        val lats = cluster.markers.map { it.lat }
                        val lngs = cluster.markers.map { it.lon }
                        if (lats.isNotEmpty() && lngs.isNotEmpty()) {
                            val box = BoundingBox(
                                lats.max(), lngs.max(),
                                lats.min(), lngs.min(),
                            )
                            mapView.zoomToBoundingBox(box.increaseByScale(1.3f), true)
                        }
                    }
                    true
                }
                icon = createClusterDrawable(mapView.context, cluster.count)
            }
        }
        mapView.overlays.add(marker)
    }

    mapView.invalidate()
}

private fun createCircularBitmap(src: Bitmap): Bitmap {
    val size = minOf(src.width, src.height)
    val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = size / 2f

    canvas.drawCircle(radius, radius, radius, paint.apply {
        shader = android.graphics.BitmapShader(
            src, android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP,
        )
    })

    // Border
    paint.shader = null
    paint.style = Paint.Style.STROKE
    paint.color = AndroidColor.WHITE
    paint.strokeWidth = size * 0.06f
    canvas.drawCircle(radius, radius, radius - paint.strokeWidth / 2, paint)

    return output
}

private fun createClusterDrawable(
    context: android.content.Context,
    count: Int,
): android.graphics.drawable.Drawable {
    val color = when {
        count <= 1 -> 0xFFFFFFFF.toInt()
        count <= 5 -> 0xFF90CAF9.toInt()
        count <= 20 -> 0xFF42A5F5.toInt()
        else -> 0xFF1565C0.toInt()
    }

    val size = when {
        count <= 1 -> 24
        count <= 5 -> 36
        count <= 20 -> 44
        else -> 52
    }

    val density = context.resources.displayMetrics.density
    val sizePx = (size * density).toInt()

    if (count <= 1) {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke((2 * density).toInt(), 0xFF000000.toInt())
            setSize(sizePx, sizePx)
        }
    }

    // Cluster with count text
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val radius = sizePx / 2f

    // Background circle
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    canvas.drawCircle(radius, radius, radius, bgPaint)

    // Border
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = AndroidColor.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2 * density
    }
    canvas.drawCircle(radius, radius, radius - density, borderPaint)

    // Count text
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = if (count <= 5) AndroidColor.BLACK else AndroidColor.WHITE
        textSize = sizePx * 0.4f
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val textY = radius - (textPaint.descent() + textPaint.ascent()) / 2
    canvas.drawText(count.toString(), radius, textY, textPaint)

    return BitmapDrawable(context.resources, bitmap)
}
