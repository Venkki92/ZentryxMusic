package com.zentryx.music.dsp

import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class AdaptiveCabinSource(val id: String, val label: String) {
    HYBRID("hybrid", "Auto (Mic + GPS)"),
    MICROPHONE("microphone", "Cabin Mic"),
    SPEED("speed", "GPS Speed");

    companion object {
        fun fromId(id: String): AdaptiveCabinSource = entries.firstOrNull { it.id == id } ?: HYBRID
    }
}

enum class AdaptiveCabinSensitivity(val id: String, val label: String, val scale: Float) {
    SUBTLE("subtle", "Subtle", 0.65f),
    BALANCED("balanced", "Balanced", 1.0f),
    AGGRESSIVE("aggressive", "Aggressive", 1.35f);

    companion object {
        fun fromId(id: String): AdaptiveCabinSensitivity = entries.firstOrNull { it.id == id } ?: BALANCED
    }
}

enum class AdaptiveCabinFocus(val id: String, val label: String) {
    DRIVER_LHD("driver_lhd", "Driver (LHD)"),
    DRIVER_RHD("driver_rhd", "Driver (RHD)"),
    FRONT_CENTER("front_center", "Front Cabin");

    companion object {
        fun fromId(id: String): AdaptiveCabinFocus = entries.firstOrNull { it.id == id } ?: DRIVER_LHD
    }
}

enum class AdaptiveCabinProfile(
    val id: String,
    val rank: Int,
    val label: String,
    val shortLabel: String,
    val baseGainDb: Float,
    val baseBalanceShift: Int,
    val baseFaderShift: Int,
    val baseBassTiltDb: Float,
    val baseVocalTiltDb: Float
) {
    QUIET("quiet", 0, "Quiet / Parked", "Quiet", 0f, 0, 0, 0f, 0f),
    CITY("city", 1, "City Cruise", "City", 1.5f, 6, 10, 1.0f, 0.5f),
    HIGHWAY("highway", 2, "Highway Cruise", "Highway", 3.0f, 12, 18, 2.0f, 1.0f),
    HIGH_NOISE("high_noise", 3, "High Noise / Autobahn", "High Noise", 4.5f, 18, 25, 3.0f, 1.5f);

    companion object {
        fun fromId(id: String): AdaptiveCabinProfile = entries.firstOrNull { it.id == id } ?: QUIET
    }
}

data class AdaptiveCabinConfig(
    val enabled: Boolean = false,
    val source: AdaptiveCabinSource = AdaptiveCabinSource.HYBRID,
    val sensitivity: AdaptiveCabinSensitivity = AdaptiveCabinSensitivity.BALANCED,
    val cabinFocus: AdaptiveCabinFocus = AdaptiveCabinFocus.DRIVER_LHD,
    val speedCityKmH: Int = 45,
    val speedHighwayKmH: Int = 85,
    val speedHighKmH: Int = 120,
    val noiseModerateDb: Int = 58,
    val noiseLoudDb: Int = 68,
    val noiseExtremeDb: Int = 76
)

data class AdaptiveDspDelta(
    val profile: AdaptiveCabinProfile = AdaptiveCabinProfile.QUIET,
    val gainDb: Float = 0f,
    val balanceDelta: Int = 0,
    val faderDelta: Int = 0,
    val bassTiltDb: Float = 0f,
    val vocalTiltDb: Float = 0f
)

data class AdaptiveEvaluationState(
    val activeProfile: AdaptiveCabinProfile = AdaptiveCabinProfile.QUIET,
    val pendingProfile: AdaptiveCabinProfile? = null,
    val pendingSinceMs: Long = 0L,
    val triggerReason: String = "idle",
    val smoothedSpeedKmH: Float = 0f,
    val lastEvaluatedAtMs: Long = 0L
)

private const val SPEED_HYSTERESIS_KMH = 7f
private const val NOISE_HYSTERESIS_DB = 3.5f
private const val ESCALATE_DWELL_MS = 1400L
private const val DEESCALATE_DWELL_MS = 3200L

private const val MAX_ACCEL_KMH_PER_SEC = 18f
private const val MAX_DECEL_KMH_PER_SEC = 28f
private const val SPEED_ACCEL_TAU_SEC = 1.4
private const val SPEED_DECEL_TAU_SEC = 1.8

private const val GAIN_ATTACK_TAU_SEC = 1.2
private const val GAIN_RELEASE_TAU_SEC = 2.2
private const val MAX_GAIN_SLEW_UP_DB_PER_SEC = 1.25f
private const val MAX_GAIN_SLEW_DOWN_DB_PER_SEC = 0.9f
private const val MAX_STAGE_SLEW_PER_SEC = 10f
private const val MAX_TILT_SLEW_DB_PER_SEC = 1.0f

fun smoothSpeedSample(
    prevSmoothedKmH: Float,
    rawSpeedKmH: Float,
    dtMs: Long,
    isInitialSample: Boolean = false
): Float {
    val safeRaw = if (rawSpeedKmH.isFinite() && rawSpeedKmH > 0f) min(300f, rawSpeedKmH) else 0f
    val safePrev = if (prevSmoothedKmH.isFinite() && prevSmoothedKmH > 0f) prevSmoothedKmH else 0f

    if (isInitialSample || dtMs <= 0L) {
        return (safeRaw * 10f).roundToInt() / 10f
    }

    val dtSec = min(10.0, dtMs / 1000.0)
    val delta = safeRaw - safePrev
    if (abs(delta) < 0.15f) {
        return (safeRaw * 10f).roundToInt() / 10f
    }

    val tau = if (delta >= 0f) SPEED_ACCEL_TAU_SEC else SPEED_DECEL_TAU_SEC
    val alpha = (1.0 - exp(-dtSec / tau)).toFloat()
    val emaStep = delta * alpha
    val maxStep = (if (delta >= 0f) MAX_ACCEL_KMH_PER_SEC else MAX_DECEL_KMH_PER_SEC) * dtSec.toFloat()
    var next = safePrev + emaStep.coerceIn(-maxStep, maxStep)

    if (abs(safeRaw - next) < 0.15f) {
        next = safeRaw
    }
    var rounded = (max(0f, next) * 10f).roundToInt() / 10f
    if (rounded == safePrev && maxStep >= 0.1f) {
        val dir = if (delta > 0f) 1f else -1f
        val nudged = ((safePrev + dir * 0.1f) * 10f).roundToInt() / 10f
        rounded = if (delta > 0f) min(safeRaw, nudged) else max(safeRaw, nudged)
    }
    return max(0f, rounded)
}

private fun rampScalar(
    current: Float,
    target: Float,
    dtSec: Double,
    tauUp: Double,
    tauDown: Double,
    maxRateUp: Float,
    maxRateDown: Float,
    snapEpsilon: Float,
    precisionScale: Float
): Float {
    val diff = target - current
    if (abs(diff) <= snapEpsilon) return target

    val increasing = abs(target) > abs(current) || (current == 0f && target != 0f)
    val tau = if (increasing) tauUp else tauDown
    val maxRate = if (increasing) maxRateUp else maxRateDown

    val alpha = (1.0 - exp(-dtSec / tau)).toFloat()
    val emaStep = diff * alpha
    val maxStep = maxRate * dtSec.toFloat()
    val rawNext = current + emaStep.coerceIn(-maxStep, maxStep)

    if (abs(target - rawNext) <= snapEpsilon) return target
    var rounded = (rawNext * precisionScale).roundToInt() / precisionScale
    val quantum = 1f / precisionScale
    if (rounded == current && maxStep >= quantum) {
        val dir = if (diff > 0f) 1f else -1f
        val nudged = ((current + dir * quantum) * precisionScale).roundToInt() / precisionScale
        rounded = if (diff > 0f) min(target, nudged) else max(target, nudged)
    }
    return rounded
}

fun smoothAdaptiveDspDelta(
    current: AdaptiveDspDelta,
    target: AdaptiveDspDelta,
    dtMs: Long
): AdaptiveDspDelta {
    if (dtMs <= 0L) return current.copy(profile = target.profile)
    val dtSec = min(5.0, dtMs / 1000.0)
    return AdaptiveDspDelta(
        profile = target.profile,
        gainDb = rampScalar(
            current.gainDb,
            target.gainDb,
            dtSec,
            GAIN_ATTACK_TAU_SEC,
            GAIN_RELEASE_TAU_SEC,
            MAX_GAIN_SLEW_UP_DB_PER_SEC,
            MAX_GAIN_SLEW_DOWN_DB_PER_SEC,
            0.05f,
            10f
        ),
        balanceDelta = rampScalar(
            current.balanceDelta.toFloat(),
            target.balanceDelta.toFloat(),
            dtSec,
            GAIN_ATTACK_TAU_SEC,
            GAIN_RELEASE_TAU_SEC,
            MAX_STAGE_SLEW_PER_SEC,
            MAX_STAGE_SLEW_PER_SEC * 0.85f,
            0.5f,
            1f
        ).roundToInt(),
        faderDelta = rampScalar(
            current.faderDelta.toFloat(),
            target.faderDelta.toFloat(),
            dtSec,
            GAIN_ATTACK_TAU_SEC,
            GAIN_RELEASE_TAU_SEC,
            MAX_STAGE_SLEW_PER_SEC,
            MAX_STAGE_SLEW_PER_SEC * 0.85f,
            0.5f,
            1f
        ).roundToInt(),
        bassTiltDb = rampScalar(
            current.bassTiltDb,
            target.bassTiltDb,
            dtSec,
            GAIN_ATTACK_TAU_SEC,
            GAIN_RELEASE_TAU_SEC,
            MAX_TILT_SLEW_DB_PER_SEC,
            MAX_TILT_SLEW_DB_PER_SEC * 0.8f,
            0.05f,
            10f
        ),
        vocalTiltDb = rampScalar(
            current.vocalTiltDb,
            target.vocalTiltDb,
            dtSec,
            GAIN_ATTACK_TAU_SEC,
            GAIN_RELEASE_TAU_SEC,
            MAX_TILT_SLEW_DB_PER_SEC,
            MAX_TILT_SLEW_DB_PER_SEC * 0.8f,
            0.05f,
            10f
        )
    )
}

fun sanitizeAdaptiveCabinConfig(config: AdaptiveCabinConfig): AdaptiveCabinConfig {
    val citySpeed = ((config.speedCityKmH.coerceIn(20, 80) / 5f).roundToInt() * 5)
    val highwaySpeed = ((config.speedHighwayKmH.coerceIn(max(citySpeed + 15, 50), 130) / 5f).roundToInt() * 5)
    val highSpeed = ((config.speedHighKmH.coerceIn(max(highwaySpeed + 15, 80), 180) / 5f).roundToInt() * 5)

    val modNoise = config.noiseModerateDb.coerceIn(42, 72)
    val loudNoise = config.noiseLoudDb.coerceIn(max(modNoise + 5, 52), 84)
    val extremeNoise = config.noiseExtremeDb.coerceIn(max(loudNoise + 5, 62), 92)

    return config.copy(
        speedCityKmH = citySpeed,
        speedHighwayKmH = highwaySpeed,
        speedHighKmH = highSpeed,
        noiseModerateDb = modNoise,
        noiseLoudDb = loudNoise,
        noiseExtremeDb = extremeNoise
    )
}

fun serializeAdaptiveCabinConfig(config: AdaptiveCabinConfig): String {
    val safe = sanitizeAdaptiveCabinConfig(config)
    return JSONObject().apply {
        put("enabled", safe.enabled)
        put("source", safe.source.id)
        put("sensitivity", safe.sensitivity.id)
        put("cabinFocus", safe.cabinFocus.id)
        put("speedCityKmH", safe.speedCityKmH)
        put("speedHighwayKmH", safe.speedHighwayKmH)
        put("speedHighKmH", safe.speedHighKmH)
        put("noiseModerateDb", safe.noiseModerateDb)
        put("noiseLoudDb", safe.noiseLoudDb)
        put("noiseExtremeDb", safe.noiseExtremeDb)
    }.toString()
}

fun deserializeAdaptiveCabinConfig(json: String?): AdaptiveCabinConfig {
    if (json.isNullOrBlank()) return AdaptiveCabinConfig()
    return try {
        val obj = JSONObject(json)
        sanitizeAdaptiveCabinConfig(
            AdaptiveCabinConfig(
                enabled = obj.optBoolean("enabled", false),
                source = AdaptiveCabinSource.fromId(obj.optString("source", "hybrid")),
                sensitivity = AdaptiveCabinSensitivity.fromId(obj.optString("sensitivity", "balanced")),
                cabinFocus = AdaptiveCabinFocus.fromId(obj.optString("cabinFocus", "driver_lhd")),
                speedCityKmH = obj.optInt("speedCityKmH", 45),
                speedHighwayKmH = obj.optInt("speedHighwayKmH", 85),
                speedHighKmH = obj.optInt("speedHighKmH", 120),
                noiseModerateDb = obj.optInt("noiseModerateDb", 58),
                noiseLoudDb = obj.optInt("noiseLoudDb", 68),
                noiseExtremeDb = obj.optInt("noiseExtremeDb", 76)
            )
        )
    } catch (_: Throwable) {
        AdaptiveCabinConfig()
    }
}

private fun classifyBySpeed(
    speedKmH: Float,
    currentRank: Int,
    config: AdaptiveCabinConfig
): AdaptiveCabinProfile {
    if (!speedKmH.isFinite() || speedKmH <= 0f) return AdaptiveCabinProfile.QUIET
    val highTh = if (currentRank >= 3) config.speedHighKmH - SPEED_HYSTERESIS_KMH else config.speedHighKmH.toFloat()
    val hwTh = if (currentRank >= 2) config.speedHighwayKmH - SPEED_HYSTERESIS_KMH else config.speedHighwayKmH.toFloat()
    val cityTh = if (currentRank >= 1) config.speedCityKmH - SPEED_HYSTERESIS_KMH else config.speedCityKmH.toFloat()

    return when {
        speedKmH >= highTh -> AdaptiveCabinProfile.HIGH_NOISE
        speedKmH >= hwTh -> AdaptiveCabinProfile.HIGHWAY
        speedKmH >= cityTh -> AdaptiveCabinProfile.CITY
        else -> AdaptiveCabinProfile.QUIET
    }
}

private fun classifyByNoise(
    cabinNoiseDb: Float?,
    currentRank: Int,
    config: AdaptiveCabinConfig
): AdaptiveCabinProfile {
    if (cabinNoiseDb == null || !cabinNoiseDb.isFinite()) return AdaptiveCabinProfile.QUIET
    val extTh = if (currentRank >= 3) config.noiseExtremeDb - NOISE_HYSTERESIS_DB else config.noiseExtremeDb.toFloat()
    val loudTh = if (currentRank >= 2) config.noiseLoudDb - NOISE_HYSTERESIS_DB else config.noiseLoudDb.toFloat()
    val modTh = if (currentRank >= 1) config.noiseModerateDb - NOISE_HYSTERESIS_DB else config.noiseModerateDb.toFloat()

    return when {
        cabinNoiseDb >= extTh -> AdaptiveCabinProfile.HIGH_NOISE
        cabinNoiseDb >= loudTh -> AdaptiveCabinProfile.HIGHWAY
        cabinNoiseDb >= modTh -> AdaptiveCabinProfile.CITY
        else -> AdaptiveCabinProfile.QUIET
    }
}

fun evaluateAdaptiveCabinState(
    prevState: AdaptiveEvaluationState,
    config: AdaptiveCabinConfig,
    cabinNoiseDb: Float?,
    speedKmH: Float,
    nowMs: Long
): AdaptiveEvaluationState {
    if (!config.enabled) {
        return AdaptiveEvaluationState(
            activeProfile = AdaptiveCabinProfile.QUIET,
            pendingProfile = null,
            pendingSinceMs = 0L,
            triggerReason = "disabled",
            smoothedSpeedKmH = 0f,
            lastEvaluatedAtMs = 0L
        )
    }

    val isInitialSample = prevState.lastEvaluatedAtMs <= 0L
    val dtMs = if (isInitialSample) 0L else max(0L, nowMs - prevState.lastEvaluatedAtMs)
    val smoothedSpeedKmH = smoothSpeedSample(
        prevSmoothedKmH = prevState.smoothedSpeedKmH,
        rawSpeedKmH = speedKmH,
        dtMs = dtMs,
        isInitialSample = isInitialSample
    )

    val currentRank = prevState.activeProfile.rank
    val useMic = config.source == AdaptiveCabinSource.MICROPHONE || config.source == AdaptiveCabinSource.HYBRID
    val useSpeed = config.source == AdaptiveCabinSource.SPEED || config.source == AdaptiveCabinSource.HYBRID

    val micProfile = if (useMic) classifyByNoise(cabinNoiseDb, currentRank, config) else AdaptiveCabinProfile.QUIET
    val speedProfile = if (useSpeed) classifyBySpeed(smoothedSpeedKmH, currentRank, config) else AdaptiveCabinProfile.QUIET

    val targetProfile: AdaptiveCabinProfile
    val reason: String
    when {
        micProfile.rank == 0 && speedProfile.rank == 0 -> {
            targetProfile = AdaptiveCabinProfile.QUIET
            reason = "idle"
        }
        micProfile.rank > speedProfile.rank -> {
            targetProfile = micProfile
            reason = "microphone"
        }
        speedProfile.rank > micProfile.rank -> {
            targetProfile = speedProfile
            reason = "speed"
        }
        else -> {
            targetProfile = micProfile
            reason = if (config.source == AdaptiveCabinSource.HYBRID) "hybrid" else config.source.id
        }
    }

    if (targetProfile == prevState.activeProfile) {
        return AdaptiveEvaluationState(
            activeProfile = prevState.activeProfile,
            pendingProfile = null,
            pendingSinceMs = 0L,
            triggerReason = reason,
            smoothedSpeedKmH = smoothedSpeedKmH,
            lastEvaluatedAtMs = nowMs
        )
    }

    val requiredDwellMs = if (targetProfile.rank > currentRank) ESCALATE_DWELL_MS else DEESCALATE_DWELL_MS
    if (prevState.pendingProfile != targetProfile) {
        return prevState.copy(
            pendingProfile = targetProfile,
            pendingSinceMs = nowMs,
            smoothedSpeedKmH = smoothedSpeedKmH,
            lastEvaluatedAtMs = nowMs
        )
    }

    val elapsed = nowMs - prevState.pendingSinceMs
    return if (elapsed >= requiredDwellMs) {
        AdaptiveEvaluationState(
            activeProfile = targetProfile,
            pendingProfile = null,
            pendingSinceMs = 0L,
            triggerReason = reason,
            smoothedSpeedKmH = smoothedSpeedKmH,
            lastEvaluatedAtMs = nowMs
        )
    } else {
        prevState.copy(
            smoothedSpeedKmH = smoothedSpeedKmH,
            lastEvaluatedAtMs = nowMs
        )
    }
}

fun calculateAdaptiveDspDelta(
    profile: AdaptiveCabinProfile,
    config: AdaptiveCabinConfig
): AdaptiveDspDelta {
    if (!config.enabled || profile == AdaptiveCabinProfile.QUIET) {
        return AdaptiveDspDelta()
    }
    val scale = config.sensitivity.scale
    val gainDb = clampStep(profile.baseGainDb * scale, 0f, 6f, 0.5f)
    val rawBal = ((profile.baseBalanceShift * scale) / 5f).roundToInt() * 5
    val balanceDelta = when (config.cabinFocus) {
        AdaptiveCabinFocus.DRIVER_LHD -> -rawBal
        AdaptiveCabinFocus.DRIVER_RHD -> rawBal
        AdaptiveCabinFocus.FRONT_CENTER -> 0
    }
    val faderDelta = ((profile.baseFaderShift * scale) / 5f).roundToInt() * 5
    val bassTiltDb = clampStep(profile.baseBassTiltDb * scale, 0f, 4.5f, 0.5f)
    val vocalTiltDb = clampStep(profile.baseVocalTiltDb * scale, 0f, 2.5f, 0.5f)

    return AdaptiveDspDelta(
        profile = profile,
        gainDb = gainDb,
        balanceDelta = balanceDelta,
        faderDelta = faderDelta,
        bassTiltDb = bassTiltDb,
        vocalTiltDb = vocalTiltDb
    )
}

fun applyAdaptiveCabinOverlay(base: DspSettings, delta: AdaptiveDspDelta): DspSettings {
    if (delta.gainDb == 0f &&
        delta.balanceDelta == 0 &&
        delta.faderDelta == 0 &&
        delta.bassTiltDb == 0f &&
        delta.vocalTiltDb == 0f
    ) {
        return base
    }

    val nextBands = base.bands16.toMutableList()
    if (nextBands.size == 16) {
        if (delta.bassTiltDb > 0f) {
            nextBands[0] = clampStep(nextBands[0] + delta.bassTiltDb * 0.85f, -12f, 12f, 0.1f)
            nextBands[1] = clampStep(nextBands[1] + delta.bassTiltDb, -12f, 12f, 0.1f)
            nextBands[2] = clampStep(nextBands[2] + delta.bassTiltDb * 0.55f, -12f, 12f, 0.1f)
        }
        if (delta.vocalTiltDb > 0f) {
            nextBands[6] = clampStep(nextBands[6] + delta.vocalTiltDb * 0.6f, -12f, 12f, 0.1f)
            nextBands[7] = clampStep(nextBands[7] + delta.vocalTiltDb, -12f, 12f, 0.1f)
            nextBands[8] = clampStep(nextBands[8] + delta.vocalTiltDb * 0.7f, -12f, 12f, 0.1f)
        }
    }

    return base.copy(
        preampDb = clampStep(base.preampDb + delta.gainDb, -12f, 6f, 0.1f),
        balance = (base.balance + delta.balanceDelta).coerceIn(-100, 100),
        fader = (base.fader + delta.faderDelta).coerceIn(-100, 100),
        bands16 = nextBands
    )
}

enum class CalibrationBalanceStatus(val hint: String) {
    STANDBY("Standby"),
    SPEED_ONLY("Speed Only"),
    QUIET_FLOOR("Below Trigger"),
    BALANCED("Calibrated"),
    UNDER_COMPENSATED("Raise Gain"),
    OVER_COMPENSATED("Gain High")
}

data class NoiseGainCalibrationDelta(
    val noiseAboveFloorDb: Float?,
    val appliedGainDb: Float,
    val deltaDb: Float?,
    val effectiveNoiseDb: Float?,
    val status: CalibrationBalanceStatus,
    val hint: String
)

fun calculateNoiseGainCalibrationDelta(
    cabinNoiseDb: Float?,
    config: AdaptiveCabinConfig,
    activeDelta: AdaptiveDspDelta
): NoiseGainCalibrationDelta {
    val appliedGainDb = (activeDelta.gainDb * 10f).roundToInt() / 10f
    val hasValidMic = cabinNoiseDb != null && cabinNoiseDb.isFinite()

    if (!config.enabled) {
        return NoiseGainCalibrationDelta(
            noiseAboveFloorDb = if (hasValidMic) ((cabinNoiseDb!! - config.noiseModerateDb) * 10f).roundToInt() / 10f else null,
            appliedGainDb = 0f,
            deltaDb = null,
            effectiveNoiseDb = if (hasValidMic) (cabinNoiseDb!! * 10f).roundToInt() / 10f else null,
            status = CalibrationBalanceStatus.STANDBY,
            hint = "Adaptive Off"
        )
    }

    if (config.source == AdaptiveCabinSource.SPEED || !hasValidMic) {
        val status = if (config.source == AdaptiveCabinSource.SPEED) {
            CalibrationBalanceStatus.SPEED_ONLY
        } else {
            CalibrationBalanceStatus.STANDBY
        }
        return NoiseGainCalibrationDelta(
            noiseAboveFloorDb = null,
            appliedGainDb = appliedGainDb,
            deltaDb = null,
            effectiveNoiseDb = null,
            status = status,
            hint = if (status == CalibrationBalanceStatus.SPEED_ONLY) "Speed Only" else "Mic Standby"
        )
    }

    val noiseAboveFloorDb = ((cabinNoiseDb!! - config.noiseModerateDb) * 10f).roundToInt() / 10f
    val deltaDb = ((noiseAboveFloorDb - appliedGainDb) * 10f).roundToInt() / 10f
    val effectiveNoiseDb = ((cabinNoiseDb - appliedGainDb) * 10f).roundToInt() / 10f

    val status = when {
        noiseAboveFloorDb < 0f && appliedGainDb <= 0.1f -> CalibrationBalanceStatus.QUIET_FLOOR
        deltaDb <= -2.0f && appliedGainDb > 0.1f -> CalibrationBalanceStatus.OVER_COMPENSATED
        deltaDb >= 10.0f -> CalibrationBalanceStatus.UNDER_COMPENSATED
        else -> CalibrationBalanceStatus.BALANCED
    }

    return NoiseGainCalibrationDelta(
        noiseAboveFloorDb = noiseAboveFloorDb,
        appliedGainDb = appliedGainDb,
        deltaDb = deltaDb,
        effectiveNoiseDb = effectiveNoiseDb,
        status = status,
        hint = status.hint
    )
}

/**
 * Estimates automotive cabin dB SPL (32..98 dB) from 16-bit PCM mono samples.
 */
fun estimateCabinNoiseDbFromPcm16(samples: ShortArray, count: Int = samples.size): Float {
    val n = count.coerceAtMost(samples.size)
    if (n <= 0) return 35f
    var sumSq = 0.0
    for (i in 0 until n) {
        val norm = samples[i] / 32768.0
        sumSq += norm * norm
    }
    val rms = sqrt(sumSq / n)
    val dbfs = 20.0 * log10(max(rms, 1e-5))
    val spl = (dbfs + 102.0).coerceIn(32.0, 98.0)
    return ((spl * 10.0).roundToInt() / 10.0).toFloat()
}
