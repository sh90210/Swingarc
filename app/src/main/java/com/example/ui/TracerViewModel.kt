package com.example.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.OpenableColumns
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.PreferencesRepository
import com.example.data.SwingRepository
import com.example.model.ClubType
import com.example.model.Handedness
import com.example.model.HandsFreeLoopState
import com.example.model.LiveTelemetryState
import com.example.model.LoopPhase
import com.example.model.SwingSession
import com.example.model.TraceColorScheme
import com.example.model.TracerCaptureMode
import com.example.model.TracerPreferences
import com.example.model.TrackingStatus
import com.example.model.VideoUploadUiState
import com.example.vision.SwingMotionTracker
import com.example.vision.VideoSwingTracer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class TracerViewModel(
    private val swingRepository: SwingRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModel() {

    val motionTracker = SwingMotionTracker()

    val preferences: StateFlow<TracerPreferences> = preferencesRepository.preferencesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = TracerPreferences()
        )

    val allSwings: StateFlow<List<SwingSession>> = swingRepository.allSwings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _telemetry = MutableStateFlow(LiveTelemetryState())
    val telemetry: StateFlow<LiveTelemetryState> = _telemetry.asStateFlow()

    private val _recentQuickReview = MutableStateFlow<SwingSession?>(null)
    val recentQuickReview: StateFlow<SwingSession?> = _recentQuickReview.asStateFlow()

    private val _ghostTrailSession = MutableStateFlow<SwingSession?>(null)
    val ghostTrailSession: StateFlow<SwingSession?> = _ghostTrailSession.asStateFlow()

    private val _uploadState = MutableStateFlow<VideoUploadUiState>(VideoUploadUiState.Empty)
    val uploadState: StateFlow<VideoUploadUiState> = _uploadState.asStateFlow()

    private val _loopState = MutableStateFlow(HandsFreeLoopState())
    val loopState: StateFlow<HandsFreeLoopState> = _loopState.asStateFlow()

    private val _isRetracingDetail = MutableStateFlow(false)
    val isRetracingDetail: StateFlow<Boolean> = _isRetracingDetail.asStateFlow()

    private var loopJob: Job? = null
    private var savedLoopClipPaths = mutableSetOf<String>()
    private var completedReplaysThisCycle = 0

    init {
        viewModelScope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                motionTracker.clubType = prefs.clubType
                motionTracker.handedness = prefs.handedness
                motionTracker.sensitivity = prefs.sensitivity
                motionTracker.autoArm = prefs.autoArm
                motionTracker.isFrontCamera = prefs.useFrontCamera
                if (_telemetry.value.status != TrackingStatus.TRACKING) {
                    motionTracker.setArmed(prefs.autoArm)
                }
            }
        }
    }

    // --- Live CameraX Auto-Trace ---

    fun analyzeCameraFrame(imageProxy: ImageProxy, viewAspectRatio: Float) {
        val state = motionTracker.processImageProxy(
            image = imageProxy,
            viewAspectRatio = viewAspectRatio
        )
        _telemetry.value = state

        val captured = state.lastCapturedSession
        if (captured != null) {
            onLiveSwingCaptured(captured)
        }
    }

    private fun onLiveSwingCaptured(session: SwingSession) {
        viewModelScope.launch {
            val savedId = swingRepository.saveSwing(session)
            val savedSession = session.copy(id = savedId)
            _recentQuickReview.value = savedSession
            _ghostTrailSession.value = savedSession
        }
    }

    fun toggleManualTrace() {
        val current = _telemetry.value.status
        if (current == TrackingStatus.TRACKING) {
            val captured = motionTracker.stopManualTraceAndAnalyze()
            if (captured != null) {
                onLiveSwingCaptured(captured)
            }
            _telemetry.update {
                it.copy(
                    status = if (preferences.value.autoArm) TrackingStatus.ARMED else TrackingStatus.IDLE,
                    activeTrail = emptyList(),
                    statusHint = if (captured != null) {
                        "Swing captured • ${captured.metrics.planeVerdict}"
                    } else {
                        "Short motion ignored — try a full swing"
                    }
                )
            }
        } else {
            _recentQuickReview.value = null
            motionTracker.startManualTrace()
            _telemetry.update {
                it.copy(
                    status = TrackingStatus.TRACKING,
                    activeTrail = emptyList(),
                    statusHint = "Tracing swing arc… tap Stop when finished"
                )
            }
        }
    }

    fun clearActiveOverlay() {
        _recentQuickReview.value = null
        _ghostTrailSession.value = null
        motionTracker.resetToReady()
    }

    fun dismissQuickReview() {
        _recentQuickReview.value = null
    }

    // --- Hands-Free Range Loop (Countdown -> Record MP4 -> Trace -> Slow-Mo Replay -> Loop) ---

    fun startHandsFreeLoop(
        outputDir: File,
        onStartVideoCapture: (File, Long, (Boolean) -> Unit) -> Unit
    ) {
        if (loopJob?.isActive == true) return
        val videoDir = File(outputDir, "swing_videos").apply { mkdirs() }

        loopJob = viewModelScope.launch {
            while (true) {
                val prefs = preferences.value
                val countdownTotal = prefs.countdownSec
                val recordMs = prefs.recordDurationSec * 1000L

                // 1. Countdown phase
                for (sec in countdownTotal downTo 1) {
                    _loopState.value = HandsFreeLoopState(
                        active = true,
                        phase = LoopPhase.COUNTDOWN,
                        countdownRemainingSec = sec,
                        recordingDurationMs = recordMs,
                        totalReplays = prefs.replayCount,
                        statusText = "Get set at address… $sec"
                    )
                    if (prefs.countdownBeeps) playBeep(isFinal = false)
                    delay(1000L)
                }

                if (prefs.countdownBeeps) playBeep(isFinal = true)

                // 2. Recording phase
                val clipFile = File(videoDir, "loop_${System.currentTimeMillis()}.mp4")
                var recordFinished = false
                var recordSucceeded = false

                _loopState.value = HandsFreeLoopState(
                    active = true,
                    phase = LoopPhase.RECORDING,
                    recordingElapsedMs = 0L,
                    recordingDurationMs = recordMs,
                    totalReplays = prefs.replayCount,
                    currentClipPath = clipFile.absolutePath,
                    statusText = "Recording swing…"
                )

                onStartVideoCapture(clipFile, recordMs) { ok ->
                    recordSucceeded = ok
                    recordFinished = true
                }

                val recStart = System.currentTimeMillis()
                while (!recordFinished) {
                    val elapsed = (System.currentTimeMillis() - recStart).coerceAtMost(recordMs)
                    _loopState.update { it.copy(recordingElapsedMs = elapsed) }
                    delay(50L)
                }

                if (!recordSucceeded || !clipFile.exists() || clipFile.length() < 4096L) {
                    runCatching { clipFile.delete() }
                    _loopState.update {
                        it.copy(statusText = "Capture interrupted — retrying countdown…")
                    }
                    delay(1200L)
                    continue
                }

                // 3. Concurrent Trace + Replay phase
                // Rule from summary: if tracing isn't finished yet, keep replaying until at least
                // one full replay finishes WITH the trace visible!
                completedReplaysThisCycle = 0
                _loopState.value = HandsFreeLoopState(
                    active = true,
                    phase = LoopPhase.REPLAYING,
                    currentReplayIndex = 1,
                    totalReplays = prefs.replayCount,
                    isTracingClip = true,
                    traceProgress = 0.05f,
                    currentClipPath = clipFile.absolutePath,
                    tracedSession = null,
                    statusText = "Replaying & tracing clubhead…"
                )

                val traceJob = launch {
                    val traced = VideoSwingTracer.traceVideoFile(
                        videoPath = clipFile.absolutePath,
                        trimStartMs = 0L,
                        trimEndMs = recordMs,
                        clubType = prefs.clubType,
                        handedness = prefs.handedness,
                        sensitivity = prefs.sensitivity,
                        frontCamera = prefs.useFrontCamera,
                        onProgress = { p, _ ->
                            _loopState.update { it.copy(traceProgress = p) }
                        }
                    )
                    _loopState.update {
                        it.copy(
                            isTracingClip = false,
                            traceProgress = 1f,
                            tracedSession = traced,
                            statusText = traced?.metrics?.planeVerdict ?: "Replay ready (no clear arc detected)"
                        )
                    }
                }

                // Wait until both tracing completes AND desired replay cycles finish (at least 1 with trace)
                var sawTracedReplay = false
                while (true) {
                    val stateNow = _loopState.value
                    if (!stateNow.isTracingClip && completedReplaysThisCycle >= 1) {
                        sawTracedReplay = true
                    }
                    if (sawTracedReplay && completedReplaysThisCycle >= prefs.replayCount) {
                        break
                    }
                    // Safety cap (20s max per replay loop)
                    delay(100L)
                }
                traceJob.join()

                // 1s gap between reps (cleanup unsaved temp file unless user saved it)
                delay(1000L)
                if (!savedLoopClipPaths.contains(clipFile.absolutePath)) {
                    runCatching { clipFile.delete() }
                }
            }
        }
    }

    fun notifyLoopReplayFinished() {
        completedReplaysThisCycle++
        val total = _loopState.value.totalReplays
        _loopState.update {
            it.copy(currentReplayIndex = (completedReplaysThisCycle + 1).coerceAtMost(total))
        }
    }

    fun saveCurrentLoopSwing() {
        val state = _loopState.value
        val path = state.currentClipPath ?: return
        val session = state.tracedSession ?: return
        savedLoopClipPaths.add(path)
        viewModelScope.launch {
            val id = swingRepository.saveSwing(session)
            _loopState.update {
                it.copy(
                    tracedSession = session.copy(id = id),
                    statusText = "Saved to Sessions ✓"
                )
            }
        }
    }

    fun stopHandsFreeLoop() {
        loopJob?.cancel()
        loopJob = null
        val path = _loopState.value.currentClipPath
        if (path != null && !savedLoopClipPaths.contains(path)) {
            runCatching { File(path).delete() }
        }
        _loopState.value = HandsFreeLoopState()
    }

    private fun playBeep(isFinal: Boolean) {
        runCatching {
            val tone = if (isFinal) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP
            val tg = ToneGenerator(AudioManager.STREAM_MUSIC, 75)
            tg.startTone(tone, if (isFinal) 180 else 110)
            viewModelScope.launch {
                delay(240L)
                runCatching { tg.release() }
            }
        }
    }

    // --- Upload & Trace Swing Video Menu ---

    fun importPickedVideo(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uploadState.value = VideoUploadUiState.Tracing(
                localPath = "",
                displayName = "Importing video…",
                progress = 0.05f,
                stageLabel = "Copying video to app workspace…"
            )
            try {
                val videoDir = File(context.filesDir, "swing_videos").apply { mkdirs() }
                val destFile = File(videoDir, "upload_${System.currentTimeMillis()}.mp4")
                val displayName = resolveDisplayName(context, uri) ?: "Swing Clip.mp4"

                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: error("Unable to open selected video.")
                }

                val meta = withContext(Dispatchers.IO) {
                    VideoSwingTracer.readMetadata(destFile.absolutePath)
                }

                if (meta.durationMs < 300L) {
                    runCatching { destFile.delete() }
                    _uploadState.value = VideoUploadUiState.Error("Selected video is too short or unreadable.")
                    return@launch
                }

                val defaultEnd = if (meta.durationMs > 7000L) 6000L else meta.durationMs
                _uploadState.value = VideoUploadUiState.ClipSelected(
                    localPath = destFile.absolutePath,
                    displayName = displayName,
                    durationMs = meta.durationMs,
                    width = meta.uprightWidth,
                    height = meta.uprightHeight,
                    trimStartMs = 0L,
                    trimEndMs = defaultEnd
                )
            } catch (e: Exception) {
                _uploadState.value = VideoUploadUiState.Error(
                    e.message ?: "Failed to import swing video."
                )
            }
        }
    }

    fun updateUploadTrim(startMs: Long, endMs: Long) {
        val current = _uploadState.value as? VideoUploadUiState.ClipSelected ?: return
        val safeStart = startMs.coerceIn(0L, (current.durationMs - 400L).coerceAtLeast(0L))
        val safeEnd = endMs.coerceIn(safeStart + 400L, current.durationMs)
        _uploadState.value = current.copy(trimStartMs = safeStart, trimEndMs = safeEnd)
    }

    fun traceSelectedUpload() {
        val selected = _uploadState.value as? VideoUploadUiState.ClipSelected ?: return
        val prefs = preferences.value
        viewModelScope.launch {
            _uploadState.value = VideoUploadUiState.Tracing(
                localPath = selected.localPath,
                displayName = selected.displayName,
                progress = 0.04f,
                stageLabel = "Preparing video frames…"
            )

            val session = VideoSwingTracer.traceVideoFile(
                videoPath = selected.localPath,
                trimStartMs = selected.trimStartMs,
                trimEndMs = selected.trimEndMs,
                clubType = prefs.clubType,
                handedness = prefs.handedness,
                sensitivity = prefs.sensitivity,
                frontCamera = false,
                onProgress = { progress, stage ->
                    _uploadState.value = VideoUploadUiState.Tracing(
                        localPath = selected.localPath,
                        displayName = selected.displayName,
                        progress = progress,
                        stageLabel = stage
                    )
                }
            )

            if (session == null) {
                _uploadState.value = VideoUploadUiState.Error(
                    "Could not lock onto a full swing in this window. Try trimming tightly around the 2–5 second swing or raising sensitivity."
                )
            } else {
                val id = swingRepository.saveSwing(session)
                _uploadState.value = VideoUploadUiState.TracedReady(
                    session = session.copy(id = id),
                    displayName = selected.displayName
                )
            }
        }
    }

    fun resetUploadToClipSelect(localPath: String, displayName: String) {
        viewModelScope.launch {
            val meta = withContext(Dispatchers.IO) { VideoSwingTracer.readMetadata(localPath) }
            _uploadState.value = VideoUploadUiState.ClipSelected(
                localPath = localPath,
                displayName = displayName,
                durationMs = meta.durationMs,
                width = meta.uprightWidth,
                height = meta.uprightHeight,
                trimStartMs = 0L,
                trimEndMs = if (meta.durationMs > 7000L) 6000L else meta.durationMs
            )
        }
    }

    fun clearUploadSelection() {
        val cur = _uploadState.value
        if (cur is VideoUploadUiState.ClipSelected) {
            runCatching { File(cur.localPath).delete() }
        }
        _uploadState.value = VideoUploadUiState.Empty
    }

    fun retraceSavedSwing(session: SwingSession) {
        val path = session.videoPath ?: return
        if (_isRetracingDetail.value) return
        val prefs = preferences.value
        viewModelScope.launch {
            _isRetracingDetail.value = true
            try {
                val updated = VideoSwingTracer.traceVideoFile(
                    videoPath = path,
                    trimStartMs = session.trimStartMs,
                    trimEndMs = session.trimEndMs,
                    clubType = prefs.clubType,
                    handedness = prefs.handedness,
                    sensitivity = prefs.sensitivity,
                    frontCamera = session.frontCamera,
                    onProgress = { _, _ -> }
                )
                if (updated != null) {
                    swingRepository.saveSwing(updated.copy(id = session.id, createdAtMs = session.createdAtMs))
                }
            } finally {
                _isRetracingDetail.value = false
            }
        }
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
        }.getOrNull()
    }

    // --- Detail / History & Preferences ---

    fun observeSwingById(id: Long): Flow<SwingSession?> = swingRepository.observeSwing(id)

    fun setClubType(clubType: ClubType) {
        viewModelScope.launch { preferencesRepository.setClubType(clubType) }
    }

    fun setHandedness(handedness: Handedness) {
        viewModelScope.launch { preferencesRepository.setHandedness(handedness) }
    }

    fun setSensitivity(sensitivity: Float) {
        viewModelScope.launch { preferencesRepository.setSensitivity(sensitivity) }
    }

    fun toggleCameraFacing() {
        viewModelScope.launch {
            preferencesRepository.setUseFrontCamera(!preferences.value.useFrontCamera)
        }
    }

    fun togglePlaneGuide() {
        viewModelScope.launch {
            preferencesRepository.setShowPlaneGuide(!preferences.value.showPlaneGuide)
        }
    }

    fun toggleAutoArm() {
        viewModelScope.launch {
            preferencesRepository.setAutoArm(!preferences.value.autoArm)
        }
    }

    fun toggleTrailGhost() {
        viewModelScope.launch {
            preferencesRepository.setShowTrailGhost(!preferences.value.showTrailGhost)
        }
    }

    fun setCaptureMode(mode: TracerCaptureMode) {
        if (mode != TracerCaptureMode.HANDS_FREE_LOOP) {
            stopHandsFreeLoop()
        }
        viewModelScope.launch { preferencesRepository.setCaptureMode(mode) }
    }

    fun setTraceColorScheme(scheme: TraceColorScheme) {
        viewModelScope.launch { preferencesRepository.setTraceColorScheme(scheme) }
    }

    fun setCountdownSec(sec: Int) {
        viewModelScope.launch { preferencesRepository.setCountdownSec(sec) }
    }

    fun setRecordDurationSec(sec: Int) {
        viewModelScope.launch { preferencesRepository.setRecordDurationSec(sec) }
    }

    fun setReplayCount(count: Int) {
        viewModelScope.launch { preferencesRepository.setReplayCount(count) }
    }

    fun setLoopReplaySpeed(speed: Float) {
        viewModelScope.launch { preferencesRepository.setLoopReplaySpeed(speed) }
    }

    fun toggleCountdownBeeps() {
        viewModelScope.launch { preferencesRepository.setCountdownBeeps(!preferences.value.countdownBeeps) }
    }

    fun deleteSwing(id: Long) {
        viewModelScope.launch { swingRepository.deleteSwing(id) }
    }

    fun clearAllSwings() {
        viewModelScope.launch {
            swingRepository.clearAllSwings()
            _ghostTrailSession.value = null
            _recentQuickReview.value = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopHandsFreeLoop()
    }
}

class TracerViewModelFactory(
    private val swingRepository: SwingRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TracerViewModel::class.java)) {
            return TracerViewModel(swingRepository, preferencesRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}
