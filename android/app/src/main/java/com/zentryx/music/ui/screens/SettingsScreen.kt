package com.zentryx.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zentryx.music.dsp.FACTORY_DSP_PRESETS
import com.zentryx.music.player.CockpitThemeMode
import com.zentryx.music.player.MbuxAccentColor
import com.zentryx.music.ui.theme.MbuxAmbientBorder
import com.zentryx.music.ui.theme.MbuxObsidian
import com.zentryx.music.ui.theme.MbuxSurface
import com.zentryx.music.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDsp: () -> Unit = {},
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val currentAccent by viewModel.accentColor.collectAsStateWithLifecycle()
    val duckingEnabled by viewModel.duckingEnabled.collectAsStateWithLifecycle()
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val isPurging by viewModel.isPurgingCache.collectAsStateWithLifecycle()

    val sdvcEnabled by viewModel.sdvcEnabled.collectAsStateWithLifecycle()
    val sdvcSensitivity by viewModel.sdvcSensitivity.collectAsStateWithLifecycle()
    val storageLocation by viewModel.storageLocation.collectAsStateWithLifecycle()
    val swcLongPressAction by viewModel.swcLongPressAction.collectAsStateWithLifecycle()
    val swcDoublePressAction by viewModel.swcDoublePressAction.collectAsStateWithLifecycle()
    val ignitionFadeEnabled by viewModel.ignitionFadeEnabled.collectAsStateWithLifecycle()

    val autoResumeOnBoot by viewModel.autoResumeOnBoot.collectAsStateWithLifecycle()
    val autoResumeSoftRamp by viewModel.autoResumeSoftRamp.collectAsStateWithLifecycle()
    val swipeGesturesEnabled by viewModel.swipeGesturesEnabled.collectAsStateWithLifecycle()
    val cockpitThemeMode by viewModel.cockpitThemeMode.collectAsStateWithLifecycle()
    val nightBrightness by viewModel.nightBrightness.collectAsStateWithLifecycle()
    val amberFilterEnabled by viewModel.amberFilterEnabled.collectAsStateWithLifecycle()
    val idleScreensaverSeconds by viewModel.idleScreensaverSeconds.collectAsStateWithLifecycle()
    val dspSettings by viewModel.dspSettings.collectAsStateWithLifecycle()
    val customDspPresets by viewModel.customDspPresets.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }
    val accentColor = Color(currentAccent.hexColor)

    val activePresetName = (FACTORY_DSP_PRESETS + customPresetsOrEmpty(customDspPresets))
        .firstOrNull { it.id == dspSettings.selectedPresetId }?.name ?: "Custom Curve"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "HEADUNIT SETTINGS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack, modifier = Modifier.size(48.dp)) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MbuxObsidian),
                modifier = Modifier.height(48.dp)
            )
        },
        containerColor = MbuxObsidian,
        modifier = modifier
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: 16-Band DSP Console, Server, Accent, Cockpit Night Comfort, Offline Storage, SWC
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 0: Pro 16-Band ESP & DSP Console Launcher
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, accentColor.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
                        .clickable { onNavigateToDsp() }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = accentColor, modifier = Modifier.size(22.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("PRO 16-BAND ESP & DSP CONSOLE", fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, color = Color.White)
                                Text(
                                    text = "Active Profile: $activePresetName • ${if (dspSettings.hpfCutoffHz.hz > 0) "${dspSettings.hpfCutoffHz.hz}Hz HPF" else "Full Range"}",
                                    fontSize = 10.sp,
                                    color = accentColor
                                )
                            }
                        }
                        Button(
                            onClick = onNavigateToDsp,
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Open EQ", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Card 1: Nextcloud Server Connection & Hardware Keystore Session
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Cloud, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("NEXTCLOUD SERVER", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(serverConfig?.serverUrl ?: "https://cloud.zentryx.bg", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = accentColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Account: ${serverConfig?.username ?: "Not connected"}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.fillMaxWidth().border(0.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        ) {
                            Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("PERMANENT SESSION: HARDWARE KEYSTORE AES-256", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = accentColor)
                            }
                        }
                    }
                }

                // Card 2: MBUX Accent + Cockpit Day/Night & Dimmer
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("COCKPIT THEME & NIGHT COMFORT", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            MbuxAccentColor.entries.forEach { accent ->
                                val isSelected = currentAccent == accent
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.clickable { viewModel.setAccentColor(accent) }.padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .background(Color(accent.hexColor))
                                            .border(if (isSelected) 3.dp else 1.dp, if (isSelected) Color.White else Color.Transparent, CircleShape)
                                    )
                                    Text(accent.name, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.White else Color(0xFF94A3B8))
                                }
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            CockpitThemeMode.entries.forEach { mode ->
                                val sel = cockpitThemeMode == mode
                                FilterChip(
                                    selected = sel,
                                    onClick = { viewModel.setCockpitThemeMode(mode) },
                                    label = { Text(mode.label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Dimmer (${(nightBrightness * 100).roundToInt()}%):", fontSize = 11.sp, color = Color.White, modifier = Modifier.width(96.dp))
                            Slider(
                                value = nightBrightness,
                                onValueChange = { viewModel.setNightBrightness(it) },
                                valueRange = 0.35f..1.0f,
                                colors = SliderDefaults.colors(thumbColor = accentColor, activeTrackColor = accentColor),
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Warm W203 Amber Night Filter", fontSize = 12.sp, color = Color.White)
                            Switch(checked = amberFilterEnabled, onCheckedChange = { viewModel.setAmberFilterEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }
                    }
                }

                // Card 3: Offline Download Storage Target (eMMC / MicroSD / USB OTG)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DownloadForOffline, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OFFLINE STORAGE TARGET", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("INTERNAL" to "eMMC Flash", "SDCARD" to "MicroSD", "USB" to "USB OTG").forEach { (key, label) ->
                                val isSelected = storageLocation == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setStorageLocation(key) },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Card 4: Steering Wheel Buttons (CAN-Bus SWC)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.SportsMotorsports, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("STEERING WHEEL BUTTONS (CAN-BUS SWC)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Long Press:", fontSize = 12.sp, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("SEEK_15S" to "+15s", "NEXT_ALBUM" to "Next Album", "FAVORITE" to "Heart").forEach { (act, txt) ->
                                    FilterChip(
                                        selected = swcLongPressAction == act,
                                        onClick = { viewModel.setSwcLongPressAction(act) },
                                        label = { Text(txt, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                    )
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Double Click:", fontSize = 12.sp, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("FAVORITE" to "Favorite", "MUTE" to "Mute").forEach { (act, txt) ->
                                    FilterChip(
                                        selected = swcDoublePressAction == act,
                                        onClick = { viewModel.setSwcDoublePressAction(act) },
                                        label = { Text(txt, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right Column: Auto-Resume, Swipe/Screensaver, GPS SDVC, Engine Standby, Cache & Logout
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 5: Ignition Auto-Resume & Cockpit Gestures
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("IGNITION AUTO-RESUME & ERGONOMICS", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Resume on Boot", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Resumes last track & position automatically on startup", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(checked = autoResumeOnBoot, onCheckedChange = { viewModel.setAutoResumeOnBoot(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Soft-Start Ignition Ramp (1.4s)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Prevents sudden cabin volume blast on ignition resume", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(checked = autoResumeSoftRamp, onCheckedChange = { viewModel.setAutoResumeSoftRamp(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("No-Look Horizontal Swipe Skip", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                            Switch(checked = swipeGesturesEnabled, onCheckedChange = { viewModel.setSwipeGesturesEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }

                        Text("Idle Visualizer Screensaver:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(0 to "Off", 20 to "20s", 45 to "45s", 90 to "90s").forEach { (sec, label) ->
                                FilterChip(
                                    selected = idleScreensaverSeconds == sec,
                                    onClick = { viewModel.setIdleScreensaverSeconds(sec) },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Card 6: GALA / Speed-Dependent Volume (SDVC)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("GALA / SPEED-DEPENDENT VOLUME (SDVC)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automatic Highway Volume Boost", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Reads hardware GPS speed to neutralize cabin road noise", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(checked = sdvcEnabled, onCheckedChange = { viewModel.setSdvcEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }
                        if (sdvcEnabled) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Sensitivity:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("LOW" to "+1.5 dB", "MEDIUM" to "+3.0 dB", "HIGH" to "+5.0 dB").forEach { (sens, label) ->
                                        FilterChip(
                                            selected = sdvcSensitivity == sens,
                                            onClick = { viewModel.setSdvcSensitivity(sens) },
                                            label = { Text(label, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 7: Engine Standby & Audio Focus Ducking
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Engine Standby ACC Fade", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("800ms speaker-pop fade on ACC cut & starter crank delay", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(checked = ignitionFadeEnabled, onCheckedChange = { viewModel.setIgnitionFadeEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Navigation / System Audio Ducking", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Follows Android AudioFocus for GPS turn-by-turn prompts", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(checked = duckingEnabled, onCheckedChange = { viewModel.setDuckingEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gapless Playback (Media3)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Seamless transitions for live sets and albums", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(checked = gaplessEnabled, onCheckedChange = { viewModel.setGaplessEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }
                    }
                }

                // Card 8: Stream Cache, Rescan & Logout
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier.fillMaxWidth().border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val cacheMb = cacheSizeBytes / (1024 * 1024)
                        Text("Media3 Stream Cache: $cacheMb MB / 250 MB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.purgeStreamCache() }, enabled = !isPurging, modifier = Modifier.weight(1f).height(38.dp), shape = RoundedCornerShape(8.dp)) {
                                Text("Purge Cache", fontSize = 11.sp)
                            }
                            Button(onClick = { viewModel.triggerFullRescan() }, colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black), modifier = Modifier.weight(1f).height(38.dp), shape = RoundedCornerShape(8.dp)) {
                                Text("Rescan WebDAV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Clear Keystore Credentials", fontSize = 11.sp, color = Color(0xFF94A3B8))
                            Button(onClick = { showLogoutDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(34.dp)) {
                                Text("Log Out", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log Out from Nextcloud?", color = Color.White) },
            text = { Text("Your saved credentials and local catalog will be permanently cleared from this headunit.", color = Color(0xFF94A3B8)) },
            containerColor = MbuxSurface,
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        viewModel.logout(onLoggedOut)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Log Out") }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel", color = accentColor) }
            }
        )
    }
}

private fun customPresetsOrEmpty(list: List<com.zentryx.music.dsp.DspPreset16>): List<com.zentryx.music.dsp.DspPreset16> = list
