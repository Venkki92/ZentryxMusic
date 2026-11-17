package com.zentryx.music.dsp

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.util.Log
import com.zentryx.music.player.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Native Android Headunit Audio DSP Engine.
 * - On Android 9+ (API 28+, e.g., AutoChips AC8259P Pie): attaches full 16-band DynamicsProcessing
 *   with pre-EQ, L/R channel preamp + balance trims, multiband dynamics compression, and soft limiter.
 * - On Android 8.0/8.1 (API 26–27): cleanly falls back to hardware Equalizer + BassBoost + LoudnessEnhancer.
 */
@Singleton
class AndroidDspAudioEngine @Inject constructor(
    private val settingsPreferences: SettingsPreferences
) {
    companion object {
        private const val TAG = "ZentryxDspEngine"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _dspSettings = MutableStateFlow(DspSettings())
    val dspSettings: StateFlow<DspSettings> = _dspSettings.asStateFlow()

    private val _customPresets = MutableStateFlow<List<DspPreset16>>(emptyList())
    val customPresets: StateFlow<List<DspPreset16>> = _customPresets.asStateFlow()

    private val _isAttached = MutableStateFlow(false)
    val isAttached: StateFlow<Boolean> = _isAttached.asStateFlow()

    private var currentAudioSessionId: Int = 0
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var legacyEqualizer: Equalizer? = null
    private var legacyBassBoost: BassBoost? = null
    private var legacyLoudness: LoudnessEnhancer? = null

    init {
        scope.launch {
            settingsPreferences.dspSettingsJsonFlow.collectLatest { json ->
                val parsed = deserializeDspSettings(json)
                _dspSettings.value = parsed
                applyDspToHardware(parsed)
            }
        }
        scope.launch {
            settingsPreferences.customDspPresetsJsonFlow.collectLatest { json ->
                _customPresets.value = deserializeCustomPresets(json)
            }
        }
    }

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (audioSessionId == currentAudioSessionId && _isAttached.value) return
        releaseEffects()
        currentAudioSessionId = audioSessionId

        var attached = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            attached = initDynamicsProcessing(audioSessionId)
        }
        if (!attached) {
            attached = initLegacyEffects(audioSessionId)
        }
        _isAttached.value = attached
        applyDspToHardware(_dspSettings.value)
    }

    private fun initDynamicsProcessing(sessionId: Int): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        return try {
            val builder = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true, 16,
                true, 1,
                false, 0,
                true
            )
            builder.setPreferredFrameDuration(10f)
            val dp = DynamicsProcessing(0, sessionId, builder.build())
            dp.enabled = _dspSettings.value.dspEnabled
            dynamicsProcessing = dp
            true
        } catch (t: Throwable) {
            Log.w(TAG, "DynamicsProcessing unavailable on headunit, falling back to Legacy Equalizer: ${t.message}")
            dynamicsProcessing?.release()
            dynamicsProcessing = null
            false
        }
    }

    private fun initLegacyEffects(sessionId: Int): Boolean {
        return try {
            val eq = Equalizer(0, sessionId)
            val bb = try { BassBoost(0, sessionId) } catch (_: Throwable) { null }
            val le = try { LoudnessEnhancer(sessionId) } catch (_: Throwable) { null }
            legacyEqualizer = eq
            legacyBassBoost = bb
            legacyLoudness = le
            true
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy AudioEffect attach failed: ${t.message}")
            false
        }
    }

    fun updateSettings(newSettings: DspSettings) {
        _dspSettings.value = newSettings
        applyDspToHardware(newSettings)
        scope.launch {
            settingsPreferences.saveDspSettingsJson(serializeDspSettings(newSettings))
        }
    }

    fun saveCustomPreset(name: String) {
        val cleanName = name.trim().ifBlank { "Custom ${_customPresets.value.size + 1}" }
        val s = _dspSettings.value
        val preset = DspPreset16(
            id = "custom_${System.currentTimeMillis()}",
            name = cleanName,
            description = "Saved headunit calibration",
            bands16 = s.bands16,
            bassBoostDb = s.bassBoostDb,
            bassBoostFreqHz = s.bassBoostFreqHz,
            preampDb = s.preampDb,
            hpfCutoffHz = s.hpfCutoffHz.hz,
            loudnessContour = s.loudnessContour,
            compressorProfileId = s.compressorProfile.id,
            isCustom = true
        )
        val updated = listOf(preset) + _customPresets.value
        _customPresets.value = updated
        scope.launch {
            settingsPreferences.saveCustomDspPresetsJson(serializeCustomPresets(updated))
        }
        updateSettings(s.copy(selectedPresetId = preset.id))
    }

    fun deleteCustomPreset(presetId: String) {
        val updated = _customPresets.value.filterNot { it.id == presetId }
        _customPresets.value = updated
        scope.launch {
            settingsPreferences.saveCustomDspPresetsJson(serializeCustomPresets(updated))
        }
    }

    private fun applyDspToHardware(settings: DspSettings) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dp = dynamicsProcessing
            if (dp != null) {
                try {
                    dp.enabled = settings.dspEnabled
                    if (!settings.dspEnabled) return

                    // Balance + preamp gain per L/R channel
                    val (leftBalDb, rightBalDb) = calculateChannelBalanceDb(settings.balance)
                    dp.setInputGainbyChannel(0, (settings.preampDb + leftBalDb).coerceIn(-30f, 12f))
                    dp.setInputGainbyChannel(1, (settings.preampDb + rightBalDb).coerceIn(-30f, 12f))

                    // 16-band Parametric Pre-EQ
                    for (i in DSP_16_FREQUENCIES.indices) {
                        val bandGain = effectiveBandGainDb(i, settings)
                        val eqBand = DynamicsProcessing.EqBand(true, DSP_16_FREQUENCIES[i], bandGain)
                        dp.setPreEqBandAllChannelsTo(i, eqBand)
                    }

                    // Dynamics Compressor stage (MBC)
                    val comp = settings.compressorProfile
                    val mbcEnabled = comp != CompressorProfile.OFF
                    val mbcBand = DynamicsProcessing.MbcBand(
                        mbcEnabled,
                        20000f,
                        comp.attackMs,
                        comp.releaseMs,
                        comp.ratio.coerceAtLeast(1f),
                        comp.thresholdDb,
                        comp.kneeWidthDb,
                        -90f,
                        1f,
                        0f,
                        0f
                    )
                    dp.setMbcBandAllChannelsTo(0, mbcBand)

                    // Safety Brickwall Output Limiter (prevents DAC clipping)
                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        2f,
                        80f,
                        10f,
                        -0.5f,
                        0f
                    )
                    dp.setLimiterAllChannelsTo(limiter)
                    return
                } catch (t: Throwable) {
                    Log.w(TAG, "Error updating DynamicsProcessing: ${t.message}")
                }
            }
        }

        // Legacy Equalizer fallback (API 26–27 or restricted firmware)
        try {
            val eq = legacyEqualizer
            if (eq != null) {
                eq.enabled = settings.dspEnabled
                if (settings.dspEnabled) {
                    val range = eq.bandLevelRange
                    val minMb = range[0].toInt()
                    val maxMb = range[1].toInt()
                    val numBands = eq.numberOfBands.toInt()
                    for (b in 0 until numBands) {
                        val centerHz = eq.getCenterFreq(b.toShort()) / 1000f
                        val closestIdx = DSP_16_FREQUENCIES.indices.minByOrNull { abs(DSP_16_FREQUENCIES[it] - centerHz) } ?: 0
                        val targetMb = (effectiveBandGainDb(closestIdx, settings) * 100f).toInt().coerceIn(minMb, maxMb)
                        eq.setBandLevel(b.toShort(), targetMb.toShort())
                    }
                }
            }
            legacyBassBoost?.let { bb ->
                bb.enabled = settings.dspEnabled && settings.bassBoostDb > 0.5f
                if (bb.strengthSupported) {
                    val strength = ((settings.bassBoostDb / 12f) * 1000f).toInt().coerceIn(0, 1000)
                    bb.setStrength(strength.toShort())
                }
            }
            legacyLoudness?.let { le ->
                le.enabled = settings.dspEnabled && settings.loudnessContour
                if (settings.loudnessContour) {
                    le.setTargetGain(((settings.preampDb + 4f).coerceIn(0f, 8f) * 100f).toInt())
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Legacy Equalizer apply warning: ${t.message}")
        }
    }

    fun releaseEffects() {
        try { dynamicsProcessing?.release() } catch (_: Throwable) {}
        try { legacyEqualizer?.release() } catch (_: Throwable) {}
        try { legacyBassBoost?.release() } catch (_: Throwable) {}
        try { legacyLoudness?.release() } catch (_: Throwable) {}
        dynamicsProcessing = null
        legacyEqualizer = null
        legacyBassBoost = null
        legacyLoudness = null
        _isAttached.value = false
    }
}
