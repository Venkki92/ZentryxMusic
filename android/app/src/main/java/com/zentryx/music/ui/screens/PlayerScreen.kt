package com.zentryx.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import com.zentryx.music.player.CockpitThemeMode
import com.zentryx.music.player.SwapGroupMode
import com.zentryx.music.player.SwapMediaGroup
import com.zentryx.music.player.extractFolderKey
import com.zentryx.music.player.findCurrentSwapGroupIndex
import com.zentryx.music.ui.components.CrossfadeAlbumArt
import com.zentryx.music.ui.components.DriveModeHudDialog
import com.zentryx.music.ui.components.FullscreenVisualizerDialog
import com.zentryx.music.ui.components.MercedesVisualizerCanvas
import com.zentryx.music.ui.components.StreamCodecInspectorDialog
import com.zentryx.music.ui.components.rememberVoiceSearchLauncher
import com.zentryx.music.viewmodel.PlayerViewModel
import com.zentryx.music.visualizer.VisualizerColorway
import com.zentryx.music.visualizer.VisualizerMode
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

private enum class CockpitRailTab(val label: String) {
    FOLDERS("Folders"),
    ALBUMS("Albums"),
    QUEUE("Queue")
}

/**
 * MBUX-style cockpit player optimized for 8-inch 1024x600 landscape automotive displays.
 * Supports 6 interactive Mercedes visualizers + colorway presets + fullscreen mode, Pro 16-Band DSP link,
 * swipe-to-skip gestures, GPS speed readout, podcast playback rate, sleep timer, and -20dB talk ducking.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit = {},
    canGoBack: Boolean = true,
    canGoForward: Boolean = false,
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
    val activeOutputRoute by viewModel.activeOutputRoute.collectAsStateWithLifecycle()
    val driveModeAutoSpeedKmH by viewModel.driveModeAutoSpeedKmH.collectAsStateWithLifecycle()
    val visualizerFrame by viewModel.visualizerFrame.collectAsStateWithLifecycle()
    val visualizerMode by viewModel.visualizerMode.collectAsStateWithLifecycle()
    val visualizerColorway by viewModel.visualizerColorway.collectAsStateWithLifecycle()
    val accent by viewModel.accentColor.collectAsStateWithLifecycle()
    val playbackRate by viewModel.playbackRate.collectAsStateWithLifecycle()
    val swipeEnabled by viewModel.swipeGesturesEnabled.collectAsStateWithLifecycle()
    val idleScreensaverSec by viewModel.idleScreensaverSeconds.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val activeDownloads by viewModel.activeDownloads.collectAsStateWithLifecycle()
    val downloadFeedback by viewModel.downloadFeedback.collectAsStateWithLifecycle()
    val cockpitThemeMode by viewModel.cockpitThemeMode.collectAsStateWithLifecycle()
    val swapGroupMode by viewModel.swapGroupMode.collectAsStateWithLifecycle()
    val splitScreenRailEnabled by viewModel.splitScreenRailEnabled.collectAsStateWithLifecycle()
    val folderSwapGroups by viewModel.folderSwapGroups.collectAsStateWithLifecycle()
    val albumSwapGroups by viewModel.albumSwapGroups.collectAsStateWithLifecycle()
    val wifiAutoCacheState by viewModel.wifiAutoCacheState.collectAsStateWithLifecycle()

    val accentColor = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val borderColor = MaterialTheme.colorScheme.outline

    var showQueueDrawer by remember { mutableStateOf(false) }
    var showQuickSwapDrawer by remember { mutableStateOf(false) }
    var quickSwapOfflineOnly by remember { mutableStateOf(false) }
    var railTab by remember { mutableStateOf(CockpitRailTab.FOLDERS) }
    var showVisualizerTile by remember { mutableStateOf(true) }
    var showFullscreenVisualizer by remember { mutableStateOf(false) }
    var showDriveModeHud by remember { mutableStateOf(false) }
    var driveModeDismissedThisTrip by remember { mutableStateOf(false) }
    var showCodecInspector by remember { mutableStateOf(false) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionSlider by remember { mutableFloatStateOf(0f) }
    var lastInteractionMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var swipeHudText by remember { mutableStateOf<String?>(null) }

    val activeGroups = if (swapGroupMode == SwapGroupMode.FOLDERS) folderSwapGroups else albumSwapGroups
    val activeGroupIndex = remember(activeGroups, currentSong) {
        findCurrentSwapGroupIndex(activeGroups, currentSong)
    }
    val activeGroup = activeGroups.getOrNull(activeGroupIndex)

    val launchVoiceInDriveHud = rememberVoiceSearchLauncher(
        onResult = { spoken -> swipeHudText = "VOICE: $spoken" },
        onUnavailable = { swipeHudText = "Voice recognizer unavailable" }
    )

    LaunchedEffect(speedKmH, driveModeAutoSpeedKmH, currentSong) {
        if (driveModeAutoSpeedKmH > 0 && speedKmH >= driveModeAutoSpeedKmH && !driveModeDismissedThisTrip && currentSong != null) {
            showDriveModeHud = true
        }
    }

    LaunchedEffect(playbackState.currentPositionMs) {
        if (!isSeeking) {
            seekPositionSlider = playbackState.currentPositionMs.toFloat()
        }
    }

    LaunchedEffect(swipeHudText) {
        if (swipeHudText != null) {
            delay(900L)
            swipeHudText = null
        }
    }

    LaunchedEffect(downloadFeedback?.id) {
        val fb = downloadFeedback
        if (fb != null && fb.progressPercent == null) {
            delay(3500L)
            if (downloadFeedback?.id == fb.id) {
                viewModel.clearDownloadFeedback()
            }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onNavigateBack,
                            enabled = canGoBack,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (canGoBack) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        IconButton(
                            onClick = onNavigateForward,
                            enabled = canGoForward,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (canGoForward) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                },
                actions = {
                    // 1-Tap W203 Day / Night / Auto Amber cycle
                    IconButton(
                        onClick = {
                            val next = viewModel.cycleCockpitThemeMode()
                            swipeHudText = "DIMMER: ${next.label.uppercase(Locale.US)}"
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        val themeIcon = when (cockpitThemeMode) {
                            CockpitThemeMode.DAY -> Icons.Default.WbSunny
                            CockpitThemeMode.NIGHT -> Icons.Default.DarkMode
                            CockpitThemeMode.AUTO -> Icons.Default.BrightnessAuto
                        }
                        Icon(
                            imageVector = themeIcon,
                            contentDescription = "Cycle Day/Night W203 Amber",
                            tint = if (cockpitThemeMode == CockpitThemeMode.AUTO) accentColor else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Cockpit Quick-Swap Folder/Album Drawer trigger
                    IconButton(onClick = { showQuickSwapDrawer = true }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Quick-Swap Folders & Albums",
                            tint = if (showQuickSwapDrawer) accentColor else Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    // Toggle Split-Screen Live Folder/Album Rail
                    IconButton(
                        onClick = {
                            viewModel.toggleSplitScreenRail()
                            showQueueDrawer = false
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ViewSidebar,
                            contentDescription = "Toggle Split-Screen Rail",
                            tint = if (splitScreenRailEnabled) accentColor else Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Drive Mode Large-Target HUD shortcut
                    IconButton(onClick = { showDriveModeHud = true }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Drive Mode HUD",
                            tint = if (showDriveModeHud) accentColor else Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
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
                    val currentDownloadPct = currentSong?.id?.let { activeDownloads[it] }
                    IconButton(
                        onClick = { viewModel.downloadCurrentSong() },
                        enabled = currentDownloadPct == null,
                        modifier = Modifier.size(42.dp)
                    ) {
                        if (currentDownloadPct != null) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(26.dp)) {
                                CircularProgressIndicator(
                                    progress = { (currentDownloadPct / 100f).coerceIn(0.05f, 1f) },
                                    color = accentColor,
                                    trackColor = Color(0xFF1E293B),
                                    strokeWidth = 2.2.dp,
                                    modifier = Modifier.size(24.dp)
                                )
                                Text(
                                    text = "$currentDownloadPct",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Black,
                                    color = accentColor
                                )
                            }
                        } else {
                            Icon(
                                imageVector = if (currentSong?.isDownloaded == true) Icons.Default.CheckCircle else Icons.Default.Download,
                                contentDescription = "Download Offline",
                                tint = if (currentSong?.isDownloaded == true) accentColor else Color.White,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                    IconButton(onClick = { showAddToPlaylistDialog = true }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add to Playlist",
                            tint = Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    IconButton(onClick = { showQueueDrawer = !showQueueDrawer }, modifier = Modifier.size(42.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = "Queue",
                            tint = if (showQueueDrawer) accentColor else Color.White,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                modifier = Modifier.height(48.dp)
            )
        },
        containerColor = bgColor,
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val errorMsg = playbackState.errorMessage
            if (!errorMsg.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF78350F),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMsg,
                            color = Color.White,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearError() }, modifier = Modifier.size(28.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            val dlFeedback = downloadFeedback
            if (dlFeedback != null) {
                Surface(
                    color = if (dlFeedback.isError) Color(0xFF7F1D1D) else accentColor.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (dlFeedback.progressPercent != null) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = accentColor,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (dlFeedback.isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = if (dlFeedback.isError) Color(0xFFFCA5A5) else accentColor,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Text(
                                    text = dlFeedback.message,
                                    color = if (dlFeedback.isError) Color.White else accentColor,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { viewModel.clearDownloadFeedback() }, modifier = Modifier.size(26.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(15.dp))
                            }
                        }
                        val pct = dlFeedback.progressPercent
                        if (pct != null) {
                            LinearProgressIndicator(
                                progress = { (pct / 100f).coerceIn(0f, 1f) },
                                color = accentColor,
                                trackColor = Color(0xFF1E293B),
                                modifier = Modifier.fillMaxWidth().height(2.dp)
                            )
                        }
                    }
                }
            }

        Box(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
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
                                if (dragAccum < -95f) {
                                    viewModel.skipToNext()
                                    swipeHudText = "NEXT TRACK >>"
                                } else if (dragAccum > 95f) {
                                    viewModel.skipToPrevious()
                                    swipeHudText = "<< PREV TRACK"
                                }
                                dragAccum = 0f
                            }
                        )
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Left Pane: Visualizer / Artwork Toggle + Track Metadata (compact when Split Rail is open)
            Column(
                modifier = Modifier
                    .weight(if (splitScreenRailEnabled && !showQueueDrawer) 0.28f else 0.40f)
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
                                    .background(surfaceVariantColor)
                                    .border(1.dp, borderColor, RoundedCornerShape(14.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                CrossfadeAlbumArt(
                                    artworkPath = currentSong.artworkPath,
                                    contentDescription = currentSong.album,
                                    durationMillis = 350,
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = accentColor.copy(alpha = 0.85f),
                                        modifier = Modifier.size(72.dp)
                                    )
                                }
                            }
                        }

                        // Tap-to-inspect Codec Badge
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            contentColor = accentColor,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .border(0.5.dp, borderColor, RoundedCornerShape(4.dp))
                                .clickable { showCodecInspector = true }
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

                    // Compact Visualizer / Artwork / Mode + Colorway Controls
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
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Style", fontSize = 10.sp, color = Color.White)
                            }
                            VisualizerColorway.entries.forEach { cw ->
                                val activeCw = cw == visualizerColorway
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(cw.primaryHex))
                                        .border(
                                            width = if (activeCw) 2.dp else 1.dp,
                                            color = if (activeCw) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable { viewModel.setVisualizerColorway(cw) }
                                )
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
                    val signalBadge = playbackState.signalPathBadge ?: currentSong.signalPathSummary
                    if (signalBadge.isNotBlank()) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accentColor.copy(alpha = 0.12f),
                            modifier = Modifier
                                .border(0.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                .clickable { showCodecInspector = true }
                        ) {
                            Text(
                                text = "$signalBadge · Inspect",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Right Pane (60%): Queue Reorder Drawer OR Seekbar + Transport + Quick QOL Controls
            if (showQueueDrawer) {
                Column(
                    modifier = Modifier
                        .weight(0.60f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(14.dp))
                        .background(surfaceColor)
                        .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "UP-NEXT QUEUE (${playbackState.queue.size})",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = accentColor
                            )
                        )
                        if (playbackState.queue.size > 1) {
                            TextButton(
                                onClick = { viewModel.clearQueue() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                modifier = Modifier.height(26.dp)
                            ) {
                                Text("Clear Upcoming", fontSize = 10.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
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
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (isItemActive) accentColor else Color(0xFF64748B)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.title,
                                            fontWeight = if (isItemActive) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 13.sp,
                                            color = if (isItemActive) accentColor else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(text = item.artist, fontSize = 11.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveQueueItem(index, index - 1) },
                                        enabled = index > 0,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) Color.White else Color(0xFF334155),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveQueueItem(index, index + 1) },
                                        enabled = index < playbackState.queue.lastIndex,
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move Down",
                                            tint = if (index < playbackState.queue.lastIndex) Color.White else Color(0xFF334155),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = { viewModel.removeQueueItem(index) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove",
                                            tint = Color(0xFF94A3B8),
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
                        .weight(if (splitScreenRailEnabled) 0.39f else 0.60f)
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
                                .background(surfaceVariantColor)
                                .border(1.dp, borderColor, CircleShape)
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
                                .background(surfaceVariantColor)
                                .border(1.dp, borderColor, CircleShape)
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

                    // Dedicated On-The-Go Prev / Next Folder (or Album) Swap Bar (48dp touch targets)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val modeNoun = if (swapGroupMode == SwapGroupMode.FOLDERS) "Folder" else "Album"
                        OutlinedButton(
                            onClick = {
                                viewModel.swapToAdjacentGroup(-1) { hud -> swipeHudText = hud }
                            },
                            enabled = activeGroups.size > 1,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous $modeNoun", tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Prev $modeNoun", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
                        }

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = surfaceColor,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                                .clickable { showQuickSwapDrawer = true }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = activeGroup?.displayName ?: extractFolderKey(currentSong).ifBlank { currentSong.album },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (activeGroupIndex >= 0 && activeGroups.isNotEmpty()) {
                                            "$modeNoun ${activeGroupIndex + 1}/${activeGroups.size} · ${activeGroup?.offlineCount ?: 0}/${activeGroup?.trackCount ?: 0} offline"
                                        } else {
                                            "Tap to Quick-Swap"
                                        },
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = accentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Icon(Icons.Default.SwapHoriz, contentDescription = "Swap Drawer", tint = accentColor, modifier = Modifier.size(18.dp))
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                viewModel.swapToAdjacentGroup(1) { hud -> swipeHudText = hud }
                            },
                            enabled = activeGroups.size > 1,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Next $modeNoun", fontSize = 11.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(Icons.Default.SkipNext, contentDescription = "Next $modeNoun", tint = accentColor, modifier = Modifier.size(18.dp))
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

                        FilterChip(
                            selected = dspSettings.dspEnabled && dspSettings.surroundMode != com.zentryx.music.dsp.SurroundMode.OFF,
                            onClick = {
                                val next = viewModel.cycleSurroundMode()
                                swipeHudText = "3D: ${next.label.uppercase(Locale.US)}"
                            },
                            label = {
                                Text(
                                    text = dspSettings.surroundMode.shortLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
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

                // Split-Screen Live Right Rail (Folders | Albums | Queue)
                if (splitScreenRailEnabled) {
                    Column(
                        modifier = Modifier
                            .weight(0.33f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(surfaceColor)
                            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF0A0F1E), RoundedCornerShape(8.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            CockpitRailTab.entries.forEach { tab ->
                                val selected = railTab == tab
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (selected) accentColor else Color.Transparent,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            railTab = tab
                                            when (tab) {
                                                CockpitRailTab.FOLDERS -> viewModel.setSwapGroupMode(SwapGroupMode.FOLDERS)
                                                CockpitRailTab.ALBUMS -> viewModel.setSwapGroupMode(SwapGroupMode.ALBUMS)
                                                CockpitRailTab.QUEUE -> Unit
                                            }
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = tab.label,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (selected) Color.Black else Color(0xFFCBD5E1),
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        when (railTab) {
                            CockpitRailTab.FOLDERS, CockpitRailTab.ALBUMS -> {
                                val railGroups = if (railTab == CockpitRailTab.FOLDERS) folderSwapGroups else albumSwapGroups
                                val railActiveIdx = findCurrentSwapGroupIndex(railGroups, currentSong)
                                if (railGroups.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text("Scan Nextcloud to populate", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        itemsIndexed(railGroups, key = { _, g -> g.key }) { idx, group ->
                                            val isCurrent = idx == railActiveIdx
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isCurrent) accentColor.copy(alpha = 0.16f) else Color(0xFF0A0F1E),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .border(
                                                        width = if (isCurrent) 1.dp else 0.5.dp,
                                                        color = if (isCurrent) accentColor else borderColor.copy(alpha = 0.5f),
                                                        shape = RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable {
                                                        if (viewModel.playSwapGroup(group)) {
                                                            swipeHudText = "${railTab.label.dropLast(1).uppercase(Locale.US)}: ${group.displayName}"
                                                        }
                                                    }
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = group.displayName,
                                                            fontSize = 11.sp,
                                                            fontWeight = if (isCurrent) FontWeight.Black else FontWeight.SemiBold,
                                                            color = if (isCurrent) accentColor else Color.White,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Text(
                                                            text = "${group.trackCount} trk · ${group.offlineCount}/${group.trackCount} offline",
                                                            fontSize = 9.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            color = if (group.isFullyOffline) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                    if (railTab == CockpitRailTab.FOLDERS) {
                                                        IconButton(
                                                            onClick = { viewModel.togglePinFolderForWifiCache(group.key) },
                                                            modifier = Modifier.size(34.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (group.isPinnedForWifiCache) Icons.Default.Wifi else Icons.Default.CloudDownload,
                                                                contentDescription = "Pin folder for Home Wi-Fi Auto-Cache",
                                                                tint = if (group.isPinnedForWifiCache) accentColor else Color(0xFF64748B),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            CockpitRailTab.QUEUE -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    itemsIndexed(playbackState.queue) { index, item ->
                                        val isItemActive = index == playbackState.currentQueueIndex
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isItemActive) accentColor.copy(alpha = 0.16f) else Color(0xFF0A0F1E),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(8.dp))
                                                .clickable { viewModel.playQueueIndex(index) }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${index + 1}",
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isItemActive) accentColor else Color(0xFF64748B),
                                                    modifier = Modifier.width(20.dp)
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = item.title,
                                                        fontSize = 11.sp,
                                                        fontWeight = if (isItemActive) FontWeight.Black else FontWeight.Medium,
                                                        color = if (isItemActive) accentColor else Color.White,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = item.artist,
                                                        fontSize = 9.sp,
                                                        color = Color(0xFF94A3B8),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Transient Swipe-Skip HUD Feedback Pill
        val hud = swipeHudText
        if (hud != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(alpha = 0.88f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .border(1.dp, accentColor, RoundedCornerShape(20.dp))
            ) {
                Text(
                    text = hud,
                    color = accentColor,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
        }
        }
    }

    if (showAddToPlaylistDialog && currentSong != null) {
        AlertDialog(
            onDismissRequest = { showAddToPlaylistDialog = false },
            title = { Text("Add \"${currentSong.title}\" to Playlist", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            containerColor = surfaceColor,
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newPlaylistName,
                            onValueChange = { newPlaylistName = it },
                            placeholder = { Text("New playlist name…", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    viewModel.createPlaylistAndAddCurrent(newPlaylistName)
                                    newPlaylistName = ""
                                    showAddToPlaylistDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black)
                        ) {
                            Text("Create")
                        }
                    }
                    if (playlists.isNotEmpty()) {
                        HorizontalDivider(color = Color(0xFF1E293B))
                        playlists.forEach { pl ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF0F172A),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.addCurrentSongToPlaylist(pl.id)
                                        showAddToPlaylistDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(pl.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("${pl.songCount} tracks", color = accentColor, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAddToPlaylistDialog = false }) {
                    Text("Close", color = accentColor)
                }
            }
        )
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

    if (showDriveModeHud) {
        DriveModeHudDialog(
            playbackState = playbackState,
            speedKmH = speedKmH,
            isAttenuated = isAttenuated,
            accentColor = accentColor,
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onPrevious = { viewModel.skipToPrevious() },
            onNext = { viewModel.skipToNext() },
            onSeekBack15 = { viewModel.seekBy(-15_000L) },
            onSeekForward15 = { viewModel.seekBy(15_000L) },
            onToggleFavorite = { viewModel.toggleFavorite() },
            onToggleAttenuation = { viewModel.toggleAttenuation() },
            onVoiceSearch = launchVoiceInDriveHud,
            onDismiss = {
                driveModeDismissedThisTrip = true
                showDriveModeHud = false
            }
        )
    }

    if (showCodecInspector && currentSong != null) {
        StreamCodecInspectorDialog(
            song = currentSong,
            dspSettings = dspSettings,
            activeOutputRoute = activeOutputRoute,
            replayGainAppliedDb = playbackState.replayGainAppliedDb,
            accentColor = accentColor,
            onDismiss = { showCodecInspector = false }
        )
    }

    // Cockpit Quick-Swap Folder / Album Drawer Dialog (Glove-friendly driving picker + Home Wi-Fi Pin)
    if (showQuickSwapDrawer) {
        val rawGroups = if (swapGroupMode == SwapGroupMode.FOLDERS) folderSwapGroups else albumSwapGroups
        val filteredGroups = if (quickSwapOfflineOnly) rawGroups.filter { it.offlineCount > 0 } else rawGroups
        val currentIdx = findCurrentSwapGroupIndex(filteredGroups, currentSong)

        AlertDialog(
            onDismissRequest = { showQuickSwapDrawer = false },
            containerColor = surfaceColor,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "COCKPIT QUICK-SWAP",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp,
                            color = accentColor
                        )
                        Text(
                            text = wifiAutoCacheState.lastStatusMessage,
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.syncPinnedFoldersNow() },
                        enabled = wifiAutoCacheState.pinnedFolderCount > 0,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, tint = accentColor, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync Pins", fontSize = 10.sp, color = accentColor, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SwapGroupMode.entries.forEach { mode ->
                            FilterChip(
                                selected = swapGroupMode == mode,
                                onClick = { viewModel.setSwapGroupMode(mode) },
                                label = { Text(mode.label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor.copy(alpha = 0.22f),
                                    selectedLabelColor = accentColor
                                )
                            )
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        FilterChip(
                            selected = quickSwapOfflineOnly,
                            onClick = { quickSwapOfflineOnly = !quickSwapOfflineOnly },
                            label = { Text("Offline Ready", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF22C55E).copy(alpha = 0.22f),
                                selectedLabelColor = Color(0xFF4ADE80)
                            )
                        )
                    }

                    if (filteredGroups.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (quickSwapOfflineOnly) "No offline-cached groups match" else "No folders or albums indexed yet",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            itemsIndexed(filteredGroups, key = { _, g -> g.key }) { idx, group ->
                                val isCurrent = idx == currentIdx
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isCurrent) accentColor.copy(alpha = 0.16f) else Color(0xFF0A0F1E),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 52.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(
                                            width = if (isCurrent) 1.dp else 0.75.dp,
                                            color = if (isCurrent) accentColor else borderColor,
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        .clickable {
                                            if (viewModel.playSwapGroup(group)) {
                                                swipeHudText = "${swapGroupMode.label.dropLast(1)} ${idx + 1}/${filteredGroups.size} · ${group.displayName}"
                                                showQuickSwapDrawer = false
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (swapGroupMode == SwapGroupMode.FOLDERS) Icons.Default.Folder else Icons.Default.Album,
                                            contentDescription = null,
                                            tint = if (isCurrent) accentColor else Color(0xFF94A3B8),
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = group.displayName,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrent) accentColor else Color.White,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${group.subtitle} · ${group.trackCount} trk · ${group.offlineCount}/${group.trackCount} offline",
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = if (group.isFullyOffline) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (swapGroupMode == SwapGroupMode.FOLDERS) {
                                            OutlinedButton(
                                                onClick = { viewModel.togglePinFolderForWifiCache(group.key) },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                                modifier = Modifier.height(34.dp),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = if (group.isPinnedForWifiCache) Icons.Default.Wifi else Icons.Default.CloudDownload,
                                                    contentDescription = "Pin for Wi-Fi Auto-Cache",
                                                    tint = if (group.isPinnedForWifiCache) accentColor else Color(0xFF94A3B8),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = if (group.isPinnedForWifiCache) "Pinned" else "Wi-Fi Pin",
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (group.isPinnedForWifiCache) accentColor else Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQuickSwapDrawer = false }) {
                    Text("Close", color = accentColor, fontWeight = FontWeight.Bold)
                }
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
