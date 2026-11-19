package com.zentryx.music.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zentryx.music.BuildConfig
import com.zentryx.music.dsp.FACTORY_DSP_PRESETS
import com.zentryx.music.models.ScanStatus
import com.zentryx.music.player.CockpitThemeMode
import com.zentryx.music.player.GpsTelemetryStatus
import com.zentryx.music.player.MbuxAccentColor
import com.zentryx.music.player.RoadtripPool
import com.zentryx.music.swc.SwcAction
import com.zentryx.music.swc.SwcPressType
import com.zentryx.music.viewmodel.SettingsViewModel
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToDsp: () -> Unit = {},
    onLoggedOut: () -> Unit,
    onNavigateForward: () -> Unit = {},
    canGoBack: Boolean = true,
    canGoForward: Boolean = false,
    modifier: Modifier = Modifier
) {
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val currentAccent by viewModel.accentColor.collectAsStateWithLifecycle()
    val duckingEnabled by viewModel.duckingEnabled.collectAsStateWithLifecycle()
    val bufferMode by viewModel.bufferMode.collectAsStateWithLifecycle()
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsStateWithLifecycle()
    val replayGainEnabled by viewModel.replayGainEnabled.collectAsStateWithLifecycle()
    val crossfadeSeconds by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val isPurging by viewModel.isPurgingCache.collectAsStateWithLifecycle()
    val scanProgress by viewModel.scanProgress.collectAsStateWithLifecycle()
    val isLocalScanning by viewModel.isLocalScanning.collectAsStateWithLifecycle()
    val backupStatusMessage by viewModel.backupStatusMessage.collectAsStateWithLifecycle()

    val sdvcEnabled by viewModel.sdvcEnabled.collectAsStateWithLifecycle()
    val sdvcSensitivity by viewModel.sdvcSensitivity.collectAsStateWithLifecycle()
    val currentSpeedKmH by viewModel.currentSpeedKmH.collectAsStateWithLifecycle()
    val currentBoostDb by viewModel.currentBoostDb.collectAsStateWithLifecycle()
    val gpsStatus by viewModel.gpsStatus.collectAsStateWithLifecycle()

    val storageLocation by viewModel.storageLocation.collectAsStateWithLifecycle()
    val customDownloadUri by viewModel.customDownloadUri.collectAsStateWithLifecycle()
    val customDownloadLabel by viewModel.customDownloadLabel.collectAsStateWithLifecycle()
    val localSourceLabel by viewModel.localSourceLabel.collectAsStateWithLifecycle()
    val storageSummary = remember(storageLocation, customDownloadLabel, customDownloadUri) {
        viewModel.getStorageSummary(storageLocation, customDownloadLabel, customDownloadUri)
    }
    val context = LocalContext.current
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(treeUri, flags)
            } catch (_: SecurityException) {
            }
            val label = try {
                DocumentFile.fromTreeUri(context, treeUri)?.name ?: treeUri.lastPathSegment ?: "Selected Folder"
            } catch (_: Throwable) {
                treeUri.lastPathSegment ?: "Selected Folder"
            }
            viewModel.setCustomDownloadFolder(treeUri.toString(), label)
            viewModel.setStorageLocation("CUSTOM")
        }
    }
    val localMusicFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
            }
            val label = try {
                DocumentFile.fromTreeUri(context, treeUri)?.name ?: treeUri.lastPathSegment ?: "USB Audio"
            } catch (_: Throwable) {
                treeUri.lastPathSegment ?: "USB Audio"
            }
            viewModel.setLocalSourceFolder(treeUri, label)
        }
    }
    val backupExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { targetUri ->
        if (targetUri != null) {
            viewModel.exportSettingsToUri(targetUri)
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { sourceUri ->
        if (sourceUri != null) {
            viewModel.importSettingsFromUri(sourceUri)
        }
    }
    val swcLongPressAction by viewModel.swcLongPressAction.collectAsStateWithLifecycle()
    val swcDoublePressAction by viewModel.swcDoublePressAction.collectAsStateWithLifecycle()
    val swcCustomKeyMap by viewModel.swcCustomKeyMap.collectAsStateWithLifecycle()
    val swcLearningTarget by viewModel.swcLearningTarget.collectAsStateWithLifecycle()
    val swcLastCapturedKeyLabel by viewModel.swcLastCapturedKeyLabel.collectAsStateWithLifecycle()
    val ignitionFadeEnabled by viewModel.ignitionFadeEnabled.collectAsStateWithLifecycle()

    val autoResumeOnBoot by viewModel.autoResumeOnBoot.collectAsStateWithLifecycle()
    val autoResumeSoftRamp by viewModel.autoResumeSoftRamp.collectAsStateWithLifecycle()
    val autoPlayOnBluetooth by viewModel.autoPlayOnBluetooth.collectAsStateWithLifecycle()
    val duckOnReverseGear by viewModel.duckOnReverseGear.collectAsStateWithLifecycle()
    val globalAudioSessionAttach by viewModel.globalAudioSessionAttach.collectAsStateWithLifecycle()
    val isGlobalEqAttached by viewModel.isGlobalEqAttached.collectAsStateWithLifecycle()
    val floatingOverlayEnabled by viewModel.floatingOverlayEnabled.collectAsStateWithLifecycle()
    val driverProfiles by viewModel.driverProfiles.collectAsStateWithLifecycle()
    val activeDriverProfileId by viewModel.activeDriverProfileId.collectAsStateWithLifecycle()
    val roadtripHours by viewModel.roadtripHours.collectAsStateWithLifecycle()
    val roadtripPool by viewModel.roadtripPool.collectAsStateWithLifecycle()
    val roadtripPlan by viewModel.roadtripPlan.collectAsStateWithLifecycle()
    val batchDownloadProgress by viewModel.batchDownloadProgress.collectAsStateWithLifecycle()
    val mountedUsbVolumes by viewModel.mountedUsbVolumes.collectAsStateWithLifecycle()
    val usbEventMessage by viewModel.usbEventMessage.collectAsStateWithLifecycle()

    val swipeGesturesEnabled by viewModel.swipeGesturesEnabled.collectAsStateWithLifecycle()
    val cockpitThemeMode by viewModel.cockpitThemeMode.collectAsStateWithLifecycle()
    val nightBrightness by viewModel.nightBrightness.collectAsStateWithLifecycle()
    val amberFilterEnabled by viewModel.amberFilterEnabled.collectAsStateWithLifecycle()
    val idleScreensaverSeconds by viewModel.idleScreensaverSeconds.collectAsStateWithLifecycle()
    val dualPaneCockpit by viewModel.dualPaneCockpit.collectAsStateWithLifecycle()
    val driveModeAutoSpeedKmH by viewModel.driveModeAutoSpeedKmH.collectAsStateWithLifecycle()
    val dspSettings by viewModel.dspSettings.collectAsStateWithLifecycle()
    val customDspPresets by viewModel.customDspPresets.collectAsStateWithLifecycle()
    val activeOutputRoute by viewModel.activeOutputRoute.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var swcLearnPressType by remember { mutableStateOf(SwcPressType.SHORT) }
    val accentColor = Color(currentAccent.hexColor)
    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val borderColor = MaterialTheme.colorScheme.outline

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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onNavigateBack,
                            enabled = canGoBack,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = if (canGoBack) Color.White else Color(0xFF475569)
                            )
                        }
                        IconButton(
                            onClick = onNavigateForward,
                            enabled = canGoForward,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Forward",
                                tint = if (canGoForward) Color.White else Color(0xFF475569)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgColor),
                modifier = Modifier.height(48.dp)
            )
        },
        containerColor = bgColor,
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
                // Card 0A: Multi-Profile Driver Cockpit Slots
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("DRIVER COCKPIT PROFILES", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                            }
                            OutlinedButton(
                                onClick = { viewModel.saveCurrentTuneToActiveDriverProfile() },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Save Current Tune", fontSize = 10.sp, color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            driverProfiles.forEach { profile ->
                                val selected = profile.id == activeDriverProfileId
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (selected) accentColor.copy(alpha = 0.16f) else Color(0xFF0A0F1E),
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, if (selected) accentColor else borderColor, RoundedCornerShape(10.dp))
                                        .clickable { viewModel.selectDriverProfile(profile.id) }
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(profile.name, fontSize = 11.sp, fontWeight = FontWeight.Black, color = if (selected) accentColor else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(profile.surroundMode.badge, fontSize = 9.sp, color = Color(0xFF94A3B8), maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 0: Pro 16-Band ESP & DSP Console Launcher
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
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
                                Text("PRO DSP, LOGIC 7 & CABIN RTA CONSOLE", fontWeight = FontWeight.Black, fontSize = 11.sp, letterSpacing = 1.sp, color = Color.White)
                                Text(
                                    text = "$activePresetName • ${dspSettings.surroundMode.badge} • ${dspSettings.crossoverSlopeDb}dB/oct",
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
                            Text("Open DSP", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Card 1: Nextcloud Server Connection & Hardware Keystore Session
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, borderColor, RoundedCornerShape(12.dp))
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
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
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
                                val shortTag = when (accent) {
                                    MbuxAccentColor.LIME -> "Lime"
                                    MbuxAccentColor.AMBER -> "Amber"
                                    MbuxAccentColor.CYAN -> "Cyan"
                                    MbuxAccentColor.SILVER -> "Silver"
                                    MbuxAccentColor.RED -> "AMG Red"
                                }
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
                                    Text(shortTag, fontSize = 9.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.White else Color(0xFF94A3B8))
                                }
                            }
                        }
                        Text(
                            text = "Active Theme: ${currentAccent.displayName}",
                            fontSize = 10.sp,
                            color = accentColor,
                            fontWeight = FontWeight.SemiBold
                        )

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

                // Card 3: Offline Download Storage Target (eMMC / MicroSD / USB OTG / Custom SAF Folder)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.DownloadForOffline, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("OFFLINE STORAGE TARGET", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                            }
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier.height(30.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = accentColor, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select Folder", fontSize = 10.sp, color = accentColor, fontWeight = FontWeight.Bold)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("INTERNAL" to "eMMC Flash", "SDCARD" to "MicroSD", "USB" to "USB OTG", "CUSTOM" to "Custom").forEach { (key, label) ->
                                val isSelected = storageLocation == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        if (key == "CUSTOM" && customDownloadUri.isNullOrBlank()) {
                                            folderPickerLauncher.launch(null)
                                        } else {
                                            viewModel.setStorageLocation(key)
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text(
                            text = storageSummary,
                            fontSize = 10.sp,
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Card 3B: Offline Roadtrip Budget Planner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Map, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("OFFLINE ROADTRIP BUDGET PLANNER", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3, 5, 8).forEach { hrs ->
                                FilterChip(
                                    selected = roadtripHours == hrs,
                                    onClick = { viewModel.setRoadtripHours(hrs) },
                                    label = { Text("${hrs}h", fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            RoadtripPool.entries.forEach { pool ->
                                FilterChip(
                                    selected = roadtripPool == pool,
                                    onClick = { viewModel.setRoadtripPool(pool) },
                                    label = { Text(pool.label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                )
                            }
                        }
                        Surface(shape = RoundedCornerShape(8.dp), color = Color(0xFF0A0F1E), modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "${roadtripPlan.selectedSongs.size} tracks (${roadtripPlan.totalMinutes} min) · ${roadtripPlan.alreadyOfflineCount} already cached",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = if (roadtripPlan.missingOfflineSongs.isEmpty()) "Ready for offline roadtrip!" else "${roadtripPlan.missingOfflineSongs.size} missing tracks (~${roadtripPlan.estimatedMissingMb} MB)",
                                    fontSize = 10.sp,
                                    color = accentColor
                                )
                            }
                        }
                        Button(
                            onClick = { viewModel.pinRoadtripOffline() },
                            enabled = roadtripPlan.missingOfflineSongs.isNotEmpty() && batchDownloadProgress == null,
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(36.dp)
                        ) {
                            val label = batchDownloadProgress?.let { (done, total) -> "Pinning $done / $total tracks…" }
                                ?: "Pin ${roadtripPlan.missingOfflineSongs.size} Roadtrip Tracks Offline"
                            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Black)
                        }
                    }
                }

                // Card 4: Steering Wheel Buttons (CAN-Bus SWC) + Short/Long Interactive Key Learner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.SportsMotorsports, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("CAN-BUS SWC & KEY REMAPPER", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                            }
                            if (swcCustomKeyMap.shortPressMap.isNotEmpty() || swcCustomKeyMap.longPressMap.isNotEmpty()) {
                                TextButton(onClick = { viewModel.clearSwcCustomKeyMap() }, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                                    Text("Reset", fontSize = 10.sp, color = Color(0xFFEF4444))
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Default Hold:", fontSize = 11.sp, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("SEEK_15S" to "+15s", "NEXT_ALBUM" to "Next Album", "CYCLE_SURROUND" to "Logic 7", "TALK_DUCK" to "Duck").forEach { (act, txt) ->
                                    FilterChip(
                                        selected = swcLongPressAction == act,
                                        onClick = { viewModel.setSwcLongPressAction(act) },
                                        label = { Text(txt, fontSize = 9.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                    )
                                }
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Double Click:", fontSize = 11.sp, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("FAVORITE" to "Favorite", "CRUISING_MIX" to "Mix", "MUTE" to "Mute").forEach { (act, txt) ->
                                    FilterChip(
                                        selected = swcDoublePressAction == act,
                                        onClick = { viewModel.setSwcDoublePressAction(act) },
                                        label = { Text(txt, fontSize = 9.sp) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Learn Custom Hardware Key:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF94A3B8))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                FilterChip(
                                    selected = swcLearnPressType == SwcPressType.SHORT,
                                    onClick = { swcLearnPressType = SwcPressType.SHORT },
                                    label = { Text("Short Press", fontSize = 9.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                )
                                FilterChip(
                                    selected = swcLearnPressType == SwcPressType.LONG,
                                    onClick = { swcLearnPressType = SwcPressType.LONG },
                                    label = { Text("Long Press", fontSize = 9.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor)
                                )
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(
                                SwcAction.CRUISING_MIX,
                                SwcAction.TALK_DUCK,
                                SwcAction.CYCLE_SURROUND,
                                SwcAction.CYCLE_EQ_PRESET,
                                SwcAction.TOGGLE_FAVORITE,
                                SwcAction.NEXT_ALBUM
                            ).forEach { act ->
                                val isLearningThis = swcLearningTarget?.action == act && swcLearningTarget?.pressType == swcLearnPressType
                                FilterChip(
                                    selected = isLearningThis,
                                    onClick = {
                                        if (isLearningThis) viewModel.cancelSwcLearning()
                                        else viewModel.startSwcLearning(act, swcLearnPressType)
                                    },
                                    label = { Text(if (isLearningThis) "Press Key…" else act.displayName, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFF59E0B).copy(alpha = 0.25f), selectedLabelColor = Color(0xFFF59E0B))
                                )
                            }
                        }

                        if (!swcLastCapturedKeyLabel.isNullOrBlank()) {
                            Text(swcLastCapturedKeyLabel!!, fontSize = 10.sp, color = accentColor)
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
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Bluetooth A2DP Auto-Play Trigger", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Resumes playback automatically when BT phone/headunit links", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(checked = autoPlayOnBluetooth, onCheckedChange = { viewModel.setAutoPlayOnBluetooth(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Reverse-Gear Parking Sensor Duck (-20 dB)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Ducks cabin music on Topway/Microntek/CAN reverse signal", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(checked = duckOnReverseGear, onCheckedChange = { viewModel.setDuckOnReverseGear(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Floating PiP Audio Bar Over Maps / Waze", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Draggable translucent bar using Android System Overlay", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(
                                checked = floatingOverlayEnabled,
                                onCheckedChange = { wantEnable ->
                                    val ok = viewModel.setFloatingOverlayEnabled(wantEnable)
                                    if (!ok && wantEnable) {
                                        runCatching {
                                            val permIntent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            context.startActivity(permIntent)
                                        }
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                            )
                        }

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("System-Wide Global EQ (Session 0)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text(
                                    text = if (isGlobalEqAttached) "Active globally across Spotify, YouTube & Nav" else "Applies cabin tune to all Android audio apps",
                                    fontSize = 10.sp,
                                    color = if (isGlobalEqAttached) accentColor else Color(0xFF94A3B8)
                                )
                            }
                            Switch(checked = globalAudioSessionAttach, onCheckedChange = { viewModel.setGlobalAudioSessionAttach(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
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

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Dual-Pane Widescreen Cockpit", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Pins live Now Playing deck beside Library on wide displays", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(checked = dualPaneCockpit, onCheckedChange = { viewModel.setDualPaneCockpit(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }

                        Text("Drive Mode Auto-Engage Speed (GPS):", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(0 to "Manual", 15 to "15 km/h", 30 to "30 km/h").forEach { (kmh, label) ->
                                FilterChip(
                                    selected = driveModeAutoSpeedKmH == kmh,
                                    onClick = { viewModel.setDriveModeAutoSpeedKmH(kmh) },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Card 6: GALA / Speed-Dependent Volume (SDVC) + Live GPS Telemetry
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("GALA / SPEED-DEPENDENT VOLUME (SDVC)", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                            }
                            Text(
                                text = "${currentSpeedKmH.toInt()} km/h · +${String.format(Locale.US, "%.1f", currentBoostDb)} dB",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automatic Highway Volume Boost", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("GNSS Status: ${gpsStatus.label}", fontSize = 10.sp, color = if (gpsStatus == GpsTelemetryStatus.ACTIVE) accentColor else Color(0xFF94A3B8))
                            }
                            Switch(checked = sdvcEnabled, onCheckedChange = { viewModel.setSdvcEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }
                        if (sdvcEnabled) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                OutlinedButton(
                                    onClick = { viewModel.pollGps() },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    modifier = Modifier.height(30.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Poll Live GPS", fontSize = 10.sp, color = Color.White)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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

                // Card 7: Engine Standby, Audio Focus Ducking & Tunnel Guard Buffer
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
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
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Tunnel Guard Lookahead Buffer", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Pre-caches 4 MiB of upcoming 2 queue tracks on 4G/LTE", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(
                                checked = bufferMode == "TUNNEL_GUARD",
                                onCheckedChange = { checked -> viewModel.setBufferMode(if (checked) "TUNNEL_GUARD" else "RAM_SAVER") },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                            )
                        }
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("ReplayGain Loudness Normalization", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Balances quiet acoustic tracks & loud masters via DSP", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(checked = replayGainEnabled, onCheckedChange = { viewModel.setReplayGainEnabled(it) }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor))
                        }
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        Text("DJ Crossfade Transition:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(0 to "Off", 2 to "2s", 4 to "4s", 6 to "6s", 8 to "8s").forEach { (sec, label) ->
                                FilterChip(
                                    selected = crossfadeSeconds == sec,
                                    onClick = { viewModel.setCrossfadeSeconds(sec) },
                                    label = { Text(label, fontSize = 10.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = accentColor.copy(alpha = 0.2f), selectedLabelColor = accentColor),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Card 8: Local USB / Device Audio, Portable DSP Backup, Stream Cache & Logout
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = surfaceColor,
                    modifier = Modifier.fillMaxWidth().border(1.dp, borderColor, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("LOCAL USB OTG / MICROSD AUTO-WATCHER", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                        Text(
                            text = if (mountedUsbVolumes.isNotEmpty()) {
                                "Detected Drives: ${mountedUsbVolumes.joinToString(" · ")}"
                            } else {
                                localSourceLabel?.let { "USB / SAF Folder: $it (+ Device MediaStore)" } ?: "Auto-watches inserted USB OTG & MicroSD drives"
                            },
                            fontSize = 10.sp,
                            color = accentColor,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (!usbEventMessage.isNullOrBlank()) {
                            Text(usbEventMessage!!, fontSize = 10.sp, color = Color(0xFF94A3B8), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { localMusicFolderLauncher.launch(null) },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Pick USB Folder", fontSize = 11.sp, color = Color.White)
                            }
                            Button(
                                onClick = { viewModel.scanLocalAudio() },
                                enabled = !isLocalScanning,
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (isLocalScanning) "Scanning…" else "Scan USB / SD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                        Text("PORTABLE DSP & SETTINGS BACKUP", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = Color.White))
                        if (!backupStatusMessage.isNullOrBlank()) {
                            Text(
                                text = backupStatusMessage!!,
                                fontSize = 10.sp,
                                color = accentColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { backupExportLauncher.launch("zentryx-tune.json") },
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Export .json", fontSize = 10.sp, color = Color.White)
                            }
                            OutlinedButton(
                                onClick = { backupImportLauncher.launch(arrayOf("application/json", "*/*")) },
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Import .json", fontSize = 10.sp, color = Color.White)
                            }
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.pushBackupToNextcloud() },
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cloud Save", fontSize = 10.sp, color = accentColor)
                            }
                            OutlinedButton(
                                onClick = { viewModel.pullBackupFromNextcloud() },
                                modifier = Modifier.weight(1f).height(34.dp),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cloud Restore", fontSize = 10.sp, color = accentColor)
                            }
                        }

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                        val cacheMb = cacheSizeBytes / (1024 * 1024)
                        Text("Media3 Stream Cache: $cacheMb MB / 250 MB", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        if (scanProgress.status != ScanStatus.IDLE) {
                            val scanNote = when (scanProgress.status) {
                                ScanStatus.SCANNING -> "Scanning: ${scanProgress.currentFolder} (${scanProgress.filesProcessed} tracks)"
                                ScanStatus.COMPLETED -> "Scan complete: ${scanProgress.filesProcessed} tracks indexed"
                                ScanStatus.ERROR -> "Scan failed: ${scanProgress.errorMessage ?: "WebDAV error"}"
                                else -> scanProgress.status.name
                            }
                            Text(
                                text = scanNote,
                                fontSize = 10.sp,
                                color = if (scanProgress.status == ScanStatus.ERROR) MaterialTheme.colorScheme.error else accentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { viewModel.purgeStreamCache() }, enabled = !isPurging, modifier = Modifier.weight(1f).height(38.dp), shape = RoundedCornerShape(8.dp)) {
                                Text(if (isPurging) "Purging…" else "Purge Cache", fontSize = 11.sp)
                            }
                            Button(
                                onClick = { viewModel.triggerFullRescan() },
                                enabled = !scanProgress.isScanning,
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(if (scanProgress.isScanning) "Scanning…" else "Rescan WebDAV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Clear Keystore Credentials", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Text("Zentryx Music v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", fontSize = 10.sp, color = Color(0xFF64748B))
                            }
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
            containerColor = surfaceColor,
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
