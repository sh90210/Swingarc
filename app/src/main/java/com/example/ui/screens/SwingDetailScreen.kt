package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.CoachingTip
import com.example.model.FeedbackSeverity
import com.example.model.SwingPhase
import com.example.ui.TracerViewModel
import com.example.ui.components.MetricPill
import com.example.ui.components.SwingArcCanvas
import com.example.ui.components.SynchronizedVideoTracePlayer
import com.example.ui.components.phaseColor
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.BackswingCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.FairwayOutline
import com.example.ui.theme.FairwayOutlineSubtle
import com.example.ui.theme.FairwaySurface
import com.example.ui.theme.FairwaySurfaceElevated
import com.example.ui.theme.FollowThroughMint
import com.example.ui.theme.ImpactGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SageMuted
import com.example.ui.theme.TurfWhite
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SwingDetailScreen(
    swingId: Long,
    viewModel: TracerViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val session by viewModel.observeSwingById(swingId).collectAsStateWithLifecycle(initialValue = null)
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    val isRetracing by viewModel.isRetracingDetail.collectAsStateWithLifecycle()

    var playbackProgress by remember { mutableFloatStateOf(1f) }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableFloatStateOf(0.5f) }
    var showDetectedPlanes by remember { mutableStateOf(true) }
    var showTargetCorridor by remember { mutableStateOf(true) }

    LaunchedEffect(isPlaying, playbackSpeed, session?.id) {
        if (isPlaying && session != null) {
            if (playbackProgress >= 0.99f) {
                playbackProgress = 0.02f
            }
            while (isPlaying && playbackProgress < 1f) {
                delay(16L)
                playbackProgress = (playbackProgress + 0.014f * playbackSpeed).coerceAtMost(1f)
            }
            if (playbackProgress >= 1f) {
                isPlaying = false
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        val currentSession = session
        if (currentSession == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Loading Swing Analysis…",
                    style = MaterialTheme.typography.titleMedium,
                    color = SageMuted
                )
            }
        } else {
            val metrics = currentSession.metrics
            val hasVideo = currentSession.videoPath?.let { File(it).exists() } == true
            val backColor = phaseColor(SwingPhase.BACKSWING, prefs.traceColorScheme)
            val downColor = phaseColor(SwingPhase.DOWNSWING, prefs.traceColorScheme)

            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TurfWhite
                            )
                        }
                        Column {
                            Text(
                                text = "${currentSession.clubType.displayName} • ${currentSession.handedness.shortLabel}" +
                                    if (hasVideo) " • Video Trace" else " • Live Trace",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricEmerald
                            )
                            Text(
                                text = metrics.planeVerdict,
                                style = MaterialTheme.typography.titleLarge,
                                color = TurfWhite
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasVideo) {
                            OutlinedButton(
                                onClick = { viewModel.retraceSavedSwing(currentSession) },
                                enabled = !isRetracing,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline),
                                modifier = Modifier.testTag("retrace_video_button")
                            ) {
                                Icon(Icons.Filled.AutoFixHigh, contentDescription = null, tint = ElectricEmerald, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (isRetracing) "Tracing…" else "Re-trace", color = TurfWhite, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.deleteSwing(currentSession.id)
                                onBack()
                            },
                            modifier = Modifier.testTag("detail_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DeleteOutline,
                                contentDescription = "Delete swing",
                                tint = AlertCoral
                            )
                        }
                    }
                }

                // Legend Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PhaseLegendBadge("Backswing", backColor)
                        PhaseLegendBadge("Downswing", downColor)
                        PhaseLegendBadge("Impact", ImpactGold)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = showDetectedPlanes,
                            onClick = { showDetectedPlanes = !showDetectedPlanes },
                            label = { Text("Planes") }
                        )
                        FilterChip(
                            selected = showTargetCorridor,
                            onClick = { showTargetCorridor = !showTargetCorridor },
                            label = { Text("Guide") }
                        )
                    }
                }

                // Synchronized Video Player OR Pure Arc Replay Viewport
                if (hasVideo) {
                    SynchronizedVideoTracePlayer(
                        videoPath = currentSession.videoPath!!,
                        videoAspectRatio = currentSession.videoAspectRatio,
                        points = currentSession.points,
                        clubType = currentSession.clubType,
                        handedness = currentSession.handedness,
                        colorScheme = prefs.traceColorScheme,
                        trimStartMs = currentSession.trimStartMs,
                        trimEndMs = currentSession.trimEndMs,
                        apexIndex = metrics.apexIndex,
                        impactIndex = metrics.impactIndex,
                        initialSpeed = 0.5f,
                        autoPlay = true,
                        loopEnabledDefault = true,
                        showPlaneGuide = showTargetCorridor,
                        showDetectedPlanes = showDetectedPlanes
                    )
                } else {
                    val activeIdx = ((currentSession.points.lastIndex) * playbackProgress)
                        .roundToInt()
                        .coerceIn(0, currentSession.points.lastIndex.coerceAtLeast(0))
                    val activePhase = currentSession.points.getOrNull(activeIdx)?.phase ?: SwingPhase.IMPACT

                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = FairwaySurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.86f)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(ObsidianBg)
                                    .border(1.dp, FairwayOutlineSubtle, RoundedCornerShape(18.dp))
                            ) {
                                TelemetryGridBackground()
                                SwingArcCanvas(
                                    points = currentSession.points,
                                    showPlaneGuide = showTargetCorridor,
                                    showDetectedPlanes = showDetectedPlanes,
                                    apexIndex = metrics.apexIndex,
                                    impactIndex = metrics.impactIndex,
                                    clubType = currentSession.clubType,
                                    handedness = currentSession.handedness,
                                    colorScheme = prefs.traceColorScheme,
                                    playbackProgress = playbackProgress,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Text(
                                    text = activePhase.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = phaseColor(activePhase, prefs.traceColorScheme),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(12.dp)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = ElectricEmerald,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    IconButton(
                                        onClick = { isPlaying = !isPlaying },
                                        modifier = Modifier.testTag("replay_play_pause_button")
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (isPlaying) "Pause replay" else "Play replay",
                                            tint = ObsidianBg
                                        )
                                    }
                                }
                                Slider(
                                    value = playbackProgress,
                                    onValueChange = {
                                        isPlaying = false
                                        playbackProgress = it
                                    },
                                    valueRange = 0.02f..1f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ElectricEmerald,
                                        activeTrackColor = ElectricEmerald,
                                        inactiveTrackColor = FairwaySurfaceElevated
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("replay_scrubber_slider")
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(0.25f to "¼×", 0.5f to "½×", 0.75f to "¾×", 1f to "1×").forEach { (spd, label) ->
                                    FilterChip(
                                        selected = kotlin.math.abs(playbackSpeed - spd) < 0.05f,
                                        onClick = { playbackSpeed = spd },
                                        label = { Text(label) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = BackswingCyan.copy(alpha = 0.2f),
                                            selectedLabelColor = BackswingCyan,
                                            containerColor = FairwaySurfaceElevated,
                                            labelColor = TurfWhite
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                // 4-Metric Telemetry Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricPill(
                        label = "Score",
                        value = "${metrics.overallScore}/100",
                        accent = ElectricEmerald,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Tempo (${String.format(Locale.US, "%.1f", currentSession.clubType.idealTempoRatio)}:1 ideal)",
                        value = "${String.format(Locale.US, "%.1f", metrics.tempoRatio)}:1",
                        accent = BackswingCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricPill(
                        label = "Plane (${currentSession.clubType.idealPlaneAngleDeg.roundToInt()}° ideal)",
                        value = "${metrics.planeAngleDeg.roundToInt()}°",
                        accent = ImpactGold,
                        modifier = Modifier.weight(1f)
                    )
                    MetricPill(
                        label = "Smoothness",
                        value = "${metrics.arcSmoothnessScore}/100",
                        accent = FollowThroughMint,
                        modifier = Modifier.weight(1f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = FairwaySurface,
                    border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("BACKSWING TIME", style = MaterialTheme.typography.labelSmall, color = SageMuted)
                            Text("${metrics.backswingMs} ms", style = MaterialTheme.typography.titleMedium, color = backColor)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("DOWNSWING TIME", style = MaterialTheme.typography.labelSmall, color = SageMuted)
                            Text("${metrics.downswingMs} ms", style = MaterialTheme.typography.titleMedium, color = downColor)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("PLANE SHIFT", style = MaterialTheme.typography.labelSmall, color = SageMuted)
                            val shiftSign = if (metrics.planeDeviationDeg >= 0f) "+" else ""
                            Text(
                                text = "$shiftSign${metrics.planeDeviationDeg.roundToInt()}°",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (kotlin.math.abs(metrics.planeDeviationDeg) <= 5f) ElectricEmerald else ImpactGold
                            )
                        }
                    }
                }

                Text(
                    text = "COACHING FEEDBACK",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricEmerald,
                    modifier = Modifier.padding(top = 4.dp)
                )

                metrics.tips.forEach { tip ->
                    CoachingTipCard(tip)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun PhaseLegendBadge(label: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = SageMuted)
    }
}

@Composable
private fun TelemetryGridBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cols = 6
        val rows = 8
        for (c in 1 until cols) {
            val x = (size.width / cols) * c
            drawLine(
                color = FairwayOutlineSubtle.copy(alpha = 0.55f),
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 1f
            )
        }
        for (r in 1 until rows) {
            val y = (size.height / rows) * r
            drawLine(
                color = FairwayOutlineSubtle.copy(alpha = 0.55f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f
            )
        }
    }
}

@Composable
private fun CoachingTipCard(tip: CoachingTip) {
    val (icon, accent) = when (tip.severity) {
        FeedbackSeverity.OPTIMAL -> Icons.Filled.CheckCircle to ElectricEmerald
        FeedbackSeverity.TUNE -> Icons.Filled.Tune to ImpactGold
        FeedbackSeverity.ALERT -> Icons.Filled.WarningAmber to AlertCoral
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = FairwaySurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(text = tip.title, style = MaterialTheme.typography.titleSmall, color = TurfWhite)
                Text(text = tip.detail, style = MaterialTheme.typography.bodyMedium, color = SageMuted)
            }
        }
    }
}
