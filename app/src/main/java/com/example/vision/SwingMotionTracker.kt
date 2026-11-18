package com.example.vision

import androidx.camera.core.ImageProxy
import com.example.model.ClubType
import com.example.model.Handedness
import com.example.model.LiveTelemetryState
import com.example.model.SwingPhase
import com.example.model.SwingPoint
import com.example.model.SwingSession
import com.example.model.TrackingStatus
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * High-speed real-time Y-plane motion tracker designed for CameraX ImageAnalysis frames.
 * Extracts fast-moving clubhead motion centroids while suppressing stationary background
 * and low-velocity body sway, then runs automatic swing-lifecycle segmentation.
 */
class SwingMotionTracker {

    companion object {
        const val GRID_W = 48
        const val GRID_H = 64
        private const val CELL_COUNT = GRID_W * GRID_H
    }

    private val currGrid = FloatArray(CELL_COUNT)
    private val prevGrid = FloatArray(CELL_COUNT)
    private val bgGrid = FloatArray(CELL_COUNT)
    private val diffGrid = FloatArray(CELL_COUNT)

    private var hasInitialized = false
    private var lastFrameTimeMs = 0L
    private var fpsCounterFrames = 0
    private var fpsWindowStartMs = 0L
    private var currentFps = 0

    // Smoothed live cursor state
    private var smoothX = 0.5f
    private var smoothY = 0.65f
    private var velX = 0f
    private var velY = 0f
    private var hasCursor = false

    // Swing lifecycle state
    private var status = TrackingStatus.ARMED
    private var consecutiveMotionFrames = 0
    private var lowMotionSinceMs = 0L
    private var trackingStartedMs = 0L
    private val preTriggerBuffer = ArrayDeque<SwingPoint>(6)
    private val capturedPoints = mutableListOf<SwingPoint>()
    private var peakEnergyInSwing = 0f

    // Configuration
    @Volatile var clubType: ClubType = ClubType.IRON_7
    @Volatile var handedness: Handedness = Handedness.RIGHT
    @Volatile var sensitivity: Float = 0.62f
    @Volatile var autoArm: Boolean = true
    @Volatile var isFrontCamera: Boolean = false

    fun setArmed(armed: Boolean) {
        if (status == TrackingStatus.TRACKING) return
        status = if (armed) TrackingStatus.ARMED else TrackingStatus.IDLE
        consecutiveMotionFrames = 0
        preTriggerBuffer.clear()
    }

    fun startManualTrace(nowMs: Long = System.currentTimeMillis()) {
        capturedPoints.clear()
        preTriggerBuffer.clear()
        status = TrackingStatus.TRACKING
        trackingStartedMs = nowMs
        lowMotionSinceMs = 0L
        peakEnergyInSwing = 0f
    }

    fun stopManualTraceAndAnalyze(nowMs: Long = System.currentTimeMillis()): SwingSession? {
        if (status != TrackingStatus.TRACKING) return null
        return finalizeSwing(nowMs)
    }

    fun resetToReady() {
        capturedPoints.clear()
        preTriggerBuffer.clear()
        consecutiveMotionFrames = 0
        lowMotionSinceMs = 0L
        peakEnergyInSwing = 0f
        status = if (autoArm) TrackingStatus.ARMED else TrackingStatus.IDLE
    }

    fun processImageProxy(
        image: ImageProxy,
        viewAspectRatio: Float,
        nowMs: Long = System.currentTimeMillis()
    ): LiveTelemetryState {
        val plane = image.planes.firstOrNull()
        if (plane == null) {
            image.close()
            return buildSnapshot("Camera frame unavailable")
        }

        val buffer = plane.buffer
        val rowStride = plane.rowStride
        val pixelStride = plane.pixelStride
        val imgW = image.width
        val imgH = image.height
        val rotation = image.imageInfo.rotationDegrees

        sampleYPlaneToUprightGrid(
            buffer = buffer,
            imgW = imgW,
            imgH = imgH,
            rowStride = rowStride,
            pixelStride = pixelStride,
            rotationDegrees = rotation,
            mirrorX = isFrontCamera,
            viewAspectRatio = viewAspectRatio,
            outGrid = currGrid
        )
        image.close()

        return processLumaGrid(currGrid, nowMs)
    }

    /**
     * Pure grid processing entrypoint — also directly callable from JVM unit tests.
     */
    fun processLumaGrid(
        lumaGrid: FloatArray,
        nowMs: Long = System.currentTimeMillis()
    ): LiveTelemetryState {
        require(lumaGrid.size == CELL_COUNT) { "Expected $CELL_COUNT luma samples" }

        if (fpsWindowStartMs == 0L) fpsWindowStartMs = nowMs
        fpsCounterFrames++
        if (nowMs - fpsWindowStartMs >= 800L) {
            currentFps = ((fpsCounterFrames * 1000L) / (nowMs - fpsWindowStartMs).coerceAtLeast(1L)).toInt()
            fpsCounterFrames = 0
            fpsWindowStartMs = nowMs
        }

        if (!hasInitialized) {
            System.arraycopy(lumaGrid, 0, prevGrid, 0, CELL_COUNT)
            System.arraycopy(lumaGrid, 0, bgGrid, 0, CELL_COUNT)
            hasInitialized = true
            lastFrameTimeMs = nowMs
            return buildSnapshot(statusHintFor(status))
        }

        val dtSec = ((nowMs - lastFrameTimeMs).coerceIn(8L, 120L)) / 1000f
        lastFrameTimeMs = nowMs

        // Sensitivity maps 0.2..1.0 -> adaptive noise threshold 26..8
        val sens = sensitivity.coerceIn(0.2f, 1.0f)
        val noiseThreshold = 26f - (sens * 18f)
        val bgAlpha = if (status == TrackingStatus.TRACKING) 0.015f else 0.07f

        var maxCellEnergy = 0f
        var peakGx = GRID_W / 2
        var peakGy = GRID_H / 2
        var activeCells = 0
        var totalMotionEnergy = 0f

        // Compute torso silhouette center from foreground delta so distal clubhead tip stands out
        var bodySumW = 0f
        var bodySumX = 0f
        var bodySumY = 0f
        for (gy in (GRID_H / 5) until (GRID_H * 4 / 5)) {
            val rowOffset = gy * GRID_W
            for (gx in (GRID_W / 6) until (GRID_W * 5 / 6)) {
                val idx = rowOffset + gx
                val fg = (abs(lumaGrid[idx] - bgGrid[idx]) - (noiseThreshold * 1.15f)).coerceAtLeast(0f)
                if (fg > 0f) {
                    bodySumW += fg
                    bodySumX += gx * fg
                    bodySumY += gy * fg
                }
            }
        }
        val bodyNormX = if (bodySumW > 0f) (bodySumX / bodySumW) / (GRID_W - 1f) else 0.5f
        val bodyNormY = if (bodySumW > 0f) (bodySumY / bodySumW) / (GRID_H - 1f) else 0.55f

        // Compute frame-to-frame + background delta with distal clubhead weighting
        for (gy in 1 until GRID_H - 1) {
            val rowOffset = gy * GRID_W
            val ny = gy.toFloat() / (GRID_H - 1f)
            for (gx in 1 until GRID_W - 1) {
                val idx = rowOffset + gx
                val nx = gx.toFloat() / (GRID_W - 1f)
                val cur = lumaGrid[idx]
                val frameDelta = abs(cur - prevGrid[idx])
                val bgDelta = abs(cur - bgGrid[idx])
                val combined = frameDelta * 0.68f + bgDelta * 0.32f
                val rawExcess = (combined - noiseThreshold).coerceAtLeast(0f)

                val distFromBody = hypot(nx - bodyNormX, ny - bodyNormY)
                val distalBoost = if (distFromBody > 0.14f) 1f + (distFromBody * 1.15f) else 0.55f
                val weightedExcess = rawExcess * distalBoost
                diffGrid[idx] = weightedExcess

                if (rawExcess > 0f) {
                    activeCells++
                    totalMotionEnergy += rawExcess
                    if (weightedExcess > maxCellEnergy) {
                        maxCellEnergy = weightedExcess
                        peakGx = gx
                        peakGy = gy
                    }
                }

                prevGrid[idx] = cur
                bgGrid[idx] = bgGrid[idx] * (1f - bgAlpha) + cur * bgAlpha
            }
        }

        // Reject global camera shake when > 55% of frame lights up uniformly
        val isCameraShake = activeCells > (CELL_COUNT * 0.52f)
        val normalizedEnergy = if (isCameraShake) 0f else (maxCellEnergy / 65f).coerceIn(0f, 1f)

        var currentCursor: SwingPoint? = null
        val triggerThreshold = 0.22f - (sens * 0.08f) // ~0.14..0.20

        if (!isCameraShake && maxCellEnergy > 4f && activeCells >= 2) {
            // Focus on local 7x7 window around strongest motion peak (prefers clubhead tip over torso)
            // Also blend toward velocity-predicted neighborhood when tracking fast swing
            val predGx = if (hasCursor) ((smoothX + velX * dtSec) * (GRID_W - 1)).toInt().coerceIn(2, GRID_W - 3) else peakGx
            val predGy = if (hasCursor) ((smoothY + velY * dtSec) * (GRID_H - 1)).toInt().coerceIn(2, GRID_H - 3) else peakGy

            val usePredicted = hasCursor && status == TrackingStatus.TRACKING &&
                hypot((predGx - peakGx).toFloat(), (predGy - peakGy).toFloat()) <= 12f
            val centerGx = if (usePredicted) (peakGx * 0.65f + predGx * 0.35f).toInt() else peakGx
            val centerGy = if (usePredicted) (peakGy * 0.65f + predGy * 0.35f).toInt() else peakGy

            var sumW = 0f
            var sumX = 0f
            var sumY = 0f
            val r = 4
            for (dy in -r..r) {
                val y = (centerGy + dy).coerceIn(1, GRID_H - 2)
                for (dx in -r..r) {
                    val x = (centerGx + dx).coerceIn(1, GRID_W - 2)
                    val e = diffGrid[y * GRID_W + x]
                    if (e > 0f) {
                        val w = e * e
                        sumW += w
                        sumX += x * w
                        sumY += y * w
                    }
                }
            }

            if (sumW > 0f) {
                val rawNormX = (sumX / sumW) / (GRID_W - 1).toFloat()
                val rawNormY = (sumY / sumW) / (GRID_H - 1).toFloat()

                val rawDist = if (hasCursor) hypot(rawNormX - smoothX, rawNormY - smoothY) else 0f
                // Velocity-adaptive smoothing: fast downswing follows tightly, idle stays steady
                val filterAlpha = if (!hasCursor) 1f else (0.35f + rawDist * 3.2f).coerceIn(0.35f, 0.88f)

                val nextX = if (hasCursor) smoothX + (rawNormX - smoothX) * filterAlpha else rawNormX
                val nextY = if (hasCursor) smoothY + (rawNormY - smoothY) * filterAlpha else rawNormY

                val instVx = if (hasCursor) (nextX - smoothX) / dtSec else 0f
                val instVy = if (hasCursor) (nextY - smoothY) / dtSec else 0f
                val speed = hypot(instVx, instVy)

                smoothX = nextX
                smoothY = nextY
                velX = instVx
                velY = instVy
                hasCursor = true

                currentCursor = SwingPoint(
                    x = nextX.coerceIn(0f, 1f),
                    y = nextY.coerceIn(0f, 1f),
                    timestampMs = nowMs,
                    speed = speed,
                    energy = normalizedEnergy,
                    phase = if (status == TrackingStatus.TRACKING) inferLivePhase(nextY) else SwingPhase.ADDRESS
                )
            }
        }

        // Lifecycle transitions
        var completedSession: SwingSession? = null
        var hint = statusHintFor(status)

        when (status) {
            TrackingStatus.IDLE -> {
                if (isCameraShake) hint = "Hold camera steady on tripod or stand"
            }

            TrackingStatus.ARMED -> {
                if (isCameraShake) {
                    consecutiveMotionFrames = 0
                    hint = "Steady the camera to arm swing detection"
                } else if (currentCursor != null && normalizedEnergy >= triggerThreshold) {
                    preTriggerBuffer.addLast(currentCursor)
                    if (preTriggerBuffer.size > 4) preTriggerBuffer.removeFirst()
                    consecutiveMotionFrames++
                    if (consecutiveMotionFrames >= 3) {
                        status = TrackingStatus.TRACKING
                        trackingStartedMs = preTriggerBuffer.firstOrNull()?.timestampMs ?: nowMs
                        lowMotionSinceMs = 0L
                        peakEnergyInSwing = normalizedEnergy
                        capturedPoints.clear()
                        capturedPoints.addAll(preTriggerBuffer)
                        preTriggerBuffer.clear()
                        hint = "Tracing swing arc…"
                    }
                } else {
                    consecutiveMotionFrames = 0
                    if (currentCursor != null) {
                        preTriggerBuffer.addLast(currentCursor)
                        if (preTriggerBuffer.size > 4) preTriggerBuffer.removeFirst()
                    }
                }
            }

            TrackingStatus.TRACKING -> {
                if (currentCursor != null && normalizedEnergy >= (triggerThreshold * 0.48f)) {
                    peakEnergyInSwing = max(peakEnergyInSwing, normalizedEnergy)
                    lowMotionSinceMs = 0L
                    val lastPt = capturedPoints.lastOrNull()
                    if (lastPt == null || hypot(currentCursor.x - lastPt.x, currentCursor.y - lastPt.y) > 0.008f) {
                        capturedPoints.add(currentCursor)
                    }
                    hint = "Tracing ${currentCursor.phase.label.lowercase()}…"
                } else {
                    if (lowMotionSinceMs == 0L) lowMotionSinceMs = nowMs
                    val settledMs = nowMs - lowMotionSinceMs
                    val totalElapsedMs = nowMs - trackingStartedMs
                    if ((settledMs >= 360L && capturedPoints.size >= 8) || totalElapsedMs >= 4200L) {
                        completedSession = finalizeSwing(nowMs)
                        hint = if (completedSession != null) {
                            "Swing captured • ${completedSession.metrics.planeVerdict}"
                        } else {
                            "Short motion ignored — complete a full swing"
                        }
                    }
                }

                // Safety cap
                if (capturedPoints.size >= 240) {
                    completedSession = finalizeSwing(nowMs)
                }
            }

            TrackingStatus.REVIEW -> {
                // Remains in review until UI acknowledges or resets
            }
        }

        return LiveTelemetryState(
            status = status,
            fps = currentFps,
            motionEnergy = normalizedEnergy,
            liveCursor = currentCursor,
            activeTrail = capturedPoints.toList(),
            statusHint = hint,
            lastCapturedSession = completedSession
        )
    }

    private fun inferLivePhase(currentY: Float): SwingPhase {
        if (capturedPoints.size < 3) return SwingPhase.BACKSWING
        val minYSoFar = capturedPoints.minOf { it.y }
        return when {
            currentY <= minYSoFar + 0.03f && velY < 0.08f -> SwingPhase.TOP
            velY < -0.15f -> SwingPhase.BACKSWING
            velY > 0.25f -> SwingPhase.DOWNSWING
            else -> SwingPhase.FOLLOW_THROUGH
        }
    }

    private fun finalizeSwing(nowMs: Long): SwingSession? {
        val raw = capturedPoints.toList()
        capturedPoints.clear()
        preTriggerBuffer.clear()
        consecutiveMotionFrames = 0
        lowMotionSinceMs = 0L

        val analyzed = SwingBiomechanicsAnalyzer.analyze(
            rawPoints = raw,
            clubType = clubType,
            handedness = handedness
        )

        if (analyzed == null) {
            status = if (autoArm) TrackingStatus.ARMED else TrackingStatus.IDLE
            return null
        }

        status = if (autoArm) TrackingStatus.ARMED else TrackingStatus.IDLE
        val (taggedPoints, metrics) = analyzed
        return SwingSession(
            createdAtMs = nowMs,
            clubType = clubType,
            handedness = handedness,
            points = taggedPoints,
            metrics = metrics,
            frontCamera = isFrontCamera
        )
    }

    private fun buildSnapshot(hint: String): LiveTelemetryState = LiveTelemetryState(
        status = status,
        fps = currentFps,
        motionEnergy = 0f,
        liveCursor = null,
        activeTrail = capturedPoints.toList(),
        statusHint = hint,
        lastCapturedSession = null
    )

    private fun statusHintFor(s: TrackingStatus): String = when (s) {
        TrackingStatus.IDLE -> "Tap Trace to capture your next swing"
        TrackingStatus.ARMED -> "Frame club & body at address — swing to auto-trace"
        TrackingStatus.TRACKING -> "Tracing swing arc…"
        TrackingStatus.REVIEW -> "Reviewing captured swing"
    }

    private fun sampleYPlaneToUprightGrid(
        buffer: java.nio.ByteBuffer,
        imgW: Int,
        imgH: Int,
        rowStride: Int,
        pixelStride: Int,
        rotationDegrees: Int,
        mirrorX: Boolean,
        viewAspectRatio: Float,
        outGrid: FloatArray
    ) {
        val uprightW = if (rotationDegrees == 90 || rotationDegrees == 270) imgH else imgW
        val uprightH = if (rotationDegrees == 90 || rotationDegrees == 270) imgW else imgH
        val sensorAspect = uprightW.toFloat() / uprightH.toFloat().coerceAtLeast(1f)
        val targetAspect = viewAspectRatio.coerceIn(0.35f, 2.5f)

        // Match PreviewView ScaleType.FILL_CENTER visible crop
        val (cropX0, cropW, cropY0, cropH) = if (sensorAspect > targetAspect) {
            val wFrac = (targetAspect / sensorAspect).coerceIn(0.1f, 1f)
            QuadFloat((1f - wFrac) * 0.5f, wFrac, 0f, 1f)
        } else {
            val hFrac = (sensorAspect / targetAspect).coerceIn(0.1f, 1f)
            QuadFloat(0f, 1f, (1f - hFrac) * 0.5f, hFrac)
        }

        val maxBufIndex = buffer.limit() - 1
        for (gy in 0 until GRID_H) {
            val viewY = gy.toFloat() / (GRID_H - 1)
            val rotY = cropY0 + viewY * cropH
            for (gx in 0 until GRID_W) {
                val viewX = gx.toFloat() / (GRID_W - 1)
                val unmirroredRotX = cropX0 + viewX * cropW
                val rotX = if (mirrorX) 1f - unmirroredRotX else unmirroredRotX

                // Inverse rotation from upright [rotX, rotY] back to raw sensor [rawX, rawY]
                val rawX: Float
                val rawY: Float
                when (rotationDegrees) {
                    90 -> {
                        rawX = rotY
                        rawY = 1f - rotX
                    }
                    180 -> {
                        rawX = 1f - rotX
                        rawY = 1f - rotY
                    }
                    270 -> {
                        rawX = 1f - rotY
                        rawY = rotX
                    }
                    else -> {
                        rawX = rotX
                        rawY = rotY
                    }
                }

                val px = (rawX * (imgW - 1)).toInt().coerceIn(0, imgW - 1)
                val py = (rawY * (imgH - 1)).toInt().coerceIn(0, imgH - 1)
                val bufIdx = (py * rowStride + px * pixelStride).coerceIn(0, maxBufIndex)
                outGrid[gy * GRID_W + gx] = (buffer.get(bufIdx).toInt() and 0xFF).toFloat()
            }
        }
    }

    private data class QuadFloat(val a: Float, val b: Float, val c: Float, val d: Float)
}
