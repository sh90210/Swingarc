package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.ClubType
import com.example.model.Handedness
import com.example.model.TraceColorScheme
import com.example.model.VideoUploadUiState
import com.example.ui.TracerViewModel
import com.example.ui.components.MetricPill
import com.example.ui.components.SynchronizedVideoTracePlayer
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
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UploadTraceScreen(
    viewModel: TracerViewModel,
    onInspectSwing: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.importPickedVideo(context, uri)
        }
    }

    fun launchPicker() {
        videoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
        )
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "VIDEO TRACER LAB",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricEmerald
                    )
                    Text(
                        text = "Upload & Trace Swing",
                        style = MaterialTheme.typography.headlineMedium,
                        color = TurfWhite
                    )
                }

                if (uploadState !is VideoUploadUiState.Empty && uploadState !is VideoUploadUiState.Tracing) {
                    OutlinedButton(
                        onClick = { launchPicker() },
                        border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("upload_pick_another_top_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = null,
                            tint = ElectricEmerald,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("New Clip", color = TurfWhite, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            when (val state = uploadState) {
                is VideoUploadUiState.Empty -> {
                    // Hero Banner Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(190.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .border(1.dp, FairwayOutline, RoundedCornerShape(22.dp))
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.img_camera_guide_hero_1794926032754),
                            contentDescription = "Swing arc video upload banner",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, ObsidianBg.copy(alpha = 0.88f))
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Frame-by-Frame Background Subtraction",
                                style = MaterialTheme.typography.titleLarge,
                                color = TurfWhite
                            )
                            Text(
                                text = "Import any driving range or home swing clip. SwingArc extracts the clubhead arc, finds top-pause transition, and builds a synchronized slow-mo trace.",
                                style = MaterialTheme.typography.bodySmall,
                                color = SageMuted
                            )
                        }
                    }

                    // Primary Upload CTA
                    Button(
                        onClick = { launchPicker() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("upload_select_video_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricEmerald,
                            contentColor = ObsidianBg
                        ),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CloudUpload,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Upload Swing Video",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Pre-Trace Club & Style Configuration
                    ClubAndStyleConfigCard(
                        selectedClub = prefs.clubType,
                        selectedHand = prefs.handedness,
                        selectedColor = prefs.traceColorScheme,
                        sensitivity = prefs.sensitivity,
                        onSelectClub = { viewModel.setClubType(it) },
                        onSelectHand = { viewModel.setHandedness(it) },
                        onSelectColor = { viewModel.setTraceColorScheme(it) },
                        onSensitivityChange = { viewModel.setSensitivity(it) }
                    )

                    // Best-Results Filming Guide
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = FairwaySurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "BEST CLIP RESULTS",
                                style = MaterialTheme.typography.labelSmall,
                                color = ImpactGold
                            )
                            Text(
                                text = "• Tripod or stand camera (stationary background gives cleanest clubhead separation)",
                                style = MaterialTheme.typography.bodySmall,
                                color = SageMuted
                            )
                            Text(
                                text = "• Down-the-line or face-on view with full club & golfer inside frame",
                                style = MaterialTheme.typography.bodySmall,
                                color = SageMuted
                            )
                            Text(
                                text = "• 60 fps clips in bright lighting yield sharper downswing arcs",
                                style = MaterialTheme.typography.bodySmall,
                                color = SageMuted
                            )
                        }
                    }
                }

                is VideoUploadUiState.ClipSelected -> {
                    // Clip Info Header
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = FairwaySurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(FairwaySurfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.VideoFile,
                                    contentDescription = null,
                                    tint = BackswingCyan
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = state.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = TurfWhite,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${state.width}×${state.height} • ${String.format(Locale.US, "%.1fs", state.durationMs / 1000f)} clip",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SageMuted
                                )
                            }
                        }
                    }

                    // Video Preview (before tracing)
                    val aspect = (state.width.toFloat() / state.height.toFloat().coerceAtLeast(1f))
                        .coerceIn(0.45f, 1.9f)
                    SynchronizedVideoTracePlayer(
                        videoPath = state.localPath,
                        videoAspectRatio = aspect,
                        points = emptyList(),
                        clubType = prefs.clubType,
                        handedness = prefs.handedness,
                        colorScheme = prefs.traceColorScheme,
                        trimStartMs = state.trimStartMs,
                        trimEndMs = state.trimEndMs,
                        initialSpeed = 1f,
                        autoPlay = false,
                        loopEnabledDefault = true
                    )

                    // Swing Window Trimmer
                    if (state.durationMs > 1200L) {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = FairwaySurface,
                            border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "SWING WINDOW TRIM",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SageMuted
                                    )
                                    Text(
                                        text = "${String.format(Locale.US, "%.1fs", state.trimStartMs / 1000f)} → ${String.format(Locale.US, "%.1fs", state.trimEndMs / 1000f)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = ElectricEmerald
                                    )
                                }
                                RangeSlider(
                                    value = state.trimStartMs.toFloat()..state.trimEndMs.toFloat(),
                                    onValueChange = { range ->
                                        viewModel.updateUploadTrim(
                                            range.start.toLong(),
                                            range.endInclusive.toLong()
                                        )
                                    },
                                    valueRange = 0f..state.durationMs.toFloat(),
                                    colors = SliderDefaults.colors(
                                        thumbColor = ElectricEmerald,
                                        activeTrackColor = ElectricEmerald,
                                        inactiveTrackColor = FairwaySurfaceElevated
                                    ),
                                    modifier = Modifier.testTag("upload_trim_range_slider")
                                )
                                Text(
                                    text = "Tightly framing just the 2–5 second swing produces the cleanest background model.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SageMuted
                                )
                            }
                        }
                    }

                    ClubAndStyleConfigCard(
                        selectedClub = prefs.clubType,
                        selectedHand = prefs.handedness,
                        selectedColor = prefs.traceColorScheme,
                        sensitivity = prefs.sensitivity,
                        onSelectClub = { viewModel.setClubType(it) },
                        onSelectHand = { viewModel.setHandedness(it) },
                        onSelectColor = { viewModel.setTraceColorScheme(it) },
                        onSensitivityChange = { viewModel.setSensitivity(it) }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.clearUploadSelection() },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline)
                        ) {
                            Text("Cancel", color = TurfWhite)
                        }
                        Button(
                            onClick = { viewModel.traceSelectedUpload() },
                            modifier = Modifier
                                .weight(2f)
                                .height(52.dp)
                                .testTag("trace_uploaded_video_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricEmerald,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoFixHigh,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Trace Swing", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                is VideoUploadUiState.Tracing -> {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = FairwaySurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, ElectricEmerald.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(ElectricEmerald.copy(alpha = 0.16f))
                                    .border(2.dp, ElectricEmerald, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${(state.progress * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ElectricEmerald,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = "Tracing Clubhead Arc…",
                                style = MaterialTheme.typography.titleLarge,
                                color = TurfWhite
                            )
                            Text(
                                text = state.stageLabel,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SageMuted
                            )
                            LinearProgressIndicator(
                                progress = { state.progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                                color = ElectricEmerald,
                                trackColor = FairwaySurfaceElevated
                            )
                        }
                    }
                }

                is VideoUploadUiState.TracedReady -> {
                    val session = state.session
                    val metrics = session.metrics

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = ElectricEmerald,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Saved to Sessions • ${metrics.planeVerdict}",
                            style = MaterialTheme.typography.titleSmall,
                            color = ElectricEmerald
                        )
                    }

                    SynchronizedVideoTracePlayer(
                        videoPath = session.videoPath!!,
                        videoAspectRatio = session.videoAspectRatio,
                        points = session.points,
                        clubType = session.clubType,
                        handedness = session.handedness,
                        colorScheme = prefs.traceColorScheme,
                        trimStartMs = session.trimStartMs,
                        trimEndMs = session.trimEndMs,
                        apexIndex = metrics.apexIndex,
                        impactIndex = metrics.impactIndex,
                        initialSpeed = 0.5f,
                        autoPlay = true,
                        loopEnabledDefault = true
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricPill(
                            label = "Score",
                            value = "${metrics.overallScore}",
                            accent = ElectricEmerald,
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            label = "Tempo",
                            value = "${String.format(Locale.US, "%.1f", metrics.tempoRatio)}:1",
                            accent = BackswingCyan,
                            modifier = Modifier.weight(1f)
                        )
                        MetricPill(
                            label = "Plane",
                            value = "${metrics.planeAngleDeg.roundToInt()}°",
                            accent = ImpactGold,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { launchPicker() },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(14.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null, tint = TurfWhite, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Trace Another", color = TurfWhite)
                        }
                        Button(
                            onClick = { onInspectSwing(session.id) },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(50.dp)
                                .testTag("upload_open_breakdown_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricEmerald,
                                contentColor = ObsidianBg
                            ),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Text("Full Breakdown", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                is VideoUploadUiState.Error -> {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = FairwaySurface,
                        border = androidx.compose.foundation.BorderStroke(1.dp, AlertCoral.copy(alpha = 0.6f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = AlertCoral)
                                Text("Could Not Trace Clip", style = MaterialTheme.typography.titleMedium, color = TurfWhite)
                            }
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = SageMuted
                            )
                            Button(
                                onClick = { launchPicker() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = ElectricEmerald,
                                    contentColor = ObsidianBg
                                ),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("Choose Video")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ClubAndStyleConfigCard(
    selectedClub: ClubType,
    selectedHand: Handedness,
    selectedColor: TraceColorScheme,
    sensitivity: Float,
    onSelectClub: (ClubType) -> Unit,
    onSelectHand: (Handedness) -> Unit,
    onSelectColor: (TraceColorScheme) -> Unit,
    onSensitivityChange: (Float) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = FairwaySurface,
        border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.SportsGolf, contentDescription = null, tint = ElectricEmerald, modifier = Modifier.size(16.dp))
                Text("TRACER PARAMETERS", style = MaterialTheme.typography.labelSmall, color = ElectricEmerald)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ClubType.entries.forEach { club ->
                    FilterChip(
                        selected = selectedClub == club,
                        onClick = { onSelectClub(club) },
                        label = { Text(club.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricEmerald.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricEmerald,
                            containerColor = FairwaySurfaceElevated,
                            labelColor = TurfWhite
                        )
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Handedness.entries.forEach { hand ->
                    FilterChip(
                        selected = selectedHand == hand,
                        onClick = { onSelectHand(hand) },
                        label = { Text(hand.shortLabel) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BackswingCyan.copy(alpha = 0.2f),
                            selectedLabelColor = BackswingCyan,
                            containerColor = FairwaySurfaceElevated,
                            labelColor = TurfWhite
                        )
                    )
                }
                TraceColorScheme.entries.forEach { scheme ->
                    FilterChip(
                        selected = selectedColor == scheme,
                        onClick = { onSelectColor(scheme) },
                        label = { Text(scheme.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ImpactGold.copy(alpha = 0.2f),
                            selectedLabelColor = ImpactGold,
                            containerColor = FairwaySurfaceElevated,
                            labelColor = TurfWhite
                        )
                    )
                }
            }

            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("DETECTION SENSITIVITY", style = MaterialTheme.typography.labelSmall, color = SageMuted)
                    Text("${(sensitivity * 100).roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = ImpactGold)
                }
                Slider(
                    value = sensitivity,
                    onValueChange = onSensitivityChange,
                    valueRange = 0.2f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricEmerald,
                        activeTrackColor = ElectricEmerald,
                        inactiveTrackColor = FairwaySurfaceElevated
                    )
                )
            }
        }
    }
}
