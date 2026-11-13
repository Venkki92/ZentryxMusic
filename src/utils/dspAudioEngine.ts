import {
  DSP_16_FREQUENCIES,
  COMPRESSOR_PROFILES,
  DspSettings,
  calculateLoudnessContourGains,
  calculateBalanceGains,
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

export class HeadunitDspEngine {
  readonly ctx: AudioContext;
  readonly input: GainNode;
  readonly output: GainNode;

  private readonly preampNode: GainNode;
  private readonly hpfNode: BiquadFilterNode;
  private readonly bassBoostNode: BiquadFilterNode;
  private readonly eqFilters: BiquadFilterNode[];
  private readonly loudnessLowNode: BiquadFilterNode;
  private readonly loudnessHighNode: BiquadFilterNode;

  private readonly splitter: ChannelSplitterNode;
  private readonly directLeftGain: GainNode;
  private readonly crossLeftGain: GainNode;
  private readonly directRightGain: GainNode;
  private readonly crossRightGain: GainNode;
  private readonly leftDelayNode: DelayNode;
  private readonly rightDelayNode: DelayNode;
  private readonly leftBalanceGain: GainNode;
  private readonly rightBalanceGain: GainNode;
  private readonly merger: ChannelMergerNode;

  private readonly compressorNode: DynamicsCompressorNode;

  constructor(ctx: AudioContext) {
    this.ctx = ctx;

    this.input = ctx.createGain();
    this.input.channelCount = 2;
    this.input.channelCountMode = 'explicit';

    this.preampNode = ctx.createGain();

    this.hpfNode = ctx.createBiquadFilter();
    this.hpfNode.type = 'allpass';
    this.hpfNode.frequency.value = 80;
    this.hpfNode.Q.value = 0.707;

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

    this.splitter = ctx.createChannelSplitter(2);
    this.directLeftGain = ctx.createGain();
    this.crossLeftGain = ctx.createGain();
    this.directRightGain = ctx.createGain();
    this.crossRightGain = ctx.createGain();

    this.directLeftGain.gain.value = 1;
    this.crossLeftGain.gain.value = 0;
    this.directRightGain.gain.value = 1;
    this.crossRightGain.gain.value = 0;

    this.leftDelayNode = ctx.createDelay(0.05);
    this.rightDelayNode = ctx.createDelay(0.05);
    this.leftBalanceGain = ctx.createGain();
    this.rightBalanceGain = ctx.createGain();
    this.merger = ctx.createChannelMerger(2);

    this.compressorNode = ctx.createDynamicsCompressor();
    this.output = ctx.createGain();

    // Wire serial front-end filtering chain
    let cursor: AudioNode = this.input;
    cursor.connect(this.preampNode);
    cursor = this.preampNode;
    cursor.connect(this.hpfNode);
    cursor = this.hpfNode;
    cursor.connect(this.bassBoostNode);
    cursor = this.bassBoostNode;

    for (const eqNode of this.eqFilters) {
      cursor.connect(eqNode);
      cursor = eqNode;
    }

    cursor.connect(this.loudnessLowNode);
    this.loudnessLowNode.connect(this.loudnessHighNode);

    // Wire Mid/Side stereo width matrix + per-channel Time Alignment + L/R Balance
    this.loudnessHighNode.connect(this.splitter);

    this.splitter.connect(this.directLeftGain, 0);
    this.splitter.connect(this.crossLeftGain, 1);
    this.directLeftGain.connect(this.leftDelayNode);
    this.crossLeftGain.connect(this.leftDelayNode);
    this.leftDelayNode.connect(this.leftBalanceGain);
    this.leftBalanceGain.connect(this.merger, 0, 0);

    this.splitter.connect(this.directRightGain, 1);
    this.splitter.connect(this.crossRightGain, 0);
    this.directRightGain.connect(this.rightDelayNode);
    this.crossRightGain.connect(this.rightDelayNode);
    this.rightDelayNode.connect(this.rightBalanceGain);
    this.rightBalanceGain.connect(this.merger, 0, 1);

    // Final stage: Dynamics Compressor -> Output
    this.merger.connect(this.compressorNode);
    this.compressorNode.connect(this.output);
  }

  applySettings(settings: DspSettings, masterVolumePercent: number): void {
    const active = settings.dspEnabled;

    // 1. Zero-Clip Preamp Trim
    const preampGain = active ? dbToLinearGain(settings.preampDb) : 1;
    smoothSet(this.preampNode.gain, preampGain, this.ctx);

    // 2. Door-Speaker High-Pass Filter Crossover
    if (active && settings.hpfCutoffHz > 0) {
      this.hpfNode.type = 'highpass';
      smoothSet(this.hpfNode.frequency, settings.hpfCutoffHz, this.ctx);
      smoothSet(this.hpfNode.Q, 0.707, this.ctx);
    } else {
      this.hpfNode.type = 'allpass';
      smoothSet(this.hpfNode.frequency, 80, this.ctx);
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

    // 6. 3D Stereo Width Mid/Side Matrix
    const matrix = active
      ? calculateStereoWidthMatrix(settings.stereoWidthPercent)
      : { directGain: 1, crossGain: 0 };
    smoothSet(this.directLeftGain.gain, matrix.directGain, this.ctx);
    smoothSet(this.crossLeftGain.gain, matrix.crossGain, this.ctx);
    smoothSet(this.directRightGain.gain, matrix.directGain, this.ctx);
    smoothSet(this.crossRightGain.gain, matrix.crossGain, this.ctx);

    // 7. Cabin Time Alignment (L/R Delays in seconds)
    const leftDelaySec = active ? settings.leftDelayMs / 1000 : 0;
    const rightDelaySec = active ? settings.rightDelayMs / 1000 : 0;
    smoothSet(this.leftDelayNode.delayTime, leftDelaySec, this.ctx);
    smoothSet(this.rightDelayNode.delayTime, rightDelaySec, this.ctx);

    // 8. L/R Stereo Balance (always active so user balance remains intact)
    const bal = calculateBalanceGains(settings.balance);
    smoothSet(this.leftBalanceGain.gain, bal.leftGain, this.ctx);
    smoothSet(this.rightBalanceGain.gain, bal.rightGain, this.ctx);

    // 9. Dynamics Compressor Limiter
    const compProfile = active
      ? COMPRESSOR_PROFILES[settings.compressorProfile]
      : COMPRESSOR_PROFILES.off;
    smoothSet(this.compressorNode.threshold, compProfile.threshold, this.ctx);
    smoothSet(this.compressorNode.knee, compProfile.knee, this.ctx);
    smoothSet(this.compressorNode.ratio, compProfile.ratio, this.ctx);
    smoothSet(this.compressorNode.attack, compProfile.attack, this.ctx);
    smoothSet(this.compressorNode.release, compProfile.release, this.ctx);
  }
}
