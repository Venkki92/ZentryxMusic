package com.zentryx.music.ui.components

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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Hardware-accelerated 60fps Compose Canvas rendering 6 interactive Mercedes-Benz / MBUX / AMG visualizers
 * with true spring-damped analog needle physics, interpolated 60fps FFT bars, and dynamic beat bloom.
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
    val primary = Color(colorway.primaryHex)
    val secondary = Color(colorway.secondaryHex)
    val needleColor = Color(colorway.needleHex)
    val dialBg = Color(colorway.dialBgHex)

    // 60fps display-synced physics clock + spring needle + smoothed interpolated bands
    var phase by remember { mutableFloatStateOf(0f) }
    var springNeedlePos by remember { mutableFloatStateOf(0f) }
    var springNeedleVel by remember { mutableFloatStateOf(0f) }
    var springBeatGlow by remember { mutableFloatStateOf(0f) }
    val displayBands = remember { FloatArray(32) }
    val displayPeaks = remember { FloatArray(32) }

    val latestFrame by rememberUpdatedState(frame)
    val latestPlaying by rememberUpdatedState(isPlaying)

    LaunchedEffect(Unit) {
        var lastNs = withFrameNanos { it }
        while (true) {
            withFrameNanos { nowNs ->
                val dt = ((nowNs - lastNs) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                lastNs = nowNs

                val f = latestFrame
                val playing = latestPlaying

                phase = (phase + dt * if (playing) (1.6f + f.subBass * 2.2f) else 0.35f) % (200f * PI.toFloat())

                val targetBass = if (f.isLiveAudio) f.subBass else if (playing) 0.38f + 0.30f * abs(sin(phase * 1.8f)) else 0.04f
                val targetMid = if (f.isLiveAudio) f.mid else if (playing) 0.32f + 0.25f * abs(cos(phase * 2.4f)) else 0.03f
                val targetBeat = if (f.isLiveAudio) f.beatPulse else if (playing) ((sin(phase * 3.8f) + 1f) * 0.28f) else 0f

                val targetNeedle = (targetBass * 0.65f + targetMid * 0.25f + targetBeat * 0.20f).coerceIn(0f, 1f)

                // Critically-responsive automotive VDO needle spring dynamics
                val stiffness = 240f
                val damping = 20f
                val force = (targetNeedle - springNeedlePos) * stiffness - springNeedleVel * damping
                springNeedleVel += force * dt
                springNeedlePos = (springNeedlePos + springNeedleVel * dt).coerceIn(0f, 1f)

                springBeatGlow = springBeatGlow * 0.84f + targetBeat * 0.35f
                if (targetBeat > springBeatGlow) springBeatGlow = targetBeat

                // Interpolate 32 FFT bars smoothly at display refresh rate (60fps)
                for (i in 0 until 32) {
                    val target = if (f.isLiveAudio) {
                        f.bands32[i]
                    } else if (playing) {
                        0.18f + 0.56f * abs(sin(phase * 2.2f + i * 0.32f))
                    } else {
                        0.03f
                    }
                    val coeff = if (target > displayBands[i]) (dt * 28f).coerceAtMost(0.9f) else (dt * 10f).coerceAtMost(0.45f)
                    displayBands[i] += (target - displayBands[i]) * coeff

                    val pkTarget = if (f.isLiveAudio) f.peaks32[i] else (displayBands[i] + 0.06f).coerceAtMost(1f)
                    displayPeaks[i] = if (displayBands[i] >= displayPeaks[i]) {
                        displayBands[i]
                    } else {
                        (displayPeaks[i] - dt * 0.45f).coerceAtLeast(pkTarget.coerceAtMost(displayPeaks[i]))
                    }
                }
            }
        }
    }

    // Trigger Canvas invalidation cleanly every physics frame
    val animTick = phase
    val currentNeedle = springNeedlePos
    val currentBeat = springBeatGlow.coerceIn(0f, 1f)
    val energyBass = if (frame.isLiveAudio) frame.subBass else if (isPlaying) 0.38f + 0.28f * sin(animTick * 2f) else 0.06f

    Box(
        modifier = modifier
            .background(dialBg)
            .border(1.dp, primary.copy(alpha = 0.28f + currentBeat * 0.32f), RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val radius = min(w, h) * 0.42f

            when (mode) {
                VisualizerMode.GAUGE -> {
                    // W203 VDO Cluster Dial with spring needle + beat halo
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primary.copy(alpha = 0.14f + currentBeat * 0.26f), dialBg),
                            center = Offset(cx, cy),
                            radius = radius * 1.18f
                        ),
                        radius = radius * 1.08f,
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
                    val ticks = 28
                    for (i in 0..ticks) {
                        val frac = i / ticks.toFloat()
                        val deg = startAngleDeg + frac * sweepDeg
                        val rad = (deg * PI / 180.0).toFloat()
                        val isMajor = i % 4 == 0
                        val isRedline = i >= 22
                        val innerR = radius * if (isMajor) 0.75f else 0.84f
                        val outerR = radius * 0.94f
                        val isLit = frac <= currentNeedle
                        drawLine(
                            color = when {
                                isRedline && isLit -> Color(0xFFEF4444)
                                isRedline -> Color(0xFF7F1D1D)
                                isLit -> primary
                                isMajor -> Color(0xFF94A3B8)
                                else -> Color(0xFF475569)
                            },
                            start = Offset(cx + cos(rad) * innerR, cy + sin(rad) * innerR),
                            end = Offset(cx + cos(rad) * outerR, cy + sin(rad) * outerR),
                            strokeWidth = if (isMajor) 3.dp.toPx() else 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    // Inner peak arc ring
                    drawArc(
                        brush = Brush.sweepGradient(listOf(primary, secondary, Color(0xFFEF4444)), Offset(cx, cy)),
                        startAngle = startAngleDeg,
                        sweepAngle = currentNeedle * sweepDeg,
                        useCenter = false,
                        topLeft = Offset(cx - radius * 0.65f, cy - radius * 0.65f),
                        size = Size(radius * 1.3f, radius * 1.3f),
                        style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
                        alpha = 0.72f
                    )
                    // Spring-damped needle
                    val needleDeg = startAngleDeg + currentNeedle * sweepDeg
                    val needleRad = (needleDeg * PI / 180.0).toFloat()
                    drawLine(
                        color = needleColor.copy(alpha = 0.25f),
                        start = Offset(cx, cy),
                        end = Offset(cx + cos(needleRad) * radius * 0.86f, cy + sin(needleRad) * radius * 0.86f),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = needleColor,
                        start = Offset(cx, cy),
                        end = Offset(cx + cos(needleRad) * radius * 0.84f, cy + sin(needleRad) * radius * 0.84f),
                        strokeWidth = 3.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(color = primary, radius = 9.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(color = Color.Black, radius = 4.dp.toPx(), center = Offset(cx, cy))
                }

                VisualizerMode.STAR_PULSE -> {
                    val rays = 48
                    val innerRing = radius * (0.52f + currentBeat * 0.08f)
                    for (r in 0 until rays) {
                        val bandVal = displayBands[(r * 31) / rays]
                        val angle = ((r / rays.toFloat()) * 2f * PI).toFloat() + animTick * 0.12f
                        val rayLen = innerRing + radius * (0.10f + bandVal * 0.44f)
                        drawLine(
                            color = if (r % 2 == 0) primary.copy(alpha = 0.9f) else secondary.copy(alpha = 0.75f),
                            start = Offset(cx + cos(angle) * innerRing, cy + sin(angle) * innerRing),
                            end = Offset(cx + cos(angle) * rayLen, cy + sin(angle) * rayLen),
                            strokeWidth = 2.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    drawCircle(color = primary.copy(alpha = 0.25f + currentBeat * 0.45f), radius = innerRing * 1.06f, center = Offset(cx, cy), style = Stroke(width = 6.dp.toPx()))
                    drawCircle(color = primary, radius = innerRing, center = Offset(cx, cy), style = Stroke(width = 2.5.dp.toPx()))
                    val starRot = -PI.toFloat() / 2f + animTick * 0.08f
                    val starPath = Path().apply {
                        for (k in 0 until 6) {
                            val theta = starRot + (k * PI.toFloat() / 3f)
                            val r = if (k % 2 == 0) innerRing * 0.90f else innerRing * 0.20f
                            val px = cx + cos(theta) * r
                            val py = cy + sin(theta) * r
                            if (k == 0) moveTo(px, py) else lineTo(px, py)
                        }
                        close()
                    }
                    drawPath(path = starPath, color = Color.White.copy(alpha = 0.95f))
                }

                VisualizerMode.AMG_TELEMETRY -> {
                    val barCount = 20
                    val slotW = (w * 0.86f) / barCount
                    val startX = w * 0.07f
                    val midY = h * 0.56f
                    for (i in 0 until barCount) {
                        val frac = displayBands[(i * 31) / barCount]
                        val barH = (h * 0.40f * frac).coerceAtLeast(4f)
                        val barColor = if (frac > 0.78f) Color(0xFFEF4444) else if (frac > 0.48f) secondary else primary
                        drawRoundRect(
                            color = barColor.copy(alpha = 0.92f),
                            topLeft = Offset(startX + i * slotW, midY - barH),
                            size = Size(slotW * 0.68f, barH),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                    }
                    // Oscilloscope trace
                    val wavePath = Path()
                    for (i in 0 until 64) {
                        val px = w * 0.06f + (i / 63f) * (w * 0.88f)
                        val sample = if (frame.isLiveAudio) frame.waveform64[i] else sin(animTick * 3.5f + i * 0.35f) * 0.45f
                        val py = h * 0.77f + sample * (h * 0.14f)
                        if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
                    }
                    drawPath(path = wavePath, color = primary.copy(alpha = 0.28f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
                    drawPath(path = wavePath, color = Color.White, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                }

                VisualizerMode.BURMESTER_WAVE -> {
                    val cols = 14
                    val rows = 6
                    for (r in 1..rows) {
                        for (c in 1..cols) {
                            drawCircle(
                                color = Color(0xFF334155).copy(alpha = 0.35f + currentBeat * 0.25f),
                                radius = 2.dp.toPx(),
                                center = Offset((c / (cols + 1f)) * w, (r / (rows + 1f)) * h)
                            )
                        }
                    }
                    listOf(primary to 1.0f, secondary to 0.72f, Color.White.copy(alpha = 0.6f) to 0.44f).forEachIndexed { layerIdx, (col, ampScale) ->
                        val p = Path()
                        for (i in 0 until 64) {
                            val px = (i / 63f) * w
                            val waveSample = if (frame.isLiveAudio) frame.waveform64[i] else sin(animTick * 2.8f + i * 0.25f + layerIdx) * 0.42f
                            val py = cy + waveSample * (h * 0.30f * ampScale) + sin(animTick * 1.8f + i * 0.15f + layerIdx * 1.4f) * (8f + energyBass * 30f)
                            if (i == 0) p.moveTo(px, py) else p.lineTo(px, py)
                        }
                        drawPath(path = p, color = col, style = Stroke(width = if (layerIdx == 0) 3.2.dp.toPx() else 2.dp.toPx(), cap = StrokeCap.Round))
                    }
                }

                VisualizerMode.COMAND -> {
                    val bars = 28
                    val gap = 3.5.dp.toPx()
                    val usableW = w * 0.88f
                    val barW = (usableW - gap * (bars - 1)) / bars
                    val baseX = w * 0.06f
                    val baseY = h * 0.86f
                    val maxBarH = h * 0.68f
                    for (i in 0 until bars) {
                        val bIdx = (i * 31) / bars
                        val level = displayBands[bIdx]
                        val peak = displayPeaks[bIdx]
                        val bh = (level * maxBarH).coerceAtLeast(5f)
                        drawRoundRect(
                            brush = Brush.verticalGradient(listOf(secondary, primary)),
                            topLeft = Offset(baseX + i * (barW + gap), baseY - bh),
                            size = Size(barW, bh),
                            cornerRadius = CornerRadius(3f, 3f)
                        )
                        val peakY = baseY - (peak * maxBarH)
                        drawLine(
                            color = Color.White.copy(alpha = 0.92f),
                            start = Offset(baseX + i * (barW + gap), peakY),
                            end = Offset(baseX + i * (barW + gap) + barW, peakY),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                VisualizerMode.MFD_AMBER -> {
                    val segments = 11
                    val cols = 16
                    val cellW = (w * 0.84f) / cols
                    val cellH = (h * 0.58f) / segments
                    val startX = w * 0.08f
                    val bottomY = h * 0.83f
                    for (c in 0 until cols) {
                        val v = displayBands[(c * 31) / cols]
                        val litCount = (v * segments).toInt().coerceIn(if (isPlaying) 1 else 0, segments)
                        for (s in 0 until segments) {
                            val isLit = s < litCount
                            val segCol = when {
                                !isLit -> Color(0xFF1E293B).copy(alpha = 0.30f)
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

        // Top VDO HUD Header
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
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

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
