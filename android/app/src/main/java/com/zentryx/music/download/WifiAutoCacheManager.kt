package com.zentryx.music.download

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.util.Log
import com.zentryx.music.player.SettingsPreferences
import com.zentryx.music.player.extractFolderKey
import com.zentryx.music.player.selectTracksForWifiAutoCache
import com.zentryx.music.repository.MusicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class WifiAutoCacheStatus(val label: String) {
    DISABLED("Disabled"),
    NO_PINS("No Pinned Folders"),
    WAITING_WIFI("Waiting for Home Wi-Fi"),
    SYNCING("Syncing Pinned Folders"),
    UP_TO_DATE("All Pinned Cached"),
    CAP_REACHED("Cache Cap Reached")
}

data class WifiAutoCacheState(
    val enabled: Boolean = true,
    val status: WifiAutoCacheStatus = WifiAutoCacheStatus.NO_PINS,
    val isOnEligibleWifi: Boolean = false,
    val activeSsid: String? = null,
    val configuredSsidFilter: String = "",
    val pinnedFolderCount: Int = 0,
    val cachedInPinnedCount: Int = 0,
    val totalInPinnedCount: Int = 0,
    val pendingCount: Int = 0,
    val lastStatusMessage: String = "Pin Nextcloud folders to auto-cache on Home Wi-Fi"
)

/**
 * Watches unmetered Wi-Fi connectivity (with optional Home SSID allowlist) and automatically
 * downloads newly added or uncached Nextcloud tracks inside pinned folders via [DownloadManager].
 */
@Singleton
class WifiAutoCacheManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPreferences: SettingsPreferences,
    private val musicRepository: MusicRepository,
    private val downloadManager: DownloadManager
) {
    companion object {
        private const val TAG = "WifiAutoCacheManager"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val connectivityManager: ConnectivityManager? =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _state = MutableStateFlow(WifiAutoCacheState())
    val state: StateFlow<WifiAutoCacheState> = _state.asStateFlow()

    private var lastAutoSyncTimestampMs: Long = 0L
    private var syncDebounceJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            scheduleEvaluation(triggerDownload = true)
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            scheduleEvaluation(triggerDownload = true)
        }

        override fun onLost(network: Network) {
            scheduleEvaluation(triggerDownload = false)
        }
    }

    init {
        registerNetworkCallback()
        scope.launch {
            combine(
                settingsPreferences.wifiAutoCacheEnabledFlow,
                settingsPreferences.pinnedCacheFoldersFlow,
                settingsPreferences.wifiAutoCacheSsidFlow,
                settingsPreferences.wifiAutoCacheMaxMbFlow,
                musicRepository.getAllSongs()
            ) { enabled, pins, ssidFilter, maxMb, songs ->
                PolicySnapshot(enabled, pins, ssidFilter, maxMb, songs)
            }.collect { snapshot ->
                evaluatePolicy(snapshot, allowAutoTrigger = true)
            }
        }
    }

    private data class PolicySnapshot(
        val enabled: Boolean,
        val pinnedFolders: Set<String>,
        val ssidFilter: String,
        val maxCacheMb: Int,
        val songs: List<com.zentryx.music.models.Song>
    )

    private fun registerNetworkCallback() {
        try {
            val req = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            connectivityManager?.registerNetworkCallback(req, networkCallback)
        } catch (t: Throwable) {
            Log.w(TAG, "Wi-Fi callback registration warning: ${t.message}")
        }
    }

    private fun detectActiveWifi(): Pair<Boolean, String?> {
        val cm = connectivityManager ?: return false to null
        return try {
            val network = cm.activeNetwork ?: return false to null
            val caps = cm.getNetworkCapabilities(network) ?: return false to null
            val hasWifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
            val isUnmetered = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            val ssid = readCurrentWifiSsid()
            (hasWifi && isUnmetered) to ssid
        } catch (_: Throwable) {
            false to null
        }
    }

    @Suppress("DEPRECATION")
    private fun readCurrentWifiSsid(): String? {
        return try {
            val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val raw = wm?.connectionInfo?.ssid ?: return null
            val cleaned = raw.trim().removePrefix("\"").removeSuffix("\"")
            if (cleaned.isBlank() || cleaned.equals("<unknown ssid>", ignoreCase = true)) null else cleaned
        } catch (_: Throwable) {
            null
        }
    }

    private fun isWifiEligible(isUnmeteredWifi: Boolean, activeSsid: String?, filter: String): Boolean {
        if (!isUnmeteredWifi) return false
        val cleanFilter = filter.trim()
        if (cleanFilter.isEmpty()) return true
        if (activeSsid.isNullOrBlank()) return false
        val allowed = cleanFilter.split(",", ";").map { it.trim() }.filter { it.isNotEmpty() }
        return allowed.any { it.equals(activeSsid, ignoreCase = true) }
    }

    private fun scheduleEvaluation(triggerDownload: Boolean) {
        syncDebounceJob?.cancel()
        syncDebounceJob = scope.launch {
            delay(800L)
            val enabled = settingsPreferences.wifiAutoCacheEnabledFlow.firstOrNull() ?: true
            val pins = settingsPreferences.pinnedCacheFoldersFlow.firstOrNull() ?: emptySet()
            val ssidFilter = settingsPreferences.wifiAutoCacheSsidFlow.firstOrNull() ?: ""
            val maxMb = settingsPreferences.wifiAutoCacheMaxMbFlow.firstOrNull() ?: 4096
            val songs = musicRepository.getAllSongs().firstOrNull().orEmpty()
            evaluatePolicy(PolicySnapshot(enabled, pins, ssidFilter, maxMb, songs), allowAutoTrigger = triggerDownload)
        }
    }

    private fun evaluatePolicy(snapshot: PolicySnapshot, allowAutoTrigger: Boolean) {
        val (unmeteredWifi, activeSsid) = detectActiveWifi()
        val eligible = isWifiEligible(unmeteredWifi, activeSsid, snapshot.ssidFilter)
        val normalizedPins = snapshot.pinnedFolders.map { it.trim().trim('/').lowercase(Locale.US) }.toSet()

        val pinnedSongs = snapshot.songs.filter { song ->
            normalizedPins.contains(extractFolderKey(song).trim().trim('/').lowercase(Locale.US))
        }
        val cachedInPinned = pinnedSongs.count { it.isDownloaded }
        val totalInPinned = pinnedSongs.size
        val currentCachedBytes = computeOfflineCachedBytes(snapshot.songs)
        val tracksToCache = selectTracksForWifiAutoCache(
            songs = snapshot.songs,
            pinnedFolders = snapshot.pinnedFolders,
            maxCacheMb = snapshot.maxCacheMb,
            currentCachedBytes = currentCachedBytes
        )
        val missingTotal = pinnedSongs.count { !it.isDownloaded && it.sourceType != "LOCAL" }

        val status = when {
            !snapshot.enabled -> WifiAutoCacheStatus.DISABLED
            snapshot.pinnedFolders.isEmpty() -> WifiAutoCacheStatus.NO_PINS
            missingTotal == 0 -> WifiAutoCacheStatus.UP_TO_DATE
            tracksToCache.isEmpty() && missingTotal > 0 -> WifiAutoCacheStatus.CAP_REACHED
            downloadManager.batchProgress.value != null -> WifiAutoCacheStatus.SYNCING
            !eligible -> WifiAutoCacheStatus.WAITING_WIFI
            else -> WifiAutoCacheStatus.SYNCING
        }

        val message = when (status) {
            WifiAutoCacheStatus.DISABLED -> "Home Wi-Fi auto-cache paused"
            WifiAutoCacheStatus.NO_PINS -> "Pin any Nextcloud folder to cache automatically on Wi-Fi"
            WifiAutoCacheStatus.UP_TO_DATE -> "${snapshot.pinnedFolders.size} pinned folder(s) · $cachedInPinned/$totalInPinned cached"
            WifiAutoCacheStatus.CAP_REACHED -> "Storage cap (${snapshot.maxCacheMb} MB) reached · $missingTotal track(s) skipped"
            WifiAutoCacheStatus.WAITING_WIFI -> {
                val target = snapshot.ssidFilter.ifBlank { "unmetered Wi-Fi" }
                "$missingTotal track(s) queued · waiting for $target"
            }
            WifiAutoCacheStatus.SYNCING -> "Caching ${tracksToCache.size} new track(s) on ${activeSsid ?: "Wi-Fi"}…"
        }

        _state.update {
            it.copy(
                enabled = snapshot.enabled,
                status = status,
                isOnEligibleWifi = eligible,
                activeSsid = activeSsid,
                configuredSsidFilter = snapshot.ssidFilter,
                pinnedFolderCount = snapshot.pinnedFolders.size,
                cachedInPinnedCount = cachedInPinned,
                totalInPinnedCount = totalInPinned,
                pendingCount = missingTotal,
                lastStatusMessage = message
            )
        }

        val now = System.currentTimeMillis()
        if (
            allowAutoTrigger &&
            snapshot.enabled &&
            eligible &&
            tracksToCache.isNotEmpty() &&
            downloadManager.batchProgress.value == null &&
            (now - lastAutoSyncTimestampMs > 12_000L)
        ) {
            lastAutoSyncTimestampMs = now
            downloadManager.downloadBatch(tracksToCache)
        }
    }

    /**
     * Triggers an immediate manual sync of all pinned Nextcloud folders.
     */
    fun syncPinnedFoldersNow(forceEvenWithoutWifi: Boolean = true) {
        scope.launch {
            val pins = settingsPreferences.pinnedCacheFoldersFlow.firstOrNull() ?: emptySet()
            val ssidFilter = settingsPreferences.wifiAutoCacheSsidFlow.firstOrNull() ?: ""
            val maxMb = settingsPreferences.wifiAutoCacheMaxMbFlow.firstOrNull() ?: 4096
            val songs = musicRepository.getAllSongs().firstOrNull().orEmpty()
            val (unmeteredWifi, activeSsid) = detectActiveWifi()
            val eligible = forceEvenWithoutWifi || isWifiEligible(unmeteredWifi, activeSsid, ssidFilter)
            if (!eligible) {
                _state.update {
                    it.copy(
                        status = WifiAutoCacheStatus.WAITING_WIFI,
                        lastStatusMessage = "Connect to ${ssidFilter.ifBlank { "unmetered Wi-Fi" }} to sync pinned folders"
                    )
                }
                return@launch
            }
            val currentCachedBytes = computeOfflineCachedBytes(songs)
            val toDownload = selectTracksForWifiAutoCache(songs, pins, maxMb, currentCachedBytes)
            if (toDownload.isEmpty()) {
                evaluatePolicy(PolicySnapshot(true, pins, ssidFilter, maxMb, songs), allowAutoTrigger = false)
                return@launch
            }
            lastAutoSyncTimestampMs = System.currentTimeMillis()
            _state.update {
                it.copy(
                    status = WifiAutoCacheStatus.SYNCING,
                    lastStatusMessage = "Syncing ${toDownload.size} track(s) from ${pins.size} pinned folder(s)…"
                )
            }
            downloadManager.downloadBatch(toDownload)
        }
    }

    private fun computeOfflineCachedBytes(songs: List<com.zentryx.music.models.Song>): Long {
        var total = 0L
        for (song in songs) {
            if (!song.isDownloaded || song.sourceType == "LOCAL") continue
            val path = song.localFilePath
            val fileBytes = if (!path.isNullOrBlank() && !path.startsWith("content://")) {
                runCatching { File(path).takeIf { it.exists() }?.length() ?: 0L }.getOrDefault(0L)
            } else {
                0L
            }
            total += when {
                fileBytes > 0L -> fileBytes
                song.fileSize > 0L -> song.fileSize
                else -> 6L * 1024L * 1024L
            }
        }
        return total
    }
}
