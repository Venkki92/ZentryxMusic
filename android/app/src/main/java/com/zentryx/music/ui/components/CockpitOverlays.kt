package com.zentryx.music.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.zentryx.music.dsp.AudioOutputRoute
import com.zentryx.music.dsp.DspSettings
import com.zentryx.music.dsp.FACTORY_DSP_PRESETS
import com.zentryx.music.models.PlaybackState
import com.zentryx.music.models.Song
import java.io.File
import java.util.Locale

/**
 * Reusable Android speech recognizer launcher for hands-free in-car track & artist voice commands.
 * Gracefully invokes [onUnavailable] on stripped headunit ROMs without Google Voice Search installed.
 */
@Composable
fun rememberVoiceSearchLauncher(
    onResult: (String) -> Unit,
    onUnavailable: () -> Unit = {}
): () -> Unit {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { res ->
        if (res.resultCode == Activity.RESULT_OK) {
            val matches = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val best = matches?.firstOrNull { !it.isNullOrBlank() }?.trim()
            if (!best.isNullOrBlank()) {
                onResult(best)
            }
        }
    }

    return {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Say artist, song title, or 'Play ...'")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try {
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            onUnavailable()
        } catch (_: Throwable) {
            onUnavailable()
        }
    }
}

/**
 * Simplified oversized-target Drive Mode HUD overlay for low-distraction eyes-free headunit operation.
 */
@Composable
fun DriveModeHudDialog(
    playbackState: PlaybackState,
    speedKmH: Float,
    isAttenuated: Boolean,
    accentColor: Color,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekBack15: () -> Unit,
    onSeekForward15: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleAttenuation: () -> Unit,
    onVoiceSearch: () -> Unit,
    onDismiss: () -> Unit
) {
    val song = playbackState.currentSong
    val progress = if (playbackState.durationMs > 0) {
        (playbackState.currentPositionMs.toFloat() / playbackState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Color(0xFF04070E),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Telemetry & Exit Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = accentColor.copy(alpha = 0.16f),
                            modifier = Modifier.border(1.dp, accentColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(imageVector = Icons.Default.DirectionsCar, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                                Text("DRIVE HUD", color = accentColor, fontWeight = FontWeight.Black, fontSize = 12.sp, letterSpacing = 1.4.sp)
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0F172A),
                            modifier = Modifier.border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                text = "${speedKmH.toInt()} km/h",
                                color = Color.White,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onVoiceSearch,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF111C33), contentColor = accentColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Mic, contentDescription = "Voice Command", modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Voice", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.height(46.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Exit Drive Mode", tint = Color.White, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exit HUD", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                // Center Track Info + Progress Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(118.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF0F172A))
                            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (song?.artworkPath != null && File(song.artworkPath).exists()) {
                            AsyncImage(
                                model = File(song.artworkPath),
                                contentDescription = song.album,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(imageVector = Icons.Default.MusicNote, contentDescription = null, tint = accentColor, modifier = Modifier.size(48.dp))
                        }
                    }

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = song?.title ?: "Ready to Play",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 26.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (song != null) "${song.artist} • ${song.album}" else "Select a track or press Play",
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            color = accentColor,
                            trackColor = Color(0xFF1E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                text = formatClockMs(playbackState.currentPositionMs),
                                color = accentColor,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = formatClockMs(playbackState.durationMs),
                                color = Color(0xFF64748B),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                // Oversized Eyes-Free Transport Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onSeekBack15,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(68.dp).width(96.dp)
                    ) {
                        Text("-15s", fontSize = 17.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }

                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(40.dp))
                    }

                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = Color.Black,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF1E293B))
                            .border(1.dp, Color(0xFF334155), CircleShape)
                    ) {
                        Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(40.dp))
                    }

                    Button(
                        onClick = onSeekForward15,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A), contentColor = Color.White),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(68.dp).width(96.dp)
                    ) {
                        Text("+15s", fontSize = 17.sp, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                    }
                }

                // Bottom Wide Cabin Shortcuts
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onToggleFavorite,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (song?.isFavorite == true) accentColor.copy(alpha = 0.22f) else Color(0xFF0F172A),
                            contentColor = if (song?.isFavorite == true) accentColor else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(
                            imageVector = if (song?.isFavorite == true) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (song?.isFavorite == true) "Favorited" else "Favorite Track", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Button(
                        onClick = onToggleAttenuation,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAttenuated) Color(0xFFF59E0B).copy(alpha = 0.25f) else Color(0xFF0F172A),
                            contentColor = if (isAttenuated) Color(0xFFF59E0B) else Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(52.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VolumeDown, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isAttenuated) "-20dB Talk Duck ACTIVE" else "Talk Duck (-20 dB)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

/**
 * Tap-to-inspect technical Signal Path, Codec & Active DSP telemetry modal.
 */
@Composable
fun StreamCodecInspectorDialog(
    song: Song,
    dspSettings: DspSettings,
    activeOutputRoute: AudioOutputRoute,
    replayGainAppliedDb: Float?,
    accentColor: Color,
    onDismiss: () -> Unit
) {
    val activePresetName = FACTORY_DSP_PRESETS.firstOrNull { it.id == dspSettings.selectedPresetId }?.name
        ?: dspSettings.selectedPresetId
    val sizeText = when {
        song.fileSize >= 1024L * 1024L -> String.format(Locale.US, "%.2f MB", song.fileSize / (1024f * 1024f))
        song.fileSize >= 1024L -> "${song.fileSize / 1024L} KB"
        song.fileSize > 0L -> "${song.fileSize} B"
        else -> "Unknown"
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF0B1120),
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .heightIn(max = 520.dp)
                .border(1.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = accentColor)
                        Column {
                            Text("AUDIO STREAM & DSP INSPECTOR", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp, letterSpacing = 0.8.sp)
                            Text(song.title, color = accentColor, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                HorizontalDivider(color = Color(0xFF1E293B))

                InspectorRow("Codec / Container", "${song.format.name} (${song.mimeType.ifBlank { "audio/*" }})" + if (song.isHighRes) " • HI-RES" else "", accentColor)
                InspectorRow("Sample Rate / Depth", "${song.sampleRateHz?.let { "$it Hz" } ?: "Native"} / ${song.bitsPerSample?.let { "$it-bit" } ?: "16-bit PCM"}", Color.White)
                InspectorRow("Bitrate & File Size", "${song.bitrateKbps?.let { "$it kbps" } ?: "VBR / Lossless"} • $sizeText", Color.White)
                InspectorRow("ReplayGain Applied", replayGainAppliedDb?.let { String.format(Locale.US, "%+.2f dB", it) } ?: "0.00 dB (Inactive/None)", Color.White)
                InspectorRow("Audio Output Route", activeOutputRoute.label, accentColor)
                InspectorRow(
                    "Active DSP Pipeline",
                    if (!dspSettings.dspEnabled) "Bypassed (Bit-Direct)"
                    else "$activePresetName • HPF ${if (dspSettings.hpfCutoffHz.hz > 0) "${dspSettings.hpfCutoffHz.hz}Hz" else "Off"} • Sub LPF ${if (dspSettings.subLpfHz > 0) "${dspSettings.subLpfHz}Hz${if (dspSettings.subPhaseInverted) " 180°" else ""}" else "Off"} • Ceiling ${dspSettings.peakLimiterCeilingDb} dBFS" +
                        if (dspSettings.monoFoldDown) " • MONO" else "",
                    accentColor
                )
                InspectorRow("Source & Cache", "${song.sourceType} • ${if (song.isDownloaded) "Pinned Offline (${song.localFilePath?.take(42) ?: "Local"})" else "Network Stream / ExoCache"}", Color.White)
                InspectorRow("Playback Stats", "${song.playCount} plays • Last cursor ${formatClockMs(song.lastPositionMs)}", Color.White)
                InspectorRow("Relative Path / URL", song.relativePath.ifBlank { song.fileUrl }, Color(0xFF94A3B8))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor, contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Close", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun InspectorRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF94A3B8),
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(140.dp)
        )
        Text(
            text = value,
            color = valueColor,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatClockMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000L
    val min = totalSec / 60L
    val sec = totalSec % 60L
    return String.format(Locale.US, "%d:%02d", min, sec)
}
