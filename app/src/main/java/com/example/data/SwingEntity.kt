package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ClubType
import com.example.model.Handedness
import com.example.model.SwingMetrics
import com.example.model.SwingSession

@Entity(tableName = "swing_sessions")
data class SwingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val createdAtMs: Long,
    val clubTypeOrdinal: Int,
    val handednessOrdinal: Int,
    val frontCamera: Boolean,
    val pointsSerialized: String,
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
    val tipsSerialized: String,
    val videoPath: String? = null,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val videoAspectRatio: Float = 0.5625f
) {
    fun toDomain(): SwingSession = SwingSession(
        id = id,
        createdAtMs = createdAtMs,
        clubType = ClubType.entries[clubTypeOrdinal.coerceIn(0, ClubType.entries.lastIndex)],
        handedness = Handedness.entries[handednessOrdinal.coerceIn(0, Handedness.entries.lastIndex)],
        points = SwingPointCodec.decodePoints(pointsSerialized),
        metrics = SwingMetrics(
            tempoRatio = tempoRatio,
            backswingMs = backswingMs,
            downswingMs = downswingMs,
            planeAngleDeg = planeAngleDeg,
            planeDeviationDeg = planeDeviationDeg,
            peakVelocityScore = peakVelocityScore,
            arcSmoothnessScore = arcSmoothnessScore,
            overallScore = overallScore,
            planeVerdict = planeVerdict,
            apexIndex = apexIndex,
            impactIndex = impactIndex,
            tips = SwingPointCodec.decodeTips(tipsSerialized)
        ),
        frontCamera = frontCamera,
        videoPath = videoPath,
        trimStartMs = trimStartMs,
        trimEndMs = trimEndMs,
        videoAspectRatio = videoAspectRatio
    )

    companion object {
        fun fromDomain(session: SwingSession): SwingEntity = SwingEntity(
            id = session.id,
            createdAtMs = session.createdAtMs,
            clubTypeOrdinal = session.clubType.ordinal,
            handednessOrdinal = session.handedness.ordinal,
            frontCamera = session.frontCamera,
            pointsSerialized = SwingPointCodec.encodePoints(session.points),
            tempoRatio = session.metrics.tempoRatio,
            backswingMs = session.metrics.backswingMs,
            downswingMs = session.metrics.downswingMs,
            planeAngleDeg = session.metrics.planeAngleDeg,
            planeDeviationDeg = session.metrics.planeDeviationDeg,
            peakVelocityScore = session.metrics.peakVelocityScore,
            arcSmoothnessScore = session.metrics.arcSmoothnessScore,
            overallScore = session.metrics.overallScore,
            planeVerdict = session.metrics.planeVerdict,
            apexIndex = session.metrics.apexIndex,
            impactIndex = session.metrics.impactIndex,
            tipsSerialized = SwingPointCodec.encodeTips(session.metrics.tips),
            videoPath = session.videoPath,
            trimStartMs = session.trimStartMs,
            trimEndMs = session.trimEndMs,
            videoAspectRatio = session.videoAspectRatio
        )
    }
}
