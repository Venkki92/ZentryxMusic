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
import com.zentryx.music.player.MbuxAccentColor
import com.zentryx.music.ui.theme.MbuxAmbientBorder
import com.zentryx.music.ui.theme.MbuxObsidian
import com.zentryx.music.ui.theme.MbuxSurface
import com.zentryx.music.ui.theme.MbuxSurfaceVariant
import com.zentryx.music.ui.theme.ZentryxLime
import com.zentryx.music.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onLoggedOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val serverConfig by viewModel.serverConfig.collectAsStateWithLifecycle()
    val currentAccent by viewModel.accentColor.collectAsStateWithLifecycle()
    val duckingEnabled by viewModel.duckingEnabled.collectAsStateWithLifecycle()
    val gaplessEnabled by viewModel.gaplessEnabled.collectAsStateWithLifecycle()
    val bufferMode by viewModel.bufferMode.collectAsStateWithLifecycle()
    val cacheSizeBytes by viewModel.cacheSizeBytes.collectAsStateWithLifecycle()
    val isPurging by viewModel.isPurgingCache.collectAsStateWithLifecycle()

    // Features 2, 3, 4, 7 States
    val sdvcEnabled by viewModel.sdvcEnabled.collectAsStateWithLifecycle()
    val sdvcSensitivity by viewModel.sdvcSensitivity.collectAsStateWithLifecycle()
    val storageLocation by viewModel.storageLocation.collectAsStateWithLifecycle()
    val swcLongPressAction by viewModel.swcLongPressAction.collectAsStateWithLifecycle()
    val swcDoublePressAction by viewModel.swcDoublePressAction.collectAsStateWithLifecycle()
    val ignitionFadeEnabled by viewModel.ignitionFadeEnabled.collectAsStateWithLifecycle()

    var showLogoutDialog by remember { mutableStateOf(false) }

    val accentColor = Color(currentAccent.hexColor)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SETTINGS",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.5.sp,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MbuxObsidian
                ),
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
            // Left Column (50%): Server, MBUX Accent, Offline Storage, Steering Wheel
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Nextcloud Server Connection & Permanent Session
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
                            Text(
                                text = "NEXTCLOUD SERVER",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = serverConfig?.serverUrl ?: "https://cloud.zentryx.bg",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = accentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "Account: ${serverConfig?.username ?: "Not connected"}",
                            style = MaterialTheme.typography.bodySmall.copy(color = Color(0xFF94A3B8))
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        // Permanent Session Status Banner
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(0.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(accentColor))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "PERMANENT SESSION: HARDWARE KEYSTORE AES-256",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = accentColor
                                )
                            }
                        }
                    }
                }

                // Card 2: MBUX Ambient Accent Color
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Palette, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "MBUX AMBIENT ACCENT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            MbuxAccentColor.values().forEach { accent ->
                                val isSelected = currentAccent == accent
                                val col = Color(accent.hexColor)
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .clickable { viewModel.setAccentColor(accent) }
                                        .padding(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(col)
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) Color.White else Color.Transparent,
                                                shape = CircleShape
                                            )
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = accent.name,
                                        fontSize = 9.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }
                    }
                }

                // Card 3: Feature 3 - Offline Storage Target
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DownloadForOffline, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "OFFLINE DOWNLOAD STORAGE TARGET",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("INTERNAL" to "eMMC Flash", "SDCARD" to "MicroSD Slot", "USB" to "USB OTG").forEach { (key, label) ->
                                val isSelected = storageLocation == key
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.setStorageLocation(key) },
                                    label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                        selectedLabelColor = accentColor
                                    ),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Card 4: Feature 4 - Steering Wheel Controls (CAN-Bus SWC)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.SportsMotorsports, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "STEERING WHEEL BUTTONS (CAN-BUS SWC)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Long Press Action:", fontSize = 12.sp, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("SEEK_15S" to "+15s", "NEXT_ALBUM" to "Next Album", "FAVORITE" to "Heart").forEach { (act, txt) ->
                                    val isSelected = swcLongPressAction == act
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setSwcLongPressAction(act) },
                                        label = { Text(txt, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                            selectedLabelColor = accentColor
                                        )
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Double Click Action:", fontSize = 12.sp, color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("FAVORITE" to "Toggle Favorite", "MUTE" to "Mute").forEach { (act, txt) ->
                                    val isSelected = swcDoublePressAction == act
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.setSwcDoublePressAction(act) },
                                        label = { Text(txt, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                            selectedLabelColor = accentColor
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Right Column (50%): SDVC GALA, Ignition Standby, Radar Ducking, Cache & Logout
            Column(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 5: Feature 2 - Speed-Dependent Volume Control (GALA / SDVC)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Speed, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GALA / SPEED-DEPENDENT VOLUME (SDVC)",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Automatic Highway Volume Boost", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Compensates for OM646 diesel rumble & road noise above 50 km/h", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(
                                checked = sdvcEnabled,
                                onCheckedChange = { viewModel.setSdvcEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                            )
                        }

                        if (sdvcEnabled) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GALA Sensitivity:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf("LOW" to "+1.5 dB", "MEDIUM" to "+3.0 dB", "HIGH" to "+5.0 dB").forEach { (sens, label) ->
                                        val isSelected = sdvcSensitivity == sens
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.setSdvcSensitivity(sens) },
                                            label = { Text(label, fontSize = 10.sp) },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = accentColor.copy(alpha = 0.2f),
                                                selectedLabelColor = accentColor
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Card 6: Feature 7 - Engine Standby & Voltage Protection
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.PowerSettingsNew, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "ENGINE STANDBY & FADE TRANSITION",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    color = Color.White
                                )
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Smooth Ignition Fade Out & In", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Prevents speaker pops on ACC cut and waits for starter crank voltage stabilization", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(
                                checked = ignitionFadeEnabled,
                                onCheckedChange = { viewModel.setIgnitionFadeEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                            )
                        }
                    }
                }

                // Card 7: Audio Focus Ducking & Gapless
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("PDC Radar / GPS Audio Ducking", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Ducks volume during reverse parking radar & navigation prompts", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(
                                checked = duckingEnabled,
                                onCheckedChange = { viewModel.setDuckingEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                            )
                        }

                        HorizontalDivider(color = Color(0xFF1E293B), thickness = 0.5.dp)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Gapless Playback (ExoPlayer)", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text("Seamless track cross-transitions for live albums and classical suites", fontSize = 10.sp, color = Color(0xFF94A3B8))
                            }
                            Switch(
                                checked = gaplessEnabled,
                                onCheckedChange = { viewModel.setGaplessEnabled(it) },
                                colors = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = accentColor)
                            )
                        }
                    }
                }

                // Card 8: Stream Cache & Rescan
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MbuxAmbientBorder, RoundedCornerShape(12.dp))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val cacheMb = cacheSizeBytes / (1024 * 1024)
                        Text(
                            text = "Stream Cache: $cacheMb MB / 250 MB Allocated",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.purgeStreamCache() },
                                enabled = !isPurging,
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Purge Cache", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { viewModel.triggerFullRescan() },
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Rescan WebDAV", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Card 9: Logout
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MbuxSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF331D24), RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Account Session", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                            Text("Clear credentials from hardware KeyStore", fontSize = 10.sp, color = Color(0xFF94A3B8))
                        }
                        Button(
                            onClick = { showLogoutDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("Log Out", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                ) {
                    Text("Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("Cancel", color = accentColor)
                }
            }
        )
    }
}
