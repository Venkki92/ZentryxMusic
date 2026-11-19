package com.zentryx.music.dsp

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zentryx.music.player.SettingsPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
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
 * - Combines CarDspAudioProcessor (Butterworth HPF, Sub LPF/Phase, Mono fold-down, ms L/R Time Alignment, Limiter ceiling)
 *   with hardware DynamicsProcessing (16-band Pre-EQ, Preamp/Cabin Stage + GALA SDVC boost, MBC, Limiter).
 * - Monitors active AudioOutputRoute (Cabin Speakers, Bluetooth A2DP, USB DAC / AUX) for auto-profiles.
 * - Provides instant A/B DSP profile comparison and temporary Hold-to-Bypass comparison.
 */
@Singleton
class AndroidDspAudioEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsPreferences: SettingsPreferences,
    private val carDspAudioProcessor: CarDspAudioProcessor
) {
    companion object {
        private const val TAG = "ZentryxDspEngine"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private val _dspSettings = MutableStateFlow(DspSettings())
    val dspSettings: StateFlow<DspSettings> = _dspSettings.asStateFlow()

    private val _customPresets = MutableStateFlow<List<DspPreset16>>(emptyList())
    val customPresets: StateFlow<List<DspPreset16>> = _customPresets.asStateFlow()

    private val _isAttached = MutableStateFlow(false)
    val isAttached: StateFlow<Boolean> = _isAttached.asStateFlow()

    private val _isGlobalEqAttached = MutableStateFlow(false)
    val isGlobalEqAttached: StateFlow<Boolean> = _isGlobalEqAttached.asStateFlow()

    private val _activeOutputRoute = MutableStateFlow(detectCurrentOutputRoute())
    val activeOutputRoute: StateFlow<AudioOutputRoute> = _activeOutputRoute.asStateFlow()

    // A/B Profile Compare state (Slot A is live unless activeSlot == 'B')
    private val _activeCompareSlot = MutableStateFlow("A")
    val activeCompareSlot: StateFlow<String> = _activeCompareSlot.asStateFlow()

    private var slotBSnapshot: DspSettings = applyPresetToDsp(DspSettings(), FACTORY_DSP_PRESETS.first { it.id == "flat" })
    private val _slotBPresetName = MutableStateFlow("Flat / Studio Direct")
    val slotBPresetName: StateFlow<String> = _slotBPresetName.asStateFlow()

    private var isHoldBypassed: Boolean = false
    private var preHoldDspEnabled: Boolean = true

    private var currentAudioSessionId: Int = 0
    private var globalSessionRequested: Boolean = false
    private var attachedSessionId: Int = -1
    private var dynamicSdvcBoostDb: Float = 0f
    private var currentVehicleSpeedKmH: Float = 0f
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var legacyEqualizer: Equalizer? = null
    private var legacyBassBoost: BassBoost? = null
    private var legacyLoudness: LoudnessEnhancer? = null

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            handleOutputRouteChanged(detectCurrentOutputRoute())
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            handleOutputRouteChanged(detectCurrentOutputRoute())
        }
    }

    init {
        scope.launch {
            settingsPreferences.dspSettingsJsonFlow.collectLatest { json ->
                val parsed = deserializeDspSettings(json)
                _dspSettings.value = parsed
                carDspAudioProcessor.updateSettings(parsed)
                applyDspToHardware(parsed)
            }
        }
        scope.launch {
            settingsPreferences.customDspPresetsJsonFlow.collectLatest { json ->
                _customPresets.value = deserializeCustomPresets(json)
            }
        }
        scope.launch {
            settingsPreferences.globalAudioSessionAttachFlow.collectLatest { globalEnabled ->
                if (globalEnabled != globalSessionRequested) {
                    globalSessionRequested = globalEnabled
                    rebindAudioSession()
                }
            }
        }
        try {
            audioManager?.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
        } catch (t: Throwable) {
            Log.w(TAG, "AudioDeviceCallback registration warning: ${t.message}")
        }
    }

    private fun detectCurrentOutputRoute(): AudioOutputRoute {
        val devices = try {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        } catch (_: Throwable) {
            emptyArray()
        }
        if (devices.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO }) {
            return AudioOutputRoute.BLUETOOTH_A2DP
        }
        if (devices.any {
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_AUX_LINE
            }
        ) {
            return AudioOutputRoute.USB_DAC_AUX
        }
        return AudioOutputRoute.HEADUNIT_SPEAKERS
    }

    private fun handleOutputRouteChanged(newRoute: AudioOutputRoute) {
        if (_activeOutputRoute.value == newRoute) return
        _activeOutputRoute.value = newRoute
        val current = _dspSettings.value
        if (current.autoSwitchDevicePreset) {
            val targetPresetId = current.devicePresetMap[newRoute.id]
            val preset = (FACTORY_DSP_PRESETS + _customPresets.value).firstOrNull { it.id == targetPresetId }
            if (preset != null && current.selectedPresetId != preset.id) {
                updateSettings(applyPresetToDsp(current, preset))
            }
        }
    }

    fun selectOutputRouteManually(route: AudioOutputRoute) {
        _activeOutputRoute.value = route
        val current = _dspSettings.value
        if (current.autoSwitchDevicePreset) {
            val targetPresetId = current.devicePresetMap[route.id]
            val preset = (FACTORY_DSP_PRESETS + _customPresets.value).firstOrNull { it.id == targetPresetId }
            if (preset != null) {
                updateSettings(applyPresetToDsp(current, preset))
            }
        }
    }

    fun snapshotCurrentToSlotB() {
        val current = _dspSettings.value
        slotBSnapshot = current
        val name = (FACTORY_DSP_PRESETS + _customPresets.value)
            .firstOrNull { it.id == current.selectedPresetId }?.name ?: "Custom Curve"
        _slotBPresetName.value = name
    }

    fun toggleCompareSlot() {
        val current = _dspSettings.value
        val previousOther = slotBSnapshot
        slotBSnapshot = current
        val oldName = (FACTORY_DSP_PRESETS + _customPresets.value)
            .firstOrNull { it.id == current.selectedPresetId }?.name ?: "Custom Curve"
        _slotBPresetName.value = oldName
        _activeCompareSlot.value = if (_activeCompareSlot.value == "A") "B" else "A"
        updateSettings(previousOther)
    }

    fun setHoldBypass(bypassed: Boolean) {
        if (bypassed && !isHoldBypassed) {
            isHoldBypassed = true
            preHoldDspEnabled = _dspSettings.value.dspEnabled
            val temp = _dspSettings.value.copy(dspEnabled = false)
            _dspSettings.value = temp
            carDspAudioProcessor.updateSettings(temp)
            applyDspToHardware(temp)
        } else if (!bypassed && isHoldBypassed) {
            isHoldBypassed = false
            val restored = _dspSettings.value.copy(dspEnabled = preHoldDspEnabled)
            _dspSettings.value = restored
            carDspAudioProcessor.updateSettings(restored)
            applyDspToHardware(restored)
        }
    }

    fun applyPresetById(presetId: String) {
        val preset = (FACTORY_DSP_PRESETS + _customPresets.value).firstOrNull { it.id == presetId } ?: return
        if (_dspSettings.value.selectedPresetId == preset.id) return
        updateSettings(applyPresetToDsp(_dspSettings.value, preset))
    }

    fun cycleSurroundMode(): SurroundMode {
        val modes = SurroundMode.entries
        val cur = _dspSettings.value.surroundMode
        val next = modes[(modes.indexOf(cur) + 1) % modes.size]
        updateSettings(_dspSettings.value.copy(dspEnabled = true, surroundMode = next))
        return next
    }

    fun cycleEqPreset(): DspPreset16 {
        val all = FACTORY_DSP_PRESETS + _customPresets.value
        val curIdx = all.indexOfFirst { it.id == _dspSettings.value.selectedPresetId }
        val next = all[(curIdx + 1) % all.size]
        updateSettings(applyPresetToDsp(_dspSettings.value, next))
        return next
    }

    fun updateVehicleSpeedKmH(speedKmH: Float) {
        currentVehicleSpeedKmH = speedKmH.coerceIn(0f, 240f)
        carDspAudioProcessor.setRoadNoiseSpeedKmH(currentVehicleSpeedKmH)
    }

    fun attachAudioSession(audioSessionId: Int) {
        if (audioSessionId <= 0 && !globalSessionRequested) return
        if (audioSessionId > 0) {
            currentAudioSessionId = audioSessionId
        }
        rebindAudioSession()
    }

    private fun rebindAudioSession() {
        val targetSession = if (globalSessionRequested) 0 else currentAudioSessionId
        if (targetSession < 0 || (!globalSessionRequested && targetSession == 0)) return
        if (targetSession == attachedSessionId && _isAttached.value) return

        releaseEffects()
        var attached = false
        var globalSucceeded = false

        if (globalSessionRequested) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attached = initDynamicsProcessing(0)
            }
            if (!attached) {
                attached = initLegacyEffects(0)
            }
            if (attached) {
                globalSucceeded = true
                attachedSessionId = 0
            } else if (currentAudioSessionId > 0) {
                Log.w(TAG, "Global session 0 rejected by OEM firmware; falling back to player session $currentAudioSessionId")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    attached = initDynamicsProcessing(currentAudioSessionId)
                }
                if (!attached) {
                    attached = initLegacyEffects(currentAudioSessionId)
                }
                if (attached) attachedSessionId = currentAudioSessionId
            }
        } else if (currentAudioSessionId > 0) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                attached = initDynamicsProcessing(currentAudioSessionId)
            }
            if (!attached) {
                attached = initLegacyEffects(currentAudioSessionId)
            }
            if (attached) attachedSessionId = currentAudioSessionId
        }

        _isAttached.value = attached
        _isGlobalEqAttached.value = globalSucceeded
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
            dp.enabled = _dspSettings.value.dspEnabled || dynamicSdvcBoostDb > 0.1f
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
        carDspAudioProcessor.updateSettings(newSettings)
        applyDspToHardware(newSettings)
        scope.launch {
            settingsPreferences.saveDspSettingsJson(serializeDspSettings(newSettings))
        }
    }

    /**
     * Applies dynamic GPS GALA / SDVC speed compensation gain (in dB) directly through
     * the hardware pre-gain & brickwall limiter stage so highway boost never clips DACs.
     */
    fun setDynamicSdvcBoostDb(boostDb: Float) {
        val clamped = boostDb.coerceIn(0f, 6f)
        if (abs(clamped - dynamicSdvcBoostDb) < 0.1f) return
        dynamicSdvcBoostDb = clamped
        applyDspToHardware(_dspSettings.value)
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
        val sdvcActive = dynamicSdvcBoostDb > 0.1f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val dp = dynamicsProcessing
            if (dp != null) {
                try {
                    val active = settings.dspEnabled || sdvcActive
                    dp.enabled = active
                    if (!active) return

                    // Balance + Fader cabin stage + preamp + GALA SDVC speed gain per L/R channel
                    val (leftStageDb, rightStageDb) = calculateCabinStageGains(settings.balance, settings.fader)
                    val basePreamp = if (settings.dspEnabled) settings.preampDb else 0f
                    dp.setInputGainbyChannel(0, (basePreamp + (if (settings.dspEnabled) leftStageDb else 0f) + dynamicSdvcBoostDb).coerceIn(-30f, 12f))
                    dp.setInputGainbyChannel(1, (basePreamp + (if (settings.dspEnabled) rightStageDb else 0f) + dynamicSdvcBoostDb).coerceIn(-30f, 12f))

                    // 16-band Parametric Pre-EQ (zeroed if user bypassed DSP but kept SDVC active)
                    for (i in DSP_16_FREQUENCIES.indices) {
                        val bandGain = if (settings.dspEnabled) effectiveBandGainDb(i, settings) else 0f
                        val eqBand = DynamicsProcessing.EqBand(settings.dspEnabled, DSP_16_FREQUENCIES[i], bandGain)
                        dp.setPreEqBandAllChannelsTo(i, eqBand)
                    }

                    // Dynamics Compressor stage (MBC)
                    val comp = settings.compressorProfile
                    val mbcEnabled = settings.dspEnabled && comp != CompressorProfile.OFF
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

                    // Safety Brickwall Output Limiter with user-selectable peak ceiling
                    val ceiling = if (settings.dspEnabled) sanitizeLimiterCeilingDb(settings.peakLimiterCeilingDb) else -0.5f
                    val limiter = DynamicsProcessing.Limiter(
                        true,
                        true,
                        0,
                        2f,
                        80f,
                        10f,
                        ceiling,
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
                val loudnessBoostDb = (if (settings.dspEnabled && settings.loudnessContour) 4f else 0f) + dynamicSdvcBoostDb
                le.enabled = loudnessBoostDb > 0.2f
                if (le.enabled) {
                    le.setTargetGain((loudnessBoostDb.coerceIn(0f, 10f) * 100f).toInt())
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
        attachedSessionId = -1
        _isAttached.value = false
        _isGlobalEqAttached.value = false
    }
}
