package com.zentryx.music.auto

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.constraints.ConstraintManager
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.zentryx.music.dsp.AndroidDspAudioEngine
import com.zentryx.music.dsp.CompressorProfile
import com.zentryx.music.dsp.FACTORY_DSP_PRESETS
import com.zentryx.music.dsp.HpfCutoffHz
import com.zentryx.music.player.ZentryxMediaBrowseHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Allows safe one-tap 16-band DSP preset switching (e.g. Pioneer TS-E170Ci Kit,
 * W203 Sound System, Highway Cruiser) directly from the Android Auto display.
 */
class ZentryxCarDspScreen(
    carContext: CarContext,
    private val dspAudioEngine: AndroidDspAudioEngine
) : Screen(carContext), DefaultLifecycleObserver {

    private val screenScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observerJob: Job? = null

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        observerJob?.cancel()
        observerJob = screenScope.launch {
            dspAudioEngine.dspSettings.collectLatest {
                invalidate()
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        observerJob?.cancel()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        screenScope.cancel()
    }

    override fun onGetTemplate(): Template {
        val current = dspAudioEngine.dspSettings.value
        val allPresets = dspAudioEngine.customPresets.value + FACTORY_DSP_PRESETS

        val hostLimit = try {
            carContext.getCarService(ConstraintManager::class.java)
                .getContentLimit(ConstraintManager.CONTENT_LIMIT_TYPE_LIST)
        } catch (_: Throwable) {
            ZentryxMediaBrowseHelper.DEFAULT_CAR_LIST_LIMIT
        }

        val listBuilder = ItemList.Builder()
        listBuilder.addItem(
            Row.Builder()
                .setTitle(if (current.dspEnabled) "Bypass DSP (Currently Active)" else "Enable 16-Band Car DSP")
                .addText(
                    if (current.dspEnabled) "Tap to hear flat DAC output"
                    else "Tap to restore hardware EQ, HPF & time alignment"
                )
                .setOnClickListener {
                    dspAudioEngine.updateSettings(current.copy(dspEnabled = !current.dspEnabled))
                }
                .build()
        )

        val remainingSlots = (hostLimit - 1).coerceAtLeast(1)
        ZentryxMediaBrowseHelper.capForCarList(allPresets, remainingSlots).forEach { preset ->
            val isSelected = current.dspEnabled && current.selectedPresetId == preset.id
            listBuilder.addItem(
                Row.Builder()
                    .setTitle(if (isSelected) "✓ ${preset.name}" else preset.name)
                    .addText(preset.description)
                    .setOnClickListener {
                        dspAudioEngine.updateSettings(
                            current.copy(
                                dspEnabled = true,
                                selectedPresetId = preset.id,
                                bands16 = preset.bands16,
                                bassBoostDb = preset.bassBoostDb,
                                bassBoostFreqHz = preset.bassBoostFreqHz,
                                preampDb = preset.preampDb,
                                hpfCutoffHz = preset.hpfCutoffHz?.let { HpfCutoffHz.fromHz(it) } ?: current.hpfCutoffHz,
                                loudnessContour = preset.loudnessContour ?: current.loudnessContour,
                                compressorProfile = preset.compressorProfileId?.let { CompressorProfile.fromId(it) }
                                    ?: current.compressorProfile
                            )
                        )
                    }
                    .build()
            )
        }

        return ListTemplate.Builder()
            .setTitle("Audio DSP Profiles")
            .setHeaderAction(Action.BACK)
            .setSingleList(listBuilder.build())
            .build()
    }
}
