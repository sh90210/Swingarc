package com.example.vision

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import com.example.model.ClubType
import com.example.model.Handedness
import com.example.model.SwingPhase
import com.example.model.SwingPoint
import com.example.model.SwingSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Deterministic video-file clubhead tracer implementing background subtraction,
 * golfer silhouette core estimation, distal extremity clubhead extraction,
 * and occlusion-bridge interpolation across a trimmed video window.
 */
object VideoSwingTracer {

    const val GRID_W = 48
    const val GRID_H = 64
    private const val CELL_COUNT = GRID_W * GRID_H

    data class VideoMetadata(
        val durationMs: Long,
        val width: Int,
        val height: Int,
        val rotationDegrees: Int
    ) {
        val uprightWidth: Int get() = if (rotationDegrees == 90 || rotationDegrees == 270) height else width
        val uprightHeight: Int get() = if (rotationDegrees == 90 || rotationDegrees == 270) width else height
        val aspectRatio: Float
            get() = (uprightWidth.toFloat() / uprightHeight.toFloat().coerceAtLeast(1f))
                .coerceIn(0.4f, 2.2f)
    }

    fun readMetadata(videoPath: String): VideoMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoPath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 720
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1280
            val rot = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            VideoMetadata(durationMs = dur, width = w, height = h, rotationDegrees = rot)
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun traceVideoFile(
        videoPath: String,
        trimStartMs: Long,
        trimEndMs: Long,
        clubType: ClubType,
        handedness: Handedness,
        sensitivity: Float,
        frontCamera: Boolean,
        onProgress: (Float, String) -> Unit
    ): SwingSession? = withContext(Dispatchers.Default) {
        val meta = readMetadata(videoPath)
        val safeStart = trimStartMs.coerceIn(0L, meta.durationMs)
        val safeEnd = (if (trimEndMs <= safeStart) meta.durationMs else trimEndMs)
            .coerceIn(safeStart + 200L, max(safeStart + 200L, meta.durationMs))
        val windowMs = (safeEnd - safeStart).coerceAtLeast(400L)

        // Sample up to ~84 frames deterministically across the swing window
        val frameCount = (windowMs / 30L).toInt().coerceIn(24, 84)
        val timestamps = List(frameCount) { idx ->
            safeStart + ((windowMs * idx) / max(1, frameCount - 1))
        }

        val grids = ArrayList<FloatArray>(frameCount)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoPath)
            timestamps.forEachIndexed { idx, tsMs ->
                onProgress(
                    0.05f + 0.65f * (idx.toFloat() / frameCount),
                    "Decoding frame ${idx + 1} / $frameCount"
                )
                val bmp = extractGridBitmap(retriever, tsMs * 1000L)
                if (bmp != null) {
                    grids += bitmapToLumaGrid(bmp)
                    bmp.recycle()
                } else if (grids.isNotEmpty()) {
                    grids += grids.last().copyOf()
                }
            }
        } finally {
            runCatching { retriever.release() }
        }

        if (grids.size < 10) return@withContext null

        onProgress(0.78f, "Subtracting background & extracting clubhead")
        val result = analyzeFrameSequence(
            grids = grids,
            timestampsMs = timestamps.take(grids.size),
            clubType = clubType,
            handedness = handedness,
            sensitivity = sensitivity,
            frontCamera = frontCamera,
            videoPath = videoPath,
            trimStartMs = safeStart,
            trimEndMs = safeEnd,
            videoAspectRatio = meta.aspectRatio
        )
        onProgress(1f, "Complete")
        result
    }

    private fun extractGridBitmap(retriever: MediaMetadataRetriever, timeUs: Long): Bitmap? {
        return try {
            val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                retriever.getScaledFrameAtTime(
                    timeUs,
                    MediaMetadataRetriever.OPTION_CLOSEST,
                    GRID_W * 2,
                    GRID_H * 2
                )
            } else {
                retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            } ?: return null

            if (raw.width == GRID_W && raw.height == GRID_H) {
                raw
            } else {
                val scaled = Bitmap.createScaledBitmap(raw, GRID_W, GRID_H, true)
                if (scaled !== raw) raw.recycle()
                scaled
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun bitmapToLumaGrid(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(CELL_COUNT)
        bitmap.getPixels(pixels, 0, GRID_W, 0, 0, GRID_W, GRID_H)
        val grid = FloatArray(CELL_COUNT)
        for (i in 0 until CELL_COUNT) {
            val c = pixels[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            grid[i] = 0.299f * r + 0.587f * g + 0.114f * b
        }
        return grid
    }

    /**
     * Pure deterministic pipeline testable on JVM without MediaMetadataRetriever:
     * 1. Builds static median background grid across quietest frames (v4 background subtraction).
     * 2. Finds golfer core silhouette & extracts distal protruding clubhead tip + motion peak (v3/v4).
     * 3. Trims idle lead-in / post-finish frames and bridges occluded frames smoothly (v5/v6).
     * 4. Runs SwingBiomechanicsAnalyzer (with top-pause transition fix).
     */
    fun analyzeFrameSequence(
        grids: List<FloatArray>,
        timestampsMs: List<Long>,
        clubType: ClubType,
        handedness: Handedness,
        sensitivity: Float,
        frontCamera: Boolean,
        videoPath: String? = null,
        trimStartMs: Long = timestampsMs.firstOrNull() ?: 0L,
        trimEndMs: Long = timestampsMs.lastOrNull() ?: 0L,
        videoAspectRatio: Float = 9f / 16f
    ): SwingSession? {
        if (grids.size < 10 || grids.size != timestampsMs.size) return null

        val sens = sensitivity.coerceIn(0.2f, 1.0f)
        val fgThreshold = 26f - sens * 14f
        val motThreshold = 22f - sens * 14f

        // Step 1: Deterministic per-cell median background model across sampled frames
        val bgGrid = FloatArray(CELL_COUNT)
        val sampleStep = max(1, grids.size / 15)
        val sampledIndices = (grids.indices step sampleStep).toList()
        val scratch = FloatArray(sampledIndices.size)
        for (cell in 0 until CELL_COUNT) {
            sampledIndices.forEachIndexed { k, frameIdx ->
                scratch[k] = grids[frameIdx][cell]
            }
            scratch.sort()
            bgGrid[cell] = scratch[scratch.size / 2]
        }

        data class RawDetection(
            val x: Float,
            val y: Float,
            val confidence: Float,
            val motionEnergy: Float,
            val ts: Long
        )

        val detections = ArrayList<RawDetection>(grids.size)
        var prevX = 0.5f
        var prevY = 0.72f
        var hasPrev = false

        for (f in grids.indices) {
            val cur = grids[f]
            val prev = if (f > 0) grids[f - 1] else cur

            // Estimate golfer torso silhouette centroid from foreground cells in central band
            var bodySumW = 0f
            var bodySumX = 0f
            var bodySumY = 0f
            for (gy in (GRID_H / 5) until (GRID_H * 4 / 5)) {
                for (gx in (GRID_W / 6) until (GRID_W * 5 / 6)) {
                    val idx = gy * GRID_W + gx
                    val fg = (abs(cur[idx] - bgGrid[idx]) - fgThreshold).coerceAtLeast(0f)
                    if (fg > 0f) {
                        bodySumW += fg
                        bodySumX += gx * fg
                        bodySumY += gy * fg
                    }
                }
            }
            val bodyX = if (bodySumW > 0f) (bodySumX / bodySumW) / (GRID_W - 1f) else 0.5f
            val bodyY = if (bodySumW > 0f) (bodySumY / bodySumW) / (GRID_H - 1f) else 0.55f

            // Score each cell: fast frame motion + distal foreground protrusion away from torso core
            var bestScore = 0f
            var bestGx = GRID_W / 2
            var bestGy = GRID_H / 2
            var maxMotion = 0f
            var activeMotionCells = 0

            val scores = FloatArray(CELL_COUNT)
            for (gy in 1 until GRID_H - 1) {
                val ny = gy.toFloat() / (GRID_H - 1f)
                for (gx in 1 until GRID_W - 1) {
                    val idx = gy * GRID_W + gx
                    val nx = gx.toFloat() / (GRID_W - 1f)

                    val mot = (abs(cur[idx] - prev[idx]) - motThreshold).coerceAtLeast(0f)
                    val fg = (abs(cur[idx] - bgGrid[idx]) - fgThreshold).coerceAtLeast(0f)
                    if (mot > 0f) activeMotionCells++
                    if (mot > maxMotion) maxMotion = mot

                    // Distance from golfer torso center boosts distal clubhead & shaft tip
                    val distFromBody = hypot(nx - bodyX, ny - bodyY)
                    val distalBoost = if (distFromBody > 0.14f) 1f + (distFromBody * 1.45f) else 0.35f
                    val continuityBoost = if (hasPrev) {
                        val dPrev = hypot(nx - prevX, ny - prevY)
                        if (dPrev < 0.22f) 1.35f else 0.72f
                    } else 1f

                    val score = ((mot * 1.15f) + (fg * 0.45f)) * distalBoost * continuityBoost
                    scores[idx] = score
                    if (score > bestScore) {
                        bestScore = score
                        bestGx = gx
                        bestGy = gy
                    }
                }
            }

            val isShake = activeMotionCells > CELL_COUNT * 0.55f
            if (!isShake && bestScore > 8f) {
                var wSum = 0f
                var xSum = 0f
                var ySum = 0f
                val r = 3
                for (dy in -r..r) {
                    val y = (bestGy + dy).coerceIn(1, GRID_H - 2)
                    for (dx in -r..r) {
                        val x = (bestGx + dx).coerceIn(1, GRID_W - 2)
                        val s = scores[y * GRID_W + x]
                        if (s > bestScore * 0.35f) {
                            wSum += s
                            xSum += x * s
                            ySum += y * s
                        }
                    }
                }
                val rx = if (wSum > 0f) (xSum / wSum) / (GRID_W - 1f) else prevX
                val ry = if (wSum > 0f) (ySum / wSum) / (GRID_H - 1f) else prevY
                val conf = (bestScore / 70f).coerceIn(0.15f, 1f)
                prevX = rx
                prevY = ry
                hasPrev = true
                detections += RawDetection(
                    x = rx.coerceIn(0f, 1f),
                    y = ry.coerceIn(0f, 1f),
                    confidence = conf,
                    motionEnergy = (maxMotion / 65f).coerceIn(0f, 1f),
                    ts = timestampsMs[f]
                )
            } else {
                // Occluded / stationary frame (clubhead behind back or still at address)
                detections += RawDetection(
                    x = prevX,
                    y = prevY,
                    confidence = 0f,
                    motionEnergy = 0f,
                    ts = timestampsMs[f]
                )
            }
        }

        // Step 2: Trim inactive idle lead-in / post-finish frames
        val activeThreshold = 0.14f
        val firstActive = detections.indexOfFirst { it.motionEnergy >= activeThreshold || it.confidence >= 0.35f }
        val lastActive = detections.indexOfLast { it.motionEnergy >= activeThreshold || it.confidence >= 0.35f }
        if (firstActive < 0 || lastActive - firstActive < 7) return null

        val startSlice = max(0, firstActive - 2)
        val endSlice = min(detections.lastIndex, lastActive + 2)
        val trimmed = detections.subList(startSlice, endSlice + 1).toMutableList()

        // Step 3: Bridge occluded frames (v5/v6 occlusion interpolation)
        var lastGoodIdx = -1
        for (i in trimmed.indices) {
            if (trimmed[i].confidence > 0.1f) {
                if (lastGoodIdx >= 0 && i - lastGoodIdx > 1) {
                    val pStart = trimmed[lastGoodIdx]
                    val pEnd = trimmed[i]
                    val gap = i - lastGoodIdx
                    for (k in 1 until gap) {
                        val t = k.toFloat() / gap.toFloat()
                        trimmed[lastGoodIdx + k] = trimmed[lastGoodIdx + k].copy(
                            x = pStart.x + (pEnd.x - pStart.x) * t,
                            y = pStart.y + (pEnd.y - pStart.y) * t,
                            confidence = 0.25f
                        )
                    }
                }
                lastGoodIdx = i
            }
        }

        val validPoints = trimmed.filter { it.confidence > 0f }
        if (validPoints.size < 8) return null

        // Compute instantaneous speeds along the trajectory
        val swingPoints = validPoints.mapIndexed { idx, det ->
            val speed = if (idx == 0) {
                0.4f
            } else {
                val prev = validPoints[idx - 1]
                val dt = ((det.ts - prev.ts).coerceAtLeast(12L)) / 1000f
                hypot(det.x - prev.x, det.y - prev.y) / dt
            }
            SwingPoint(
                x = det.x,
                y = det.y,
                timestampMs = det.ts,
                speed = speed,
                energy = det.motionEnergy.coerceAtLeast(det.confidence * 0.6f),
                phase = SwingPhase.ADDRESS
            )
        }

        val analyzed = SwingBiomechanicsAnalyzer.analyze(
            rawPoints = swingPoints,
            clubType = clubType,
            handedness = handedness
        ) ?: return null

        val (taggedPoints, metrics) = analyzed
        return SwingSession(
            createdAtMs = System.currentTimeMillis(),
            clubType = clubType,
            handedness = handedness,
            points = taggedPoints,
            metrics = metrics,
            frontCamera = frontCamera,
            videoPath = videoPath,
            trimStartMs = trimStartMs,
            trimEndMs = trimEndMs,
            videoAspectRatio = videoAspectRatio
        )
    }
}
