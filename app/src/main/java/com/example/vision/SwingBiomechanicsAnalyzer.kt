package com.example.vision

import com.example.model.ClubType
import com.example.model.CoachingTip
import com.example.model.FeedbackSeverity
import com.example.model.Handedness
import com.example.model.SwingMetrics
import com.example.model.SwingPhase
import com.example.model.SwingPoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure biomechanical analyzer that segments raw tracked camera/video points into
 * Address / Backswing / Top (end-of-pause) / Downswing / Impact / Follow-Through
 * and computes deterministic tempo, swing-plane angle, plane shift, smoothness,
 * and coaching feedback.
 */
object SwingBiomechanicsAnalyzer {

    fun analyze(
        rawPoints: List<SwingPoint>,
        clubType: ClubType,
        handedness: Handedness
    ): Pair<List<SwingPoint>, SwingMetrics>? {
        if (rawPoints.size < 8) return null

        val minX = rawPoints.minOf { it.x }
        val maxX = rawPoints.maxOf { it.x }
        val minY = rawPoints.minOf { it.y }
        val maxY = rawPoints.maxOf { it.y }
        val span = hypot(maxX - minX, maxY - minY)
        if (span < 0.14f) return null

        val smoothed = smoothTrajectory(rawPoints)

        // 1. Find the highest elevation region (smallest screen Y) in the first 75% of the swing
        val apexSearchEnd = max(3, (smoothed.size * 0.75f).toInt()).coerceAtMost(smoothed.lastIndex - 2)
        var peakTopIdx = 1
        var bestY = Float.MAX_VALUE
        for (i in 1..apexSearchEnd) {
            if (smoothed[i].y < bestY) {
                bestY = smoothed[i].y
                peakTopIdx = i
            }
        }

        // 2. Top-Pause Transition Fix (v5 insight):
        // Keep the top/backswing phase until the END of any pause at the top,
        // switching to Downswing right when downward acceleration starts.
        var apexIdx = peakTopIdx
        val pauseBandY = bestY + 0.025f
        for (i in peakTopIdx..apexSearchEnd) {
            val pt = smoothed[i]
            if (pt.y > pauseBandY) break
            apexIdx = i
            val nextPt = smoothed.getOrNull(i + 1)
            if (nextPt != null && (nextPt.y - pt.y) > 0.022f) {
                break
            }
        }

        // 3. Locate Impact as peak velocity point in the lower hitting zone after apex
        var impactIdx = (apexIdx + 1).coerceAtMost(smoothed.lastIndex)
        var peakDownswingScore = -1f
        val addressY = smoothed.first().y
        for (i in (apexIdx + 1) until smoothed.size) {
            val pt = smoothed[i]
            val lowerZoneBonus = if (pt.y >= addressY - 0.12f) 1.3f else 0.8f
            val score = pt.speed * lowerZoneBonus
            if (score > peakDownswingScore) {
                peakDownswingScore = score
                impactIdx = i
            }
        }
        if (impactIdx <= apexIdx) {
            impactIdx = (apexIdx + 1).coerceAtMost(smoothed.lastIndex)
        }

        val taggedPoints = smoothed.mapIndexed { idx, pt ->
            val phase = when {
                idx == 0 -> SwingPhase.ADDRESS
                idx < apexIdx -> SwingPhase.BACKSWING
                idx == apexIdx -> SwingPhase.TOP
                idx < impactIdx -> SwingPhase.DOWNSWING
                idx == impactIdx -> SwingPhase.IMPACT
                else -> SwingPhase.FOLLOW_THROUGH
            }
            pt.copy(phase = phase)
        }

        val startTime = taggedPoints.first().timestampMs
        val apexTime = taggedPoints[apexIdx].timestampMs
        val impactTime = taggedPoints[impactIdx].timestampMs

        val backswingMs = max(180L, apexTime - startTime)
        val downswingMs = max(90L, impactTime - apexTime)
        val tempoRatio = (backswingMs.toFloat() / downswingMs.toFloat()).coerceIn(1.1f, 5.5f)

        val addressPt = taggedPoints.first()
        val apexPt = taggedPoints[apexIdx]
        val impactPt = taggedPoints[impactIdx]

        val backswingAngle = computePlaneAngleDeg(addressPt, apexPt)
        val downswingAngle = computePlaneAngleDeg(impactPt, apexPt)
        val planeAngleDeg = ((backswingAngle * 0.45f) + (downswingAngle * 0.55f)).coerceIn(15f, 85f)

        val planeShiftDeg = downswingAngle - backswingAngle
        val clubDiffDeg = planeAngleDeg - clubType.idealPlaneAngleDeg

        val arcSmoothnessScore = computeSmoothnessScore(taggedPoints)
        val maxSpeed = taggedPoints.maxOf { it.speed }
        val peakVelocityScore = (maxSpeed * 38f).roundToInt().coerceIn(35, 138)

        val tempoDiff = abs(tempoRatio - clubType.idealTempoRatio)
        val tempoPenalty = (tempoDiff * 14f).coerceAtMost(28f)
        val planePenalty = (abs(planeShiftDeg) * 1.4f + abs(clubDiffDeg) * 0.7f).coerceAtMost(32f)
        val smoothBonus = (arcSmoothnessScore - 70) * 0.25f
        val overallScore = (92f - tempoPenalty - planePenalty + smoothBonus)
            .roundToInt()
            .coerceIn(42, 99)

        val planeVerdict = when {
            abs(planeShiftDeg) <= 4.5f && abs(clubDiffDeg) <= clubType.planeToleranceDeg ->
                "On-Plane Slot"
            planeShiftDeg > 4.5f ->
                "Steep / Over-the-Top (+${planeShiftDeg.roundToInt()}°)"
            planeShiftDeg < -4.5f ->
                "Shallow / Inside (${planeShiftDeg.roundToInt()}°)"
            clubDiffDeg > clubType.planeToleranceDeg ->
                "Upright vs ${clubType.shortCode} Target"
            else ->
                "Flat vs ${clubType.shortCode} Target"
        }

        val tips = buildCoachingTips(
            clubType = clubType,
            handedness = handedness,
            tempoRatio = tempoRatio,
            planeAngleDeg = planeAngleDeg,
            planeShiftDeg = planeShiftDeg,
            smoothness = arcSmoothnessScore,
            impactIdx = impactIdx,
            totalPoints = taggedPoints.size
        )

        val metrics = SwingMetrics(
            tempoRatio = (tempoRatio * 10f).roundToInt() / 10f,
            backswingMs = backswingMs,
            downswingMs = downswingMs,
            planeAngleDeg = (planeAngleDeg * 10f).roundToInt() / 10f,
            planeDeviationDeg = (planeShiftDeg * 10f).roundToInt() / 10f,
            peakVelocityScore = peakVelocityScore,
            arcSmoothnessScore = arcSmoothnessScore,
            overallScore = overallScore,
            planeVerdict = planeVerdict,
            apexIndex = apexIdx,
            impactIndex = impactIdx,
            tips = tips
        )

        return taggedPoints to metrics
    }

    private fun smoothTrajectory(points: List<SwingPoint>): List<SwingPoint> {
        if (points.size < 3) return points
        return points.mapIndexed { idx, pt ->
            if (idx == 0 || idx == points.lastIndex) {
                pt
            } else {
                val prev = points[idx - 1]
                val next = points[idx + 1]
                pt.copy(
                    x = prev.x * 0.22f + pt.x * 0.56f + next.x * 0.22f,
                    y = prev.y * 0.22f + pt.y * 0.56f + next.y * 0.22f
                )
            }
        }
    }

    private fun computePlaneAngleDeg(lower: SwingPoint, upper: SwingPoint): Float {
        val dx = abs(upper.x - lower.x).coerceAtLeast(0.02f)
        val dy = abs(lower.y - upper.y).coerceAtLeast(0.01f)
        val angleRad = atan2(dy, dx)
        return Math.toDegrees(angleRad.toDouble()).toFloat().coerceIn(15f, 85f)
    }

    private fun computeSmoothnessScore(points: List<SwingPoint>): Int {
        if (points.size < 5) return 75
        var sharpTurns = 0
        var totalDeviation = 0f
        for (i in 1 until points.lastIndex) {
            val v1x = points[i].x - points[i - 1].x
            val v1y = points[i].y - points[i - 1].y
            val v2x = points[i + 1].x - points[i].x
            val v2y = points[i + 1].y - points[i].y
            val m1 = hypot(v1x, v1y)
            val m2 = hypot(v2x, v2y)
            if (m1 > 0.004f && m2 > 0.004f) {
                val dot = ((v1x * v2x) + (v1y * v2y)) / (m1 * m2)
                val clamped = dot.coerceIn(-1f, 1f)
                if (clamped < 0.25f) sharpTurns++
                totalDeviation += (1f - clamped)
            }
        }
        val avgDev = totalDeviation / (points.size - 2).coerceAtLeast(1)
        val penalty = (avgDev * 58f) + (max(0, sharpTurns - 2) * 4f)
        return (98f - penalty).roundToInt().coerceIn(48, 98)
    }

    private fun buildCoachingTips(
        clubType: ClubType,
        handedness: Handedness,
        tempoRatio: Float,
        planeAngleDeg: Float,
        planeShiftDeg: Float,
        smoothness: Int,
        impactIdx: Int,
        totalPoints: Int
    ): List<CoachingTip> {
        val tips = mutableListOf<CoachingTip>()
        val ideal = clubType.idealPlaneAngleDeg
        val shiftAbs = abs(planeShiftDeg)

        when {
            planeShiftDeg > 5f -> tips += CoachingTip(
                title = "Shallow the Downswing Transition",
                detail = "Downswing tracked +${shiftAbs.roundToInt()}° steeper than your backswing (${handedness.shortLabel}). Let your hands drop into the slot before rotating your chest.",
                severity = FeedbackSeverity.ALERT
            )
            planeShiftDeg < -5f -> tips += CoachingTip(
                title = "Keep Arms Connected on Downswing",
                detail = "Path dropped ${shiftAbs.roundToInt()}° under the backswing plane. Maintain torso rotation through impact to avoid getting stuck inside.",
                severity = FeedbackSeverity.TUNE
            )
            abs(planeAngleDeg - ideal) > clubType.planeToleranceDeg -> {
                val direction = if (planeAngleDeg > ideal) "upright" else "flat"
                tips += CoachingTip(
                    title = "Adjust ${clubType.displayName} Plane Angle",
                    detail = "Measured ${planeAngleDeg.roundToInt()}° ($direction vs ${ideal.roundToInt()}° target). Check ball distance and spine tilt at address.",
                    severity = FeedbackSeverity.TUNE
                )
            }
            else -> tips += CoachingTip(
                title = "Locked-In ${clubType.displayName} Swing Plane",
                detail = "Backswing and downswing matched within ${shiftAbs.roundToInt()}° at a ${planeAngleDeg.roundToInt()}° plane angle — clean repeatable slot.",
                severity = FeedbackSeverity.OPTIMAL
            )
        }

        val targetTempo = clubType.idealTempoRatio
        val tempoDiff = tempoRatio - targetTempo
        when {
            tempoDiff < -0.55f -> tips += CoachingTip(
                title = "Rushed Transition (${String.format("%.1f", tempoRatio)}:1)",
                detail = "Give the clubhead a fraction more time at the top toward ${String.format("%.1f", targetTempo)}:1 so your lower body can lead.",
                severity = FeedbackSeverity.TUNE
            )
            tempoDiff > 0.8f -> tips += CoachingTip(
                title = "Accelerate Through Impact (${String.format("%.1f", tempoRatio)}:1)",
                detail = "Backswing pacing is unhurried; commit to an assertive downswing release past the ball.",
                severity = FeedbackSeverity.TUNE
            )
            else -> tips += CoachingTip(
                title = "Tour-Grade Tempo (${String.format("%.1f", tempoRatio)}:1)",
                detail = "Smooth rhythm near the ${String.format("%.1f", targetTempo)}:1 ${clubType.displayName} benchmark.",
                severity = FeedbackSeverity.OPTIMAL
            )
        }

        val impactRatio = impactIdx.toFloat() / max(1, totalPoints - 1)
        when {
            smoothness < 70 -> tips += CoachingTip(
                title = "Smooth Out Mid-Swing Hand Action",
                detail = "Minor path wobble detected (${smoothness}/100). Keep grip pressure light and swing as one connected unit.",
                severity = FeedbackSeverity.TUNE
            )
            impactRatio > 0.85f -> tips += CoachingTip(
                title = "Extend Through Follow-Through",
                detail = "Peak speed arrived late in frame — keep the tracer capturing a full balanced finish.",
                severity = FeedbackSeverity.OPTIMAL
            )
            else -> tips += CoachingTip(
                title = "Clean Release Through Impact",
                detail = "Arc smoothness scored ${smoothness}/100 with peak clubhead speed peaking right through the hitting zone.",
                severity = FeedbackSeverity.OPTIMAL
            )
        }

        return tips
    }
}
