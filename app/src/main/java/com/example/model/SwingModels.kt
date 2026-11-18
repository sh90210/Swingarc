package com.example.model

enum class ClubType(
    val displayName: String,
    val shortCode: String,
    val idealPlaneAngleDeg: Float,
    val idealTempoRatio: Float,
    val planeToleranceDeg: Float
) {
    DRIVER(
        displayName = "Driver",
        shortCode = "DR",
        idealPlaneAngleDeg = 46f,
        idealTempoRatio = 3.0f,
        planeToleranceDeg = 6f
    ),
    IRON_7(
        displayName = "7-Iron",
        shortCode = "7I",
        idealPlaneAngleDeg = 56f,
        idealTempoRatio = 3.0f,
        planeToleranceDeg = 5.5f
    ),
    WEDGE(
        displayName = "Wedge",
        shortCode = "WG",
        idealPlaneAngleDeg = 62f,
        idealTempoRatio = 2.8f,
        planeToleranceDeg = 5f
    ),
    PUTTER(
        displayName = "Putter",
        shortCode = "PT",
        idealPlaneAngleDeg = 71f,
        idealTempoRatio = 2.0f,
        planeToleranceDeg = 4f
    )
}

enum class Handedness(val label: String, val shortLabel: String) {
    RIGHT("Right-Handed", "RH"),
    LEFT("Left-Handed", "LH")
}

enum class TracerCaptureMode(val label: String) {
    AUTO_TRACE("Live Auto-Trace"),
    HANDS_FREE_LOOP("Hands-Free Loop")
}

enum class TraceColorScheme(val label: String) {
    CLASSIC_RED_GREEN("Red / Green"),
    PRO_CYAN_EMERALD("Cyan / Emerald")
}

enum class SwingPhase(val label: String) {
    ADDRESS("Address"),
    BACKSWING("Backswing"),
    TOP("Top"),
    DOWNSWING("Downswing"),
    IMPACT("Impact"),
    FOLLOW_THROUGH("Follow-Through")
}

data class SwingPoint(
    val x: Float,
    val y: Float,
    val timestampMs: Long,
    val speed: Float,
    val energy: Float,
    val phase: SwingPhase
)

enum class TrackingStatus(val label: String) {
    IDLE("Standby"),
    ARMED("Armed • Ready"),
    TRACKING("Tracing Swing"),
    REVIEW("Swing Captured")
}

enum class LoopPhase {
    IDLE,
    COUNTDOWN,
    RECORDING,
    REPLAYING
}

data class HandsFreeLoopState(
    val active: Boolean = false,
    val phase: LoopPhase = LoopPhase.IDLE,
    val countdownRemainingSec: Int = 0,
    val recordingElapsedMs: Long = 0L,
    val recordingDurationMs: Long = 4000L,
    val currentReplayIndex: Int = 1,
    val totalReplays: Int = 2,
    val isTracingClip: Boolean = false,
    val traceProgress: Float = 0f,
    val currentClipPath: String? = null,
    val tracedSession: SwingSession? = null,
    val statusText: String = "Tap Start Loop for hands-free range practice"
)

enum class FeedbackSeverity {
    OPTIMAL,
    TUNE,
    ALERT
}

data class CoachingTip(
    val title: String,
    val detail: String,
    val severity: FeedbackSeverity
)

data class SwingMetrics(
    val tempoRatio: Float,
    val backswingMs: Long,
    val downswingMs: Long,
    val planeAngleDeg: Float,
    val planeDeviationDeg: Float,
    val peakVelocityScore: Int,
    val arcSmoothnessScore: Int,
    val overallScore: Int,
    val planeVerdict: String,
    val apexIndex: Int,
    val impactIndex: Int,
    val tips: List<CoachingTip>
)

data class SwingSession(
    val id: Long = 0L,
    val createdAtMs: Long,
    val clubType: ClubType,
    val handedness: Handedness,
    val points: List<SwingPoint>,
    val metrics: SwingMetrics,
    val frontCamera: Boolean,
    val videoPath: String? = null,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val videoAspectRatio: Float = 9f / 16f
) {
    val isVideoBacked: Boolean get() = !videoPath.isNullOrBlank()
}

data class TracerPreferences(
    val clubType: ClubType = ClubType.IRON_7,
    val handedness: Handedness = Handedness.RIGHT,
    val sensitivity: Float = 0.62f,
    val useFrontCamera: Boolean = false,
    val showPlaneGuide: Boolean = true,
    val autoArm: Boolean = true,
    val showTrailGhost: Boolean = true,
    val captureMode: TracerCaptureMode = TracerCaptureMode.AUTO_TRACE,
    val traceColorScheme: TraceColorScheme = TraceColorScheme.CLASSIC_RED_GREEN,
    val countdownSec: Int = 3,
    val recordDurationSec: Int = 4,
    val replayCount: Int = 2,
    val loopReplaySpeed: Float = 0.5f,
    val countdownBeeps: Boolean = true
)

data class LiveTelemetryState(
    val status: TrackingStatus = TrackingStatus.ARMED,
    val fps: Int = 0,
    val motionEnergy: Float = 0f,
    val liveCursor: SwingPoint? = null,
    val activeTrail: List<SwingPoint> = emptyList(),
    val statusHint: String = "Frame yourself at address and swing cleanly",
    val lastCapturedSession: SwingSession? = null
)

sealed interface VideoUploadUiState {
    data object Empty : VideoUploadUiState
    data class ClipSelected(
        val localPath: String,
        val displayName: String,
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val trimStartMs: Long,
        val trimEndMs: Long
    ) : VideoUploadUiState
    data class Tracing(
        val localPath: String,
        val displayName: String,
        val progress: Float,
        val stageLabel: String
    ) : VideoUploadUiState
    data class TracedReady(
        val session: SwingSession,
        val displayName: String
    ) : VideoUploadUiState
    data class Error(val message: String) : VideoUploadUiState
}
