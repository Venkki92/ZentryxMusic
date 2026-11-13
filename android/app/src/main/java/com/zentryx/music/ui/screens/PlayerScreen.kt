package com.zentryx.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zentryx.music.models.RepeatMode
import com.zentryx.music.ui.theme.MbuxAmbientBorder
import com.zentryx.music.ui.theme.MbuxObsidian
import com.zentryx.music.ui.theme.MbuxSurface
import com.zentryx.music.ui.theme.MbuxSurfaceVariant
import com.zentryx.music.ui.theme.ZentryxLime
import com.zentryx.music.viewmodel.PlayerViewModel
import java.io.File
import java.util.Locale

/**
 * MBUX-style cockpit player optimized for 8-inch 1024x600 landscape automotive displays.
 * Incorporates Zentryx IT (#020617 / #A3E635) branding with Mercedes digital cockpit ergonomics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val currentSong = playbackState.currentSong
    val isLyricsVisible by viewModel.isLyricsVisible.collectAsStateWithLifecycle()
    val lyrics by viewModel.lyrics.collectAsStateWithLifecycle()
    val speedKmH by viewModel.currentSpeedKmH.collectAsStateWithLifecycle()
    val volumeMultiplier by viewModel.volumeMultiplier.collectAsStateWithLifecycle()

    var showQueueDrawer by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionSlider by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playbackState.currentPositionMs) {
        if (!isSeeking) {
            seekPositionSlider = playbackState.currentPositionMs.toFloat()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "MEDIA",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = ZentryxLime
                            )
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        if (playbackState.queue.isNotEmpty()) {
                            Text(
                                text = "•  TRACK ${playbackState.currentQueueIndex + 1}/${playbackState.queue.size}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
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
                    IconButton(
                        onClick = { viewModel.toggleFavorite() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (currentSong?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (currentSong?.isFavorite == true) ZentryxLime else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleLyrics() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Lyrics Karaoke",
                            tint = if (isLyricsVisible) ZentryxLime else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { viewModel.downloadCurrentSong() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (currentSong?.isDownloaded == true) Icons.Default.CheckCircle else Icons.Default.Download,
                            contentDescription = "Download Offline",
                            tint = if (currentSong?.isDownloaded == true) ZentryxLime else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = { showQueueDrawer = !showQueueDrawer },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = "Queue",
                            tint = if (showQueueDrawer) ZentryxLime else Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MbuxObsidian
                ),
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
                .padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left Pane (42% width): MBUX Artwork Tile + Metadata OR Lyrics View
            Column(
                modifier = Modifier
                    .weight(0.42f)
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
                            .size(190.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MbuxSurfaceVariant)
                            .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(16.dp)),
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
                                tint = ZentryxLime.copy(alpha = 0.8f),
                                modifier = Modifier.size(80.dp)
                            )
                        }

                        // MBUX Codec badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.85f),
                            contentColor = ZentryxLime,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .border(0.5.dp, MbuxAmbientBorder, RoundedCornerShape(4.dp))
                        ) {
                            Text(
                                text = currentSong.format.name,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = currentSong.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${currentSong.artist} • ${currentSong.album}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 13.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right Pane (58% width): Seek Bar + MBUX Automotive Transport Controls
            if (showQueueDrawer) {
                Column(
                    modifier = Modifier
                        .weight(0.58f)
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
                            color = ZentryxLime
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
                                color = if (isItemActive) ZentryxLime.copy(alpha = 0.15f) else Color(0xFF0F172A),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.playQueueIndex(index) }
                                    .border(
                                        width = if (isItemActive) 1.dp else 0.dp,
                                        color = if (isItemActive) ZentryxLime else Color.Transparent,
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
                                        color = if (isItemActive) ZentryxLime else Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = if (isItemActive) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isItemActive) ZentryxLime else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = item.artist,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    if (isItemActive) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = null,
                                            tint = ZentryxLime,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(0.58f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // MBUX Sleek Progress Slider
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Slider(
                            value = seekPositionSlider,
                            onValueChange = {
                                isSeeking = true
                                seekPositionSlider = it
                            },
                            onValueChangeFinished = {
                                viewModel.seekTo(seekPositionSlider.toLong())
                                isSeeking = false
                            },
                            valueRange = 0f..(playbackState.durationMs.toFloat().coerceAtLeast(1f)),
                            colors = SliderDefaults.colors(
                                thumbColor = ZentryxLime,
                                activeTrackColor = ZentryxLime,
                                inactiveTrackColor = Color(0xFF1E293B)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatDuration(seekPositionSlider.toLong()),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                fontWeight = FontWeight.Bold,
                                color = ZentryxLime
                            )
                            Text(
                                text = formatDuration(playbackState.durationMs),
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                color = Color(0xFF64748B)
                            )
                        }
                    }

                    // Transport Buttons (Ergonomic MBUX Glass Touch Targets)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Shuffle
                        IconButton(
                            onClick = { viewModel.toggleShuffle() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.shuffleEnabled) ZentryxLime else Color(0xFF475569),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Previous Track
                        IconButton(
                            onClick = { viewModel.skipToPrevious() },
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(MbuxSurfaceVariant)
                                .border(1.dp, MbuxAmbientBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Central Play/Pause in Electric Lime (#A3E635)
                        IconButton(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(68.dp)
                                .clip(CircleShape)
                                .background(ZentryxLime)
                        ) {
                            if (playbackState.isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = Color.Black,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }

                        // Next Track
                        IconButton(
                            onClick = { viewModel.skipToNext() },
                            modifier = Modifier
                                .size(58.dp)
                                .clip(CircleShape)
                                .background(MbuxSurfaceVariant)
                                .border(1.dp, MbuxAmbientBorder, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp)
                            )
                        }

                        // Repeat Mode
                        IconButton(
                            onClick = { viewModel.cycleRepeatMode() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            val (icon, tint) = when (playbackState.repeatMode) {
                                RepeatMode.OFF -> Icons.Default.Repeat to Color(0xFF475569)
                                RepeatMode.ALL -> Icons.Default.Repeat to ZentryxLime
                                RepeatMode.ONE -> Icons.Default.RepeatOne to ZentryxLime
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = "Repeat",
                                tint = tint,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
