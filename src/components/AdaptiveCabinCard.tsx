import React, { useState } from 'react';
import { Mic, Gauge, Sliders, ChevronDown, ChevronUp, ShieldAlert, Activity } from 'lucide-react';
import {
  ADAPTIVE_CABIN_PROFILES,
  ADAPTIVE_PROFILE_ORDER,
  AdaptiveCabinConfig,
  AdaptiveCabinFocus,
  AdaptiveCabinSensitivity,
  AdaptiveCabinSource,
  AdaptiveDspDelta,
  AdaptiveEvaluationState,
  CabinMicStatus,
  calculateNoiseGainCalibrationDelta,
} from '../utils/adaptiveCabinProfile';

interface AdaptiveCabinCardProps {
  accentHex: string;
  config: AdaptiveCabinConfig;
  onChangeConfig: (next: AdaptiveCabinConfig | ((prev: AdaptiveCabinConfig) => AdaptiveCabinConfig)) => void;
  evalState: AdaptiveEvaluationState;
  activeDelta: AdaptiveDspDelta;
  cabinNoiseDb: number | null;
  micStatus: CabinMicStatus;
  currentSpeedKmH: number;
}

const SOURCE_OPTIONS: Array<{ id: AdaptiveCabinSource; label: string; hint: string }> = [
  { id: 'hybrid', label: 'Hybrid (Mic + GPS)', hint: 'Uses whichever detects higher cabin noise' },
  { id: 'microphone', label: 'Cabin Mic', hint: 'Real-time interior dB SPL sensing' },
  { id: 'speed', label: 'GPS Speed', hint: 'Vehicle km/h thresholds only' },
];

const SENSITIVITY_OPTIONS: Array<{ id: AdaptiveCabinSensitivity; label: string; mult: string }> = [
  { id: 'subtle', label: 'Subtle', mult: '0.65×' },
  { id: 'balanced', label: 'Balanced', mult: '1.0×' },
  { id: 'aggressive', label: 'Aggressive', mult: '1.35×' },
];

const FOCUS_OPTIONS: Array<{ id: AdaptiveCabinFocus; label: string }> = [
  { id: 'driver_lhd', label: 'Driver (LHD)' },
  { id: 'driver_rhd', label: 'Driver (RHD)' },
  { id: 'front_center', label: 'Front Cabin' },
];

function formatSigned(val: number, suffix = ''): string {
  if (val > 0) return `+${val}${suffix}`;
  return `${val}${suffix}`;
}

export const AdaptiveCabinCard: React.FC<AdaptiveCabinCardProps> = ({
  accentHex,
  config,
  onChangeConfig,
  evalState,
  activeDelta,
  cabinNoiseDb,
  micStatus,
  currentSpeedKmH,
}) => {
  const [showThresholds, setShowThresholds] = useState(false);

  const activeSpec = ADAPTIVE_CABIN_PROFILES[evalState.activeProfile];
  const pendingSpec = evalState.pendingProfile ? ADAPTIVE_CABIN_PROFILES[evalState.pendingProfile] : null;
  const noiseBarPercent =
    cabinNoiseDb !== null ? Math.max(4, Math.min(100, ((cabinNoiseDb - 32) / 62) * 100)) : 0;
  const displaySpeedKmH =
    config.enabled && evalState.lastEvaluatedAtMs > 0 ? evalState.smoothedSpeedKmH : currentSpeedKmH;
  const speedBarPercent = Math.max(0, Math.min(100, (displaySpeedKmH / Math.max(140, config.speedHighKmH + 20)) * 100));
  const calibration = calculateNoiseGainCalibrationDelta(
    micStatus === 'active' ? cabinNoiseDb : null,
    config,
    activeDelta
  );
  // Map calibration delta (-12 dB .. +16 dB) to 0..100% bar marker (0 dB sits at ~43%)
  const deltaMarkerPercent =
    calibration.deltaDb !== null
      ? Math.max(4, Math.min(96, ((calibration.deltaDb + 12) / 28) * 100))
      : 43;
  const deltaToneColor =
    calibration.status === 'balanced'
      ? accentHex
      : calibration.status === 'under_compensated'
        ? '#f59e0b'
        : calibration.status === 'over_compensated'
          ? '#38bdf8'
          : '#94a3b8';

  const updateField = <K extends keyof AdaptiveCabinConfig>(key: K, value: AdaptiveCabinConfig[K]) => {
    onChangeConfig((prev) => {
      const next = { ...prev, [key]: value };
      // Maintain monotonic ordering across speed & noise thresholds
      if (key === 'speedCityKmH') {
        next.speedHighwayKmH = Math.max(next.speedHighwayKmH, (value as number) + 15);
        next.speedHighKmH = Math.max(next.speedHighKmH, next.speedHighwayKmH + 15);
      } else if (key === 'speedHighwayKmH') {
        next.speedCityKmH = Math.min(next.speedCityKmH, (value as number) - 15);
        next.speedHighKmH = Math.max(next.speedHighKmH, (value as number) + 15);
      } else if (key === 'speedHighKmH') {
        next.speedHighwayKmH = Math.min(next.speedHighwayKmH, (value as number) - 15);
        next.speedCityKmH = Math.min(next.speedCityKmH, next.speedHighwayKmH - 15);
      } else if (key === 'noiseModerateDb') {
        next.noiseLoudDb = Math.max(next.noiseLoudDb, (value as number) + 5);
        next.noiseExtremeDb = Math.max(next.noiseExtremeDb, next.noiseLoudDb + 5);
      } else if (key === 'noiseLoudDb') {
        next.noiseModerateDb = Math.min(next.noiseModerateDb, (value as number) - 5);
        next.noiseExtremeDb = Math.max(next.noiseExtremeDb, (value as number) + 5);
      } else if (key === 'noiseExtremeDb') {
        next.noiseLoudDb = Math.min(next.noiseLoudDb, (value as number) - 5);
        next.noiseModerateDb = Math.min(next.noiseModerateDb, next.noiseLoudDb - 5);
      }
      return next;
    });
  };

  return (
    <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5">
      {/* Header Row with Prominent On/Off Toggle */}
      <div className="flex flex-wrap items-center justify-between gap-4 pb-3 border-b border-slate-800/60">
        <div className="flex items-center gap-3 min-w-0">
          <div
            className="w-10 h-10 rounded-xl flex items-center justify-center border shrink-0"
            style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
          >
            <Gauge className="w-5 h-5" style={{ color: accentHex }} />
          </div>
          <div className="min-w-0">
            <h3 className="text-base font-bold text-white leading-snug">
              Auto Cabin Noise &amp; Speed DSP Profile
            </h3>
            <p className="text-xs text-slate-400">
              {config.enabled
                ? `${activeSpec.label} · Gain ${formatSigned(activeDelta.gainDb, ' dB')} · Bal ${formatSigned(activeDelta.balanceDelta)} · Fad ${formatSigned(activeDelta.faderDelta)}`
                : 'Off · Base manual DSP gain and balance preserved untouched'}
            </p>
          </div>
        </div>

        <div className="flex items-center gap-3 shrink-0">
          <span className="text-xs font-medium text-slate-300">
            {config.enabled ? 'Enabled' : 'Off'}
          </span>
          <button
            type="button"
            role="switch"
            aria-checked={config.enabled}
            aria-label="Toggle Automated Cabin Noise and Speed DSP Profile Switching"
            onClick={() => onChangeConfig((prev) => ({ ...prev, enabled: !prev.enabled }))}
            className={`w-14 h-8 rounded-full p-1 transition-colors shrink-0 focus-visible:outline-2 focus-visible:outline-offset-2 ${
              config.enabled ? '' : 'bg-slate-700'
            }`}
            style={config.enabled ? { backgroundColor: accentHex } : {}}
          >
            <div
              className={`w-6 h-6 rounded-full bg-black transition-transform duration-150 ${
                config.enabled ? 'translate-x-6' : 'translate-x-0'
              }`}
            />
          </button>
        </div>
      </div>

      {/* Live Sensor Readout + Active Profile Strip */}
      <div className={`space-y-4 transition-opacity ${config.enabled ? '' : 'opacity-50'}`}>
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
          {/* Cabin Mic dB Level Meter */}
          <div className="p-3.5 rounded-xl bg-black/45 border border-slate-800 space-y-2">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-300">
                <Mic className="w-3.5 h-3.5 text-slate-400" />
                <span>Cabin Noise Level</span>
              </div>
              <span
                className="text-xs font-mono font-bold tabular-nums"
                style={{ color: config.enabled && micStatus === 'active' ? accentHex : '#94a3b8' }}
              >
                {config.source === 'speed'
                  ? 'Mic Bypassed'
                  : micStatus === 'active' && cabinNoiseDb !== null
                    ? `${cabinNoiseDb.toFixed(1)} dB SPL`
                    : micStatus === 'starting'
                      ? 'Listening...'
                      : micStatus === 'denied'
                        ? 'Permission Denied'
                        : micStatus === 'unavailable'
                          ? 'No Mic Input'
                          : 'Standby'}
              </span>
            </div>
            <div className="w-full h-2 bg-slate-900 rounded-full overflow-hidden">
              <div
                className="h-full rounded-full transition-[width] duration-200"
                style={{
                  width: `${config.enabled && micStatus === 'active' ? noiseBarPercent : 0}%`,
                  backgroundColor:
                    cabinNoiseDb !== null && cabinNoiseDb >= config.noiseExtremeDb
                      ? '#f59e0b'
                      : accentHex,
                }}
              />
            </div>
            <div className="flex justify-between text-[11px] font-mono tabular-nums text-slate-500">
              <span>City {config.noiseModerateDb} dB</span>
              <span>Hwy {config.noiseLoudDb} dB</span>
              <span>High {config.noiseExtremeDb} dB</span>
            </div>
          </div>

          {/* Live Vehicle Speed Readout */}
          <div className="p-3.5 rounded-xl bg-black/45 border border-slate-800 space-y-2">
            <div className="flex items-center justify-between gap-2">
              <div className="flex items-center gap-1.5 text-xs font-semibold text-slate-300">
                <Gauge className="w-3.5 h-3.5 text-slate-400" />
                <span>Vehicle GPS Speed</span>
              </div>
              <span
                className="text-xs font-mono font-bold tabular-nums"
                style={{ color: config.enabled && config.source !== 'microphone' ? accentHex : '#94a3b8' }}
              >
                {config.source === 'microphone'
                  ? 'Speed Bypassed'
                  : config.enabled && evalState.lastEvaluatedAtMs > 0 && Math.abs(currentSpeedKmH - displaySpeedKmH) >= 2
                    ? `${Math.round(displaySpeedKmH)} km/h (raw ${Math.round(currentSpeedKmH)})`
                    : `${Math.round(displaySpeedKmH)} km/h`}
              </span>
            </div>
            <div className="w-full h-2 bg-slate-900 rounded-full overflow-hidden">
              <div
                className="h-full rounded-full transition-[width] duration-200"
                style={{
                  width: `${config.enabled && config.source !== 'microphone' ? speedBarPercent : 0}%`,
                  backgroundColor: accentHex,
                }}
              />
            </div>
            <div className="flex justify-between text-[11px] font-mono tabular-nums text-slate-500">
              <span>City {config.speedCityKmH}</span>
              <span>Hwy {config.speedHighwayKmH}</span>
              <span>High {config.speedHighKmH} km/h</span>
            </div>
          </div>
        </div>

        {/* Real-Time Noise ↔ DSP Gain Calibration Delta Indicator */}
        <div
          className="px-3.5 py-2.5 rounded-xl bg-black/50 border border-slate-800/90 flex flex-col sm:flex-row sm:items-center justify-between gap-2.5"
          aria-live="polite"
        >
          <div className="flex items-center gap-2 min-w-0">
            <Activity className="w-3.5 h-3.5 shrink-0" style={{ color: deltaToneColor }} />
            <span className="text-[11px] font-semibold text-slate-300 whitespace-nowrap">
              Calibration Δ
            </span>
            <span
              className="text-xs font-mono font-bold tabular-nums whitespace-nowrap"
              style={{ color: deltaToneColor }}
            >
              {calibration.deltaDb !== null
                ? `${formatSigned(calibration.deltaDb, ' dB')}`
                : calibration.status === 'speed_only'
                  ? `DSP ${formatSigned(calibration.appliedGainDb, ' dB')}`
                  : '—'}
            </span>
            <span className="text-[11px] text-slate-500 truncate">
              {calibration.noiseAboveFloorDb !== null
                ? `(${formatSigned(calibration.noiseAboveFloorDb)} dB noise vs ${formatSigned(calibration.appliedGainDb)} dB gain)`
                : calibration.status === 'speed_only'
                  ? '(GPS speed mode)'
                  : config.enabled
                    ? '(waiting for cabin mic)'
                    : '(enable to calibrate)'}
            </span>
          </div>

          <div className="flex items-center gap-2.5 shrink-0">
            <div
              className="relative w-24 h-1.5 bg-slate-900 rounded-full overflow-hidden border border-slate-800"
              title="Calibration balance: left = DSP gain exceeds noise, center = balanced, right = noise exceeds gain"
            >
              <div
                className="absolute top-0 bottom-0 w-px bg-slate-600"
                style={{ left: '43%' }}
                aria-hidden="true"
              />
              <div
                className="absolute top-1/2 -translate-y-1/2 w-2 h-2 rounded-full transition-[left] duration-200"
                style={{
                  left: `calc(${deltaMarkerPercent}% - 4px)`,
                  backgroundColor: deltaToneColor,
                }}
              />
            </div>
            <span
              className="text-[10px] font-mono font-bold uppercase tracking-wider whitespace-nowrap"
              style={{ color: deltaToneColor }}
            >
              {calibration.hint}
            </span>
          </div>
        </div>

        {/* Mic Permission Fallback Warning */}
        {config.enabled && (micStatus === 'denied' || micStatus === 'unavailable') && config.source !== 'speed' && (
          <div className="p-3 rounded-xl bg-amber-500/10 border border-amber-500/35 text-xs text-amber-200 flex items-start gap-2.5">
            <ShieldAlert className="w-4 h-4 text-amber-400 shrink-0 mt-0.5" />
            <div>
              {config.source === 'hybrid'
                ? 'Microphone access is unavailable or blocked — automatically continuing with GPS speed threshold switching.'
                : 'Microphone permission was denied or no audio input was detected. Allow microphone access or switch Source to GPS Speed.'}
            </div>
          </div>
        )}

        {/* 4-Stage Acoustic Profile State Strip */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
          {ADAPTIVE_PROFILE_ORDER.map((pid) => {
            const spec = ADAPTIVE_CABIN_PROFILES[pid];
            const isActive = config.enabled && evalState.activeProfile === pid;
            const isPending = config.enabled && evalState.pendingProfile === pid;
            return (
              <div
                key={pid}
                className={`p-3 rounded-xl border transition-colors ${
                  isActive
                    ? 'bg-slate-800/90 text-white'
                    : 'bg-black/40 border-slate-800 text-slate-400'
                }`}
                style={isActive ? { borderColor: accentHex } : {}}
              >
                <div className="flex items-center justify-between gap-1">
                  <span
                    className="text-xs font-bold truncate"
                    style={isActive ? { color: accentHex } : {}}
                  >
                    {spec.shortLabel}
                  </span>
                  <span className="text-[11px] font-mono tabular-nums text-slate-400">
                    {isActive ? 'Active' : isPending ? 'Next' : `+${spec.baseGainDb}dB`}
                  </span>
                </div>
                <div className="text-[11px] font-mono tabular-nums text-slate-500 mt-1">
                  {pid === 'quiet'
                    ? 'Base DSP'
                    : `Bal ${spec.baseBalanceShift} · Fad +${spec.baseFaderShift}`}
                </div>
              </div>
            );
          })}
        </div>

        {/* Live Applied Compensation Summary */}
        <div className="flex flex-wrap items-center justify-between gap-3 px-3.5 py-2.5 rounded-xl bg-black/55 border border-slate-800/90 text-xs">
          <div className="text-slate-300">
            <span className="font-semibold text-white">{activeSpec.label}</span>
            <span className="text-slate-500 mx-1.5" aria-hidden="true">·</span>
            <span className="text-slate-400">
              {pendingSpec
                ? `Transitioning to ${pendingSpec.shortLabel}...`
                : evalState.triggerReason === 'microphone'
                  ? 'Triggered by Cabin Mic'
                  : evalState.triggerReason === 'speed'
                    ? 'Triggered by GPS Speed'
                    : evalState.triggerReason === 'hybrid'
                      ? 'Triggered by Mic & Speed'
                      : config.enabled
                        ? 'Monitoring cabin environment'
                        : 'Adaptive switching paused'}
            </span>
          </div>
          <div className="flex items-center gap-3 font-mono tabular-nums text-[11px]">
            <span style={{ color: activeDelta.gainDb > 0 ? accentHex : '#94a3b8' }}>
              Gain {formatSigned(activeDelta.gainDb, ' dB')}
            </span>
            <span className="text-slate-600" aria-hidden="true">/</span>
            <span style={{ color: activeDelta.balanceDelta !== 0 ? accentHex : '#94a3b8' }}>
              Bal {formatSigned(activeDelta.balanceDelta)}
            </span>
            <span className="text-slate-600" aria-hidden="true">/</span>
            <span style={{ color: activeDelta.faderDelta !== 0 ? accentHex : '#94a3b8' }}>
              Fad {formatSigned(activeDelta.faderDelta)}
            </span>
          </div>
        </div>

        {/* Controls: Sensor Source, Sensitivity, Cabin Focus */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-3">
          <div className="space-y-1.5">
            <div className="text-xs font-semibold text-slate-300">Detection Source</div>
            <div className="grid grid-cols-3 gap-1 p-1 rounded-xl bg-black/55 border border-slate-800">
              {SOURCE_OPTIONS.map((opt) => {
                const selected = config.source === opt.id;
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => updateField('source', opt.id)}
                    className={`min-h-[40px] px-2 py-1.5 rounded-lg text-[11px] font-bold transition-colors whitespace-nowrap ${
                      selected ? 'text-black' : 'text-slate-400 hover:text-white'
                    }`}
                    style={selected ? { backgroundColor: accentHex } : {}}
                    title={opt.hint}
                  >
                    {opt.id === 'hybrid' ? 'Auto' : opt.id === 'microphone' ? 'Mic' : 'Speed'}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="space-y-1.5">
            <div className="text-xs font-semibold text-slate-300">Compensation Strength</div>
            <div className="grid grid-cols-3 gap-1 p-1 rounded-xl bg-black/55 border border-slate-800">
              {SENSITIVITY_OPTIONS.map((opt) => {
                const selected = config.sensitivity === opt.id;
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => updateField('sensitivity', opt.id)}
                    className={`min-h-[40px] px-2 py-1.5 rounded-lg text-[11px] font-bold transition-colors whitespace-nowrap ${
                      selected ? 'text-black' : 'text-slate-400 hover:text-white'
                    }`}
                    style={selected ? { backgroundColor: accentHex } : {}}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>
          </div>

          <div className="space-y-1.5">
            <div className="text-xs font-semibold text-slate-300">Balance &amp; Stage Focus</div>
            <div className="grid grid-cols-3 gap-1 p-1 rounded-xl bg-black/55 border border-slate-800">
              {FOCUS_OPTIONS.map((opt) => {
                const selected = config.cabinFocus === opt.id;
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => updateField('cabinFocus', opt.id)}
                    className={`min-h-[40px] px-2 py-1.5 rounded-lg text-[11px] font-bold transition-colors whitespace-nowrap ${
                      selected ? 'text-black' : 'text-slate-400 hover:text-white'
                    }`}
                    style={selected ? { backgroundColor: accentHex } : {}}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* Collapsible Threshold Tuning Drawer */}
        <div className="pt-1">
          <button
            type="button"
            onClick={() => setShowThresholds((prev) => !prev)}
            className="min-h-[40px] w-full px-3.5 py-2 rounded-xl bg-black/45 border border-slate-800 hover:border-slate-700 text-xs font-semibold text-slate-300 hover:text-white flex items-center justify-between transition-colors"
          >
            <span className="flex items-center gap-2">
              <Sliders className="w-3.5 h-3.5 text-slate-400" />
              <span>Customize Noise (dB) &amp; Speed (km/h) Thresholds</span>
            </span>
            {showThresholds ? <ChevronUp className="w-4 h-4" /> : <ChevronDown className="w-4 h-4" />}
          </button>

          {showThresholds && (
            <div className="mt-3 grid grid-cols-1 sm:grid-cols-2 gap-3">
              {/* Mic Noise Thresholds */}
              <div className="p-4 rounded-xl bg-black/45 border border-slate-800 space-y-3">
                <div className="text-xs font-bold text-white">Microphone Thresholds (dB SPL)</div>
                {[
                  { key: 'noiseModerateDb' as const, label: 'City Noise Trigger', min: 45, max: 68 },
                  { key: 'noiseLoudDb' as const, label: 'Highway Noise Trigger', min: 55, max: 80 },
                  { key: 'noiseExtremeDb' as const, label: 'High Noise Trigger', min: 65, max: 90 },
                ].map((item) => (
                  <div key={item.key} className="space-y-1">
                    <div className="flex justify-between text-[11px]">
                      <span className="text-slate-400">{item.label}</span>
                      <span className="font-mono font-bold tabular-nums text-white">
                        {config[item.key]} dB
                      </span>
                    </div>
                    <input
                      type="range"
                      aria-label={item.label}
                      min={item.min}
                      max={item.max}
                      step={1}
                      value={config[item.key]}
                      onChange={(e) => updateField(item.key, Number(e.target.value))}
                      className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                      style={{ accentColor: accentHex }}
                    />
                  </div>
                ))}
              </div>

              {/* Speed Thresholds */}
              <div className="p-4 rounded-xl bg-black/45 border border-slate-800 space-y-3">
                <div className="text-xs font-bold text-white">GPS Speed Thresholds (km/h)</div>
                {[
                  { key: 'speedCityKmH' as const, label: 'City Cruise Speed', min: 25, max: 75 },
                  { key: 'speedHighwayKmH' as const, label: 'Highway Cruise Speed', min: 55, max: 125 },
                  { key: 'speedHighKmH' as const, label: 'High Speed / Autobahn', min: 85, max: 170 },
                ].map((item) => (
                  <div key={item.key} className="space-y-1">
                    <div className="flex justify-between text-[11px]">
                      <span className="text-slate-400">{item.label}</span>
                      <span className="font-mono font-bold tabular-nums text-white">
                        {config[item.key]} km/h
                      </span>
                    </div>
                    <input
                      type="range"
                      aria-label={item.label}
                      min={item.min}
                      max={item.max}
                      step={5}
                      value={config[item.key]}
                      onChange={(e) => updateField(item.key, Number(e.target.value))}
                      className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                      style={{ accentColor: accentHex }}
                    />
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
