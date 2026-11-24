import React, { useMemo, useRef, useState } from 'react';
import {
  Sliders,
  Sparkles,
  Speaker,
  Compass,
  Waves,
  Save,
  Trash2,
  RotateCcw,
  Shield,
  Volume2,
  Activity,
  GitCompare,
  Headphones,
  Crosshair,
  Radio,
  Gauge,
  Mic,
  CheckCircle2,
  Square,
} from 'lucide-react';
import {
  DSP_16_FREQUENCIES,
  FACTORY_DSP_PRESETS,
  STAGE_PRESETS,
  COMPRESSOR_PROFILES,
  AUDIO_OUTPUT_ROUTES,
  SUB_LPF_OPTIONS_HZ,
  LIMITER_CEILING_OPTIONS_DB,
  SURROUND_MODES,
  ZERO_BANDS_16,
  DspSettings,
  DspPreset16,
  DspBandGains16,
  HpfCutoffHz,
  SubLpfHz,
  CrossoverSlopeDb,
  LimiterCeilingDb,
  AudioOutputRouteId,
  ListeningStagePreset,
  CompressorProfileId,
  SurroundModeId,
  interpolate5To16Bands,
  downsample16To5Bands,
  calculateLoudnessContourGains,
  calculateRoadNoiseContourDb,
  buildDspResponseCurvePoints,
  applyPresetToDsp,
} from '../utils/dspConsole';
import { CabinRtaController, CabinRtaSweepResult, startCabinRtaSweep } from '../utils/cabinRta';
import {
  ADAPTIVE_CABIN_PROFILES,
  AdaptiveCabinProfileId,
  AdaptiveDspDelta,
} from '../utils/adaptiveCabinProfile';

interface DspConsolePanelProps {
  accentHex: string;
  dspSettings: DspSettings;
  onChangeDspSettings: (next: DspSettings | ((prev: DspSettings) => DspSettings)) => void;
  customPresets: DspPreset16[];
  onSaveCustomPreset: (name: string) => void;
  onDeleteCustomPreset: (presetId: string) => void;
  masterVolume: number;
  currentSpeedKmH?: number;
  activeCompareSlot?: 'A' | 'B';
  slotBPresetName?: string | null;
  onToggleCompareSlot?: () => void;
  onSnapshotSlotB?: () => void;
  onHoldBypassChange?: (bypassed: boolean) => void;
  activeOutputRoute?: AudioOutputRouteId;
  onSelectOutputRoute?: (route: AudioOutputRouteId) => void;
  getAudioContext?: () => AudioContext | null;
  adaptiveCabinEnabled?: boolean;
  adaptiveProfileId?: AdaptiveCabinProfileId;
  adaptiveDspDelta?: AdaptiveDspDelta;
  onToggleAdaptiveCabin?: () => void;
}

type DspSubTab = 'eq' | 'surround' | 'stage' | 'rta';

const BAND_5_LABELS = ['60Hz', '250Hz', '1kHz', '4kHz', '12kHz'] as const;
const HPF_OPTIONS: HpfCutoffHz[] = [0, 60, 80, 100, 120];
const HARMAN_SURROUND_ORDER: SurroundModeId[] = [
  'off',
  'logic7_surround',
  'concert_hall',
  'studio_focused',
];
const DOLBY_VIRTUAL_SURROUND_ORDER: SurroundModeId[] = [
  'dolby_pro_logic_ii',
  'dolby_atmos_virtual',
  'virtual_surround_360',
];

function formatFreqShort(freq: number): string {
  if (freq >= 1000) {
    const khz = freq / 1000;
    return Number.isInteger(khz) ? `${khz}k` : `${khz.toFixed(1)}k`;
  }
  return `${freq}`;
}

export const DspConsolePanel: React.FC<DspConsolePanelProps> = ({
  accentHex,
  dspSettings,
  onChangeDspSettings,
  customPresets,
  onSaveCustomPreset,
  onDeleteCustomPreset,
  masterVolume,
  currentSpeedKmH = 0,
  activeCompareSlot = 'A',
  slotBPresetName = null,
  onToggleCompareSlot,
  onSnapshotSlotB,
  onHoldBypassChange,
  activeOutputRoute = 'headunit_speakers',
  onSelectOutputRoute,
  getAudioContext,
  adaptiveCabinEnabled = false,
  adaptiveProfileId = 'quiet',
  adaptiveDspDelta,
  onToggleAdaptiveCabin,
}) => {
  const [activeSubTab, setActiveSubTab] = useState<DspSubTab>('eq');
  const [newPresetName, setNewPresetName] = useState('');
  const [isHoldingBypass, setIsHoldingBypass] = useState(false);

  // Cabin RTA 5-second Pink-Noise Sweep state
  const [rtaRunning, setRtaRunning] = useState(false);
  const [rtaProgress, setRtaProgress] = useState(0);
  const [rtaSource, setRtaSource] = useState<'microphone' | 'loopback' | null>(null);
  const [rtaLiveBands, setRtaLiveBands] = useState<number[]>(() => new Array(16).fill(-54));
  const [rtaResult, setRtaResult] = useState<CabinRtaSweepResult | null>(null);
  const [rtaAppliedNotice, setRtaAppliedNotice] = useState(false);
  const rtaControllerRef = useRef<CabinRtaController | null>(null);

  const stagePadRef = useRef<HTMLDivElement | null>(null);
  const isDraggingStageRef = useRef(false);

  const updateStageFromPointer = (clientX: number, clientY: number) => {
    const el = stagePadRef.current;
    if (!el) return;
    const rect = el.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return;
    const normX = Math.max(0, Math.min(1, (clientX - rect.left) / rect.width));
    const normY = Math.max(0, Math.min(1, (clientY - rect.top) / rect.height));
    const nextBalance = Math.round(((normX * 2 - 1) * 100) / 5) * 5;
    const nextFader = Math.round(((1 - normY * 2) * 100) / 5) * 5;
    onChangeDspSettings((prev) => ({
      ...prev,
      balance: Math.max(-100, Math.min(100, nextBalance)),
      fader: Math.max(-100, Math.min(100, nextFader)),
    }));
  };

  const allPresets = useMemo(
    () => [...FACTORY_DSP_PRESETS, ...customPresets],
    [customPresets]
  );

  const curvePoints = useMemo(
    () => buildDspResponseCurvePoints(dspSettings, masterVolume, 640, 140, currentSpeedKmH),
    [dspSettings, masterVolume, currentSpeedKmH]
  );

  const svgPath = useMemo(() => {
    if (curvePoints.length === 0) return '';
    return curvePoints
      .map((pt, i) => `${i === 0 ? 'M' : 'L'} ${pt.x} ${pt.y}`)
      .join(' ');
  }, [curvePoints]);

  const svgAreaPath = useMemo(() => {
    if (curvePoints.length === 0) return '';
    const line = curvePoints
      .map((pt, i) => `${i === 0 ? 'M' : 'L'} ${pt.x} ${pt.y}`)
      .join(' ');
    return `${line} L 640 70 L 0 70 Z`;
  }, [curvePoints]);

  const loudnessBoost = useMemo(
    () =>
      calculateLoudnessContourGains(
        dspSettings.dspEnabled && dspSettings.loudnessContour,
        masterVolume
      ),
    [dspSettings.dspEnabled, dspSettings.loudnessContour, masterVolume]
  );

  const roadNoiseContour = useMemo(
    () => calculateRoadNoiseContourDb(currentSpeedKmH, dspSettings.dspEnabled && dspSettings.roadNoiseEqEnabled),
    [currentSpeedKmH, dspSettings.dspEnabled, dspSettings.roadNoiseEqEnabled]
  );

  const maxBandGain = useMemo(
    () => Math.max(...dspSettings.bands16, 0) + dspSettings.bassBoostDb,
    [dspSettings.bands16, dspSettings.bassBoostDb]
  );
  const clipWarning =
    dspSettings.dspEnabled && maxBandGain + dspSettings.preampDb > 6;

  const bands5 = useMemo(
    () => downsample16To5Bands(dspSettings.bands16),
    [dspSettings.bands16]
  );

  const handleSelectPreset = (preset: DspPreset16) => {
    onChangeDspSettings((prev) => applyPresetToDsp(prev, preset));
  };

  const handleChangeBand16 = (index: number, gain: number) => {
    onChangeDspSettings((prev) => {
      const nextBands = [...prev.bands16] as DspBandGains16;
      nextBands[index] = gain;
      return {
        ...prev,
        selectedPresetId: 'custom',
        bands16: nextBands,
      };
    });
  };

  const handleChangeBand5 = (bandIdx: number, gain: number) => {
    const next5 = [...bands5];
    next5[bandIdx] = gain;
    const interpolated = interpolate5To16Bands(next5);
    onChangeDspSettings((prev) => ({
      ...prev,
      selectedPresetId: 'custom',
      bands16: interpolated,
    }));
  };

  const handleSavePreset = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newPresetName.trim();
    if (!trimmed) return;
    onSaveCustomPreset(trimmed);
    setNewPresetName('');
  };

  const handleSelectStage = (stage: Exclude<ListeningStagePreset, 'custom'>) => {
    const cfg = STAGE_PRESETS[stage];
    onChangeDspSettings((prev) => ({
      ...prev,
      listeningStage: stage,
      leftDelayMs: cfg.leftDelayMs,
      rightDelayMs: cfg.rightDelayMs,
    }));
  };

  const handleStartRtaSweep = async () => {
    if (rtaRunning) {
      rtaControllerRef.current?.cancel();
      rtaControllerRef.current = null;
      setRtaRunning(false);
      return;
    }

    const AudioCtxCtor = window.AudioContext || (window as any).webkitAudioContext;
    const ctx = getAudioContext?.() || (AudioCtxCtor ? new AudioCtxCtor() : null);
    if (!ctx) return;

    setRtaRunning(true);
    setRtaAppliedNotice(false);
    setRtaResult(null);
    setRtaProgress(0);

    const ctrl = startCabinRtaSweep({
      ctx,
      durationMs: 5000,
      onProgress: (p, src) => {
        setRtaProgress(p);
        setRtaSource(src);
      },
      onBandsUpdate: (bands) => setRtaLiveBands([...bands]),
    });
    rtaControllerRef.current = ctrl;

    try {
      const res = await ctrl.promise;
      setRtaResult(res);
      setRtaSource(res.source);
    } catch {
      // Cancelled or unavailable
    } finally {
      setRtaRunning(false);
      rtaControllerRef.current = null;
    }
  };

  const handleApplyRtaCalibration = () => {
    if (!rtaResult) return;
    onChangeDspSettings((prev) => ({
      ...prev,
      dspEnabled: true,
      selectedPresetId: 'custom',
      bands16: [...rtaResult.suggestedCorrections] as DspBandGains16,
      preampDb: Math.min(prev.preampDb, -2),
    }));
    setRtaAppliedNotice(true);
  };

  const activeDisabledClass = dspSettings.dspEnabled
    ? ''
    : 'opacity-45 pointer-events-none select-none';

  const activeSurroundInfo = SURROUND_MODES[dspSettings.surroundMode ?? 'off'];

  const subTabs: Array<{ id: DspSubTab; label: string; badge?: string; icon: React.ReactNode }> = [
    { id: 'eq', label: 'EQ & Presets', icon: <Sliders className="w-4 h-4" /> },
    {
      id: 'surround',
      label: 'Surround 3D',
      badge: dspSettings.surroundMode !== 'off' ? activeSurroundInfo.shortLabel : undefined,
      icon: <Radio className="w-4 h-4" />,
    },
    {
      id: 'stage',
      label: 'Stage & X-Over',
      badge: `${dspSettings.crossoverSlopeDb}dB`,
      icon: <Compass className="w-4 h-4" />,
    },
    {
      id: 'rta',
      label: 'Cabin RTA',
      badge: rtaRunning ? 'LIVE' : undefined,
      icon: <Mic className="w-4 h-4" />,
    },
  ];

  return (
    <div className="space-y-4 min-w-0 w-full">
      {/* ============================================================ */}
      {/* PERSISTENT MASTER DSP HEADER & CLEAN 4-TAB SWITCHER          */}
      {/* ============================================================ */}
      <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-4 sm:p-5 shadow-xl space-y-4 min-w-0">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-3 min-w-0">
            <div
              className="w-11 h-11 rounded-xl flex items-center justify-center border shrink-0"
              style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
            >
              <Sliders className="w-5 h-5" style={{ color: accentHex }} />
            </div>
            <div className="min-w-0">
              <h3 className="text-base font-bold text-white flex items-center gap-2 flex-wrap">
                <span>Harman Logic 7 &amp; 16-Band DSP</span>
                <span
                  className="text-[10px] px-2 py-0.5 rounded-md font-mono font-bold uppercase whitespace-nowrap"
                  style={
                    dspSettings.dspEnabled
                      ? { backgroundColor: `${accentHex}24`, color: accentHex }
                      : { backgroundColor: '#1e293b', color: '#94a3b8' }
                  }
                >
                  {dspSettings.dspEnabled ? 'DSP Online' : 'Bypassed'}
                </span>
              </h3>
              <p className="text-xs text-slate-400 truncate">
                {activeSurroundInfo.label} · {dspSettings.crossoverSlopeDb} dB/oct Linkwitz-Riley ·{' '}
                {adaptiveCabinEnabled
                  ? `Auto Cabin: ${ADAPTIVE_CABIN_PROFILES[adaptiveProfileId].shortLabel}${adaptiveDspDelta && adaptiveDspDelta.gainDb > 0 ? ` (+${adaptiveDspDelta.gainDb} dB)` : ''}`
                  : dspSettings.roadNoiseEqEnabled
                    ? `Road EQ +${roadNoiseContour.subBassDb} dB`
                    : 'Road EQ Off'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 flex-wrap">
            {onToggleAdaptiveCabin && (
              <button
                type="button"
                onClick={onToggleAdaptiveCabin}
                className="min-h-[40px] px-3 py-1.5 rounded-xl text-[11px] font-bold border flex items-center gap-1.5 active:scale-95 transition-transform whitespace-nowrap"
                style={
                  adaptiveCabinEnabled
                    ? { backgroundColor: `${accentHex}24`, borderColor: accentHex, color: accentHex }
                    : { backgroundColor: 'rgba(0,0,0,0.6)', borderColor: '#334155', color: '#cbd5e1' }
                }
                title="Toggle Automated Cabin Noise & Speed DSP Profile Switching"
              >
                <Gauge className="w-3.5 h-3.5" />
                <span>
                  Auto Cabin: {adaptiveCabinEnabled ? ADAPTIVE_CABIN_PROFILES[adaptiveProfileId].shortLabel : 'Off'}
                </span>
              </button>
            )}
            {onSnapshotSlotB && (
              <button
                type="button"
                onClick={onSnapshotSlotB}
                className="min-h-[40px] px-3 py-1.5 rounded-xl text-[11px] font-bold bg-black/60 border border-slate-700 text-slate-200 hover:text-white active:scale-95 transition-transform whitespace-nowrap"
                title="Snapshot current DSP settings to Compare Slot B"
              >
                Snap A→B
              </button>
            )}

            {onToggleCompareSlot && (
              <button
                type="button"
                onClick={onToggleCompareSlot}
                className="min-h-[40px] px-3 py-1.5 rounded-xl text-[11px] font-bold border flex items-center gap-1.5 active:scale-95 transition-transform whitespace-nowrap"
                style={
                  activeCompareSlot === 'B'
                    ? { backgroundColor: `${accentHex}24`, borderColor: accentHex, color: accentHex }
                    : { backgroundColor: 'rgba(0,0,0,0.6)', borderColor: '#334155', color: '#e2e8f0' }
                }
                title="Switch between Slot A and Slot B DSP tuning"
              >
                <GitCompare className="w-3.5 h-3.5" />
                <span>
                  Slot {activeCompareSlot}
                  {slotBPresetName ? ` (${slotBPresetName})` : ''}
                </span>
              </button>
            )}

            {onHoldBypassChange && (
              <button
                type="button"
                onPointerDown={() => {
                  setIsHoldingBypass(true);
                  onHoldBypassChange(true);
                }}
                onPointerUp={() => {
                  setIsHoldingBypass(false);
                  onHoldBypassChange(false);
                }}
                onPointerLeave={() => {
                  if (isHoldingBypass) {
                    setIsHoldingBypass(false);
                    onHoldBypassChange(false);
                  }
                }}
                onPointerCancel={() => {
                  setIsHoldingBypass(false);
                  onHoldBypassChange(false);
                }}
                className={`min-h-[40px] px-3 py-1.5 rounded-xl text-[11px] font-bold border transition-colors whitespace-nowrap select-none ${
                  isHoldingBypass
                    ? 'bg-amber-400 text-black border-amber-300'
                    : 'bg-black/60 text-slate-300 border-slate-700 hover:text-white'
                }`}
                title="Press and hold to audition dry unfiltered audio"
              >
                {isHoldingBypass ? 'Bypassing...' : 'Hold Bypass'}
              </button>
            )}

            <button
              type="button"
              aria-label="Toggle Master DSP Engine"
              onClick={() =>
                onChangeDspSettings((prev) => ({ ...prev, dspEnabled: !prev.dspEnabled }))
              }
              className={`w-14 h-8 rounded-full p-1 transition-colors shrink-0 ${
                dspSettings.dspEnabled ? '' : 'bg-slate-700'
              }`}
              style={dspSettings.dspEnabled ? { backgroundColor: accentHex } : {}}
            >
              <div
                className={`w-6 h-6 rounded-full bg-black transition-transform ${
                  dspSettings.dspEnabled ? 'translate-x-6' : 'translate-x-0'
                }`}
              />
            </button>
          </div>
        </div>

        {/* Organized 4-Tab Segmented Console Bar */}
        <div
          role="tablist"
          aria-label="DSP Console Sections"
          className="grid grid-cols-2 sm:grid-cols-4 gap-1.5 p-1.5 rounded-xl bg-black/55 border border-slate-800/80"
        >
          {subTabs.map((tab) => {
            const isSelected = activeSubTab === tab.id;
            return (
              <button
                key={tab.id}
                role="tab"
                type="button"
                aria-selected={isSelected}
                onClick={() => setActiveSubTab(tab.id)}
                className={`min-h-[44px] px-3 py-2 rounded-lg text-xs font-bold flex items-center justify-center gap-2 transition-all active:scale-95 ${
                  isSelected
                    ? 'text-black shadow-md'
                    : 'text-slate-300 hover:text-white hover:bg-slate-900/70'
                }`}
                style={isSelected ? { backgroundColor: accentHex } : {}}
              >
                {tab.icon}
                <span className="truncate">{tab.label}</span>
                {tab.badge && !isSelected && (
                  <span className="text-[9px] font-mono px-1.5 py-0.5 rounded bg-slate-800 text-slate-300">
                    {tab.badge}
                  </span>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* ============================================================ */}
      {/* TAB 1: EQ, RESPONSE CURVE, PRESETS & AUTO-ROUTING            */}
      {/* ============================================================ */}
      {activeSubTab === 'eq' && (
        <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5 min-w-0 overflow-hidden">
          {/* Live SVG Frequency Response Curve + EQ Mode Switch + Preamp */}
          <div className={`space-y-4 ${activeDisabledClass}`}>
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="inline-flex rounded-xl bg-black/60 border border-slate-800 p-1">
                <button
                  type="button"
                  onClick={() =>
                    onChangeDspSettings((prev) => ({ ...prev, eqViewMode: '16band' }))
                  }
                  className={`min-h-[38px] px-3.5 py-1 rounded-lg text-xs font-bold transition-colors whitespace-nowrap ${
                    dspSettings.eqViewMode === '16band'
                      ? 'text-black'
                      : 'text-slate-400 hover:text-white'
                  }`}
                  style={
                    dspSettings.eqViewMode === '16band' ? { backgroundColor: accentHex } : {}
                  }
                >
                  16-Band Pro
                </button>
                <button
                  type="button"
                  onClick={() =>
                    onChangeDspSettings((prev) => ({ ...prev, eqViewMode: '5band' }))
                  }
                  className={`min-h-[38px] px-3.5 py-1 rounded-lg text-xs font-bold transition-colors whitespace-nowrap ${
                    dspSettings.eqViewMode === '5band'
                      ? 'text-black'
                      : 'text-slate-400 hover:text-white'
                  }`}
                  style={
                    dspSettings.eqViewMode === '5band' ? { backgroundColor: accentHex } : {}
                  }
                >
                  5-Band Quick
                </button>
              </div>

              {/* Zero-Clip Preamp Trim */}
              <div className="flex items-center gap-3 flex-1 min-w-[220px] max-w-sm bg-black/50 border border-slate-800 rounded-xl px-3.5 py-2">
                <div className="shrink-0">
                  <div className="text-[11px] font-bold text-slate-300">Preamp Trim</div>
                  <div className="text-[10px] font-mono tabular-nums text-slate-400">
                    {dspSettings.preampDb > 0 ? `+${dspSettings.preampDb}` : dspSettings.preampDb} dB
                  </div>
                </div>
                <input
                  type="range"
                  aria-label="DSP Preamp Trim"
                  min={-12}
                  max={6}
                  step={0.5}
                  value={dspSettings.preampDb}
                  onChange={(e) =>
                    onChangeDspSettings((prev) => ({
                      ...prev,
                      preampDb: Number(e.target.value),
                    }))
                  }
                  className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                  style={{ accentColor: accentHex }}
                />
              </div>
            </div>

            {clipWarning && (
              <div className="px-3.5 py-2 rounded-xl bg-amber-500/10 border border-amber-500/40 text-amber-300 text-xs font-medium">
                Peak EQ + Bass Boost exceeds +6 dB headroom — reduce Preamp Trim to avoid DAC clipping.
              </div>
            )}

            {/* Live Frequency Response Curve Visualizer */}
            <div className="bg-black/60 border border-slate-800 rounded-2xl p-3 space-y-1.5">
              <div className="flex items-center justify-between text-[10px] font-mono text-slate-400 px-1 gap-2 flex-wrap">
                <span>COMPOSITE RESPONSE (25 Hz – 18 kHz)</span>
                <span className="tabular-nums">
                  HPF: {dspSettings.hpfCutoffHz === 0 ? 'OFF' : `${dspSettings.hpfCutoffHz}Hz (${dspSettings.crossoverSlopeDb}dB)`} · SUB: +{dspSettings.bassBoostDb}dB
                </span>
              </div>
              <svg
                viewBox="0 0 640 140"
                className="w-full h-32 block overflow-visible"
                role="img"
                aria-label="Live DSP Frequency Response Curve"
              >
                {[14, 42, 70, 98, 126].map((yPos, idx) => (
                  <line
                    key={yPos}
                    x1={0}
                    y1={yPos}
                    x2={640}
                    y2={yPos}
                    stroke={idx === 2 ? '#334155' : '#1e293b'}
                    strokeWidth={idx === 2 ? 1.5 : 1}
                    strokeDasharray={idx === 2 ? undefined : '3 3'}
                  />
                ))}
                {[84, 224, 362, 498, 602].map((xPos) => (
                  <line
                    key={xPos}
                    x1={xPos}
                    y1={0}
                    x2={xPos}
                    y2={140}
                    stroke="#1e293b"
                    strokeWidth={1}
                  />
                ))}
                <path d={svgAreaPath} fill={accentHex} fillOpacity={0.14} />
                <path
                  d={svgPath}
                  fill="none"
                  stroke={accentHex}
                  strokeWidth={2.5}
                  strokeLinecap="round"
                  strokeLinejoin="round"
                />
              </svg>
              <div className="flex justify-between text-[10px] font-mono text-slate-500 px-1">
                <span>32Hz</span>
                <span>125Hz</span>
                <span>500Hz</span>
                <span>1kHz</span>
                <span>4kHz</span>
                <span>16kHz</span>
              </div>
            </div>

            {/* Interactive EQ Sliders */}
            <div className="p-4 bg-black/40 border border-slate-800/80 rounded-2xl overflow-x-auto">
              {dspSettings.eqViewMode === '16band' ? (
                <div className="grid grid-cols-[repeat(16,minmax(0,1fr))] gap-1.5 min-w-[720px] text-center">
                  {DSP_16_FREQUENCIES.map((freq, idx) => {
                    const gain = dspSettings.bands16[idx] ?? 0;
                    const label = formatFreqShort(freq);
                    return (
                      <div key={freq} className="flex flex-col items-center gap-2">
                        <span
                          className="text-[11px] font-mono font-bold tabular-nums"
                          style={{
                            color: gain > 0 ? accentHex : gain < 0 ? '#94a3b8' : '#ffffff',
                          }}
                        >
                          {gain > 0 ? `+${gain}` : gain}
                        </span>
                        <div className="w-8 h-32 flex items-center justify-center overflow-visible">
                          <input
                            type="range"
                            aria-label={`EQ band ${freq >= 1000 ? `${freq / 1000}kHz` : `${freq}Hz`}`}
                            min={-12}
                            max={12}
                            step={0.5}
                            value={gain}
                            onChange={(e) => handleChangeBand16(idx, Number(e.target.value))}
                            className="w-28 shrink-0 h-2 bg-slate-800 rounded-lg cursor-pointer -rotate-90 origin-center"
                            style={{ accentColor: accentHex }}
                          />
                        </div>
                        <span className="text-[10px] font-mono text-slate-400 font-semibold">
                          {label}
                        </span>
                      </div>
                    );
                  })}
                </div>
              ) : (
                <div className="grid grid-cols-5 gap-3 text-center">
                  {BAND_5_LABELS.map((label, idx) => {
                    const gain = bands5[idx] ?? 0;
                    return (
                      <div key={label} className="flex flex-col items-center gap-2">
                        <span
                          className="text-xs font-mono font-bold tabular-nums"
                          style={{
                            color: gain > 0 ? accentHex : gain < 0 ? '#94a3b8' : '#ffffff',
                          }}
                        >
                          {gain > 0 ? `+${gain}` : gain} dB
                        </span>
                        <div className="w-10 h-32 flex items-center justify-center overflow-visible">
                          <input
                            type="range"
                            aria-label={`5-band EQ ${label}`}
                            min={-12}
                            max={12}
                            step={0.5}
                            value={gain}
                            onChange={(e) => handleChangeBand5(idx, Number(e.target.value))}
                            className="w-28 shrink-0 h-2 bg-slate-800 rounded-lg cursor-pointer -rotate-90 origin-center"
                            style={{ accentColor: accentHex }}
                          />
                        </div>
                        <span className="text-xs font-mono text-slate-400 font-semibold">
                          {label}
                        </span>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>

          {/* Vehicle & Custom DSP Presets */}
          <div className="space-y-3 pt-2 border-t border-slate-800/60">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <label className="text-xs font-bold uppercase tracking-wider text-slate-300">
                Vehicle &amp; Custom DSP Presets
              </label>
              <button
                type="button"
                onClick={() =>
                  onChangeDspSettings((prev) => ({
                    ...prev,
                    selectedPresetId: 'flat',
                    bands16: [...ZERO_BANDS_16] as DspBandGains16,
                    preampDb: 0,
                    bassBoostDb: 0,
                    hpfCutoffHz: 0,
                  }))
                }
                className="min-h-[38px] px-3 py-1.5 rounded-lg text-xs font-semibold bg-black/50 border border-slate-800 text-slate-300 hover:text-white hover:border-slate-700 flex items-center gap-1.5 whitespace-nowrap"
              >
                <RotateCcw className="w-3.5 h-3.5" />
                Flatten Curve
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-3 gap-2">
              {allPresets.map((preset) => {
                const isSelected = dspSettings.selectedPresetId === preset.id;
                return (
                  <div key={preset.id} className="relative group">
                    <button
                      type="button"
                      onClick={() => handleSelectPreset(preset)}
                      className={`w-full min-h-[54px] p-2.5 pr-7 rounded-xl border text-left transition-transform active:scale-95 ${
                        isSelected
                          ? 'bg-slate-800/90 text-white shadow'
                          : 'border-slate-800 bg-black/40 text-slate-400 hover:text-white hover:border-slate-700'
                      }`}
                      style={isSelected ? { borderColor: accentHex } : {}}
                    >
                      <div
                        className="font-bold text-xs truncate"
                        style={isSelected ? { color: accentHex } : {}}
                      >
                        {preset.name}
                      </div>
                      <div className="text-[10px] text-slate-500 mt-0.5 line-clamp-1">
                        {preset.description}
                      </div>
                    </button>
                    {preset.isCustom && (
                      <button
                        type="button"
                        aria-label={`Delete preset ${preset.name}`}
                        onClick={(e) => {
                          e.stopPropagation();
                          onDeleteCustomPreset(preset.id);
                        }}
                        className="absolute top-1.5 right-1.5 w-6 h-6 rounded-lg flex items-center justify-center text-slate-500 hover:text-red-400 hover:bg-red-500/10"
                        title={`Delete ${preset.name}`}
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    )}
                  </div>
                );
              })}
            </div>

            {dspSettings.selectedPresetId === 'pioneer_ts_e170ci' && (
              <div className="p-3.5 rounded-xl bg-slate-900/80 border border-slate-700/80 text-xs text-slate-300 leading-relaxed space-y-1">
                <div className="font-bold text-white">
                  Pioneer TS-E170Ci Calibration Active (60 Hz HPF · +5.5 dB @ 80 Hz · -4.5 dB Highs)
                </div>
                <p className="text-[11px] text-slate-400">
                  Protects the 73.4 Hz door woofer resonance while restoring chest punch and attenuating harsh 2.5–9 kHz tweeter glare.
                </p>
              </div>
            )}

            <form onSubmit={handleSavePreset} className="flex flex-wrap sm:flex-nowrap gap-2 pt-1">
              <input
                type="text"
                value={newPresetName}
                onChange={(e) => setNewPresetName(e.target.value)}
                maxLength={32}
                placeholder="Save current curve as custom preset..."
                aria-label="Custom preset name"
                className="flex-1 min-w-[180px] min-h-[44px] px-3.5 py-2 rounded-xl bg-black/60 border border-slate-800 text-xs text-white placeholder-slate-500 focus:outline-none focus:border-slate-600"
              />
              <button
                type="submit"
                disabled={!newPresetName.trim()}
                className="min-h-[44px] px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-1.5 transition-opacity disabled:opacity-40 whitespace-nowrap shrink-0 text-black"
                style={{ backgroundColor: accentHex }}
              >
                <Save className="w-3.5 h-3.5" />
                Save Preset
              </button>
            </form>

            {/* Auto-Genre & Audio Output Route Automation */}
            <div className="pt-3 border-t border-slate-800/60 space-y-3">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div className="p-3 rounded-xl bg-black/40 border border-slate-800 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-xs font-bold text-white">Auto-Preset by Track Genre</div>
                    <div className="text-[10px] text-slate-400">
                      Matches ID3 genre or folder (Rock, Electronic, Hip-Hop, Jazz, Podcast)
                    </div>
                  </div>
                  <button
                    type="button"
                    aria-label="Toggle Auto-Preset by Track Genre"
                    onClick={() =>
                      onChangeDspSettings((prev) => ({
                        ...prev,
                        autoGenrePresetEnabled: !prev.autoGenrePresetEnabled,
                      }))
                    }
                    className={`w-11 h-6 rounded-full p-0.5 transition-colors shrink-0 ${
                      dspSettings.autoGenrePresetEnabled ? '' : 'bg-slate-700'
                    }`}
                    style={dspSettings.autoGenrePresetEnabled ? { backgroundColor: accentHex } : {}}
                  >
                    <div
                      className={`w-5 h-5 rounded-full bg-black transition-transform ${
                        dspSettings.autoGenrePresetEnabled ? 'translate-x-5' : 'translate-x-0'
                      }`}
                    />
                  </button>
                </div>

                <div className="p-3 rounded-xl bg-black/40 border border-slate-800 flex items-center justify-between gap-3">
                  <div className="min-w-0">
                    <div className="text-xs font-bold text-white">Auto-Switch on Output Device</div>
                    <div className="text-[10px] text-slate-400">
                      Applies linked preset between Speakers, BT &amp; USB DAC
                    </div>
                  </div>
                  <button
                    type="button"
                    aria-label="Toggle Auto-Switch Preset on Output Device"
                    onClick={() =>
                      onChangeDspSettings((prev) => ({
                        ...prev,
                        autoSwitchDevicePreset: !prev.autoSwitchDevicePreset,
                      }))
                    }
                    className={`w-11 h-6 rounded-full p-0.5 transition-colors shrink-0 ${
                      dspSettings.autoSwitchDevicePreset ? '' : 'bg-slate-700'
                    }`}
                    style={dspSettings.autoSwitchDevicePreset ? { backgroundColor: accentHex } : {}}
                  >
                    <div
                      className={`w-5 h-5 rounded-full bg-black transition-transform ${
                        dspSettings.autoSwitchDevicePreset ? 'translate-x-5' : 'translate-x-0'
                      }`}
                    />
                  </button>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                {AUDIO_OUTPUT_ROUTES.map((route) => {
                  const isActiveRoute = activeOutputRoute === route.id;
                  const mappedId = dspSettings.devicePresetMap[route.id] || 'w203_bose';
                  const mappedPreset =
                    allPresets.find((p) => p.id === mappedId) || FACTORY_DSP_PRESETS[1];
                  return (
                    <div
                      key={route.id}
                      className={`p-2.5 rounded-xl border flex flex-col gap-1.5 ${
                        isActiveRoute
                          ? 'bg-slate-900/90 border-slate-600'
                          : 'bg-black/40 border-slate-800'
                      }`}
                      style={isActiveRoute ? { borderColor: `${accentHex}88` } : {}}
                    >
                      <div className="flex items-center justify-between gap-1">
                        <button
                          type="button"
                          onClick={() => onSelectOutputRoute?.(route.id)}
                          className="text-left flex items-center gap-1.5 text-[11px] font-bold text-white truncate"
                        >
                          <Headphones
                            className="w-3.5 h-3.5 shrink-0"
                            style={{ color: isActiveRoute ? accentHex : '#64748b' }}
                          />
                          <span className="truncate">{route.label}</span>
                        </button>
                        {isActiveRoute && (
                          <span
                            className="text-[9px] font-mono font-bold px-1.5 py-0.5 rounded"
                            style={{ backgroundColor: `${accentHex}24`, color: accentHex }}
                          >
                            ACTIVE
                          </span>
                        )}
                      </div>
                      <button
                        type="button"
                        onClick={() => {
                          const idx = allPresets.findIndex((p) => p.id === mappedPreset.id);
                          const nextPreset = allPresets[(idx + 1) % allPresets.length];
                          onChangeDspSettings((prev) => ({
                            ...prev,
                            devicePresetMap: {
                              ...prev.devicePresetMap,
                              [route.id]: nextPreset.id,
                            },
                          }));
                        }}
                        className="text-[10px] font-mono text-left px-2 py-1 rounded-lg bg-black/60 border border-slate-800 text-slate-300 hover:text-white truncate"
                        title="Click to cycle linked preset for this audio output"
                      >
                        Preset: {mappedPreset.name}
                      </button>
                    </div>
                  );
                })}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================ */}
      {/* TAB 2: HARMAN LOGIC 7, DOLBY SURROUND & VIRTUAL 3D ACOUSTICS */}
      {/* ============================================================ */}
      {activeSubTab === 'surround' && (
        <div className={`space-y-4 ${activeDisabledClass}`}>
          {/* Surround Matrix Selector (Harman Logic 7 + Dolby Surround + 360° Virtualizer) */}
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5">
            <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-800/60">
              <div className="flex items-center gap-3">
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center border shrink-0"
                  style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
                >
                  <Radio className="w-5 h-5" style={{ color: accentHex }} />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white">
                    Surround Matrix — Harman Logic 7 · Dolby · Virtual 3D
                  </h3>
                  <p className="text-xs text-slate-400">
                    Mid/Side center lock, 90° Dolby Pro Logic II rear matrix, Atmos pinna height dome &amp; 360° cross-talk cancellation
                  </p>
                </div>
              </div>
              <div className="flex items-center gap-2">
                <span
                  className="text-[10px] font-mono font-bold px-2.5 py-1 rounded-lg border"
                  style={{
                    backgroundColor: `${accentHex}14`,
                    borderColor: `${accentHex}44`,
                    color: accentHex,
                  }}
                >
                  {activeSurroundInfo.badge}
                </span>
                {dspSettings.monoFoldDown && (
                  <span className="text-[10px] font-mono font-bold px-2.5 py-1 rounded-lg bg-amber-500/15 text-amber-300 border border-amber-500/40">
                    Mono Fold Active
                  </span>
                )}
              </div>
            </div>

            {/* Family 1: Stereo Direct & Harman Logic 7 */}
            <div className="space-y-2">
              <div className="text-[10px] font-mono font-bold uppercase tracking-wider text-slate-400">
                Stereo Direct &amp; Harman Logic 7 Series
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-2.5">
                {HARMAN_SURROUND_ORDER.map((modeId) => {
                  const mode = SURROUND_MODES[modeId];
                  const isSelected = (dspSettings.surroundMode ?? 'off') === modeId;
                  return (
                    <button
                      key={modeId}
                      type="button"
                      onClick={() =>
                        onChangeDspSettings((prev) => ({
                          ...prev,
                          dspEnabled: true,
                          surroundMode: modeId,
                          monoFoldDown: modeId !== 'off' ? false : prev.monoFoldDown,
                        }))
                      }
                      className={`min-h-[82px] p-3.5 rounded-xl border text-left transition-transform active:scale-95 flex flex-col justify-between ${
                        isSelected
                          ? 'bg-slate-800/90 text-white shadow'
                          : 'border-slate-800 bg-black/40 text-slate-400 hover:text-white hover:border-slate-700'
                      }`}
                      style={isSelected ? { borderColor: accentHex } : {}}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span
                          className="font-bold text-xs"
                          style={isSelected ? { color: accentHex } : {}}
                        >
                          {mode.label}
                        </span>
                        {mode.haasLeftMs > 0 && (
                          <span className="text-[10px] font-mono tabular-nums text-slate-500">
                            {mode.haasLeftMs}/{mode.haasRightMs}ms
                          </span>
                        )}
                      </div>
                      <p className="text-[11px] text-slate-400 leading-snug mt-1.5">
                        {mode.tagline}
                      </p>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Family 2: Dolby Surround & Virtual 3D Engines */}
            <div className="space-y-2">
              <div className="text-[10px] font-mono font-bold uppercase tracking-wider text-slate-400">
                Dolby Surround &amp; Virtual 3D Spatializer Engines
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5">
                {DOLBY_VIRTUAL_SURROUND_ORDER.map((modeId) => {
                  const mode = SURROUND_MODES[modeId];
                  const isSelected = (dspSettings.surroundMode ?? 'off') === modeId;
                  return (
                    <button
                      key={modeId}
                      type="button"
                      onClick={() =>
                        onChangeDspSettings((prev) => ({
                          ...prev,
                          dspEnabled: true,
                          surroundMode: modeId,
                          monoFoldDown: false,
                        }))
                      }
                      className={`min-h-[88px] p-3.5 rounded-xl border text-left transition-transform active:scale-95 flex flex-col justify-between ${
                        isSelected
                          ? 'bg-slate-800/90 text-white shadow'
                          : 'border-slate-800 bg-black/40 text-slate-400 hover:text-white hover:border-slate-700'
                      }`}
                      style={isSelected ? { borderColor: accentHex } : {}}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span
                          className="font-bold text-xs"
                          style={isSelected ? { color: accentHex } : {}}
                        >
                          {mode.label}
                        </span>
                        <span className="text-[9px] font-mono font-bold px-1.5 py-0.5 rounded bg-black/60 text-slate-300 border border-slate-700/80">
                          {mode.badge}
                        </span>
                      </div>
                      <p className="text-[11px] text-slate-400 leading-snug mt-1.5">
                        {mode.tagline}
                      </p>
                      <div className="flex items-center gap-2 mt-2 text-[9px] font-mono text-slate-500">
                        <span>Haas {mode.haasLeftMs}/{mode.haasRightMs}ms</span>
                        {mode.useAllPassPhase && <span>· 90° AP</span>}
                        {mode.crosstalkCancelGain > 0 && <span>· XTC</span>}
                        {mode.heightBoostMaxDb >= 3 && <span>· Height Dome</span>}
                      </div>
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Live Cabin 3D Soundstage Field Radar */}
            <div className="p-4 rounded-xl bg-black/50 border border-slate-800 flex flex-col sm:flex-row items-center gap-4">
              <svg
                viewBox="0 0 220 110"
                className="w-56 h-28 shrink-0"
                role="img"
                aria-label="Live 3D Cabin Surround Soundstage Radar"
              >
                {/* Cabin Outline */}
                <rect
                  x="64"
                  y="14"
                  width="92"
                  height="82"
                  rx="22"
                  fill="#0f172a"
                  stroke="#334155"
                  strokeWidth="1.5"
                />
                {/* Surround Rear/Side Wrap Field */}
                {dspSettings.surroundMode !== 'off' && !dspSettings.monoFoldDown && (
                  <>
                    <ellipse
                      cx="110"
                      cy="58"
                      rx={38 + (dspSettings.surroundIntensity / 100) * 58}
                      ry={26 + (dspSettings.surroundIntensity / 100) * 20}
                      fill={accentHex}
                      fillOpacity={0.07 + (dspSettings.surroundIntensity / 100) * 0.12}
                      stroke={accentHex}
                      strokeOpacity={0.45}
                      strokeDasharray="4 3"
                      strokeWidth="1.2"
                    />
                    {/* Overhead Virtual Height Dome */}
                    <circle
                      cx="110"
                      cy="52"
                      r={10 + ((dspSettings.virtualHeight ?? 45) / 100) * (activeSurroundInfo.heightBoostMaxDb * 6.5)}
                      fill="none"
                      stroke={accentHex}
                      strokeOpacity={0.28 + ((dspSettings.virtualHeight ?? 45) / 100) * 0.45}
                      strokeWidth="1.5"
                    />
                    {/* Locked Dash Center Vocal Beam */}
                    <path
                      d={`M 110 18 L ${110 - 12 - (dspSettings.centerCoherence / 100) * 14} 44 L ${110 + 12 + (dspSettings.centerCoherence / 100) * 14} 44 Z`}
                      fill={accentHex}
                      fillOpacity={0.14 + (dspSettings.centerCoherence / 100) * 0.22}
                    />
                  </>
                )}
                {/* Front L / C / R & Rear Surround Speaker Nodes */}
                <circle cx="72" cy="24" r="3.5" fill={accentHex} />
                <circle cx="148" cy="24" r="3.5" fill={accentHex} />
                <circle
                  cx="110"
                  cy="19"
                  r="3.2"
                  fill={dspSettings.surroundMode !== 'off' ? accentHex : '#475569'}
                />
                <circle
                  cx="72"
                  cy="84"
                  r="3"
                  fill={dspSettings.surroundMode !== 'off' ? accentHex : '#475569'}
                />
                <circle
                  cx="148"
                  cy="84"
                  r="3"
                  fill={dspSettings.surroundMode !== 'off' ? accentHex : '#475569'}
                />
                <text x="110" y="57" textAnchor="middle" fill="#cbd5e1" fontSize="8" fontFamily="monospace">
                  {activeSurroundInfo.shortLabel.toUpperCase()}
                </text>
              </svg>

              <div className="flex-1 grid grid-cols-2 gap-2.5 w-full text-[11px]">
                <div className="p-2.5 rounded-lg bg-slate-900/70 border border-slate-800/80">
                  <div className="text-[10px] text-slate-400">Matrix Topology</div>
                  <div className="font-bold text-white mt-0.5">{activeSurroundInfo.label}</div>
                </div>
                <div className="p-2.5 rounded-lg bg-slate-900/70 border border-slate-800/80">
                  <div className="text-[10px] text-slate-400">Decorrelation Engine</div>
                  <div className="font-mono font-bold text-slate-200 mt-0.5">
                    {dspSettings.surroundMode === 'off'
                      ? 'Bypass (2.0)'
                      : activeSurroundInfo.crosstalkCancelGain > 0
                        ? '90° AP + Haas + XTC'
                        : activeSurroundInfo.useAllPassPhase
                          ? '90° All-Pass + Haas'
                          : 'Dual Haas Matrix'}
                  </div>
                </div>
                <div className="p-2.5 rounded-lg bg-slate-900/70 border border-slate-800/80">
                  <div className="text-[10px] text-slate-400">Pinna Height Air (7.2 kHz)</div>
                  <div className="font-mono font-bold mt-0.5" style={{ color: accentHex }}>
                    {dspSettings.surroundMode === 'off'
                      ? '0.0 dB'
                      : `+${(activeSurroundInfo.heightBoostMaxDb * ((dspSettings.virtualHeight ?? 45) / 100)).toFixed(1)} dB`}
                  </div>
                </div>
                <div className="p-2.5 rounded-lg bg-slate-900/70 border border-slate-800/80">
                  <div className="text-[10px] text-slate-400">Center Dash Lock (2.2 kHz)</div>
                  <div className="font-mono font-bold mt-0.5" style={{ color: accentHex }}>
                    {dspSettings.surroundMode === 'off'
                      ? '0.0 dB'
                      : `+${(activeSurroundInfo.centerBoostMaxDb * (dspSettings.centerCoherence / 100)).toFixed(1)} dB`}
                  </div>
                </div>
              </div>
            </div>

            {/* Surround Immersion, Center Vocal Coherence & Virtual Height Elevation */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-3.5">
              <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-2.5">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-white">Surround Immersion</span>
                  <span className="text-xs font-mono font-bold tabular-nums" style={{ color: accentHex }}>
                    {dspSettings.surroundIntensity}%
                  </span>
                </div>
                <input
                  type="range"
                  aria-label="Surround Immersion"
                  min={0}
                  max={100}
                  step={5}
                  disabled={dspSettings.surroundMode === 'off'}
                  value={dspSettings.surroundIntensity}
                  onChange={(e) =>
                    onChangeDspSettings((prev) => ({
                      ...prev,
                      surroundIntensity: Number(e.target.value),
                    }))
                  }
                  className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer disabled:opacity-40"
                  style={{ accentColor: accentHex }}
                />
                <p className="text-[10px] text-slate-500">
                  Side-channel Haas wrap, 90° rear decode &amp; XTC width bloom
                </p>
              </div>

              <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-2.5">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-white">Center Vocal Lock</span>
                  <span className="text-xs font-mono font-bold tabular-nums" style={{ color: accentHex }}>
                    {dspSettings.centerCoherence}%
                  </span>
                </div>
                <input
                  type="range"
                  aria-label="Center Vocal Coherence"
                  min={0}
                  max={100}
                  step={5}
                  disabled={dspSettings.surroundMode === 'off'}
                  value={dspSettings.centerCoherence}
                  onChange={(e) =>
                    onChangeDspSettings((prev) => ({
                      ...prev,
                      centerCoherence: Number(e.target.value),
                    }))
                  }
                  className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer disabled:opacity-40"
                  style={{ accentColor: accentHex }}
                />
                <p className="text-[10px] text-slate-500">
                  Anchors dialogue &amp; lead vocals onto the center dash (2.2 kHz)
                </p>
              </div>

              <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-2.5">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-white">Virtual Height 3D</span>
                  <span className="text-xs font-mono font-bold tabular-nums" style={{ color: accentHex }}>
                    {dspSettings.virtualHeight ?? 45}%
                  </span>
                </div>
                <input
                  type="range"
                  aria-label="Virtual Height 3D Elevation"
                  min={0}
                  max={100}
                  step={5}
                  disabled={dspSettings.surroundMode === 'off'}
                  value={dspSettings.virtualHeight ?? 45}
                  onChange={(e) =>
                    onChangeDspSettings((prev) => ({
                      ...prev,
                      virtualHeight: Number(e.target.value),
                    }))
                  }
                  className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer disabled:opacity-40"
                  style={{ accentColor: accentHex }}
                />
                <p className="text-[10px] text-slate-500">
                  Lifts soundstage off door woofers toward windshield &amp; roofline
                </p>
              </div>
            </div>

            {/* 3D Stereo Width */}
            <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-2">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2 text-xs font-bold text-white">
                  <Waves className="w-4 h-4" style={{ color: accentHex }} />
                  <span>Base 3D Stereo Width (Mid/Side Matrix)</span>
                </div>
                <span className="text-xs font-mono font-bold tabular-nums" style={{ color: accentHex }}>
                  {dspSettings.stereoWidthPercent}%
                </span>
              </div>
              <input
                type="range"
                aria-label="3D Stereo Width"
                min={0}
                max={150}
                step={5}
                value={dspSettings.stereoWidthPercent}
                onChange={(e) =>
                  onChangeDspSettings((prev) => ({
                    ...prev,
                    stereoWidthPercent: Number(e.target.value),
                  }))
                }
                className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                style={{ accentColor: accentHex }}
              />
            </div>
          </div>

          {/* Road-Noise Acoustic Contour + Dynamic Fletcher-Munson Loudness */}
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-4">
            <div className="p-4 rounded-xl bg-black/40 border border-slate-800 flex items-center justify-between gap-4">
              <div className="space-y-1">
                <div className="text-sm font-bold text-white flex items-center gap-2 flex-wrap">
                  <Gauge className="w-4 h-4 shrink-0" style={{ color: accentHex }} />
                  <span>Road-Noise Acoustic Contour (Cabin EQ Compensator)</span>
                  {dspSettings.roadNoiseEqEnabled && (
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded-md bg-slate-800 text-slate-200 tabular-nums whitespace-nowrap">
                      +{roadNoiseContour.subBassDb}dB Sub / +{roadNoiseContour.vocalPresenceDb}dB Vocal @ {Math.round(currentSpeedKmH)} km/h
                    </span>
                  )}
                </div>
                <p className="text-xs text-slate-400">
                  Speed-sensitive spectral tilt: automatically lifts sub-120 Hz bass (up to +4 dB) and 2.8 kHz vocal clarity (+1.5 dB) above 50 km/h to defeat highway tire &amp; wind masking.
                </p>
              </div>
              <button
                type="button"
                aria-label="Toggle Road-Noise Acoustic Contour"
                onClick={() =>
                  onChangeDspSettings((prev) => ({
                    ...prev,
                    roadNoiseEqEnabled: !prev.roadNoiseEqEnabled,
                  }))
                }
                className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                  dspSettings.roadNoiseEqEnabled ? '' : 'bg-slate-700'
                }`}
                style={dspSettings.roadNoiseEqEnabled ? { backgroundColor: accentHex } : {}}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-black transition-transform ${
                    dspSettings.roadNoiseEqEnabled ? 'translate-x-6' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            <div className="p-4 rounded-xl bg-black/40 border border-slate-800 flex items-center justify-between gap-4">
              <div className="space-y-1">
                <div className="text-sm font-bold text-white flex items-center gap-2 flex-wrap">
                  <Sparkles className="w-4 h-4 shrink-0" style={{ color: accentHex }} />
                  <span>Dynamic Loudness Contour (Fletcher-Munson)</span>
                  {dspSettings.loudnessContour && (
                    <span className="text-[10px] font-mono px-2 py-0.5 rounded-md bg-slate-800 text-slate-200 tabular-nums whitespace-nowrap">
                      +{loudnessBoost.lowShelfDb}dB Low / +{loudnessBoost.highShelfDb}dB Air @ {masterVolume}% Vol
                    </span>
                  )}
                </div>
                <p className="text-xs text-slate-400">
                  Compensates human ear bass &amp; treble sensitivity drops at lower cabin listening volumes.
                </p>
              </div>
              <button
                type="button"
                aria-label="Toggle Dynamic Loudness Contour"
                onClick={() =>
                  onChangeDspSettings((prev) => ({
                    ...prev,
                    loudnessContour: !prev.loudnessContour,
                  }))
                }
                className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                  dspSettings.loudnessContour ? '' : 'bg-slate-700'
                }`}
                style={dspSettings.loudnessContour ? { backgroundColor: accentHex } : {}}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-black transition-transform ${
                    dspSettings.loudnessContour ? 'translate-x-6' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================ */}
      {/* TAB 3: STAGE, TIME ALIGNMENT, 12/24 dB X-OVER & DYNAMICS     */}
      {/* ============================================================ */}
      {activeSubTab === 'stage' && (
        <div className="space-y-4">
          {/* 2D Cabin Soundstage Pad + Time Alignment */}
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5">
            <div className="flex items-center gap-3 pb-3 border-b border-slate-800/60">
              <div
                className="w-10 h-10 rounded-xl flex items-center justify-center border shrink-0"
                style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
              >
                <Compass className="w-5 h-5" style={{ color: accentHex }} />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">Cabin Time Alignment &amp; 2D Stage</h3>
                <p className="text-xs text-slate-400">
                  Millisecond driver-seat propagation delay &amp; draggable L/R Balance + Front/Rear Fader stage
                </p>
              </div>
            </div>

            <div className={`space-y-4 ${activeDisabledClass}`}>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {(['all', 'driver', 'driver_rhd', 'center'] as const).map((stageKey) => {
                  const info = STAGE_PRESETS[stageKey];
                  const isSelected = dspSettings.listeningStage === stageKey;
                  return (
                    <button
                      key={stageKey}
                      type="button"
                      onClick={() => handleSelectStage(stageKey)}
                      className={`min-h-[52px] p-2.5 rounded-xl border text-left transition-transform active:scale-95 ${
                        isSelected
                          ? 'bg-slate-800 text-white shadow'
                          : 'border-slate-800 bg-black/40 text-slate-400 hover:text-white'
                      }`}
                      style={isSelected ? { borderColor: accentHex } : {}}
                    >
                      <div
                        className="text-xs font-bold"
                        style={isSelected ? { color: accentHex } : {}}
                      >
                        {info.label}
                      </div>
                      <div className="text-[10px] font-mono text-slate-500 mt-0.5">{info.desc}</div>
                    </button>
                  );
                })}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold text-white">Left Door Delay</span>
                    <span className="font-mono font-bold tabular-nums" style={{ color: accentHex }}>
                      {dspSettings.leftDelayMs.toFixed(1)} ms ({Math.round(dspSettings.leftDelayMs * 34.3)} cm)
                    </span>
                  </div>
                  <input
                    type="range"
                    aria-label="Left Channel Delay"
                    min={0}
                    max={5}
                    step={0.1}
                    value={dspSettings.leftDelayMs}
                    onChange={(e) =>
                      onChangeDspSettings((prev) => ({
                        ...prev,
                        listeningStage: 'custom',
                        leftDelayMs: Number(e.target.value),
                      }))
                    }
                    className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                    style={{ accentColor: accentHex }}
                  />
                </div>

                <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-2">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold text-white">Right Door Delay</span>
                    <span className="font-mono font-bold tabular-nums" style={{ color: accentHex }}>
                      {dspSettings.rightDelayMs.toFixed(1)} ms ({Math.round(dspSettings.rightDelayMs * 34.3)} cm)
                    </span>
                  </div>
                  <input
                    type="range"
                    aria-label="Right Channel Delay"
                    min={0}
                    max={5}
                    step={0.1}
                    value={dspSettings.rightDelayMs}
                    onChange={(e) =>
                      onChangeDspSettings((prev) => ({
                        ...prev,
                        listeningStage: 'custom',
                        rightDelayMs: Number(e.target.value),
                      }))
                    }
                    className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                    style={{ accentColor: accentHex }}
                  />
                </div>
              </div>
            </div>

            {/* 2D Cabin Soundstage Pad */}
            <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-2">
                <div className="flex items-center gap-2 text-xs font-bold text-white">
                  <Crosshair className="w-4 h-4" style={{ color: accentHex }} />
                  <span>2D Cabin Balance &amp; Fader Stage</span>
                </div>
                <span className="text-xs font-mono font-bold tabular-nums text-slate-200">
                  {dspSettings.balance === 0
                    ? 'L/R Center'
                    : dspSettings.balance < 0
                    ? `L ${Math.abs(dspSettings.balance)}%`
                    : `R ${dspSettings.balance}%`}{' '}
                  ·{' '}
                  {(dspSettings.fader ?? 0) === 0
                    ? 'F/R Center'
                    : (dspSettings.fader ?? 0) > 0
                    ? `Front ${dspSettings.fader}%`
                    : `Rear ${Math.abs(dspSettings.fader ?? 0)}%`}
                </span>
              </div>

              <div className="flex flex-col sm:flex-row items-center gap-5">
                <div
                  ref={stagePadRef}
                  role="slider"
                  aria-label="2D Cabin Stage Pad"
                  aria-valuetext={`Balance ${dspSettings.balance}, Fader ${dspSettings.fader ?? 0}`}
                  tabIndex={0}
                  onPointerDown={(e) => {
                    isDraggingStageRef.current = true;
                    (e.currentTarget as HTMLDivElement).setPointerCapture?.(e.pointerId);
                    updateStageFromPointer(e.clientX, e.clientY);
                  }}
                  onPointerMove={(e) => {
                    if (isDraggingStageRef.current) {
                      updateStageFromPointer(e.clientX, e.clientY);
                    }
                  }}
                  onPointerUp={() => {
                    isDraggingStageRef.current = false;
                  }}
                  onPointerCancel={() => {
                    isDraggingStageRef.current = false;
                  }}
                  className="relative w-56 h-44 rounded-2xl bg-[#050811] border border-slate-700/80 touch-none cursor-crosshair shrink-0 overflow-hidden select-none"
                >
                  <div className="absolute inset-x-0 top-1/2 h-px bg-slate-800/80" />
                  <div className="absolute inset-y-0 left-1/2 w-px bg-slate-800/80" />
                  <span className="absolute top-1.5 left-1/2 -translate-x-1/2 text-[9px] font-mono text-slate-500">FRONT</span>
                  <span className="absolute bottom-1.5 left-1/2 -translate-x-1/2 text-[9px] font-mono text-slate-500">REAR</span>
                  <span className="absolute left-2 top-1/2 -translate-y-1/2 text-[9px] font-mono text-slate-500">L</span>
                  <span className="absolute right-2 top-1/2 -translate-y-1/2 text-[9px] font-mono text-slate-500">R</span>

                  <div
                    className="absolute w-7 h-7 -ml-3.5 -mt-3.5 rounded-full border-2 border-white shadow-lg pointer-events-none transition-[left,top] duration-75"
                    style={{
                      left: `${((dspSettings.balance + 100) / 200) * 100}%`,
                      top: `${((100 - (dspSettings.fader ?? 0)) / 200) * 100}%`,
                      backgroundColor: accentHex,
                      boxShadow: `0 0 18px ${accentHex}`,
                    }}
                  />
                </div>

                <div className="flex-1 w-full grid grid-cols-2 gap-2">
                  {[
                    { label: 'Driver Focus', bal: -35, fader: 30 },
                    { label: 'Front Cabin', bal: 0, fader: 70 },
                    { label: 'All Center', bal: 0, fader: 0 },
                    { label: 'Rear Chill', bal: 0, fader: -65 },
                  ].map((snap) => {
                    const isSnap =
                      dspSettings.balance === snap.bal && (dspSettings.fader ?? 0) === snap.fader;
                    return (
                      <button
                        key={snap.label}
                        type="button"
                        onClick={() =>
                          onChangeDspSettings((prev) => ({
                            ...prev,
                            balance: snap.bal,
                            fader: snap.fader,
                          }))
                        }
                        className={`min-h-[44px] px-3 py-2 rounded-xl border text-xs font-bold transition-transform active:scale-95 ${
                          isSnap
                            ? 'bg-slate-800 text-white shadow'
                            : 'bg-black/50 border-slate-800 text-slate-300 hover:text-white'
                        }`}
                        style={isSnap ? { borderColor: accentHex, color: accentHex } : {}}
                      >
                        {snap.label}
                      </button>
                    );
                  })}
                </div>
              </div>
            </div>
          </div>

          {/* Crossover Slope (12 vs 24 dB/oct), Sub Exciter, Sub Delay & Phase */}
          <div className={`bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5 ${activeDisabledClass}`}>
            <div className="flex flex-wrap items-center justify-between gap-3 pb-3 border-b border-slate-800/60">
              <div className="flex items-center gap-3">
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center border shrink-0"
                  style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
                >
                  <Speaker className="w-5 h-5" style={{ color: accentHex }} />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white">Crossover Network &amp; Subwoofer Phase</h3>
                  <p className="text-xs text-slate-400">
                    Linkwitz-Riley 12 / 24 dB/oct slopes, trunk sub delay alignment &amp; sub-bass exciter
                  </p>
                </div>
              </div>

              {/* 12 dB/oct vs 24 dB/oct Slope Selector */}
              <div className="inline-flex rounded-xl bg-black/60 border border-slate-800 p-1">
                {([12, 24] as CrossoverSlopeDb[]).map((slope) => {
                  const isSel = (dspSettings.crossoverSlopeDb ?? 12) === slope;
                  return (
                    <button
                      key={slope}
                      type="button"
                      onClick={() =>
                        onChangeDspSettings((prev) => ({ ...prev, crossoverSlopeDb: slope }))
                      }
                      className={`min-h-[36px] px-3 py-1 rounded-lg text-xs font-mono font-bold transition-colors whitespace-nowrap ${
                        isSel ? 'text-black' : 'text-slate-400 hover:text-white'
                      }`}
                      style={isSel ? { backgroundColor: accentHex } : {}}
                    >
                      {slope} dB/oct {slope === 24 ? 'LR4' : 'BW2'}
                    </button>
                  );
                })}
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-2.5">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-white">Sub-Bass Boost</span>
                  <span className="text-xs font-mono font-bold tabular-nums" style={{ color: accentHex }}>
                    +{dspSettings.bassBoostDb} dB
                  </span>
                </div>
                <input
                  type="range"
                  aria-label="Sub Bass Boost"
                  min={0}
                  max={12}
                  step={0.5}
                  value={dspSettings.bassBoostDb}
                  onChange={(e) =>
                    onChangeDspSettings((prev) => ({
                      ...prev,
                      bassBoostDb: Number(e.target.value),
                    }))
                  }
                  className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                  style={{ accentColor: accentHex }}
                />
              </div>

              <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-2.5">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-white">Bass Center Frequency</span>
                  <span className="text-xs font-mono font-bold tabular-nums text-slate-200">
                    {dspSettings.bassBoostFreqHz} Hz
                  </span>
                </div>
                <input
                  type="range"
                  aria-label="Bass Boost Center Frequency"
                  min={40}
                  max={100}
                  step={5}
                  value={dspSettings.bassBoostFreqHz}
                  onChange={(e) =>
                    onChangeDspSettings((prev) => ({
                      ...prev,
                      bassBoostFreqHz: Number(e.target.value),
                    }))
                  }
                  className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                  style={{ accentColor: accentHex }}
                />
              </div>
            </div>

            {/* Door Speaker HPF */}
            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-200">Door Speaker High-Pass Crossover (HPF)</span>
                <span className="text-[11px] font-mono text-slate-400">
                  {dspSettings.hpfCutoffHz === 0
                    ? 'Full-Range'
                    : `${dspSettings.hpfCutoffHz} Hz · ${dspSettings.crossoverSlopeDb} dB/oct`}
                </span>
              </div>
              <div className="grid grid-cols-5 gap-2">
                {HPF_OPTIONS.map((hz) => {
                  const isSelected = dspSettings.hpfCutoffHz === hz;
                  return (
                    <button
                      key={hz}
                      type="button"
                      onClick={() =>
                        onChangeDspSettings((prev) => ({ ...prev, hpfCutoffHz: hz }))
                      }
                      className={`min-h-[44px] px-2 py-2 rounded-xl border text-xs font-bold transition-transform active:scale-95 whitespace-nowrap ${
                        isSelected
                          ? 'bg-slate-800 text-white shadow'
                          : 'border-slate-800 bg-black/40 text-slate-400 hover:text-white'
                      }`}
                      style={isSelected ? { borderColor: accentHex, color: accentHex } : {}}
                    >
                      {hz === 0 ? 'Off' : `${hz} Hz`}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Sub LPF, Sub Trim, Phase Flip & Sub Delay Alignment */}
            <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-4">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div>
                  <div className="text-sm font-bold text-white">Subwoofer LPF, Phase &amp; Trunk Delay Alignment</div>
                  <p className="text-xs text-slate-400">
                    Align sub-band arrival time with front door mid-woofers to pull trunk bass onto the dash
                  </p>
                </div>
                <button
                  type="button"
                  onClick={() =>
                    onChangeDspSettings((prev) => ({
                      ...prev,
                      subPhaseInverted: !prev.subPhaseInverted,
                    }))
                  }
                  className={`min-h-[40px] px-3 py-1.5 rounded-xl text-xs font-mono font-bold border transition-transform active:scale-95 ${
                    dspSettings.subPhaseInverted
                      ? 'text-black border-transparent shadow'
                      : 'bg-slate-900 border-slate-700 text-slate-200 hover:text-white'
                  }`}
                  style={dspSettings.subPhaseInverted ? { backgroundColor: accentHex } : {}}
                >
                  Phase: {dspSettings.subPhaseInverted ? '180° Inverted' : '0° Normal'}
                </button>
              </div>

              <div className="grid grid-cols-5 gap-2">
                {SUB_LPF_OPTIONS_HZ.map((hz: SubLpfHz) => {
                  const isSelected = dspSettings.subLpfHz === hz;
                  return (
                    <button
                      key={hz}
                      type="button"
                      onClick={() =>
                        onChangeDspSettings((prev) => ({ ...prev, subLpfHz: hz }))
                      }
                      className={`min-h-[40px] px-2 py-1.5 rounded-xl border text-xs font-bold transition-transform active:scale-95 ${
                        isSelected
                          ? 'bg-slate-800 text-white shadow'
                          : 'border-slate-800 bg-black/50 text-slate-400 hover:text-white'
                      }`}
                      style={isSelected ? { borderColor: accentHex, color: accentHex } : {}}
                    >
                      {hz === 0 ? 'Full (100Hz)' : `${hz} Hz`}
                    </button>
                  );
                })}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold text-slate-300">Sub-Band Output Trim</span>
                    <span className="font-mono font-bold tabular-nums" style={{ color: accentHex }}>
                      {dspSettings.subTrimDb > 0 ? `+${dspSettings.subTrimDb}` : dspSettings.subTrimDb} dB
                    </span>
                  </div>
                  <input
                    type="range"
                    aria-label="Sub-Band Trim dB"
                    min={-6}
                    max={6}
                    step={0.5}
                    value={dspSettings.subTrimDb}
                    onChange={(e) =>
                      onChangeDspSettings((prev) => ({
                        ...prev,
                        subTrimDb: Number(e.target.value),
                      }))
                    }
                    className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                    style={{ accentColor: accentHex }}
                  />
                </div>

                <div className="space-y-1.5">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold text-slate-300">Sub Delay Alignment</span>
                    <span className="font-mono font-bold tabular-nums" style={{ color: accentHex }}>
                      {(dspSettings.subDelayMs ?? 0).toFixed(1)} ms ({Math.round((dspSettings.subDelayMs ?? 0) * 34.3)} cm)
                    </span>
                  </div>
                  <input
                    type="range"
                    aria-label="Sub Delay Alignment ms"
                    min={0}
                    max={15}
                    step={0.5}
                    value={dspSettings.subDelayMs ?? 0}
                    onChange={(e) =>
                      onChangeDspSettings((prev) => ({
                        ...prev,
                        subDelayMs: Number(e.target.value),
                      }))
                    }
                    className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                    style={{ accentColor: accentHex }}
                  />
                </div>
              </div>
            </div>
          </div>

          {/* Dynamics Compressor, Peak Limiter Ceiling & Mono Fold */}
          <div className={`bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-4 ${activeDisabledClass}`}>
            <div className="flex items-center gap-3 pb-3 border-b border-slate-800/60">
              <div
                className="w-10 h-10 rounded-xl flex items-center justify-center border shrink-0"
                style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
              >
                <Shield className="w-5 h-5" style={{ color: accentHex }} />
              </div>
              <div>
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <span>Dynamic Compressor, Peak Limiter &amp; Mono Fold</span>
                  <Activity className="w-4 h-4 text-slate-400" />
                </h3>
                <p className="text-xs text-slate-400">
                  RMS leveling, brickwall DAC clipping ceiling &amp; phase-safe mono summing
                </p>
              </div>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {(['off', 'balanced', 'night_highway', 'club_punch'] as CompressorProfileId[]).map(
                (id) => {
                  const prof = COMPRESSOR_PROFILES[id];
                  const isSelected = dspSettings.compressorProfile === id;
                  return (
                    <button
                      key={id}
                      type="button"
                      onClick={() =>
                        onChangeDspSettings((prev) => ({ ...prev, compressorProfile: id }))
                      }
                      className={`min-h-[60px] p-3 rounded-xl border text-left transition-transform active:scale-95 ${
                        isSelected
                          ? 'bg-slate-800 text-white shadow'
                          : 'border-slate-800 bg-black/40 text-slate-400 hover:text-white'
                      }`}
                      style={isSelected ? { borderColor: accentHex } : {}}
                    >
                      <div
                        className="font-bold text-xs"
                        style={isSelected ? { color: accentHex } : {}}
                      >
                        {prof.label}
                      </div>
                      <div className="text-[10px] text-slate-500 mt-0.5 line-clamp-2">{prof.desc}</div>
                    </button>
                  );
                }
              )}
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
              <div className="p-3.5 rounded-xl bg-black/40 border border-slate-800 space-y-2">
                <div className="text-xs font-bold text-white">Master Peak Limiter Ceiling</div>
                <div className="grid grid-cols-4 gap-1.5">
                  {LIMITER_CEILING_OPTIONS_DB.map((dbVal: LimiterCeilingDb) => {
                    const isSel = (dspSettings.peakLimiterCeilingDb ?? -0.5) === dbVal;
                    return (
                      <button
                        key={dbVal}
                        type="button"
                        onClick={() =>
                          onChangeDspSettings((prev) => ({
                            ...prev,
                            peakLimiterCeilingDb: dbVal,
                          }))
                        }
                        className={`min-h-[38px] px-1.5 py-1 rounded-lg border text-[11px] font-mono font-bold transition-colors ${
                          isSel
                            ? 'bg-slate-800 text-white'
                            : 'border-slate-800 bg-black/50 text-slate-400 hover:text-white'
                        }`}
                        style={isSel ? { borderColor: accentHex, color: accentHex } : {}}
                      >
                        {dbVal === 0 ? '0.0dB' : `${dbVal}dB`}
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="p-3.5 rounded-xl bg-black/40 border border-slate-800 flex items-center justify-between gap-3">
                <div>
                  <div className="text-xs font-bold text-white flex items-center gap-1.5">
                    <Volume2 className="w-3.5 h-3.5" style={{ color: accentHex }} />
                    <span>Mono Cabin Fold-Down</span>
                  </div>
                  <p className="text-[10px] text-slate-400 mt-0.5">
                    Sums L+R cleanly for spoken radio or vintage hard-panned stereo masters
                  </p>
                </div>
                <button
                  type="button"
                  aria-label="Toggle Mono Cabin Fold-Down"
                  onClick={() =>
                    onChangeDspSettings((prev) => ({
                      ...prev,
                      monoFoldDown: !prev.monoFoldDown,
                    }))
                  }
                  className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                    dspSettings.monoFoldDown ? '' : 'bg-slate-700'
                  }`}
                  style={dspSettings.monoFoldDown ? { backgroundColor: accentHex } : {}}
                >
                  <div
                    className={`w-4 h-4 rounded-full bg-black transition-transform ${
                      dspSettings.monoFoldDown ? 'translate-x-6' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================ */}
      {/* TAB 4: CABIN RTA & 5-SECOND PINK-NOISE AUTO-CALIBRATION      */}
      {/* ============================================================ */}
      {activeSubTab === 'rta' && (
        <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5">
          <div className="flex flex-wrap items-center justify-between gap-4 pb-3 border-b border-slate-800/60">
            <div className="flex items-center gap-3">
              <div
                className="w-10 h-10 rounded-xl flex items-center justify-center border shrink-0"
                style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
              >
                <Mic className="w-5 h-5" style={{ color: accentHex }} />
              </div>
              <div>
                <h3 className="text-base font-bold text-white">Cabin RTA &amp; 5-Second Pink-Noise Calibration</h3>
                <p className="text-xs text-slate-400">
                  Plays controlled -3 dB/oct pink noise, measures 16-band cabin acoustics, and computes Harman target EQ offsets
                </p>
              </div>
            </div>

            <button
              type="button"
              onClick={handleStartRtaSweep}
              className="min-h-[44px] px-5 py-2.5 rounded-xl text-xs font-bold flex items-center gap-2 transition-transform active:scale-95 text-black shrink-0"
              style={{ backgroundColor: rtaRunning ? '#f59e0b' : accentHex }}
            >
              {rtaRunning ? (
                <>
                  <Square className="w-4 h-4 fill-current" />
                  <span>Stop Sweep ({Math.round(rtaProgress * 100)}%)</span>
                </>
              ) : (
                <>
                  <Activity className="w-4 h-4" />
                  <span>Run 5s Pink-Noise Calibration</span>
                </>
              )}
            </button>
          </div>

          {/* Live 16-Band RTA Bar Graph */}
          <div className="bg-black/60 border border-slate-800 rounded-2xl p-4 space-y-3">
            <div className="flex items-center justify-between text-[11px] font-mono text-slate-400">
              <span>REAL-TIME 16-BAND OCTAVE ANALYZER</span>
              <span>
                {rtaSource === 'microphone'
                  ? 'SOURCE: CABIN MICROPHONE'
                  : rtaSource === 'loopback'
                  ? 'SOURCE: DSP LOOPBACK ANALYZER'
                  : 'READY TO SWEEP'}
              </span>
            </div>

            <div className="h-40 grid grid-cols-[repeat(16,minmax(0,1fr))] gap-1.5 items-end pt-4 px-1">
              {DSP_16_FREQUENCIES.map((freq, idx) => {
                const dbVal = rtaResult
                  ? rtaResult.measuredDb[idx]
                  : rtaLiveBands[idx] ?? -60;
                const normHeight = Math.max(6, Math.min(100, ((dbVal + 70) / 60) * 100));
                const suggested = rtaResult?.suggestedCorrections[idx];
                return (
                  <div key={freq} className="h-full flex flex-col items-center justify-end gap-1">
                    {suggested !== undefined && (
                      <span
                        className="text-[9px] font-mono font-bold tabular-nums"
                        style={{ color: suggested > 0 ? accentHex : suggested < 0 ? '#94a3b8' : '#64748b' }}
                      >
                        {suggested > 0 ? `+${suggested}` : suggested}
                      </span>
                    )}
                    <div className="w-full flex-1 flex items-end bg-slate-900/70 rounded-md overflow-hidden p-0.5">
                      <div
                        className="w-full rounded-sm transition-[height] duration-100"
                        style={{
                          height: `${normHeight}%`,
                          backgroundColor: accentHex,
                          opacity: rtaRunning || rtaResult ? 0.9 : 0.28,
                        }}
                      />
                    </div>
                    <span className="text-[9px] font-mono text-slate-500">
                      {formatFreqShort(freq)}
                    </span>
                  </div>
                );
              })}
            </div>

            {rtaRunning && (
              <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
                <div
                  className="h-full transition-[width] duration-100"
                  style={{ width: `${Math.round(rtaProgress * 100)}%`, backgroundColor: accentHex }}
                />
              </div>
            )}
          </div>

          {/* 1-Tap Apply Calibration Card */}
          {rtaResult && (
            <div className="p-4 rounded-xl bg-slate-900/80 border border-slate-700/80 flex flex-wrap items-center justify-between gap-4">
              <div className="space-y-1">
                <div className="text-sm font-bold text-white flex items-center gap-2">
                  <CheckCircle2 className="w-4 h-4" style={{ color: accentHex }} />
                  <span>Cabin Acoustic Profile Ready ({rtaResult.source === 'microphone' ? 'Live Mic' : 'Loopback'})</span>
                </div>
                <p className="text-xs text-slate-400">
                  Damped inverse corrections computed toward the Harman automotive cabin curve (clamped safely to ±6 dB).
                </p>
              </div>
              <button
                type="button"
                onClick={handleApplyRtaCalibration}
                className="min-h-[44px] px-4 py-2 rounded-xl text-xs font-bold text-black transition-transform active:scale-95 shrink-0"
                style={{ backgroundColor: accentHex }}
              >
                {rtaAppliedNotice ? 'Calibration Applied to 16-Band EQ' : 'Apply Cabin Calibration'}
              </button>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
