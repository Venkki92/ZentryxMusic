package com.zentryx.music.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.zentryx.music.dsp.FACTORY_DSP_PRESETS
import com.zentryx.music.models.Album
import com.zentryx.music.models.Artist
import com.zentryx.music.models.PlaybackState
import com.zentryx.music.models.Playlist
import com.zentryx.music.models.ScanStatus
import com.zentryx.music.models.Song
import com.zentryx.music.network.webdav.DiagnosticProbeEntry
import com.zentryx.music.network.webdav.DiagnosticStepStatus
import com.zentryx.music.network.webdav.ScanDiagnosticsState
import com.zentryx.music.ui.components.DriveModeHudDialog
import com.zentryx.music.ui.components.MiniPlayer
import com.zentryx.music.ui.components.StreamCodecInspectorDialog
import com.zentryx.music.ui.components.rememberVoiceSearchLauncher
import com.zentryx.music.viewmodel.LibraryTab
import com.zentryx.music.viewmodel.LibraryViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onLogout: () -> Unit,
    onNavigateToDsp: () -> Unit = {},
    onNavigateBack: () -> Unit = {},
    onNavigateForward: () -> Unit = {},
    canGoBack: Boolean = false,
    canGoForward: Boolean = false,
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val libraryQuery by viewModel.libraryQuery.collectAsStateWithLifecycle()
    val pagedSongs = viewModel.pagedSongs.collectAsLazyPagingItems()
    val allSongs by viewModel.allSongs.collectAsStateWithLifecycle()
    val filteredSongs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val playbackState by viewModel.playerManager.playbackState.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
    val recentSongs by viewModel.recentSongs.collectAsStateWithLifecycle()
    val mostPlayedSongs by viewModel.mostPlayedSongs.collectAsStateWithLifecycle()
    val resumableSongs by viewModel.resumableSongs.collectAsStateWithLifecycle()
    val isOfflineOnly by viewModel.isOfflineOnly.collectAsStateWithLifecycle()
    val dualPaneCockpit by viewModel.dualPaneCockpit.collectAsStateWithLifecycle()
    val driveModeAutoSpeedKmH by viewModel.driveModeAutoSpeedKmH.collectAsStateWithLifecycle()
    val currentSpeedKmH by viewModel.currentSpeedKmH.collectAsStateWithLifecycle()
    val dspSettings by viewModel.dspSettings.collectAsStateWithLifecycle()
    val activeOutputRoute by viewModel.activeOutputRoute.collectAsStateWithLifecycle()
    val isAttenuated by viewModel.isAttenuated.collectAsStateWithLifecycle()

    val folderBrowserPath by viewModel.folderBrowserPath.collectAsStateWithLifecycle()
    val subFoldersInPath by viewModel.subFoldersInPath.collectAsStateWithLifecycle()
    val songsInFolderPath by viewModel.songsInFolderPath.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylist by viewModel.selectedPlaylist.collectAsStateWithLifecycle()
    val selectedPlaylistSongs by viewModel.selectedPlaylistSongs.collectAsStateWithLifecycle()
    val batchProgress by viewModel.batchProgress.collectAsStateWithLifecycle()
    val isLocalScanning by viewModel.isLocalScanning.collectAsStateWithLifecycle()
    val playlistSyncMessage by viewModel.playlistSyncMessage.collectAsStateWithLifecycle()
    val diagnosticsState by viewModel.diagnosticsState.collectAsStateWithLifecycle()
    val showDiagnosticsDialog by viewModel.showDiagnosticsDialog.collectAsStateWithLifecycle()

    val isWideViewport = LocalConfiguration.current.screenWidthDp >= 820
    val isDualPaneActive = dualPaneCockpit && isWideViewport

    val accent = MaterialTheme.colorScheme.primary
    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderCol = MaterialTheme.colorScheme.outline

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val songsListState = rememberLazyListState()
    val albumsGridState = rememberLazyGridState()
    val artistsListState = rememberLazyListState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var songToAddPlaylist by remember { mutableStateOf<Song?>(null) }
    var showDriveModeHud by remember { mutableStateOf(false) }
    var driveModeDismissedThisTrip by remember { mutableStateOf(false) }
    var showCodecInspector by remember { mutableStateOf(false) }
    var voiceFeedbackMsg by remember { mutableStateOf<String?>(null) }

    val launchVoiceSearch = rememberVoiceSearchLauncher(
        onResult = { spoken -> viewModel.handleVoiceSearchResult(spoken) },
        onUnavailable = { voiceFeedbackMsg = "Voice recognizer unavailable on this headunit" }
    )

    LaunchedEffect(currentSpeedKmH, driveModeAutoSpeedKmH, playbackState.currentSong) {
        if (driveModeAutoSpeedKmH > 0 && currentSpeedKmH >= driveModeAutoSpeedKmH && !driveModeDismissedThisTrip && playbackState.currentSong != null) {
            showDriveModeHud = true
        }
    }

    val usbFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            viewModel.scanLocalAudio(treeUri)
        }
    }

    val audioPermission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_AUDIO
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val audioPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.scanLocalAudio()
    }
    val triggerLocalScan = {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            audioPermission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.scanLocalAudio()
        } else {
            audioPermLauncher.launch(audioPermission)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ZENTRYX",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = Color.White
                            )
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "MUSIC",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 2.sp,
                                color = accent
                            )
                        )
                        if (isOfflineOnly) {
                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = accent.copy(alpha = 0.16f),
                                modifier = Modifier.border(0.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            ) {
                                Text(
                                    text = "OFFLINE ONLY",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accent,
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
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Menu Back",
                                tint = if (canGoBack) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        IconButton(
                            onClick = onNavigateForward,
                            enabled = canGoForward,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Menu Forward",
                                tint = if (canGoForward) Color.White else Color(0xFF475569),
                                modifier = Modifier.size(21.dp)
                            )
                        }
                    }
                },
                actions = {
                    if (isWideViewport) {
                        IconButton(
                            onClick = { viewModel.setDualPaneCockpit(!dualPaneCockpit) },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ViewSidebar,
                                contentDescription = "Toggle Dual-Pane Cockpit",
                                tint = if (dualPaneCockpit) accent else Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(
                        onClick = { showDriveModeHud = true },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Drive Mode HUD",
                            tint = if (showDriveModeHud) accent else Color.White
                        )
                    }

                    IconButton(
                        onClick = launchVoiceSearch,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = "Voice Search",
                            tint = accent
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleOfflineOnly() },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = if (isOfflineOnly) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                            contentDescription = "Offline Mode",
                            tint = if (isOfflineOnly) accent else Color(0xFF94A3B8)
                        )
                    }

                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }

                    if (scanProgress.isScanning) {
                        Button(
                            onClick = { viewModel.cancelScan() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Cancel (${scanProgress.filesProcessed})",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = 11.sp
                            )
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.startScan() },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = "Scan", tint = accent)
                        }
                    }

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF94A3B8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor
                ),
                modifier = Modifier.height(46.dp)
            )
        },
        bottomBar = {
            if (!isDualPaneActive) {
                MiniPlayer(
                    playbackState = playbackState,
                    onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                    onSkipNext = { viewModel.playerManager.skipToNext() },
                    onOpenPlayer = onNavigateToPlayer
                )
            }
        },
        containerColor = bgColor,
        modifier = modifier
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isDualPaneActive) {
                CockpitNowPlayingPane(
                    playbackState = playbackState,
                    speedKmH = currentSpeedKmH,
                    isAttenuated = isAttenuated,
                    dspPresetId = dspSettings.selectedPresetId,
                    accent = accent,
                    surfaceColor = surfaceColor,
                    borderCol = borderCol,
                    onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                    onSkipPrev = { viewModel.playerManager.skipToPrevious() },
                    onSkipNext = { viewModel.playerManager.skipToNext() },
                    onSeekTo = { pos -> viewModel.playerManager.seekTo(pos) },
                    onToggleFavorite = { viewModel.toggleFavoriteCurrentSong() },
                    onToggleAttenuation = { viewModel.toggleAttenuation() },
                    onOpenDriveHud = { showDriveModeHud = true },
                    onInspectCodec = { showCodecInspector = true },
                    onOpenFullPlayer = onNavigateToPlayer,
                    onOpenDsp = onNavigateToDsp,
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                )
                VerticalDivider(color = Color(0xFF1E293B), thickness = 1.dp)
            }
            Column(
                modifier = Modifier
                    .weight(if (isDualPaneActive) 0.65f else 1f)
                    .fillMaxHeight()
            ) {
            if (scanProgress.isScanning) {
                Surface(
                    color = accent.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Syncing WebDAV: ${scanProgress.currentFolder.ifBlank { "Connecting…" }} (${scanProgress.filesProcessed} tracks)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = accent),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (scanProgress.status == ScanStatus.ERROR && !scanProgress.errorMessage.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sync Error: ${scanProgress.errorMessage}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = MaterialTheme.colorScheme.onErrorContainer),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TextButton(onClick = { viewModel.startScan() }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("Retry", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }
            }

            // Batch Album / Favorites Download Banner
            val bp = batchProgress
            if (bp != null) {
                Surface(
                    color = accent.copy(alpha = 0.16f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = accent)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline Batch Pinning: ${bp.first} / ${bp.second} tracks saved…",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }
            }

            // Playlist / Queue / Voice / Local Scan Feedback Banner
            val syncMsg = playlistSyncMessage ?: voiceFeedbackMsg
            if (!syncMsg.isNullOrBlank()) {
                Surface(
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = syncMsg,
                            fontSize = 11.sp,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            viewModel.clearPlaylistSyncMessage()
                            voiceFeedbackMsg = null
                        }, modifier = Modifier.size(26.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            // Playback / Offline restriction banner
            val playbackError = playbackState.errorMessage
            if (!playbackError.isNullOrBlank()) {
                Surface(
                    color = Color(0xFF78350F),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = Color(0xFFFDE68A), modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = playbackError,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = Color.White),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { viewModel.playerManager.clearErrorMessage() },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Real-time Library Search Bar
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = surfaceColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
                    .border(
                        width = if (libraryQuery.isNotBlank()) 1.dp else 0.5.dp,
                        color = if (libraryQuery.isNotBlank()) accent else Color(0xFF1E293B),
                        shape = RoundedCornerShape(10.dp)
                    )
            ) {
                TextField(
                    value = libraryQuery,
                    onValueChange = { viewModel.updateLibraryQuery(it) },
                    placeholder = {
                        Text(
                            text = "Search songs, artists, albums...",
                            color = Color(0xFF64748B),
                            fontSize = 14.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Filter Library",
                            tint = Color(0xFF94A3B8)
                        )
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = launchVoiceSearch, modifier = Modifier.size(40.dp)) {
                                Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice Search", tint = accent)
                            }
                            if (libraryQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { viewModel.clearLibraryQuery() },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // MBUX Low-Profile Navigation Tabs + Smart Shelves
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = bgColor,
                contentColor = accent,
                edgePadding = 12.dp,
                divider = {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                },
                modifier = Modifier.height(40.dp)
            ) {
                val songsDisplayCount = if (libraryQuery.isNotBlank()) filteredSongs.size else pagedSongs.itemCount
                Tab(
                    selected = selectedTab == LibraryTab.SONGS,
                    onClick = { viewModel.selectTab(LibraryTab.SONGS) },
                    text = { Text("SONGS ($songsDisplayCount)", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (selectedTab == LibraryTab.SONGS) accent else Color(0xFF94A3B8)) }
                )
                Tab(
                    selected = selectedTab == LibraryTab.ALBUMS,
                    onClick = { viewModel.selectTab(LibraryTab.ALBUMS) },
                    text = { Text("ALBUMS (${albums.size})", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (selectedTab == LibraryTab.ALBUMS) accent else Color(0xFF94A3B8)) }
                )
                Tab(
                    selected = selectedTab == LibraryTab.ARTISTS,
                    onClick = { viewModel.selectTab(LibraryTab.ARTISTS) },
                    text = { Text("ARTISTS (${artists.size})", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (selectedTab == LibraryTab.ARTISTS) accent else Color(0xFF94A3B8)) }
                )
                Tab(
                    selected = selectedTab == LibraryTab.FOLDERS,
                    onClick = { viewModel.selectTab(LibraryTab.FOLDERS) },
                    text = { Text("FOLDERS", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (selectedTab == LibraryTab.FOLDERS) accent else Color(0xFF94A3B8)) }
                )
                Tab(
                    selected = selectedTab == LibraryTab.PLAYLISTS,
                    onClick = { viewModel.selectTab(LibraryTab.PLAYLISTS) },
                    text = { Text("PLAYLISTS (${playlists.size})", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (selectedTab == LibraryTab.PLAYLISTS) accent else Color(0xFF94A3B8)) }
                )
                Tab(
                    selected = selectedTab == LibraryTab.RECENT,
                    onClick = { viewModel.selectTab(LibraryTab.RECENT) },
                    text = { Text("RECENT (${recentSongs.size})", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (selectedTab == LibraryTab.RECENT) accent else Color(0xFF94A3B8)) }
                )
                Tab(
                    selected = selectedTab == LibraryTab.MOST_PLAYED,
                    onClick = { viewModel.selectTab(LibraryTab.MOST_PLAYED) },
                    text = { Text("MOST PLAYED (${mostPlayedSongs.size})", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (selectedTab == LibraryTab.MOST_PLAYED) accent else Color(0xFF94A3B8)) }
                )
                Tab(
                    selected = selectedTab == LibraryTab.RESUME,
                    onClick = { viewModel.selectTab(LibraryTab.RESUME) },
                    text = { Text("RESUME (${resumableSongs.size})", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (selectedTab == LibraryTab.RESUME) accent else Color(0xFF94A3B8)) }
                )
                Tab(
                    selected = selectedTab == LibraryTab.FAVORITES,
                    onClick = { viewModel.selectTab(LibraryTab.FAVORITES) },
                    text = { Text("FAVORITES (${favoriteSongs.size})", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (selectedTab == LibraryTab.FAVORITES) accent else Color(0xFF94A3B8)) }
                )
                Tab(
                    selected = selectedTab == LibraryTab.OFFLINE,
                    onClick = { viewModel.selectTab(LibraryTab.OFFLINE) },
                    text = { Text("OFFLINE (${downloadedSongs.size})", fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = if (selectedTab == LibraryTab.OFFLINE) accent else Color(0xFF94A3B8)) }
                )
            }

            // Tab Viewport
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    LibraryTab.SONGS -> {
                        if (libraryQuery.isNotBlank()) {
                            if (filteredSongs.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No results found for \"$libraryQuery\"",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(filteredSongs, key = { it.id }) { song ->
                                        val isCurrent = playbackState.currentSong?.id == song.id
                                        SongListItem(
                                            song = song,
                                            isCurrent = isCurrent,
                                            isPlaying = isCurrent && playbackState.isPlaying,
                                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                                            onDownload = { viewModel.downloadSong(song) },
                                            onAddToPlaylist = { songToAddPlaylist = song },
                                            onPlayNext = { viewModel.playNext(song) },
                                            onAddToQueue = { viewModel.addToQueue(song) },
                                            onClick = { viewModel.playSong(song, filteredSongs) }
                                        )
                                    }
                                }
                            }
                        } else if (pagedSongs.itemCount == 0) {
                            EmptyLibraryView(
                                onScan = { viewModel.startScan() },
                                onScanLocal = triggerLocalScan,
                                onPickUsbFolder = { usbFolderPicker.launch(null) }
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = songsListState,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentPadding = PaddingValues(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(
                                        count = pagedSongs.itemCount,
                                        key = { index -> pagedSongs[index]?.id ?: index }
                                    ) { index ->
                                        val song = pagedSongs[index]
                                        if (song != null) {
                                            val isCurrent = playbackState.currentSong?.id == song.id
                                            SongListItem(
                                                song = song,
                                                isCurrent = isCurrent,
                                                isPlaying = isCurrent && playbackState.isPlaying,
                                                onToggleFavorite = { viewModel.toggleFavorite(song) },
                                                onDownload = { viewModel.downloadSong(song) },
                                                onAddToPlaylist = { songToAddPlaylist = song },
                                                onPlayNext = { viewModel.playNext(song) },
                                                onAddToQueue = { viewModel.addToQueue(song) },
                                                onClick = { viewModel.playSong(song) }
                                            )
                                        }
                                    }
                                }
                                AlphabetQuickJumpRail(
                                    accent = accent,
                                    onSelectLetter = { letter ->
                                        val snapIdx = pagedSongs.itemSnapshotList.items.indexOfFirst { item ->
                                            val ch = item?.title?.trim()?.firstOrNull()?.uppercaseChar()
                                            if (letter == '#') ch != null && !ch.isLetter() else ch == letter
                                        }
                                        val fallbackIdx = if (snapIdx >= 0) {
                                            snapIdx
                                        } else {
                                            val allIdx = allSongs.indexOfFirst { s ->
                                                val ch = s.title.trim().firstOrNull()?.uppercaseChar()
                                                if (letter == '#') ch != null && !ch.isLetter() else ch == letter
                                            }
                                            allIdx.coerceIn(0, (pagedSongs.itemCount - 1).coerceAtLeast(0))
                                        }
                                        coroutineScope.launch {
                                            songsListState.scrollToItem(fallbackIdx)
                                        }
                                    }
                                )
                            }
                        }
                    }

                    LibraryTab.ALBUMS -> {
                        if (albums.isEmpty()) {
                            EmptyLibraryView(
                                onScan = { viewModel.startScan() },
                                onScanLocal = triggerLocalScan,
                                onPickUsbFolder = { usbFolderPicker.launch(null) }
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxSize()) {
                                LazyVerticalGrid(
                                    state = albumsGridState,
                                    columns = GridCells.Adaptive(minSize = 145.dp),
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    items(albums, key = { "${it.name}_${it.artist}" }) { album ->
                                        AlbumGridItem(
                                            album = album,
                                            onDownloadAlbum = { viewModel.downloadAlbum(album) },
                                            onClick = { viewModel.playAlbum(album) }
                                        )
                                    }
                                }
                                AlphabetQuickJumpRail(
                                    accent = accent,
                                    onSelectLetter = { letter ->
                                        val idx = albums.indexOfFirst { album ->
                                            val ch = album.name.trim().firstOrNull()?.uppercaseChar()
                                            if (letter == '#') ch != null && !ch.isLetter() else ch == letter
                                        }
                                        if (idx >= 0) {
                                            coroutineScope.launch {
                                                albumsGridState.scrollToItem(idx)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    LibraryTab.ARTISTS -> {
                        if (artists.isEmpty()) {
                            EmptyLibraryView(
                                onScan = { viewModel.startScan() },
                                onScanLocal = triggerLocalScan,
                                onPickUsbFolder = { usbFolderPicker.launch(null) }
                            )
                        } else {
                            Row(modifier = Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = artistsListState,
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    contentPadding = PaddingValues(start = 12.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(artists, key = { it.name }) { artist ->
                                        ArtistListItem(artist = artist)
                                    }
                                }
                                AlphabetQuickJumpRail(
                                    accent = accent,
                                    onSelectLetter = { letter ->
                                        val idx = artists.indexOfFirst { artist ->
                                            val ch = artist.name.trim().firstOrNull()?.uppercaseChar()
                                            if (letter == '#') ch != null && !ch.isLetter() else ch == letter
                                        }
                                        if (idx >= 0) {
                                            coroutineScope.launch {
                                                artistsListState.scrollToItem(idx)
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    LibraryTab.FOLDERS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Folder Breadcrumbs + USB Scanner Action Strip
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(surfaceColor.copy(alpha = 0.65f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (folderBrowserPath.isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { viewModel.navigateFolderUp() },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Up", fontSize = 11.sp)
                                    }
                                }
                                Text(
                                    text = if (folderBrowserPath.isEmpty()) "/ Root Folders" else "/ $folderBrowserPath",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.pinFolderOffline(folderBrowserPath) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.DownloadForOffline, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Pin Folder", fontSize = 10.sp, color = accent, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.startScanDiagnostics() },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.NetworkCheck, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Scan Diagnostics", fontSize = 10.sp, color = Color.White, maxLines = 1)
                                }
                                OutlinedButton(
                                    onClick = { usbFolderPicker.launch(null) },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Usb, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("USB Folder", fontSize = 10.sp, color = Color.White, maxLines = 1)
                                }
                                Button(
                                    onClick = triggerLocalScan,
                                    enabled = !isLocalScanning,
                                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text(if (isLocalScanning) "Scanning…" else "Scan Local", fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                }
                            }

                            if (subFoldersInPath.isEmpty() && songsInFolderPath.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No folders in this directory.\nScan Nextcloud or Local USB storage.",
                                        color = Color(0xFF64748B),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(subFoldersInPath, key = { "dir_$it" }) { folderName ->
                                        val cleanParent = folderBrowserPath.trim('/')
                                        val subFullPath = if (cleanParent.isEmpty()) folderName else "$cleanParent/$folderName"
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = surfaceColor,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { viewModel.openFolder(folderName) }
                                                .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(imageVector = Icons.Default.Folder, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                                                Spacer(modifier = Modifier.width(12.dp))
                                                Text(
                                                    text = folderName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 14.sp,
                                                    color = Color.White,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                IconButton(
                                                    onClick = { viewModel.pinFolderOffline(subFullPath) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.DownloadForOffline,
                                                        contentDescription = "Pin Folder Offline",
                                                        tint = accent,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                    items(songsInFolderPath, key = { "f_${it.id}" }) { song ->
                                        val isCurrent = playbackState.currentSong?.id == song.id
                                        SongListItem(
                                            song = song,
                                            isCurrent = isCurrent,
                                            isPlaying = isCurrent && playbackState.isPlaying,
                                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                                            onDownload = { viewModel.downloadSong(song) },
                                            onAddToPlaylist = { songToAddPlaylist = song },
                                            onPlayNext = { viewModel.playNext(song) },
                                            onAddToQueue = { viewModel.addToQueue(song) },
                                            onClick = { viewModel.playSong(song, songsInFolderPath) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LibraryTab.PLAYLISTS -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(surfaceColor.copy(alpha = 0.65f))
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val currentPl = selectedPlaylist
                                if (currentPl != null) {
                                    OutlinedButton(
                                        onClick = { viewModel.selectPlaylist(null) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("All Playlists", fontSize = 11.sp)
                                    }
                                    Text(
                                        text = currentPl.name,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Button(
                                        onClick = { viewModel.playPlaylist(currentPl) },
                                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Text(
                                        text = "CUSTOM PLAYLISTS (.M3U8 CLOUD SYNC)",
                                        color = Color(0xFF94A3B8),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedButton(
                                        onClick = { viewModel.syncPlaylistsWithNextcloud() },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Sync .m3u8", fontSize = 10.sp, color = Color.White)
                                    }
                                    Button(
                                        onClick = { showCreatePlaylistDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New Playlist", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            val currentPl = selectedPlaylist
                            if (currentPl != null) {
                                if (selectedPlaylistSongs.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "This playlist is empty.\nTap '+' on any track in Songs or Player to add it.",
                                            color = Color(0xFF64748B),
                                            fontSize = 13.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        items(selectedPlaylistSongs, key = { "pls_${it.id}" }) { song ->
                                            val isCurrent = playbackState.currentSong?.id == song.id
                                            SongListItem(
                                                song = song,
                                                isCurrent = isCurrent,
                                                isPlaying = isCurrent && playbackState.isPlaying,
                                                onToggleFavorite = { viewModel.toggleFavorite(song) },
                                                onDownload = { viewModel.downloadSong(song) },
                                                onRemoveFromPlaylist = { viewModel.removeSongFromPlaylist(currentPl.id, song.id) },
                                                onClick = { viewModel.playSong(song, selectedPlaylistSongs) }
                                            )
                                        }
                                    }
                                }
                            } else if (playlists.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "No custom playlists yet.\nCreate one or Sync .m3u8 from your Nextcloud /Playlists folder.",
                                        color = Color(0xFF64748B),
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(playlists, key = { it.id }) { pl ->
                                        PlaylistListItem(
                                            playlist = pl,
                                            onOpen = { viewModel.selectPlaylist(pl) },
                                            onPlay = { viewModel.playPlaylist(pl) },
                                            onDelete = { viewModel.deletePlaylist(pl.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LibraryTab.RECENT -> {
                        if (recentSongs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No recently played tracks yet.\nStart listening to populate your Recent shelf.",
                                    color = Color(0xFF64748B),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(recentSongs, key = { "rec_${it.id}" }) { song ->
                                    val isCurrent = playbackState.currentSong?.id == song.id
                                    SongListItem(
                                        song = song,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && playbackState.isPlaying,
                                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                                        onDownload = { viewModel.downloadSong(song) },
                                        onAddToPlaylist = { songToAddPlaylist = song },
                                        onPlayNext = { viewModel.playNext(song) },
                                        onAddToQueue = { viewModel.addToQueue(song) },
                                        onClick = { viewModel.playSong(song, recentSongs) }
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.MOST_PLAYED -> {
                        if (mostPlayedSongs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No playback statistics yet.\nYour top headunit rotation tracks appear here automatically.",
                                    color = Color(0xFF64748B),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(mostPlayedSongs, key = { "top_${it.id}" }) { song ->
                                    val isCurrent = playbackState.currentSong?.id == song.id
                                    SongListItem(
                                        song = song,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && playbackState.isPlaying,
                                        badgeLabel = "${song.playCount}x",
                                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                                        onDownload = { viewModel.downloadSong(song) },
                                        onAddToPlaylist = { songToAddPlaylist = song },
                                        onPlayNext = { viewModel.playNext(song) },
                                        onAddToQueue = { viewModel.addToQueue(song) },
                                        onClick = { viewModel.playSong(song, mostPlayedSongs) }
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.RESUME -> {
                        if (resumableSongs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No in-progress tracks or mixes to resume.\nLong DJ sets, podcasts, and audiobooks paused mid-way appear here.",
                                    color = Color(0xFF64748B),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(resumableSongs, key = { "res_${it.id}" }) { song ->
                                    val isCurrent = playbackState.currentSong?.id == song.id
                                    SongListItem(
                                        song = song,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && playbackState.isPlaying,
                                        badgeLabel = "Resume @ ${formatDuration(song.lastPositionMs)}",
                                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                                        onDownload = { viewModel.downloadSong(song) },
                                        onAddToPlaylist = { songToAddPlaylist = song },
                                        onPlayNext = { viewModel.playNext(song) },
                                        onAddToQueue = { viewModel.addToQueue(song) },
                                        onClick = { viewModel.resumeSong(song, resumableSongs) }
                                    )
                                }
                            }
                        }
                    }

                    LibraryTab.FAVORITES -> {
                        if (favoriteSongs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No favorite tracks starred yet.\nTap the heart on any song to save it to your Favorites.",
                                    color = Color(0xFF64748B),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(surfaceColor.copy(alpha = 0.65f))
                                        .padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${favoriteSongs.size} favorite track(s)",
                                        fontSize = 12.sp,
                                        color = Color(0xFF94A3B8),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Button(
                                        onClick = { viewModel.pinAllFavorites() },
                                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Pin All Favorites Offline", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(favoriteSongs, key = { it.id }) { song ->
                                        val isCurrent = playbackState.currentSong?.id == song.id
                                        SongListItem(
                                            song = song,
                                            isCurrent = isCurrent,
                                            isPlaying = isCurrent && playbackState.isPlaying,
                                            onToggleFavorite = { viewModel.toggleFavorite(song) },
                                            onDownload = { viewModel.downloadSong(song) },
                                            onAddToPlaylist = { songToAddPlaylist = song },
                                            onPlayNext = { viewModel.playNext(song) },
                                            onAddToQueue = { viewModel.addToQueue(song) },
                                            onClick = { viewModel.playSong(song, favoriteSongs) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    LibraryTab.OFFLINE -> {
                        if (downloadedSongs.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No offline tracks pinned yet.\nDownload songs or albums to listen through tunnels and dead zones.",
                                    color = Color(0xFF64748B),
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(downloadedSongs, key = { it.id }) { song ->
                                    val isCurrent = playbackState.currentSong?.id == song.id
                                    SongListItem(
                                        song = song,
                                        isCurrent = isCurrent,
                                        isPlaying = isCurrent && playbackState.isPlaying,
                                        onToggleFavorite = { viewModel.toggleFavorite(song) },
                                        onDownload = { viewModel.downloadSong(song) },
                                        onAddToPlaylist = { songToAddPlaylist = song },
                                        onPlayNext = { viewModel.playNext(song) },
                                        onAddToQueue = { viewModel.addToQueue(song) },
                                        onClick = { viewModel.playSong(song, downloadedSongs) }
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

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create New Playlist", color = Color.White) },
            containerColor = surfaceColor,
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    placeholder = { Text("e.g. Night Autobahn Mix") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank()) {
                            viewModel.createPlaylist(newPlaylistName)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel", color = accent) }
            }
        )
    }

    val pendingSong = songToAddPlaylist
    if (pendingSong != null) {
        AlertDialog(
            onDismissRequest = { songToAddPlaylist = null },
            title = { Text("Add \"${pendingSong.title}\" to Playlist", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                                    viewModel.createPlaylist(newPlaylistName)
                                    newPlaylistName = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black)
                        ) { Text("Create") }
                    }
                    playlists.forEach { pl ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.addSongToPlaylist(pl.id, pendingSong.id)
                                    songToAddPlaylist = null
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(pl.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text("${pl.songCount} tracks", color = accent, fontSize = 11.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { songToAddPlaylist = null }) { Text("Close", color = accent) }
            }
        )
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out from Nextcloud?", color = Color.White) },
            text = { Text("Saved credentials and local library index will be cleared from this headunit.", color = Color(0xFF94A3B8)) },
            containerColor = surfaceColor,
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLogout)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = accent)
                }
            }
        )
    }

    if (showDiagnosticsDialog) {
        ScanDiagnosticsDialog(
            state = diagnosticsState,
            accent = accent,
            surfaceColor = surfaceColor,
            onRerun = { viewModel.startScanDiagnostics() },
            onDismiss = { viewModel.dismissScanDiagnostics() }
        )
    }

    if (showDriveModeHud) {
        DriveModeHudDialog(
            playbackState = playbackState,
            speedKmH = currentSpeedKmH,
            isAttenuated = isAttenuated,
            accentColor = accent,
            onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
            onPrevious = { viewModel.playerManager.skipToPrevious() },
            onNext = { viewModel.playerManager.skipToNext() },
            onSeekBack15 = { viewModel.seekBy(-15_000L) },
            onSeekForward15 = { viewModel.seekBy(15_000L) },
            onToggleFavorite = { viewModel.toggleFavoriteCurrentSong() },
            onToggleAttenuation = { viewModel.toggleAttenuation() },
            onVoiceSearch = launchVoiceSearch,
            onDismiss = {
                driveModeDismissedThisTrip = true
                showDriveModeHud = false
            }
        )
    }

    val inspSong = playbackState.currentSong
    if (showCodecInspector && inspSong != null) {
        StreamCodecInspectorDialog(
            song = inspSong,
            dspSettings = dspSettings,
            activeOutputRoute = activeOutputRoute,
            replayGainAppliedDb = playbackState.replayGainAppliedDb,
            accentColor = accent,
            onDismiss = { showCodecInspector = false }
        )
    }
}

@Composable
private fun CockpitNowPlayingPane(
    playbackState: PlaybackState,
    speedKmH: Float,
    isAttenuated: Boolean,
    dspPresetId: String,
    accent: Color,
    surfaceColor: Color,
    borderCol: Color,
    onTogglePlayPause: () -> Unit,
    onSkipPrev: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleAttenuation: () -> Unit,
    onOpenDriveHud: () -> Unit,
    onInspectCodec: () -> Unit,
    onOpenFullPlayer: () -> Unit,
    onOpenDsp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val song = playbackState.currentSong
    val presetName = FACTORY_DSP_PRESETS.firstOrNull { it.id == dspPresetId }?.name ?: dspPresetId

    Column(
        modifier = modifier
            .background(Color(0xFF060B16))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "COCKPIT DECK",
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.4.sp,
                color = accent
            )
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = surfaceColor,
                modifier = Modifier
                    .border(0.5.dp, borderCol, RoundedCornerShape(6.dp))
                    .clickable { onOpenDsp() }
            ) {
                Text(
                    text = presetName,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (song == null) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = accent.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tap any track to launch", color = Color(0xFF64748B), fontSize = 12.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(134.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(surfaceColor)
                        .border(1.dp, borderCol, RoundedCornerShape(14.dp))
                        .clickable { onOpenFullPlayer() },
                    contentAlignment = Alignment.Center
                ) {
                    if (song.artworkPath != null && File(song.artworkPath).exists()) {
                        AsyncImage(
                            model = File(song.artworkPath),
                            contentDescription = song.album,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = accent, modifier = Modifier.size(52.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = song.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )

                val badge = playbackState.signalPathBadge ?: song.signalPathSummary
                if (badge.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = accent.copy(alpha = 0.12f),
                        modifier = Modifier
                            .border(0.5.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .clickable { onInspectCodec() }
                    ) {
                        Text(
                            text = badge,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Slider(
                    value = playbackState.currentPositionMs.toFloat().coerceIn(0f, playbackState.durationMs.toFloat().coerceAtLeast(1f)),
                    onValueChange = { onSeekTo(it.toLong()) },
                    valueRange = 0f..(playbackState.durationMs.toFloat().coerceAtLeast(1f)),
                    colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = Color(0xFF1E293B)),
                    modifier = Modifier.fillMaxWidth().height(26.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatDuration(playbackState.currentPositionMs), fontSize = 10.sp, color = accent, fontFamily = FontFamily.Monospace)
                    Text("${speedKmH.toInt()} km/h", fontSize = 10.sp, color = Color(0xFF94A3B8), fontFamily = FontFamily.Monospace)
                    Text(formatDuration(playbackState.durationMs), fontSize = 10.sp, color = Color(0xFF64748B), fontFamily = FontFamily.Monospace)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) accent else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onSkipPrev, modifier = Modifier.size(44.dp)) {
                    Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(accent)
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = onSkipNext, modifier = Modifier.size(44.dp)) {
                    Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = onToggleAttenuation, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.Default.VolumeDown,
                        contentDescription = "-20dB Duck",
                        tint = if (isAttenuated) Color(0xFFF59E0B) else Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onOpenDriveHud,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).height(32.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Drive HUD", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = onOpenFullPlayer,
                    colors = ButtonDefaults.buttonColors(containerColor = accent.copy(alpha = 0.18f), contentColor = accent),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier.weight(1f).height(32.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Full Deck", fontSize = 10.sp, color = accent, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ScanDiagnosticsDialog(
    state: ScanDiagnosticsState,
    accent: Color,
    surfaceColor: Color,
    onRerun: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0B1120),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(min = 280.dp, max = 560.dp)
                .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Nextcloud WebDAV Scan Diagnostics",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = if (state.isRunning) state.currentStepText else "Step-by-step HTTP status & folder permissions",
                                color = Color(0xFF94A3B8),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF94A3B8))
                    }
                }

                if (state.isRunning) {
                    LinearProgressIndicator(
                        color = accent,
                        trackColor = Color(0xFF1E293B),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }

                val summary = state.summaryMessage
                if (!summary.isNullOrBlank()) {
                    val hasFail = state.entries.any { it.status == DiagnosticStepStatus.FAIL }
                    val hasWarn = state.entries.any { it.status == DiagnosticStepStatus.WARN }
                    val summaryColor = when {
                        hasFail -> Color(0xFFEF4444)
                        hasWarn -> Color(0xFFF59E0B)
                        else -> Color(0xFF22C55E)
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = summaryColor.copy(alpha = 0.12f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, summaryColor.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    ) {
                        Text(
                            text = summary,
                            color = summaryColor,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(min = 140.dp, max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(state.entries, key = { it.id }) { entry ->
                        DiagnosticProbeRow(entry = entry, accent = accent, surfaceColor = surfaceColor)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onRerun,
                        enabled = !state.isRunning,
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-run Check", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Text("Close", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticProbeRow(
    entry: DiagnosticProbeEntry,
    accent: Color,
    surfaceColor: Color
) {
    val badgeColor = when (entry.status) {
        DiagnosticStepStatus.PASS -> Color(0xFF22C55E)
        DiagnosticStepStatus.WARN -> Color(0xFFF59E0B)
        DiagnosticStepStatus.FAIL -> Color(0xFFEF4444)
        DiagnosticStepStatus.RUNNING -> accent
    }
    val httpText = when (val code = entry.httpStatusCode) {
        null -> if (entry.status == DiagnosticStepStatus.RUNNING) "…" else "ERR"
        else -> "HTTP $code"
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = surfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, badgeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = entry.stepTitle,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (entry.latencyMs > 0L) {
                        Text(
                            text = "${entry.latencyMs} ms",
                            color = Color(0xFF64748B),
                            fontSize = 10.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = badgeColor.copy(alpha = 0.16f),
                        modifier = Modifier.border(0.5.dp, badgeColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = httpText,
                            color = badgeColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Text(
                text = entry.targetPath,
                color = accent.copy(alpha = 0.9f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Permissions: ${entry.permissionsLabel}",
                color = Color(0xFF38BDF8),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = entry.detailMessage,
                color = Color(0xFF94A3B8),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AlphabetQuickJumpRail(
    accent: Color,
    onSelectLetter: (Char) -> Unit
) {
    val letters = remember { listOf('#') + ('A'..'Z').toList() }
    Column(
        modifier = Modifier
            .width(24.dp)
            .fillMaxHeight()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        letters.forEach { ch ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { onSelectLetter(ch) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = ch.toString(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent.copy(alpha = 0.85f)
                )
            }
        }
    }
}

@Composable
private fun PlaylistListItem(
    playlist: Playlist,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val borderCol = MaterialTheme.colorScheme.outline
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpen() }
            .border(0.5.dp, borderCol, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = Icons.Default.QueueMusic, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(playlist.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${playlist.songCount} track(s)", fontSize = 11.sp, color = Color(0xFF94A3B8))
            }
            IconButton(onClick = onPlay, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play Playlist", tint = accent)
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Delete Playlist", tint = Color(0xFF64748B))
            }
        }
    }
}

@Composable
private fun SongListItem(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    badgeLabel: String? = null,
    onToggleFavorite: () -> Unit = {},
    onDownload: () -> Unit = {},
    onAddToPlaylist: (() -> Unit)? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onAddToQueue: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val borderCol = MaterialTheme.colorScheme.outline

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrent) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isCurrent) 1.dp else 0.5.dp,
                color = if (isCurrent) accent else Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            if (song.artworkPath != null && File(song.artworkPath).exists()) {
                val thumbRequest = remember(song.artworkPath) {
                    ImageRequest.Builder(context)
                        .data(File(song.artworkPath))
                        .size(128)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = thumbRequest,
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(0.5.dp, borderCol, RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCurrent) accent else Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Equalizer else Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = if (isCurrent) Color.Black else Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isCurrent) accent else Color.White,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = if (song.sourceType == "LOCAL") "[USB] ${song.artist} • ${song.album}" else "${song.artist} • ${song.album}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = Color(0xFF94A3B8),
                            fontSize = 12.sp
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (!badgeLabel.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = badgeLabel,
                                color = accent,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onPlayNext != null) {
                    IconButton(
                        onClick = onPlayNext,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = "Play Next",
                            tint = accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                if (onAddToQueue != null) {
                    IconButton(
                        onClick = onAddToQueue,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddToQueue,
                            contentDescription = "Add to Queue",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) accent else Color(0xFF64748B),
                        modifier = Modifier.size(17.dp)
                    )
                }

                if (song.sourceType != "LOCAL") {
                    IconButton(
                        onClick = onDownload,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = if (song.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                            contentDescription = "Download Offline",
                            tint = if (song.isDownloaded) accent else Color(0xFF64748B),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                if (onAddToPlaylist != null) {
                    IconButton(
                        onClick = onAddToPlaylist,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Add to Playlist",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                if (onRemoveFromPlaylist != null) {
                    IconButton(
                        onClick = onRemoveFromPlaylist,
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = "Remove from Playlist",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = accent,
                        modifier = Modifier.border(0.5.dp, borderCol, RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = if (song.isHighRes) "${song.format.name} HR" else song.format.name,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDuration(song.durationMs),
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumGridItem(
    album: Album,
    onDownloadAlbum: () -> Unit,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val borderCol = MaterialTheme.colorScheme.outline

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, borderCol, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            val context = LocalContext.current
            if (album.artworkPath != null && File(album.artworkPath).exists()) {
                val coverRequest = remember(album.artworkPath) {
                    ImageRequest.Builder(context)
                        .data(File(album.artworkPath))
                        .size(320)
                        .crossfade(true)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = coverRequest,
                    contentDescription = album.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Album,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = accent
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = album.name,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${album.songCount} tracks",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                IconButton(
                    onClick = onDownloadAlbum,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadForOffline,
                        contentDescription = "Download Album",
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistListItem(artist: Artist) {
    val accent = MaterialTheme.colorScheme.primary
    val borderCol = MaterialTheme.colorScheme.outline

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, borderCol, RoundedCornerShape(10.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(accent.copy(alpha = 0.15f))
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(21.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.albumCount} albums • ${artist.songCount} tracks",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

@Composable
private fun EmptyLibraryView(
    onScan: () -> Unit,
    onScanLocal: () -> Unit = {},
    onPickUsbFolder: () -> Unit = {}
) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CloudQueue,
                contentDescription = null,
                modifier = Modifier.size(54.dp),
                tint = accent
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Library is Empty",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Scan your Nextcloud WebDAV folders or Local USB / Device audio storage.",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onScan,
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Nextcloud", fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onScanLocal,
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Storage, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan Device", color = Color.White, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(
                    onClick = onPickUsbFolder,
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(imageVector = Icons.Default.Usb, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick USB Folder", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
