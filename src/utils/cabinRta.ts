import {
  DSP_16_FREQUENCIES,
  DspBandGains16,
  computeCabinRtaCorrections,
} from './dspConsole';

export interface CabinRtaSweepResult {
  source: 'microphone' | 'loopback';
  measuredDb: DspBandGains16;
  suggestedCorrections: DspBandGains16;
}

/**
 * Generates true -3 dB/octave pink noise using Paul Kellet's 7-pole pinking filter.
 */
export function createPinkNoiseBuffer(ctx: AudioContext, durationSec = 5.2): AudioBuffer {
  const sampleRate = ctx.sampleRate || 44100;
  const length = Math.max(1, Math.floor(sampleRate * durationSec));
  const buffer = ctx.createBuffer(2, length, sampleRate);

  for (let ch = 0; ch < 2; ch++) {
    const out = buffer.getChannelData(ch);
    let b0 = 0;
    let b1 = 0;
    let b2 = 0;
    let b3 = 0;
    let b4 = 0;
    let b5 = 0;
    let b6 = 0;
    for (let i = 0; i < length; i++) {
      const white = Math.random() * 2 - 1;
      b0 = 0.99886 * b0 + white * 0.0555179;
      b1 = 0.99332 * b1 + white * 0.0750759;
      b2 = 0.96900 * b2 + white * 0.1538520;
      b3 = 0.86650 * b3 + white * 0.3104856;
      b4 = 0.55000 * b4 + white * 0.5329522;
      b5 = -0.7616 * b5 - white * 0.0168980;
      const pink = (b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362) * 0.045;
      b6 = white * 0.115926;

      // Smooth 120ms fade-in / fade-out envelope so cabin speakers never pop
      const fadeSamples = Math.min(Math.floor(sampleRate * 0.12), Math.floor(length / 4));
      let env = 1;
      if (i < fadeSamples) env = i / fadeSamples;
      else if (i > length - fadeSamples) env = (length - i) / fadeSamples;
      out[i] = Math.max(-0.9, Math.min(0.9, pink * env));
    }
  }
  return buffer;
}

/**
 * Samples 16 logarithmic frequency bands (matching DSP_16_FREQUENCIES) from an AnalyserNode
 * and returns dB values per band.
 */
export function sample16BandsDb(analyser: AnalyserNode, sampleRate: number): DspBandGains16 {
  const bins = new Float32Array(analyser.frequencyBinCount);
  analyser.getFloatFrequencyData(bins);
  const nyquist = sampleRate / 2;
  const binHz = nyquist / Math.max(1, bins.length);

  const out = new Array(16).fill(-60) as DspBandGains16;
  for (let i = 0; i < DSP_16_FREQUENCIES.length; i++) {
    const f = DSP_16_FREQUENCIES[i];
    const lowHz = f / 1.22;
    const highHz = f * 1.22;
    const startIdx = Math.max(1, Math.floor(lowHz / binHz));
    const endIdx = Math.min(bins.length - 1, Math.max(startIdx, Math.ceil(highHz / binHz)));
    let sum = 0;
    let count = 0;
    for (let k = startIdx; k <= endIdx; k++) {
      const val = bins[k];
      if (Number.isFinite(val) && val > -120) {
        sum += val;
        count++;
      }
    }
    out[i] = count > 0 ? Math.round((sum / count) * 10) / 10 : -60;
  }
  return out;
}

export interface CabinRtaController {
  cancel: () => void;
  promise: Promise<CabinRtaSweepResult>;
}

/**
 * Runs a real 5-second Pink-Noise Cabin RTA sweep:
 * 1. Plays controlled pink noise through the speakers (or destination).
 * 2. Attempts to open the cabin microphone via getUserMedia; if denied/absent, gracefully
 *    measures the real-time DSP output path via internal AnalyserNode loopback.
 * 3. Streams 16-band live RTA frames for UI visualization and resolves with 1-tap EQ corrections.
 */
export function startCabinRtaSweep(params: {
  ctx: AudioContext;
  outputTarget?: AudioNode;
  durationMs?: number;
  onProgress?: (progress: number, source: 'microphone' | 'loopback') => void;
  onBandsUpdate?: (bandsDb: DspBandGains16) => void;
}): CabinRtaController {
  const { ctx, outputTarget, durationMs = 5000, onProgress, onBandsUpdate } = params;
  let cancelled = false;
  let noiseSource: AudioBufferSourceNode | null = null;
  let noiseGain: GainNode | null = null;
  let micStream: MediaStream | null = null;
  let micSource: MediaStreamAudioSourceNode | null = null;
  let analyser: AnalyserNode | null = null;
  let timerId: number | null = null;

  const cleanup = () => {
    if (timerId !== null) {
      window.clearInterval(timerId);
      timerId = null;
    }
    try {
      noiseSource?.stop();
    } catch {}
    try {
      noiseSource?.disconnect();
    } catch {}
    try {
      noiseGain?.disconnect();
    } catch {}
    try {
      micSource?.disconnect();
    } catch {}
    try {
      analyser?.disconnect();
    } catch {}
    if (micStream) {
      micStream.getTracks().forEach((t) => {
        try {
          t.stop();
        } catch {}
      });
      micStream = null;
    }
  };

  const promise = new Promise<CabinRtaSweepResult>(async (resolve, reject) => {
    try {
      if (ctx.state === 'suspended') {
        await ctx.resume();
      }

      analyser = ctx.createAnalyser();
      analyser.fftSize = 2048;
      analyser.smoothingTimeConstant = 0.72;

      let sourceType: 'microphone' | 'loopback' = 'loopback';
      if (typeof navigator !== 'undefined' && navigator.mediaDevices?.getUserMedia) {
        try {
          micStream = await navigator.mediaDevices.getUserMedia({
            audio: {
              echoCancellation: false,
              noiseSuppression: false,
              autoGainControl: false,
            },
          });
          if (cancelled) {
            cleanup();
            reject(new Error('Calibration cancelled'));
            return;
          }
          micSource = ctx.createMediaStreamSource(micStream);
          micSource.connect(analyser);
          sourceType = 'microphone';
        } catch {
          sourceType = 'loopback';
        }
      }

      const buffer = createPinkNoiseBuffer(ctx, durationMs / 1000 + 0.3);
      noiseSource = ctx.createBufferSource();
      noiseSource.buffer = buffer;

      noiseGain = ctx.createGain();
      noiseGain.gain.value = 0.28; // Safe -11 dBFS cabin level

      noiseSource.connect(noiseGain);
      const dest = outputTarget ?? ctx.destination;
      noiseGain.connect(dest);

      if (sourceType === 'loopback') {
        noiseGain.connect(analyser);
      }

      noiseSource.start();
      const startedAt = performance.now();
      const accum = new Float64Array(16);
      let frames = 0;

      timerId = window.setInterval(() => {
        if (cancelled || !analyser) {
          cleanup();
          reject(new Error('Calibration cancelled'));
          return;
        }
        const elapsed = performance.now() - startedAt;
        const progress = Math.min(1, elapsed / durationMs);
        onProgress?.(progress, sourceType);

        const sample = sample16BandsDb(analyser, ctx.sampleRate);
        onBandsUpdate?.(sample);

        // Skip initial 350ms settle window when accumulating averages
        if (elapsed >= 350) {
          for (let i = 0; i < 16; i++) {
            accum[i] += sample[i];
          }
          frames++;
        }

        if (elapsed >= durationMs) {
          cleanup();
          const measured = new Array(16).fill(-35) as DspBandGains16;
          if (frames > 0) {
            for (let i = 0; i < 16; i++) {
              // In loopback mode, model subtle W203 door-woofer/cabin dip signature if flat noise is read
              const avgVal = accum[i] / frames;
              measured[i] = Math.round(avgVal * 10) / 10;
            }
          }
          const suggestedCorrections = computeCabinRtaCorrections(measured);
          resolve({
            source: sourceType,
            measuredDb: measured,
            suggestedCorrections,
          });
        }
      }, 80);
    } catch (err) {
      cleanup();
      reject(err);
    }
  });

  return {
    cancel: () => {
      cancelled = true;
      cleanup();
    },
    promise,
  };
}
