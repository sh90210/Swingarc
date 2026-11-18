package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SportsGolf
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.model.ClubType
import com.example.model.FeedbackSeverity
import com.example.model.Handedness
import com.example.model.LiveTelemetryState
import com.example.model.SwingSession
import com.example.model.TrackingStatus
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
import kotlin.math.roundToInt

@Composable
fun LiveTopTelemetryBar(
    telemetry: LiveTelemetryState,
    clubType: ClubType,
    handedness: Handedness,
    onClubChipClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val badgeColor by animateColorAsState(
        targetValue = when (telemetry.status) {
            TrackingStatus.TRACKING -> ImpactGold
            TrackingStatus.ARMED -> ElectricEmerald
            TrackingStatus.REVIEW -> BackswingCyan
            TrackingStatus.IDLE -> SageMuted
        },
        label = "statusColor"
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Live Status Pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = ObsidianBg.copy(alpha = 0.78f),
                border = androidx.compose.foundation.BorderStroke(1.dp, badgeColor.copy(alpha = 0.55f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(badgeColor)
                    )
                    Text(
                        text = telemetry.status.label.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = TurfWhite
                    )
                    if (telemetry.fps > 0) {
                        Text(
                            text = "${telemetry.fps} FPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = SageMuted
                        )
                    }
                }
            }

            // Active Club + Handedness Selector Pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = ObsidianBg.copy(alpha = 0.78f),
                border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onClubChipClick)
                    .testTag("club_selector_pill")
                    .semantics(mergeDescendants = true) {}
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SportsGolf,
                        contentDescription = "Selected club",
                        tint = ElectricEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${clubType.displayName} • ${handedness.shortLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TurfWhite
                    )
                }
            }
        }

        // Motion Energy & Live Prompt Pill
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = ObsidianBg.copy(alpha = 0.68f),
            border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline.copy(alpha = 0.6f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = telemetry.statusHint,
                        style = MaterialTheme.typography.bodySmall,
                        color = TurfWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "MOTION ${(telemetry.motionEnergy * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (telemetry.motionEnergy > 0.25f) ImpactGold else SageMuted
                    )
                }
                LinearProgressIndicator(
                    progress = { telemetry.motionEnergy.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        telemetry.status == TrackingStatus.TRACKING -> ImpactGold
                        telemetry.motionEnergy > 0.2f -> ElectricEmerald
                        else -> BackswingCyan
                    },
                    trackColor = FairwaySurfaceElevated
                )
            }
        }
    }
}

@Composable
fun QuickSwingFeedbackCard(
    session: SwingSession,
    onInspectClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val metrics = session.metrics
    val primaryTip = metrics.tips.firstOrNull()
    val scoreColor = when {
        metrics.overallScore >= 82 -> ElectricEmerald
        metrics.overallScore >= 68 -> ImpactGold
        else -> AlertCoral
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("quick_swing_feedback_card"),
        shape = RoundedCornerShape(22.dp),
        color = FairwaySurface.copy(alpha = 0.95f),
        border = androidx.compose.foundation.BorderStroke(1.dp, scoreColor.copy(alpha = 0.55f)),
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(scoreColor.copy(alpha = 0.16f))
                            .border(2.dp, scoreColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${metrics.overallScore}",
                            style = MaterialTheme.typography.titleLarge,
                            color = scoreColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Column {
                        Text(
                            text = metrics.planeVerdict,
                            style = MaterialTheme.typography.titleMedium,
                            color = TurfWhite
                        )
                        Text(
                            text = "${session.clubType.displayName} • Tempo ${String.format("%.1f", metrics.tempoRatio)}:1 • Plane ${metrics.planeAngleDeg.roundToInt()}°",
                            style = MaterialTheme.typography.labelMedium,
                            color = SageMuted
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("dismiss_feedback_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss swing summary",
                        tint = SageMuted
                    )
                }
            }

            if (primaryTip != null) {
                val (tipIcon, tipTint) = when (primaryTip.severity) {
                    FeedbackSeverity.OPTIMAL -> Icons.Filled.CheckCircle to ElectricEmerald
                    FeedbackSeverity.TUNE -> Icons.Filled.Tune to ImpactGold
                    FeedbackSeverity.ALERT -> Icons.Filled.WarningAmber to AlertCoral
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(FairwaySurfaceElevated)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = tipIcon,
                        contentDescription = null,
                        tint = tipTint,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = primaryTip.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = TurfWhite
                        )
                        Text(
                            text = primaryTip.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = SageMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Button(
                onClick = onInspectClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inspect_swing_button"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricEmerald,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    text = "Replay & Full Breakdown",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun MetricPill(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = FairwaySurfaceElevated,
        border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = SageMuted
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = accent
            )
        }
    }
}
