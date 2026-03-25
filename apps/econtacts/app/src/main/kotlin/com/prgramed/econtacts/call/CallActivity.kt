package com.prgramed.econtacts.call

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.telecom.Call
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tty
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.grapheneapps.core.designsystem.theme.GrapheneAppsTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@AndroidEntryPoint
class CallActivity : ComponentActivity() {

    private val viewModel: InCallViewModel by viewModels()
    private var proximityWakeLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Show on lock screen without dismissing keyguard — allows interaction above lock
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        @Suppress("DEPRECATION")
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Proximity sensor: turn screen off when near ear
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            proximityWakeLock = powerManager.newWakeLock(
                PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,
                "eContacts:proximity",
            )
        }

        enableEdgeToEdge()
        setContent {
            GrapheneAppsTheme {
                InCallScreen(viewModel = viewModel, onFinish = {
                    releaseProximityLock()
                    finishAndRemoveTask()
                })
            }
        }
    }

    override fun onResume() {
        super.onResume()
        proximityWakeLock?.let { lock ->
            if (!lock.isHeld) lock.acquire()
        }
    }

    override fun onPause() {
        super.onPause()
        releaseProximityLock()
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseProximityLock()
    }

    private fun releaseProximityLock() {
        proximityWakeLock?.let { lock ->
            if (lock.isHeld) lock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, CallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
private fun InCallScreen(viewModel: InCallViewModel, onFinish: () -> Unit) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Auto-close after call ends
    LaunchedEffect(uiState.callState) {
        if (uiState.callState == Call.STATE_DISCONNECTED) {
            delay(2000)
            onFinish()
        }
    }
    // Fallback: if all calls removed from CallManager, close immediately
    LaunchedEffect(Unit) {
        CallManager.calls.collect { calls ->
            if (calls.isEmpty() && uiState.callState == Call.STATE_DISCONNECTED) {
                delay(1500)
                onFinish()
            }
        }
    }

    val stateText = when {
        uiState.callState == Call.STATE_DISCONNECTED -> "Call ended"
        uiState.isOnHold -> "On hold"
        else -> when (uiState.callState) {
            Call.STATE_RINGING -> "Incoming call"
            Call.STATE_DIALING -> "Calling..."
            Call.STATE_ACTIVE -> "On call"
            Call.STATE_CONNECTING -> "Connecting..."
            else -> ""
        }
    }
    val isRinging = uiState.callState == Call.STATE_RINGING
    val isActive = uiState.callState == Call.STATE_ACTIVE || uiState.isOnHold
    val displayName = uiState.callerName ?: uiState.callerNumber.ifBlank { "Unknown" }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(64.dp))

            // Caller info
            CallerInfoSection(
                displayName = displayName,
                photoUri = uiState.callerPhotoUri,
                callerNumber = uiState.callerNumber,
                callerName = uiState.callerName,
                stateText = stateText,
                durationSeconds = uiState.callDurationSeconds,
                simLabel = uiState.simLabel,
                showTimer = isActive,
                isRttActive = uiState.isRttActive,
            )

            // RTT panel or spacer
            if (uiState.showRttPanel) {
                RttChatPanel(
                    transcript = uiState.rttTranscript,
                    pendingRemoteText = uiState.pendingRemoteText,
                    pendingLocalText = uiState.pendingLocalText,
                    onTextChanged = viewModel::onRttTextChanged,
                    onSend = viewModel::sendRttMessage,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Control grid (not shown when ringing or disconnected)
            if (!isRinging && uiState.callState != Call.STATE_DISCONNECTED) {
                ControlGrid(
                    isMuted = uiState.isMuted,
                    audioRoute = uiState.audioRoute,
                    isOnHold = uiState.isOnHold,
                    isDtmfActive = uiState.showDtmfPad,
                    isRttActive = uiState.isRttActive,
                    canMerge = uiState.canMerge,
                    hasMultipleCalls = uiState.hasMultipleCalls,
                    onMuteToggle = viewModel::toggleMute,
                    onDialpadToggle = viewModel::toggleDtmfPad,
                    onAudioRouteClick = viewModel::showAudioRoutePicker,
                    onHoldToggle = viewModel::toggleHold,
                    onAddCall = viewModel::addCall,
                    onMerge = viewModel::mergeConference,
                    onRttToggle = viewModel::toggleRtt,
                )
                Spacer(modifier = Modifier.height(32.dp))
            }

            // Action buttons
            if (isRinging) {
                SwipeAnswerHandle(
                    onAnswer = { viewModel.answer() },
                    onDecline = { viewModel.hangup() },
                    modifier = Modifier.padding(bottom = 48.dp),
                )
            } else if (uiState.callState != Call.STATE_DISCONNECTED) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.hangup() },
                        containerColor = Color(0xFFF44336),
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(
                            Icons.Default.CallEnd,
                            contentDescription = "Hang up",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        // DTMF overlay
        AnimatedVisibility(
            visible = uiState.showDtmfPad,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            DtmfPadOverlay(
                onDigit = viewModel::onDtmfDigit,
                onDismiss = viewModel::toggleDtmfPad,
            )
        }
    }

    // Audio route picker dialog
    if (uiState.showAudioRoutePicker) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAudioRoutePicker() },
            title = { Text("Audio output") },
            text = {
                Column {
                    uiState.availableAudioRoutes.forEach { option ->
                        val isSelected = option.route == uiState.audioRoute
                        TextButton(
                            onClick = { viewModel.selectAudioRoute(option.route) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = when (option.route) {
                                        android.telecom.CallAudioState.ROUTE_SPEAKER ->
                                            Icons.AutoMirrored.Filled.VolumeUp
                                        android.telecom.CallAudioState.ROUTE_BLUETOOTH ->
                                            Icons.Default.Bluetooth
                                        else -> Icons.Default.Call
                                    },
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAudioRoutePicker() }) { Text("Cancel") }
            },
        )
    }

    // RTT error dialog
    if (uiState.rttError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissRttError() },
            title = { Text("RTT") },
            text = { Text(uiState.rttError!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissRttError() }) { Text("OK") }
            },
        )
    }

    // RTT request dialog
    if (uiState.showRttRequestDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.declineRttRequest() },
            title = { Text("RTT Request") },
            text = { Text("The other party wants to communicate using Real-Time Text.") },
            confirmButton = {
                TextButton(onClick = { viewModel.acceptRttRequest() }) { Text("Accept") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.declineRttRequest() }) { Text("Decline") }
            },
        )
    }
}

@Composable
private fun CallerInfoSection(
    displayName: String,
    photoUri: String?,
    callerNumber: String,
    callerName: String?,
    stateText: String,
    durationSeconds: Long,
    simLabel: String?,
    showTimer: Boolean,
    isRttActive: Boolean,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Avatar
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (photoUri != null) {
                AsyncImage(
                    model = photoUri,
                    contentDescription = "Contact photo",
                    modifier = Modifier
                        .size(96.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = displayName.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        // Show number below name if name was resolved
        if (callerName != null && callerNumber.isNotBlank()) {
            Text(
                text = callerNumber,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stateText,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Timer
        if (showTimer && durationSeconds > 0) {
            val minutes = durationSeconds / 60
            val seconds = durationSeconds % 60
            Text(
                text = "%02d:%02d".format(minutes, seconds),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // SIM label
        if (simLabel != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = simLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // RTT active indicator
        if (isRttActive) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "RTT active",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ControlGrid(
    isMuted: Boolean,
    audioRoute: Int,
    isOnHold: Boolean,
    isDtmfActive: Boolean,
    isRttActive: Boolean,
    canMerge: Boolean,
    hasMultipleCalls: Boolean,
    onMuteToggle: () -> Unit,
    onDialpadToggle: () -> Unit,
    onAudioRouteClick: () -> Unit,
    onHoldToggle: () -> Unit,
    onAddCall: () -> Unit,
    onMerge: () -> Unit,
    onRttToggle: () -> Unit,
) {
    val audioIcon = when (audioRoute) {
        android.telecom.CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
        android.telecom.CallAudioState.ROUTE_BLUETOOTH -> Icons.Default.Bluetooth
        else -> Icons.Default.Call // earpiece
    }
    val audioLabel = when (audioRoute) {
        android.telecom.CallAudioState.ROUTE_SPEAKER -> "Speaker"
        android.telecom.CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth"
        else -> "Phone"
    }
    val audioActive = audioRoute != android.telecom.CallAudioState.ROUTE_EARPIECE

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Row 1: Mute, Dialpad, Audio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CallControlButton(
                icon = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                label = "Mute",
                isActive = isMuted,
                onClick = onMuteToggle,
            )
            CallControlButton(
                icon = Icons.Default.Dialpad,
                label = "Dialpad",
                isActive = isDtmfActive,
                onClick = onDialpadToggle,
            )
            CallControlButton(
                icon = audioIcon,
                label = audioLabel,
                isActive = audioActive,
                onClick = onAudioRouteClick,
            )
        }
        // Row 2: Hold, Add call, RTT
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            CallControlButton(
                icon = if (isOnHold) Icons.Default.PlayArrow else Icons.Default.Pause,
                label = if (isOnHold) "Resume" else "Hold",
                isActive = isOnHold,
                onClick = onHoldToggle,
            )
            if (canMerge) {
                CallControlButton(
                    icon = Icons.Default.CallMerge,
                    label = "Merge",
                    isActive = false,
                    onClick = onMerge,
                )
            } else {
                CallControlButton(
                    icon = Icons.Default.PersonAdd,
                    label = "Add call",
                    isActive = false,
                    onClick = onAddCall,
                )
            }
            CallControlButton(
                icon = Icons.Default.Tty,
                label = "RTT",
                isActive = isRttActive,
                onClick = onRttToggle,
            )
        }
    }
}

@Composable
private fun CallControlButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = if (isActive) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// region RTT Chat

@Composable
private fun RttChatPanel(
    transcript: List<RttMessage>,
    pendingRemoteText: String,
    pendingLocalText: String,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(transcript.size, pendingRemoteText) {
        if (transcript.isNotEmpty() || pendingRemoteText.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier = modifier.padding(top = 8.dp)) {
        // Transcript
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            reverseLayout = true,
        ) {
            // Pending remote text (shown at top = bottom of reversed list)
            if (pendingRemoteText.isNotEmpty()) {
                item(key = "pending_remote") {
                    RttBubble(
                        text = pendingRemoteText,
                        isRemote = true,
                        isPending = true,
                    )
                }
            }
            items(
                items = transcript.reversed(),
                key = { "${it.timestamp}_${it.isRemote}" },
            ) { message ->
                RttBubble(
                    text = message.text,
                    isRemote = message.isRemote,
                    isPending = false,
                )
            }
        }

        // Input row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = pendingLocalText,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type RTT message") },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onSend) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun RttBubble(text: String, isRemote: Boolean, isPending: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = if (isRemote) Arrangement.Start else Arrangement.End,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = when {
                isPending -> MaterialTheme.colorScheme.surfaceContainerHighest
                isRemote -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.primaryContainer
            },
        ) {
            Text(
                text = text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                color = when {
                    isPending -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    isRemote -> MaterialTheme.colorScheme.onSecondaryContainer
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

// endregion

// region Swipe to Answer

@Composable
private fun SwipeAnswerHandle(
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val thresholdPx = with(density) { 120.dp.toPx() }

    val progress = (offsetY.value / thresholdPx).coerceIn(-1f, 1f)
    val answerGreen = Color(0xFF4CAF50)
    val declineRed = Color(0xFFF44336)
    val neutralColor = MaterialTheme.colorScheme.surfaceContainerHighest

    val handleColor = when {
        progress < 0 -> lerp(neutralColor, answerGreen, abs(progress))
        progress > 0 -> lerp(neutralColor, declineRed, progress)
        else -> neutralColor
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Answer hint (above)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = answerGreen.copy(alpha = 0.5f + abs(progress.coerceAtMost(0f)) * 0.5f),
                modifier = Modifier.size(32.dp),
            )
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = null,
                tint = answerGreen.copy(alpha = 0.3f + abs(progress.coerceAtMost(0f)) * 0.7f),
                modifier = Modifier.size(32.dp).offset(y = (-12).dp),
            )
        }

        // Decline hint (below)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = declineRed.copy(alpha = 0.3f + progress.coerceAtLeast(0f) * 0.7f),
                modifier = Modifier.size(32.dp).offset(y = 12.dp),
            )
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = declineRed.copy(alpha = 0.5f + progress.coerceAtLeast(0f) * 0.5f),
                modifier = Modifier.size(32.dp),
            )
        }

        // Draggable handle
        Box(
            modifier = Modifier
                .offset { IntOffset(0, offsetY.value.roundToInt()) }
                .size(72.dp)
                .clip(CircleShape)
                .background(handleColor)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            val current = offsetY.value
                            when {
                                current < -thresholdPx -> onAnswer()
                                current > thresholdPx -> onDecline()
                                else -> scope.launch {
                                    offsetY.animateTo(0f, spring(dampingRatio = 0.6f))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetY.animateTo(0f, spring(dampingRatio = 0.6f))
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch { offsetY.snapTo(offsetY.value + dragAmount) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = when {
                    progress < -0.5f -> Icons.Default.Call
                    progress > 0.5f -> Icons.Default.CallEnd
                    else -> Icons.Default.Call
                },
                contentDescription = "Swipe to answer or decline",
                tint = when {
                    abs(progress) > 0.5f -> Color.White
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(32.dp),
            )
        }
    }
}

// endregion

// region DTMF Pad

private data class DtmfButton(val digit: Char, val label: String)

private val dtmfButtons = listOf(
    listOf(DtmfButton('1', ""), DtmfButton('2', "ABC"), DtmfButton('3', "DEF")),
    listOf(DtmfButton('4', "GHI"), DtmfButton('5', "JKL"), DtmfButton('6', "MNO")),
    listOf(DtmfButton('7', "PQRS"), DtmfButton('8', "TUV"), DtmfButton('9', "WXYZ")),
    listOf(DtmfButton('*', ""), DtmfButton('0', "+"), DtmfButton('#', "")),
)

@Composable
private fun DtmfPadOverlay(
    onDigit: (Char) -> Unit,
    onDismiss: () -> Unit,
) {
    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_DTMF, 80)
        } catch (_: Exception) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose { toneGenerator?.release() }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            dtmfButtons.forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    row.forEach { button ->
                        Surface(
                            modifier = Modifier
                                .size(72.dp)
                                .clickable {
                                    onDigit(button.digit)
                                    val toneType = dtmfToneType(button.digit)
                                    if (toneType >= 0) toneGenerator?.startTone(toneType, 150)
                                },
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = button.digit.toString(),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                if (button.label.isNotEmpty()) {
                                    Text(
                                        text = button.label,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Close button
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .clickable(onClick = onDismiss),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Dialpad,
                        contentDescription = "Close dialpad",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

private fun dtmfToneType(digit: Char): Int = when (digit) {
    '0' -> ToneGenerator.TONE_DTMF_0
    '1' -> ToneGenerator.TONE_DTMF_1
    '2' -> ToneGenerator.TONE_DTMF_2
    '3' -> ToneGenerator.TONE_DTMF_3
    '4' -> ToneGenerator.TONE_DTMF_4
    '5' -> ToneGenerator.TONE_DTMF_5
    '6' -> ToneGenerator.TONE_DTMF_6
    '7' -> ToneGenerator.TONE_DTMF_7
    '8' -> ToneGenerator.TONE_DTMF_8
    '9' -> ToneGenerator.TONE_DTMF_9
    '*' -> ToneGenerator.TONE_DTMF_S
    '#' -> ToneGenerator.TONE_DTMF_P
    else -> -1
}

// endregion
