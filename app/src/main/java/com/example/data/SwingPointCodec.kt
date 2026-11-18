package com.example.data

import com.example.model.CoachingTip
import com.example.model.FeedbackSeverity
import com.example.model.SwingPhase
import com.example.model.SwingPoint

object SwingPointCodec {
    private const val POINT_SEP = ";"
    private const val FIELD_SEP = ","
    private const val TIP_SEP = "\n---\n"
    private const val TIP_FIELD_SEP = "|"

    fun encodePoints(points: List<SwingPoint>): String =
        points.joinToString(POINT_SEP) { pt ->
            "${pt.x}$FIELD_SEP${pt.y}$FIELD_SEP${pt.timestampMs}$FIELD_SEP${pt.speed}$FIELD_SEP${pt.energy}$FIELD_SEP${pt.phase.ordinal}"
        }

    fun decodePoints(raw: String): List<SwingPoint> {
        if (raw.isBlank()) return emptyList()
        return raw.split(POINT_SEP).mapNotNull { token ->
            val parts = token.split(FIELD_SEP)
            if (parts.size < 6) return@mapNotNull null
            val x = parts[0].toFloatOrNull() ?: return@mapNotNull null
            val y = parts[1].toFloatOrNull() ?: return@mapNotNull null
            val ts = parts[2].toLongOrNull() ?: return@mapNotNull null
            val speed = parts[3].toFloatOrNull() ?: 0f
            val energy = parts[4].toFloatOrNull() ?: 0f
            val phaseOrd = parts[5].toIntOrNull()?.coerceIn(0, SwingPhase.entries.lastIndex) ?: 0
            SwingPoint(
                x = x,
                y = y,
                timestampMs = ts,
                speed = speed,
                energy = energy,
                phase = SwingPhase.entries[phaseOrd]
            )
        }
    }

    fun encodeTips(tips: List<CoachingTip>): String =
        tips.joinToString(TIP_SEP) { tip ->
            val cleanTitle = tip.title.replace(TIP_FIELD_SEP, " ").replace(TIP_SEP, " ")
            val cleanDetail = tip.detail.replace(TIP_FIELD_SEP, " ").replace(TIP_SEP, " ")
            "${tip.severity.ordinal}$TIP_FIELD_SEP$cleanTitle$TIP_FIELD_SEP$cleanDetail"
        }

    fun decodeTips(raw: String): List<CoachingTip> {
        if (raw.isBlank()) return emptyList()
        return raw.split(TIP_SEP).mapNotNull { block ->
            val parts = block.split(TIP_FIELD_SEP)
            if (parts.size < 3) return@mapNotNull null
            val sevOrd = parts[0].toIntOrNull()?.coerceIn(0, FeedbackSeverity.entries.lastIndex) ?: 0
            CoachingTip(
                severity = FeedbackSeverity.entries[sevOrd],
                title = parts[1],
                detail = parts[2]
            )
        }
    }
}
