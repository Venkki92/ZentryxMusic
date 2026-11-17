package com.zentryx.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zentryx.music.visualizer.VisualizerColorway
import com.zentryx.music.visualizer.VisualizerFrame
import com.zentryx.music.visualizer.VisualizerMode
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Hardware-accelerated Compose Canvas rendering 6 interactive Mercedes-Benz / MBUX / AMG visualizers
 * driven by live ExoPlayer FFT + beat transient audio telemetry.
 */
@Composable
fun MercedesVisualizerCanvas(
    mode: VisualizerMode,
    colorway: VisualizerColorway,
    frame: VisualizerFrame,
    isPlaying: Boolean,
    trackTitle: String,
    trackArtist: String,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "S203VisPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val primary = Color(colorway.primaryHex)
    val secondary = Color(colorway.secondaryHex)
    val needle = Color(colorway.needleHex)
    val dialBg = Color(colorway.dialBgHex)

    // If hardware Visualizer capture has no permission or paused, modulate lightly during playback or stay idle
    val energyBass = if (frame.isLiveAudio) frame.subBass else if (isPlaying) 0.35f + 0.25f * sin(phase * 2f) else 0.08f
    val energyMid = if (frame.isLiveAudio) frame.mid else if (isPlaying) 0.28f + 0.20f * cos(phase * 3f) else 0.06f
    val energyTreble = if (frame.isLiveAudio) frame.treble else if (isPlaying) 0.22f + 0.18f * sin(phase * 5f) else 0.04f
    val beat = if (frame.isLiveAudio) frame.beatPulse else if (isPlaying) ((sin(phase * 4f) + 1f) * 0.25f) else 0f

    Box(
        modifier = modifier
            .background(dialBg)
            .border(1.dp, primary.copy(alpha = 0.28f), RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val radius = min(w, h) * 0.42f

            when (mode) {
                VisualizerMode.GAUGE -> {
                    // W203 VDO Twin/Single Analog Gauge
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primary.copy(alpha = 0.12f + beat * 0.16f), dialBg),
                            center = Offset(cx, cy),
                            radius = radius * 1.15f
                        ),
                        radius = radius * 1.05f,
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = Color(0xFF334155),
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 3.dp.toPx())
                    )
                    val startAngleDeg = 135f
                    val sweepDeg = 270f
                    val ticks = 24
                    for (i in 0..ticks) {
                        val frac = i / ticks.toFloat()
                        val deg = startAngleDeg + frac * sweepDeg
                        val rad = (deg * PI / 180.0).toFloat()
                        val isMajor = i % 4 == 0
                        val isRedline = i >= 19
                        val innerR = radius * if (isMajor) 0.76f else 0.84f
                        val outerR = radius * 0.94f
                        drawLine(
                            color = when {
                                isRedline -> Color(0xFFEF4444)
                                isMajor -> primary
                                else -> Color(0xFF64748B)
                            },
                            start = Offset(cx + cos(rad) * innerR, cy + sin(rad) * innerR),
                            end = Offset(cx + cos(rad) * outerR, cy + sin(rad) * outerR),
                            strokeWidth = if (isMajor) 3.dp.toPx() else 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    // Needle driven by bass + mid transient
                    val needleEnergy = (energyBass * 0.65f + energyMid * 0.35f).coerceIn(0f, 1f)
                    val needleDeg = startAngleDeg + needleEnergy * sweepDeg
                    val needleRad = (needleDeg * PI / 180.0).toFloat()
                    drawLine(
                        color = needle,
                        start = Offset(cx, cy),
                        end = Offset(cx + cos(needleRad) * radius * 0.82f, cy + sin(needleRad) * radius * 0.82f),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = primary, radius = 8.dp.toPx(), center = Offset(cx, cy))
                }

                VisualizerMode.STAR_PULSE -> {
                    // Mercedes 3-Pointed Emblem + 48 Harmonic Rays
                    val rays = 48
                    for (r in 0 until rays) {
                        val bandVal = if (frame.isLiveAudio) frame.bands32[r % 32] else (0.25f + 0.35f * absSin(phase * 2f + r * 0.35f))
                        val angle = ((r / rays.toFloat()) * 2f * PI).toFloat() + phase * 0.25f
                        val rayLen = radius * (0.62f + bandVal * 0.45f)
                        drawLine(
                            color = if (r % 2 == 0) primary.copy(alpha = 0.85f) else secondary.copy(alpha = 0.65f),
                            start = Offset(cx + cos(angle) * radius * 0.56f, cy + sin(angle) * radius * 0.56f),
                            end = Offset(cx + cos(angle) * rayLen, cy + sin(angle) * rayLen),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    val ringR = radius * (0.56f + beat * 0.08f)
                    drawCircle(color = primary, radius = ringR, center = Offset(cx, cy), style = Stroke(width = 3.dp.toPx()))
                    // 3-pointed star vertices rotated slowly
                    val starRot = -PI.toFloat() / 2f + phase * 0.15f
                    val starPath = Path().apply {
                        for (k in 0 until 6) {
                            val theta = starRot + (k * PI.toFloat() / 3f)
                            val r = if (k % 2 == 0) ringR * 0.92f else ringR * 0.22f
                            val px = cx + cos(theta) * r
                            val py = cy + sin(theta) * r
                            if (k == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                    drawPath(path = starPath, color = Color.White.copy(alpha = 0.92f))
                }

                VisualizerMode.AMG_TELEMETRY -> {
                    // Motorsport Redline Bars + Live Scrolling Torque Waveform
                    val barCount = 18
                    val slotW = (w * 0.84f) / barCount
                    val startX = w * 0.08f
                    for (i in 0 until barCount) {
                        val frac = if (frame.isLiveAudio) frame.bands32[(i * 31) / barCount] else (0.2f + 0.55f * absSin(phase * 3f + i * 0.4f))
                        val barH = (h * 0.42f * frac).coerceAtLeast(4f)
                        val barColor = if (frac > 0.78f) Color(0xFFEF4444) else if (frac > 0.5f) secondary else primary
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.9f),
                            topLeft = Offset(startX + i * slotW, h * 0.55f - barH),
                            size = Size(slotW * 0.65f, barH),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                    // Oscilloscope trace at bottom
                    val wavePath = Path()
                    for (i in 0 until 64) {
                        val px = w * 0.06f + (i / 63f) * (w * 0.88f)
                        val sample = if (frame.isLiveAudio) frame.waveform64[i] else sin(phase * 4f + i * 0.35f) * 0.45f
                        val py = h * 0.78f + sample * (h * 0.13f)
                        if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
                    }
                    drawPath(path = wavePath, color = Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }

                VisualizerMode.BURMESTER_WAVE -> {
                    // Perforated aluminum grille background dots
                    val cols = 12
                    val rows = 6
                    for (r in 1..rows) {
                        for (c in 1..cols) {
                            drawCircle(
                                color = Color(0xFF334155).copy(alpha = 0.45f),
                                radius = 2.dp.toPx(),
                                center = Offset((c / (cols + 1f)) * w, (r / (rows + 1f)) * h)
                            )
                        }
                    }
                    // Dual acoustic silk ribbons
                    listOf(primary to 1.0f, secondary to 0.7f).forEachIndexed { layerIdx, (col, ampScale) ->
                        val p = Path()
                        for (i in 0 until 64) {
                            val px = (i / 63f) * w
                            val waveSample = if (frame.isLiveAudio) frame.waveform64[i] else sin(phase * 3f + i * 0.25f + layerIdx) * 0.4f
                            val py = cy + waveSample * (h * 0.28f * ampScale) + sin(phase * 2f + i * 0.14f + layerIdx * 1.5f) * (10f + energyBass * 28f)
                            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                        }
                        drawPath(path = p, color = col, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                    }
                }

                VisualizerMode.COMAND -> {
                    // 32-Band Harman/Kardon COMAND Spectrum Analyzer + Gravity Peak Hold
                    val bars = 24
                    val gap = 4.dp.toPx()
                    val usableW = w * 0.88f
                    val barW = (usableW - gap * (bars - 1)) / bars
                    val baseX = w * 0.06f
                    val baseY = h * 0.86f
                    val maxBarH = h * 0.68f
                    for (i in 0 until bars) {
                        val bIdx = (i * 31) / bars
                        val level = if (frame.isLiveAudio) frame.bands32[bIdx] else (0.18f + 0.5f * absSin(phase * 2.5f + i * 0.3f))
                        val peak = if (frame.isLiveAudio) frame.peaks32[bIdx] else (level + 0.08f).coerceAtMost(1f)
                        val bh = (level * maxBarH).coerceAtLeast(6f)
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(secondary, primary)),
                            topLeft = Offset(baseX + i * (barW + gap), baseY - bh),
                            size = Size(barW, bh),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                        val peakY = baseY - (peak * maxBarH)
                        drawLine(
                            color = Color.White.copy(alpha = 0.9f),
                            start = Offset(baseX + i * (barW + gap), peakY),
                            end = Offset(baseX + i * (barW + gap) + barW, peakY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                VisualizerMode.MFD_AMBER -> {
                    // W203 Center MFD 16-column segmented LED bar readout
                    val segments = 10
                    val cols = 14
                    val cellW = (w * 0.82f) / cols
                    val cellH = (h * 0.56f) / segments
                    val startX = w * 0.09f
                    val bottomY = h * 0.82f
                    for (c in 0 until cols) {
                        val v = if (frame.isLiveAudio) frame.bands32[(c * 31) / cols] else (0.15f + 0.55f * absSin(phase * 3.2f + c * 0.45f))
                        val litCount = (v * segments).toInt().coerceIn(1, segments)
                        for (s in 0 until segments) {
                            val isLit = s < litCount
                            val segCol = when {
                                !isLit -> Color(0xFF1E293B).copy(alpha = 0.35f)
                                s >= segments - 2 -> Color(0xFFEF4444)
                                else -> primary
                            }
                            drawRoundRect(
                                color = segCol,
                                topLeft = Offset(startX + c * cellW, bottomY - (s + 1) * cellH),
                                size = Size(cellW * 0.72f, cellH * 0.72f),
                                cornerRadius = CornerRadius(2f, 2f)
                            )
                        }
                    }
                }
            }
        }

        // Subtle VDO cluster track overlay at top
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = mode.shortLabel.uppercase(),
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Black,
                color = primary,
                maxLines = 1
            )
            Text(
                text = if (frame.isLiveAudio) "LIVE FFT" else if (isPlaying) "SYNC" else "IDLE",
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (frame.isLiveAudio) primary else Color(0xFF94A3B8)
            )
        }

        Text(
            text = "$trackArtist — $trackTitle",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

private fun absSin(v: Float): Float = kotlin.math.abs(sin(v))

@Composable
fun FullscreenVisualizerDialog(
    mode: VisualizerMode,
    colorway: VisualizerColorway,
    frame: VisualizerFrame,
    isPlaying: Boolean,
    trackTitle: String,
    trackArtist: String,
    accentColor: Color,
    onSelectMode: (VisualizerMode) -> Unit,
    onSelectColorway: (VisualizerColorway) -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF020617))
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Top Mode + Colorway Switcher Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        VisualizerMode.entries.forEach { m ->
                            val selected = m == mode
                            FilterChip(
                                selected = selected,
                                onClick = { onSelectMode(m) },
                                label = { Text(m.shortLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accentColor.copy(alpha = 0.22f),
                                    selectedLabelColor = accentColor
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        VisualizerColorway.entries.forEach { cw ->
                            val active = cw == colorway
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(cw.primaryHex))
                                    .border(if (active) 2.5.dp else 1.dp, if (active) Color.White else Color.Transparent, CircleShape)
                                    .clickable { onSelectColorway(cw) }
                            )
                        }

                        IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Exit Fullscreen", tint = Color.White)
                        }
                    }
                }

                // Expanded Visualizer Canvas
                MercedesVisualizerCanvas(
                    mode = mode,
                    colorway = colorway,
                    frame = frame,
                    isPlaying = isPlaying,
                    trackTitle = trackTitle,
                    trackArtist = trackArtist,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                )

                // Compact Driving Transport Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0B1120))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(trackTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(trackArtist, color = Color(0xFF94A3B8), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPrevious, modifier = Modifier.size(46.dp)) {
                            Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(CircleShape)
                                .background(accentColor)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        IconButton(onClick = onNext, modifier = Modifier.size(46.dp)) {
                            Icon(imageVector = Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }
                }
            }
        }
    }
}
