package com.zentryx.music.player

import com.zentryx.music.dsp.ListeningStagePreset
import com.zentryx.music.dsp.SurroundMode
import com.zentryx.music.models.Song
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/**
 * Multi-Profile Driver Cockpit Slot switching theme, night comfort, cabin stage,
 * Logic 7 surround mode, sub trim, and EQ preset atomically in 1 tap.
 */
data class DriverProfile(
    val id: String,
    val name: String,
    val subtitle: String,
    val accentColor: MbuxAccentColor,
    val themeMode: CockpitThemeMode,
    val nightBrightness: Float,
    val amberFilterEnabled: Boolean,
    val listeningStage: ListeningStagePreset,
    val balance: Int,
    val fader: Int,
    val surroundMode: SurroundMode,
    val surroundIntensity: Int,
    val subTrimDb: Float,
    val dspPresetId: String
)

val DEFAULT_DRIVER_PROFILES: List<DriverProfile> = listOf(
    DriverProfile(
        id = "driver_venki",
        name = "Venki",
        subtitle = "Driver LHD · Logic 7 · W203 Bose",
        accentColor = MbuxAccentColor.LIME,
        themeMode = CockpitThemeMode.NIGHT,
        nightBrightness = 1.0f,
        amberFilterEnabled = false,
        listeningStage = ListeningStagePreset.DRIVER_LHD,
        balance = -15,
        fader = 15,
        surroundMode = SurroundMode.LOGIC7_SURROUND,
        surroundIntensity = 58,
        subTrimDb = 1.5f,
        dspPresetId = "w203_bose"
    ),
    DriverProfile(
        id = "driver_night_cruise",
        name = "Night Cruise",
        subtitle = "Warm Amber · Dimmed · Concert Hall",
        accentColor = MbuxAccentColor.AMBER,
        themeMode = CockpitThemeMode.NIGHT,
        nightBrightness = 0.65f,
        amberFilterEnabled = true,
        listeningStage = ListeningStagePreset.DRIVER_LHD,
        balance = -10,
        fader = 10,
        surroundMode = SurroundMode.CONCERT_HALL,
        surroundIntensity = 65,
        subTrimDb = 2.0f,
        dspPresetId = "highway_cruiser"
    ),
    DriverProfile(
        id = "driver_full_cabin",
        name = "Full Cabin",
        subtitle = "Symmetric 4-Seat · Studio Focused",
        accentColor = MbuxAccentColor.CYAN,
        themeMode = CockpitThemeMode.AUTO,
        nightBrightness = 1.0f,
        amberFilterEnabled = false,
        listeningStage = ListeningStagePreset.ALL,
        balance = 0,
        fader = 0,
        surroundMode = SurroundMode.STUDIO_FOCUSED,
        surroundIntensity = 42,
        subTrimDb = 0.5f,
        dspPresetId = "pioneer_ts_e170ci"
    )
)

fun serializeDriverProfiles(profiles: List<DriverProfile>): String {
    val arr = JSONArray()
    profiles.forEach { p ->
        arr.put(JSONObject().apply {
            put("id", p.id)
            put("name", p.name)
            put("subtitle", p.subtitle)
            put("accentColor", p.accentColor.name)
            put("themeMode", p.themeMode.id)
            put("nightBrightness", p.nightBrightness.toDouble())
            put("amberFilterEnabled", p.amberFilterEnabled)
            put("listeningStage", p.listeningStage.id)
            put("balance", p.balance)
            put("fader", p.fader)
            put("surroundMode", p.surroundMode.id)
            put("surroundIntensity", p.surroundIntensity)
            put("subTrimDb", p.subTrimDb.toDouble())
            put("dspPresetId", p.dspPresetId)
        })
    }
    return arr.toString()
}

fun deserializeDriverProfiles(json: String?): List<DriverProfile> {
    if (json.isNullOrBlank()) return DEFAULT_DRIVER_PROFILES
    return try {
        val arr = JSONArray(json)
        val parsed = (0 until arr.length()).mapNotNull { idx ->
            val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
            val id = obj.optString("id").ifBlank { return@mapNotNull null }
            val fallback = DEFAULT_DRIVER_PROFILES.firstOrNull { it.id == id } ?: DEFAULT_DRIVER_PROFILES.first()
            DriverProfile(
                id = id,
                name = obj.optString("name", fallback.name),
                subtitle = obj.optString("subtitle", fallback.subtitle),
                accentColor = runCatching { MbuxAccentColor.valueOf(obj.optString("accentColor", fallback.accentColor.name)) }
                    .getOrDefault(fallback.accentColor),
                themeMode = CockpitThemeMode.fromId(obj.optString("themeMode", fallback.themeMode.id)),
                nightBrightness = obj.optDouble("nightBrightness", fallback.nightBrightness.toDouble()).toFloat().coerceIn(0.35f, 1.0f),
                amberFilterEnabled = obj.optBoolean("amberFilterEnabled", fallback.amberFilterEnabled),
                listeningStage = ListeningStagePreset.fromId(obj.optString("listeningStage", fallback.listeningStage.id)),
                balance = obj.optInt("balance", fallback.balance).coerceIn(-100, 100),
                fader = obj.optInt("fader", fallback.fader).coerceIn(-100, 100),
                surroundMode = SurroundMode.fromId(obj.optString("surroundMode", fallback.surroundMode.id)),
                surroundIntensity = obj.optInt("surroundIntensity", fallback.surroundIntensity).coerceIn(0, 100),
                subTrimDb = obj.optDouble("subTrimDb", fallback.subTrimDb.toDouble()).toFloat().coerceIn(-6f, 6f),
                dspPresetId = obj.optString("dspPresetId", fallback.dspPresetId)
            )
        }
        parsed.ifEmpty { DEFAULT_DRIVER_PROFILES }
    } catch (_: Throwable) {
        DEFAULT_DRIVER_PROFILES
    }
}

enum class RoadtripPool(val id: String, val label: String) {
    FAVORITES_AND_UNHEARD("favorites_unheard", "Favorites + Unheard"),
    FAVORITES_ONLY("favorites_only", "Favorites Only"),
    MOST_PLAYED("most_played", "Most Played"),
    FULL_LIBRARY("full_library", "Full Library")
}

data class RoadtripPlan(
    val targetHours: Int,
    val pool: RoadtripPool,
    val selectedSongs: List<Song>,
    val missingOfflineSongs: List<Song>,
    val alreadyOfflineCount: Int,
    val totalMinutes: Int,
    val estimatedMissingMb: Int
)

fun buildRoadtripOfflinePlan(
    allSongs: List<Song>,
    targetHours: Int,
    pool: RoadtripPool
): RoadtripPlan {
    val safeHours = targetHours.coerceIn(1, 24)
    val targetMs = safeHours * 3_600_000L

    val candidates: List<Song> = when (pool) {
        RoadtripPool.FAVORITES_ONLY -> allSongs.filter { it.isFavorite }
            .sortedByDescending { it.playCount }
        RoadtripPool.MOST_PLAYED -> allSongs.sortedByDescending { it.playCount }
        RoadtripPool.FAVORITES_AND_UNHEARD -> {
            val favs = allSongs.filter { it.isFavorite }.sortedByDescending { it.playCount }
            val unheard = allSongs.filter { !it.isFavorite && it.playCount == 0 }
            val rest = allSongs.filter { !it.isFavorite && it.playCount > 0 }.sortedByDescending { it.playCount }
            favs + unheard + rest
        }
        RoadtripPool.FULL_LIBRARY -> allSongs
    }

    val picked = mutableListOf<Song>()
    var accumMs = 0L
    for (song in candidates) {
        if (accumMs >= targetMs) break
        picked.add(song)
        accumMs += if (song.durationMs > 10_000L) song.durationMs else 210_000L // 3.5 min fallback estimate
    }

    val alreadyOffline = picked.count { it.isDownloaded }
    val missing = picked.filter { !it.isDownloaded }
    val missingBytes = missing.sumOf { song ->
        if (song.fileSize > 100_000L) song.fileSize else 8L * 1024L * 1024L
    }

    return RoadtripPlan(
        targetHours = safeHours,
        pool = pool,
        selectedSongs = picked,
        missingOfflineSongs = missing,
        alreadyOfflineCount = alreadyOffline,
        totalMinutes = (accumMs / 60_000.0).roundToInt(),
        estimatedMissingMb = (missingBytes / (1024.0 * 1024.0)).roundToInt()
    )
}
