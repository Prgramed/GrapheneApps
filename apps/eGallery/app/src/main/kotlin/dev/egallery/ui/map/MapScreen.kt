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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.XYTileSource
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onPhotoClick: (String) -> Unit,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val allMarkers by viewModel.markers.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mapViewRef = remember { mutableStateOf<MapView?>(null) }
    val selectedCluster by viewModel.selectedCluster.collectAsState()

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
    val currentMarkers = rememberUpdatedState(validMarkers)
    val onClusterClick: (PhotoCluster) -> Unit = remember { { cluster -> viewModel.selectCluster(cluster) } }
    // Use saved position if available, otherwise first marker
    val centerPoint = remember(validMarkers) {
        if (viewModel.savedCenterLat != null) {
            GeoPoint(viewModel.savedCenterLat!!, viewModel.savedCenterLon!!)
        } else if (validMarkers.isNotEmpty()) {
            GeoPoint(validMarkers.first().lat, validMarkers.first().lon)
        } else {
            GeoPoint(51.5, -0.1)
        }
    }

    // Re-add markers whenever data changes (e.g. API returns)
    LaunchedEffect(validMarkers) {
        val mapView = mapViewRef.value ?: return@LaunchedEffect
        addMarkersAsync(mapView, viewModel, validMarkers, onPhotoClick, onClusterClick, scope)
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
                    controller.setZoom(viewModel.savedZoom)
                    controller.setCenter(centerPoint)
                    minZoomLevel = 2.0
                    maxZoomLevel = 19.0

                    // Save position + rebuild markers on zoom/scroll change
                    var lastZoomInt = zoomLevelDouble.toInt()
                    addMapListener(object : MapListener {
                        override fun onScroll(event: ScrollEvent?): Boolean {
                            val center = mapCenter
                            viewModel.saveMapPosition(zoomLevelDouble, center.latitude, center.longitude)
                            return false
                        }
                        override fun onZoom(event: ZoomEvent?): Boolean {
                            val center = mapCenter
                            viewModel.saveMapPosition(zoomLevelDouble, center.latitude, center.longitude)
                            val newZoomInt = zoomLevelDouble.toInt()
                            if (newZoomInt != lastZoomInt) {
                                lastZoomInt = newZoomInt
                                scope.launch {
                                    addMarkersAsync(this@apply, viewModel, currentMarkers.value, onPhotoClick, onClusterClick, scope)
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

    // Cluster photo grid bottom sheet
    selectedCluster?.let { cluster ->
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        // Filter to valid server IDs only (temp negative IDs have no server thumbnail)
        val validPhotos = remember(cluster) {
            cluster.markers.filter { it.id.length > 10 && !it.id.startsWith("-") }
        }
        ModalBottomSheet(
            onDismissRequest = { viewModel.selectCluster(null) },
            sheetState = sheetState,
        ) {
            Text(
                text = "${validPhotos.size} photos",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(validPhotos) { marker ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                // Save map position before navigating
                                mapViewRef.value?.let { mv ->
                                    val c = mv.mapCenter
                                    viewModel.saveMapPosition(mv.zoomLevelDouble, c.latitude, c.longitude)
                                }
                                onPhotoClick(marker.id)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(viewModel.thumbnailUrl(marker.id))
                                .size(256)
                                .memoryCacheKey("map_thumb_${marker.id}")
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            imageLoader = SingletonImageLoader.get(context),
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
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

// Returns cell size in degrees for clustering at a given zoom level
private fun clusterCellSizeForZoom(zoom: Double): Double = when {
    zoom >= 16 -> 0.001   // ~100m
    zoom >= 14 -> 0.01    // ~1km
    zoom >= 11 -> 0.1     // ~10km
    zoom >= 8 -> 1.0      // ~100km
    zoom >= 5 -> 5.0      // ~500km
    else -> 20.0           // ~2000km
}

private suspend fun addMarkersAsync(
    mapView: MapView,
    viewModel: MapViewModel,
    items: List<dev.egallery.api.dto.ImmichMapMarker>,
    onPhotoClick: (String) -> Unit,
    onClusterClick: (PhotoCluster) -> Unit,
    scope: kotlinx.coroutines.CoroutineScope,
) {
    val cellSize = clusterCellSizeForZoom(mapView.zoomLevelDouble)

    // Cluster off main thread
    val clusters = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
        viewModel.cluster(items, cellSize)
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
                    onClusterClick(cluster)
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
