package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.model.ClubType
import com.example.model.Handedness
import com.example.model.SwingPhase
import com.example.model.SwingPoint
import com.example.model.TraceColorScheme
import com.example.ui.theme.BackswingCyan
import com.example.ui.theme.BackswingRed
import com.example.ui.theme.DownswingGreen
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.FollowThroughMint
import com.example.ui.theme.ImpactGold
import com.example.ui.theme.TurfWhite
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun SwingArcCanvas(
    points: List<SwingPoint>,
    modifier: Modifier = Modifier,
    ghostPoints: List<SwingPoint> = emptyList(),
    liveCursor: SwingPoint? = null,
    showPlaneGuide: Boolean = false,
    showDetectedPlanes: Boolean = false,
    showTrace: Boolean = true,
    apexIndex: Int = -1,
    impactIndex: Int = -1,
    clubType: ClubType = ClubType.IRON_7,
    handedness: Handedness = Handedness.RIGHT,
    colorScheme: TraceColorScheme = TraceColorScheme.CLASSIC_RED_GREEN,
    playbackProgress: Float = 1f,
    currentVideoTimeMs: Long? = null,
    isMiniPreview: Boolean = false
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        if (showPlaneGuide && !isMiniPreview) {
            drawPlaneGuide(
                w = w,
                h = h,
                clubType = clubType,
                handedness = handedness
            )
        }

        if (!showTrace) {
            liveCursor?.let { drawLiveReticle(it, w, h, colorScheme) }
            return@Canvas
        }

        if (ghostPoints.size >= 2 && !isMiniPreview) {
            drawTrajectoryPath(
                points = ghostPoints,
                w = w,
                h = h,
                baseStrokePx = 5f,
                alphaScale = 0.24f,
                glowEnabled = false,
                colorScheme = colorScheme
            )
        }

        val visiblePoints = when {
            points.isEmpty() -> emptyList()
            currentVideoTimeMs != null -> sliceByTimestamp(points, currentVideoTimeMs)
            playbackProgress >= 0.999f -> points
            else -> {
                val count = (points.size * playbackProgress.coerceIn(0f, 1f))
                    .roundToInt()
                    .coerceAtLeast(1)
                points.take(count)
            }
        }

        if (visiblePoints.size >= 2) {
            drawTrajectoryPath(
                points = visiblePoints,
                w = w,
                h = h,
                baseStrokePx = if (isMiniPreview) 4.5f else 9f,
                alphaScale = 1f,
                glowEnabled = true,
                colorScheme = colorScheme
            )
        }

        if (showDetectedPlanes && points.size >= 6 && apexIndex in 1 until points.lastIndex) {
            val addressPt = points.first()
            val apexPt = points[apexIndex]
            val impactPt = if (impactIndex in (apexIndex + 1)..points.lastIndex) {
                points[impactIndex]
            } else {
                points.last()
            }

            val backColor = phaseColor(SwingPhase.BACKSWING, colorScheme)
            val downColor = phaseColor(SwingPhase.DOWNSWING, colorScheme)

            drawLine(
                color = backColor.copy(alpha = 0.8f),
                start = Offset(addressPt.x * w, addressPt.y * h),
                end = Offset(apexPt.x * w, apexPt.y * h),
                strokeWidth = 3.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
            )
            drawLine(
                color = downColor.copy(alpha = 0.9f),
                start = Offset(apexPt.x * w, apexPt.y * h),
                end = Offset(impactPt.x * w, impactPt.y * h),
                strokeWidth = 3.5f
            )
        }

        if (!isMiniPreview && visiblePoints.isNotEmpty()) {
            if (apexIndex in 0 until visiblePoints.size) {
                val apex = visiblePoints[apexIndex]
                val center = Offset(apex.x * w, apex.y * h)
                val apexColor = phaseColor(SwingPhase.BACKSWING, colorScheme)
                drawCircle(color = apexColor.copy(alpha = 0.25f), radius = 20f, center = center)
                drawCircle(color = apexColor, radius = 8.5f, center = center, style = Stroke(width = 3.5f))
                drawCircle(color = TurfWhite, radius = 3.5f, center = center)
            }

            if (impactIndex in 0 until visiblePoints.size) {
                val impact = visiblePoints[impactIndex]
                val center = Offset(impact.x * w, impact.y * h)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(ImpactGold.copy(alpha = 0.55f), Color.Transparent),
                        center = center,
                        radius = 36f
                    ),
                    radius = 36f,
                    center = center
                )
                drawCircle(color = ImpactGold, radius = 11.5f, center = center, style = Stroke(width = 4f))
                drawCircle(color = TurfWhite, radius = 4f, center = center)
            }
        }

        val activeHead = when {
            isMiniPreview -> null
            currentVideoTimeMs != null && visiblePoints.isNotEmpty() -> visiblePoints.last()
            playbackProgress < 0.999f && visiblePoints.isNotEmpty() -> visiblePoints.last()
            else -> liveCursor
        }
        if (activeHead != null) {
            drawLiveReticle(activeHead, w, h, colorScheme)
        }
    }
}

private fun sliceByTimestamp(points: List<SwingPoint>, videoMs: Long): List<SwingPoint> {
    if (points.isEmpty()) return emptyList()
    if (videoMs < points.first().timestampMs) return emptyList()
    if (videoMs >= points.last().timestampMs) return points

    val result = ArrayList<SwingPoint>(points.size)
    for (i in points.indices) {
        val pt = points[i]
        if (pt.timestampMs <= videoMs) {
            result += pt
        } else {
            val prev = result.lastOrNull()
            if (prev != null && pt.timestampMs > prev.timestampMs) {
                val frac = ((videoMs - prev.timestampMs).toFloat() / (pt.timestampMs - prev.timestampMs)).coerceIn(0f, 1f)
                result += pt.copy(
                    x = prev.x + (pt.x - prev.x) * frac,
                    y = prev.y + (pt.y - prev.y) * frac,
                    timestampMs = videoMs
                )
            }
            break
        }
    }
    return result
}

private fun DrawScope.drawLiveReticle(
    head: SwingPoint,
    w: Float,
    h: Float,
    colorScheme: TraceColorScheme
) {
    val cx = head.x * w
    val cy = head.y * h
    val color = phaseColor(head.phase, colorScheme)
    val pulseRadius = 15f + (head.energy * 16f)

    drawCircle(color = color.copy(alpha = 0.28f), radius = pulseRadius, center = Offset(cx, cy))
    drawCircle(color = color, radius = 9.5f, center = Offset(cx, cy), style = Stroke(width = 3f))
    drawCircle(color = TurfWhite, radius = 4f, center = Offset(cx, cy))
}

private fun DrawScope.drawPlaneGuide(
    w: Float,
    h: Float,
    clubType: ClubType,
    handedness: Handedness
) {
    val ballX = w * 0.5f
    val ballY = h * 0.72f
    val dirSign = if (handedness == Handedness.RIGHT) -1f else 1f
    val angleRad = Math.toRadians(clubType.idealPlaneAngleDeg.toDouble())
    val lineLen = h * 0.48f

    val topX = ballX + dirSign * (cos(angleRad) * lineLen).toFloat()
    val topY = ballY - (sin(angleRad) * lineLen).toFloat()

    val upperRad = Math.toRadians((clubType.idealPlaneAngleDeg + clubType.planeToleranceDeg).toDouble())
    val lowerRad = Math.toRadians((clubType.idealPlaneAngleDeg - clubType.planeToleranceDeg).toDouble())

    val upperTop = Offset(
        ballX + dirSign * (cos(upperRad) * lineLen).toFloat(),
        ballY - (sin(upperRad) * lineLen).toFloat()
    )
    val lowerTop = Offset(
        ballX + dirSign * (cos(lowerRad) * lineLen).toFloat(),
        ballY - (sin(lowerRad) * lineLen).toFloat()
    )

    val corridorPath = Path().apply {
        moveTo(ballX, ballY)
        lineTo(upperTop.x, upperTop.y)
        lineTo(lowerTop.x, lowerTop.y)
        close()
    }
    drawPath(
        path = corridorPath,
        brush = Brush.linearGradient(
            colors = listOf(ElectricEmerald.copy(alpha = 0.14f), ElectricEmerald.copy(alpha = 0.03f)),
            start = Offset(ballX, ballY),
            end = Offset(topX, topY)
        )
    )

    drawLine(
        color = ElectricEmerald.copy(alpha = 0.55f),
        start = Offset(ballX, ballY),
        end = Offset(topX, topY),
        strokeWidth = 3f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
    )

    drawCircle(color = ImpactGold.copy(alpha = 0.24f), radius = 26f, center = Offset(ballX, ballY))
    drawCircle(color = ImpactGold.copy(alpha = 0.85f), radius = 15f, center = Offset(ballX, ballY), style = Stroke(width = 2.5f))
    drawCircle(color = TurfWhite.copy(alpha = 0.9f), radius = 4f, center = Offset(ballX, ballY))

    drawLine(
        color = TurfWhite.copy(alpha = 0.22f),
        start = Offset(ballX - w * 0.18f, ballY),
        end = Offset(ballX + w * 0.18f, ballY),
        strokeWidth = 2f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
    )
}

private fun DrawScope.drawTrajectoryPath(
    points: List<SwingPoint>,
    w: Float,
    h: Float,
    baseStrokePx: Float,
    alphaScale: Float,
    glowEnabled: Boolean,
    colorScheme: TraceColorScheme
) {
    if (points.size < 2) return

    for (i in 0 until points.lastIndex) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val pMidNext = if (i + 2 < points.size) points[i + 2] else p1

        val start = Offset(p0.x * w, p0.y * h)
        val ctrl = Offset(p1.x * w, p1.y * h)
        val end = if (i + 2 < points.size) {
            Offset(((p1.x + pMidNext.x) * 0.5f) * w, ((p1.y + pMidNext.y) * 0.5f) * h)
        } else {
            ctrl
        }

        val segStart = if (i == 0) {
            start
        } else {
            val prev = points[i - 1]
            Offset(((prev.x + p0.x) * 0.5f) * w, ((prev.y + p0.y) * 0.5f) * h)
        }

        val segmentPath = Path().apply {
            moveTo(segStart.x, segStart.y)
            quadraticTo(ctrl.x, ctrl.y, end.x, end.y)
        }

        val color = phaseColor(p1.phase, colorScheme)
        val speedFactor = (0.75f + (p1.speed * 0.25f)).coerceIn(0.75f, 1.45f)
        val strokeW = baseStrokePx * speedFactor

        if (glowEnabled) {
            drawPath(
                path = segmentPath,
                color = color.copy(alpha = 0.26f * alphaScale),
                style = Stroke(width = strokeW * 2.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
        }

        drawPath(
            path = segmentPath,
            color = color.copy(alpha = 0.95f * alphaScale),
            style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

fun phaseColor(
    phase: SwingPhase,
    scheme: TraceColorScheme = TraceColorScheme.CLASSIC_RED_GREEN
): Color = when (scheme) {
    TraceColorScheme.CLASSIC_RED_GREEN -> when (phase) {
        SwingPhase.ADDRESS -> TurfWhite
        SwingPhase.BACKSWING, SwingPhase.TOP -> BackswingRed
        SwingPhase.DOWNSWING -> DownswingGreen
        SwingPhase.IMPACT -> ImpactGold
        SwingPhase.FOLLOW_THROUGH -> FollowThroughMint
    }
    TraceColorScheme.PRO_CYAN_EMERALD -> when (phase) {
        SwingPhase.ADDRESS -> TurfWhite
        SwingPhase.BACKSWING, SwingPhase.TOP -> BackswingCyan
        SwingPhase.DOWNSWING -> ElectricEmerald
        SwingPhase.IMPACT -> ImpactGold
        SwingPhase.FOLLOW_THROUGH -> FollowThroughMint
    }
}
