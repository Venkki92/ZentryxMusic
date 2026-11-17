package com.zentryx.music.dsp

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.roundToInt

val DSP_16_FREQUENCIES: FloatArray = floatArrayOf(
    32f, 64f, 125f, 250f, 500f, 1000f, 1600f, 2500f,
    3500f, 5000f, 7000f, 9000f, 11000f, 13000f, 15000f, 16000f
)

val DSP_16_LABELS: List<String> = listOf(
    "32", "64", "125", "250", "500", "1k", "1.6k", "2.5k",
    "3.5k", "5k", "7k", "9k", "11k", "13k", "15k", "16k"
)

enum class EqViewMode {
    FIVE_BAND,
    SIXTEEN_BAND
}

enum class HpfCutoffHz(val hz: Int, val label: String) {
    OFF(0, "Full Range"),
    HZ_60(60, "60 Hz"),
    HZ_80(80, "80 Hz"),
    HZ_100(100, "100 Hz"),
    HZ_120(120, "120 Hz");

    companion object {
        fun fromHz(hz: Int): HpfCutoffHz = entries.firstOrNull { it.hz == hz } ?: OFF
    }
}

enum class ListeningStagePreset(
    val id: String,
    val label: String,
    val desc: String,
    val leftDelayMs: Float,
    val rightDelayMs: Float
) {
    ALL("all", "All Cabin", "0.0 ms symmetric", 0.0f, 0.0f),
    DRIVER_LHD("driver", "Driver (LHD)", "+1.4 ms L delay", 1.4f, 0.0f),
    DRIVER_RHD("driver_rhd", "Driver (RHD)", "+1.4 ms R delay", 0.0f, 1.4f),
    CENTER("center", "Front Center", "+0.4 ms front align", 0.4f, 0.4f),
    CUSTOM("custom", "Custom", "Manual timing", 0.0f, 0.0f);

    companion object {
        fun fromId(id: String): ListeningStagePreset = entries.firstOrNull { it.id == id } ?: ALL
    }
}

enum class CompressorProfile(
    val id: String,
    val label: String,
    val desc: String,
    val thresholdDb: Float,
    val kneeWidthDb: Float,
    val ratio: Float,
    val attackMs: Float,
    val releaseMs: Float
) {
    OFF("off", "Bypass", "Full dynamic range untouched", 0f, 0f, 1f, 3f, 250f),
    BALANCED("balanced", "Balanced", "Gentle cabin loudness leveling", -18f, 18f, 3f, 12f, 220f),
    NIGHT_HIGHWAY("night_highway", "Night / Highway", "Lifts quiet details over road noise", -26f, 24f, 5.5f, 8f, 280f),
    CLUB_PUNCH("club_punch", "Club Punch", "Fast recovery with peak protection", -14f, 10f, 4f, 25f, 140f);

    companion object {
        fun fromId(id: String): CompressorProfile = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

data class DspPreset16(
    val id: String,
    val name: String,
    val description: String,
    val bands16: List<Float>,
    val bassBoostDb: Float = 0f,
    val bassBoostFreqHz: Int = 65,
    val preampDb: Float = 0f,
    val hpfCutoffHz: Int? = null,
    val loudnessContour: Boolean? = null,
    val compressorProfileId: String? = null,
    val isCustom: Boolean = false
)

data class DspSettings(
    val dspEnabled: Boolean = true,
    val eqViewMode: EqViewMode = EqViewMode.SIXTEEN_BAND,
    val selectedPresetId: String = "w203_bose",
    val bands16: List<Float> = FACTORY_DSP_PRESETS[1].bands16,
    val preampDb: Float = -2f,
    val bassBoostDb: Float = 3f,
    val bassBoostFreqHz: Int = 65,
    val hpfCutoffHz: HpfCutoffHz = HpfCutoffHz.OFF,
    val loudnessContour: Boolean = true,
    val listeningStage: ListeningStagePreset = ListeningStagePreset.ALL,
    val leftDelayMs: Float = 0f,
    val rightDelayMs: Float = 0f,
    val balance: Int = 0, // -100 L .. +100 R
    val stereoWidthPercent: Int = 100, // 0 .. 150
    val compressorProfile: CompressorProfile = CompressorProfile.BALANCED
)

val FACTORY_DSP_PRESETS: List<DspPreset16> = listOf(
    DspPreset16(
        id = "flat",
        name = "Flat / Studio Direct",
        description = "Neutral reference response with zero coloration",
        bands16 = List(16) { 0f },
        bassBoostDb = 0f,
        preampDb = 0f
    ),
    DspPreset16(
        id = "w203_bose",
        name = "W203 Sound System",
        description = "Mercedes cabin signature — tight door kick, rich mids, smooth silk highs",
        bands16 = listOf(3f, 4.5f, 3.5f, 1.5f, 0f, 0.5f, 1.5f, 2f, 1.5f, 1f, 2f, 3f, 3.5f, 3f, 2f, 1.5f),
        bassBoostDb = 3f,
        preampDb = -2f
    ),
    DspPreset16(
        id = "cabin_bass",
        name = "Cabin Bass Slam",
        description = "Deep sub-bass punch engineered for trunk & door woofers",
        bands16 = listOf(7.5f, 8f, 6f, 3.5f, 1f, -0.5f, -1f, 0f, 1f, 2f, 2.5f, 3f, 3.5f, 3f, 2.5f, 2f),
        bassBoostDb = 6f,
        preampDb = -3f
    ),
    DspPreset16(
        id = "highway_cruiser",
        name = "Highway Cruiser",
        description = "Lifts bass and crisp presence over tire roar at 100+ km/h",
        bands16 = listOf(5f, 5.5f, 4f, 2f, 1f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 4f, 3.5f, 3f, 2.5f, 2f),
        bassBoostDb = 4f,
        preampDb = -2f
    ),
    DspPreset16(
        id = "podcast_clarity",
        name = "Vocal & Podcast",
        description = "Suppresses road boom and sharpens spoken vocal intelligibility",
        bands16 = listOf(-6f, -5f, -3.5f, -1.5f, 1f, 3f, 4.5f, 5f, 4f, 2.5f, 1.5f, 1f, 0f, -1f, -1.5f, -2f),
        bassBoostDb = 0f,
        preampDb = 0f
    ),
    DspPreset16(
        id = "rock_metal",
        name = "Rock & Guitars",
        description = "Snappy snare punch, forward guitar bite, airy cymbal shimmer",
        bands16 = listOf(4f, 4.5f, 3f, 1.5f, -1f, -1.5f, 0.5f, 2.5f, 3.5f, 4f, 4.5f, 5f, 4.5f, 4f, 3f, 2.5f),
        bassBoostDb = 3f,
        preampDb = -2f
    ),
    DspPreset16(
        id = "club_electronic",
        name = "Club & Electronic",
        description = "Maximum sub extension and sparkling synth air",
        bands16 = listOf(8f, 7.5f, 5f, 2f, 0f, -1f, 0f, 1.5f, 2.5f, 3.5f, 4.5f, 5.5f, 6f, 6f, 5f, 4.5f),
        bassBoostDb = 6f,
        preampDb = -3f
    ),
    DspPreset16(
        id = "vocal_jazz",
        name = "Acoustic & Jazz",
        description = "Warm upright bass body and natural brass/vocal staging",
        bands16 = listOf(2f, 3f, 2.5f, 1.5f, 1f, 1.5f, 2f, 2f, 1.5f, 2f, 2.5f, 3f, 3f, 2.5f, 2f, 1.5f),
        bassBoostDb = 1f,
        preampDb = -1f
    ),
    DspPreset16(
        id = "pioneer_ts_e170ci",
        name = "Pioneer TS-E170Ci Kit",
        description = "Anti-tin cure: 60Hz HPF + 80Hz door-bass boost + -4.5dB tweeter glare cut",
        bands16 = listOf(3.5f, 6.5f, 6f, 3f, 1f, -0.5f, -1.5f, -3.5f, -4.5f, -4f, -3.5f, -2.5f, -1.5f, -0.5f, 0.5f, 1f),
        bassBoostDb = 5.5f,
        bassBoostFreqHz = 80,
        preampDb = -6f,
        hpfCutoffHz = 60,
        loudnessContour = true,
        compressorProfileId = "balanced"
    )
)

data class EqFiveBandGroup(
    val id: String,
    val label: String,
    val freqHint: String,
    val indices: IntArray
)

val EQ_5BAND_GROUPS: List<EqFiveBandGroup> = listOf(
    EqFiveBandGroup("sub", "Sub-Bass", "32–64 Hz", intArrayOf(0, 1)),
    EqFiveBandGroup("bass", "Mid-Bass", "125–250 Hz", intArrayOf(2, 3)),
    EqFiveBandGroup("mids", "Vocals", "500–2.5k Hz", intArrayOf(4, 5, 6, 7)),
    EqFiveBandGroup("presence", "Presence", "3.5k–7k Hz", intArrayOf(8, 9, 10)),
    EqFiveBandGroup("brilliance", "Air / Highs", "9k–16k Hz", intArrayOf(11, 12, 13, 14, 15))
)

fun clampStep(value: Float, min: Float, max: Float, step: Float = 0.5f): Float {
    val clamped = value.coerceIn(min, max)
    return (clamped / step).roundToInt() * step
}

fun applyPresetToDsp(current: DspSettings, preset: DspPreset16): DspSettings {
    return current.copy(
        dspEnabled = true,
        selectedPresetId = preset.id,
        bands16 = preset.bands16.map { clampStep(it, -12f, 12f, 0.5f) },
        bassBoostDb = clampStep(preset.bassBoostDb, 0f, 12f, 0.5f),
        bassBoostFreqHz = preset.bassBoostFreqHz.coerceIn(40, 100),
        preampDb = clampStep(preset.preampDb, -12f, 6f, 0.5f),
        hpfCutoffHz = preset.hpfCutoffHz?.let { HpfCutoffHz.fromHz(it) } ?: current.hpfCutoffHz,
        loudnessContour = preset.loudnessContour ?: current.loudnessContour,
        compressorProfile = preset.compressorProfileId?.let { CompressorProfile.fromId(it) } ?: current.compressorProfile
    )
}

fun serializeDspSettings(settings: DspSettings): String {
    return JSONObject().apply {
        put("dspEnabled", settings.dspEnabled)
        put("eqViewMode", settings.eqViewMode.name)
        put("selectedPresetId", settings.selectedPresetId)
        put("bands16", JSONArray(settings.bands16.map { it.toDouble() }))
        put("preampDb", settings.preampDb.toDouble())
        put("bassBoostDb", settings.bassBoostDb.toDouble())
        put("bassBoostFreqHz", settings.bassBoostFreqHz)
        put("hpfCutoffHz", settings.hpfCutoffHz.hz)
        put("loudnessContour", settings.loudnessContour)
        put("listeningStage", settings.listeningStage.id)
        put("leftDelayMs", settings.leftDelayMs.toDouble())
        put("rightDelayMs", settings.rightDelayMs.toDouble())
        put("balance", settings.balance)
        put("stereoWidthPercent", settings.stereoWidthPercent)
        put("compressorProfile", settings.compressorProfile.id)
    }.toString()
}

fun deserializeDspSettings(json: String?): DspSettings {
    if (json.isNullOrBlank()) return DspSettings()
    return try {
        val obj = JSONObject(json)
        val arr = obj.optJSONArray("bands16")
        val bands = if (arr != null && arr.length() == 16) {
            (0 until 16).map { clampStep(arr.optDouble(it, 0.0).toFloat(), -12f, 12f) }
        } else {
            DspSettings().bands16
        }
        DspSettings(
            dspEnabled = obj.optBoolean("dspEnabled", true),
            eqViewMode = try {
                EqViewMode.valueOf(obj.optString("eqViewMode", EqViewMode.SIXTEEN_BAND.name))
            } catch (_: Exception) { EqViewMode.SIXTEEN_BAND },
            selectedPresetId = obj.optString("selectedPresetId", "w203_bose"),
            bands16 = bands,
            preampDb = clampStep(obj.optDouble("preampDb", -2.0).toFloat(), -12f, 6f),
            bassBoostDb = clampStep(obj.optDouble("bassBoostDb", 3.0).toFloat(), 0f, 12f),
            bassBoostFreqHz = obj.optInt("bassBoostFreqHz", 65).coerceIn(40, 100),
            hpfCutoffHz = HpfCutoffHz.fromHz(obj.optInt("hpfCutoffHz", 0)),
            loudnessContour = obj.optBoolean("loudnessContour", true),
            listeningStage = ListeningStagePreset.fromId(obj.optString("listeningStage", "all")),
            leftDelayMs = obj.optDouble("leftDelayMs", 0.0).toFloat().coerceIn(0f, 5f),
            rightDelayMs = obj.optDouble("rightDelayMs", 0.0).toFloat().coerceIn(0f, 5f),
            balance = obj.optInt("balance", 0).coerceIn(-100, 100),
            stereoWidthPercent = obj.optInt("stereoWidthPercent", 100).coerceIn(0, 150),
            compressorProfile = CompressorProfile.fromId(obj.optString("compressorProfile", "balanced"))
        )
    } catch (_: Exception) {
        DspSettings()
    }
}

fun serializeCustomPresets(presets: List<DspPreset16>): String {
    val arr = JSONArray()
    presets.forEach { preset ->
        arr.put(JSONObject().apply {
            put("id", preset.id)
            put("name", preset.name)
            put("description", preset.description)
            put("bands16", JSONArray(preset.bands16.map { it.toDouble() }))
            put("bassBoostDb", preset.bassBoostDb.toDouble())
            put("bassBoostFreqHz", preset.bassBoostFreqHz)
            put("preampDb", preset.preampDb.toDouble())
            preset.hpfCutoffHz?.let { put("hpfCutoffHz", it) }
            preset.loudnessContour?.let { put("loudnessContour", it) }
            preset.compressorProfileId?.let { put("compressorProfileId", it) }
        })
    }
    return arr.toString()
}

fun deserializeCustomPresets(json: String?): List<DspPreset16> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { idx ->
            val obj = arr.optJSONObject(idx) ?: return@mapNotNull null
            val bArr = obj.optJSONArray("bands16")
            if (bArr == null || bArr.length() != 16) return@mapNotNull null
            DspPreset16(
                id = obj.optString("id", "custom_$idx"),
                name = obj.optString("name", "Custom Preset ${idx + 1}"),
                description = obj.optString("description", "User-saved cabin curve"),
                bands16 = (0 until 16).map { clampStep(bArr.optDouble(it, 0.0).toFloat(), -12f, 12f) },
                bassBoostDb = clampStep(obj.optDouble("bassBoostDb", 0.0).toFloat(), 0f, 12f),
                bassBoostFreqHz = obj.optInt("bassBoostFreqHz", 65).coerceIn(40, 100),
                preampDb = clampStep(obj.optDouble("preampDb", 0.0).toFloat(), -12f, 6f),
                hpfCutoffHz = if (obj.has("hpfCutoffHz")) obj.optInt("hpfCutoffHz") else null,
                loudnessContour = if (obj.has("loudnessContour")) obj.optBoolean("loudnessContour") else null,
                compressorProfileId = obj.optString("compressorProfileId").ifBlank { null },
                isCustom = true
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun effectiveBandGainDb(
    bandIndex: Int,
    settings: DspSettings
): Float {
    val freq = DSP_16_FREQUENCIES[bandIndex]
    var gain = settings.bands16.getOrElse(bandIndex) { 0f }

    // Sub-door HPF roll-off attenuation (-12 dB/oct below cutoff)
    if (settings.hpfCutoffHz.hz > 0 && freq < settings.hpfCutoffHz.hz) {
        val octavesBelow = kotlin.math.log2(settings.hpfCutoffHz.hz / freq)
        gain -= (octavesBelow * 12f).coerceAtMost(18f)
    }

    // Dedicated parametric low-shelf Bass Boost
    if (settings.bassBoostDb > 0f && freq <= settings.bassBoostFreqHz * 1.4f) {
        val factor = when {
            freq <= settings.bassBoostFreqHz -> 1.0f
            else -> 1f - ((freq - settings.bassBoostFreqHz) / (settings.bassBoostFreqHz * 0.45f)).coerceIn(0f, 1f)
        }
        gain += settings.bassBoostDb * factor
    }

    // Fletcher-Munson Loudness Contour (+4dB low, +2.5dB high lift)
    if (settings.loudnessContour) {
        if (freq <= 125f) gain += 3.5f * (1f - (freq / 160f))
        if (freq >= 7000f) gain += 2.5f * ((freq - 6000f) / 10000f).coerceIn(0f, 1f)
    }

    return gain.coerceIn(-15f, 15f)
}

fun calculateChannelBalanceDb(balance: Int): Pair<Float, Float> {
    val norm = (balance.coerceIn(-100, 100)) / 100f
    val leftAtten = if (norm > 0f) -24f * norm else 0f
    val rightAtten = if (norm < 0f) -24f * abs(norm) else 0f
    return leftAtten to rightAtten
}
