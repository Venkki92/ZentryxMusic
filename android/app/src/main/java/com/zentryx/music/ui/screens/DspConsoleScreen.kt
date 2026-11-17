package com.zentryx.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zentryx.music.dsp.*
import com.zentryx.music.ui.theme.MbuxAmbientBorder
import com.zentryx.music.ui.theme.MbuxObsidian
import com.zentryx.music.ui.theme.MbuxSurface
import com.zentryx.music.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

/**
 * Native 16-Band Pro Headunit ESP & DSP Console for 1024x600 Landscape Cockpit.
 * Includes Pioneer TS-E170Ci Kit anti-tin calibration curve, Sub-Door HPF, Bass Boost,
 * Fletcher-Munson Loudness Contour, Time Alignment Stage, Balance, Dynamics Compressor, and Custom Presets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DspConsoleScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dsp by viewModel.dspSettings.collectAsStateWithLifecycle()
    val customPresets by viewModel.customDspPresets.collectAsStateWithLifecycle()
    val isAttached by viewModel.isDspAttached.collectAsStateWithLifecycle()
    val accent by viewModel.accentColor.collectAsStateWithLifecycle()
    val accentColor = Color(accent.hexColor)

    var newPresetName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = accentColor)
                        Text(
                            text = "PRO ESP & 16-BAND DSP CONSOLE",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.5.sp,
                                color = Color.White
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (dsp.dspEnabled) accentColor.copy(alpha = 0.16f) else Color(0xFF1E293B),
                            modifier = Modifier.border(0.5.dp, if (dsp.dspEnabled) accentColor else Color(0xFF334155), RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                text = when {
                                    !dsp.dspEnabled -> "BYPASSED"
                                    isAttached -> "HARDWARE DSP ACTIVE"
                                    else -> "ARMED (PLAY TO BIND)"
                                },
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (dsp.dspEnabled) accentColor else Color(0xFF94A3B8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        viewModel.updateDspSettings(
                            dsp.copy(
                                selectedPresetId = "flat",
                                bands16 = List(16) { 0f },
                                bassBoostDb = 0f,
                                preampDb = 0f,
                                hpfCutoffHz = HpfCutoffHz.OFF
                            )
                        )
                    }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Flatten", fontSize = 11.sp, color = Color.White)
                    }
                    Switch(
                        checked = dsp.dspEnabled,
                        onCheckedChange = { viewModel.updateDspSettings(dsp.copy(dspEnabled = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor),
                        modifier = Modifier.padding(end = 10.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MbuxObsidian),
                modifier = Modifier.height(50.dp)
            )
        },
        containerColor = MbuxObsidian,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Factory & Vehicle Presets (including Pioneer TS-E170Ci Kit)
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MbuxSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "VEHICLE & SPEAKER CALIBRATION PRESETS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color(0xFF94A3B8))
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        (FACTORY_DSP_PRESETS + customPresets).forEach { preset ->
                            val selected = dsp.selectedPresetId == preset.id
                            FilterChip(
                                selected = selected,
                                onClick = { viewModel.updateDspSettings(applyPresetToDsp(dsp, preset)) },
                                label = {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = preset.name,
                                            fontSize = 11.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (preset.isCustom) {
                                            IconButton(
                                                onClick = { viewModel.deleteCustomDspPreset(preset.id) },
                                                modifier = Modifier.size(18.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                            }
                                        }
                                    }
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor.copy(alpha = 0.22f),
                                    selectedLabelColor = accentColor
                                )
                            )
                        }
                    }

                    if (dsp.selectedPresetId == "pioneer_ts_e170ci") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.1f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "Pioneer TS-E170Ci Anti-Tin Active (60Hz HPF • +5.5dB @ 80Hz • -4.5dB glare notch • -6dB headroom trim). Tip: Set the passive crossover box tweeter attenuator jumper to -3 dB if door tweeters are pillar-mounted.",
                                fontSize = 11.sp,
                                color = accentColor,
                                lineHeight = 15.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            }

            // Card 2: 16-Band or 5-Band Parametric Equalizer + Preamp Trim
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MbuxSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (dsp.eqViewMode == EqViewMode.SIXTEEN_BAND) "16-BAND PARAMETRIC EQ (32 Hz – 16 kHz)" else "5-BAND QUICK DRIVE EQ",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = dsp.eqViewMode == EqViewMode.FIVE_BAND,
                                onClick = { viewModel.updateDspSettings(dsp.copy(eqViewMode = EqViewMode.FIVE_BAND)) },
                                label = { Text("5-Band", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                            )
                            FilterChip(
                                selected = dsp.eqViewMode == EqViewMode.SIXTEEN_BAND,
                                onClick = { viewModel.updateDspSettings(dsp.copy(eqViewMode = EqViewMode.SIXTEEN_BAND)) },
                                label = { Text("16-Band", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                            )
                        }
                    }

                    if (dsp.eqViewMode == EqViewMode.FIVE_BAND) {
                        EQ_5BAND_GROUPS.forEach { group ->
                            val avgGain = group.indices.map { dsp.bands16[it] }.average().toFloat()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(modifier = Modifier.width(120.dp)) {
                                    Text(group.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                    Text(group.freqHint, fontSize = 10.sp, color = Color(0xFF94A3B8))
                                }
                                Slider(
                                    value = avgGain,
                                    onValueChange = { target ->
                                        val updated = dsp.bands16.toMutableList()
                                        val snapped = clampStep(target, -12f, 12f)
                                        group.indices.forEach { idx -> updated[idx] = snapped }
                                        viewModel.updateDspSettings(dsp.copy(bands16 = updated, selectedPresetId = "custom"))
                                    },
                                    valueRange = -12f..12f,
                                    colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${if (avgGain >= 0) "+" else ""}${(avgGain * 10f).roundToInt() / 10f} dB",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = accentColor,
                                    modifier = Modifier.width(64.dp)
                                )
                            }
                        }
                    } else {
                        // 16 individual bands scrollable or grid cleanly sized for 1024x600
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DSP_16_LABELS.forEachIndexed { idx, freqLabel ->
                                val gain = dsp.bands16[idx]
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .width(56.dp)
                                        .background(Color(0xFF0A0F1E), RoundedCornerShape(8.dp))
                                        .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                        .padding(vertical = 8.dp, horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = "${if (gain >= 0) "+" else ""}$gain",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (gain != 0f) accentColor else Color(0xFF94A3B8)
                                    )
                                    Slider(
                                        value = gain,
                                        onValueChange = { v ->
                                            val next = dsp.bands16.toMutableList()
                                            next[idx] = clampStep(v, -12f, 12f)
                                            viewModel.updateDspSettings(dsp.copy(bands16 = next, selectedPresetId = "custom"))
                                        },
                                        valueRange = -12f..12f,
                                        colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                                        modifier = Modifier.width(52.dp)
                                    )
                                    Text(
                                        text = freqLabel,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // Preamp Headroom Trim (-12..+6 dB)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(modifier = Modifier.width(150.dp)) {
                            Text("Preamp Headroom Trim", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Prevents clipping with +EQ boost", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                        Slider(
                            value = dsp.preampDb,
                            onValueChange = { v ->
                                viewModel.updateDspSettings(dsp.copy(preampDb = clampStep(v, -12f, 6f), selectedPresetId = "custom"))
                            },
                            valueRange = -12f..6f,
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${if (dsp.preampDb >= 0) "+" else ""}${dsp.preampDb} dB",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = accentColor,
                            modifier = Modifier.width(64.dp)
                        )
                    }
                }
            }

            // Row 3: Two-Column Hardware DSP Controls for 1024x600 ergonomics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Left Card: Crossover HPF + Bass Boost + Loudness
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "DOOR WOOFER CROSSOVER & BASS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White)
                        )

                        Text("High-Pass Filter (Door Woofer Protection):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            HpfCutoffHz.entries.forEach { cutoff ->
                                val selected = dsp.hpfCutoffHz == cutoff
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateDspSettings(dsp.copy(hpfCutoffHz = cutoff)) },
                                    label = { Text(cutoff.label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Bass Shelf (+${dsp.bassBoostDb} dB):", fontSize = 11.sp, color = Color.White, modifier = Modifier.width(120.dp))
                            Slider(
                                value = dsp.bassBoostDb,
                                onValueChange = { viewModel.updateDspSettings(dsp.copy(bassBoostDb = clampStep(it, 0f, 12f))) },
                                valueRange = 0f..12f,
                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fletcher-Munson Loudness", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text("Adds warmth & highs at low listening volumes", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(
                                checked = dsp.loudnessContour,
                                onCheckedChange = { viewModel.updateDspSettings(dsp.copy(loudnessContour = it)) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                            )
                        }
                    }
                }

                // Right Card: Time Alignment, Balance, Compressor & Save Custom Preset
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .weight(1f)
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "STAGING, BALANCE & DYNAMICS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White)
                        )

                        Text("Cabin Listening Position / Alignment:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            listOf(
                                ListeningStagePreset.ALL,
                                ListeningStagePreset.DRIVER_LHD,
                                ListeningStagePreset.DRIVER_RHD,
                                ListeningStagePreset.CENTER
                            ).forEach { stage ->
                                val selected = dsp.listeningStage == stage
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        viewModel.updateDspSettings(
                                            dsp.copy(
                                                listeningStage = stage,
                                                leftDelayMs = stage.leftDelayMs,
                                                rightDelayMs = stage.rightDelayMs
                                            )
                                        )
                                    },
                                    label = { Text(stage.label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            val balLabel = when {
                                dsp.balance < 0 -> "L ${kotlin.math.abs(dsp.balance)}%"
                                dsp.balance > 0 -> "R ${dsp.balance}%"
                                else -> "Center"
                            }
                            Text("Balance ($balLabel):", fontSize = 11.sp, color = Color.White, modifier = Modifier.width(120.dp))
                            Slider(
                                value = dsp.balance.toFloat(),
                                onValueChange = { viewModel.updateDspSettings(dsp.copy(balance = it.roundToInt().coerceIn(-100, 100))) },
                                valueRange = -100f..100f,
                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Text("Dynamics Limiter / Road-Noise Compressor:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            CompressorProfile.entries.forEach { prof ->
                                val selected = dsp.compressorProfile == prof
                                FilterChip(
                                    selected = selected,
                                    onClick = { viewModel.updateDspSettings(dsp.copy(compressorProfile = prof)) },
                                    label = { Text(prof.label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = newPresetName,
                                onValueChange = { newPresetName = it },
                                placeholder = { Text("Save curve name…", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    viewModel.saveCustomDspPreset(newPresetName)
                                    newPresetName = ""
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
