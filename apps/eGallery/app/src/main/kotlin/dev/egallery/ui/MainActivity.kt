package dev.egallery.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

import dev.egallery.data.db.dao.MediaDao
import dev.egallery.data.db.dao.UploadQueueDao
import dev.egallery.data.db.entity.MediaEntity
import dev.egallery.data.db.entity.UploadQueueEntity
import dev.egallery.data.preferences.AppPreferencesRepository
import dev.egallery.sync.DeviceMediaScanner
import dev.egallery.sync.EvictionScheduler
import dev.egallery.sync.UploadWorker
import dev.egallery.ui.navigation.EGalleryNavGraph
import dev.egallery.util.MediaFileUtil
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.grapheneapps.core.designsystem.theme.GrapheneAppsTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var evictionScheduler: EvictionScheduler
    @Inject lateinit var uploadQueueDao: UploadQueueDao
    @Inject lateinit var mediaDao: MediaDao
    @Inject lateinit var preferencesRepository: AppPreferencesRepository
    @Inject lateinit var deviceMediaScanner: DeviceMediaScanner
    @Inject lateinit var credentialStore: dev.egallery.data.CredentialStore

    private val scope get() = lifecycleScope
    private val tempIdCounter = AtomicInteger(-1000) // separate range from CameraWatcher

    // Intent-derived state
    private val startUri = mutableStateOf<String?>(null)
    private val pickerMode = mutableStateOf(false)

    private val mediaPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val anyGranted = results.values.any { it }
        if (anyGranted) {
            scope.launch(Dispatchers.IO) { deviceMediaScanner.scanIfNeeded() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        resolveIntent(intent)
        // CameraWatcher removed — sync handles new photos via MediaStore scan
        evictionScheduler.scheduleDaily()
        handleFirstLaunch()
        requestMediaPermissionsAndScan()
        enableEdgeToEdge()
        setContent {
            val pendingCount by uploadQueueDao.getAll().map { it.size }
                .collectAsState(initial = 0)
            GrapheneAppsTheme {
                EGalleryNavGraph(
                    credentialStore = credentialStore,
                    pendingUploadCount = pendingCount,
                    isNasReachable = true, // Immich uses API key — always reachable if configured
                    pickerMode = pickerMode.value,
                    onPickResult = { uri ->
                        setResult(RESULT_OK, Intent().setData(uri))
                        finish()
                    },
                    startUri = startUri.value,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        resolveIntent(intent)
    }

    private fun resolveIntent(intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                Timber.d("ACTION_VIEW: $uri (type: ${intent.type})")
                if (uri != null) {
                    startUri.value = uri.toString()
                }
            }
            Intent.ACTION_GET_CONTENT -> {
                Timber.d("GET_CONTENT: picker mode")
                pickerMode.value = true
            }
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                Timber.d("ACTION_SEND: $uri")
                if (uri != null) {
                    scope.launch(Dispatchers.IO) { importSharedUri(uri) }
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val uris = intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                Timber.d("ACTION_SEND_MULTIPLE: ${uris?.size} items")
                uris?.forEach { uri ->
                    scope.launch(Dispatchers.IO) { importSharedUri(uri) }
                }
            }
        }
    }

    private suspend fun importSharedUri(uri: Uri) {
        try {
            val filename = uri.lastPathSegment ?: "shared_${System.currentTimeMillis()}"
            val destDir = File(filesDir, "shared_imports")
            destDir.mkdirs()
            val destFile = File(destDir, filename)

            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return

            val captureDate = MediaFileUtil.extractCaptureDate(destFile)
            val mediaType = MediaFileUtil.mediaTypeFromFile(destFile.name)
            val tempNasId = tempIdCounter.getAndDecrement().toString()

            val entity = MediaEntity(
                nasId = tempNasId,
                filename = destFile.name,
                captureDate = captureDate,
                fileSize = destFile.length(),
                mediaType = mediaType.name,
                folderId = 0,
                cacheKey = "",
                localPath = destFile.absolutePath,
                storageStatus = "UPLOAD_PENDING",
                lastSyncedAt = System.currentTimeMillis(),
            )
            mediaDao.upsert(entity)

            val queueItem = UploadQueueEntity(
                localPath = destFile.absolutePath,
                targetFolderId = 0,
            )
            uploadQueueDao.insert(queueItem)

            // Enqueue upload worker with WiFi-only preference
            val wifiOnly = preferencesRepository.wifiOnlyUpload.first()
            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            WorkManager.getInstance(this@MainActivity).enqueueUniqueWork(
                UploadWorker.WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<UploadWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(networkType)
                            .build(),
                    )
                    .build(),
            )

            Timber.d("Imported shared file: ${destFile.name} (tempNasId=$tempNasId)")
        } catch (e: Exception) {
            Timber.e(e, "Failed to import shared URI: $uri")
        }
    }

    private fun requestMediaPermissionsAndScan() {
        val hasImages = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_MEDIA_IMAGES,
        ) == PackageManager.PERMISSION_GRANTED
        val hasVideo = ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_MEDIA_VIDEO,
        ) == PackageManager.PERMISSION_GRANTED

        if (hasImages && hasVideo) {
            scope.launch(Dispatchers.IO) { deviceMediaScanner.scanIfNeeded() }
        } else {
            mediaPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.READ_MEDIA_VIDEO,
                ),
            )
        }
    }

    private fun handleFirstLaunch() {
        scope.launch(Dispatchers.IO) {
            if (preferencesRepository.isFirstLaunchDone()) return@launch
            preferencesRepository.setFirstLaunchDone()

            // Request MANAGE_MEDIA permission
            if (!MediaStore.canManageMedia(this@MainActivity)) {
                runOnUiThread {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA)
                        intent.data = Uri.parse("package:${packageName}")
                        startActivity(intent)
                    } catch (e: Exception) {
                        Timber.w(e, "Could not open MANAGE_MEDIA settings")
                    }
                }
            }

            // Tip about becoming default gallery
            runOnUiThread {
                android.widget.Toast.makeText(
                    this@MainActivity,
                    "Tip: Open a photo from Files to set eGallery as your default viewer",
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
    }
}
