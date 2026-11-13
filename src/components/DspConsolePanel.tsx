import React, { useMemo, useState } from 'react';
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
} from 'lucide-react';
import {
  DSP_16_FREQUENCIES,
  FACTORY_DSP_PRESETS,
  STAGE_PRESETS,
  COMPRESSOR_PROFILES,
  ZERO_BANDS_16,
  DspSettings,
  DspPreset16,
  DspBandGains16,
  HpfCutoffHz,
  ListeningStagePreset,
  CompressorProfileId,
  interpolate5To16Bands,
  downsample16To5Bands,
  calculateLoudnessContourGains,
  buildDspResponseCurvePoints,
} from '../utils/dspConsole';

interface DspConsolePanelProps {
  accentHex: string;
  dspSettings: DspSettings;
  onChangeDspSettings: (next: DspSettings | ((prev: DspSettings) => DspSettings)) => void;
  customPresets: DspPreset16[];
  onSaveCustomPreset: (name: string) => void;
  onDeleteCustomPreset: (presetId: string) => void;
  masterVolume: number;
}

const BAND_5_LABELS = ['60Hz', '250Hz', '1kHz', '4kHz', '12kHz'] as const;
const HPF_OPTIONS: HpfCutoffHz[] = [0, 60, 80, 100, 120];

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
}) => {
  const [newPresetName, setNewPresetName] = useState('');

  const allPresets = useMemo(
    () => [...FACTORY_DSP_PRESETS, ...customPresets],
    [customPresets]
  );

  const curvePoints = useMemo(
    () => buildDspResponseCurvePoints(dspSettings, masterVolume, 640, 140),
    [dspSettings, masterVolume]
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
    onChangeDspSettings((prev) => ({
      ...prev,
      dspEnabled: true,
      selectedPresetId: preset.id,
      bands16: [...preset.bands16] as DspBandGains16,
      bassBoostDb: preset.bassBoostDb ?? prev.bassBoostDb,
      bassBoostFreqHz: preset.bassBoostFreqHz ?? prev.bassBoostFreqHz,
      preampDb: preset.preampDb ?? prev.preampDb,
      hpfCutoffHz: preset.hpfCutoffHz ?? prev.hpfCutoffHz,
      loudnessContour: preset.loudnessContour ?? prev.loudnessContour,
      compressorProfile: preset.compressorProfile ?? prev.compressorProfile,
    }));
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

  const activeDisabledClass = dspSettings.dspEnabled
    ? ''
    : 'opacity-45 pointer-events-none select-none';

  return (
    <div className="space-y-6 min-w-0 w-full">
      {/* ============================================================ */}
      {/* CARD 1: MASTER ESP/DSP HEADER, PRESETS, RESPONSE CURVE & EQ  */}
      {/* ============================================================ */}
      <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5 min-w-0 overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-4 pb-3 border-b border-slate-800/60">
          <div className="flex items-center gap-3">
            <div
              className="w-11 h-11 rounded-xl flex items-center justify-center border shrink-0"
              style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
            >
              <Sliders className="w-5 h-5" style={{ color: accentHex }} />
            </div>
            <div>
              <h3 className="text-base font-bold text-white flex items-center gap-2 flex-wrap">
                <span>Pro Headunit ESP &amp; 16-Band DSP</span>
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
              <p className="text-xs text-slate-400">
                Real-time Web Audio biquad filtering, sub-bass shelf, time alignment &amp; dynamics limiting
              </p>
            </div>
          </div>

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

        {/* Factory + Custom User Presets */}
        <div className="space-y-3">
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
              className="min-h-[36px] px-3 py-1.5 rounded-lg text-xs font-semibold bg-black/50 border border-slate-800 text-slate-300 hover:text-white hover:border-slate-700 flex items-center gap-1.5 whitespace-nowrap"
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
                    className={`w-full min-h-[54px] p-2.5 pr-7 rounded-xl border text-left transition-all active:scale-95 ${
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
                Protects the 73.4 Hz door woofer resonance while restoring chest punch and attenuating harsh 2.5–9 kHz tweeter glare. Tip: If tweeters still sound forward, switch the attenuator jumper inside the passive crossover box to <span className="font-mono text-white">-3 dB</span>.
              </p>
            </div>
          )}

          {/* Save Custom User Preset Bar */}
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
              className="min-h-[44px] px-4 py-2 rounded-xl text-xs font-bold flex items-center gap-1.5 transition-all disabled:opacity-40 whitespace-nowrap shrink-0 text-black"
              style={{ backgroundColor: accentHex }}
            >
              <Save className="w-3.5 h-3.5" />
              Save Preset
            </button>
          </form>
        </div>

        {/* Live SVG Frequency Response Curve + EQ Mode Switch + Preamp */}
        <div className={`space-y-4 ${activeDisabledClass}`}>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div className="inline-flex rounded-xl bg-black/60 border border-slate-800 p-1">
              <button
                type="button"
                onClick={() =>
                  onChangeDspSettings((prev) => ({ ...prev, eqViewMode: '16band' }))
                }
                className={`min-h-[36px] px-3 py-1 rounded-lg text-xs font-bold transition-colors whitespace-nowrap ${
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
                className={`min-h-[36px] px-3 py-1 rounded-lg text-xs font-bold transition-colors whitespace-nowrap ${
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
            <div className="flex items-center justify-between text-[10px] font-mono text-slate-500 px-1">
              <span>DSP FREQUENCY RESPONSE (25 Hz – 18 kHz)</span>
              <span className="tabular-nums">
                HPF: {dspSettings.hpfCutoffHz === 0 ? 'OFF' : `${dspSettings.hpfCutoffHz}Hz`} · SUB:{' '}
                +{dspSettings.bassBoostDb}dB
              </span>
            </div>
            <svg
              viewBox="0 0 640 140"
              className="w-full h-32 block overflow-visible"
              role="img"
              aria-label="Live DSP Frequency Response Curve"
            >
              {/* Horizontal dB Gridlines (+12, +6, 0, -6, -12) */}
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
              {/* Log Frequency Vertical Guides (60, 250, 1k, 4k, 12k) */}
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
              {/* Curve Fill & Stroke */}
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

          {/* Interactive EQ Sliders (16-Band Pro OR 5-Band Quick) */}
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
      </div>

      {/* ============================================================ */}
      {/* CARD 2: BASS ENGINE, DOOR SPEAKER CROSSOVER & LOUDNESS       */}
      {/* ============================================================ */}
      <div className={`bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5 ${activeDisabledClass}`}>
        <div className="flex items-center gap-3 pb-3 border-b border-slate-800/60">
          <div
            className="w-10 h-10 rounded-xl flex items-center justify-center border shrink-0"
            style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
          >
            <Speaker className="w-5 h-5" style={{ color: accentHex }} />
          </div>
          <div>
            <h3 className="text-base font-bold text-white">Sub-Bass Exciter, Crossover &amp; Loudness</h3>
            <p className="text-xs text-slate-400">
              Hardware low-shelf punch, door speaker High-Pass protection &amp; Fletcher-Munson contour
            </p>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          {/* Sub-Bass Boost Amount */}
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

          {/* Sub-Bass Center Frequency */}
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

        {/* Door-Speaker High-Pass Filter Crossover */}
        <div className="space-y-2">
          <div className="flex items-center justify-between">
            <span className="text-xs font-bold text-slate-200">
              Door Speaker High-Pass Crossover (HPF)
            </span>
            <span className="text-[11px] font-mono text-slate-400">
              {dspSettings.hpfCutoffHz === 0
                ? 'Full-Range (No Cut)'
                : `Cuts sub-bass below ${dspSettings.hpfCutoffHz} Hz`}
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
                  className={`min-h-[44px] px-2 py-2 rounded-xl border text-xs font-bold transition-all active:scale-95 whitespace-nowrap ${
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

        {/* Dynamic Fletcher-Munson Loudness Contour */}
        <div className="p-4 rounded-xl bg-black/40 border border-slate-800 flex items-center justify-between gap-4">
          <div className="space-y-0.5">
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
              Automatically compensates human ear bass/treble sensitivity drops at lower cabin listening volumes.
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

      {/* ============================================================ */}
      {/* CARD 3: TIME ALIGNMENT ("SWEET SPOT"), BALANCE & 3D STAGE    */}
      {/* ============================================================ */}
      <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5">
        <div className="flex items-center gap-3 pb-3 border-b border-slate-800/60">
          <div
            className="w-10 h-10 rounded-xl flex items-center justify-center border shrink-0"
            style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
          >
            <Compass className="w-5 h-5" style={{ color: accentHex }} />
          </div>
          <div>
            <h3 className="text-base font-bold text-white">Cabin Time Alignment &amp; 3D Soundstage</h3>
            <p className="text-xs text-slate-400">
              Millisecond driver-seat propagation delay, stereo L/R balance &amp; Mid/Side soundstage expander
            </p>
          </div>
        </div>

        {/* Listening Sweet Spot Stage Presets */}
        <div className={`space-y-4 ${activeDisabledClass}`}>
          <div>
            <label className="text-xs font-bold text-slate-300 block mb-2">
              Time Alignment Sweet Spot
            </label>
            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
              {(['all', 'driver', 'driver_rhd', 'center'] as const).map((stageKey) => {
                const info = STAGE_PRESETS[stageKey];
                const isSelected = dspSettings.listeningStage === stageKey;
                return (
                  <button
                    key={stageKey}
                    type="button"
                    onClick={() => handleSelectStage(stageKey)}
                    className={`min-h-[52px] p-2.5 rounded-xl border text-left transition-all active:scale-95 ${
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

          {/* 3D Stereo Width */}
          <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-2">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2 text-xs font-bold text-white">
                <Waves className="w-4 h-4" style={{ color: accentHex }} />
                <span>3D Stereo Soundstage Width (Mid/Side Matrix)</span>
              </div>
              <span className="text-xs font-mono font-bold tabular-nums" style={{ color: accentHex }}>
                {dspSettings.stereoWidthPercent}%{' '}
                {dspSettings.stereoWidthPercent === 0
                  ? '(Mono)'
                  : dspSettings.stereoWidthPercent > 105
                  ? '(Wide)'
                  : dspSettings.stereoWidthPercent === 100
                  ? '(Natural)'
                  : '(Tight)'}
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

        {/* L/R Stereo Balance (always active) */}
        <div className="p-4 rounded-xl bg-black/40 border border-slate-800 space-y-2">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2 text-xs font-bold text-white">
              <Volume2 className="w-4 h-4" style={{ color: accentHex }} />
              <span>Left / Right Stereo Balance</span>
            </div>
            <div className="flex items-center gap-2">
              <span className="text-xs font-mono font-bold tabular-nums text-slate-200">
                {dspSettings.balance === 0
                  ? 'Center (0)'
                  : dspSettings.balance < 0
                  ? `L ${Math.abs(dspSettings.balance)}%`
                  : `R ${dspSettings.balance}%`}
              </span>
              {dspSettings.balance !== 0 && (
                <button
                  type="button"
                  onClick={() =>
                    onChangeDspSettings((prev) => ({ ...prev, balance: 0 }))
                  }
                  className="text-[10px] font-bold px-2 py-1 rounded-md bg-slate-800 text-slate-300 hover:text-white"
                >
                  Center
                </button>
              )}
            </div>
          </div>
          <input
            type="range"
            aria-label="Left Right Balance"
            min={-100}
            max={100}
            step={5}
            value={dspSettings.balance}
            onChange={(e) =>
              onChangeDspSettings((prev) => ({
                ...prev,
                balance: Number(e.target.value),
              }))
            }
            className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
            style={{ accentColor: accentHex }}
          />
        </div>
      </div>

      {/* ============================================================ */}
      {/* CARD 4: DYNAMIC RANGE COMPRESSOR / PEAK LIMITER PROFILES     */}
      {/* ============================================================ */}
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
              <span>Dynamic Range Compressor &amp; Limiter</span>
              <Activity className="w-4 h-4 text-slate-400" />
            </h3>
            <p className="text-xs text-slate-400">
              Hardware RMS leveling for highway ambient roar, podcasts, and high-impact bass protection
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
                  className={`min-h-[60px] p-3 rounded-xl border text-left transition-all active:scale-95 ${
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
      </div>
    </div>
  );
};
