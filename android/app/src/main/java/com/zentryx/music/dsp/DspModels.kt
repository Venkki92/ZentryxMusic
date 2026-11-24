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

enum class AudioOutputRoute(val id: String, val label: String) {
    HEADUNIT_SPEAKERS("headunit_speakers", "Cabin Speakers"),
    BLUETOOTH_A2DP("bluetooth_a2dp", "Bluetooth Audio"),
    USB_DAC_AUX("usb_dac_aux", "USB DAC / AUX");

    companion object {
        fun fromId(id: String): AudioOutputRoute = entries.firstOrNull { it.id == id } ?: HEADUNIT_SPEAKERS
    }
}

val DEFAULT_DEVICE_PRESET_MAP: Map<String, String> = mapOf(
    AudioOutputRoute.HEADUNIT_SPEAKERS.id to "w203_bose",
    AudioOutputRoute.BLUETOOTH_A2DP.id to "highway_cruiser",
    AudioOutputRoute.USB_DAC_AUX.id to "flat"
)

enum class SurroundMode(
    val id: String,
    val label: String,
    val shortLabel: String,
    val badge: String,
    val desc: String,
    val haasLeftMs: Float,
    val haasRightMs: Float,
    val sideWeight: Float,
    val centerWeight: Float,
    val heightWeight: Float = 0f,
    val sideBandpassHz: Float = 1600f,
    val sideBandpassQ: Float = 0.55f,
    val sideWetScale: Float = 0f,
    val sideWidthTarget: Int = 100,
    val centerBoostMaxDb: Float = 0f,
    val heightBoostMaxDb: Float = 0f,
    val useAllPassPhase: Boolean = false,
    val crosstalkCancelGain: Float = 0f,
    val isDolbyOrVirtual: Boolean = false,
    val midToAmbienceMix: Float = 0f,
    val diffuserFeedback: Float = 0f,
    val hallReverbMix: Float = 0f,
    val hallDecayFeedback: Float = 0f,
    val cabinWarmthBoostDb: Float = 0f
) {
    OFF(
        id = "off",
        label = "Stereo Direct",
        shortLabel = "Direct",
        badge = "2.0 DIRECT",
        desc = "Pure unmodified 2-channel stereo signal",
        haasLeftMs = 0f,
        haasRightMs = 0f,
        sideWeight = 0f,
        centerWeight = 0f,
        heightWeight = 0f,
        sideBandpassHz = 1600f,
        sideBandpassQ = 0.55f,
        sideWetScale = 0f,
        sideWidthTarget = 100,
        centerBoostMaxDb = 0f,
        heightBoostMaxDb = 0f
    ),
    LOGIC7_SURROUND(
        id = "logic7_surround",
        label = "Logic 7 Surround",
        shortLabel = "Logic 7",
        badge = "LOGIC 7",
        desc = "Harman 7.1 cabin matrix: 4-tap decorrelated wrap, Schroeder diffuser & dash vocal lock",
        haasLeftMs = 11f,
        haasRightMs = 17f,
        sideWeight = 0.72f,
        centerWeight = 0.55f,
        heightWeight = 0.36f,
        sideBandpassHz = 1600f,
        sideBandpassQ = 0.45f,
        sideWetScale = 0.62f,
        sideWidthTarget = 162,
        centerBoostMaxDb = 3.8f,
        heightBoostMaxDb = 1.8f,
        useAllPassPhase = true,
        crosstalkCancelGain = 0.12f,
        midToAmbienceMix = 0.36f,
        diffuserFeedback = 0.52f,
        hallReverbMix = 0.22f,
        hallDecayFeedback = 0.38f,
        cabinWarmthBoostDb = 2.2f
    ),
    CONCERT_HALL(
        id = "concert_hall",
        label = "Concert Hall",
        shortLabel = "Concert",
        badge = "HALL 7.1",
        desc = "Lush damped acoustic hall reflections with wide enveloping stereo tail",
        haasLeftMs = 16f,
        haasRightMs = 24f,
        sideWeight = 0.82f,
        centerWeight = 0.36f,
        heightWeight = 0.54f,
        sideBandpassHz = 1450f,
        sideBandpassQ = 0.42f,
        sideWetScale = 0.72f,
        sideWidthTarget = 172,
        centerBoostMaxDb = 2.2f,
        heightBoostMaxDb = 2.6f,
        useAllPassPhase = true,
        midToAmbienceMix = 0.48f,
        diffuserFeedback = 0.60f,
        hallReverbMix = 0.48f,
        hallDecayFeedback = 0.62f,
        cabinWarmthBoostDb = 1.8f
    ),
    STUDIO_FOCUSED(
        id = "studio_focused",
        label = "Studio Focused",
        shortLabel = "Studio",
        badge = "STUDIO",
        desc = "Tight near-field vocal lock with crisp early cabin cues and zero tail",
        haasLeftMs = 6f,
        haasRightMs = 9f,
        sideWeight = 0.42f,
        centerWeight = 0.75f,
        heightWeight = 0.26f,
        sideBandpassHz = 1900f,
        sideBandpassQ = 0.60f,
        sideWetScale = 0.36f,
        sideWidthTarget = 128,
        centerBoostMaxDb = 4.4f,
        heightBoostMaxDb = 1.2f,
        midToAmbienceMix = 0.16f,
        diffuserFeedback = 0.32f,
        hallReverbMix = 0f,
        hallDecayFeedback = 0f,
        cabinWarmthBoostDb = 0.6f
    ),
    DOLBY_PRO_LOGIC_II(
        id = "dolby_pro_logic_ii",
        label = "Dolby Pro Logic II",
        shortLabel = "Dolby PLII",
        badge = "DOLBY PLII",
        desc = "5.1 Music Matrix: dual 90° quadrature rear steering + locked dash center",
        haasLeftMs = 13f,
        haasRightMs = 20f,
        sideWeight = 0.76f,
        centerWeight = 0.62f,
        heightWeight = 0.42f,
        sideBandpassHz = 1350f,
        sideBandpassQ = 0.44f,
        sideWetScale = 0.66f,
        sideWidthTarget = 166,
        centerBoostMaxDb = 4.0f,
        heightBoostMaxDb = 2.0f,
        useAllPassPhase = true,
        crosstalkCancelGain = 0.14f,
        isDolbyOrVirtual = true,
        midToAmbienceMix = 0.38f,
        diffuserFeedback = 0.54f,
        hallReverbMix = 0.26f,
        hallDecayFeedback = 0.42f,
        cabinWarmthBoostDb = 1.4f
    ),
    DOLBY_ATMOS_VIRTUAL(
        id = "dolby_atmos_virtual",
        label = "Dolby Virtual Atmos",
        shortLabel = "Atmos 3D",
        badge = "ATMOS 3D",
        desc = "3D cabin dome: pinna overhead elevation contour + diffused ceiling reflections",
        haasLeftMs = 9f,
        haasRightMs = 16f,
        sideWeight = 0.80f,
        centerWeight = 0.56f,
        heightWeight = 1.0f,
        sideBandpassHz = 2100f,
        sideBandpassQ = 0.42f,
        sideWetScale = 0.68f,
        sideWidthTarget = 174,
        centerBoostMaxDb = 3.6f,
        heightBoostMaxDb = 5.4f,
        useAllPassPhase = true,
        crosstalkCancelGain = 0.22f,
        isDolbyOrVirtual = true,
        midToAmbienceMix = 0.42f,
        diffuserFeedback = 0.56f,
        hallReverbMix = 0.34f,
        hallDecayFeedback = 0.48f,
        cabinWarmthBoostDb = 1.2f
    ),
    VIRTUAL_SURROUND_360(
        id = "virtual_surround_360",
        label = "360° Virtualizer",
        shortLabel = "Virtual 360",
        badge = "VIRTUAL 360",
        desc = "Wide A-pillar 360° cabin virtualizer: band-limited ITD cross-talk cancellation & wrap",
        haasLeftMs = 14f,
        haasRightMs = 22f,
        sideWeight = 0.90f,
        centerWeight = 0.44f,
        heightWeight = 0.82f,
        sideBandpassHz = 1750f,
        sideBandpassQ = 0.40f,
        sideWetScale = 0.78f,
        sideWidthTarget = 192,
        centerBoostMaxDb = 2.8f,
        heightBoostMaxDb = 4.2f,
        useAllPassPhase = true,
        crosstalkCancelGain = 0.34f,
        isDolbyOrVirtual = true,
        midToAmbienceMix = 0.46f,
        diffuserFeedback = 0.58f,
        hallReverbMix = 0.30f,
        hallDecayFeedback = 0.44f,
        cabinWarmthBoostDb = 1.6f
    );

    companion object {
        fun fromId(id: String): SurroundMode = entries.firstOrNull { it.id == id } ?: OFF
    }
}

val HARMAN_CABIN_TARGET_OFFSET_16: List<Float> = listOf(
    4.5f, 4.0f, 2.5f, 1.0f, 0.0f, 0.0f, 0.5f, 1.0f,
    0.5f, 0.0f, -0.5f, -1.0f, -1.5f, -2.0f, -2.5f, -3.0f
)

val SUB_LPF_OPTIONS_HZ: List<Int> = listOf(0, 80, 100, 120, 160)
val LIMITER_CEILING_OPTIONS_DB: List<Float> = listOf(0.0f, -0.5f, -1.5f, -3.0f)

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
    val surroundModeId: String? = null,
    val surroundIntensity: Int? = null,
    val centerCoherence: Int? = null,
    val virtualHeight: Int? = null,
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
    val crossoverSlopeDb: Int = 12, // 12 (BW2) or 24 (LR4)
    val loudnessContour: Boolean = true,
    val listeningStage: ListeningStagePreset = ListeningStagePreset.ALL,
    val leftDelayMs: Float = 0f,
    val rightDelayMs: Float = 0f,
    val balance: Int = 0, // -100 L .. +100 R
    val fader: Int = 0,   // -100 Rear .. +100 Front
    val stereoWidthPercent: Int = 100, // 0 .. 150
    val surroundMode: SurroundMode = SurroundMode.OFF,
    val surroundIntensity: Int = 75, // 0 .. 100
    val centerCoherence: Int = 55,   // 0 .. 100
    val virtualHeight: Int = 60,     // 0 .. 100 (Atmos / 360 overhead pinna air)
    val roadNoiseEqEnabled: Boolean = true,
    val compressorProfile: CompressorProfile = CompressorProfile.BALANCED,
    val subPhaseInverted: Boolean = false,
    val subLpfHz: Int = 0, // 0 = Full, 80, 100, 120, 160
    val subTrimDb: Float = 0f, // -6 .. +6 dB
    val subDelayMs: Float = 0f, // 0.0 .. 15.0 ms trunk-to-dash alignment
    val monoFoldDown: Boolean = false,
    val peakLimiterCeilingDb: Float = -0.5f, // 0.0, -0.5, -1.5, -3.0 dBFS
    val autoGenrePresetEnabled: Boolean = false,
    val autoSwitchDevicePreset: Boolean = false,
    val devicePresetMap: Map<String, String> = DEFAULT_DEVICE_PRESET_MAP
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

fun sanitizeSubLpfHz(hz: Int): Int = if (hz in SUB_LPF_OPTIONS_HZ) hz else 0

fun sanitizeCrossoverSlopeDb(slope: Int): Int = if (slope >= 24) 24 else 12

fun sanitizeLimiterCeilingDb(db: Float): Float {
    return LIMITER_CEILING_OPTIONS_DB.minByOrNull { abs(it - db) } ?: -0.5f
}

/**
 * Speed-sensitive Road-Noise Acoustic Contour ("Cabin EQ Compensator").
 * Smoothly ramps low-bass masking compensation (up to +4.0 dB @ 115 Hz) and vocal clarity
 * lift (up to +1.5 dB @ 2.8 kHz) between 50 km/h and 130+ km/h.
 */
fun calculateRoadNoiseContourDb(speedKmH: Float, enabled: Boolean): Pair<Float, Float> {
    if (!enabled || !speedKmH.isFinite() || speedKmH <= 50f) return 0f to 0f
    val t = ((speedKmH - 50f) / 80f).coerceIn(0f, 1f)
    val smooth = t * t * (3f - 2f * t)
    val lowDb = clampStep(smooth * 4.0f, 0f, 4.0f, 0.1f)
    val midDb = clampStep(smooth * 1.5f, 0f, 1.5f, 0.1f)
    return lowDb to midDb
}

/**
 * Computes damped 16-band EQ correction offsets (-6.0..+6.0 dB, 0.5 dB steps) to pull
 * a measured cabin RTA response toward the Harman Automotive Cabin target curve.
 */
fun computeCabinRtaCorrections(measuredDb: List<Float>): List<Float> {
    if (measuredDb.size != 16) return List(16) { 0f }
    val meanMeasured = measuredDb.average().toFloat()
    val meanTarget = HARMAN_CABIN_TARGET_OFFSET_16.average().toFloat()
    return (0 until 16).map { idx ->
        val relMeasured = measuredDb[idx] - meanMeasured
        val relTarget = HARMAN_CABIN_TARGET_OFFSET_16[idx] - meanTarget
        val rawCorrection = (relTarget - relMeasured) * 0.65f
        val snapped = clampStep(rawCorrection, -6f, 6f, 0.5f)
        if (abs(snapped) < 0.01f) 0f else snapped
    }
}

fun applyPresetToDsp(current: DspSettings, preset: DspPreset16): DspSettings {
    val resolvedSurroundMode = preset.surroundModeId?.let { SurroundMode.fromId(it) } ?: current.surroundMode
    val resolvedMonoFoldDown = if (preset.surroundModeId != null && resolvedSurroundMode != SurroundMode.OFF) {
        false
    } else {
        current.monoFoldDown
    }
    return current.copy(
        dspEnabled = true,
        selectedPresetId = preset.id,
        bands16 = preset.bands16.map { clampStep(it, -12f, 12f, 0.5f) },
        bassBoostDb = clampStep(preset.bassBoostDb, 0f, 12f, 0.5f),
        bassBoostFreqHz = preset.bassBoostFreqHz.coerceIn(40, 100),
        preampDb = clampStep(preset.preampDb, -12f, 6f, 0.5f),
        hpfCutoffHz = preset.hpfCutoffHz?.let { HpfCutoffHz.fromHz(it) } ?: current.hpfCutoffHz,
        loudnessContour = preset.loudnessContour ?: current.loudnessContour,
        compressorProfile = preset.compressorProfileId?.let { CompressorProfile.fromId(it) } ?: current.compressorProfile,
        surroundMode = resolvedSurroundMode,
        surroundIntensity = (preset.surroundIntensity ?: current.surroundIntensity).coerceIn(0, 100),
        centerCoherence = (preset.centerCoherence ?: current.centerCoherence).coerceIn(0, 100),
        virtualHeight = (preset.virtualHeight ?: current.virtualHeight).coerceIn(0, 100),
        monoFoldDown = resolvedMonoFoldDown
    )
}

data class SurroundMatrixDiagnostics(
    val trigger: String,
    val selectedPresetId: String,
    val dspEnabled: Boolean,
    val monoFoldDown: Boolean,
    val surroundActive: Boolean,
    val mode: SurroundMode,
    val surroundIntensityPercent: Int,
    val centerCoherencePercent: Int,
    val virtualHeightPercent: Int,
    val baseStereoWidthPercent: Int,
    val effectiveStereoWidthPercent: Int,
    val sampleRateHz: Int,
    val pcmConfigured: Boolean,
    val haasLeftMs: Float,
    val haasRightMs: Float,
    val haasLeftSamples: Int,
    val haasRightSamples: Int,
    val sideBandpassHz: Float,
    val sideBandpassQ: Float,
    val sideWetGain: Float,
    val centerBoostDb: Float,
    val centerInjectGain: Float,
    val heightBoostDb: Float,
    val allPassPhase90Active: Boolean,
    val xtcGain: Float,
    val xtcDelaySamples: Int,
    val hardwareVirtualizerActive: Boolean,
    val hardwareVirtualizerStrengthPermille: Int,
    val midToAmbienceMix: Float = 0f,
    val diffuserFeedback: Float = 0f,
    val hallReverbMix: Float = 0f,
    val cabinWarmthBoostDb: Float = 0f
)

fun computeEffectiveSurroundWidthPercent(settings: DspSettings): Int {
    val baseWidth = when {
        !settings.dspEnabled -> 100f
        settings.monoFoldDown -> 0f
        else -> settings.stereoWidthPercent.coerceIn(0, 150).toFloat()
    }
    if (!settings.dspEnabled || settings.monoFoldDown || settings.surroundMode == SurroundMode.OFF) {
        return baseWidth.roundToInt().coerceIn(0, 200)
    }
    val intensityNorm = settings.surroundIntensity.coerceIn(0, 100) / 100f
    val modeDelta = (settings.surroundMode.sideWidthTarget - 100f) * intensityNorm
    return (baseWidth + modeDelta).coerceIn(0f, 200f).roundToInt()
}

fun buildSurroundMatrixDiagnostics(
    trigger: String,
    settings: DspSettings,
    sampleRateHz: Int = 48000,
    pcmConfigured: Boolean = false,
    hardwareVirtualizerActive: Boolean = false,
    hardwareVirtualizerStrengthPermille: Int = 0
): SurroundMatrixDiagnostics {
    val sr = sampleRateHz.coerceAtLeast(8000)
    val mode = settings.surroundMode
    val intensity = settings.surroundIntensity.coerceIn(0, 100)
    val coherence = settings.centerCoherence.coerceIn(0, 100)
    val height = settings.virtualHeight.coerceIn(0, 100)
    val surroundActive = settings.dspEnabled && !settings.monoFoldDown && mode != SurroundMode.OFF
    val intensityNorm = intensity / 100f
    val coherenceNorm = coherence / 100f
    val heightNorm = height / 100f

    val centerBoostDb = if (surroundActive) coherenceNorm * mode.centerBoostMaxDb else 0f
    val centerInjectGain = if (surroundActive && centerBoostDb > 0.05f) {
        (Math.pow(10.0, centerBoostDb.toDouble() / 20.0) - 1.0).toFloat()
    } else {
        0f
    }
    val sideWetGain = if (surroundActive) intensityNorm * mode.sideWetScale else 0f
    val heightBoostDb = if (surroundActive) heightNorm * mode.heightBoostMaxDb else 0f
    val xtcGain = if (surroundActive) intensityNorm * mode.crosstalkCancelGain else 0f
    val allPassActive = surroundActive && mode.useAllPassPhase && 1100.0 < sr * 0.45
    val haasLeftSamples = if (surroundActive && mode.haasLeftMs > 0f) {
        ((mode.haasLeftMs / 1000f) * sr).roundToInt().coerceAtLeast(1)
    } else 0
    val haasRightSamples = if (surroundActive && mode.haasRightMs > 0f) {
        ((mode.haasRightMs / 1000f) * sr).roundToInt().coerceAtLeast(1)
    } else 0
    val xtcDelaySamples = if (surroundActive && xtcGain > 0.002f) {
        (0.00055 * sr).roundToInt().coerceAtLeast(1)
    } else 0

    return SurroundMatrixDiagnostics(
        trigger = trigger,
        selectedPresetId = settings.selectedPresetId,
        dspEnabled = settings.dspEnabled,
        monoFoldDown = settings.monoFoldDown,
        surroundActive = surroundActive,
        mode = mode,
        surroundIntensityPercent = intensity,
        centerCoherencePercent = coherence,
        virtualHeightPercent = height,
        baseStereoWidthPercent = settings.stereoWidthPercent.coerceIn(0, 150),
        effectiveStereoWidthPercent = computeEffectiveSurroundWidthPercent(settings),
        sampleRateHz = sr,
        pcmConfigured = pcmConfigured,
        haasLeftMs = if (surroundActive) mode.haasLeftMs else 0f,
        haasRightMs = if (surroundActive) mode.haasRightMs else 0f,
        haasLeftSamples = haasLeftSamples,
        haasRightSamples = haasRightSamples,
        sideBandpassHz = mode.sideBandpassHz,
        sideBandpassQ = mode.sideBandpassQ,
        sideWetGain = sideWetGain,
        centerBoostDb = centerBoostDb,
        centerInjectGain = centerInjectGain,
        heightBoostDb = heightBoostDb,
        allPassPhase90Active = allPassActive,
        xtcGain = xtcGain,
        xtcDelaySamples = xtcDelaySamples,
        hardwareVirtualizerActive = hardwareVirtualizerActive,
        hardwareVirtualizerStrengthPermille = if (hardwareVirtualizerActive) hardwareVirtualizerStrengthPermille else 0,
        midToAmbienceMix = if (surroundActive) mode.midToAmbienceMix else 0f,
        diffuserFeedback = if (surroundActive) mode.diffuserFeedback else 0f,
        hallReverbMix = if (surroundActive) intensityNorm * mode.hallReverbMix else 0f,
        cabinWarmthBoostDb = if (surroundActive) intensityNorm * mode.cabinWarmthBoostDb else 0f
    )
}

fun formatSurroundMatrixDiagnosticLog(diag: SurroundMatrixDiagnostics): String {
    return buildString {
        append("[DSP Surround Diagnostics] trigger=").append(diag.trigger)
        append(" | mode=").append(diag.mode.id).append(" (").append(diag.mode.badge).append(")")
        append(" | active=").append(diag.surroundActive)
        append(" | dspEnabled=").append(diag.dspEnabled)
        append(" | mono=").append(diag.monoFoldDown)
        append(" | preset=").append(diag.selectedPresetId)
        append(" | width=").append(diag.baseStereoWidthPercent).append("%->").append(diag.effectiveStereoWidthPercent).append("%")
        append(" | intensity=").append(diag.surroundIntensityPercent).append("%")
        append(" | centerLock=").append(diag.centerCoherencePercent).append("% (+")
            .append(String.format(java.util.Locale.US, "%.2f", diag.centerBoostDb)).append("dB, inj=")
            .append(String.format(java.util.Locale.US, "%.3f", diag.centerInjectGain)).append(")")
        append(" | height=").append(diag.virtualHeightPercent).append("% (+")
            .append(String.format(java.util.Locale.US, "%.2f", diag.heightBoostDb)).append("dB@7.2kHz)")
        append(" | haasL=").append(String.format(java.util.Locale.US, "%.1f", diag.haasLeftMs))
            .append("ms(").append(diag.haasLeftSamples).append("smp)")
        append(" | haasR=").append(String.format(java.util.Locale.US, "%.1f", diag.haasRightMs))
            .append("ms(").append(diag.haasRightSamples).append("smp)")
        append(" | sideBP=").append(diag.sideBandpassHz.roundToInt()).append("Hz(Q=")
            .append(String.format(java.util.Locale.US, "%.2f", diag.sideBandpassQ))
            .append(",wet=").append(String.format(java.util.Locale.US, "%.3f", diag.sideWetGain))
            .append(",midMix=").append(String.format(java.util.Locale.US, "%.2f", diag.midToAmbienceMix))
            .append(",diff=").append(String.format(java.util.Locale.US, "%.2f", diag.diffuserFeedback))
            .append(",hall=").append(String.format(java.util.Locale.US, "%.2f", diag.hallReverbMix)).append(")")
        append(" | ap90=").append(diag.allPassPhase90Active)
        append(" | xtc=").append(String.format(java.util.Locale.US, "%.3f", diag.xtcGain))
            .append("(").append(diag.xtcDelaySamples).append("smp)")
        append(" | pcm=").append(if (diag.pcmConfigured) "${diag.sampleRateHz}Hz" else "pending(${diag.sampleRateHz}Hz)")
        append(" | hwVirt=").append(if (diag.hardwareVirtualizerActive) "${diag.hardwareVirtualizerStrengthPermille}/1000" else "off")
    }
}

/**
 * Live PCM bitstream telemetry captured directly inside CarDspAudioProcessor.queueInput()
 * so SurroundMatrixDebugger can prove whether a selected profile actually mutates output samples.
 */
data class SurroundBitstreamTelemetry(
    val pcmConfigured: Boolean = false,
    val inputEncoding: String = "UNCONFIGURED",
    val sampleRateHz: Int = 44100,
    val channelCount: Int = 2,
    val coeffVersionTarget: Int = 0,
    val coeffVersionApplied: Int = -1,
    val buffersProcessed: Long = 0L,
    val framesProcessed: Long = 0L,
    val fastPathBypassed: Boolean = true,
    val surroundStageExecuted: Boolean = false,
    val bitstreamModified: Boolean = false,
    val inputRms: Float = 0f,
    val outputRms: Float = 0f,
    val surroundDeltaRms: Float = 0f,
    val peakSampleDelta: Float = 0f,
    val inputChecksum: Int = 0,
    val outputChecksum: Int = 0,
    val lastProcessedTimestampMs: Long = 0L
) {
    val surroundDeltaPercent: Float
        get() = if (inputRms > 0.0002f) {
            ((surroundDeltaRms / inputRms) * 100f).coerceIn(0f, 300f)
        } else {
            0f
        }
}

/**
 * Complete snapshot combining HeadunitDspEngine state, calculated Surround Matrix parameters,
 * and live PCM bitstream telemetry for the SurroundMatrixDebugger view.
 */
data class SurroundDebugSnapshot(
    val diagnostics: SurroundMatrixDiagnostics,
    val bitstream: SurroundBitstreamTelemetry,
    val hardwareAttached: Boolean = false,
    val globalSessionAttached: Boolean = false,
    val activeOutputRouteId: String = AudioOutputRoute.HEADUNIT_SPEAKERS.id,
    val snapshotTimestampMs: Long = System.currentTimeMillis()
) {
    val isAudioStreamingLive: Boolean
        get() = bitstream.buffersProcessed > 0L &&
            bitstream.lastProcessedTimestampMs > 0L &&
            (snapshotTimestampMs - bitstream.lastProcessedTimestampMs) <= 2000L
}

val STEREO_SPECTRUM_FREQS_HZ: FloatArray = floatArrayOf(
    60f, 100f, 160f, 250f, 400f, 630f, 1000f, 1600f,
    2200f, 3200f, 4500f, 6300f, 7200f, 9500f, 12500f, 16000f
)

val STEREO_SPECTRUM_LABELS: List<String> = listOf(
    "60", "100", "160", "250", "400", "630", "1k", "1.6k",
    "2.2k", "3.2k", "4.5k", "6.3k", "7.2k", "9.5k", "12.5k", "16k"
)

/**
 * Real-time post-DSP 16-band Left/Right frequency distribution & Mid/Side stereo spread snapshot
 * captured inside CarDspAudioProcessor for the Surround Matrix Debugger overlay.
 */
data class StereoSpectrumSnapshot(
    val leftBands: FloatArray = FloatArray(STEREO_SPECTRUM_FREQS_HZ.size),
    val rightBands: FloatArray = FloatArray(STEREO_SPECTRUM_FREQS_HZ.size),
    val midRms: Float = 0f,
    val sideRms: Float = 0f,
    val stereoSeparationPercent: Float = 0f,
    val isStreamingLive: Boolean = false,
    val sampleRateHz: Int = 48000
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is StereoSpectrumSnapshot) return false
        return leftBands.contentEquals(other.leftBands) &&
            rightBands.contentEquals(other.rightBands) &&
            midRms == other.midRms &&
            sideRms == other.sideRms &&
            stereoSeparationPercent == other.stereoSeparationPercent &&
            isStreamingLive == other.isStreamingLive &&
            sampleRateHz == other.sampleRateHz
    }

    override fun hashCode(): Int {
        var result = leftBands.contentHashCode()
        result = 31 * result + rightBands.contentHashCode()
        result = 31 * result + midRms.hashCode()
        result = 31 * result + sideRms.hashCode()
        result = 31 * result + stereoSeparationPercent.hashCode()
        result = 31 * result + isStreamingLive.hashCode()
        result = 31 * result + sampleRateHz
        return result
    }
}

private fun jsonEscape(value: String): String {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

private fun formatDec(value: Float, decimals: Int = 4): String {
    return String.format(java.util.Locale.US, "%.${decimals}f", value)
}

private fun formatHex8(value: Int): String {
    return "0x" + String.format(java.util.Locale.US, "%08X", value)
}

/**
 * Formats the live HeadunitDspEngine surround state and PCM bitstream telemetry as a clean,
 * deterministic JSON document for inspection in SurroundMatrixDebugger and Logcat.
 */
fun formatSurroundDebugJson(snapshot: SurroundDebugSnapshot): String {
    val d = snapshot.diagnostics
    val b = snapshot.bitstream
    val verdict = when {
        !d.dspEnabled -> "BYPASSED_DSP_OFF"
        d.monoFoldDown -> "SUPPRESSED_MONO_FOLDDOWN"
        d.mode == SurroundMode.OFF -> "DRY_DIRECT_STEREO"
        !b.pcmConfigured -> "PCM_PROCESSOR_NOT_CONFIGURED"
        b.buffersProcessed == 0L -> "IDLE_NO_AUDIO_BUFFERS"
        !snapshot.isAudioStreamingLive -> "PAUSED_STALE_BUFFER"
        b.surroundStageExecuted && b.bitstreamModified && b.surroundDeltaRms > 0.00005f -> "SURROUND_MODIFYING_BITSTREAM"
        b.surroundStageExecuted && b.inputRms < 0.0001f -> "SURROUND_ACTIVE_SILENT_INPUT"
        b.fastPathBypassed -> "WARNING_FAST_PATH_BYPASSED"
        else -> "SURROUND_ENGAGED_CHECK_DELTA"
    }
    return buildString {
        appendLine("{")
        appendLine("  \"verdict\": \"$verdict\",")
        appendLine("  \"engine\": {")
        appendLine("    \"dspEnabled\": ${d.dspEnabled},")
        appendLine("    \"selectedPresetId\": \"${jsonEscape(d.selectedPresetId)}\",")
        appendLine("    \"monoFoldDown\": ${d.monoFoldDown},")
        appendLine("    \"hardwareAttached\": ${snapshot.hardwareAttached},")
        appendLine("    \"globalSession0\": ${snapshot.globalSessionAttached},")
        appendLine("    \"outputRoute\": \"${jsonEscape(snapshot.activeOutputRouteId)}\",")
        appendLine("    \"trigger\": \"${jsonEscape(d.trigger)}\"")
        appendLine("  },")
        appendLine("  \"surroundProfile\": {")
        appendLine("    \"modeId\": \"${d.mode.id}\",")
        appendLine("    \"label\": \"${jsonEscape(d.mode.label)}\",")
        appendLine("    \"badge\": \"${jsonEscape(d.mode.badge)}\",")
        appendLine("    \"surroundActive\": ${d.surroundActive},")
        appendLine("    \"intensityPercent\": ${d.surroundIntensityPercent},")
        appendLine("    \"centerCoherencePercent\": ${d.centerCoherencePercent},")
        appendLine("    \"virtualHeightPercent\": ${d.virtualHeightPercent},")
        appendLine("    \"baseStereoWidthPercent\": ${d.baseStereoWidthPercent},")
        appendLine("    \"effectiveStereoWidthPercent\": ${d.effectiveStereoWidthPercent}")
        appendLine("  },")
        appendLine("  \"matrixParameters\": {")
        appendLine("    \"haasLeftMs\": ${formatDec(d.haasLeftMs, 1)},")
        appendLine("    \"haasRightMs\": ${formatDec(d.haasRightMs, 1)},")
        appendLine("    \"haasLeftSamples\": ${d.haasLeftSamples},")
        appendLine("    \"haasRightSamples\": ${d.haasRightSamples},")
        appendLine("    \"sideBandpassHz\": ${d.sideBandpassHz.roundToInt()},")
        appendLine("    \"sideBandpassQ\": ${formatDec(d.sideBandpassQ, 2)},")
        appendLine("    \"sideWetGain\": ${formatDec(d.sideWetGain, 4)},")
        appendLine("    \"midToAmbienceMix\": ${formatDec(d.midToAmbienceMix, 3)},")
        appendLine("    \"diffuserFeedback\": ${formatDec(d.diffuserFeedback, 3)},")
        appendLine("    \"hallReverbMix\": ${formatDec(d.hallReverbMix, 3)},")
        appendLine("    \"cabinWarmthBoostDb\": ${formatDec(d.cabinWarmthBoostDb, 2)},")
        appendLine("    \"centerBoostDb\": ${formatDec(d.centerBoostDb, 2)},")
        appendLine("    \"centerInjectGain\": ${formatDec(d.centerInjectGain, 4)},")
        appendLine("    \"heightBoostDb_7_2kHz\": ${formatDec(d.heightBoostDb, 2)},")
        appendLine("    \"allPassPhase90Active\": ${d.allPassPhase90Active},")
        appendLine("    \"xtcGain\": ${formatDec(d.xtcGain, 4)},")
        appendLine("    \"xtcDelaySamples\": ${d.xtcDelaySamples}")
        appendLine("  },")
        appendLine("  \"hardwareVirtualizer\": {")
        appendLine("    \"active\": ${d.hardwareVirtualizerActive},")
        appendLine("    \"strengthPermille\": ${d.hardwareVirtualizerStrengthPermille}")
        appendLine("  },")
        appendLine("  \"bitstreamOutput\": {")
        appendLine("    \"pcmConfigured\": ${b.pcmConfigured},")
        appendLine("    \"inputEncoding\": \"${jsonEscape(b.inputEncoding)}\",")
        appendLine("    \"sampleRateHz\": ${b.sampleRateHz},")
        appendLine("    \"channelCount\": ${b.channelCount},")
        appendLine("    \"coeffVersionTarget\": ${b.coeffVersionTarget},")
        appendLine("    \"coeffVersionApplied\": ${b.coeffVersionApplied},")
        appendLine("    \"buffersProcessed\": ${b.buffersProcessed},")
        appendLine("    \"framesProcessed\": ${b.framesProcessed},")
        appendLine("    \"streamingLive\": ${snapshot.isAudioStreamingLive},")
        appendLine("    \"fastPathBypassed\": ${b.fastPathBypassed},")
        appendLine("    \"surroundStageExecuted\": ${b.surroundStageExecuted},")
        appendLine("    \"bitstreamModified\": ${b.bitstreamModified},")
        appendLine("    \"inputRms\": ${formatDec(b.inputRms, 5)},")
        appendLine("    \"outputRms\": ${formatDec(b.outputRms, 5)},")
        appendLine("    \"surroundDeltaRms\": ${formatDec(b.surroundDeltaRms, 5)},")
        appendLine("    \"surroundDeltaPercent\": ${formatDec(b.surroundDeltaPercent, 1)},")
        appendLine("    \"peakSampleDelta\": ${formatDec(b.peakSampleDelta, 5)},")
        appendLine("    \"inputChecksum\": \"${formatHex8(b.inputChecksum)}\",")
        appendLine("    \"outputChecksum\": \"${formatHex8(b.outputChecksum)}\"")
        appendLine("  }")
        append("}")
    }
}

/**
 * Deterministic genre / folder keyword matcher for automatic per-track DSP preset switching.
 * Returns a factory preset ID or null when no specific genre rule matches.
 */
fun matchPresetForTrack(
    genre: String?,
    relativePathOrFolder: String?,
    title: String? = null
): String? {
    val haystack = listOfNotNull(genre, relativePathOrFolder, title)
        .joinToString(" ")
        .lowercase()
    if (haystack.isBlank()) return null

    return when {
        listOf("podcast", "spoken", "audiobook", "talk", "news", "lecture", "radio", "speech")
            .any { haystack.contains(it) } -> "podcast_clarity"
        listOf("rock", "metal", "punk", "grunge", "hardcore", "alternative", "indie", "guitar")
            .any { haystack.contains(it) } -> "rock_metal"
        listOf("electronic", "techno", "house", "trance", "edm", "club", "dnb", "drum & bass", "dubstep", "synth", "dance", "dj")
            .any { haystack.contains(it) } -> "club_electronic"
        listOf("hip hop", "hip-hop", "rap", "trap", "drill", "bass")
            .any { haystack.contains(it) } -> "cabin_bass"
        listOf("jazz", "acoustic", "blues", "soul", "classical", "folk", "bossa", "ambient")
            .any { haystack.contains(it) } -> "vocal_jazz"
        else -> null
    }
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
        put("crossoverSlopeDb", sanitizeCrossoverSlopeDb(settings.crossoverSlopeDb))
        put("loudnessContour", settings.loudnessContour)
        put("listeningStage", settings.listeningStage.id)
        put("leftDelayMs", settings.leftDelayMs.toDouble())
        put("rightDelayMs", settings.rightDelayMs.toDouble())
        put("balance", settings.balance)
        put("fader", settings.fader)
        put("stereoWidthPercent", settings.stereoWidthPercent)
        put("surroundMode", settings.surroundMode.id)
        put("surroundIntensity", settings.surroundIntensity)
        put("centerCoherence", settings.centerCoherence)
        put("virtualHeight", settings.virtualHeight)
        put("roadNoiseEqEnabled", settings.roadNoiseEqEnabled)
        put("compressorProfile", settings.compressorProfile.id)
        put("subPhaseInverted", settings.subPhaseInverted)
        put("subLpfHz", settings.subLpfHz)
        put("subTrimDb", settings.subTrimDb.toDouble())
        put("subDelayMs", settings.subDelayMs.toDouble())
        put("monoFoldDown", settings.monoFoldDown)
        put("peakLimiterCeilingDb", settings.peakLimiterCeilingDb.toDouble())
        put("autoGenrePresetEnabled", settings.autoGenrePresetEnabled)
        put("autoSwitchDevicePreset", settings.autoSwitchDevicePreset)
        val mapObj = JSONObject()
        settings.devicePresetMap.forEach { (k, v) -> mapObj.put(k, v) }
        put("devicePresetMap", mapObj)
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
        val mapObj = obj.optJSONObject("devicePresetMap")
        val parsedDeviceMap = DEFAULT_DEVICE_PRESET_MAP.toMutableMap()
        if (mapObj != null) {
            AudioOutputRoute.entries.forEach { route ->
                val mapped = mapObj.optString(route.id)
                if (mapped.isNotBlank()) parsedDeviceMap[route.id] = mapped
            }
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
            crossoverSlopeDb = sanitizeCrossoverSlopeDb(obj.optInt("crossoverSlopeDb", 12)),
            loudnessContour = obj.optBoolean("loudnessContour", true),
            listeningStage = ListeningStagePreset.fromId(obj.optString("listeningStage", "all")),
            leftDelayMs = obj.optDouble("leftDelayMs", 0.0).toFloat().coerceIn(0f, 5f),
            rightDelayMs = obj.optDouble("rightDelayMs", 0.0).toFloat().coerceIn(0f, 5f),
            balance = obj.optInt("balance", 0).coerceIn(-100, 100),
            fader = obj.optInt("fader", 0).coerceIn(-100, 100),
            stereoWidthPercent = obj.optInt("stereoWidthPercent", 100).coerceIn(0, 150),
            surroundMode = SurroundMode.fromId(obj.optString("surroundMode", "off")),
            surroundIntensity = obj.optInt("surroundIntensity", 55).coerceIn(0, 100),
            centerCoherence = obj.optInt("centerCoherence", 45).coerceIn(0, 100),
            virtualHeight = obj.optInt("virtualHeight", 45).coerceIn(0, 100),
            roadNoiseEqEnabled = obj.optBoolean("roadNoiseEqEnabled", true),
            compressorProfile = CompressorProfile.fromId(obj.optString("compressorProfile", "balanced")),
            subPhaseInverted = obj.optBoolean("subPhaseInverted", false),
            subLpfHz = sanitizeSubLpfHz(obj.optInt("subLpfHz", 0)),
            subTrimDb = clampStep(obj.optDouble("subTrimDb", 0.0).toFloat(), -6f, 6f),
            subDelayMs = clampStep(obj.optDouble("subDelayMs", 0.0).toFloat(), 0f, 15f, 0.5f),
            monoFoldDown = obj.optBoolean("monoFoldDown", false),
            peakLimiterCeilingDb = sanitizeLimiterCeilingDb(obj.optDouble("peakLimiterCeilingDb", -0.5).toFloat()),
            autoGenrePresetEnabled = obj.optBoolean("autoGenrePresetEnabled", false),
            autoSwitchDevicePreset = obj.optBoolean("autoSwitchDevicePreset", false),
            devicePresetMap = parsedDeviceMap
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
            preset.surroundModeId?.let { put("surroundModeId", it) }
            preset.surroundIntensity?.let { put("surroundIntensity", it) }
            preset.centerCoherence?.let { put("centerCoherence", it) }
            preset.virtualHeight?.let { put("virtualHeight", it) }
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
                surroundModeId = obj.optString("surroundModeId").ifBlank { null },
                surroundIntensity = if (obj.has("surroundIntensity")) obj.optInt("surroundIntensity").coerceIn(0, 100) else null,
                centerCoherence = if (obj.has("centerCoherence")) obj.optInt("centerCoherence").coerceIn(0, 100) else null,
                virtualHeight = if (obj.has("virtualHeight")) obj.optInt("virtualHeight").coerceIn(0, 100) else null,
                isCustom = true
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

fun effectiveBandGainDb(
    bandIndex: Int,
    settings: DspSettings,
    speedKmH: Float = 0f
): Float {
    val freq = DSP_16_FREQUENCIES[bandIndex]
    var gain = settings.bands16.getOrElse(bandIndex) { 0f }

    // Sub-door HPF roll-off attenuation (-12 dB/oct BW2 or -24 dB/oct LR4 below cutoff)
    if (settings.hpfCutoffHz.hz > 0 && freq < settings.hpfCutoffHz.hz) {
        val octavesBelow = kotlin.math.log2(settings.hpfCutoffHz.hz / freq)
        val slope = sanitizeCrossoverSlopeDb(settings.crossoverSlopeDb).toFloat()
        gain -= (octavesBelow * slope).coerceAtMost(24f)
    }

    // Dedicated parametric low-shelf Bass Boost
    if (settings.bassBoostDb > 0f && freq <= settings.bassBoostFreqHz * 1.4f) {
        val factor = when {
            freq <= settings.bassBoostFreqHz -> 1.0f
            else -> 1f - ((freq - settings.bassBoostFreqHz) / (settings.bassBoostFreqHz * 0.45f)).coerceIn(0f, 1f)
        }
        gain += settings.bassBoostDb * factor
    }

    // Dedicated Subwoofer LPF Trim band (subTrimDb below subLpfHz)
    val subCutoff = if (settings.subLpfHz > 0) settings.subLpfHz else 100
    if (settings.subTrimDb != 0f && freq <= subCutoff * 1.3f) {
        val subFactor = when {
            freq <= subCutoff -> 1.0f
            else -> (1f - ((freq - subCutoff) / (subCutoff * 0.35f))).coerceIn(0f, 1f)
        }
        gain += settings.subTrimDb * subFactor
    }

    // Road-Noise Acoustic Contour (speed-sensitive low-bass & vocal presence lift)
    val (rnLowDb, rnMidDb) = calculateRoadNoiseContourDb(speedKmH, settings.roadNoiseEqEnabled)
    if (rnLowDb > 0f && freq <= 160f) {
        val lowFactor = if (freq <= 125f) 1f else 0.45f
        gain += rnLowDb * lowFactor
    }
    if (rnMidDb > 0f && freq in 1600f..3500f) {
        val midFactor = if (freq == 2500f) 1f else 0.55f
        gain += rnMidDb * midFactor
    }

    // Logic 7 / Dolby / Virtual Surround Center Vocal Coherence & Overhead Pinna Height preview
    if (settings.surroundMode != SurroundMode.OFF && !settings.monoFoldDown) {
        if (freq in 1600f..3500f) {
            val coherenceLift = (settings.centerCoherence / 100f) * settings.surroundMode.centerWeight * 1.8f
            gain += if (freq == 2500f) coherenceLift else coherenceLift * 0.55f
        }
        if (freq in 5000f..11000f && settings.surroundMode.heightWeight > 0.05f) {
            val heightLift = (settings.virtualHeight / 100f) * settings.surroundMode.heightWeight * 3.4f
            gain += if (freq == 7000f) heightLift else heightLift * 0.5f
        }
    }

    // Fletcher-Munson Loudness Contour (+3.5dB low, +2.5dB high lift)
    if (settings.loudnessContour) {
        if (freq <= 125f) gain += 3.5f * (1f - (freq / 160f))
        if (freq >= 7000f) gain += 2.5f * ((freq - 6000f) / 10000f).coerceIn(0f, 1f)
    }

    // 2D Cabin Fader subtle front/rear presence contour: rear bias warms upper highs
    if (settings.fader < 0 && freq >= 3500f) {
        val rearAmt = abs(settings.fader) / 100f
        gain -= rearAmt * 2.5f
    }

    return gain.coerceIn(-15f, 15f)
}

fun calculateChannelBalanceDb(balance: Int): Pair<Float, Float> {
    val norm = (balance.coerceIn(-100, 100)) / 100f
    val leftAtten = if (norm > 0f) -24f * norm else 0f
    val rightAtten = if (norm < 0f) -24f * abs(norm) else 0f
    return leftAtten to rightAtten
}

fun calculateCabinStageGains(balance: Int, fader: Int): Pair<Float, Float> {
    val (balL, balR) = calculateChannelBalanceDb(balance)
    // Rear bias gently attenuates overall front-stage level by up to -4 dB to shift perceived cabin depth
    val faderAtten = if (fader < 0) -4f * (abs(fader.coerceIn(-100, 100)) / 100f) else 0f
    return (balL + faderAtten).coerceIn(-30f, 0f) to (balR + faderAtten).coerceIn(-30f, 0f)
}
