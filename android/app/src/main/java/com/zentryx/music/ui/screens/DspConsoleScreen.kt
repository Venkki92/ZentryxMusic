package com.zentryx.music.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zentryx.music.dsp.*
import com.zentryx.music.viewmodel.SettingsViewModel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DspConsoleTab(val label: String) {
    EQ_PRESETS("EQ & Presets"),
    LOGIC7_SURROUND("Surround 3D"),
    STAGE_XOVER("Stage & X-Over"),
    CABIN_RTA("Cabin RTA")
}

/**
 * Native 16-Band Pro Headunit ESP, Harman Logic 7 Surround & Cabin RTA Console
 * organized cleanly into 4 segmented tabs for 1024x600 Landscape Cockpit ergonomics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DspConsoleScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateForward: () -> Unit = {},
    canGoBack: Boolean = true,
    canGoForward: Boolean = false,
    modifier: Modifier = Modifier
) {
    val dsp by viewModel.dspSettings.collectAsStateWithLifecycle()
    val customPresets by viewModel.customDspPresets.collectAsStateWithLifecycle()
    val isAttached by viewModel.isDspAttached.collectAsStateWithLifecycle()
    val isGlobalEqAttached by viewModel.isGlobalEqAttached.collectAsStateWithLifecycle()
    val isHwVirtualizerActive by viewModel.isHardwareVirtualizerActive.collectAsStateWithLifecycle()
    val globalEqRequested by viewModel.globalAudioSessionAttach.collectAsStateWithLifecycle()
    val activeOutputRoute by viewModel.activeOutputRoute.collectAsStateWithLifecycle()
    val activeCompareSlot by viewModel.activeCompareSlot.collectAsStateWithLifecycle()
    val slotBPresetName by viewModel.slotBPresetName.collectAsStateWithLifecycle()
    val currentSpeedKmH by viewModel.currentSpeedKmH.collectAsStateWithLifecycle()

    val rtaRunning by viewModel.rtaRunning.collectAsStateWithLifecycle()
    val rtaProgress by viewModel.rtaProgress.collectAsStateWithLifecycle()
    val rtaStatusText by viewModel.rtaStatusText.collectAsStateWithLifecycle()
    val rtaLiveBandsDb by viewModel.rtaLiveBandsDb.collectAsStateWithLifecycle()
    val rtaResult by viewModel.rtaResult.collectAsStateWithLifecycle()

    val adaptiveConfig by viewModel.adaptiveCabinConfig.collectAsStateWithLifecycle()
    val adaptiveState by viewModel.adaptiveEvaluationState.collectAsStateWithLifecycle()
    val adaptiveDelta by viewModel.adaptiveDspDelta.collectAsStateWithLifecycle()
    val cabinNoiseDb by viewModel.cabinNoiseDb.collectAsStateWithLifecycle()
    val cabinMicStatus by viewModel.cabinMicStatus.collectAsStateWithLifecycle()

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.onRecordAudioPermissionGranted()
        }
    }

    val accent by viewModel.accentColor.collectAsStateWithLifecycle()
    val accentColor = Color(accent.hexColor)
    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outline

    var activeTab by remember { mutableStateOf(DspConsoleTab.EQ_PRESETS) }
    var newPresetName by remember { mutableStateOf("") }
    var isHoldBypassActive by remember { mutableStateOf(false) }

    val (roadLowDb, roadMidDb) = calculateRoadNoiseContourDb(currentSpeedKmH, dsp.roadNoiseEqEnabled)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = accentColor)
                        Text(
                            text = "PRO DSP & SURROUND 3D",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                color = Color.White
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (dsp.dspEnabled) accentColor.copy(alpha = 0.16f) else Color(0xFF1E293B),
                            modifier = Modifier.border(0.5.dp, if (dsp.dspEnabled) accentColor else Color(0xFF334155), RoundedCornerShape(6.dp))
                        ) {
                            Text(
                                text = when {
                                    !dsp.dspEnabled -> "BYPASSED"
                                    isGlobalEqAttached -> "GLOBAL SESSION 0"
                                    isAttached -> "${dsp.surroundMode.badge} · ${dsp.crossoverSlopeDb}dB/OCT"
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onNavigateBack, enabled = canGoBack, modifier = Modifier.size(44.dp)) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = if (canGoBack) Color.White else Color(0xFF475569))
                        }
                        IconButton(onClick = onNavigateForward, enabled = canGoForward, modifier = Modifier.size(44.dp)) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", tint = if (canGoForward) Color.White else Color(0xFF475569))
                        }
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
                                hpfCutoffHz = HpfCutoffHz.OFF,
                                surroundMode = SurroundMode.OFF,
                                stereoWidthPercent = 100
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                modifier = Modifier.height(50.dp)
            )
        },
        containerColor = bgColor,
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Persistent Segmented 4-Tab Bar + Hold-to-Bypass
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .background(Color(0xFF0A0F1E), RoundedCornerShape(10.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    DspConsoleTab.entries.forEach { tab ->
                        val selected = activeTab == tab
                        Button(
                            onClick = { activeTab = tab },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (selected) accentColor else Color.Transparent,
                                contentColor = if (selected) Color.Black else Color(0xFFCBD5E1)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                        ) {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Persistent Hold-to-Bypass button always within thumb reach
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isHoldBypassActive) Color(0xFFEF4444).copy(alpha = 0.24f) else Color(0xFF0F172A),
                    modifier = Modifier
                        .height(42.dp)
                        .border(1.dp, if (isHoldBypassActive) Color(0xFFEF4444) else borderColor, RoundedCornerShape(10.dp))
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isHoldBypassActive = true
                                    viewModel.setHoldBypass(true)
                                    tryAwaitRelease()
                                    isHoldBypassActive = false
                                    viewModel.setHoldBypass(false)
                                }
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TouchApp,
                            contentDescription = null,
                            tint = if (isHoldBypassActive) Color(0xFFEF4444) else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isHoldBypassActive) "RAW BYPASS" else "HOLD BYPASS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            color = if (isHoldBypassActive) Color(0xFFEF4444) else Color.White
                        )
                    }
                }
            }

            // Tab Content Body (smoothly scrollable inside active tab)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (activeTab) {
                    DspConsoleTab.EQ_PRESETS -> {
                        // Presets + A/B Compare
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = surfaceColor,
                            modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "VEHICLE & SPEAKER CALIBRATION PRESETS",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color(0xFF94A3B8))
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        OutlinedButton(
                                            onClick = { viewModel.toggleCompareSlot() },
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.CompareArrows, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Slot $activeCompareSlot (Alt: $slotBPresetName)", fontSize = 10.sp, color = accentColor, maxLines = 1)
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.snapshotCurrentToSlotB() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Snap A→B", fontSize = 10.sp, color = Color.White)
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    (FACTORY_DSP_PRESETS + customPresets).forEach { preset ->
                                        val selected = dsp.selectedPresetId == preset.id
                                        FilterChip(
                                            selected = selected,
                                            onClick = { viewModel.updateDspSettings(applyPresetToDsp(dsp, preset)) },
                                            label = {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(preset.name, fontSize = 11.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                                                    if (preset.isCustom) {
                                                        IconButton(onClick = { viewModel.deleteCustomDspPreset(preset.id) }, modifier = Modifier.size(18.dp)) {
                                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFEF4444), modifier = Modifier.size(13.dp))
                                                        }
                                                    }
                                                }
                                            },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.22f), selectedLabelColor = accentColor)
                                        )
                                    }
                                }

                                if (dsp.selectedPresetId == "pioneer_ts_e170ci") {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = accentColor.copy(alpha = 0.1f),
                                        modifier = Modifier.fillMaxWidth().border(0.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    ) {
                                        Text(
                                            text = "Pioneer TS-E170Ci Anti-Tin Active (60Hz HPF • +5.5dB @ 80Hz • -4.5dB glare notch • -6dB headroom trim).",
                                            fontSize = 10.sp,
                                            color = accentColor,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Response Graph + 16-Band / 5-Band EQ + Preamp
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = surfaceColor,
                            modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                                EqResponseCurveCanvas(
                                    dsp = dsp,
                                    speedKmH = currentSpeedKmH,
                                    accentColor = accentColor,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(78.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color(0xFF060A14))
                                        .border(0.5.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                                )

                                if (dsp.eqViewMode == EqViewMode.FIVE_BAND) {
                                    EQ_5BAND_GROUPS.forEach { group ->
                                        val avgGain = group.indices.map { dsp.bands16[it] }.average().toFloat()
                                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                            Text("${if (avgGain >= 0) "+" else ""}${(avgGain * 10f).roundToInt() / 10f} dB", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = accentColor, modifier = Modifier.width(64.dp))
                                        }
                                    }
                                } else {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        DSP_16_LABELS.forEachIndexed { idx, freqLabel ->
                                            VerticalEqBandFader(
                                                freqLabel = freqLabel,
                                                gainDb = dsp.bands16[idx],
                                                accentColor = accentColor,
                                                onGainChange = { newGain ->
                                                    val next = dsp.bands16.toMutableList()
                                                    next[idx] = clampStep(newGain, -12f, 12f)
                                                    viewModel.updateDspSettings(dsp.copy(bands16 = next, selectedPresetId = "custom"))
                                                },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color(0xFF1E293B))

                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Preamp Trim (${if (dsp.preampDb >= 0) "+" else ""}${dsp.preampDb} dB)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(150.dp))
                                    Slider(
                                        value = dsp.preampDb,
                                        onValueChange = { v -> viewModel.updateDspSettings(dsp.copy(preampDb = clampStep(v, -12f, 6f), selectedPresetId = "custom")) },
                                        valueRange = -12f..6f,
                                        colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = newPresetName,
                                        onValueChange = { newPresetName = it },
                                        placeholder = { Text("Save curve…", fontSize = 11.sp) },
                                        singleLine = true,
                                        modifier = Modifier.width(150.dp)
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.saveCustomDspPreset(newPresetName)
                                            newPresetName = ""
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(15.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Save", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        // Output Device & Genre Auto-Profiles
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = surfaceColor,
                            modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        ) {
                            val allPresets = FACTORY_DSP_PRESETS + customPresets
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(imageVector = Icons.Default.Speaker, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                        Text("SMART AUTO-PROFILES (${activeOutputRoute.label})", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        AudioOutputRoute.entries.forEach { route ->
                                            FilterChip(
                                                selected = activeOutputRoute == route,
                                                onClick = { viewModel.selectOutputRouteManually(route) },
                                                label = { Text(route.label, fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                            )
                                        }
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Auto-Switch per Output Route", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Switch(checked = dsp.autoSwitchDevicePreset, onCheckedChange = { viewModel.updateDspSettings(dsp.copy(autoSwitchDevicePreset = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                                    }
                                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Auto-Preset by Genre / Folder", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Switch(checked = dsp.autoGenrePresetEnabled, onCheckedChange = { viewModel.updateDspSettings(dsp.copy(autoGenrePresetEnabled = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                                    }
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AudioOutputRoute.entries.forEach { route ->
                                        val assignedId = dsp.devicePresetMap[route.id] ?: "flat"
                                        val assignedName = allPresets.firstOrNull { it.id == assignedId }?.name ?: assignedId
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFF0A0F1E),
                                            modifier = Modifier
                                                .weight(1f)
                                                .border(0.5.dp, if (activeOutputRoute == route) accentColor else borderColor, RoundedCornerShape(8.dp))
                                                .pointerInput(route, dsp.devicePresetMap, allPresets) {
                                                    detectTapGestures {
                                                        val idx = allPresets.indexOfFirst { it.id == assignedId }
                                                        val nextPreset = allPresets[(idx + 1) % allPresets.size]
                                                        val updatedMap = dsp.devicePresetMap.toMutableMap().apply { put(route.id, nextPreset.id) }
                                                        viewModel.updateDspSettings(dsp.copy(devicePresetMap = updatedMap))
                                                    }
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                                                Text(route.label, fontSize = 9.sp, color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold)
                                                Text(assignedName, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Automated Cabin Noise & Speed Adaptive DSP Profile Switcher
                        AdaptiveCabinProfileCard(
                            config = adaptiveConfig,
                            evalState = adaptiveState,
                            delta = adaptiveDelta,
                            cabinNoiseDb = cabinNoiseDb,
                            micStatus = cabinMicStatus,
                            currentSpeedKmH = currentSpeedKmH,
                            accentColor = accentColor,
                            surfaceColor = surfaceColor,
                            borderColor = borderColor,
                            onUpdateConfig = { next ->
                                if (next.enabled && next.source != AdaptiveCabinSource.SPEED &&
                                    cabinMicStatus == CabinMicMonitorStatus.PERMISSION_REQUIRED
                                ) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                                viewModel.updateAdaptiveCabinConfig(next)
                            },
                            onRequestMicPermission = {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        )
                    }

                    DspConsoleTab.LOGIC7_SURROUND -> {
                        // Harman Logic 7 + Dolby Surround + 360° Virtualizer Matrix
                        val harmanModes = SurroundMode.entries.filter { !it.isDolbyOrVirtual }
                        val dolbyVirtualModes = SurroundMode.entries.filter { it.isDolbyOrVirtual }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = surfaceColor,
                            modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("SURROUND MATRIX — HARMAN LOGIC 7 • DOLBY • VIRTUAL 3D", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = accentColor))
                                        Text("M/S center vocal lock, 90° Dolby Pro Logic II rear matrix, Atmos pinna height & 360° XTC", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        if (dsp.surroundMode != SurroundMode.OFF && !dsp.monoFoldDown) {
                                            Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF0A0F1E), modifier = Modifier.border(0.5.dp, borderColor, RoundedCornerShape(6.dp))) {
                                                Text(
                                                    text = if (isHwVirtualizerActive) "PCM + HW VIRT" else "PCM 3D MATRIX",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color(0xFFCBD5E1),
                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                        Surface(shape = RoundedCornerShape(6.dp), color = accentColor.copy(alpha = 0.16f)) {
                                            Text(dsp.surroundMode.badge, fontSize = 10.sp, fontWeight = FontWeight.Black, color = accentColor, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                        }
                                    }
                                }

                                Text("DIRECT & HARMAN LOGIC 7 SERIES", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Color(0xFF64748B))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    harmanModes.forEach { mode ->
                                        val selected = dsp.surroundMode == mode
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) accentColor.copy(alpha = 0.16f) else Color(0xFF0A0F1E),
                                            modifier = Modifier
                                                .weight(1f)
                                                .border(1.dp, if (selected) accentColor else borderColor, RoundedCornerShape(10.dp))
                                                .pointerInput(mode) {
                                                    detectTapGestures {
                                                        viewModel.updateDspSettings(
                                                            dsp.copy(
                                                                dspEnabled = true,
                                                                surroundMode = mode,
                                                                monoFoldDown = if (mode != SurroundMode.OFF) false else dsp.monoFoldDown
                                                            )
                                                        )
                                                    }
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(mode.label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (selected) accentColor else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(mode.desc, fontSize = 10.sp, color = Color(0xFF94A3B8), lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    text = if (mode == SurroundMode.OFF) "0 ms Haas" else "Haas ${mode.haasLeftMs.toInt()}/${mode.haasRightMs.toInt()} ms",
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (selected) accentColor else Color(0xFF64748B)
                                                )
                                            }
                                        }
                                    }
                                }

                                Text("DOLBY SURROUND & VIRTUAL 3D SPATIALIZER ENGINES", fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Color(0xFF64748B))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    dolbyVirtualModes.forEach { mode ->
                                        val selected = dsp.surroundMode == mode
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (selected) accentColor.copy(alpha = 0.16f) else Color(0xFF0A0F1E),
                                            modifier = Modifier
                                                .weight(1f)
                                                .border(1.dp, if (selected) accentColor else borderColor, RoundedCornerShape(10.dp))
                                                .pointerInput(mode) {
                                                    detectTapGestures {
                                                        viewModel.updateDspSettings(
                                                            dsp.copy(
                                                                dspEnabled = true,
                                                                surroundMode = mode,
                                                                monoFoldDown = false
                                                            )
                                                        )
                                                    }
                                                }
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                                    Text(mode.label, fontSize = 12.sp, fontWeight = FontWeight.Black, color = if (selected) accentColor else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                                    Text(mode.badge, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = if (selected) accentColor else Color(0xFF94A3B8))
                                                }
                                                Text(mode.desc, fontSize = 10.sp, color = Color(0xFF94A3B8), lineHeight = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                                val tagSuffix = buildString {
                                                    append("Haas ${mode.haasLeftMs.toInt()}/${mode.haasRightMs.toInt()}ms")
                                                    if (mode.useAllPassPhase) append(" • 90° AP")
                                                    if (mode.crosstalkCancelGain > 0f) append(" • XTC")
                                                }
                                                Text(
                                                    text = tagSuffix,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = if (selected) accentColor else Color(0xFF64748B)
                                                )
                                            }
                                        }
                                    }
                                }

                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Surround Immersion", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("${dsp.surroundIntensity}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = accentColor)
                                        }
                                        Slider(
                                            value = dsp.surroundIntensity.toFloat(),
                                            onValueChange = { viewModel.updateDspSettings(dsp.copy(surroundIntensity = (it / 5f).roundToInt() * 5)) },
                                            valueRange = 0f..100f,
                                            enabled = dsp.surroundMode != SurroundMode.OFF,
                                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Center Lock (2.2 kHz)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("${dsp.centerCoherence}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = accentColor)
                                        }
                                        Slider(
                                            value = dsp.centerCoherence.toFloat(),
                                            onValueChange = { viewModel.updateDspSettings(dsp.copy(centerCoherence = (it / 5f).roundToInt() * 5)) },
                                            valueRange = 0f..100f,
                                            enabled = dsp.surroundMode != SurroundMode.OFF,
                                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Virtual Height 3D", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text("${dsp.virtualHeight}%", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = accentColor)
                                        }
                                        Slider(
                                            value = dsp.virtualHeight.toFloat(),
                                            onValueChange = { viewModel.updateDspSettings(dsp.copy(virtualHeight = (it / 5f).roundToInt() * 5)) },
                                            valueRange = 0f..100f,
                                            enabled = dsp.surroundMode != SurroundMode.OFF,
                                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                                        )
                                    }
                                }
                            }
                        }

                        // Road-Noise Acoustic Contour & System-Wide Global EQ Attach
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = surfaceColor,
                                modifier = Modifier.weight(1f).border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("ROAD-NOISE ACOUSTIC CONTOUR", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = accentColor))
                                            Text("Speed-sensitive spectral tilt (50 → 130+ km/h)", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        }
                                        Switch(
                                            checked = dsp.roadNoiseEqEnabled,
                                            onCheckedChange = { viewModel.updateDspSettings(dsp.copy(roadNoiseEqEnabled = it)) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                                        )
                                    }
                                    Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF0A0F1E), modifier = Modifier.fillMaxWidth()) {
                                        Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Live GPS: ${currentSpeedKmH.toInt()} km/h", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                                            Text("115 Hz: +${String.format(Locale.US, "%.1f", roadLowDb)} dB", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = accentColor)
                                            Text("2.8 kHz: +${String.format(Locale.US, "%.1f", roadMidDb)} dB", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = accentColor)
                                        }
                                    }
                                }
                            }

                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = surfaceColor,
                                modifier = Modifier.weight(1f).border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("SYSTEM-WIDE GLOBAL EQ (SESSION 0)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = accentColor))
                                            Text("Attaches 16-band cabin tune to Spotify, YouTube & Nav mix", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        }
                                        Switch(
                                            checked = globalEqRequested,
                                            onCheckedChange = { viewModel.setGlobalAudioSessionAttach(it) },
                                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                                        )
                                    }
                                    Text(
                                        text = when {
                                            isGlobalEqAttached -> "Active: Hardware EQ bound to Android Global Mix (Session 0)"
                                            globalEqRequested -> "Requested: Will bind Session 0 if headunit OEM firmware allows (else player session)"
                                            else -> "Player-only mode: Dedicated to Zentryx Music ExoPlayer session"
                                        },
                                        fontSize = 10.sp,
                                        color = if (isGlobalEqAttached) accentColor else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }

                    DspConsoleTab.STAGE_XOVER -> {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Left Card: Crossover Slope, HPF, Bass Shelf, Loudness, Sub LPF, Sub Delay & Phase
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = surfaceColor,
                                modifier = Modifier.weight(1f).border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("CROSSOVER SLOPE, DOOR HPF & SUB ALIGN", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Filter Slope:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilterChip(
                                                selected = dsp.crossoverSlopeDb == 12,
                                                onClick = { viewModel.updateDspSettings(dsp.copy(crossoverSlopeDb = 12)) },
                                                label = { Text("12 dB/oct BW2", fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                            )
                                            FilterChip(
                                                selected = dsp.crossoverSlopeDb == 24,
                                                onClick = { viewModel.updateDspSettings(dsp.copy(crossoverSlopeDb = 24)) },
                                                label = { Text("24 dB/oct LR4", fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        HpfCutoffHz.entries.forEach { cutoff ->
                                            FilterChip(
                                                selected = dsp.hpfCutoffHz == cutoff,
                                                onClick = { viewModel.updateDspSettings(dsp.copy(hpfCutoffHz = cutoff)) },
                                                label = { Text(cutoff.label, fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Bass Shelf (+${dsp.bassBoostDb} dB):", fontSize = 11.sp, color = Color.White, modifier = Modifier.width(126.dp))
                                        Slider(
                                            value = dsp.bassBoostDb,
                                            onValueChange = { viewModel.updateDspSettings(dsp.copy(bassBoostDb = clampStep(it, 0f, 12f))) },
                                            valueRange = 0f..12f,
                                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Fletcher-Munson Loudness", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Switch(checked = dsp.loudnessContour, onCheckedChange = { viewModel.updateDspSettings(dsp.copy(loudnessContour = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                                    }

                                    HorizontalDivider(color = Color(0xFF1E293B))

                                    Text("SUBWOOFER LPF, PHASE & TRUNK DELAY", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, color = accentColor))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        SUB_LPF_OPTIONS_HZ.forEach { lpfHz ->
                                            FilterChip(
                                                selected = dsp.subLpfHz == lpfHz,
                                                onClick = { viewModel.updateDspSettings(dsp.copy(subLpfHz = lpfHz)) },
                                                label = { Text(if (lpfHz == 0) "Off" else "$lpfHz Hz", fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Sub Trim (${if (dsp.subTrimDb >= 0) "+" else ""}${dsp.subTrimDb} dB):", fontSize = 11.sp, color = Color.White, modifier = Modifier.width(126.dp))
                                        Slider(
                                            value = dsp.subTrimDb,
                                            onValueChange = { viewModel.updateDspSettings(dsp.copy(subTrimDb = clampStep(it, -6f, 6f))) },
                                            valueRange = -6f..6f,
                                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Sub Delay (${dsp.subDelayMs} ms):", fontSize = 11.sp, color = Color.White, modifier = Modifier.width(126.dp))
                                        Slider(
                                            value = dsp.subDelayMs,
                                            onValueChange = { viewModel.updateDspSettings(dsp.copy(subDelayMs = clampStep(it, 0f, 15f, 0.5f))) },
                                            valueRange = 0f..15f,
                                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Sub Polarity:", fontSize = 11.sp, color = Color.White)
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            FilterChip(selected = !dsp.subPhaseInverted, onClick = { viewModel.updateDspSettings(dsp.copy(subPhaseInverted = false)) }, label = { Text("0° Normal", fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor))
                                            FilterChip(selected = dsp.subPhaseInverted, onClick = { viewModel.updateDspSettings(dsp.copy(subPhaseInverted = true)) }, label = { Text("180° Flipped", fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor))
                                        }
                                    }
                                }
                            }

                            // Right Card: 2D Cabin Stage, L/R Time Alignment, Stereo Width, Compressor & Limiter
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = surfaceColor,
                                modifier = Modifier.weight(1f).border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("CABIN STAGE & DYNAMICS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                                        Text("L ${dsp.leftDelayMs}ms · R ${dsp.rightDelayMs}ms", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = accentColor)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        listOf(ListeningStagePreset.ALL, ListeningStagePreset.DRIVER_LHD, ListeningStagePreset.DRIVER_RHD, ListeningStagePreset.CENTER).forEach { stage ->
                                            FilterChip(
                                                selected = dsp.listeningStage == stage,
                                                onClick = { viewModel.updateDspSettings(dsp.copy(listeningStage = stage, leftDelayMs = stage.leftDelayMs, rightDelayMs = stage.rightDelayMs)) },
                                                label = { Text(stage.label, fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                            )
                                        }
                                    }

                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        CabinStage2DPad(
                                            balance = dsp.balance,
                                            fader = dsp.fader,
                                            accentColor = accentColor,
                                            onStageChange = { newBal, newFad -> viewModel.updateDspSettings(dsp.copy(balance = newBal, fader = newFad)) },
                                            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(10.dp)).background(Color(0xFF060A14)).border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                        )
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            listOf(
                                                Triple("Driver Focus", -25, 20),
                                                Triple("Front Cabin", 0, 35),
                                                Triple("All Center", 0, 0),
                                                Triple("Rear Chill", 0, -35)
                                            ).forEach { (snapLabel, snapBal, snapFad) ->
                                                val isSnap = abs(dsp.balance - snapBal) <= 5 && abs(dsp.fader - snapFad) <= 5
                                                FilterChip(
                                                    selected = isSnap,
                                                    onClick = { viewModel.updateDspSettings(dsp.copy(balance = snapBal, fader = snapFad)) },
                                                    label = { Text(snapLabel, fontSize = 10.sp) },
                                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor),
                                                    modifier = Modifier.fillMaxWidth().height(26.dp)
                                                )
                                            }
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("Stereo Width (${dsp.stereoWidthPercent}%):", fontSize = 11.sp, color = Color.White, modifier = Modifier.width(126.dp))
                                        Slider(
                                            value = dsp.stereoWidthPercent.toFloat(),
                                            onValueChange = { viewModel.updateDspSettings(dsp.copy(stereoWidthPercent = (it / 5f).roundToInt() * 5)) },
                                            valueRange = 0f..150f,
                                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        CompressorProfile.entries.forEach { prof ->
                                            FilterChip(
                                                selected = dsp.compressorProfile == prof,
                                                onClick = { viewModel.updateDspSettings(dsp.copy(compressorProfile = prof)) },
                                                label = { Text(prof.label, fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                        LIMITER_CEILING_OPTIONS_DB.forEach { ceilingDb ->
                                            FilterChip(
                                                selected = abs(dsp.peakLimiterCeilingDb - ceilingDb) < 0.05f,
                                                onClick = { viewModel.updateDspSettings(dsp.copy(peakLimiterCeilingDb = ceilingDb)) },
                                                label = { Text("Limit $ceilingDb dB", fontSize = 10.sp) },
                                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("Mono Cabin Fold-Down", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        Switch(checked = dsp.monoFoldDown, onCheckedChange = { viewModel.updateDspSettings(dsp.copy(monoFoldDown = it)) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                                    }
                                }
                            }
                        }
                    }

                    DspConsoleTab.CABIN_RTA -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = surfaceColor,
                            modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("CABIN RTA & 5-SECOND PINK-NOISE AUTO-CALIBRATION", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = accentColor))
                                        Text(rtaStatusText, fontSize = 11.sp, color = Color(0xFFCBD5E1))
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = { viewModel.startCabinRtaSweep() },
                                            enabled = !rtaRunning,
                                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(if (rtaRunning) "Sweeping ${(rtaProgress * 100).roundToInt()}%" else "Run 5s Pink-Noise Sweep", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                        }
                                        if (rtaResult != null) {
                                            Button(
                                                onClick = { viewModel.applyCabinRtaCorrections() },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Apply Harman Curve", fontSize = 11.sp, fontWeight = FontWeight.Black)
                                            }
                                        }
                                    }
                                }

                                if (rtaRunning) {
                                    LinearProgressIndicator(
                                        progress = { rtaProgress },
                                        color = accentColor,
                                        trackColor = Color(0xFF1E293B),
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
                                    )
                                }

                                // 16-Band Live RTA Spectrum vs Harman Automotive Target Curve
                                val displayBands = rtaResult?.measuredDb16 ?: rtaLiveBandsDb
                                val corrections = rtaResult?.suggestedCorrections16
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(175.dp)
                                        .background(Color(0xFF060A14), RoundedCornerShape(10.dp))
                                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 8.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    DSP_16_LABELS.forEachIndexed { idx, label ->
                                        val measured = displayBands.getOrElse(idx) { 0f }
                                        val corr = corrections?.getOrElse(idx) { 0f }
                                        val barFrac = ((measured + 10f) / 20f).coerceIn(0.08f, 1f)
                                        Column(
                                            modifier = Modifier.weight(1f).fillMaxHeight(),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom
                                        ) {
                                            if (corr != null) {
                                                Text(
                                                    text = "${if (corr > 0f) "+" else ""}${if (corr % 1f == 0f) corr.toInt() else corr}",
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (corr > 0f) accentColor else if (corr < 0f) Color(0xFFF59E0B) else Color(0xFF64748B)
                                                )
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth()
                                                    .padding(vertical = 4.dp),
                                                contentAlignment = Alignment.BottomCenter
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(0.7f)
                                                        .fillMaxHeight(barFrac)
                                                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                        .background(
                                                            Brush.verticalGradient(
                                                                listOf(accentColor, accentColor.copy(alpha = 0.25f))
                                                            )
                                                        )
                                                )
                                            }
                                            Text(label, fontSize = 8.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF94A3B8), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EqResponseCurveCanvas(
    dsp: DspSettings,
    speedKmH: Float,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val padX = 18.dp.toPx()
        val usableW = (w - padX * 2).coerceAtLeast(1f)
        val zeroY = h * 0.52f
        val maxSpan = h * 0.40f

        listOf(-6f, 0f, 6f).forEach { dbLine ->
            val y = zeroY - (dbLine / 15f) * maxSpan
            drawLine(
                color = if (dbLine == 0f) Color(0xFF475569) else Color(0xFF1E293B),
                start = Offset(0f, y),
                end = Offset(w, y),
                strokeWidth = if (dbLine == 0f) 1.2.dp.toPx() else 0.7.dp.toPx()
            )
        }

        val pts = (0 until 16).map { idx ->
            val x = padX + (idx / 15f) * usableW
            val effDb = if (dsp.dspEnabled) effectiveBandGainDb(idx, dsp, speedKmH) else 0f
            val y = (zeroY - (effDb / 15f) * maxSpan).coerceIn(6.dp.toPx(), h - 6.dp.toPx())
            Offset(x, y)
        }

        val curvePath = Path()
        val fillPath = Path()
        pts.forEachIndexed { idx, p ->
            if (idx == 0) {
                curvePath.moveTo(p.x, p.y)
                fillPath.moveTo(p.x, zeroY)
                fillPath.lineTo(p.x, p.y)
            } else {
                val prev = pts[idx - 1]
                val midX = (prev.x + p.x) * 0.5f
                curvePath.cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
                fillPath.cubicTo(midX, prev.y, midX, p.y, p.x, p.y)
            }
        }
        fillPath.lineTo(pts.last().x, zeroY)
        fillPath.close()

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(listOf(accentColor.copy(alpha = 0.28f), accentColor.copy(alpha = 0.02f)))
        )
        drawPath(path = curvePath, color = accentColor, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        pts.forEach { p -> drawCircle(color = Color.White, radius = 2.5.dp.toPx(), center = p) }
    }
}

@Composable
private fun VerticalEqBandFader(
    freqLabel: String,
    gainDb: Float,
    accentColor: Color,
    onGainChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var boxHeightPx by remember { mutableFloatStateOf(1f) }
    val latestOnChange by rememberUpdatedState(onGainChange)

    fun mapYToGain(yPx: Float): Float {
        val norm = (1f - (yPx / boxHeightPx.coerceAtLeast(1f))).coerceIn(0f, 1f)
        return clampStep(-12f + norm * 24f, -12f, 12f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(Color(0xFF0A0F1E), RoundedCornerShape(8.dp))
            .border(0.5.dp, if (gainDb != 0f) accentColor.copy(alpha = 0.35f) else Color(0xFF1E293B), RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 2.dp)
    ) {
        Text(
            text = "${if (gainDb > 0f) "+" else ""}${if (gainDb % 1f == 0f) gainDb.toInt() else gainDb}",
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = if (gainDb != 0f) accentColor else Color(0xFF94A3B8),
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .padding(horizontal = 4.dp)
                .onSizeChanged { boxHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) { detectTapGestures { offset -> latestOnChange(mapYToGain(offset.y)) } }
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, _ ->
                        change.consume()
                        latestOnChange(mapYToGain(change.position.y))
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val trackW = 6.dp.toPx()
                val centerX = size.width / 2f
                val midY = size.height / 2f
                val thumbY = (size.height * (1f - ((gainDb + 12f) / 24f))).coerceIn(6.dp.toPx(), size.height - 6.dp.toPx())

                drawRoundRect(
                    color = Color(0xFF1E293B),
                    topLeft = Offset(centerX - trackW / 2f, 0f),
                    size = Size(trackW, size.height),
                    cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                )
                drawLine(Color(0xFF475569), Offset(centerX - 8.dp.toPx(), midY), Offset(centerX + 8.dp.toPx(), midY), strokeWidth = 1.dp.toPx())
                val topY = kotlin.math.min(midY, thumbY)
                val fillH = abs(midY - thumbY)
                if (fillH > 1f) {
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset(centerX - trackW / 2f, topY),
                        size = Size(trackW, fillH),
                        cornerRadius = CornerRadius(trackW / 2f, trackW / 2f)
                    )
                }
                drawCircle(color = accentColor, radius = 7.dp.toPx(), center = Offset(centerX, thumbY))
                drawCircle(color = Color.Black, radius = 3.dp.toPx(), center = Offset(centerX, thumbY))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(freqLabel, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun CabinStage2DPad(
    balance: Int,
    fader: Int,
    accentColor: Color,
    onStageChange: (Int, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var sizePx by remember { mutableStateOf(Size(1f, 1f)) }
    val latestOnChange by rememberUpdatedState(onStageChange)

    fun mapOffset(offset: Offset) {
        val w = sizePx.width.coerceAtLeast(1f)
        val h = sizePx.height.coerceAtLeast(1f)
        val normX = ((offset.x / w).coerceIn(0f, 1f) * 2f - 1f)
        val normY = (1f - (offset.y / h).coerceIn(0f, 1f)) * 2f - 1f
        val bal = (normX * 100f).roundToInt().coerceIn(-100, 100)
        val fad = (normY * 100f).roundToInt().coerceIn(-100, 100)
        latestOnChange(bal, fad)
    }

    Canvas(
        modifier = modifier
            .onSizeChanged { sizePx = Size(it.width.toFloat(), it.height.toFloat()) }
            .pointerInput(Unit) { detectTapGestures { pos -> mapOffset(pos) } }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    mapOffset(change.position)
                }
            }
    ) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawLine(Color(0xFF1E293B), Offset(cx, 6f), Offset(cx, size.height - 6f), strokeWidth = 1.dp.toPx())
        drawLine(Color(0xFF1E293B), Offset(6f, cy), Offset(size.width - 6f, cy), strokeWidth = 1.dp.toPx())
        drawRoundRect(
            color = Color(0xFF334155),
            topLeft = Offset(10.dp.toPx(), 10.dp.toPx()),
            size = Size(size.width - 20.dp.toPx(), size.height - 20.dp.toPx()),
            cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
            style = Stroke(width = 1.dp.toPx())
        )
        val targetX = ((balance + 100) / 200f) * size.width
        val targetY = (1f - ((fader + 100) / 200f)) * size.height
        drawCircle(color = accentColor.copy(alpha = 0.25f), radius = 16.dp.toPx(), center = Offset(targetX, targetY))
        drawCircle(color = accentColor, radius = 8.dp.toPx(), center = Offset(targetX, targetY))
        drawCircle(color = Color.Black, radius = 3.dp.toPx(), center = Offset(targetX, targetY))
    }
}

@Composable
private fun AdaptiveCabinProfileCard(
    config: AdaptiveCabinConfig,
    evalState: AdaptiveEvaluationState,
    delta: AdaptiveDspDelta,
    cabinNoiseDb: Float?,
    micStatus: CabinMicMonitorStatus,
    currentSpeedKmH: Float,
    accentColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    onUpdateConfig: (AdaptiveCabinConfig) -> Unit,
    onRequestMicPermission: () -> Unit
) {
    var showThresholds by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (config.enabled) accentColor.copy(alpha = 0.6f) else borderColor, RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                    Column {
                        Text(
                            text = "AUTO CABIN NOISE & SPEED DSP PROFILE",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = if (config.enabled) accentColor else Color.White
                            )
                        )
                        Text(
                            text = if (config.enabled) {
                                "${evalState.activeProfile.label} · Gain +${String.format(Locale.US, "%.1f", delta.gainDb)} dB · Bal ${if (delta.balanceDelta > 0) "+" else ""}${delta.balanceDelta} · Fad ${if (delta.faderDelta > 0) "+" else ""}${delta.faderDelta}"
                            } else {
                                "Off · Base manual DSP gain and cabin balance preserved"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = { enabled -> onUpdateConfig(config.copy(enabled = enabled)) },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                )
            }

            // Live Sensor Readouts (Mic dB SPL & GPS km/h)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0A0F1E),
                    modifier = Modifier.weight(1f).border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cabin Mic Noise", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Text(
                                text = when {
                                    config.source == AdaptiveCabinSource.SPEED -> "Bypassed"
                                    cabinNoiseDb != null -> "${String.format(Locale.US, "%.1f", cabinNoiseDb)} dB"
                                    else -> micStatus.label
                                },
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (config.enabled && cabinNoiseDb != null) accentColor else Color(0xFFCBD5E1)
                            )
                        }
                        val noiseFrac = if (config.enabled && cabinNoiseDb != null) {
                            ((cabinNoiseDb - 32f) / 62f).coerceIn(0.03f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { noiseFrac },
                            color = if (cabinNoiseDb != null && cabinNoiseDb >= config.noiseExtremeDb) Color(0xFFF59E0B) else accentColor,
                            trackColor = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0A0F1E),
                    modifier = Modifier.weight(1f).border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Vehicle GPS Speed", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            Text(
                                text = if (config.source == AdaptiveCabinSource.MICROPHONE) "Bypassed" else "${currentSpeedKmH.toInt()} km/h",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (config.enabled && config.source != AdaptiveCabinSource.MICROPHONE) accentColor else Color(0xFFCBD5E1)
                            )
                        }
                        val speedFrac = if (config.enabled && config.source != AdaptiveCabinSource.MICROPHONE) {
                            (currentSpeedKmH / config.speedHighKmH.coerceAtLeast(120).toFloat()).coerceIn(0f, 1f)
                        } else 0f
                        LinearProgressIndicator(
                            progress = { speedFrac },
                            color = accentColor,
                            trackColor = Color(0xFF1E293B),
                            modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }

            if (config.enabled && config.source != AdaptiveCabinSource.SPEED && micStatus == CabinMicMonitorStatus.PERMISSION_REQUIRED) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                    modifier = Modifier.fillMaxWidth().border(0.5.dp, Color(0xFFF59E0B).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Grant Microphone access for live cabin dB sensing (GPS speed still active)",
                            fontSize = 10.sp,
                            color = Color(0xFFFDE68A),
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onRequestMicPermission, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)) {
                            Text("Allow Mic", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF59E0B))
                        }
                    }
                }
            }

            // 4-Stage Profile Status Row
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdaptiveCabinProfile.entries.forEach { profile ->
                    val isActive = config.enabled && evalState.activeProfile == profile
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isActive) accentColor.copy(alpha = 0.16f) else Color(0xFF0A0F1E),
                        modifier = Modifier
                            .weight(1f)
                            .border(0.8.dp, if (isActive) accentColor else borderColor, RoundedCornerShape(8.dp))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                            Text(
                                text = profile.shortLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isActive) accentColor else Color.White,
                                maxLines = 1
                            )
                            Text(
                                text = if (profile == AdaptiveCabinProfile.QUIET) "Base DSP" else "+${profile.baseGainDb}dB · Bal ${profile.baseBalanceShift}",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFF94A3B8),
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Sensor Source, Strength & Cabin Focus Chips
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Source", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AdaptiveCabinSource.entries.forEach { src ->
                            FilterChip(
                                selected = config.source == src,
                                onClick = { onUpdateConfig(config.copy(source = src)) },
                                label = { Text(src.label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Strength", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AdaptiveCabinSensitivity.entries.forEach { sens ->
                            FilterChip(
                                selected = config.sensitivity == sens,
                                onClick = { onUpdateConfig(config.copy(sensitivity = sens)) },
                                label = { Text(sens.label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                            )
                        }
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Balance Focus", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        AdaptiveCabinFocus.entries.forEach { focus ->
                            FilterChip(
                                selected = config.cabinFocus == focus,
                                onClick = { onUpdateConfig(config.copy(cabinFocus = focus)) },
                                label = { Text(focus.label, fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                            )
                        }
                    }
                }
            }

            // Expandable Thresholds
            OutlinedButton(
                onClick = { showThresholds = !showThresholds },
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.fillMaxWidth().height(34.dp)
            ) {
                Text(
                    text = if (showThresholds) "Hide Noise & Speed Thresholds" else "Customize Noise (dB) & Speed (km/h) Thresholds",
                    fontSize = 11.sp,
                    color = Color.White
                )
            }

            if (showThresholds) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Highway Noise Trigger: ${config.noiseLoudDb} dB", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                        Slider(
                            value = config.noiseLoudDb.toFloat(),
                            onValueChange = { onUpdateConfig(config.copy(noiseLoudDb = it.roundToInt())) },
                            valueRange = 55f..80f,
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                        )
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Highway Speed Trigger: ${config.speedHighwayKmH} km/h", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color.White)
                        Slider(
                            value = config.speedHighwayKmH.toFloat(),
                            onValueChange = { onUpdateConfig(config.copy(speedHighwayKmH = (it / 5f).roundToInt() * 5)) },
                            valueRange = 55f..125f,
                            colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor)
                        )
                    }
                }
            }
        }
    }
}
