package com.zentryx.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.zentryx.music.models.Album
import com.zentryx.music.models.Artist
import com.zentryx.music.models.Song
import com.zentryx.music.ui.components.MiniPlayer
import com.zentryx.music.ui.theme.MbuxAmbientBorder
import com.zentryx.music.ui.theme.MbuxObsidian
import com.zentryx.music.ui.theme.MbuxSurface
import com.zentryx.music.ui.theme.MbuxSurfaceVariant
import com.zentryx.music.ui.theme.ZentryxLime
import com.zentryx.music.viewmodel.LibraryTab
import com.zentryx.music.viewmodel.LibraryViewModel
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
    modifier: Modifier = Modifier
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val pagedSongs = viewModel.pagedSongs.collectAsLazyPagingItems()
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val playbackState by viewModel.playerManager.playbackState.collectAsStateWithLifecycle()
    val favoriteSongs by viewModel.favoriteSongs.collectAsStateWithLifecycle()
    val downloadedSongs by viewModel.downloadedSongs.collectAsStateWithLifecycle()
    val isOfflineOnly by viewModel.isOfflineOnly.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }

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
                                color = ZentryxLime
                            )
                        )
                    }
                },
                actions = {
                    // Offline Mode switch
                    IconButton(
                        onClick = { viewModel.toggleOfflineOnly() },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = if (isOfflineOnly) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                            contentDescription = "Offline Mode",
                            tint = if (isOfflineOnly) ZentryxLime else Color(0xFF94A3B8)
                        )
                    }

                    IconButton(
                        onClick = onNavigateToSearch,
                        modifier = Modifier.size(44.dp)
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
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Sync, contentDescription = "Scan", tint = ZentryxLime)
                        }
                    }

                    // Options / Settings Button
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = Color(0xFF94A3B8))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MbuxObsidian
                ),
                modifier = Modifier.height(46.dp)
            )
        },
        bottomBar = {
            MiniPlayer(
                playbackState = playbackState,
                onTogglePlayPause = { viewModel.playerManager.togglePlayPause() },
                onSkipNext = { viewModel.playerManager.skipToNext() },
                onOpenPlayer = onNavigateToPlayer
            )
        },
        containerColor = MbuxObsidian,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Scan progress micro banner if scanning
            if (scanProgress.isScanning) {
                Surface(
                    color = ZentryxLime.copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = ZentryxLime
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Syncing WebDAV: ${scanProgress.currentFolder} (${scanProgress.filesProcessed} tracks)",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, color = ZentryxLime),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // MBUX Low-Profile Navigation Tabs
            ScrollableTabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = MbuxObsidian,
                contentColor = ZentryxLime,
                edgePadding = 12.dp,
                divider = {
                    HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                },
                modifier = Modifier.height(40.dp)
            ) {
                Tab(
                    selected = selectedTab == LibraryTab.SONGS,
                    onClick = { viewModel.selectTab(LibraryTab.SONGS) },
                    text = {
                        Text(
                            text = "SONGS (${pagedSongs.itemCount})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = if (selectedTab == LibraryTab.SONGS) ZentryxLime else Color(0xFF94A3B8)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == LibraryTab.ALBUMS,
                    onClick = { viewModel.selectTab(LibraryTab.ALBUMS) },
                    text = {
                        Text(
                            text = "ALBUMS (${albums.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = if (selectedTab == LibraryTab.ALBUMS) ZentryxLime else Color(0xFF94A3B8)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == LibraryTab.ARTISTS,
                    onClick = { viewModel.selectTab(LibraryTab.ARTISTS) },
                    text = {
                        Text(
                            text = "ARTISTS (${artists.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = if (selectedTab == LibraryTab.ARTISTS) ZentryxLime else Color(0xFF94A3B8)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == LibraryTab.FAVORITES,
                    onClick = { viewModel.selectTab(LibraryTab.FAVORITES) },
                    text = {
                        Text(
                            text = "FAVORITES (${favoriteSongs.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = if (selectedTab == LibraryTab.FAVORITES) ZentryxLime else Color(0xFF94A3B8)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == LibraryTab.OFFLINE,
                    onClick = { viewModel.selectTab(LibraryTab.OFFLINE) },
                    text = {
                        Text(
                            text = "OFFLINE (${downloadedSongs.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            letterSpacing = 1.sp,
                            color = if (selectedTab == LibraryTab.OFFLINE) ZentryxLime else Color(0xFF94A3B8)
                        )
                    }
                )
            }

            // Tab Viewport
            Box(modifier = Modifier.fillMaxSize()) {
                when (selectedTab) {
                    LibraryTab.SONGS -> {
                        if (pagedSongs.itemCount == 0) {
                            EmptyLibraryView(onScan = { viewModel.startScan() })
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
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
                                            onClick = { viewModel.playSong(song) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    LibraryTab.ALBUMS -> {
                        if (albums.isEmpty()) {
                            EmptyLibraryView(onScan = { viewModel.startScan() })
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 145.dp),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(albums, key = { "${it.name}_${it.artist}" }) { album ->
                                    AlbumGridItem(
                                        album = album,
                                        onClick = { viewModel.playAlbum(album) }
                                    )
                                }
                            }
                        }
                    }
                    LibraryTab.ARTISTS -> {
                        if (artists.isEmpty()) {
                            EmptyLibraryView(onScan = { viewModel.startScan() })
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(artists, key = { it.name }) { artist ->
                                    ArtistListItem(artist = artist)
                                }
                            }
                        }
                    }
                    LibraryTab.FAVORITES -> {
                        if (favoriteSongs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No favorite tracks starred yet.\nTap the heart on any song to save it to your Favorites.",
                                    color = Color(0xFF64748B),
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
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
                                        onClick = { viewModel.playSong(song, favoriteSongs) }
                                    )
                                }
                            }
                        }
                    }
                    LibraryTab.OFFLINE -> {
                        if (downloadedSongs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No offline tracks pinned yet.\nDownload songs to listen through tunnels and dead zones.",
                                    color = Color(0xFF64748B),
                                    fontSize = 14.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
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

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out from Nextcloud?", color = Color.White) },
            text = { Text("Saved credentials and local library index will be cleared from this headunit.", color = Color(0xFF94A3B8)) },
            containerColor = MbuxSurface,
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
                    Text("Cancel", color = ZentryxLime)
                }
            }
        )
    }
}

@Composable
private fun SongListItem(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onToggleFavorite: () -> Unit = {},
    onDownload: () -> Unit = {},
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (isCurrent) ZentryxLime.copy(alpha = 0.12f) else MbuxSurface.copy(alpha = 0.7f),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = if (isCurrent) 1.dp else 0.5.dp,
                color = if (isCurrent) ZentryxLime else Color(0xFF1E293B),
                shape = RoundedCornerShape(10.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (song.artworkPath != null && File(song.artworkPath).exists()) {
                AsyncImage(
                    model = File(song.artworkPath),
                    contentDescription = null,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(0.5.dp, MbuxAmbientBorder, RoundedCornerShape(6.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCurrent) ZentryxLime else Color(0xFF1E293B)),
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
                        color = if (isCurrent) ZentryxLime else Color.White,
                        fontSize = 14.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.artist} • ${song.album}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = Color(0xFF94A3B8),
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Action Buttons: Favorite + Download
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (song.isFavorite) ZentryxLime else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDownload,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (song.isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                        contentDescription = "Download Offline",
                        tint = if (song.isDownloaded) ZentryxLime else Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                        contentColor = ZentryxLime,
                        modifier = Modifier.border(0.5.dp, MbuxAmbientBorder, RoundedCornerShape(4.dp))
                    ) {
                        Text(
                            text = song.format.name,
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
private fun AlbumGridItem(album: Album, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MbuxSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            if (album.artworkPath != null && File(album.artworkPath).exists()) {
                AsyncImage(
                    model = File(album.artworkPath),
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
                        tint = ZentryxLime
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
            Text(
                text = "${album.songCount} tracks",
                style = MaterialTheme.typography.bodySmall.copy(
                    color = ZentryxLime,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

@Composable
private fun ArtistListItem(artist: Artist) {
    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MbuxSurface),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MbuxAmbientBorder, RoundedCornerShape(10.dp))
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
                    .background(ZentryxLime.copy(alpha = 0.15f))
                    .border(1.dp, ZentryxLime.copy(alpha = 0.5f), RoundedCornerShape(21.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = ZentryxLime,
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
private fun EmptyLibraryView(onScan: () -> Unit) {
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
                tint = ZentryxLime
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Library is Empty",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color.White)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Scan your Nextcloud WebDAV folders to index your music catalog into local MBUX storage.",
                style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8)),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onScan,
                colors = ButtonDefaults.buttonColors(containerColor = ZentryxLime, contentColor = Color.Black),
                modifier = Modifier.height(44.dp)
            ) {
                Icon(imageVector = Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Nextcloud Library", fontWeight = FontWeight.Bold)
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
