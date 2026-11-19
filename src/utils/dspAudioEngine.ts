import {
  DSP_16_FREQUENCIES,
  COMPRESSOR_PROFILES,
  SURROUND_MODES,
  DspSettings,
  calculateLoudnessContourGains,
  calculateRoadNoiseContourDb,
  calculateCabinStageGains,
  calculateStereoWidthMatrix,
  dbToLinearGain,
} from './dspConsole';

function smoothSet(param: AudioParam, value: number, ctx: AudioContext): void {
  const safeVal = Number.isFinite(value) ? value : 0;
  if (typeof param.setTargetAtTime === 'function') {
    param.setTargetAtTime(safeVal, ctx.currentTime, 0.02);
  } else {
    param.value = safeVal;
  }
}

function createCeilingCurve(ceilingLinear: number): Float32Array<ArrayBuffer> {
  const samples = 2048;
  const curve = new Float32Array(new ArrayBuffer(samples * Float32Array.BYTES_PER_ELEMENT));
  for (let i = 0; i < samples; i++) {
    const x = (i * 2) / (samples - 1) - 1;
    curve[i] = Math.max(-ceilingLinear, Math.min(ceilingLinear, x));
  }
  return curve;
}

export class HeadunitDspEngine {
  readonly ctx: AudioContext;
  readonly input: GainNode;
  readonly output: GainNode;

  private readonly preampNode: GainNode;
  private readonly hpfNode1: BiquadFilterNode;
  private readonly hpfNode2: BiquadFilterNode;
  private readonly bassBoostNode: BiquadFilterNode;
  private readonly eqFilters: BiquadFilterNode[];
  private readonly loudnessLowNode: BiquadFilterNode;
  private readonly loudnessHighNode: BiquadFilterNode;
  private readonly roadNoiseLowNode: BiquadFilterNode;
  private readonly roadNoisePresenceNode: BiquadFilterNode;
  private readonly faderWarmthNode: BiquadFilterNode;

  // Sub-band LPF crossover (12 or 24 dB/oct Linkwitz-Riley) + trim + 180-deg phase + sub delay
  private readonly subSumNode: GainNode;
  private readonly subDryGain: GainNode;
  private readonly subExtractFilter1: BiquadFilterNode;
  private readonly subExtractFilter2: BiquadFilterNode;
  private readonly subSubtractGain: GainNode;
  private readonly subDelayNode: DelayNode;
  private readonly subWetGain: GainNode;

  // Harman Logic 7 / Concert Hall / Studio Surround Matrix (Mid center coherence + Side Haas reflection)
  private readonly splitter: ChannelSplitterNode;
  private readonly directLeftGain: GainNode;
  private readonly crossLeftGain: GainNode;
  private readonly directRightGain: GainNode;
  private readonly crossRightGain: GainNode;

  // Mid (L+R) vocal coherence injection path
  private readonly midLIn: GainNode;
  private readonly midRIn: GainNode;
  private readonly centerCoherenceFilter: BiquadFilterNode;
  private readonly centerCoherenceWetGain: GainNode;

  // Side (L-R) decorrelated ambient reflection path
  private readonly sideLIn: GainNode;
  private readonly sideRIn: GainNode;
  private readonly sideBandpass: BiquadFilterNode;
  private readonly sideHaasDelayL: DelayNode;
  private readonly sideHaasDelayR: DelayNode;
  private readonly sideWetGainL: GainNode;
  private readonly sideWetGainR: GainNode;

  // Time Alignment + 2D Cabin Stage
  private readonly leftDelayNode: DelayNode;
  private readonly rightDelayNode: DelayNode;
  private readonly leftBalanceGain: GainNode;
  private readonly rightBalanceGain: GainNode;
  private readonly merger: ChannelMergerNode;

  private readonly compressorNode: DynamicsCompressorNode;
  private readonly peakLimiterShaper: WaveShaperNode;
  private lastCeilingDb: number = -999;

  constructor(ctx: AudioContext) {
    this.ctx = ctx;

    this.input = ctx.createGain();
    this.input.channelCount = 2;
    this.input.channelCountMode = 'explicit';

    this.preampNode = ctx.createGain();

    this.hpfNode1 = ctx.createBiquadFilter();
    this.hpfNode1.type = 'allpass';
    this.hpfNode1.frequency.value = 80;
    this.hpfNode1.Q.value = 0.707;

    this.hpfNode2 = ctx.createBiquadFilter();
    this.hpfNode2.type = 'allpass';
    this.hpfNode2.frequency.value = 80;
    this.hpfNode2.Q.value = 0.707;

    this.bassBoostNode = ctx.createBiquadFilter();
    this.bassBoostNode.type = 'lowshelf';
    this.bassBoostNode.frequency.value = 65;
    this.bassBoostNode.gain.value = 0;

    this.eqFilters = DSP_16_FREQUENCIES.map((freq) => {
      const f = ctx.createBiquadFilter();
      f.type = 'peaking';
      f.frequency.value = freq;
      f.Q.value = 1.85;
      f.gain.value = 0;
      return f;
    });

    this.loudnessLowNode = ctx.createBiquadFilter();
    this.loudnessLowNode.type = 'lowshelf';
    this.loudnessLowNode.frequency.value = 95;
    this.loudnessLowNode.gain.value = 0;

    this.loudnessHighNode = ctx.createBiquadFilter();
    this.loudnessHighNode.type = 'highshelf';
    this.loudnessHighNode.frequency.value = 9000;
    this.loudnessHighNode.gain.value = 0;

    this.roadNoiseLowNode = ctx.createBiquadFilter();
    this.roadNoiseLowNode.type = 'lowshelf';
    this.roadNoiseLowNode.frequency.value = 115;
    this.roadNoiseLowNode.gain.value = 0;

    this.roadNoisePresenceNode = ctx.createBiquadFilter();
    this.roadNoisePresenceNode.type = 'peaking';
    this.roadNoisePresenceNode.frequency.value = 2800;
    this.roadNoisePresenceNode.Q.value = 1.1;
    this.roadNoisePresenceNode.gain.value = 0;

    this.faderWarmthNode = ctx.createBiquadFilter();
    this.faderWarmthNode.type = 'highshelf';
    this.faderWarmthNode.frequency.value = 3500;
    this.faderWarmthNode.gain.value = 0;

    this.subDryGain = ctx.createGain();
    this.subDryGain.gain.value = 1;
    this.subExtractFilter1 = ctx.createBiquadFilter();
    this.subExtractFilter1.type = 'lowpass';
    this.subExtractFilter1.frequency.value = 100;
    this.subExtractFilter1.Q.value = 0.707;
    this.subExtractFilter2 = ctx.createBiquadFilter();
    this.subExtractFilter2.type = 'allpass';
    this.subExtractFilter2.frequency.value = 100;
    this.subExtractFilter2.Q.value = 0.707;
    this.subSubtractGain = ctx.createGain();
    this.subSubtractGain.gain.value = 0;
    this.subDelayNode = ctx.createDelay(0.05);
    this.subDelayNode.delayTime.value = 0;
    this.subWetGain = ctx.createGain();
    this.subWetGain.gain.value = 0;
    this.subSumNode = ctx.createGain();

    this.splitter = ctx.createChannelSplitter(2);
    this.directLeftGain = ctx.createGain();
    this.crossLeftGain = ctx.createGain();
    this.directRightGain = ctx.createGain();
    this.crossRightGain = ctx.createGain();

    this.directLeftGain.gain.value = 1;
    this.crossLeftGain.gain.value = 0;
    this.directRightGain.gain.value = 1;
    this.crossRightGain.gain.value = 0;

    // Logic 7 Center Vocal Coherence path (Mid = 0.5*(L+R) -> Bandpass @ 2.2kHz -> subtle dry add)
    this.midLIn = ctx.createGain();
    this.midLIn.gain.value = 0.5;
    this.midRIn = ctx.createGain();
    this.midRIn.gain.value = 0.5;
    this.centerCoherenceFilter = ctx.createBiquadFilter();
    this.centerCoherenceFilter.type = 'bandpass';
    this.centerCoherenceFilter.frequency.value = 2200;
    this.centerCoherenceFilter.Q.value = 0.9;
    this.centerCoherenceWetGain = ctx.createGain();
    this.centerCoherenceWetGain.gain.value = 0;

    // Logic 7 / Concert Hall Side Haas Reflection path (Side = 0.5*(L-R) -> Bandpass -> Haas Delay -> +/- L/R)
    this.sideLIn = ctx.createGain();
    this.sideLIn.gain.value = 0.5;
    this.sideRIn = ctx.createGain();
    this.sideRIn.gain.value = -0.5;
    this.sideBandpass = ctx.createBiquadFilter();
    this.sideBandpass.type = 'bandpass';
    this.sideBandpass.frequency.value = 1600;
    this.sideBandpass.Q.value = 0.55;
    this.sideHaasDelayL = ctx.createDelay(0.06);
    this.sideHaasDelayR = ctx.createDelay(0.06);
    this.sideHaasDelayL.delayTime.value = 0.014;
    this.sideHaasDelayR.delayTime.value = 0.021;
    this.sideWetGainL = ctx.createGain();
    this.sideWetGainR = ctx.createGain();
    this.sideWetGainL.gain.value = 0;
    this.sideWetGainR.gain.value = 0;

    this.leftDelayNode = ctx.createDelay(0.05);
    this.rightDelayNode = ctx.createDelay(0.05);
    this.leftBalanceGain = ctx.createGain();
    this.rightBalanceGain = ctx.createGain();
    this.merger = ctx.createChannelMerger(2);

    this.compressorNode = ctx.createDynamicsCompressor();
    this.peakLimiterShaper = ctx.createWaveShaper();
    this.peakLimiterShaper.oversample = '2x';
    this.output = ctx.createGain();

    // Wire serial front-end filtering chain
    let cursor: AudioNode = this.input;
    cursor.connect(this.preampNode);
    cursor = this.preampNode;
    cursor.connect(this.hpfNode1);
    cursor = this.hpfNode1;
    cursor.connect(this.hpfNode2);
    cursor = this.hpfNode2;
    cursor.connect(this.bassBoostNode);
    cursor = this.bassBoostNode;

    for (const eqNode of this.eqFilters) {
      cursor.connect(eqNode);
      cursor = eqNode;
    }

    cursor.connect(this.loudnessLowNode);
    this.loudnessLowNode.connect(this.loudnessHighNode);
    this.loudnessHighNode.connect(this.roadNoiseLowNode);
    this.roadNoiseLowNode.connect(this.roadNoisePresenceNode);
    this.roadNoisePresenceNode.connect(this.faderWarmthNode);

    // Wire Sub-Band LPF (12/24 dB/oct) + Trim + 180-deg Phase Inversion + Sub Delay parallel network
    this.faderWarmthNode.connect(this.subDryGain);
    this.subDryGain.connect(this.subSumNode);

    this.faderWarmthNode.connect(this.subExtractFilter1);
    this.subExtractFilter1.connect(this.subExtractFilter2);
    this.subExtractFilter2.connect(this.subSubtractGain);
    this.subSubtractGain.connect(this.subSumNode);
    this.subExtractFilter2.connect(this.subDelayNode);
    this.subDelayNode.connect(this.subWetGain);
    this.subWetGain.connect(this.subSumNode);

    // Wire Mid/Side stereo width + Logic 7 Matrix + Time Alignment + 2D Cabin Stage
    this.subSumNode.connect(this.splitter);

    this.splitter.connect(this.directLeftGain, 0);
    this.splitter.connect(this.crossLeftGain, 1);
    this.directLeftGain.connect(this.leftDelayNode);
    this.crossLeftGain.connect(this.leftDelayNode);

    this.splitter.connect(this.directRightGain, 1);
    this.splitter.connect(this.crossRightGain, 0);
    this.directRightGain.connect(this.rightDelayNode);
    this.crossRightGain.connect(this.rightDelayNode);

    // Logic 7 Center Vocal Coherence feed into both L & R before time alignment
    this.splitter.connect(this.midLIn, 0);
    this.splitter.connect(this.midRIn, 1);
    this.midLIn.connect(this.centerCoherenceFilter);
    this.midRIn.connect(this.centerCoherenceFilter);
    this.centerCoherenceFilter.connect(this.centerCoherenceWetGain);
    this.centerCoherenceWetGain.connect(this.leftDelayNode);
    this.centerCoherenceWetGain.connect(this.rightDelayNode);

    // Logic 7 Side Haas decorrelated ambience feed into L & R
    this.splitter.connect(this.sideLIn, 0);
    this.splitter.connect(this.sideRIn, 1);
    this.sideLIn.connect(this.sideBandpass);
    this.sideRIn.connect(this.sideBandpass);
    this.sideBandpass.connect(this.sideHaasDelayL);
    this.sideBandpass.connect(this.sideHaasDelayR);
    this.sideHaasDelayL.connect(this.sideWetGainL);
    this.sideHaasDelayR.connect(this.sideWetGainR);
    this.sideWetGainL.connect(this.leftDelayNode);
    this.sideWetGainR.connect(this.rightDelayNode);

    // Time alignment & balance -> merger
    this.leftDelayNode.connect(this.leftBalanceGain);
    this.leftBalanceGain.connect(this.merger, 0, 0);
    this.rightDelayNode.connect(this.rightBalanceGain);
    this.rightBalanceGain.connect(this.merger, 0, 1);

    // Final stage: Dynamics Compressor -> Brickwall Peak Limiter Ceiling -> Output
    this.merger.connect(this.compressorNode);
    this.compressorNode.connect(this.peakLimiterShaper);
    this.peakLimiterShaper.connect(this.output);
  }

  applySettings(settings: DspSettings, masterVolumePercent: number, speedKmH = 0): void {
    const active = settings.dspEnabled;

    // 1. Zero-Clip Preamp Trim
    const preampGain = active ? dbToLinearGain(settings.preampDb) : 1;
    smoothSet(this.preampNode.gain, preampGain, this.ctx);

    // 2. Door-Speaker High-Pass Filter Crossover (12 dB/oct or 24 dB/oct Linkwitz-Riley cascade)
    const is24Db = settings.crossoverSlopeDb === 24;
    if (active && settings.hpfCutoffHz > 0) {
      this.hpfNode1.type = 'highpass';
      smoothSet(this.hpfNode1.frequency, settings.hpfCutoffHz, this.ctx);
      smoothSet(this.hpfNode1.Q, 0.707, this.ctx);

      if (is24Db) {
        this.hpfNode2.type = 'highpass';
        smoothSet(this.hpfNode2.frequency, settings.hpfCutoffHz, this.ctx);
        smoothSet(this.hpfNode2.Q, 0.707, this.ctx);
      } else {
        this.hpfNode2.type = 'allpass';
        smoothSet(this.hpfNode2.frequency, settings.hpfCutoffHz, this.ctx);
      }
    } else {
      this.hpfNode1.type = 'allpass';
      this.hpfNode2.type = 'allpass';
      smoothSet(this.hpfNode1.frequency, 80, this.ctx);
      smoothSet(this.hpfNode2.frequency, 80, this.ctx);
    }

    // 3. Dedicated Bass Boost Low-Shelf
    smoothSet(this.bassBoostNode.frequency, settings.bassBoostFreqHz, this.ctx);
    smoothSet(this.bassBoostNode.gain, active ? settings.bassBoostDb : 0, this.ctx);

    // 4. 16-Band Parametric Peaking EQ
    for (let i = 0; i < this.eqFilters.length; i++) {
      const gainDb = active ? (settings.bands16[i] ?? 0) : 0;
      smoothSet(this.eqFilters[i].gain, gainDb, this.ctx);
    }

    // 5. Dynamic Fletcher-Munson Loudness Contour
    const loudness = calculateLoudnessContourGains(
      active && settings.loudnessContour,
      masterVolumePercent
    );
    smoothSet(this.loudnessLowNode.gain, loudness.lowShelfDb, this.ctx);
    smoothSet(this.loudnessHighNode.gain, loudness.highShelfDb, this.ctx);

    // 5b. Road-Noise Acoustic Contour (Speed-Sensitive Spectral Tilt)
    const roadContour = calculateRoadNoiseContourDb(speedKmH, active && Boolean(settings.roadNoiseEqEnabled));
    smoothSet(this.roadNoiseLowNode.gain, roadContour.subBassDb, this.ctx);
    smoothSet(this.roadNoisePresenceNode.gain, roadContour.vocalPresenceDb, this.ctx);

    // 6. Sub LPF Trim (12/24 dB/oct) + 0/180-deg Phase Inversion + Sub Delay Alignment
    const subDelayMs = Math.max(0, Math.min(15, settings.subDelayMs ?? 0));
    const useSubStage =
      active &&
      (settings.subPhaseInverted ||
        settings.subLpfHz > 0 ||
        Math.abs(settings.subTrimDb) >= 0.1 ||
        subDelayMs >= 0.25);
    const subCutoff = settings.subLpfHz > 0 ? settings.subLpfHz : 100;
    smoothSet(this.subExtractFilter1.frequency, subCutoff, this.ctx);
    this.subExtractFilter2.type = is24Db ? 'lowpass' : 'allpass';
    smoothSet(this.subExtractFilter2.frequency, subCutoff, this.ctx);
    smoothSet(this.subDelayNode.delayTime, useSubStage ? subDelayMs / 1000 : 0, this.ctx);

    if (useSubStage) {
      const trimLin = dbToLinearGain(Math.max(-6, Math.min(6, settings.subTrimDb)));
      smoothSet(this.subSubtractGain.gain, -1, this.ctx);
      smoothSet(this.subWetGain.gain, settings.subPhaseInverted ? -trimLin : trimLin, this.ctx);
    } else {
      smoothSet(this.subSubtractGain.gain, 0, this.ctx);
      smoothSet(this.subWetGain.gain, 0, this.ctx);
    }

    // 7. 3D Stereo Width + Harman Logic 7 / Surround Matrix (auto-bypassed in Mono Fold-Down)
    const surroundSpec = SURROUND_MODES[settings.surroundMode ?? 'off'] ?? SURROUND_MODES.off;
    const surroundActive = active && !settings.monoFoldDown && settings.surroundMode !== 'off';
    const intensityNorm = Math.max(0, Math.min(100, settings.surroundIntensity ?? 65)) / 100;
    const coherenceNorm = Math.max(0, Math.min(100, settings.centerCoherence ?? 55)) / 100;

    // Blend stereo width with surround mode's target spaciousness
    const effectiveWidth = settings.monoFoldDown
      ? 0
      : surroundActive
      ? Math.min(150, settings.stereoWidthPercent + (surroundSpec.sideWidthTarget - 100) * intensityNorm)
      : settings.stereoWidthPercent;

    const matrix = active
      ? calculateStereoWidthMatrix(effectiveWidth)
      : { directGain: 1, crossGain: 0 };
    smoothSet(this.directLeftGain.gain, matrix.directGain, this.ctx);
    smoothSet(this.crossLeftGain.gain, matrix.crossGain, this.ctx);
    smoothSet(this.directRightGain.gain, matrix.directGain, this.ctx);
    smoothSet(this.crossRightGain.gain, matrix.crossGain, this.ctx);

    if (surroundActive) {
      const centerLinearAdd = (dbToLinearGain(surroundSpec.centerBoostMaxDb * coherenceNorm) - 1) * 0.7;
      smoothSet(this.centerCoherenceWetGain.gain, Math.max(0, Math.min(0.45, centerLinearAdd)), this.ctx);

      const sideWet = surroundSpec.sideWetScale * intensityNorm;
      smoothSet(this.sideHaasDelayL.delayTime, surroundSpec.haasLeftMs / 1000, this.ctx);
      smoothSet(this.sideHaasDelayR.delayTime, surroundSpec.haasRightMs / 1000, this.ctx);
      smoothSet(this.sideWetGainL.gain, sideWet, this.ctx);
      smoothSet(this.sideWetGainR.gain, -sideWet, this.ctx);
    } else {
      smoothSet(this.centerCoherenceWetGain.gain, 0, this.ctx);
      smoothSet(this.sideWetGainL.gain, 0, this.ctx);
      smoothSet(this.sideWetGainR.gain, 0, this.ctx);
    }

    // 8. Cabin Time Alignment (L/R Delays in seconds)
    const leftDelaySec = active ? settings.leftDelayMs / 1000 : 0;
    const rightDelaySec = active ? settings.rightDelayMs / 1000 : 0;
    smoothSet(this.leftDelayNode.delayTime, leftDelaySec, this.ctx);
    smoothSet(this.rightDelayNode.delayTime, rightDelaySec, this.ctx);

    // 9. 2D Cabin Stage (L/R Balance + Front/Rear Fader contour)
    const stageGains = calculateCabinStageGains(settings.balance, settings.fader ?? 0);
    smoothSet(this.leftBalanceGain.gain, stageGains.leftGain, this.ctx);
    smoothSet(this.rightBalanceGain.gain, stageGains.rightGain, this.ctx);
    smoothSet(this.faderWarmthNode.gain, active ? stageGains.faderHighShelfDb : 0, this.ctx);

    // 10. Dynamics Compressor & Master Brickwall Peak Limiter Ceiling
    const compProfile = active
      ? COMPRESSOR_PROFILES[settings.compressorProfile]
      : COMPRESSOR_PROFILES.off;
    smoothSet(this.compressorNode.threshold, compProfile.threshold, this.ctx);
    smoothSet(this.compressorNode.knee, compProfile.knee, this.ctx);
    smoothSet(this.compressorNode.ratio, compProfile.ratio, this.ctx);
    smoothSet(this.compressorNode.attack, compProfile.attack, this.ctx);
    smoothSet(this.compressorNode.release, compProfile.release, this.ctx);

    const targetCeilingDb = active ? (settings.peakLimiterCeilingDb ?? -0.5) : 0;
    if (this.lastCeilingDb !== targetCeilingDb) {
      this.lastCeilingDb = targetCeilingDb;
      this.peakLimiterShaper.curve =
        targetCeilingDb < -0.05 ? createCeilingCurve(dbToLinearGain(targetCeilingDb)) : null;
    }
  }
}
