import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  S203_COLORWAYS,
  S203ColorwayId,
  S203DisplayMode,
  MERCEDES_VISUALIZER_MODES,
  generateS203DialTicks,
  calculatePeakHold,
  formatS203ClusterText,
  extractBandEnergies,
  pushRollingTelemetrySample,
  getVisualizerCanvasResolution,
  applyAttackReleaseEnvelope,
  updateRollingAgcPeak,
  computeLogSpectrumTargets,
  updateBeatTransient,
  normalizeTimeDomainBuffer,
} from '../utils/s203VisualizerLogic';
import { renderMercedesVisualizerFrame } from '../utils/mercedesVisualizerRenderer';
import { getOptimizedCanvasFps } from '../utils/mediatekOptimization';
import {
  Gauge,
  Sliders,
  Radio,
  Sparkles,
  Activity,
  Waves,
  Maximize2,
  Minimize2,
  Play,
  Pause,
  SkipBack,
  SkipForward,
  X,
} from 'lucide-react';

interface AudioVisualizerProps {
  isPlaying: boolean;
  accentHex: string;
  analyserNode?: AnalyserNode | null;
  trackTitle?: string;
  trackArtist?: string;
  trackAlbum?: string;
  positionSeconds?: number;
  durationSeconds?: number;
  currentSpeed?: number;
  ecoMode?: boolean;
  onTogglePlay?: () => void;
  onPrevTrack?: () => void;
  onNextTrack?: () => void;
  onClose?: () => void;
  externalFullscreen?: boolean;
  onExternalFullscreenExit?: () => void;
}

const MODE_ICON_MAP: Record<S203DisplayMode, React.FC<{ className?: string }>> = {
  gauge: Gauge,
  star_pulse: Sparkles,
  amg_telemetry: Activity,
  burmester_wave: Waves,
  comand: Sliders,
  mfd_amber: Radio,
};

function formatClock(secs: number): string {
  if (!Number.isFinite(secs) || secs < 0) return '0:00';
  const m = Math.floor(secs / 60);
  const s = Math.floor(secs % 60);
  return `${m}:${s < 10 ? '0' : ''}${s}`;
}

export const AudioVisualizer: React.FC<AudioVisualizerProps> = ({
  isPlaying,
  accentHex,
  analyserNode,
  trackTitle,
  trackArtist,
  trackAlbum,
  positionSeconds = 0,
  durationSeconds = 0,
  currentSpeed = 0,
  ecoMode = true,
  onTogglePlay,
  onPrevTrack,
  onNextTrack,
  onClose,
  externalFullscreen = false,
  onExternalFullscreenExit,
}) => {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const canvasBoxRef = useRef<HTMLDivElement | null>(null);
  const canvasRef = useRef<HTMLCanvasElement | null>(null);
  const animFrameRef = useRef<number | null>(null);
  const canvasLogicalSizeRef = useRef({ width: 250, height: 150, scale: 1 });

  // Persisted Mercedes Visualizer Mode & Colorway
  const [displayMode, setDisplayMode] = useState<S203DisplayMode>(() => {
    try {
      const saved = localStorage.getItem('zentryx_vis_mode') as S203DisplayMode | null;
      if (saved && MERCEDES_VISUALIZER_MODES.some((m) => m.id === saved)) return saved;
    } catch {}
    return 'gauge';
  });

  const [colorwayId, setColorwayId] = useState<S203ColorwayId>(() => {
    try {
      const saved = localStorage.getItem('zentryx_vis_colorway') as S203ColorwayId | null;
      if (saved && saved in S203_COLORWAYS) return saved;
    } catch {}
    return 'amber';
  });

  const [isVisFullscreen, setIsVisFullscreen] = useState(false);

  useEffect(() => {
    try {
      localStorage.setItem('zentryx_vis_mode', displayMode);
    } catch {}
  }, [displayMode]);

  useEffect(() => {
    try {
      localStorage.setItem('zentryx_vis_colorway', colorwayId);
    } catch {}
  }, [colorwayId]);

  // Pre-generate S203/W203 dial ticks once
  const dialTicks = useRef(generateS203DialTicks(25, 135, 405, 240)).current;
  const rpmTicks = useRef(generateS203DialTicks(29, 135, 405, 7)).current;

  // Zero-GC preallocated typed arrays for animation state
  const staticFftBufferRef = useRef(new Uint8Array(256));
  const staticRawTimeBufferRef = useRef(new Uint8Array(256));
  const timeDomainFloatRef = useRef(new Float32Array(256));
  const targetBinsRef = useRef(new Float32Array(32));
  const smoothedBinsRef = useRef(new Float32Array(32));
  const peakHoldsRef = useRef(new Float32Array(32));
  const telemetryTraceRef = useRef(new Float32Array(72).fill(0.12));
  const dampedBassRef = useRef(0.1);
  const dampedMidRef = useRef(0.1);
  const agcPeakRef = useRef(0.45);
  const avgBassRef = useRef(0.2);
  const beatPulseRef = useRef(0);
  const sawLiveSignalRef = useRef(false);
  const phaseRef = useRef(0);

  const activeColorway = S203_COLORWAYS[colorwayId] || S203_COLORWAYS.amber;
  const primaryColor = colorwayId === 'ambient' ? accentHex : activeColorway.primaryHex;
  const activeModeMeta =
    MERCEDES_VISUALIZER_MODES.find((m) => m.id === displayMode) || MERCEDES_VISUALIZER_MODES[0];

  const cycleColorway = useCallback(() => {
    const order: S203ColorwayId[] = ['amber', 'avantgarde', 'amg_gt', 'ambient'];
    const nextIdx = (order.indexOf(colorwayId) + 1) % order.length;
    setColorwayId(order[nextIdx]);
  }, [colorwayId]);

  useEffect(() => {
    if (externalFullscreen) {
      setIsVisFullscreen(true);
    }
  }, [externalFullscreen]);

  const enterFullscreen = useCallback(() => {
    setIsVisFullscreen(true);
    try {
      const el = containerRef.current;
      if (el && el.requestFullscreen && !document.fullscreenElement) {
        el.requestFullscreen().catch(() => {});
      }
    } catch {}
  }, []);

  const exitFullscreen = useCallback(() => {
    setIsVisFullscreen(false);
    if (onExternalFullscreenExit) {
      onExternalFullscreenExit();
    }
    try {
      if (document.fullscreenElement === containerRef.current && document.exitFullscreen) {
        document.exitFullscreen().catch(() => {});
      }
    } catch {}
  }, [onExternalFullscreenExit]);

  // Sync Escape key & browser fullscreen exit
  useEffect(() => {
    if (!isVisFullscreen) return;

    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        e.stopPropagation();
        exitFullscreen();
      }
    };

    const handleNativeFsChange = () => {
      // Keep overlay open only if intentionally toggled unless user exited native fullscreen explicitly
      if (!document.fullscreenElement && containerRef.current) {
        // Note: In iframes native fullscreen may reject immediately, so don't auto-close if it never entered.
      }
    };

    window.addEventListener('keydown', handleKey, true);
    document.addEventListener('fullscreenchange', handleNativeFsChange);
    return () => {
      window.removeEventListener('keydown', handleKey, true);
      document.removeEventListener('fullscreenchange', handleNativeFsChange);
    };
  }, [isVisFullscreen, exitFullscreen]);

  // Responsive Canvas Size Observer
  useEffect(() => {
    const box = canvasBoxRef.current;
    const canvas = canvasRef.current;
    if (!box || !canvas) return;

    const syncCanvasSize = () => {
      const rect = box.getBoundingClientRect();
      if (rect.width < 10 || rect.height < 10) return;

      const dpr = typeof window !== 'undefined' ? window.devicePixelRatio || 1 : 1;
      const { pixelWidth, pixelHeight, scale } = getVisualizerCanvasResolution(
        rect.width,
        rect.height,
        dpr,
        ecoMode
      );

      if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
        canvas.width = pixelWidth;
        canvas.height = pixelHeight;
      }

      canvasLogicalSizeRef.current = {
        width: rect.width,
        height: rect.height,
        scale,
      };
    };

    syncCanvasSize();
    const ro = new ResizeObserver(syncCanvasSize);
    ro.observe(box);
    return () => ro.disconnect();
  }, [ecoMode, isVisFullscreen]);

  // Resize pre-allocated FFT & waveform buffers only when analyserNode changes
  useEffect(() => {
    sawLiveSignalRef.current = false;
    agcPeakRef.current = 0.45;
    if (analyserNode) {
      const binCount = analyserNode.frequencyBinCount || 256;
      if (staticFftBufferRef.current.length !== binCount) {
        staticFftBufferRef.current = new Uint8Array(binCount);
        staticRawTimeBufferRef.current = new Uint8Array(binCount);
        timeDomainFloatRef.current = new Float32Array(binCount);
      }
    }
  }, [analyserNode]);

  // Animated Frame Loop (MediaTek 825X paced & visibility-aware)
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const fftData = staticFftBufferRef.current;
    const rawTimeData = staticRawTimeBufferRef.current;
    const timeDomainData = timeDomainFloatRef.current;
    const targetBins = targetBinsRef.current;
    const smoothedBins = smoothedBinsRef.current;
    const peakHolds = peakHoldsRef.current;
    const telemetryTrace = telemetryTraceRef.current;

    const reducedMotion =
      typeof window !== 'undefined' &&
      typeof window.matchMedia === 'function' &&
      window.matchMedia('(prefers-reduced-motion: reduce)').matches;

    let lastDrawTime = 0;
    const targetFps = getOptimizedCanvasFps(ecoMode);
    const frameInterval = 1000 / targetFps;

    const render = (time: number) => {
      animFrameRef.current = requestAnimationFrame(render);

      if (time - lastDrawTime < frameInterval) return;
      lastDrawTime = time;

      let hasLiveAudio = false;
      if (analyserNode && isPlaying) {
        analyserNode.getByteFrequencyData(fftData);
        analyserNode.getByteTimeDomainData(rawTimeData);
        const waveformRms = normalizeTimeDomainBuffer(rawTimeData, timeDomainData);
        const agc = updateRollingAgcPeak(fftData, agcPeakRef.current, 0.992, 0.25);
        agcPeakRef.current = agc.nextPeak;

        if (agc.hasSignal || waveformRms > 0.008) {
          sawLiveSignalRef.current = true;
          hasLiveAudio = true;
        } else if (sawLiveSignalRef.current) {
          // Track already yielded live samples; stay faithful during quiet musical passages
          hasLiveAudio = true;
        }
      }

      const phaseStep = reducedMotion ? 0.02 : isPlaying ? 0.085 : 0.018;
      phaseRef.current += phaseStep;
      const phase = phaseRef.current;

      const bands = extractBandEnergies(
        fftData,
        hasLiveAudio,
        isPlaying,
        phase,
        agcPeakRef.current
      );

      if (isPlaying) {
        const beat = updateBeatTransient(bands.bass, avgBassRef.current, beatPulseRef.current);
        avgBassRef.current = beat.nextAvgBass;
        beatPulseRef.current = beat.nextBeatPulse;
      } else {
        beatPulseRef.current *= 0.85;
      }

      // Compute 32 target bars via logarithmic FFT distribution or fallback waves
      if (hasLiveAudio && isPlaying) {
        computeLogSpectrumTargets(fftData, targetBins, agcPeakRef.current);
      } else if (isPlaying) {
        for (let i = 0; i < targetBins.length; i++) {
          const w1 = Math.sin(phase * 1.45 + i * 0.34);
          const w2 = Math.cos(phase * 0.82 - i * 0.23);
          const tilt = 1 - (i / targetBins.length) * 0.25;
          targetBins[i] = Math.max(0.08, Math.min(0.96, ((w1 * 0.42 + w2 * 0.36 + 0.85) / 1.75) * tilt));
        }
      } else {
        for (let i = 0; i < targetBins.length; i++) {
          targetBins[i] = 0.05 + (Math.sin(phase * 0.6 + i * 0.2) + 1) * 0.015;
        }
      }

      // Fast-attack / smooth-release per frequency bar + COMAND peak-hold gravity
      for (let i = 0; i < smoothedBins.length; i++) {
        smoothedBins[i] = applyAttackReleaseEnvelope(
          smoothedBins[i],
          targetBins[i],
          isPlaying ? 0.68 : 0.2,
          isPlaying ? 0.2 : 0.14
        );
        peakHolds[i] = calculatePeakHold(peakHolds[i], smoothedBins[i], 0.022);
      }

      // Fast-attack, automotive-damped release for dial needles & G-meter
      dampedBassRef.current = applyAttackReleaseEnvelope(
        dampedBassRef.current,
        bands.bass,
        0.58,
        0.16
      );
      dampedMidRef.current = applyAttackReleaseEnvelope(
        dampedMidRef.current,
        bands.mid,
        0.52,
        0.18
      );

      // Advance AMG telemetry trace buffer
      pushRollingTelemetrySample(
        telemetryTrace,
        Math.min(1, bands.overall * 0.85 + beatPulseRef.current * 0.3)
      );

      const nextSourceMode = hasLiveAudio ? 'live-fft' : isPlaying ? 'synthetic' : 'standby';
      if (canvas.dataset.audioSource !== nextSourceMode) {
        canvas.dataset.audioSource = nextSourceMode;
      }

      const { width, height, scale } = canvasLogicalSizeRef.current;
      ctx.setTransform(scale, 0, 0, scale, 0, 0);

      renderMercedesVisualizerFrame({
        ctx,
        width,
        height,
        mode: displayMode,
        colorway: activeColorway,
        primaryColor,
        isPlaying,
        ecoMode,
        isFullscreen: isVisFullscreen,
        reducedMotion,
        phase,
        bands,
        smoothedBins,
        peakHolds,
        telemetryTrace,
        timeDomainData,
        hasLiveAudio,
        beatPulse: beatPulseRef.current,
        dialTicks,
        rpmTicks,
        dampedBass: dampedBassRef.current,
        dampedMid: dampedMidRef.current,
        currentSpeed,
      });
    };

    animFrameRef.current = requestAnimationFrame(render);

    const handleVisibility = () => {
      if (document.hidden) {
        if (animFrameRef.current) {
          cancelAnimationFrame(animFrameRef.current);
          animFrameRef.current = null;
        }
      } else if (!animFrameRef.current) {
        lastDrawTime = performance.now();
        animFrameRef.current = requestAnimationFrame(render);
      }
    };

    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibility);
      if (animFrameRef.current) {
        cancelAnimationFrame(animFrameRef.current);
        animFrameRef.current = null;
      }
    };
  }, [
    isPlaying,
    primaryColor,
    activeColorway,
    analyserNode,
    displayMode,
    dialTicks,
    rpmTicks,
    ecoMode,
    isVisFullscreen,
    currentSpeed,
  ]);

  const formattedDisplay = formatS203ClusterText(
    trackTitle,
    trackArtist,
    isVisFullscreen ? 48 : 26
  );

  const progressPct =
    durationSeconds > 0 ? Math.min(100, Math.max(0, (positionSeconds / durationSeconds) * 100)) : 0;

  return (
    <div
      ref={containerRef}
      data-testid={isVisFullscreen ? 'visualizer-fullscreen-overlay' : 'visualizer-inline-panel'}
      data-visualizer-mode={displayMode}
      data-visualizer-fullscreen={isVisFullscreen ? 'true' : 'false'}
      className={
        isVisFullscreen
          ? 'fixed inset-0 z-50 bg-[#020617] flex flex-col justify-between p-4 sm:p-6 select-none'
          : 'w-full h-full flex flex-col items-center justify-between p-2.5 select-none'
      }
    >
      {/* Top Bar */}
      <div
        className={`w-full flex items-center justify-between gap-2 border-b border-slate-800/80 ${
          isVisFullscreen ? 'pb-3' : 'pb-1.5'
        }`}
      >
        <div className="flex items-center gap-2 min-w-0 shrink-0">
          <div
            className="w-2 h-2 rounded-full shrink-0 transition-transform"
            style={{
              backgroundColor: primaryColor,
              boxShadow: isPlaying ? `0 0 8px ${primaryColor}` : 'none',
              transform: isPlaying ? 'scale(1.15)' : 'scale(1.0)',
            }}
          />
          <span
            className={`font-mono font-bold tracking-wider text-white whitespace-nowrap ${
              isVisFullscreen ? 'text-sm' : 'text-[10px]'
            }`}
          >
            {isVisFullscreen ? `MERCEDES-BENZ · ${activeModeMeta.name.toUpperCase()}` : 'MERCEDES'}
          </span>
        </div>

        {/* Fullscreen Mode Strip (shown directly on wide bar in fullscreen) */}
        {isVisFullscreen && (
          <div className="hidden md:flex items-center bg-black/70 p-1 rounded-xl border border-slate-800 gap-1 shrink-0">
            {MERCEDES_VISUALIZER_MODES.map((m) => {
              const Icon = MODE_ICON_MAP[m.id];
              const isActive = displayMode === m.id;
              return (
                <button
                  key={m.id}
                  type="button"
                  onClick={() => setDisplayMode(m.id)}
                  className={`px-2.5 py-1.5 rounded-lg text-xs font-semibold flex items-center gap-1.5 whitespace-nowrap transition-colors ${
                    isActive
                      ? 'bg-slate-800 text-white shadow'
                      : 'text-slate-400 hover:text-white'
                  }`}
                  style={isActive ? { color: primaryColor } : undefined}
                  aria-label={`Visualizer Mode: ${m.name}`}
                  aria-pressed={isActive}
                >
                  <Icon className="w-3.5 h-3.5 shrink-0" />
                  <span>{m.shortLabel}</span>
                </button>
              );
            })}
          </div>
        )}

        {/* Utility Buttons: Colorway + Fullscreen Toggle + Close */}
        <div className="flex items-center gap-1.5 shrink-0">
          <button
            type="button"
            onClick={cycleColorway}
            className={`rounded-lg bg-black/60 border border-slate-800 text-slate-300 hover:text-white transition-colors flex items-center gap-1.5 whitespace-nowrap ${
              isVisFullscreen ? 'px-2.5 py-1.5 text-xs' : 'p-1'
            }`}
            title={`Colorway: ${activeColorway.name} (Click to change)`}
            aria-label={`Switch Colorway (${activeColorway.name})`}
          >
            <span
              className="block w-2.5 h-2.5 rounded-full border border-white/25 shrink-0"
              style={{ backgroundColor: primaryColor }}
            />
            {isVisFullscreen && <span className="hidden sm:inline font-mono text-[11px]">{activeColorway.id.toUpperCase()}</span>}
          </button>

          <button
            type="button"
            onClick={() => (isVisFullscreen ? exitFullscreen() : enterFullscreen())}
            className={`rounded-lg bg-black/60 border border-slate-800 text-slate-300 hover:text-white hover:border-slate-600 transition-colors flex items-center gap-1.5 whitespace-nowrap ${
              isVisFullscreen ? 'px-3 py-1.5 text-xs font-semibold' : 'p-1'
            }`}
            title={isVisFullscreen ? 'Exit Fullscreen Visualizer (Esc)' : 'Expand Visualizer to Fullscreen'}
            aria-label={isVisFullscreen ? 'Exit Fullscreen Visualizer' : 'Fullscreen Visualizer'}
          >
            {isVisFullscreen ? (
              <>
                <Minimize2 className="w-3.5 h-3.5" />
                <span>Exit</span>
              </>
            ) : (
              <Maximize2 className="w-3.5 h-3.5" />
            )}
          </button>

          {onClose && !isVisFullscreen && (
            <button
              type="button"
              onClick={onClose}
              className="text-slate-400 hover:text-white p-1 rounded-lg hover:bg-slate-800 transition-colors"
              title="Close Visualizer"
              aria-label="Close Visualizer"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>

      {/* Mode Picker Row (3x2 grid when inline so all 6 modes show cleanly; responsive strip on narrow fullscreen) */}
      <div
        className={
          isVisFullscreen
            ? 'w-full flex md:hidden items-center gap-1 overflow-x-auto py-1'
            : 'w-full grid grid-cols-3 gap-1 pt-1'
        }
      >
        {MERCEDES_VISUALIZER_MODES.map((m) => {
          const isActive = displayMode === m.id;
          return (
            <button
              key={m.id}
              type="button"
              onClick={() => setDisplayMode(m.id)}
              className={`shrink-0 rounded-md border font-mono text-center whitespace-nowrap transition-colors ${
                isVisFullscreen ? 'px-2.5 py-1 text-xs' : 'px-1 py-0.5 text-[9px]'
              } ${
                isActive
                  ? 'bg-slate-800 text-white border-slate-600 font-bold'
                  : 'bg-black/50 text-slate-400 border-slate-800/80 hover:text-white'
              }`}
              style={isActive ? { borderColor: primaryColor, color: primaryColor } : undefined}
              title={m.subtitle}
              aria-label={`Visualizer Mode: ${m.name}`}
              aria-pressed={isActive}
            >
              {m.shortLabel}
            </button>
          );
        })}
      </div>

      {/* Responsive Canvas Viewport */}
      <div
        ref={canvasBoxRef}
        onDoubleClick={() => (isVisFullscreen ? exitFullscreen() : enterFullscreen())}
        className={`w-full flex-1 min-h-0 flex items-center justify-center relative overflow-hidden ${
          isVisFullscreen
            ? 'my-3 rounded-2xl bg-[#04070f] border border-slate-800/90 shadow-inner'
            : 'my-0.5 rounded-xl'
        }`}
        title={isVisFullscreen ? 'Double-click to exit fullscreen' : 'Double-click for fullscreen visualizer'}
      >
        <canvas
          ref={canvasRef}
          data-testid="mercedes-visualizer-canvas"
          className="w-full h-full block"
        />
      </div>

      {/* Bottom HUD */}
      {isVisFullscreen ? (
        <div className="w-full pt-3 border-t border-slate-800/90 flex flex-col sm:flex-row items-center justify-between gap-4">
          <div className="w-full sm:max-w-lg text-center sm:text-left min-w-0">
            <div
              className="text-base sm:text-lg font-extrabold tracking-tight text-white truncate"
              title={trackTitle}
            >
              {trackTitle || 'No Track Selected'}
            </div>
            <div className="text-xs text-slate-400 truncate mt-0.5">
              {trackArtist || 'Unknown Artist'}
              {trackAlbum ? ` · ${trackAlbum}` : ''}
              {' · '}
              <span style={{ color: primaryColor }}>{activeColorway.name}</span>
              {currentSpeed > 0 ? ` · ${currentSpeed} km/h` : ''}
            </div>
            {durationSeconds > 0 && (
              <div className="mt-2 flex items-center gap-2">
                <span className="text-[11px] font-mono text-slate-400">{formatClock(positionSeconds)}</span>
                <div className="flex-1 h-1.5 bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full rounded-full"
                    style={{ width: `${progressPct}%`, backgroundColor: primaryColor }}
                  />
                </div>
                <span className="text-[11px] font-mono text-slate-400">{formatClock(durationSeconds)}</span>
              </div>
            )}
          </div>

          <div className="flex items-center gap-3 shrink-0">
            {onPrevTrack && (
              <button
                type="button"
                onClick={onPrevTrack}
                className="w-12 h-12 rounded-2xl bg-slate-900 border border-slate-700 hover:bg-slate-800 text-white flex items-center justify-center transition-colors active:scale-95"
                aria-label="Previous Track"
              >
                <SkipBack className="w-5 h-5" />
              </button>
            )}
            {onTogglePlay && (
              <button
                type="button"
                onClick={onTogglePlay}
                className="w-14 h-14 rounded-2xl text-black font-bold flex items-center justify-center shadow-xl transition-transform active:scale-95"
                style={{ backgroundColor: primaryColor }}
                aria-label={isPlaying ? 'Pause Playback' : 'Start Playback'}
              >
                {isPlaying ? <Pause className="w-6 h-6" /> : <Play className="w-6 h-6 ml-0.5" />}
              </button>
            )}
            {onNextTrack && (
              <button
                type="button"
                onClick={onNextTrack}
                className="w-12 h-12 rounded-2xl bg-slate-900 border border-slate-700 hover:bg-slate-800 text-white flex items-center justify-center transition-colors active:scale-95"
                aria-label="Next Track"
              >
                <SkipForward className="w-5 h-5" />
              </button>
            )}
          </div>
        </div>
      ) : (
        <div className="w-full text-center pt-1 border-t border-slate-800/80">
          <div
            className="text-[10px] font-mono font-semibold tracking-wide truncate max-w-[240px] mx-auto"
            style={{ color: primaryColor }}
            title={trackTitle}
          >
            {formattedDisplay}
          </div>
          <div className="text-[8px] font-mono text-slate-500 mt-0.5 flex items-center justify-center gap-1.5 truncate">
            <span>{activeModeMeta.name}</span>
            <span>·</span>
            <span>{currentSpeed > 0 ? `${currentSpeed} km/h` : 'Double-tap for Fullscreen'}</span>
          </div>
        </div>
      )}
    </div>
  );
};
