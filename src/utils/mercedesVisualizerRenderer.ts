import {
  BandEnergies,
  DialTick,
  S203Colorway,
  S203DisplayMode,
  calculateNeedleAngle,
  calculateThreePointedStarVertices,
  generateS203DialTicks,
} from './s203VisualizerLogic';

export interface VisualizerRenderParams {
  ctx: CanvasRenderingContext2D;
  width: number;
  height: number;
  mode: S203DisplayMode;
  colorway: S203Colorway;
  primaryColor: string;
  isPlaying: boolean;
  ecoMode: boolean;
  isFullscreen: boolean;
  reducedMotion: boolean;
  phase: number;
  bands: BandEnergies;
  smoothedBins: Float32Array;
  peakHolds: Float32Array;
  telemetryTrace: Float32Array;
  timeDomainData: Float32Array;
  hasLiveAudio: boolean;
  beatPulse: number;
  dialTicks: DialTick[];
  rpmTicks: DialTick[];
  dampedBass: number;
  dampedMid: number;
  currentSpeed: number;
}

function drawSingleClusterDial(
  ctx: CanvasRenderingContext2D,
  cx: number,
  cy: number,
  radius: number,
  ticks: DialTick[],
  needleEnergy: number,
  centerLabel: string,
  centerValue: string,
  colorway: S203Colorway,
  primaryColor: string,
  ecoMode: boolean,
  includeInnerMfd: boolean,
  smoothedBins: Float32Array,
  isPlaying: boolean,
  phase: number
) {
  // Dial face dark radial gradient
  const dialGrad = ctx.createRadialGradient(cx, cy, radius * 0.18, cx, cy, radius);
  dialGrad.addColorStop(0, '#111827');
  dialGrad.addColorStop(0.72, colorway.dialBg);
  dialGrad.addColorStop(1, '#020408');

  ctx.fillStyle = dialGrad;
  ctx.beginPath();
  ctx.arc(cx, cy, radius, 0, Math.PI * 2);
  ctx.fill();

  // Outer brushed metallic bezel ring
  ctx.lineWidth = Math.max(2, radius * 0.036);
  ctx.strokeStyle = colorway.bezelTrim;
  ctx.stroke();

  // Subtle inner rim highlight
  ctx.lineWidth = 1;
  ctx.strokeStyle = 'rgba(255, 255, 255, 0.16)';
  ctx.beginPath();
  ctx.arc(cx, cy, radius - Math.max(4, radius * 0.065), (135 * Math.PI) / 180, (405 * Math.PI) / 180);
  ctx.stroke();

  // Active energy arc halo behind ticks
  const needleDeg = calculateNeedleAngle(needleEnergy, 135, 405);
  ctx.lineWidth = Math.max(2, radius * 0.045);
  ctx.strokeStyle = colorway.glowHex;
  ctx.beginPath();
  ctx.arc(cx, cy, radius - Math.max(7, radius * 0.1), (135 * Math.PI) / 180, (needleDeg * Math.PI) / 180);
  ctx.stroke();

  // Dial ticks (batched into 3 paths: minor, major, redline) & labels
  const fontSize = Math.max(7, Math.round(radius * 0.11));
  const outerR = radius - Math.max(6, radius * 0.085);
  const majorLen = Math.max(7, radius * 0.12);
  const minorLen = Math.max(4, radius * 0.065);

  ctx.strokeStyle = 'rgba(255, 255, 255, 0.38)';
  ctx.lineWidth = 1;
  ctx.beginPath();
  for (let i = 0; i < ticks.length; i++) {
    const tick = ticks[i];
    if (tick.isMajor || tick.isRedline) continue;
    const rad = (tick.angleDeg * Math.PI) / 180;
    const innerR = outerR - minorLen;
    ctx.moveTo(cx + Math.cos(rad) * innerR, cy + Math.sin(rad) * innerR);
    ctx.lineTo(cx + Math.cos(rad) * outerR, cy + Math.sin(rad) * outerR);
  }
  ctx.stroke();

  ctx.strokeStyle = primaryColor;
  ctx.lineWidth = Math.max(1.5, radius * 0.025);
  ctx.beginPath();
  for (let i = 0; i < ticks.length; i++) {
    const tick = ticks[i];
    if (!tick.isMajor || tick.isRedline) continue;
    const rad = (tick.angleDeg * Math.PI) / 180;
    const innerR = outerR - majorLen;
    ctx.moveTo(cx + Math.cos(rad) * innerR, cy + Math.sin(rad) * innerR);
    ctx.lineTo(cx + Math.cos(rad) * outerR, cy + Math.sin(rad) * outerR);
  }
  ctx.stroke();

  ctx.strokeStyle = '#EF4444';
  ctx.beginPath();
  for (let i = 0; i < ticks.length; i++) {
    const tick = ticks[i];
    if (!tick.isRedline) continue;
    const rad = (tick.angleDeg * Math.PI) / 180;
    const innerR = outerR - (tick.isMajor ? majorLen : minorLen);
    ctx.moveTo(cx + Math.cos(rad) * innerR, cy + Math.sin(rad) * innerR);
    ctx.lineTo(cx + Math.cos(rad) * outerR, cy + Math.sin(rad) * outerR);
  }
  ctx.stroke();

  ctx.font = `700 ${fontSize}px ui-sans-serif, system-ui, sans-serif`;
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const numR = outerR - Math.max(12, radius * 0.22);
  for (let i = 0; i < ticks.length; i++) {
    const tick = ticks[i];
    if (tick.isMajor && tick.label) {
      const rad = (tick.angleDeg * Math.PI) / 180;
      ctx.fillStyle = tick.isRedline ? '#EF4444' : 'rgba(248, 250, 252, 0.82)';
      ctx.fillText(tick.label, cx + Math.cos(rad) * numR, cy + Math.sin(rad) * numR);
    }
  }

  // Optional inner dot-matrix window inside single compact dial
  if (includeInnerMfd) {
    const mfdW = radius * 1.15;
    const mfdH = radius * 0.54;
    const mfdX = cx - mfdW / 2;
    const mfdY = cy - mfdH / 2 - radius * 0.02;

    ctx.fillStyle = colorway.mfdBg;
    ctx.beginPath();
    ctx.roundRect(mfdX, mfdY, mfdW, mfdH, 4);
    ctx.fill();
    ctx.lineWidth = 1;
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.22)';
    ctx.stroke();

    const headerSize = Math.max(6, Math.round(radius * 0.095));
    ctx.fillStyle = primaryColor;
    ctx.font = `700 ${headerSize}px ui-monospace, monospace`;
    ctx.fillText('◄  AUDIO  ►', cx, mfdY + headerSize * 1.15);

    const bars = 12;
    const barGap = Math.max(1.5, mfdW * 0.02);
    const barW = Math.max(2, (mfdW * 0.8 - (bars - 1) * barGap) / bars);
    const barsStartX = cx - (bars * (barW + barGap) - barGap) / 2;
    const barsBaseY = mfdY + mfdH * 0.76;
    const maxBarH = mfdH * 0.42;

    for (let b = 0; b < bars; b++) {
      const binVal = isPlaying
        ? smoothedBins[Math.min(smoothedBins.length - 1, b * 2)]
        : 0.08 + Math.sin(phase + b) * 0.02;
      const bh = Math.max(2, binVal * maxBarH);
      ctx.fillStyle = primaryColor;
      ctx.globalAlpha = 0.9;
      ctx.fillRect(barsStartX + b * (barW + barGap), barsBaseY - bh, barW, bh);
    }
    ctx.globalAlpha = 1;

    const subSize = Math.max(5.5, Math.round(radius * 0.078));
    ctx.font = `600 ${subSize}px ui-monospace, monospace`;
    ctx.globalAlpha = 0.78;
    ctx.fillText(centerValue, cx, mfdY + mfdH * 0.9);
    ctx.globalAlpha = 1;
  } else {
    // Sub-readout inside twin dial
    const lblSize = Math.max(8, Math.round(radius * 0.085));
    const valSize = Math.max(11, Math.round(radius * 0.145));
    ctx.fillStyle = 'rgba(148, 163, 184, 0.8)';
    ctx.font = `600 ${lblSize}px ui-monospace, monospace`;
    ctx.fillText(centerLabel, cx, cy + radius * 0.34);
    ctx.fillStyle = primaryColor;
    ctx.font = `800 ${valSize}px ui-monospace, monospace`;
    ctx.fillText(centerValue, cx, cy + radius * 0.5);
  }

  // Needle
  const needleRad = (needleDeg * Math.PI) / 180;
  const needleLen = radius - Math.max(10, radius * 0.14);
  const needleBase = Math.max(2, radius * 0.032);

  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate(needleRad);

  ctx.beginPath();
  ctx.moveTo(-radius * 0.14, -needleBase);
  ctx.lineTo(needleLen, -0.75);
  ctx.lineTo(needleLen + Math.max(4, radius * 0.05), 0);
  ctx.lineTo(needleLen, 0.75);
  ctx.lineTo(-radius * 0.14, needleBase);
  ctx.closePath();

  ctx.fillStyle = colorway.needleHex;
  if (!ecoMode) {
    ctx.shadowColor = colorway.needleHex;
    ctx.shadowBlur = Math.min(12, radius * 0.1);
  }
  ctx.fill();
  ctx.shadowBlur = 0;

  // Needle spine
  ctx.strokeStyle = '#FFFFFF';
  ctx.lineWidth = 1;
  ctx.beginPath();
  ctx.moveTo(radius * 0.1, 0);
  ctx.lineTo(needleLen - 3, 0);
  ctx.stroke();
  ctx.restore();

  // Center Hub
  const hubR = Math.max(9, radius * 0.14);
  ctx.fillStyle = '#0f172a';
  ctx.beginPath();
  ctx.arc(cx, cy, hubR, 0, Math.PI * 2);
  ctx.fill();
  ctx.lineWidth = Math.max(1.5, radius * 0.025);
  ctx.strokeStyle = colorway.bezelTrim;
  ctx.stroke();

  ctx.fillStyle = colorway.needleHex;
  ctx.beginPath();
  ctx.arc(cx, cy, Math.max(2.5, hubR * 0.28), 0, Math.PI * 2);
  ctx.fill();

  // Curved glass glare
  const glassGrad = ctx.createLinearGradient(cx, cy - radius, cx, cy);
  glassGrad.addColorStop(0, 'rgba(255, 255, 255, 0.11)');
  glassGrad.addColorStop(0.55, 'rgba(255, 255, 255, 0.02)');
  glassGrad.addColorStop(1, 'transparent');
  ctx.fillStyle = glassGrad;
  ctx.beginPath();
  ctx.arc(cx, cy, radius - 2, Math.PI, Math.PI * 2);
  ctx.fill();
}

export function renderMercedesVisualizerFrame(params: VisualizerRenderParams): void {
  const {
    ctx,
    width,
    height,
    mode,
    colorway,
    primaryColor,
    isPlaying,
    ecoMode,
    isFullscreen,
    reducedMotion,
    phase,
    bands,
    smoothedBins,
    peakHolds,
    telemetryTrace,
    timeDomainData,
    hasLiveAudio,
    beatPulse,
    dialTicks,
    rpmTicks,
    dampedBass,
    dampedMid,
    currentSpeed,
  } = params;

  ctx.clearRect(0, 0, width, height);

  const bassWithBeat = Math.min(1, dampedBass * 0.85 + beatPulse * 0.34);
  const midWithBeat = Math.min(1, dampedMid * 0.85 + bands.treble * 0.25);

  // ==========================================================
  // 1. W203 / S203 VDO INSTRUMENT CLUSTER (Single or Twin Dial)
  // ==========================================================
  if (mode === 'gauge') {
    const canShowTwin = isFullscreen && width >= 620 && height >= 260;

    if (canShowTwin) {
      // Twin-Dial W203 Binnacle Layout + Central MFD Screen
      const dialRadius = Math.min(height * 0.42, width * 0.21);
      const centerMfdW = Math.min(260, width * 0.24);
      const centerMfdH = dialRadius * 1.18;
      const dialOffset = dialRadius + centerMfdW * 0.56;
      const cy = height * 0.52;

      // Subtle binnacle hood shadow arch
      ctx.strokeStyle = 'rgba(255,255,255,0.08)';
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.roundRect(
        width / 2 - dialOffset - dialRadius - 18,
        cy - dialRadius - 16,
        (dialOffset + dialRadius + 18) * 2,
        dialRadius * 2 + 32,
        dialRadius
      );
      ctx.stroke();

      // Left Dial: Tachometer / Bass Energy (x1000 RPM look)
      const rpmVal = (0.7 + bassWithBeat * 6.3).toFixed(1);
      drawSingleClusterDial(
        ctx,
        width / 2 - dialOffset,
        cy,
        dialRadius,
        rpmTicks.length ? rpmTicks : generateS203DialTicks(29, 135, 405, 7),
        bassWithBeat,
        'BASS RPM ×1000',
        rpmVal,
        colorway,
        primaryColor,
        ecoMode,
        false,
        smoothedBins,
        isPlaying,
        phase
      );

      // Right Dial: Speedo / Vocal & Harmonic Output Scale
      const outDb = Math.round(midWithBeat * 240);
      drawSingleClusterDial(
        ctx,
        width / 2 + dialOffset,
        cy,
        dialRadius,
        dialTicks,
        midWithBeat,
        currentSpeed > 0 ? 'GPS KM/H' : 'HARMONIC IDX',
        currentSpeed > 0 ? `${currentSpeed}` : `${outDb}`,
        colorway,
        primaryColor,
        ecoMode,
        false,
        smoothedBins,
        isPlaying,
        phase
      );

      // Center VDO MFD Panel between the Twin Dials
      const mfdX = width / 2 - centerMfdW / 2;
      const mfdY = cy - centerMfdH / 2;
      ctx.fillStyle = colorway.mfdBg;
      ctx.beginPath();
      ctx.roundRect(mfdX, mfdY, centerMfdW, centerMfdH, 10);
      ctx.fill();
      ctx.lineWidth = 1.5;
      ctx.strokeStyle = colorway.bezelTrim;
      ctx.stroke();

      ctx.fillStyle = primaryColor;
      ctx.font = '700 11px ui-monospace, monospace';
      ctx.textAlign = 'center';
      ctx.fillText('◄   AUDIO DSP   ►', width / 2, mfdY + 20);

      // Divider rule
      ctx.strokeStyle = 'rgba(255,255,255,0.14)';
      ctx.beginPath();
      ctx.moveTo(mfdX + 12, mfdY + 28);
      ctx.lineTo(mfdX + centerMfdW - 12, mfdY + 28);
      ctx.stroke();

      // Central Multi-band spectrum inside MFD
      const barCount = 16;
      const barGap = 4;
      const barW = Math.max(4, (centerMfdW - 28 - (barCount - 1) * barGap) / barCount);
      const startX = width / 2 - (barCount * (barW + barGap) - barGap) / 2;
      const baseLineY = mfdY + centerMfdH - 30;
      const barsTopLimit = mfdY + 74;
      const maxH = Math.max(24, baseLineY - barsTopLimit);

      // Upper MFD telemetry readout
      ctx.fillStyle = 'rgba(248, 250, 252, 0.72)';
      ctx.font = '700 10px ui-monospace, monospace';
      ctx.fillText(isPlaying ? 'ACTIVE STREAM' : 'SYSTEM READY', width / 2, mfdY + 44);

      ctx.fillStyle = primaryColor;
      ctx.font = '800 13px ui-monospace, monospace';
      ctx.fillText(
        `${Math.round(bands.overall * 100)}% LOAD`,
        width / 2,
        mfdY + 61
      );

      for (let b = 0; b < barCount; b++) {
        const val = isPlaying ? smoothedBins[b * 2] : 0.14 + Math.sin(phase * 0.7 + b * 0.5) * 0.04;
        const bh = Math.max(4, val * maxH);
        ctx.fillStyle = val > 0.82 ? colorway.needleHex : primaryColor;
        ctx.fillRect(startX + b * (barW + barGap), baseLineY - bh, barW, bh);
      }

      ctx.fillStyle = primaryColor;
      ctx.font = '600 10px ui-monospace, monospace';
      ctx.fillText(isPlaying ? 'COMAND APS · ACTIVE' : 'COMAND STANDBY', width / 2, mfdY + centerMfdH - 14);
    } else {
      // Single Classic Dial
      const cx = width / 2;
      const cy = height / 2 + Math.min(10, height * 0.03);
      const dialRadius = Math.min(width, height) * 0.44;
      drawSingleClusterDial(
        ctx,
        cx,
        cy,
        dialRadius,
        dialTicks,
        bassWithBeat,
        'AUDIO',
        'S203 · VDO',
        colorway,
        primaryColor,
        ecoMode,
        true,
        smoothedBins,
        isPlaying,
        phase
      );
    }
    return;
  }

  // ==========================================================
  // 2. MERCEDES EMBLEM STARLIGHT (3-Pointed Star + 48-64 Rays)
  // ==========================================================
  if (mode === 'star_pulse') {
    const cx = width / 2;
    const cy = height / 2;
    const minDim = Math.min(width, height);
    const outerMaxR = minDim * 0.45;
    const emblemScale = 1 + (reducedMotion ? 0.02 : bassWithBeat * 0.13 + beatPulse * 0.11);
    const starOuterR = minDim * 0.25 * emblemScale;
    const ringR = minDim * 0.275 * (1 + (reducedMotion ? 0 : beatPulse * 0.035));

    // Beat-reactive radial cockpit spotlight
    const bgGlow = ctx.createRadialGradient(cx, cy, ringR * 0.2, cx, cy, outerMaxR * (1.08 + beatPulse * 0.18));
    bgGlow.addColorStop(0, colorway.glowHex);
    bgGlow.addColorStop(0.55, 'rgba(15, 23, 42, 0.25)');
    bgGlow.addColorStop(1, 'transparent');
    ctx.fillStyle = bgGlow;
    ctx.beginPath();
    ctx.arc(cx, cy, outerMaxR * 1.25, 0, Math.PI * 2);
    ctx.fill();

    // Orbiting harmonic dashed precision ring (spins slightly faster on energetic tracks)
    const rotSlow = reducedMotion ? 0 : phase * 0.25;
    ctx.save();
    ctx.translate(cx, cy);
    ctx.rotate(rotSlow);
    ctx.strokeStyle = beatPulse > 0.4 ? primaryColor : 'rgba(203, 213, 225, 0.24)';
    ctx.globalAlpha = 0.24 + beatPulse * 0.45;
    ctx.lineWidth = Math.max(1.2, minDim * 0.004);
    ctx.setLineDash([Math.max(4, minDim * 0.03), Math.max(6, minDim * 0.045)]);
    ctx.beginPath();
    ctx.arc(0, 0, outerMaxR * (0.93 + beatPulse * 0.03), 0, Math.PI * 2);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.restore();
    ctx.globalAlpha = 1;

    // Symmetrical mirrored 48-64 Ray Radial Spectrum around the emblem ring (batched into 3 stroke calls)
    const numRays = isFullscreen ? 64 : 48;
    const halfRays = numRays / 2;
    const rayBaseR = ringR + Math.max(5, minDim * 0.022);
    const rayMaxLen = outerMaxR - rayBaseR;
    const raySpin = reducedMotion ? 0 : phase * 0.08;

    ctx.lineWidth = Math.max(2, (Math.PI * 2 * rayBaseR) / (numRays * 2.3));

    // Bucket 1: Subtle ambient rays (val <= 0.35)
    ctx.strokeStyle = primaryColor;
    ctx.globalAlpha = 0.42;
    ctx.beginPath();
    for (let i = 0; i < numRays; i++) {
      const mirrorPos = i < halfRays ? i : numRays - 1 - i;
      const binIdx = Math.min(smoothedBins.length - 1, Math.floor((mirrorPos / halfRays) * smoothedBins.length));
      const val = isPlaying ? smoothedBins[binIdx] : 0.1 + Math.sin(phase * 0.8 + i * 0.4) * 0.03;
      if (val > 0.35) continue;
      const angle = (i / numRays) * Math.PI * 2 - Math.PI / 2 + raySpin;
      const rayLen = Math.max(3, val * rayMaxLen);
      ctx.moveTo(cx + Math.cos(angle) * rayBaseR, cy + Math.sin(angle) * rayBaseR);
      ctx.lineTo(cx + Math.cos(angle) * (rayBaseR + rayLen), cy + Math.sin(angle) * (rayBaseR + rayLen));
    }
    ctx.stroke();

    // Bucket 2: Active mid-energy rays (0.35 < val <= 0.74)
    ctx.globalAlpha = 0.78;
    ctx.beginPath();
    for (let i = 0; i < numRays; i++) {
      const mirrorPos = i < halfRays ? i : numRays - 1 - i;
      const binIdx = Math.min(smoothedBins.length - 1, Math.floor((mirrorPos / halfRays) * smoothedBins.length));
      const val = isPlaying ? smoothedBins[binIdx] : 0.1 + Math.sin(phase * 0.8 + i * 0.4) * 0.03;
      if (val <= 0.35 || val > 0.74) continue;
      const angle = (i / numRays) * Math.PI * 2 - Math.PI / 2 + raySpin;
      const rayLen = Math.max(3, val * rayMaxLen);
      ctx.moveTo(cx + Math.cos(angle) * rayBaseR, cy + Math.sin(angle) * rayBaseR);
      ctx.lineTo(cx + Math.cos(angle) * (rayBaseR + rayLen), cy + Math.sin(angle) * (rayBaseR + rayLen));
    }
    ctx.stroke();

    // Bucket 3: Peak needleHex accent rays (val > 0.74)
    ctx.strokeStyle = colorway.needleHex;
    ctx.globalAlpha = 0.96;
    ctx.beginPath();
    for (let i = 0; i < numRays; i++) {
      const mirrorPos = i < halfRays ? i : numRays - 1 - i;
      const binIdx = Math.min(smoothedBins.length - 1, Math.floor((mirrorPos / halfRays) * smoothedBins.length));
      const val = isPlaying ? smoothedBins[binIdx] : 0.1 + Math.sin(phase * 0.8 + i * 0.4) * 0.03;
      if (val <= 0.74) continue;
      const angle = (i / numRays) * Math.PI * 2 - Math.PI / 2 + raySpin;
      const rayLen = Math.max(3, val * rayMaxLen);
      ctx.moveTo(cx + Math.cos(angle) * rayBaseR, cy + Math.sin(angle) * rayBaseR);
      ctx.lineTo(cx + Math.cos(angle) * (rayBaseR + rayLen), cy + Math.sin(angle) * (rayBaseR + rayLen));
    }
    ctx.stroke();
    ctx.globalAlpha = 1;

    // Metallic Outer Emblem Chrome Ring
    const ringGrad = ctx.createLinearGradient(cx - ringR, cy - ringR, cx + ringR, cy + ringR);
    ringGrad.addColorStop(0, '#F8FAFC');
    ringGrad.addColorStop(0.5, colorway.bezelTrim);
    ringGrad.addColorStop(1, '#475569');

    ctx.strokeStyle = ringGrad;
    ctx.lineWidth = Math.max(3, minDim * 0.022);
    ctx.beginPath();
    ctx.arc(cx, cy, ringR, 0, Math.PI * 2);
    ctx.stroke();

    // Inner dark cavity behind star
    ctx.fillStyle = '#050811';
    ctx.beginPath();
    ctx.arc(cx, cy, ringR - Math.max(2, minDim * 0.011), 0, Math.PI * 2);
    ctx.fill();

    // Faceted 3D Mercedes 3-Pointed Star Geometry (batched into 2 fill calls)
    const starRot = reducedMotion ? 0 : Math.sin(phase * 0.35) * 0.05 + (midWithBeat - 0.3) * 0.08;
    const verts = calculateThreePointedStarVertices(cx, cy, starOuterR, 0.21, starRot);

    ctx.fillStyle = '#E2E8F0';
    ctx.beginPath();
    for (let i = 0; i < 6; i += 2) {
      const next = (i + 1) % 6;
      ctx.moveTo(cx, cy);
      ctx.lineTo(verts[i].x, verts[i].y);
      ctx.lineTo(verts[next].x, verts[next].y);
      ctx.closePath();
    }
    ctx.fill();

    ctx.fillStyle = '#64748B';
    ctx.beginPath();
    for (let i = 1; i < 6; i += 2) {
      const next = (i + 1) % 6;
      ctx.moveTo(cx, cy);
      ctx.lineTo(verts[i].x, verts[i].y);
      ctx.lineTo(verts[next].x, verts[next].y);
      ctx.closePath();
    }
    ctx.fill();

    ctx.strokeStyle = primaryColor;
    ctx.globalAlpha = 0.4 + bassWithBeat * 0.55;
    ctx.lineWidth = 1 + beatPulse * 1.4;
    ctx.beginPath();
    ctx.moveTo(verts[0].x, verts[0].y);
    for (let i = 1; i < verts.length; i++) {
      ctx.lineTo(verts[i].x, verts[i].y);
    }
    ctx.closePath();
    ctx.stroke();
    ctx.globalAlpha = 1;
    return;
  }

  // ==========================================================
  // 3. AMG TRACK PACE TELEMETRY (G-Force Circle + Live Graph)
  // ==========================================================
  if (mode === 'amg_telemetry') {
    const pad = Math.max(12, Math.min(width, height) * 0.045);
    const isWide = width >= 460;

    // Carbon telemetry grid background (batched into one path)
    ctx.strokeStyle = 'rgba(148, 163, 184, 0.08)';
    ctx.lineWidth = 1;
    const gridStep = isFullscreen ? 36 : 22;
    ctx.beginPath();
    for (let gx = pad; gx < width - pad; gx += gridStep) {
      ctx.moveTo(gx, pad);
      ctx.lineTo(gx, height - pad);
    }
    for (let gy = pad; gy < height - pad; gy += gridStep) {
      ctx.moveTo(pad, gy);
      ctx.lineTo(width - pad, gy);
    }
    ctx.stroke();

    // Header
    const hdrSize = isFullscreen ? 12 : 8;
    ctx.fillStyle = '#EF4444';
    ctx.font = `800 ${hdrSize}px ui-monospace, monospace`;
    ctx.textAlign = 'left';
    ctx.fillText('AMG TRACK PACE', pad + 4, pad + hdrSize);

    ctx.fillStyle = 'rgba(226, 232, 240, 0.72)';
    ctx.textAlign = 'right';
    const powerKw = Math.round(bands.overall * 410 + beatPulse * 65 + (isPlaying ? 30 : 12));
    ctx.fillText(`${powerKw} kW · ${Math.round(bassWithBeat * 780)} Nm`, width - pad - 4, pad + hdrSize);

    // G-Force Ring geometry
    const topY = pad + hdrSize + 10;
    const gRadius = isWide
      ? Math.min((height - topY - pad) * 0.42, width * 0.16)
      : Math.min((height - topY - pad) * 0.28, width * 0.22);
    const gCx = isWide ? pad + gRadius + 16 : pad + gRadius + 8;
    const gCy = isWide ? (topY + height - pad) / 2 : topY + gRadius + 6;

    for (const mult of [0.35, 0.68, 1.0]) {
      ctx.strokeStyle = mult === 1.0 ? 'rgba(248, 250, 252, 0.28)' : 'rgba(148, 163, 184, 0.16)';
      ctx.lineWidth = mult === 1.0 ? 1.5 : 1;
      ctx.beginPath();
      ctx.arc(gCx, gCy, gRadius * mult, 0, Math.PI * 2);
      ctx.stroke();
    }
    ctx.beginPath();
    ctx.moveTo(gCx - gRadius, gCy);
    ctx.lineTo(gCx + gRadius, gCy);
    ctx.moveTo(gCx, gCy - gRadius);
    ctx.lineTo(gCx, gCy + gRadius);
    ctx.stroke();

    // G-Dot responds directly to bass/treble ratio + beat kicks
    const gMag = Math.min(0.95, (reducedMotion ? 0.2 : 0.92) * (dampedBass * 0.72 + beatPulse * 0.28));
    const gAngle = phase * 0.9 + (bands.treble - bands.bass) * 2.4;
    const gxOffset = Math.cos(gAngle) * gRadius * gMag;
    const gyOffset = Math.sin(gAngle * 1.15) * gRadius * Math.min(0.95, midWithBeat * 0.88);
    const dotX = gCx + gxOffset;
    const dotY = gCy + gyOffset;

    ctx.strokeStyle = primaryColor;
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(gCx, gCy);
    ctx.lineTo(dotX, dotY);
    ctx.stroke();

    ctx.fillStyle = beatPulse > 0.45 ? '#F97316' : '#EF4444';
    ctx.beginPath();
    ctx.arc(dotX, dotY, Math.max(4, gRadius * (0.1 + beatPulse * 0.04)), 0, Math.PI * 2);
    ctx.fill();
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = 1.5;
    ctx.stroke();

    // Rolling Torque / Audio Waveform Graph
    const graphX = gCx + gRadius + (isWide ? 28 : 16);
    const graphW = Math.max(60, width - pad - graphX);
    const graphY = topY + 6;
    const graphH = Math.max(48, height - pad - graphY - (isWide ? 14 : 6));

    ctx.fillStyle = 'rgba(2, 6, 23, 0.65)';
    ctx.beginPath();
    ctx.roundRect(graphX, graphY, graphW, graphH, 8);
    ctx.fill();
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.14)';
    ctx.lineWidth = 1;
    ctx.stroke();

    // Reference telemetry lines inside graph
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.beginPath();
    for (const ratio of [0.25, 0.5, 0.75]) {
      const ly = graphY + graphH * ratio;
      ctx.moveTo(graphX + 6, ly);
      ctx.lineTo(graphX + graphW - 6, ly);
    }
    ctx.stroke();

    if (isWide) {
      ctx.fillStyle = 'rgba(148, 163, 184, 0.55)';
      ctx.font = `600 ${isFullscreen ? 10 : 7}px ui-monospace, monospace`;
      ctx.textAlign = 'left';
      ctx.fillText('PEAK', graphX + 10, graphY + 14);
      ctx.fillText('MID', graphX + 10, graphY + graphH * 0.5 - 4);
    }

    // Live audio oscilloscope whisper behind torque trace when live FFT waveform is available
    if (hasLiveAudio && isPlaying && timeDomainData.length > 4) {
      const waveMidY = graphY + graphH * 0.5;
      const waveStep = Math.max(1, Math.floor(timeDomainData.length / 64));
      ctx.beginPath();
      let started = false;
      for (let i = 0; i < timeDomainData.length; i += waveStep) {
        const t = i / (timeDomainData.length - 1);
        const px = graphX + t * graphW;
        const py = waveMidY + timeDomainData[i] * (graphH * 0.36);
        if (!started) {
          ctx.moveTo(px, py);
          started = true;
        } else {
          ctx.lineTo(px, py);
        }
      }
      ctx.strokeStyle = 'rgba(248, 250, 252, 0.25)';
      ctx.lineWidth = 1.2;
      ctx.stroke();
    }

    const len = telemetryTrace.length;
    if (len > 1) {
      const areaGrad = ctx.createLinearGradient(0, graphY, 0, graphY + graphH);
      areaGrad.addColorStop(0, colorway.glowHex);
      areaGrad.addColorStop(1, 'rgba(2, 6, 23, 0)');

      ctx.beginPath();
      for (let i = 0; i < len; i++) {
        const px = graphX + (i / (len - 1)) * graphW;
        const val = Math.max(0.06, telemetryTrace[i]);
        const py = graphY + graphH - val * (graphH - 16) - 6;
        if (i === 0) ctx.moveTo(px, py);
        else ctx.lineTo(px, py);
      }
      ctx.lineTo(graphX + graphW, graphY + graphH);
      ctx.lineTo(graphX, graphY + graphH);
      ctx.closePath();
      ctx.fillStyle = areaGrad;
      ctx.fill();

      ctx.beginPath();
      let lastX = graphX;
      let lastY = graphY + graphH;
      for (let i = 0; i < len; i++) {
        const px = graphX + (i / (len - 1)) * graphW;
        const val = Math.max(0.06, telemetryTrace[i]);
        const py = graphY + graphH - val * (graphH - 16) - 6;
        if (i === 0) ctx.moveTo(px, py);
        else ctx.lineTo(px, py);
        lastX = px;
        lastY = py;
      }
      ctx.strokeStyle = primaryColor;
      ctx.lineWidth = isFullscreen ? 2.5 : 1.8;
      ctx.stroke();

      ctx.fillStyle = '#FFFFFF';
      ctx.beginPath();
      ctx.arc(lastX, lastY, isFullscreen ? 4 + beatPulse * 1.5 : 2.5, 0, Math.PI * 2);
      ctx.fill();
    }

    if (isWide) {
      ctx.fillStyle = 'rgba(248, 250, 252, 0.7)';
      ctx.font = `700 ${isFullscreen ? 11 : 8}px ui-monospace, monospace`;
      ctx.textAlign = 'center';
      ctx.fillText(`G-SYNC ${(0.2 + bassWithBeat * 1.45).toFixed(2)}G`, gCx, gCy + gRadius + 18);
    }
    return;
  }

  // ==========================================================
  // 4. BURMESTER 3D WAVE (Batched Grille + Real Audio Oscilloscope Ribbons)
  // ==========================================================
  if (mode === 'burmester_wave') {
    const spacing = isFullscreen ? 34 : 22;
    const cols = Math.floor(width / spacing);
    const rows = Math.floor(height / spacing);

    ctx.fillStyle = '#E2E8F0';
    ctx.globalAlpha = 0.08 + bands.overall * 0.16 + beatPulse * 0.08;
    ctx.beginPath();
    for (let r = 1; r < rows; r++) {
      const dy = r * spacing;
      for (let c = 1; c < cols; c++) {
        ctx.rect(c * spacing, dy, 2, 2);
      }
    }
    ctx.fill();
    ctx.globalAlpha = 1;

    ctx.fillStyle = 'rgba(248, 250, 252, 0.45)';
    ctx.font = `700 ${isFullscreen ? 11 : 8}px ui-monospace, monospace`;
    ctx.textAlign = 'left';
    ctx.fillText('BURMESTER · HIGH-END 3D SURROUND', 14, isFullscreen ? 22 : 14);

    const layers = [
      { color: colorway.secondaryHex, amp: bands.treble * 0.42 + 0.06, freq: 3.1, speed: 1.5, alpha: 0.52, width: 1.5, waveBlend: 0.35 },
      { color: primaryColor, amp: midWithBeat * 0.48 + 0.08, freq: 2.2, speed: 1.1, alpha: 0.85, width: 2.4, waveBlend: 0.85 },
      { color: colorway.needleHex, amp: bassWithBeat * 0.52 + 0.08, freq: 1.5, speed: 0.85, alpha: 0.72, width: 2.5, waveBlend: 0.45 },
      { color: '#F8FAFC', amp: bands.overall * 0.32 + 0.05, freq: 4.0, speed: 1.9, alpha: 0.55, width: 1.3, waveBlend: 1.0 },
    ];

    const midY = height * 0.54;
    const steps = isFullscreen ? 72 : 42;
    const ampScale = reducedMotion ? height * 0.25 : height * 0.76;

    for (let l = 0; l < layers.length; l++) {
      const layer = layers[l];
      ctx.beginPath();

      for (let s = 0; s <= steps; s++) {
        const t = s / steps;
        const x = t * width;
        const envelope = Math.sin(t * Math.PI);
        const binSample = smoothedBins[Math.min(smoothedBins.length - 1, Math.floor(t * (smoothedBins.length - 1)))];

        let waveOffset = 0;
        if (hasLiveAudio && isPlaying && timeDomainData.length > 0) {
          const tdIdx = Math.min(timeDomainData.length - 1, Math.floor(t * (timeDomainData.length - 1)));
          // Blend genuine audio waveform displacement with harmonic bass/mid ribbon shape
          const realWave = timeDomainData[tdIdx] * layer.waveBlend * 0.65;
          const harmonic =
            Math.sin(t * Math.PI * layer.freq * 2 + phase * layer.speed + l * 1.1) *
            (layer.amp * 0.55 + binSample * 0.25);
          waveOffset = (realWave + harmonic) * envelope * ampScale * 0.5;
        } else {
          const wave =
            Math.sin(t * Math.PI * layer.freq * 2 + phase * layer.speed + l * 1.1) *
            (layer.amp + binSample * 0.28);
          waveOffset = wave * envelope * ampScale * 0.5;
        }

        const y = midY + waveOffset;
        if (s === 0) ctx.moveTo(x, y);
        else ctx.lineTo(x, y);
      }

      ctx.strokeStyle = layer.color;
      ctx.globalAlpha = layer.alpha;
      ctx.lineWidth = isFullscreen ? layer.width * 1.45 : layer.width;
      ctx.stroke();
    }
    ctx.globalAlpha = 1;
    return;
  }

  // ==========================================================
  // 5. COMAND APS NTG2 (Harman/Kardon Logic 7 Multi-Band EQ)
  // ==========================================================
  if (mode === 'comand') {
    const numCols = isFullscreen ? 28 : 18;
    const marginX = isFullscreen ? 28 : 12;
    const colGap = isFullscreen ? 6 : 3;
    const colW = Math.max(4, Math.floor((width - marginX * 2 - (numCols - 1) * colGap) / numCols));
    const totalW = numCols * (colW + colGap) - colGap;
    const startX = Math.floor((width - totalW) / 2);

    ctx.fillStyle = 'rgba(255, 255, 255, 0.38)';
    ctx.font = `700 ${isFullscreen ? 11 : 8}px ui-monospace, monospace`;
    ctx.textAlign = 'left';
    ctx.fillText('COMAND APS · HARMAN/KARDON LOGIC 7', startX, isFullscreen ? 22 : 14);

    ctx.textAlign = 'right';
    ctx.fillStyle = primaryColor;
    ctx.fillText('DSP 24-BIT', startX + totalW, isFullscreen ? 22 : 14);

    const meterTop = isFullscreen ? 34 : 22;
    const meterBottom = height - (isFullscreen ? 32 : 24);
    const meterH = Math.max(40, meterBottom - meterTop);

    ctx.strokeStyle = 'rgba(255, 255, 255, 0.08)';
    ctx.lineWidth = 1;
    for (const dm of [0.2, 0.4, 0.6, 0.8, 1.0]) {
      const gy = meterBottom - dm * meterH;
      ctx.beginPath();
      ctx.moveTo(startX, gy);
      ctx.lineTo(startX + totalW, gy);
      ctx.stroke();
    }

    const segStep = isFullscreen ? 6 : 4;
    const segH = isFullscreen ? 4 : 3;
    const numSegments = Math.max(6, Math.floor(meterH / segStep));
    const midThreshold = Math.floor(numSegments * 0.65);
    const highThreshold = Math.floor(numSegments * 0.85);

    // 1. Batched unlit background segments (1 draw call)
    ctx.fillStyle = 'rgba(255, 255, 255, 0.04)';
    ctx.beginPath();
    for (let i = 0; i < numCols; i++) {
      const val = isPlaying ? smoothedBins[i % smoothedBins.length] : 0.08 + Math.sin(phase * 0.8 + i * 0.3) * 0.02;
      const activeSegments = Math.floor(val * numSegments);
      const cx = startX + i * (colW + colGap);
      for (let s = activeSegments; s < numSegments; s++) {
        ctx.rect(cx, meterBottom - (s + 1) * segStep, colW, segH);
      }
    }
    ctx.fill();

    // 2. Batched primary zone segments (1 draw call)
    ctx.fillStyle = primaryColor;
    ctx.globalAlpha = 0.95;
    ctx.beginPath();
    for (let i = 0; i < numCols; i++) {
      const val = isPlaying ? smoothedBins[i % smoothedBins.length] : 0.08 + Math.sin(phase * 0.8 + i * 0.3) * 0.02;
      const activeSegments = Math.min(midThreshold, Math.floor(val * numSegments));
      const cx = startX + i * (colW + colGap);
      for (let s = 0; s < activeSegments; s++) {
        ctx.rect(cx, meterBottom - (s + 1) * segStep, colW, segH);
      }
    }
    ctx.fill();

    // 3. Batched amber/orange upper zone segments (1 draw call)
    ctx.fillStyle = colorway.needleHex;
    ctx.beginPath();
    for (let i = 0; i < numCols; i++) {
      const val = isPlaying ? smoothedBins[i % smoothedBins.length] : 0.08;
      const activeSegments = Math.min(highThreshold, Math.floor(val * numSegments));
      const cx = startX + i * (colW + colGap);
      for (let s = midThreshold; s < activeSegments; s++) {
        ctx.rect(cx, meterBottom - (s + 1) * segStep, colW, segH);
      }
    }
    ctx.fill();

    // 4. Batched redline segments + white peak holds (2 draw calls)
    ctx.fillStyle = '#EF4444';
    ctx.beginPath();
    for (let i = 0; i < numCols; i++) {
      const val = isPlaying ? smoothedBins[i % smoothedBins.length] : 0.08;
      const activeSegments = Math.floor(val * numSegments);
      const cx = startX + i * (colW + colGap);
      for (let s = highThreshold; s < activeSegments; s++) {
        ctx.rect(cx, meterBottom - (s + 1) * segStep, colW, segH);
      }
    }
    ctx.fill();

    ctx.fillStyle = '#FFFFFF';
    ctx.globalAlpha = 0.92;
    ctx.beginPath();
    for (let i = 0; i < numCols; i++) {
      const peakVal = peakHolds[i] || 0.08;
      const peakY = meterBottom - Math.floor(peakVal * meterH);
      const cx = startX + i * (colW + colGap);
      ctx.rect(cx, Math.max(meterTop, peakY - 2), colW, 2);
    }
    ctx.fill();

    ctx.globalAlpha = 1;
    ctx.fillStyle = 'rgba(255, 255, 255, 0.52)';
    ctx.font = `${isFullscreen ? 10 : 7}px ui-monospace, monospace`;
    ctx.textAlign = 'center';
    const labelY = height - (isFullscreen ? 12 : 10);
    ctx.fillText('31Hz', startX + colW / 2, labelY);
    ctx.fillText('250Hz', startX + Math.floor(totalW * 0.25), labelY);
    ctx.fillText('1kHz', startX + Math.floor(totalW * 0.5), labelY);
    ctx.fillText('4kHz', startX + Math.floor(totalW * 0.75), labelY);
    ctx.fillText('16kHz', startX + totalW - colW / 2, labelY);
    return;
  }

  // ==========================================================
  // 6. VDO AMBER DOT-MATRIX (Classic Cluster Phosphor Grid)
  // ==========================================================
  const dotSize = isFullscreen ? 8 : 3;
  const gap = isFullscreen ? 4 : 2;
  const cols = Math.max(12, Math.floor((width - 24) / (dotSize + gap)));
  const rows = Math.max(8, Math.floor((height - (isFullscreen ? 62 : 34)) / (dotSize + gap)));
  const gridW = cols * (dotSize + gap) - gap;
  const startX = Math.floor((width - gridW) / 2);
  const startY = isFullscreen ? 32 : 20;

  ctx.fillStyle = primaryColor;
  ctx.font = `700 ${isFullscreen ? 11 : 8}px ui-monospace, monospace`;
  ctx.textAlign = 'left';
  ctx.fillText('[ AUDIO ]', startX, isFullscreen ? 18 : 12);

  ctx.textAlign = 'right';
  ctx.fillText('MERCEDES-BENZ VDO', startX + gridW, isFullscreen ? 18 : 12);

  // Batch unlit matrix pixels (1 draw call)
  ctx.fillStyle = colorway.mfdBg;
  ctx.globalAlpha = 0.45;
  ctx.beginPath();
  for (let c = 0; c < cols; c++) {
    const binIdx = Math.floor((c / cols) * Math.min(32, smoothedBins.length));
    const energyRatio = isPlaying ? smoothedBins[binIdx] : 0.08 + Math.sin(phase * 0.7 + c * 0.3) * 0.02;
    const activeDots = Math.floor(energyRatio * rows);
    const dotX = startX + c * (dotSize + gap);
    for (let r = activeDots; r < rows; r++) {
      const dotY = startY + (rows - 1 - r) * (dotSize + gap);
      ctx.rect(dotX, dotY, dotSize, dotSize);
    }
  }
  ctx.fill();

  // Batch lit matrix pixels (1 draw call)
  ctx.fillStyle = primaryColor;
  ctx.globalAlpha = 0.94;
  ctx.beginPath();
  for (let c = 0; c < cols; c++) {
    const binIdx = Math.floor((c / cols) * Math.min(32, smoothedBins.length));
    const energyRatio = isPlaying ? smoothedBins[binIdx] : 0.08 + Math.sin(phase * 0.7 + c * 0.3) * 0.02;
    const activeDots = Math.floor(energyRatio * rows);
    const dotX = startX + c * (dotSize + gap);
    for (let r = 0; r < activeDots; r++) {
      const dotY = startY + (rows - 1 - r) * (dotSize + gap);
      ctx.rect(dotX, dotY, dotSize, dotSize);
    }
  }
  ctx.fill();

  ctx.globalAlpha = 1;
  ctx.font = `700 ${isFullscreen ? 10 : 7}px ui-monospace, monospace`;
  ctx.textAlign = 'center';
  const bottomY = height - (isFullscreen ? 10 : 4);
  const lamps = ['ESP', 'ABS', 'SRS', 'LIM', 'BAS'];
  const step = gridW / lamps.length;
  for (let l = 0; l < lamps.length; l++) {
    const lx = startX + step * (l + 0.5);
    ctx.fillStyle = lamps[l] === 'LIM' && isPlaying ? primaryColor : 'rgba(255, 255, 255, 0.28)';
    ctx.fillText(`[${lamps[l]}]`, lx, bottomY);
  }
}
