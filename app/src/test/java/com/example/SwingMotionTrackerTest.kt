package com.example

import com.example.data.SwingPointCodec
import com.example.model.ClubType
import com.example.model.Handedness
import com.example.model.SwingPhase
import com.example.model.SwingPoint
import com.example.model.TrackingStatus
import com.example.vision.SwingBiomechanicsAnalyzer
import com.example.vision.SwingMotionTracker
import com.example.vision.VideoSwingTracer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwingMotionTrackerTest {

    @Test
    fun biomechanicsAnalyzer_segmentsBackswingTopDownswingImpact_andRespectsTopPause() {
        val raw = mutableListOf<SwingPoint>()
        var t = 1000L
        // Backswing (10 frames)
        for (i in 0..9) {
            val f = i / 9f
            raw += SwingPoint(
                x = 0.50f - 0.22f * f,
                y = 0.72f - 0.50f * f,
                timestampMs = t,
                speed = 0.7f,
                energy = 0.5f,
                phase = SwingPhase.ADDRESS
            )
            t += 40L
        }
        // Pause at the top for 4 frames around y=0.22
        for (i in 0..3) {
            raw += SwingPoint(
                x = 0.28f,
                y = 0.22f + (i * 0.002f),
                timestampMs = t,
                speed = 0.1f,
                energy = 0.15f,
                phase = SwingPhase.ADDRESS
            )
            t += 45L
        }
        // Downswing + Impact (5 frames)
        for (i in 1..5) {
            val f = i / 5f
            val speed = if (i == 5) 2.9f else 1.2f + 0.3f * i
            raw += SwingPoint(
                x = 0.28f + 0.21f * f,
                y = 0.22f + 0.51f * f,
                timestampMs = t,
                speed = speed,
                energy = 0.9f,
                phase = SwingPhase.ADDRESS
            )
            t += 32L
        }
        // Follow-through (4 frames)
        for (i in 1..4) {
            val f = i / 4f
            raw += SwingPoint(
                x = 0.49f + 0.22f * f,
                y = 0.73f - 0.38f * f,
                timestampMs = t,
                speed = 1.5f,
                energy = 0.6f,
                phase = SwingPhase.ADDRESS
            )
            t += 35L
        }

        val result = SwingBiomechanicsAnalyzer.analyze(
            rawPoints = raw,
            clubType = ClubType.IRON_7,
            handedness = Handedness.RIGHT
        )

        assertNotNull(result)
        val (tagged, metrics) = result!!
        // Apex should be at the END of the top pause (index >= 12), not index 9
        assertTrue("Expected apexIndex at end of top pause (>= 12), got ${metrics.apexIndex}", metrics.apexIndex >= 12)
        assertEquals(SwingPhase.TOP, tagged[metrics.apexIndex].phase)
        assertEquals(SwingPhase.IMPACT, tagged[metrics.impactIndex].phase)
        assertTrue("Expected tempo ratio >= 2.0, got ${metrics.tempoRatio}", metrics.tempoRatio >= 2.0f)
    }

    @Test
    fun motionTracker_detectsMovingClubheadBlobAndCompletesSwing() {
        val tracker = SwingMotionTracker().apply {
            clubType = ClubType.DRIVER
            handedness = Handedness.RIGHT
            autoArm = true
            sensitivity = 0.7f
        }

        fun frameWithBlob(cxNorm: Float, cyNorm: Float): FloatArray {
            val grid = FloatArray(SwingMotionTracker.GRID_W * SwingMotionTracker.GRID_H) { 30f }
            val cx = (cxNorm * (SwingMotionTracker.GRID_W - 1)).toInt()
            val cy = (cyNorm * (SwingMotionTracker.GRID_H - 1)).toInt()
            for (dy in -2..2) {
                for (dx in -2..2) {
                    val x = (cx + dx).coerceIn(0, SwingMotionTracker.GRID_W - 1)
                    val y = (cy + dy).coerceIn(0, SwingMotionTracker.GRID_H - 1)
                    grid[y * SwingMotionTracker.GRID_W + x] = 220f
                }
            }
            return grid
        }

        var t = 10_000L
        tracker.processLumaGrid(FloatArray(SwingMotionTracker.GRID_W * SwingMotionTracker.GRID_H) { 30f }, t)
        t += 33L

        for (i in 0..12) {
            val f = i / 12f
            val state = tracker.processLumaGrid(frameWithBlob(0.55f - 0.30f * f, 0.75f - 0.50f * f), t)
            if (i >= 4) {
                assertEquals(TrackingStatus.TRACKING, state.status)
            }
            t += 33L
        }

        for (i in 1..6) {
            val f = i / 6f
            tracker.processLumaGrid(frameWithBlob(0.25f + 0.32f * f, 0.25f + 0.52f * f), t)
            t += 28L
        }

        var captured = false
        for (i in 0..16) {
            val state = tracker.processLumaGrid(FloatArray(SwingMotionTracker.GRID_W * SwingMotionTracker.GRID_H) { 30f }, t)
            if (state.lastCapturedSession != null) {
                captured = true
                assertTrue(state.lastCapturedSession!!.points.size >= 8)
                break
            }
            t += 33L
        }

        assertTrue("Expected tracker to auto-capture completed swing", captured)
    }

    @Test
    fun videoSwingTracer_isDeterministicAndExtractsDistalClubhead() {
        fun frameWithGolferAndClub(clubX: Float, clubY: Float): FloatArray {
            val grid = FloatArray(VideoSwingTracer.GRID_W * VideoSwingTracer.GRID_H) { 25f }
            // Static golfer torso silhouette near center (0.5, 0.55)
            for (y in 26..44) {
                for (x in 21..27) {
                    grid[y * VideoSwingTracer.GRID_W + x] = 110f
                }
            }
            // Bright distal clubhead tip
            val cx = (clubX * (VideoSwingTracer.GRID_W - 1)).toInt()
            val cy = (clubY * (VideoSwingTracer.GRID_H - 1)).toInt()
            for (dy in -2..2) {
                for (dx in -2..2) {
                    val x = (cx + dx).coerceIn(0, VideoSwingTracer.GRID_W - 1)
                    val y = (cy + dy).coerceIn(0, VideoSwingTracer.GRID_H - 1)
                    grid[y * VideoSwingTracer.GRID_W + x] = 235f
                }
            }
            return grid
        }

        val grids = mutableListOf<FloatArray>()
        val timestamps = mutableListOf<Long>()
        var t = 0L

        // Idle lead-in
        repeat(4) {
            grids += frameWithGolferAndClub(0.50f, 0.74f)
            timestamps += t
            t += 35L
        }
        // Backswing
        for (i in 1..12) {
            val f = i / 12f
            grids += frameWithGolferAndClub(0.50f - 0.25f * f, 0.74f - 0.52f * f)
            timestamps += t
            t += 40L
        }
        // Downswing + Impact + Follow-through
        for (i in 1..10) {
            val f = i / 10f
            val x = if (i <= 5) 0.25f + 0.25f * (i / 5f) else 0.50f + 0.24f * ((i - 5) / 5f)
            val y = if (i <= 5) 0.22f + 0.52f * (i / 5f) else 0.74f - 0.40f * ((i - 5) / 5f)
            grids += frameWithGolferAndClub(x, y)
            timestamps += t
            t += 30L
        }

        val run1 = VideoSwingTracer.analyzeFrameSequence(
            grids = grids,
            timestampsMs = timestamps,
            clubType = ClubType.IRON_7,
            handedness = Handedness.RIGHT,
            sensitivity = 0.65f,
            frontCamera = false
        )
        val run2 = VideoSwingTracer.analyzeFrameSequence(
            grids = grids,
            timestampsMs = timestamps,
            clubType = ClubType.IRON_7,
            handedness = Handedness.RIGHT,
            sensitivity = 0.65f,
            frontCamera = false
        )

        assertNotNull(run1)
        assertNotNull(run2)
        assertEquals(run1!!.points, run2!!.points)
        assertEquals(run1.metrics.overallScore, run2.metrics.overallScore)
        assertEquals(run1.metrics.tempoRatio, run2.metrics.tempoRatio)
    }

    @Test
    fun codec_roundTripsSwingPoints() {
        val points = listOf(
            SwingPoint(0.25f, 0.75f, 1000L, 1.2f, 0.8f, SwingPhase.BACKSWING),
            SwingPoint(0.45f, 0.32f, 1250L, 2.6f, 0.95f, SwingPhase.IMPACT)
        )
        val encoded = SwingPointCodec.encodePoints(points)
        val decoded = SwingPointCodec.decodePoints(encoded)
        assertEquals(points, decoded)
    }
}
