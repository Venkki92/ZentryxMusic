package com.zentryx.music.player

import com.zentryx.music.models.Song
import java.util.Locale

enum class SwapGroupMode(val id: String, val label: String) {
    FOLDERS("folders", "Folders"),
    ALBUMS("albums", "Albums");

    companion object {
        fun fromId(id: String?): SwapGroupMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: FOLDERS
    }
}

data class SwapMediaGroup(
    val id: String,
    val name: String,
    val subtitle: String,
    val songs: List<Song>,
    val cachedCount: Int,
    val isPinnedForWifiCache: Boolean,
    val artworkPath: String?
) {
    val key: String get() = id
    val displayName: String get() = name
    val trackCount: Int get() = songs.size
    val offlineCount: Int get() = cachedCount
    val isFullyCached: Boolean get() = songs.isNotEmpty() && cachedCount >= songs.size
    val isFullyOffline: Boolean get() = isFullyCached
}

/**
 * Extracts a canonical, human-readable folder key from a track's Nextcloud or local relative path.
 */
fun extractFolderKey(song: Song): String {
    val clean = song.relativePath
        .replace('\\', '/')
        .trim()
        .trimStart('/')
    val slashIdx = clean.lastIndexOf('/')
    if (slashIdx > 0) {
        return clean.substring(0, slashIdx).trim('/')
    }
    return song.album.trim().ifBlank { "Root Music" }
}

/**
 * Extracts the concise display folder name (last segment) and optional parent breadcrumb.
 */
fun formatFolderDisplayParts(folderKey: String): Pair<String, String> {
    val clean = folderKey.trim().trim('/')
    if (clean.isEmpty()) return "Root Music" to "/"
    val slashIdx = clean.lastIndexOf('/')
    return if (slashIdx > 0) {
        clean.substring(slashIdx + 1) to clean.substring(0, slashIdx)
    } else {
        clean to "Nextcloud Folder"
    }
}

/**
 * Builds deterministic, alphabetically sorted Folder groups for on-the-go cockpit swapping,
 * split-screen live rail display, and Home Wi-Fi auto-cache pinning.
 */
fun buildFolderSwapGroups(
    songs: List<Song>,
    pinnedFolders: Set<String> = emptySet(),
    offlineOnly: Boolean = false
): List<SwapMediaGroup> {
    val filtered = if (offlineOnly) songs.filter { it.isDownloaded } else songs
    if (filtered.isEmpty()) return emptyList()
    val normalizedPins = pinnedFolders.map { it.trim().trim('/').lowercase(Locale.US) }.toSet()

    return filtered
        .groupBy { extractFolderKey(it) }
        .entries
        .sortedBy { it.key.lowercase(Locale.US) }
        .map { (folderKey, groupSongs) ->
            val orderedSongs = groupSongs.sortedWith(
                compareBy<Song>(
                    { it.trackNumber ?: Int.MAX_VALUE },
                    { it.relativePath.lowercase(Locale.US) },
                    { it.title.lowercase(Locale.US) }
                )
            )
            val (name, parent) = formatFolderDisplayParts(folderKey)
            val cachedCount = orderedSongs.count { it.isDownloaded }
            val pinned = normalizedPins.contains(folderKey.trim().trim('/').lowercase(Locale.US))
            SwapMediaGroup(
                id = folderKey,
                name = name,
                subtitle = parent,
                songs = orderedSongs,
                cachedCount = cachedCount,
                isPinnedForWifiCache = pinned,
                artworkPath = orderedSongs.firstOrNull { !it.artworkPath.isNullOrBlank() }?.artworkPath
            )
        }
}

/**
 * Builds deterministic, alphabetically sorted Album groups for on-the-go cockpit swapping.
 */
fun buildAlbumSwapGroups(
    songs: List<Song>,
    pinnedFolders: Set<String> = emptySet(),
    offlineOnly: Boolean = false
): List<SwapMediaGroup> {
    val filtered = if (offlineOnly) songs.filter { it.isDownloaded } else songs
    if (filtered.isEmpty()) return emptyList()
    val normalizedPins = pinnedFolders.map { it.trim().trim('/').lowercase(Locale.US) }.toSet()

    return filtered
        .groupBy { song ->
            val album = song.album.trim().ifBlank { "Unknown Album" }
            val artist = song.artist.trim().ifBlank { "Unknown Artist" }
            "$album||$artist"
        }
        .entries
        .sortedBy { it.key.lowercase(Locale.US) }
        .map { (key, groupSongs) ->
            val parts = key.split("||", limit = 2)
            val albumName = parts.getOrElse(0) { "Unknown Album" }
            val artistName = parts.getOrElse(1) { "Unknown Artist" }
            val orderedSongs = groupSongs.sortedWith(
                compareBy<Song>(
                    { it.trackNumber ?: Int.MAX_VALUE },
                    { it.title.lowercase(Locale.US) }
                )
            )
            val folderKey = extractFolderKey(orderedSongs.first())
            val cachedCount = orderedSongs.count { it.isDownloaded }
            val pinned = normalizedPins.contains(folderKey.trim().trim('/').lowercase(Locale.US))
            SwapMediaGroup(
                id = folderKey,
                name = albumName,
                subtitle = artistName,
                songs = orderedSongs,
                cachedCount = cachedCount,
                isPinnedForWifiCache = pinned,
                artworkPath = orderedSongs.firstOrNull { !it.artworkPath.isNullOrBlank() }?.artworkPath
            )
        }
}

/**
 * Resolves the index of the group containing [currentSong], or -1 if not found.
 */
fun findCurrentSwapGroupIndex(
    groups: List<SwapMediaGroup>,
    currentSong: Song?,
    mode: SwapGroupMode = SwapGroupMode.FOLDERS
): Int {
    if (groups.isEmpty() || currentSong == null) return -1
    val directSongHit = groups.indexOfFirst { g -> g.songs.any { it.id == currentSong.id } }
    if (directSongHit >= 0) return directSongHit
    return when (mode) {
        SwapGroupMode.FOLDERS -> {
            val key = extractFolderKey(currentSong)
            groups.indexOfFirst { it.id.equals(key, ignoreCase = true) }
        }
        SwapGroupMode.ALBUMS -> {
            groups.indexOfFirst {
                it.name.equals(currentSong.album, ignoreCase = true) &&
                    it.subtitle.equals(currentSong.artist, ignoreCase = true)
            }
        }
    }
}

/**
 * Returns the adjacent (prev/next, wrapping cyclically) [SwapMediaGroup] and its 0-based index.
 */
fun findAdjacentSwapGroup(
    groups: List<SwapMediaGroup>,
    currentSong: Song?,
    delta: Int
): Pair<SwapMediaGroup, Int>? = findAdjacentSwapGroup(groups, currentSong, SwapGroupMode.FOLDERS, delta)

fun findAdjacentSwapGroup(
    groups: List<SwapMediaGroup>,
    currentSong: Song?,
    mode: SwapGroupMode,
    delta: Int
): Pair<SwapMediaGroup, Int>? {
    if (groups.isEmpty()) return null
    val curIdx = findCurrentSwapGroupIndex(groups, currentSong, mode)
    val nextIdx = if (curIdx < 0) {
        if (delta >= 0) 0 else groups.lastIndex
    } else {
        Math.floorMod(curIdx + delta, groups.size)
    }
    return groups[nextIdx] to nextIdx
}

/**
 * Evaluates whether [hourOfDay] (0..23) falls inside the W203 Night Amber window.
 * Supports normal schedules (e.g. day=7..night=19) as well as inverted schedules.
 */
fun isW203NightWindow(hourOfDay: Int, dayStartHour: Int = 7, nightStartHour: Int = 19): Boolean {
    val h = hourOfDay.coerceIn(0, 23)
    val day = dayStartHour.coerceIn(0, 23)
    val night = nightStartHour.coerceIn(0, 23)
    if (day == night) return false
    return if (day < night) {
        h < day || h >= night
    } else {
        h >= night && h < day
    }
}

data class W203DimmerResolution(
    val useDayMode: Boolean,
    val isAmberNightActive: Boolean,
    val effectiveBrightness: Float = 1f
)

/**
 * Resolves effective Daylight vs. W203 Night Amber state from the user's mode, schedule,
 * optional GPS solar calculation, manual amber override, and night brightness level.
 */
fun resolveW203DimmerState(
    themeMode: CockpitThemeMode,
    hourOfDay: Int,
    dayStartHour: Int = 7,
    nightStartHour: Int = 19,
    isSolarDaylight: Boolean? = null,
    autoAmberAtNight: Boolean = true,
    manualAmberFilter: Boolean = false,
    nightBrightness: Float = 1f
): W203DimmerResolution {
    val useDay = when (themeMode) {
        CockpitThemeMode.DAY -> true
        CockpitThemeMode.NIGHT -> false
        CockpitThemeMode.AUTO -> {
            // Respect explicit schedule first, falling back to solar daylight when schedule matches defaults
            val scheduleIsNight = isW203NightWindow(hourOfDay, dayStartHour, nightStartHour)
            if (isSolarDaylight != null && dayStartHour == 7 && nightStartHour == 19) {
                isSolarDaylight
            } else {
                !scheduleIsNight
            }
        }
    }
    val amberNight = manualAmberFilter || (!useDay && autoAmberAtNight)
    val clampedNight = nightBrightness.coerceIn(0.35f, 1f)
    val effectiveBrightness = if (useDay && !manualAmberFilter) 1f else clampedNight
    return W203DimmerResolution(
        useDayMode = useDay,
        isAmberNightActive = amberNight,
        effectiveBrightness = effectiveBrightness
    )
}

/**
 * Selects uncached Nextcloud songs belonging to [pinnedFolders] while honoring [maxCacheMb] cap
 * (0 = unlimited). Excludes already-downloaded tracks and local USB tracks.
 */
fun selectTracksForWifiAutoCache(
    songs: List<Song>,
    pinnedFolders: Set<String>,
    maxCacheMb: Int,
    currentCachedBytes: Long
): List<Song> {
    if (pinnedFolders.isEmpty() || songs.isEmpty()) return emptyList()
    val normalizedPins = pinnedFolders.map { it.trim().trim('/').lowercase(Locale.US) }.toSet()
    val capBytes = if (maxCacheMb <= 0) Long.MAX_VALUE else maxCacheMb.toLong() * 1024L * 1024L

    val candidates = songs.filter { song ->
        !song.isDownloaded &&
            song.sourceType != "LOCAL" &&
            song.fileUrl.isNotBlank() &&
            normalizedPins.contains(extractFolderKey(song).trim().trim('/').lowercase(Locale.US))
    }.sortedWith(
        compareBy<Song>(
            { extractFolderKey(it).lowercase(Locale.US) },
            { it.trackNumber ?: Int.MAX_VALUE },
            { it.title.lowercase(Locale.US) }
        )
    )

    if (capBytes == Long.MAX_VALUE) return candidates

    val selected = mutableListOf<Song>()
    var projectedBytes = currentCachedBytes.coerceAtLeast(0L)
    for (song in candidates) {
        val estBytes = song.fileSize.takeIf { it > 0L } ?: (8L * 1024L * 1024L)
        if (projectedBytes + estBytes > capBytes) break
        selected.add(song)
        projectedBytes += estBytes
    }
    return selected
}
