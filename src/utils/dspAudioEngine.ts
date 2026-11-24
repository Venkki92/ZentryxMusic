import {
  DSP_16_FREQUENCIES,
  COMPRESSOR_PROFILES,
  SURROUND_MODES,
  DspSettings,
  SurroundMatrixDiagnostics,
  SurroundModeId,
  buildSurroundMatrixDiagnostics,
  calculateLoudnessContourGains,
  calculateRoadNoiseContourDb,
  calculateCabinStageGains,
  calculateStereoWidthMatrix,
  dbToLinearGain,
  formatSurroundMatrixDiagnosticLog,
} from './dspConsole';
import { STEREO_SPECTRUM_FREQS_HZ, StereoSpectrumSnapshot } from './w203Cockpit';

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

  // Side (L-R) decorrelated ambient reflection path + Dolby PLII 90-deg all-pass + Virtual Atmos Height + 360° XTC
  private readonly sideLIn: GainNode;
  private readonly sideRIn: GainNode;
  private readonly sideBandpass: BiquadFilterNode;
  private readonly sideAllpassPhase: BiquadFilterNode;
  private readonly sideHaasDelayL: DelayNode;
  private readonly sideHaasDelayR: DelayNode;
  private readonly sideWetGainL: GainNode;
  private readonly sideWetGainR: GainNode;
  private readonly heightElevationNode: BiquadFilterNode;
  private readonly xtcDelayLToR: DelayNode;
  private readonly xtcDelayRToL: DelayNode;
  private readonly xtcGainLToR: GainNode;
  private readonly xtcGainRToL: GainNode;

  // Time Alignment + 2D Cabin Stage
  private readonly leftDelayNode: DelayNode;
  private readonly rightDelayNode: DelayNode;
  private readonly leftBalanceGain: GainNode;
  private readonly rightBalanceGain: GainNode;
  private readonly merger: ChannelMergerNode;

  private readonly compressorNode: DynamicsCompressorNode;
  private readonly peakLimiterShaper: WaveShaperNode;
  private readonly spectrumSplitter: ChannelSplitterNode;
  private readonly analyserLeft: AnalyserNode;
  private readonly analyserRight: AnalyserNode;
  private readonly freqBinsLeft: Float32Array<ArrayBuffer>;
  private readonly freqBinsRight: Float32Array<ArrayBuffer>;
  private readonly timeBinsLeft: Float32Array<ArrayBuffer>;
  private readonly timeBinsRight: Float32Array<ArrayBuffer>;
  private readonly smoothedLeftBands: Float32Array;
  private readonly smoothedRightBands: Float32Array;
  private lastCeilingDb: number = -999;
  private lastLoggedSurroundMode: SurroundModeId | null = null;
  public lastSurroundDiagnostics: SurroundMatrixDiagnostics | null = null;

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

    // Logic 7 / Dolby PLII / Atmos / Virtual 360 Side Haas Reflection path + 90-deg All-Pass Phase
    this.sideLIn = ctx.createGain();
    this.sideLIn.gain.value = 0.5;
    this.sideRIn = ctx.createGain();
    this.sideRIn.gain.value = -0.5;
    this.sideBandpass = ctx.createBiquadFilter();
    this.sideBandpass.type = 'bandpass';
    this.sideBandpass.frequency.value = 1600;
    this.sideBandpass.Q.value = 0.55;
    this.sideAllpassPhase = ctx.createBiquadFilter();
    this.sideAllpassPhase.type = 'allpass';
    this.sideAllpassPhase.frequency.value = 1100;
    this.sideAllpassPhase.Q.value = 0.707;
    this.sideHaasDelayL = ctx.createDelay(0.06);
    this.sideHaasDelayR = ctx.createDelay(0.06);
    this.sideHaasDelayL.delayTime.value = 0.014;
    this.sideHaasDelayR.delayTime.value = 0.021;
    this.sideWetGainL = ctx.createGain();
    this.sideWetGainR = ctx.createGain();
    this.sideWetGainL.gain.value = 0;
    this.sideWetGainR.gain.value = 0;

    // Dolby Virtual Atmos / 360° Overhead Pinna Elevation Filter (~7.2 kHz height cue lift)
    this.heightElevationNode = ctx.createBiquadFilter();
    this.heightElevationNode.type = 'peaking';
    this.heightElevationNode.frequency.value = 7200;
    this.heightElevationNode.Q.value = 0.85;
    this.heightElevationNode.gain.value = 0;

    // 360° Virtualizer Inter-Aural Cross-Talk Cancellation (0.55 ms ITD inverted cross-feed)
    this.xtcDelayLToR = ctx.createDelay(0.01);
    this.xtcDelayRToL = ctx.createDelay(0.01);
    this.xtcDelayLToR.delayTime.value = 0.00055;
    this.xtcDelayRToL.delayTime.value = 0.00055;
    this.xtcGainLToR = ctx.createGain();
    this.xtcGainRToL = ctx.createGain();
    this.xtcGainLToR.gain.value = 0;
    this.xtcGainRToL.gain.value = 0;

    this.leftDelayNode = ctx.createDelay(0.05);
    this.rightDelayNode = ctx.createDelay(0.05);
    this.leftBalanceGain = ctx.createGain();
    this.rightBalanceGain = ctx.createGain();
    this.merger = ctx.createChannelMerger(2);

    this.compressorNode = ctx.createDynamicsCompressor();
    this.peakLimiterShaper = ctx.createWaveShaper();
    this.peakLimiterShaper.oversample = '2x';
    this.output = ctx.createGain();

    // Post-surround L/R stereo spectrum analyzers (zero-allocation typed buffers)
    this.spectrumSplitter = ctx.createChannelSplitter(2);
    this.analyserLeft = ctx.createAnalyser();
    this.analyserRight = ctx.createAnalyser();
    this.analyserLeft.fftSize = 1024;
    this.analyserRight.fftSize = 1024;
    this.analyserLeft.smoothingTimeConstant = 0.72;
    this.analyserRight.smoothingTimeConstant = 0.72;
    const binCount = this.analyserLeft.frequencyBinCount;
    this.freqBinsLeft = new Float32Array(new ArrayBuffer(binCount * Float32Array.BYTES_PER_ELEMENT));
    this.freqBinsRight = new Float32Array(new ArrayBuffer(binCount * Float32Array.BYTES_PER_ELEMENT));
    this.timeBinsLeft = new Float32Array(new ArrayBuffer(this.analyserLeft.fftSize * Float32Array.BYTES_PER_ELEMENT));
    this.timeBinsRight = new Float32Array(new ArrayBuffer(this.analyserRight.fftSize * Float32Array.BYTES_PER_ELEMENT));
    this.smoothedLeftBands = new Float32Array(STEREO_SPECTRUM_FREQS_HZ.length);
    this.smoothedRightBands = new Float32Array(STEREO_SPECTRUM_FREQS_HZ.length);

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

    // Side Haas decorrelated ambience feed (with Dolby PLII 90-deg All-Pass Phase stage)
    this.splitter.connect(this.sideLIn, 0);
    this.splitter.connect(this.sideRIn, 1);
    this.sideLIn.connect(this.sideBandpass);
    this.sideRIn.connect(this.sideBandpass);
    this.sideBandpass.connect(this.sideAllpassPhase);
    this.sideAllpassPhase.connect(this.sideHaasDelayL);
    this.sideAllpassPhase.connect(this.sideHaasDelayR);
    this.sideHaasDelayL.connect(this.sideWetGainL);
    this.sideHaasDelayR.connect(this.sideWetGainR);
    this.sideWetGainL.connect(this.leftDelayNode);
    this.sideWetGainR.connect(this.rightDelayNode);

    // 360° Virtualizer Inter-Aural Cross-Talk Cancellation inverted cross-feed
    this.splitter.connect(this.xtcDelayLToR, 0);
    this.xtcDelayLToR.connect(this.xtcGainLToR);
    this.xtcGainLToR.connect(this.rightDelayNode);
    this.splitter.connect(this.xtcDelayRToL, 1);
    this.xtcDelayRToL.connect(this.xtcGainRToL);
    this.xtcGainRToL.connect(this.leftDelayNode);

    // Time alignment & balance -> merger -> Virtual Atmos Pinna Height Elevation -> Compressor -> Limiter
    this.leftDelayNode.connect(this.leftBalanceGain);
    this.leftBalanceGain.connect(this.merger, 0, 0);
    this.rightDelayNode.connect(this.rightBalanceGain);
    this.rightBalanceGain.connect(this.merger, 0, 1);

    this.merger.connect(this.heightElevationNode);
    this.heightElevationNode.connect(this.compressorNode);
    this.compressorNode.connect(this.peakLimiterShaper);
    this.peakLimiterShaper.connect(this.output);

    // Parallel non-intrusive tap for real-time L/R stereo frequency distribution debugger
    this.output.connect(this.spectrumSplitter);
    this.spectrumSplitter.connect(this.analyserLeft, 0);
    this.spectrumSplitter.connect(this.analyserRight, 1);
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

      const heightNorm = Math.max(0, Math.min(100, settings.virtualHeight ?? 45)) / 100;
      const heightDb = surroundSpec.heightBoostMaxDb * heightNorm;
      smoothSet(this.heightElevationNode.gain, heightDb, this.ctx);

      smoothSet(this.sideBandpass.frequency, surroundSpec.sideBandpassHz, this.ctx);
      smoothSet(this.sideBandpass.Q, surroundSpec.sideBandpassQ, this.ctx);
      smoothSet(this.sideAllpassPhase.frequency, surroundSpec.useAllPassPhase ? 1100 : 18000, this.ctx);

      const sideWet = surroundSpec.sideWetScale * intensityNorm;
      smoothSet(this.sideHaasDelayL.delayTime, surroundSpec.haasLeftMs / 1000, this.ctx);
      smoothSet(this.sideHaasDelayR.delayTime, surroundSpec.haasRightMs / 1000, this.ctx);
      smoothSet(this.sideWetGainL.gain, sideWet, this.ctx);
      smoothSet(this.sideWetGainR.gain, -sideWet, this.ctx);

      const xtcNeg = -(surroundSpec.crosstalkCancelGain * intensityNorm);
      smoothSet(this.xtcGainLToR.gain, xtcNeg, this.ctx);
      smoothSet(this.xtcGainRToL.gain, xtcNeg, this.ctx);
    } else {
      smoothSet(this.centerCoherenceWetGain.gain, 0, this.ctx);
      smoothSet(this.heightElevationNode.gain, 0, this.ctx);
      smoothSet(this.sideWetGainL.gain, 0, this.ctx);
      smoothSet(this.sideWetGainR.gain, 0, this.ctx);
      smoothSet(this.xtcGainLToR.gain, 0, this.ctx);
      smoothSet(this.xtcGainRToL.gain, 0, this.ctx);
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

    const nextMode: SurroundModeId = settings.surroundMode ?? 'off';
    if (this.lastLoggedSurroundMode !== null && this.lastLoggedSurroundMode !== nextMode) {
      this.logSurroundMatrixDiagnostics(`HeadunitDspEngine.applySettings(${nextMode})`, settings);
    }
    this.lastLoggedSurroundMode = nextMode;
  }

  logSurroundMatrixDiagnostics(trigger: string, settings: DspSettings): SurroundMatrixDiagnostics {
    const diag = buildSurroundMatrixDiagnostics(trigger, settings, this.ctx.sampleRate || 48000);
    this.lastSurroundDiagnostics = diag;
    console.info(formatSurroundMatrixDiagnosticLog(diag));
    return diag;
  }

  getStereoSpectrumSnapshot(): StereoSpectrumSnapshot {
    const sampleRate = this.ctx.sampleRate || 48000;
    const nyquist = sampleRate / 2;
    const binCount = this.analyserLeft.frequencyBinCount;

    this.analyserLeft.getFloatFrequencyData(this.freqBinsLeft);
    this.analyserRight.getFloatFrequencyData(this.freqBinsRight);

    const leftBands: number[] = new Array(STEREO_SPECTRUM_FREQS_HZ.length);
    const rightBands: number[] = new Array(STEREO_SPECTRUM_FREQS_HZ.length);

    let peakBandEnergy = 0;
    for (let i = 0; i < STEREO_SPECTRUM_FREQS_HZ.length; i++) {
      const centerHz = STEREO_SPECTRUM_FREQS_HZ[i];
      const prevHz = i === 0 ? centerHz * 0.72 : Math.sqrt(STEREO_SPECTRUM_FREQS_HZ[i - 1] * centerHz);
      const nextHz =
        i === STEREO_SPECTRUM_FREQS_HZ.length - 1
          ? Math.min(nyquist, centerHz * 1.28)
          : Math.sqrt(centerHz * STEREO_SPECTRUM_FREQS_HZ[i + 1]);

      const startBin = Math.max(1, Math.floor((prevHz / nyquist) * binCount));
      const endBin = Math.min(binCount - 1, Math.max(startBin, Math.ceil((nextHz / nyquist) * binCount)));

      let maxDbL = -100;
      let maxDbR = -100;
      for (let b = startBin; b <= endBin; b++) {
        const dbL = this.freqBinsLeft[b];
        const dbR = this.freqBinsRight[b];
        if (Number.isFinite(dbL) && dbL > maxDbL) maxDbL = dbL;
        if (Number.isFinite(dbR) && dbR > maxDbR) maxDbR = dbR;
      }

      const normL = Math.max(0, Math.min(1, (maxDbL + 84) / 72));
      const normR = Math.max(0, Math.min(1, (maxDbR + 84) / 72));

      this.smoothedLeftBands[i] = this.smoothedLeftBands[i] * 0.45 + normL * 0.55;
      this.smoothedRightBands[i] = this.smoothedRightBands[i] * 0.45 + normR * 0.55;
      leftBands[i] = this.smoothedLeftBands[i];
      rightBands[i] = this.smoothedRightBands[i];
      if (leftBands[i] > peakBandEnergy) peakBandEnergy = leftBands[i];
      if (rightBands[i] > peakBandEnergy) peakBandEnergy = rightBands[i];
    }

    let midSqSum = 0;
    let sideSqSum = 0;
    let timeSamples = 0;
    if (
      typeof this.analyserLeft.getFloatTimeDomainData === 'function' &&
      typeof this.analyserRight.getFloatTimeDomainData === 'function'
    ) {
      this.analyserLeft.getFloatTimeDomainData(this.timeBinsLeft);
      this.analyserRight.getFloatTimeDomainData(this.timeBinsRight);
      const len = Math.min(this.timeBinsLeft.length, this.timeBinsRight.length);
      for (let i = 0; i < len; i++) {
        const l = this.timeBinsLeft[i];
        const r = this.timeBinsRight[i];
        const mid = 0.5 * (l + r);
        const side = 0.5 * (l - r);
        midSqSum += mid * mid;
        sideSqSum += side * side;
      }
      timeSamples = len;
    }

    const midRms = timeSamples > 0 ? Math.sqrt(midSqSum / timeSamples) : 0;
    const sideRms = timeSamples > 0 ? Math.sqrt(sideSqSum / timeSamples) : 0;
    const isActive = midRms + sideRms > 0.0015 || peakBandEnergy > 0.04;
    const stereoSeparationPercent = isActive
      ? Math.max(0, Math.min(100, (sideRms / (midRms + sideRms + 1e-6)) * 200))
      : 0;

    return {
      leftBands,
      rightBands,
      midRms,
      sideRms,
      stereoSeparationPercent,
      isActive,
      sampleRateHz: sampleRate,
    };
  }
}
