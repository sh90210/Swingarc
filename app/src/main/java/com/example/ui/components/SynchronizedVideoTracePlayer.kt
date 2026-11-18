package com.example.ui.components

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.ClubType
import com.example.model.Handedness
import com.example.model.SwingPoint
import com.example.model.TraceColorScheme
import com.example.ui.theme.BackswingCyan
import com.example.ui.theme.ElectricEmerald
import com.example.ui.theme.FairwayOutline
import com.example.ui.theme.FairwaySurface
import com.example.ui.theme.FairwaySurfaceElevated
import com.example.ui.theme.ImpactGold
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SageMuted
import com.example.ui.theme.TurfWhite
import kotlinx.coroutines.delay

/**
 * Synchronized video + progressive swing-arc overlay player.
 * Supports slow-mo (0.25x..1.0x), tap-to-hide controls, Trace On/Off toggle,
 * Loop On/Off toggle, and replay cycle callbacks for Hands-Free Range Loop mode.
 */
@Composable
fun SynchronizedVideoTracePlayer(
    videoPath: String,
    videoAspectRatio: Float,
    points: List<SwingPoint>,
    clubType: ClubType,
    handedness: Handedness,
    colorScheme: TraceColorScheme,
    modifier: Modifier = Modifier,
    trimStartMs: Long = 0L,
    trimEndMs: Long = 0L,
    apexIndex: Int = -1,
    impactIndex: Int = -1,
    initialSpeed: Float = 0.5f,
    autoPlay: Boolean = true,
    loopEnabledDefault: Boolean = true,
    showPlaneGuide: Boolean = false,
    showDetectedPlanes: Boolean = false,
    onReplayCompleted: (() -> Unit)? = null
) {
    var mediaPlayer by remember(videoPath) { mutableStateOf<MediaPlayer?>(null) }
    var isPrepared by remember(videoPath) { mutableStateOf(false) }
    var isPlaying by remember(videoPath) { mutableStateOf(autoPlay) }
    var playbackSpeed by remember { mutableFloatStateOf(initialSpeed.coerceIn(0.25f, 1.0f)) }
    var loopEnabled by remember { mutableStateOf(loopEnabledDefault) }
    var showTrace by remember { mutableStateOf(true) }
    var controlsVisible by remember { mutableStateOf(true) }
    var totalDurationMs by remember(videoPath) { mutableLongStateOf(1000L) }
    var currentPosMs by remember(videoPath) { mutableLongStateOf(trimStartMs) }

    val effectiveStart = trimStartMs.coerceAtLeast(0L)
    val effectiveEnd = if (trimEndMs > effectiveStart) trimEndMs else totalDurationMs

    fun applySpeed(player: MediaPlayer, speed: Float) {
        runCatching {
            player.playbackParams = PlaybackParams().setSpeed(speed)
            if (!isPlaying && player.isPlaying) {
                player.pause()
            }
        }
    }

    // Poll player position smoothly at ~60Hz for synchronized self-drawing arc
    LaunchedEffect(mediaPlayer, isPrepared, isPlaying, effectiveStart, effectiveEnd, loopEnabled) {
        val player = mediaPlayer ?: return@LaunchedEffect
        if (!isPrepared) return@LaunchedEffect

        while (true) {
            val pos = runCatching { player.currentPosition.toLong() }.getOrDefault(effectiveStart)
            currentPosMs = pos

            if (isPlaying && pos >= effectiveEnd - 25L) {
                onReplayCompleted?.invoke()
                if (loopEnabled) {
                    runCatching {
                        player.seekTo(effectiveStart.toInt())
                        if (!player.isPlaying) player.start()
                    }
                } else {
                    runCatching { player.pause() }
                    isPlaying = false
                }
            }
            delay(16L)
        }
    }

    LaunchedEffect(playbackSpeed, mediaPlayer, isPrepared) {
        val player = mediaPlayer
        if (player != null && isPrepared) {
            applySpeed(player, playbackSpeed)
        }
    }

    DisposableEffect(videoPath) {
        onDispose {
            runCatching {
                mediaPlayer?.stop()
                mediaPlayer?.release()
            }
            mediaPlayer = null
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Aspect-matched video + aligned SwingArcCanvas container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(ObsidianBg)
                .border(1.dp, FairwayOutline, RoundedCornerShape(20.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { controlsVisible = !controlsVisible },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(videoAspectRatio.coerceIn(0.45f, 1.9f))
            ) {
                AndroidView(
                    factory = { ctx ->
                        TextureView(ctx).apply {
                            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(
                                    surface: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) {
                                    val mp = MediaPlayer().apply {
                                        setDataSource(videoPath)
                                        setSurface(Surface(surface))
                                        setOnPreparedListener { preparedMp ->
                                            totalDurationMs = preparedMp.duration.toLong().coerceAtLeast(500L)
                                            isPrepared = true
                                            if (effectiveStart > 0L) {
                                                preparedMp.seekTo(effectiveStart.toInt())
                                            }
                                            applySpeed(preparedMp, playbackSpeed)
                                            if (autoPlay) {
                                                preparedMp.start()
                                                isPlaying = true
                                            }
                                        }
                                        setOnCompletionListener {
                                            onReplayCompleted?.invoke()
                                            if (loopEnabled) {
                                                seekTo(effectiveStart.toInt())
                                                start()
                                            } else {
                                                isPlaying = false
                                            }
                                        }
                                        prepareAsync()
                                    }
                                    mediaPlayer = mp
                                }

                                override fun onSurfaceTextureSizeChanged(
                                    surface: SurfaceTexture,
                                    width: Int,
                                    height: Int
                                ) = Unit

                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                    runCatching {
                                        mediaPlayer?.stop()
                                        mediaPlayer?.release()
                                    }
                                    mediaPlayer = null
                                    isPrepared = false
                                    return true
                                }

                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Progressive Swing Arc Overlay aligned 1:1 with video frame
                SwingArcCanvas(
                    points = points,
                    showPlaneGuide = showPlaneGuide,
                    showDetectedPlanes = showDetectedPlanes,
                    showTrace = showTrace,
                    apexIndex = apexIndex,
                    impactIndex = impactIndex,
                    clubType = clubType,
                    handedness = handedness,
                    colorScheme = colorScheme,
                    currentVideoTimeMs = currentPosMs,
                    modifier = Modifier.fillMaxSize()
                )

                // Top floating pills (Trace On/Off & Loop) - tap video to hide/show
                this@Box.let {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = controlsVisible,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(ObsidianBg.copy(alpha = 0.72f), Color.Transparent)
                                    )
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (showTrace) ElectricEmerald.copy(alpha = 0.22f) else FairwaySurface.copy(alpha = 0.85f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (showTrace) ElectricEmerald else FairwayOutline
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { showTrace = !showTrace }
                                    .testTag("video_trace_toggle_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Timeline,
                                        contentDescription = "Toggle trace overlay",
                                        tint = if (showTrace) ElectricEmerald else SageMuted,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (showTrace) "Trace On" else "Trace Off",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (showTrace) ElectricEmerald else TurfWhite
                                    )
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (loopEnabled) BackswingCyan.copy(alpha = 0.22f) else FairwaySurface.copy(alpha = 0.85f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (loopEnabled) BackswingCyan else FairwayOutline
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .clickable { loopEnabled = !loopEnabled }
                                    .testTag("video_loop_toggle_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Repeat,
                                        contentDescription = "Toggle loop",
                                        tint = if (loopEnabled) BackswingCyan else SageMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Text(
                                        text = if (loopEnabled) "Loop On" else "Loop Off",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (loopEnabled) BackswingCyan else TurfWhite
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Scrubber & Slow-Mo Speed Deck
        AnimatedVisibility(visible = controlsVisible) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = FairwaySurface,
                border = androidx.compose.foundation.BorderStroke(1.dp, FairwayOutline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = ElectricEmerald,
                            modifier = Modifier.size(44.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val mp = mediaPlayer ?: return@IconButton
                                    if (!isPrepared) return@IconButton
                                    if (isPlaying) {
                                        runCatching { mp.pause() }
                                        isPlaying = false
                                    } else {
                                        if (currentPosMs >= effectiveEnd - 40L) {
                                            runCatching { mp.seekTo(effectiveStart.toInt()) }
                                        }
                                        runCatching { mp.start() }
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier.testTag("video_play_pause_button")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause video" else "Play video",
                                    tint = ObsidianBg
                                )
                            }
                        }

                        val span = (effectiveEnd - effectiveStart).coerceAtLeast(100L).toFloat()
                        val sliderFraction = ((currentPosMs - effectiveStart) / span).coerceIn(0f, 1f)
                        Slider(
                            value = sliderFraction,
                            onValueChange = { frac ->
                                val mp = mediaPlayer ?: return@Slider
                                if (!isPrepared) return@Slider
                                val targetMs = effectiveStart + (span * frac).toLong()
                                currentPosMs = targetMs
                                runCatching { mp.seekTo(targetMs.toInt()) }
                            },
                            colors = SliderDefaults.colors(
                                thumbColor = ElectricEmerald,
                                activeTrackColor = ElectricEmerald,
                                inactiveTrackColor = FairwaySurfaceElevated
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("video_scrubber_slider")
                        )

                        Text(
                            text = "${((currentPosMs - effectiveStart).coerceAtLeast(0L) / 1000f).let { String.format("%.1fs", it) }}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SageMuted
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(0.25f to "¼×", 0.5f to "½×", 0.75f to "¾×", 1.0f to "1×").forEach { (spd, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(playbackSpeed - spd) < 0.05f,
                                onClick = { playbackSpeed = spd },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ImpactGold.copy(alpha = 0.22f),
                                    selectedLabelColor = ImpactGold,
                                    containerColor = FairwaySurfaceElevated,
                                    labelColor = TurfWhite
                                )
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Tap video to hide UI",
                            style = MaterialTheme.typography.labelSmall,
                            color = SageMuted,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
    }
}
