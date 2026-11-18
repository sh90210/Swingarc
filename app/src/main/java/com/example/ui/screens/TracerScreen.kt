package com.example.ui.screens

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Architecture
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Architecture
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ClubType
import com.example.model.Handedness
import com.example.model.LoopPhase
import com.example.model.TraceColorScheme
import com.example.model.TracerCaptureMode
import com.example.model.TracerPreferences
import com.example.model.TrackingStatus
import com.example.ui.TracerViewModel
import com.example.ui.components.LiveTopTelemetryBar
import com.example.ui.components.QuickSwingFeedbackCard
import com.example.ui.components.SwingArcCanvas
import com.example.ui.components.SynchronizedVideoTracePlayer
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.BackswingCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.FairwayOutline
import com.example.ui.theme.FairwaySurface
import com.example.ui.theme.FairwaySurfaceElevated
import com.example.ui.theme.ImpactGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SageMuted
import com.example.ui.theme.TurfWhite
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TracerScreen(
    viewModel: TracerViewModel,
    onInspectSwing: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val telemetry by viewModel.telemetry.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val quickReview by viewModel.recentQuickReview.collectAsStateWithLifecycle()
    val ghostSession by viewModel.ghostTrailSession.collectAsStateWithLifecycle()
    val loopState by viewModel.loopState.collectAsStateWithLifecycle()

    var showCalibrationSheet by remember { mutableStateOf(false) }
    var viewAspectRatio by remember { mutableFloatStateOf(9f / 16f) }
    val rootView = LocalView.current

    // Keep screen awake while on the driving range camera tracer
    DisposableEffect(rootView) {
        rootView.keepScreenOn = true
        onDispose { rootView.keepScreenOn = false }
    }

    CameraPermissionGate(modifier = modifier) {
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current
        val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
        var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
        var activeRecording by remember { mutableStateOf<Recording?>(null) }

        // Stop active loop if app goes to background (matches project summary behavior)
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) {
                    runCatching { activeRecording?.stop() }
                    activeRecording = null
                    viewModel.stopHandsFreeLoop()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                runCatching { activeRecording?.stop() }
                analysisExecutor.shutdown()
            }
        }

        val previewView = remember {
            PreviewView(context).apply {
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
        }

        // Bind CameraX use cases cleanly depending on mode (Auto-Trace vs Hands-Free Loop VideoCapture)
        LaunchedEffect(prefs.useFrontCamera, prefs.captureMode, lifecycleOwner) {
            val providerFuture = ProcessCameraProvider.getInstance(context)
            providerFuture.addListener({
                try {
                    val cameraProvider = providerFuture.get()
                    val resolutionSelector = ResolutionSelector.Builder()
                        .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                        .build()

                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build()
                        .also { it.surfaceProvider = previewView.surfaceProvider }

                    val lensFacing = if (prefs.useFrontCamera) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                    val selector = CameraSelector.Builder()
                        .requireLensFacing(lensFacing)
                        .build()

                    cameraProvider.unbindAll()

                    if (prefs.captureMode == TracerCaptureMode.HANDS_FREE_LOOP) {
                        val recorder = Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.HD))
                            .build()
                        val vc = VideoCapture.withOutput(recorder)
                        videoCapture = vc
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, vc)
                    } else {
                        videoCapture = null
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setResolutionSelector(resolutionSelector)
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { analysis ->
                                analysis.setAnalyzer(analysisExecutor) { imageProxy ->
                                    viewModel.analyzeCameraFrame(
                                        imageProxy = imageProxy,
                                        viewAspectRatio = viewAspectRatio
                                    )
                                }
                            }
                        cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageAnalysis)
                    }
                } catch (e: Exception) {
                    Log.e("TracerScreen", "CameraX bind failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }

        fun startTimedVideoCapture(outputFile: File, durationMs: Long, onDone: (Boolean) -> Unit) {
            val vc = videoCapture
            if (vc == null) {
                onDone(false)
                return
            }
            val outputOpts = FileOutputOptions.Builder(outputFile).build()
            var finished = false
            val rec = vc.output
                .prepareRecording(context, outputOpts)
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Finalize -> {
                            activeRecording = null
                            if (!finished) {
                                finished = true
                                onDone(!event.hasError())
                            }
                        }
                    }
                }
            activeRecording = rec
            // Auto-stop after configured duration
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (!finished) {
                    runCatching { rec.stop() }
                }
            }, durationMs)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
                .onSizeChanged { size ->
                    if (size.height > 0 && size.width > 0) {
                        viewAspectRatio = size.width.toFloat() / size.height.toFloat()
                    }
                }
        ) {
            // Live CameraX Preview
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Live Swing Arc Overlay (Auto-Trace mode)
            if (prefs.captureMode == TracerCaptureMode.AUTO_TRACE) {
                val activePoints = when {
                    telemetry.status == TrackingStatus.TRACKING -> telemetry.activeTrail
                    quickReview != null -> quickReview!!.points
                    else -> emptyList()
                }
                val ghostPoints = if (
                    prefs.showTrailGhost &&
                    telemetry.status != TrackingStatus.TRACKING &&
                    quickReview == null
                ) {
                    ghostSession?.points ?: emptyList()
                } else {
                    emptyList()
                }

                SwingArcCanvas(
                    points = activePoints,
                    ghostPoints = ghostPoints,
                    liveCursor = telemetry.liveCursor,
                    showPlaneGuide = prefs.showPlaneGuide,
                    apexIndex = quickReview?.metrics?.apexIndex ?: -1,
                    impactIndex = quickReview?.metrics?.impactIndex ?: -1,
                    clubType = prefs.clubType,
                    handedness = prefs.handedness,
                    colorScheme = prefs.traceColorScheme,
                    modifier = Modifier.fillMaxSize()
                )
            } else if (prefs.showPlaneGuide && loopState.phase != LoopPhase.REPLAYING) {
                SwingArcCanvas(
                    points = emptyList(),
                    showPlaneGuide = true,
                    clubType = prefs.clubType,
                    handedness = prefs.handedness,
                    colorScheme = prefs.traceColorScheme,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Hands-Free Loop Replay Overlay
            if (prefs.captureMode == TracerCaptureMode.HANDS_FREE_LOOP &&
                loopState.phase == LoopPhase.REPLAYING &&
                loopState.currentClipPath != null
            ) {
                val clipPath = loopState.currentClipPath!!
                val traced = loopState.tracedSession
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ObsidianBg.copy(alpha = 0.94f))
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 72.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier.widthIn(max = 600.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "REPLAY ${loopState.currentReplayIndex}/${loopState.totalReplays} • ${loopState.statusText}",
                                style = MaterialTheme.typography.labelMedium,
                                color = ElectricEmerald
                            )
                            if (traced != null && traced.id == 0L) {
                                OutlinedButton(
                                    onClick = { viewModel.saveCurrentLoopSwing() },
                                    border = androidx.compose.foundation.BorderStroke(1.dp, ElectricEmerald),
                                    shape = RoundedCornerShape(999.dp),
                                    modifier = Modifier.testTag("loop_save_swing_button")
                                ) {
                                    Icon(Icons.Filled.BookmarkAdd, contentDescription = null, tint = ElectricEmerald, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Save", color = ElectricEmerald)
                                }
                            }
                        }

                        if (loopState.isTracingClip) {
                            LinearProgressIndicator(
                                progress = { loopState.traceProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = ImpactGold,
                                trackColor = FairwaySurfaceElevated
                            )
                        }

                        SynchronizedVideoTracePlayer(
                            videoPath = clipPath,
                            videoAspectRatio = traced?.videoAspectRatio ?: viewAspectRatio,
                            points = traced?.points ?: emptyList(),
                            clubType = prefs.clubType,
                            handedness = prefs.handedness,
                            colorScheme = prefs.traceColorScheme,
                            trimStartMs = 0L,
                            trimEndMs = loopState.recordingDurationMs,
                            apexIndex = traced?.metrics?.apexIndex ?: -1,
                            impactIndex = traced?.metrics?.impactIndex ?: -1,
                            initialSpeed = prefs.loopReplaySpeed,
                            autoPlay = true,
                            loopEnabledDefault = true,
                            onReplayCompleted = { viewModel.notifyLoopReplayFinished() }
                        )
                    }
                }
            }

            // Center Countdown / Recording HUD for Hands-Free Loop
            if (prefs.captureMode == TracerCaptureMode.HANDS_FREE_LOOP && loopState.active) {
                when (loopState.phase) {
                    LoopPhase.COUNTDOWN -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(136.dp)
                                .clip(CircleShape)
                                .background(ObsidianBg.copy(alpha = 0.78f))
                                .border(3.dp, ImpactGold, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "${loopState.countdownRemainingSec}",
                                    fontSize = 54.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ImpactGold
                                )
                                Text(
                                    text = "ADDRESS",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TurfWhite
                                )
                            }
                        }
                    }
                    LoopPhase.RECORDING -> {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            shape = RoundedCornerShape(999.dp),
                            color = AlertCoral.copy(alpha = 0.9f)
                        ) {
                            val secLeft = ((loopState.recordingDurationMs - loopState.recordingElapsedMs) / 1000f)
                                .coerceAtLeast(0f)
                            Text(
                                text = "● REC  ${String.format("%.1fs", secLeft)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = ObsidianBg,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                            )
                        }
                    }
                    else -> Unit
                }
            }

            // Subtle Top Scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ObsidianBg.copy(alpha = 0.76f), Color.Transparent)
                        )
                    )
            )

            // Top Telemetry + Mode Toggle Bar
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiveTopTelemetryBar(
                    telemetry = telemetry,
                    clubType = prefs.clubType,
                    handedness = prefs.handedness,
                    onClubChipClick = { showCalibrationSheet = true }
                )

                // Quick Mode Toggle Row (Auto-Trace vs Hands-Free Loop)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TracerCaptureMode.entries.forEach { mode ->
                        val selected = prefs.captureMode == mode
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) ElectricEmerald.copy(alpha = 0.22f) else ObsidianBg.copy(alpha = 0.65f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (selected) ElectricEmerald else FairwayOutline
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable { viewModel.setCaptureMode(mode) }
                                .testTag("mode_pill_${mode.name}")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                if (mode == TracerCaptureMode.HANDS_FREE_LOOP) {
                                    Icon(
                                        imageVector = Icons.Filled.Loop,
                                        contentDescription = null,
                                        tint = if (selected) ElectricEmerald else SageMuted,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (selected) ElectricEmerald else TurfWhite
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Overlay: Quick Review + Pro Range Control Deck
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .widthIn(max = 640.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = prefs.captureMode == TracerCaptureMode.AUTO_TRACE &&
                        quickReview != null &&
                        telemetry.status != TrackingStatus.TRACKING,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 }
                ) {
                    quickReview?.let { captured ->
                        QuickSwingFeedbackCard(
                            session = captured,
                            onInspectClick = {
                                viewModel.dismissQuickReview()
                                onInspectSwing(captured.id)
                            },
                            onDismiss = { viewModel.dismissQuickReview() }
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = ObsidianBg.copy(alpha = 0.86f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline),
                    tonalElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ControlDeckIconButton(
                            icon = if (prefs.showPlaneGuide) Icons.Filled.Architecture else Icons.Outlined.Architecture,
                            label = "Plane",
                            active = prefs.showPlaneGuide,
                            onClick = { viewModel.togglePlaneGuide() },
                            testTag = "toggle_plane_guide_button"
                        )

                        ControlDeckIconButton(
                            icon = Icons.Filled.Cameraswitch,
                            label = if (prefs.useFrontCamera) "Front" else "Rear",
                            active = prefs.useFrontCamera,
                            onClick = { viewModel.toggleCameraFacing() },
                            testTag = "flip_camera_button"
                        )

                        val isLoopMode = prefs.captureMode == TracerCaptureMode.HANDS_FREE_LOOP
                        val isActive = if (isLoopMode) loopState.active else telemetry.status == TrackingStatus.TRACKING
                        val shutterColor = if (isActive) AlertCoral else ElectricEmerald

                        Box(
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(shutterColor.copy(alpha = 0.18f))
                                .border(3.dp, shutterColor, CircleShape)
                                .clickable {
                                    if (isLoopMode) {
                                        if (loopState.active) {
                                            runCatching { activeRecording?.stop() }
                                            viewModel.stopHandsFreeLoop()
                                        } else {
                                            viewModel.startHandsFreeLoop(context.filesDir) { file, dur, onDone ->
                                                startTimedVideoCapture(file, dur, onDone)
                                            }
                                        }
                                    } else {
                                        viewModel.toggleManualTrace()
                                    }
                                }
                                .testTag("trace_shutter_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(shutterColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = when {
                                        isActive -> Icons.Filled.Stop
                                        isLoopMode -> Icons.Filled.Loop
                                        else -> Icons.Filled.FiberManualRecord
                                    },
                                    contentDescription = if (isActive) "Stop" else "Start",
                                    tint = ObsidianBg,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        ControlDeckIconButton(
                            icon = Icons.Filled.ClearAll,
                            label = "Clear",
                            active = false,
                            onClick = { viewModel.clearActiveOverlay() },
                            testTag = "clear_trace_button"
                        )

                        ControlDeckIconButton(
                            icon = Icons.Filled.Tune,
                            label = "Setup",
                            active = showCalibrationSheet,
                            onClick = { showCalibrationSheet = true },
                            testTag = "open_setup_button"
                        )
                    }
                }
            }
        }

        if (showCalibrationSheet) {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ModalBottomSheet(
                onDismissRequest = { showCalibrationSheet = false },
                sheetState = sheetState,
                containerColor = FairwaySurface,
                contentColor = TurfWhite
            ) {
                CalibrationSheetContent(
                    prefs = prefs,
                    onClubSelected = { viewModel.setClubType(it) },
                    onHandednessSelected = { viewModel.setHandedness(it) },
                    onColorSchemeSelected = { viewModel.setTraceColorScheme(it) },
                    onSensitivityChanged = { viewModel.setSensitivity(it) },
                    onAutoArmToggle = { viewModel.toggleAutoArm() },
                    onGhostTrailToggle = { viewModel.toggleTrailGhost() },
                    onCountdownSecChange = { viewModel.setCountdownSec(it) },
                    onRecordDurationSecChange = { viewModel.setRecordDurationSec(it) },
                    onReplayCountChange = { viewModel.setReplayCount(it) },
                    onLoopSpeedChange = { viewModel.setLoopReplaySpeed(it) },
                    onBeepsToggle = { viewModel.toggleCountdownBeeps() }
                )
            }
        }
    }
}

@Composable
private fun ControlDeckIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) ElectricEmerald else TurfWhite,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) ElectricEmerald else SageMuted
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalibrationSheetContent(
    prefs: TracerPreferences,
    onClubSelected: (ClubType) -> Unit,
    onHandednessSelected: (Handedness) -> Unit,
    onColorSchemeSelected: (TraceColorScheme) -> Unit,
    onSensitivityChanged: (Float) -> Unit,
    onAutoArmToggle: () -> Unit,
    onGhostTrailToggle: () -> Unit,
    onCountdownSecChange: (Int) -> Unit,
    onRecordDurationSecChange: (Int) -> Unit,
    onReplayCountChange: (Int) -> Unit,
    onLoopSpeedChange: (Float) -> Unit,
    onBeepsToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "Tracker Calibration",
                style = MaterialTheme.typography.headlineSmall,
                color = TurfWhite
            )
            Text(
                text = "Configure club plane, trace palette, sensitivity, and hands-free loop timing.",
                style = MaterialTheme.typography.bodyMedium,
                color = SageMuted
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("CLUB TARGET PLANE", style = MaterialTheme.typography.labelSmall, color = SageMuted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClubType.entries.forEach { club ->
                    FilterChip(
                        selected = prefs.clubType == club,
                        onClick = { onClubSelected(club) },
                        label = { Text("${club.displayName} (${club.idealPlaneAngleDeg.roundToInt()}°)") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricEmerald,
                            containerColor = FairwaySurfaceElevated,
                            labelColor = TurfWhite
                        ),
                        modifier = Modifier.testTag("club_chip_${club.shortCode}")
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("STANCE & TRACE COLORS", style = MaterialTheme.typography.labelSmall, color = SageMuted)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Handedness.entries.forEach { hand ->
                    FilterChip(
                        selected = prefs.handedness == hand,
                        onClick = { onHandednessSelected(hand) },
                        label = { Text(hand.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BackswingCyan.copy(alpha = 0.2f),
                            selectedLabelColor = BackswingCyan,
                            containerColor = FairwaySurfaceElevated,
                            labelColor = TurfWhite
                        )
                    )
                }
                TraceColorScheme.entries.forEach { scheme ->
                    FilterChip(
                        selected = prefs.traceColorScheme == scheme,
                        onClick = { onColorSchemeSelected(scheme) },
                        label = { Text(scheme.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ImpactGold.copy(alpha = 0.2f),
                            selectedLabelColor = ImpactGold,
                            containerColor = FairwaySurfaceElevated,
                            labelColor = TurfWhite
                        )
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("MOTION SENSITIVITY", style = MaterialTheme.typography.labelSmall, color = SageMuted)
                Text("${(prefs.sensitivity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = ImpactGold)
            }
            Slider(
                value = prefs.sensitivity,
                onValueChange = onSensitivityChanged,
                valueRange = 0.2f..1.0f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricEmerald,
                    activeTrackColor = ElectricEmerald,
                    inactiveTrackColor = FairwaySurfaceElevated
                )
            )
        }

        // Hands-Free Loop Timings
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = FairwaySurfaceElevated
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("HANDS-FREE RANGE LOOP", style = MaterialTheme.typography.labelSmall, color = ElectricEmerald)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Countdown", style = MaterialTheme.typography.bodyMedium, color = TurfWhite)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(2, 3, 5).forEach { sec ->
                            FilterChip(
                                selected = prefs.countdownSec == sec,
                                onClick = { onCountdownSecChange(sec) },
                                label = { Text("${sec}s") }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Record Window", style = MaterialTheme.typography.bodyMedium, color = TurfWhite)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(3, 4, 5).forEach { sec ->
                            FilterChip(
                                selected = prefs.recordDurationSec == sec,
                                onClick = { onRecordDurationSecChange(sec) },
                                label = { Text("${sec}s") }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Replay Speed", style = MaterialTheme.typography.bodyMedium, color = TurfWhite)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.25f to "¼×", 0.5f to "½×", 0.75f to "¾×", 1f to "1×").forEach { (spd, lbl) ->
                            FilterChip(
                                selected = kotlin.math.abs(prefs.loopReplaySpeed - spd) < 0.05f,
                                onClick = { onLoopSpeedChange(spd) },
                                label = { Text(lbl) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Replays / Swing", style = MaterialTheme.typography.bodyMedium, color = TurfWhite)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 2, 3).forEach { cnt ->
                            FilterChip(
                                selected = prefs.replayCount == cnt,
                                onClick = { onReplayCountChange(cnt) },
                                label = { Text("${cnt}×") }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Countdown Beeps", style = MaterialTheme.typography.bodyMedium, color = TurfWhite)
                    Switch(
                        checked = prefs.countdownBeeps,
                        onCheckedChange = { onBeepsToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = ElectricEmerald
                        )
                    )
                }
            }
        }

        // Live Auto-Trace Toggles
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = FairwaySurfaceElevated
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Detect Swings", style = MaterialTheme.typography.titleSmall, color = TurfWhite)
                        Text("Automatically triggers trace when clubhead moves", style = MaterialTheme.typography.bodySmall, color = SageMuted)
                    }
                    Switch(
                        checked = prefs.autoArm,
                        onCheckedChange = { onAutoArmToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = ElectricEmerald
                        )
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Previous Swing Ghost", style = MaterialTheme.typography.titleSmall, color = TurfWhite)
                        Text("Keep last swing arc faintly visible", style = MaterialTheme.typography.bodySmall, color = SageMuted)
                    }
                    Switch(
                        checked = prefs.showTrailGhost,
                        onCheckedChange = { onGhostTrailToggle() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = ObsidianBg,
                            checkedTrackColor = ElectricEmerald
                        )
                    )
                }
            }
        }
    }
}
