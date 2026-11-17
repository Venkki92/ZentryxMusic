package com.zentryx.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zentryx.music.models.RepeatMode
import com.zentryx.music.ui.components.FullscreenVisualizerDialog
import com.zentryx.music.ui.components.MercedesVisualizerCanvas
import com.zentryx.music.ui.theme.MbuxAmbientBorder
import com.zentryx.music.ui.theme.MbuxObsidian
import com.zentryx.music.ui.theme.MbuxSurface
import com.zentryx.music.ui.theme.MbuxSurfaceVariant
import com.zentryx.music.viewmodel.PlayerViewModel
import com.zentryx.music.visualizer.VisualizerColorway
import com.zentryx.music.visualizer.VisualizerMode
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

/**
 * MBUX-style cockpit player optimized for 8-inch 1024x600 landscape automotive displays.
 * Supports 6 interactive Mercedes visualizers + fullscreen mode, Pro 16-Band DSP link,
 * swipe-to-skip gestures, GPS speed readout, podcast playback rate, sleep timer, and -20dB talk ducking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDsp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentSong = playbackState.currentSong
    val isLyricsVisible by viewModel.isLyricsVisible.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val speedKmH by viewModel.currentSpeedKmH.collectAsStateWithLifecycle()
    val isAttenuated by viewModel.isAttenuated.collectAsStateWithLifecycle()
    val sleepMinutes by viewModel.sleepTimerMinutes.collectAsStateWithLifecycle()
    val sleepRemainingSec by viewModel.sleepTimerRemainingSec.collectAsStateWithLifecycle()
    val dspSettings by viewModel.dspSettings.collectAsStateWithLifecycle()
    val visualizerFrame by viewModel.visualizerFrame.collectAsStateWithLifecycle()
    val visualizerMode by viewModel.visualizerMode.collectAsStateWithLifecycle()
    val visualizerColorway by viewModel.visualizerColorway.collectAsStateWithLifecycle()
    val accent by viewModel.accentColor.collectAsStateWithLifecycle()
    val playbackRate by viewModel.playbackRate.collectAsStateWithLifecycle()
    val swipeEnabled by viewModel.swipeGesturesEnabled.collectAsStateWithLifecycle()
    val idleScreensaverSec by viewModel.idleScreensaverSeconds.collectAsStateWithLifecycle()

    val accentColor = Color(accent.hexColor)

    var showQueueDrawer by remember { mutableStateOf(false) }
    var showVisualizerTile by remember { mutableStateOf(true) }
    var showFullscreenVisualizer by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionSlider by remember { mutableFloatStateOf(0f) }
    var lastInteractionMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(playbackState.currentPositionMs) {
        if (!isSeeking) {
            seekPositionSlider = playbackState.currentPositionMs.toFloat()
        }
    }

    // Idle Visualizer Screensaver trigger when playing
    LaunchedEffect(idleScreensaverSec, playbackState.isPlaying, lastInteractionMillis) {
        if (idleScreensaverSec > 0 && playbackState.isPlaying && !showFullscreenVisualizer) {
            delay(idleScreensaverSec * 1000L)
            if (playbackState.isPlaying) {
                showFullscreenVisualizer = true
            }
        }
    }

    var dragAccum by remember { mutableFloatStateOf(0f) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "MEDIA",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = accentColor
                            )
                        )
                        if (playbackState.queue.isNotEmpty()) {
                            Text(
                                text = "• ${playbackState.currentQueueIndex + 1}/${playbackState.queue.size}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                        if (dspSettings.dspEnabled) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = accentColor.copy(alpha = 0.14f),
                                modifier = Modifier
                                    .border(0.5.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                    .clickable { onNavigateToDsp() }
                            ) {
                                Text(
                                    text = "DSP · ${dspSettings.selectedPresetId.uppercase()}",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor,
                                    maxLines = 1,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    // Quick 16-Band DSP Console shortcut
                    IconButton(onClick = onNavigateToDsp, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "16-Band DSP Console",
                            tint = if (dspSettings.dspEnabled) accentColor else Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    // Fullscreen Mercedes Cluster Visualizer button
                    IconButton(onClick = { showFullscreenVisualizer = true }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Fullscreen Visualizer",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.toggleFavorite() }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = if (currentSong?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (currentSong?.isFavorite == true) accentColor else Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.toggleLyrics() }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Lyrics Karaoke",
                            tint = if (isLyricsVisible) accentColor else Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    IconButton(onClick = { viewModel.downloadCurrentSong() }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = if (currentSong?.isDownloaded == true) Icons.Default.CheckCircle else Icons.Default.Download,
                            contentDescription = "Download Offline",
                            tint = if (currentSong?.isDownloaded == true) accentColor else Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    IconButton(onClick = { showQueueDrawer = !showQueueDrawer }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Queue",
                            tint = if (showQueueDrawer) accentColor else Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MbuxObsidian),
                modifier = Modifier.height(48.dp)
            )
        },
        containerColor = MbuxObsidian,
        modifier = modifier
    ) { paddingValues ->
        if (currentSong == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No track currently loaded",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color(0xFF94A3B8)
                )
            }
            return@Scaffold
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 18.dp, vertical = 6.dp)
                .pointerInput(swipeEnabled) {
                    if (swipeEnabled) {
                        detectHorizontalDragGestures(
                            onDragStart = {
                                dragAccum = 0f
                                lastInteractionMillis = System.currentTimeMillis()
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragAccum += dragAmount
                            },
                            onDragEnd = {
                                if (dragAccum < -95f) viewModel.skipToNext()
                                else if (dragAccum > 95f) viewModel.skipToPrevious()
                                dragAccum = 0f
                            }
                        )
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left Pane (40%): Visualizer / Artwork Toggle + Mode Chip + Track Metadata
            Column(
                modifier = Modifier
                    .weight(0.40f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isLyricsVisible) {
                    LyricsView(
                        lyrics = lyrics,
                        currentPositionMs = playbackState.currentPositionMs,
                        onSeekTo = { pos -> viewModel.seekTo(pos) },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(width = 230.dp, height = 188.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                lastInteractionMillis = System.currentTimeMillis()
                                showVisualizerTile = !showVisualizerTile
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (showVisualizerTile) {
                            MercedesVisualizerCanvas(
                                mode = visualizerMode,
                                colorway = visualizerColorway,
                                frame = visualizerFrame,
                                isPlaying = playbackState.isPlaying,
                                trackTitle = currentSong.title,
                                trackArtist = currentSong.artist,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MbuxSurfaceVariant)
                                    .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (currentSong.artworkPath != null && File(currentSong.artworkPath).exists()) {
                                    AsyncImage(
                                        model = File(currentSong.artworkPath),
                                        contentDescription = currentSong.album,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = accentColor.copy(alpha = 0.85f),
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                            }
                        }

                        // Codec Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            contentColor = accentColor,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .border(0.5.dp, MbuxAmbientBorder, RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = currentSong.format.name,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Compact Visualizer / Artwork / Mode Controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = showVisualizerTile,
                            onClick = { showVisualizerTile = !showVisualizerTile },
                            label = {
                                Text(
                                    if (showVisualizerTile) visualizerMode.shortLabel else "Artwork",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                selectedLabelColor = accentColor
                            )
                        )
                        if (showVisualizerTile) {
                            OutlinedButton(
                                onClick = {
                                    val modes = VisualizerMode.entries
                                    val next = modes[(modes.indexOf(visualizerMode) + 1) % modes.size]
                                    viewModel.setVisualizerMode(next)
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Next Style", fontSize = 10.sp, color = Color.White)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = currentSong.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 16.sp),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${currentSong.artist} • ${currentSong.album}",
                        style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8), fontSize = 12.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Pane (60%): Queue OR Seekbar + Transport + Quick QOL Controls
            if (showQueueDrawer) {
                Column(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MbuxSurface)
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "PLAYLIST QUEUE",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = accentColor
                        ),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        itemsIndexed(playbackState.queue) { index, item ->
                            val isItemActive = index == playbackState.currentQueueIndex
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isItemActive) accentColor.copy(alpha = 0.15f) else Color(0xFF0F172A),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.playQueueIndex(index) }
                                    .border(
                                        width = if (isItemActive) 1.dp else 0.dp,
                                        color = if (isItemActive) accentColor else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isItemActive) accentColor else Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = if (isItemActive) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isItemActive) accentColor else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(text = item.artist, fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Seek Slider + Elapsed / Total
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = seekPositionSlider,
                            onValueChange = {
                                lastInteractionMillis = System.currentTimeMillis()
                                isSeeking = true
                                seekPositionSlider = it
                            },
                            onValueChangeFinished = {
                                viewModel.seekTo(seekPositionSlider.toLong())
                                isSeeking = false
                            },
                            valueRange = 0f..(playbackState.durationMs.toFloat().coerceAtLeast(1f)),
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor,
                                inactiveTrackColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = formatDuration(seekPositionSlider.toLong()),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                color = accentColor
                            )
                            Text(
                                text = "${speedKmH.toInt()} km/h",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = formatDuration(playbackState.durationMs),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Primary Driving Transport Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.toggleShuffle() }, modifier = Modifier.size(46.dp)) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.shuffleEnabled) accentColor else Color(0xFF475569),
                                modifier = Modifier.size(23.dp)
                            )
                        }

                        IconButton(
                            onClick = { viewModel.skipToPrevious() },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MbuxSurfaceVariant)
                                .border(1.dp, MbuxAmbientBorder, CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(30.dp))
                        }

                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        ) {
                            if (playbackState.isBuffering) {
                                CircularProgressIndicator(modifier = Modifier.size(30.dp), color = Color.Black, strokeWidth = 3.dp)
                            } else {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.skipToNext() },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(MbuxSurfaceVariant)
                                .border(1.dp, MbuxAmbientBorder, CircleShape)
                        ) {
                            Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(30.dp))
                        }

                        IconButton(onClick = { viewModel.cycleRepeatMode() }, modifier = Modifier.size(46.dp)) {
                            val (icon, tint) = when (playbackState.repeatMode) {
                                RepeatMode.OFF -> Icons.Default.Repeat to Color(0xFF475569)
                                RepeatMode.ALL -> Icons.Default.Repeat to accentColor
                                RepeatMode.ONE -> Icons.Default.RepeatOne to accentColor
                            }
                            Icon(imageVector = icon, contentDescription = "Repeat", tint = tint, modifier = Modifier.size(23.dp))
                        }
                    }

                    // Bottom QOL Strip: -20dB Duck, Speed Rate, Sleep Timer, DSP Console
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = isAttenuated,
                            onClick = { viewModel.toggleAttenuation() },
                            label = { Text(if (isAttenuated) "-20dB ON" else "Duck", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.25f),
                                selectedLabelColor = Color(0xFFF59E0B)
                            )
                        )

                        FilterChip(
                            selected = playbackRate != 1.0f,
                            onClick = {
                                val rates = listOf(1.0f, 1.25f, 1.5f, 2.0f, 0.75f)
                                val idx = rates.indexOfFirst { kotlin.math.abs(it - playbackRate) < 0.05f }
                                val next = rates[(idx + 1) % rates.size]
                                viewModel.setPlaybackRate(next)
                            },
                            label = { Text("${playbackRate}x", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                selectedLabelColor = accentColor
                            )
                        )

                        FilterChip(
                            selected = sleepRemainingSec != null,
                            onClick = {
                                val nextMin = when (sleepMinutes) {
                                    0 -> 15
                                    15 -> 30
                                    30 -> 45
                                    45 -> 60
                                    else -> 0
                                }
                                viewModel.setSleepTimer(nextMin)
                            },
                            label = {
                                val txt = sleepRemainingSec?.let { formatDuration(it * 1000L) } ?: "Sleep"
                                Text(txt, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                selectedLabelColor = accentColor
                            )
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        OutlinedButton(
                            onClick = onNavigateToDsp,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("EQ / DSP", fontSize = 10.sp, color = accentColor, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    if (showFullscreenVisualizer && currentSong != null) {
        FullscreenVisualizerDialog(
            mode = visualizerMode,
            colorway = visualizerColorway,
            frame = visualizerFrame,
            isPlaying = playbackState.isPlaying,
            trackTitle = currentSong.title,
            trackArtist = currentSong.artist,
            accentColor = accentColor,
            onSelectMode = { viewModel.setVisualizerMode(it) },
            onSelectColorway = { viewModel.setVisualizerColorway(it) },
            onPrevious = { viewModel.skipToPrevious() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.skipToNext() },
            onDismiss = {
                lastInteractionMillis = System.currentTimeMillis()
                showFullscreenVisualizer = false
            }
        )
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
