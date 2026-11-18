package com.example.ui.screens

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.SwingSession
import com.example.model.TraceColorScheme
import com.example.ui.TracerViewModel
import com.example.ui.components.MetricPill
import com.example.ui.components.SwingArcCanvas
import com.example.ui.theme.AlertCoral
import com.example.ui.theme.BackswingCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.FairwayOutline
import com.example.ui.theme.FairwaySurface
import com.example.ui.theme.FairwaySurfaceElevated
import com.example.ui.theme.ImpactGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SageMuted
import com.example.ui.theme.TurfWhite
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private enum class HistoryFilter(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    LIVE("Live Traces")
}

@Composable
fun HistoryScreen(
    viewModel: TracerViewModel,
    onSelectSwing: (Long) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swings by viewModel.allSwings.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()
    var confirmClearAll by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }

    val filteredSwings = remember(swings, filter) {
        when (filter) {
            HistoryFilter.ALL -> swings
            HistoryFilter.VIDEOS -> swings.filter { it.isVideoBacked }
            HistoryFilter.LIVE -> swings.filter { !it.isVideoBacked }
        }
    }

    val groupedByDay = remember(filteredSwings) {
        filteredSwings.groupBy { dayLabel(it.createdAtMs) }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .statusBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SESSION LOG",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricEmerald
                    )
                    Text(
                        text = "Captured Swings",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TurfWhite
                    )
                }

                if (swings.isNotEmpty()) {
                    TextButton(
                        onClick = { confirmClearAll = true },
                        modifier = Modifier.testTag("clear_all_swings_button")
                    ) {
                        Text(
                            text = "Clear All",
                            style = MaterialTheme.typography.labelLarge,
                            color = AlertCoral
                        )
                    }
                }
            }

            if (swings.isEmpty()) {
                EmptyHistoryState(
                    onOpenCamera = onOpenCamera,
                    onOpenUpload = onOpenUpload,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val avgScore = swings.map { it.metrics.overallScore }.average().roundToInt()
                val avgTempo = swings.map { it.metrics.tempoRatio.toDouble() }.average()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricPill(label = "Swings", value = "${swings.size}", accent = TurfWhite, modifier = Modifier.weight(1f))
                    MetricPill(label = "Avg Score", value = "$avgScore", accent = ElectricEmerald, modifier = Modifier.weight(1f))
                    MetricPill(
                        label = "Avg Tempo",
                        value = "${String.format(Locale.US, "%.1f", avgTempo)}:1",
                        accent = BackswingCyan,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryFilter.entries.forEach { item ->
                        FilterChip(
                            selected = filter == item,
                            onClick = { filter = item },
                            label = { Text(item.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricEmerald.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricEmerald,
                                containerColor = FairwaySurfaceElevated,
                                labelColor = TurfWhite
                            )
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    groupedByDay.forEach { (dayHeading, itemsInDay) ->
                        item(key = "header_$dayHeading") {
                            Text(
                                text = dayHeading.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = SageMuted,
                                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                            )
                        }
                        items(itemsInDay, key = { it.id }) { session ->
                            SwingSessionCard(
                                session = session,
                                colorScheme = prefs.traceColorScheme,
                                onClick = { onSelectSwing(session.id) },
                                onDelete = { viewModel.deleteSwing(session.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            containerColor = FairwaySurface,
            titleContentColor = TurfWhite,
            textContentColor = SageMuted,
            title = { Text("Clear all captured swings?") },
            text = { Text("This deletes your local swing trace history and saved video clips.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllSwings()
                        confirmClearAll = false
                    }
                ) {
                    Text("Clear All", color = AlertCoral)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) {
                    Text("Cancel", color = TurfWhite)
                }
            }
        )
    }
}

private fun dayLabel(timestampMs: Long): String {
    return when {
        DateUtils.isToday(timestampMs) -> "Today"
        DateUtils.isToday(timestampMs + DateUtils.DAY_IN_MILLIS) -> "Yesterday"
        else -> SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(timestampMs))
    }
}

@Composable
private fun SwingSessionCard(
    session: SwingSession,
    colorScheme: TraceColorScheme,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val metrics = session.metrics
    val scoreColor = when {
        metrics.overallScore >= 82 -> ElectricEmerald
        metrics.overallScore >= 68 -> ImpactGold
        else -> AlertCoral
    }
    val formatter = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("swing_card_${session.id}"),
        shape = RoundedCornerShape(20.dp),
        color = FairwaySurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 74.dp, height = 92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ObsidianBg)
                    .border(1.dp, FairwayOutline, RoundedCornerShape(14.dp))
                    .padding(6.dp)
            ) {
                SwingArcCanvas(
                    points = session.points,
                    colorScheme = colorScheme,
                    isMiniPreview = true
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .clip(RoundedCornerShape(6.dp))
                        .background(FairwaySurfaceElevated)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = session.clubType.shortCode,
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricEmerald
                    )
                }
                if (session.isVideoBacked) {
                    Icon(
                        imageVector = Icons.Filled.PlayCircleFilled,
                        contentDescription = "Video swing",
                        tint = ImpactGold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(18.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = metrics.planeVerdict,
                    style = MaterialTheme.typography.titleMedium,
                    color = TurfWhite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${session.clubType.displayName} (${session.handedness.shortLabel}) • ${formatter.format(Date(session.createdAtMs))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = SageMuted
                )

                Spacer(Modifier.height(2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallStatChip("Tempo ${String.format(Locale.US, "%.1f", metrics.tempoRatio)}:1", BackswingCyan)
                    SmallStatChip("Plane ${metrics.planeAngleDeg.roundToInt()}°", ImpactGold)
                }
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(scoreColor.copy(alpha = 0.14f))
                        .border(1.5.dp, scoreColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${metrics.overallScore}",
                        style = MaterialTheme.typography.titleMedium,
                        color = scoreColor,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("delete_swing_${session.id}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "Delete swing",
                        tint = SageMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallStatChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(FairwaySurfaceElevated)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun EmptyHistoryState(
    onOpenCamera: () -> Unit,
    onOpenUpload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(FairwaySurfaceElevated)
                .border(1.dp, FairwayOutline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Timeline,
                contentDescription = null,
                tint = ElectricEmerald,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(text = "No Swing Traces Yet", style = MaterialTheme.typography.headlineSmall, color = TurfWhite)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Capture live swings with the camera or upload a recorded swing video to trace the clubhead arc.",
            style = MaterialTheme.typography.bodyMedium,
            color = SageMuted,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onOpenCamera,
                colors = ButtonDefaults.buttonColors(containerColor = ElectricEmerald, contentColor = ObsidianBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.testTag("empty_open_tracer_button")
            ) {
                Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Live Tracer", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton(
                onClick = onOpenUpload,
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline),
                modifier = Modifier.testTag("empty_open_upload_button")
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = BackswingCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Upload Video", color = TurfWhite, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
