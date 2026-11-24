package com.zentryx.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zentryx.music.dsp.*
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Convenience overload bound directly to [HeadunitDspEngine] (`AndroidDspAudioEngine`).
 */
@Composable
fun SurroundMatrixDebugger(
    engine: HeadunitDspEngine,
    accentColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true
) {
    val dsp by engine.dspSettings.collectAsStateWithLifecycle()
    SurroundMatrixDebugger(
        dspSettings = dsp,
        accentColor = accentColor,
        surfaceColor = surfaceColor,
        borderColor = borderColor,
        onSelectSurroundMode = { mode -> engine.selectSurroundMode(mode) },
        onToggleDspEnabled = { enabled -> engine.updateSettings(dsp.copy(dspEnabled = enabled)) },
        onToggleMonoFoldDown = { mono -> engine.updateSettings(dsp.copy(monoFoldDown = mono)) },
        snapshotProvider = { trigger -> engine.getSurroundDebugSnapshot(trigger) },
        onDumpToLogcat = { engine.logSurroundMatrixDiagnostics("debugger_manual_dump") },
        modifier = modifier,
        initiallyExpanded = initiallyExpanded
    )
}

/**
 * Live Surround Matrix & PCM Bitstream Debugger for Automotive Headunit verification.
 *
 * - Exposes one-tap toggle buttons for every [SurroundMode] (Direct, Logic 7, Concert Hall,
 *   Studio Focus, Dolby Pro Logic II, Dolby Atmos 3D, Virtual 360°) plus DSP Master & Mono toggles.
 * - Reads live [HeadunitDspEngine] state and [CarDspAudioProcessor] PCM bitstream telemetry
 *   every 250 ms and immediately on profile change.
 * - Displays the active DSP parameter map and bitstream verification fields in a syntax-colored
 *   JSON-like inspector so you can confirm whether switching profiles actually modifies output PCM.
 */
@Composable
fun SurroundMatrixDebugger(
    dspSettings: DspSettings,
    accentColor: Color,
    surfaceColor: Color,
    borderColor: Color,
    onSelectSurroundMode: (SurroundMode) -> Unit,
    onToggleDspEnabled: (Boolean) -> Unit,
    onToggleMonoFoldDown: (Boolean) -> Unit,
    snapshotProvider: (trigger: String) -> SurroundDebugSnapshot,
    onDumpToLogcat: () -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true
) {
    val clipboard = LocalClipboardManager.current
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    var lastTrigger by remember { mutableStateOf("debugger_init") }
    var snapshot by remember { mutableStateOf(snapshotProvider("debugger_init")) }
    var statusToast by remember { mutableStateOf<String?>(null) }

    // Refresh immediately whenever DSP settings or trigger change
    LaunchedEffect(
        dspSettings.dspEnabled,
        dspSettings.surroundMode,
        dspSettings.surroundIntensity,
        dspSettings.centerCoherence,
        dspSettings.virtualHeight,
        dspSettings.stereoWidthPercent,
        dspSettings.monoFoldDown,
        dspSettings.selectedPresetId,
        lastTrigger
    ) {
        snapshot = snapshotProvider(lastTrigger)
    }

    // Live 250 ms telemetry poll while expanded so buffer counts, RMS & bitstream checksums stream live
    LaunchedEffect(expanded) {
        while (expanded) {
            snapshot = snapshotProvider("live_poll")
            delay(250L)
        }
    }

    LaunchedEffect(statusToast) {
        if (statusToast != null) {
            delay(2200L)
            statusToast = null
        }
    }

    val diag = snapshot.diagnostics
    val bit = snapshot.bitstream
    val isLiveModifying = diag.surroundActive &&
        bit.pcmConfigured &&
        snapshot.isAudioStreamingLive &&
        bit.surroundStageExecuted &&
        bit.bitstreamModified &&
        bit.surroundDeltaRms > 0.00005f

    val (verdictLabel, verdictColor) = when {
        !diag.dspEnabled -> "BYPASSED (DSP OFF)" to Color(0xFFEF4444)
        diag.monoFoldDown -> "SUPPRESSED (MONO)" to Color(0xFFF59E0B)
        diag.mode == SurroundMode.OFF -> "DRY DIRECT STEREO" to Color(0xFF94A3B8)
        !bit.pcmConfigured -> "PCM NOT CONFIGURED" to Color(0xFFF59E0B)
        bit.buffersProcessed == 0L -> "IDLE · PLAY AUDIO TO VERIFY" to Color(0xFFF59E0B)
        !snapshot.isAudioStreamingLive -> "PAUSED (LAST BUFFER HELD)" to Color(0xFF94A3B8)
        isLiveModifying -> "BITSTREAM MODIFIED LIVE" to Color(0xFF22C55E)
        bit.surroundStageExecuted && bit.inputRms < 0.0001f -> "ACTIVE · SILENT INPUT" to accentColor
        bit.fastPathBypassed -> "WARNING: FAST-PATH BYPASS" to Color(0xFFEF4444)
        else -> "SURROUND ARMED" to accentColor
    }

    val formattedJson = remember(snapshot) { formatSurroundDebugJson(snapshot) }
    val highlightedJson = remember(formattedJson, accentColor) {
        buildHighlightedJsonText(formattedJson, accentColor)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = surfaceColor,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (isLiveModifying) Color(0xFF22C55E).copy(alpha = 0.55f) else borderColor, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header row with collapse toggle & live bitstream verdict badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { expanded = !expanded }
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "SURROUND MATRIX DEBUGGER (LIVE BITSTREAM INSPECTOR)",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.sp,
                                color = accentColor
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "HeadunitDspEngine parameter map & real-time PCM output delta verifier",
                            fontSize = 10.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = verdictColor.copy(alpha = 0.16f),
                        modifier = Modifier.border(0.75.dp, verdictColor.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    ) {
                        Text(
                            text = verdictLabel,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = verdictColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse debugger" else "Expand debugger",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (expanded) {
                // Live Profile Toggle Strip (every SurroundMode + DSP Master & Mono guard toggles)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "LIVE PROFILE TOGGLES",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp,
                            color = Color(0xFF64748B)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            DebuggerQuickToggle(
                                label = if (dspSettings.dspEnabled) "DSP: ON" else "DSP: OFF",
                                active = dspSettings.dspEnabled,
                                activeColor = Color(0xFF22C55E),
                                borderColor = borderColor,
                                onClick = {
                                    lastTrigger = "debugger_toggle_dsp(${!dspSettings.dspEnabled})"
                                    onToggleDspEnabled(!dspSettings.dspEnabled)
                                }
                            )
                            DebuggerQuickToggle(
                                label = if (dspSettings.monoFoldDown) "MONO: ON" else "MONO: OFF",
                                active = dspSettings.monoFoldDown,
                                activeColor = Color(0xFFF59E0B),
                                borderColor = borderColor,
                                onClick = {
                                    lastTrigger = "debugger_toggle_mono(${!dspSettings.monoFoldDown})"
                                    onToggleMonoFoldDown(!dspSettings.monoFoldDown)
                                }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SurroundMode.entries.forEach { mode ->
                            val isSelected = dspSettings.surroundMode == mode
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) accentColor else Color(0xFF0A0F1E),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isSelected) accentColor else borderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        lastTrigger = "debugger_toggle(${mode.id})"
                                        onSelectSurroundMode(mode)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = mode.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isSelected) Color.Black else Color.White,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = mode.badge,
                                        fontSize = 8.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) Color(0xFF0F172A) else Color(0xFF94A3B8),
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // Compact live bitstream verification readout tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val deltaColor = if (bit.surroundDeltaRms > 0.00005f) Color(0xFF22C55E) else Color(0xFF94A3B8)
                    val modifiedColor = if (bit.bitstreamModified && bit.surroundStageExecuted) Color(0xFF22C55E) else Color(0xFF94A3B8)
                    DebuggerStatTile(
                        title = "BITSTREAM MUTATED",
                        value = if (bit.bitstreamModified) "TRUE" else "FALSE",
                        subtitle = if (bit.surroundStageExecuted) "Surround DSP ran" else if (bit.fastPathBypassed) "Dry passthrough" else "Non-surround DSP",
                        valueColor = modifiedColor,
                        borderColor = borderColor,
                        modifier = Modifier.weight(1f)
                    )
                    DebuggerStatTile(
                        title = "SURROUND Δ RMS",
                        value = String.format(Locale.US, "%.5f", bit.surroundDeltaRms),
                        subtitle = "Peak Δ ${String.format(Locale.US, "%.4f", bit.peakSampleDelta)}",
                        valueColor = deltaColor,
                        borderColor = borderColor,
                        modifier = Modifier.weight(1f)
                    )
                    DebuggerStatTile(
                        title = "PCM PIPELINE",
                        value = if (bit.pcmConfigured) "${bit.sampleRateHz / 1000}k · ${bit.inputEncoding.removePrefix("PCM_")}" else "PENDING",
                        subtitle = "Buf #${bit.buffersProcessed} · Coeff v${bit.coeffVersionApplied}/${bit.coeffVersionTarget}",
                        valueColor = if (bit.pcmConfigured) Color.White else Color(0xFFF59E0B),
                        borderColor = borderColor,
                        modifier = Modifier.weight(1f)
                    )
                    DebuggerStatTile(
                        title = "IN → OUT CHECKSUM",
                        value = if (bit.inputChecksum == bit.outputChecksum) "IDENTICAL" else "MODIFIED",
                        subtitle = "${String.format(Locale.US, "%04X", bit.inputChecksum and 0xFFFF)} → ${String.format(Locale.US, "%04X", bit.outputChecksum and 0xFFFF)}",
                        valueColor = if (bit.inputChecksum != bit.outputChecksum) Color(0xFF22C55E) else Color(0xFF94A3B8),
                        borderColor = borderColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                // JSON-like Parameter Map & Bitstream Inspector View
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF060A14),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(10.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "ACTIVE DSP PARAMETER MAP (JSON)",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.8.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (statusToast != null) {
                                    Text(
                                        text = statusToast!!,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = accentColor
                                    )
                                }
                                OutlinedButton(
                                    onClick = {
                                        lastTrigger = "debugger_manual_dump"
                                        onDumpToLogcat()
                                        statusToast = "Logged to ZentryxDspEngine"
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.75.dp, borderColor)
                                ) {
                                    Icon(Icons.Default.Terminal, contentDescription = null, tint = accentColor, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Logcat", fontSize = 10.sp, color = Color.White)
                                }
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(formattedJson))
                                        statusToast = "JSON copied"
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp),
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(0.75.dp, borderColor)
                                ) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = accentColor, modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Copy JSON", fontSize = 10.sp, color = Color.White)
                                }
                            }
                        }

                        SelectionContainer {
                            Text(
                                text = highlightedJson,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DebuggerQuickToggle(
    label: String,
    active: Boolean,
    activeColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (active) activeColor.copy(alpha = 0.18f) else Color(0xFF0A0F1E),
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .border(0.75.dp, if (active) activeColor else borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Black,
            color = if (active) activeColor else Color(0xFF94A3B8),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun DebuggerStatTile(
    title: String,
    value: String,
    subtitle: String,
    valueColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF0A0F1E),
        modifier = modifier.border(0.75.dp, borderColor, RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                color = Color(0xFF64748B),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF94A3B8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildHighlightedJsonText(json: String, accentColor: Color): AnnotatedString {
    val keyColor = Color(0xFF7DD3FC)
    val stringColor = Color(0xFF86EFAC)
    val numberColor = Color(0xFFFDE68A)
    val boolTrueColor = Color(0xFF4ADE80)
    val boolFalseColor = Color(0xFFF87171)
    val punctColor = Color(0xFF64748B)

    return buildAnnotatedString {
        val lines = json.split("\n")
        lines.forEachIndexed { lineIdx, line ->
            val colonIdx = line.indexOf("\": ")
            if (colonIdx > 0) {
                val leading = line.substring(0, colonIdx + 1)
                val valuePart = line.substring(colonIdx + 3)
                val trimmedVal = valuePart.trimEnd(',')
                val hasComma = valuePart.endsWith(",")

                withStyle(SpanStyle(color = keyColor)) { append(leading) }
                withStyle(SpanStyle(color = punctColor)) { append(": ") }

                val valColor = when {
                    trimmedVal == "true" -> boolTrueColor
                    trimmedVal == "false" -> boolFalseColor
                    trimmedVal.startsWith("\"") -> if (trimmedVal.contains("SURROUND_MODIFYING_BITSTREAM")) accentColor else stringColor
                    trimmedVal == "{" || trimmedVal == "}" -> punctColor
                    else -> numberColor
                }
                withStyle(SpanStyle(color = valColor)) { append(trimmedVal) }
                if (hasComma) {
                    withStyle(SpanStyle(color = punctColor)) { append(",") }
                }
            } else {
                withStyle(SpanStyle(color = punctColor)) { append(line) }
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }
}
