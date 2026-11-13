import React, { useState } from 'react';
import {
  Sliders,
  Volume2,
  VolumeX,
  Car,
  Compass,
  Moon,
  Sun,
  SunMoon,
  Clock,
  Sparkles,
  Download,
  Trash2,
  RefreshCw,
  Cloud,
  FolderOpen,
  Terminal,
  Activity,
  FileText,
  Copy,
  Check,
  CheckCircle,
  Radio,
  Gauge,
  Zap,
  ShieldCheck,
  Code,
  Bug,
  FolderCheck,
  FolderSearch,
  KeyRound,
  FileAudio,
  Power,
  Lock,
  Keyboard,
  Flame,
  Smartphone,
  SlidersHorizontal,
} from 'lucide-react';
import { DspPreset16, DspSettings } from '../utils/dspConsole';
import { DspConsolePanel } from './DspConsolePanel';
import {
  SteeringAction,
  SteeringKeyBindings,
  STEERING_ACTION_LABELS,
  formatSteeringKeyLabel,
} from '../utils/carConvenience';
import { PWAInstallButton } from './PWAComponents';
import { DiagnosticLogEntry } from '../App';

export interface SettingsPanelProps {
  accentHex: string;
  accentKey: string;
  onSetAccentKey: (key: any) => void;
  accentPalette: Record<string, { hex: string; name: string }>;

  // Pro 16-Band DSP Console
  dspSettings: DspSettings;
  onChangeDspSettings: (next: DspSettings | ((prev: DspSettings) => DspSettings)) => void;
  customDspPresets: DspPreset16[];
  onSaveCustomDspPreset: (name: string) => void;
  onDeleteCustomDspPreset: (presetId: string) => void;
  masterVolume: number;

  // Attenuation
  isAttenuated: boolean;
  onToggleAttenuation: () => void;

  // Vehicle SCV
  scvSensitivity: 'off' | 'low' | 'medium' | 'high';
  onChangeScvSensitivity: (sens: 'off' | 'low' | 'medium' | 'high') => void;
  currentGpsSpeed: number;
  calculatedSpeedBoostDb: number;

  // Gestures
  enableSwipeGestures: boolean;
  onToggleSwipeGestures: (enabled: boolean) => void;

  // MediaTek 825X_Pro Hardware Optimization
  mediatekEcoMode: boolean;
  onToggleMediatekEcoMode: (enabled: boolean) => void;

  // Theme & Live GPS Status
  cockpitThemeMode: 'auto' | 'night' | 'day';
  onChangeThemeMode: (mode: 'auto' | 'night' | 'day') => void;
  gpsStatus: 'active' | 'waiting' | 'denied' | 'unavailable' | 'off';
  onRequestGpsRefresh: () => void;

  // Podcast Mode & Playback Speed
  isPodcastMode: boolean;
  onTogglePodcastMode: (enabled: boolean) => void;
  playbackRate: number;
  onChangePlaybackRate: (rate: number) => void;

  // Sleep Timer
  sleepTimerMinutes: number | null;
  sleepTimerRemainingSec: number | null;
  onSetSleepTimer: (minutes: number | null) => void;

  // Tunnel & Deadzone Guard
  deadzoneGuardMode: 'standard' | 'aggressive';
  onChangeDeadzoneGuard: (mode: 'standard' | 'aggressive') => void;

  // Live Header Range Metadata Hydration
  autoFetchMetadata: boolean;
  onToggleAutoFetchMetadata: (enabled: boolean) => void;

  // Nextcloud Connection & Folders
  isLoggedIn: boolean;
  username: string;
  musicFolder: string;
  onOpenFolderBrowser: () => void;
  onRescanMusic: () => void;
  isScanning: boolean;
  onSignOut: () => void;
  onOpenSignInModal: () => void;
  serverUrl: string;

  // Offline Storage
  offlineStorageBytes: number;
  offlineTrackCount: number;
  onBatchSyncCurrentFolder: () => void;
  isBatchSyncing: boolean;
  batchSyncProgress: { current: number; total: number; trackTitle: string } | null;
  onClearOfflineStorage: () => void;
  storageQuota: { usageBytes: number; quotaBytes: number; persisted: boolean } | null;
  onRequestPersistentStorage: () => void;

  // Category 1: Startup & App
  autoResumeOnBoot: boolean;
  onToggleAutoResumeOnBoot: (enabled: boolean) => void;
  autoResumeSoftRamp: boolean;
  onToggleAutoResumeSoftRamp: (enabled: boolean) => void;

  // Category 2: Driving Ergonomics & Night Comfort
  idleScreensaverSeconds: number;
  onChangeIdleScreensaverSeconds: (secs: number) => void;
  nightBrightness: number;
  onChangeNightBrightness: (val: number) => void;
  warmAmberFilter: boolean;
  onToggleWarmAmberFilter: (enabled: boolean) => void;
  driverSafeMode: boolean;
  onToggleDriverSafeMode: (enabled: boolean) => void;
  swcBindings: SteeringKeyBindings;
  onUpdateSwcBinding: (action: SteeringAction, keyBinding: string) => void;
  onResetSwcBindings: () => void;
  onListeningSwcChange?: (listening: boolean) => void;

  // Category 3: Audio Polish & Smart Rolling Cache
  replayGainEnabled: boolean;
  onToggleReplayGain: (enabled: boolean) => void;
  crossfadeSeconds: number;
  onChangeCrossfadeSeconds: (secs: number) => void;
  smartRollingCache: boolean;
  onToggleSmartRollingCache: (enabled: boolean) => void;
  offlineCacheCapMb: number;
  onChangeOfflineCacheCapMb: (mb: number) => void;

  // Diagnostics & Wire Logs
  diagnosticLogs: DiagnosticLogEntry[];
  onRunDiagnostics: () => void;
  isDiagnosing: boolean;
  serverProxyLogs: string;
  onFetchServerProxyLogs: () => void;
  isLoadingServerLogs: boolean;
  onClearDiagnostics: () => void;
  onCopyDiagnosticReport: () => void;
  copiedReport: boolean;
}

export const SettingsPanel: React.FC<SettingsPanelProps> = ({
  accentHex,
  accentKey,
  onSetAccentKey,
  accentPalette,

  dspSettings,
  onChangeDspSettings,
  customDspPresets,
  onSaveCustomDspPreset,
  onDeleteCustomDspPreset,
  masterVolume,

  isAttenuated,
  onToggleAttenuation,

  scvSensitivity,
  onChangeScvSensitivity,
  currentGpsSpeed,
  calculatedSpeedBoostDb,

  enableSwipeGestures,
  onToggleSwipeGestures,

  mediatekEcoMode,
  onToggleMediatekEcoMode,

  cockpitThemeMode,
  onChangeThemeMode,
  gpsStatus,
  onRequestGpsRefresh,

  isPodcastMode,
  onTogglePodcastMode,
  playbackRate,
  onChangePlaybackRate,

  sleepTimerMinutes,
  sleepTimerRemainingSec,
  onSetSleepTimer,

  deadzoneGuardMode,
  onChangeDeadzoneGuard,

  autoFetchMetadata,
  onToggleAutoFetchMetadata,

  isLoggedIn,
  username,
  musicFolder,
  onOpenFolderBrowser,
  onRescanMusic,
  isScanning,
  onSignOut,
  onOpenSignInModal,
  serverUrl,

  offlineStorageBytes,
  offlineTrackCount,
  onBatchSyncCurrentFolder,
  isBatchSyncing,
  batchSyncProgress,
  onClearOfflineStorage,
  storageQuota,
  onRequestPersistentStorage,

  autoResumeOnBoot,
  onToggleAutoResumeOnBoot,
  autoResumeSoftRamp,
  onToggleAutoResumeSoftRamp,

  idleScreensaverSeconds,
  onChangeIdleScreensaverSeconds,
  nightBrightness,
  onChangeNightBrightness,
  warmAmberFilter,
  onToggleWarmAmberFilter,
  driverSafeMode,
  onToggleDriverSafeMode,
  swcBindings,
  onUpdateSwcBinding,
  onResetSwcBindings,
  onListeningSwcChange,

  replayGainEnabled,
  onToggleReplayGain,
  crossfadeSeconds,
  onChangeCrossfadeSeconds,
  smartRollingCache,
  onToggleSmartRollingCache,
  offlineCacheCapMb,
  onChangeOfflineCacheCapMb,

  diagnosticLogs,
  onRunDiagnostics,
  isDiagnosing,
  serverProxyLogs,
  onFetchServerProxyLogs,
  isLoadingServerLogs,
  onClearDiagnostics,
  onCopyDiagnosticReport,
  copiedReport,
}) => {
  const [activeCategory, setActiveCategory] = useState<
    'acoustics' | 'vehicle' | 'podcasts' | 'cloud' | 'offline' | 'diagnostics'
  >('acoustics');

  const [diagTab, setDiagTab] = useState<'client' | 'server'>('client');
  const [diagFilter, setDiagFilter] = useState<'all' | 'errors' | 'success'>('all');
  const [expandedLogId, setExpandedLogId] = useState<string | null>(null);
  const [listeningAction, setListeningAction] = useState<SteeringAction | null>(null);

  React.useEffect(() => {
    onListeningSwcChange?.(listeningAction !== null);
    if (!listeningAction) return;

    const handleCaptureKey = (e: KeyboardEvent) => {
      e.preventDefault();
      e.stopPropagation();
      if (e.key === 'Escape') {
        setListeningAction(null);
        return;
      }
      const captured = e.code && e.code !== 'Unidentified' ? e.code : e.key;
      if (captured) {
        onUpdateSwcBinding(listeningAction, captured);
      }
      setListeningAction(null);
    };

    window.addEventListener('keydown', handleCaptureKey, true);
    return () => {
      window.removeEventListener('keydown', handleCaptureKey, true);
      onListeningSwcChange?.(false);
    };
  }, [listeningAction, onUpdateSwcBinding, onListeningSwcChange]);

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
  };

  const formatTimerSec = (seconds: number) => {
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  };

  return (
    <div className="w-full max-w-4xl mx-auto space-y-6 pb-24">
      {/* Settings Top Category Navigation Bar */}
      <div className="grid grid-cols-2 sm:grid-cols-6 gap-2 p-1.5 bg-[#0b1120] border border-slate-800 rounded-2xl shadow-xl">
        {[
          { id: 'acoustics', label: 'Acoustics & EQ', icon: Sliders },
          { id: 'vehicle', label: 'Vehicle & SCV', icon: Car },
          { id: 'podcasts', label: 'Podcast & Speed', icon: Radio },
          { id: 'offline', label: 'Offline Roadtrip', icon: Download },
          { id: 'cloud', label: 'Nextcloud Sync', icon: Cloud },
          { id: 'diagnostics', label: 'Wire Logs', icon: Terminal },
        ].map((cat) => {
          const Icon = cat.icon;
          const isActive = activeCategory === cat.id;
          return (
            <button
              key={cat.id}
              onClick={() => setActiveCategory(cat.id as any)}
              className={`py-2.5 px-2 rounded-xl text-xs font-bold transition-all flex flex-col sm:flex-row items-center justify-center gap-1.5 active:scale-95 ${
                isActive ? 'text-black shadow-md' : 'text-slate-400 hover:text-white hover:bg-slate-900/40'
              }`}
              style={isActive ? { backgroundColor: accentHex } : {}}
            >
              <Icon className="w-4 h-4 shrink-0" />
              <span className="truncate">{cat.label}</span>
            </button>
          );
        })}
      </div>

      {/* ============================================================== */}
      {/* CATEGORY 1: PRO HEADUNIT ESP/DSP CONSOLE & PLAYBACK POLISH     */}
      {/* ============================================================== */}
      {activeCategory === 'acoustics' && (
        <div className="space-y-6 animate-in fade-in duration-150">
          <DspConsolePanel
            accentHex={accentHex}
            dspSettings={dspSettings}
            onChangeDspSettings={onChangeDspSettings}
            customPresets={customDspPresets}
            onSaveCustomPreset={onSaveCustomDspPreset}
            onDeleteCustomPreset={onDeleteCustomDspPreset}
            masterVolume={masterVolume}
          />

          {/* Quick Talk Attenuation, ReplayGain & Crossfade */}
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl divide-y divide-slate-800/60 overflow-hidden shadow-xl">
            {/* Quick Attenuation / "Talk" Ducking (-20 dB) */}
            <div className="p-4 sm:p-5 flex items-center justify-between gap-4">
              <div className="space-y-0.5">
                <div className="text-sm font-bold text-white">Quick Attenuation / "Talk" Mode (-20 dB)</div>
                <p className="text-xs text-slate-400">
                  Ducks playback volume by -20dB without pausing for drive-thrus, toll booths, and passenger chats. (Shortcut: <kbd className="px-1.5 py-0.5 rounded bg-black border border-slate-700 font-mono text-white text-[10px]">T</kbd>)
                </p>
              </div>

              <button
                onClick={onToggleAttenuation}
                className={`px-4 py-2 rounded-xl text-xs font-bold transition-all shrink-0 active:scale-95 flex items-center gap-1.5 ${
                  isAttenuated ? 'bg-amber-400 text-black shadow-lg animate-pulse' : 'bg-slate-800 text-slate-200 border border-slate-700 hover:text-white'
                }`}
              >
                {isAttenuated ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
                {isAttenuated ? 'Ducked (-20dB)' : 'Test Duck'}
              </button>
            </div>

            {/* Smart ReplayGain Track Loudness Matching */}
            <div className="p-4 sm:p-5 flex items-center justify-between gap-4">
              <div className="space-y-0.5">
                <div className="text-sm font-bold text-white flex items-center gap-2">
                  <span>ReplayGain Track Volume Matching</span>
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-cyan-500/20 text-cyan-300 font-mono">
                    ID3 / Vorbis Tag
                  </span>
                </div>
                <p className="text-xs text-slate-400">
                  Reads embedded <code className="text-slate-300">REPLAYGAIN_TRACK_GAIN</code> tags to match perceived volume between masters and decades.
                </p>
              </div>

              <button
                type="button"
                aria-label="Toggle ReplayGain Track Volume Matching"
                onClick={() => onToggleReplayGain(!replayGainEnabled)}
                className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                  replayGainEnabled ? 'bg-lime-400' : 'bg-slate-700'
                }`}
                style={replayGainEnabled ? { backgroundColor: accentHex } : {}}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-black transition-transform ${
                    replayGainEnabled ? 'translate-x-6' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            {/* Optional Track Crossfade (0s = Off / Gapless up to 6s) */}
            <div className="p-4 sm:p-5 space-y-2.5">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-sm font-bold text-white flex items-center gap-2">
                    <SlidersHorizontal className="w-4 h-4" style={{ color: accentHex }} />
                    <span>Smooth Track Crossfade</span>
                  </div>
                  <p className="text-xs text-slate-400">
                    Equal-power fade across track changes (0s = instant gapless playback).
                  </p>
                </div>
                <span className="text-xs font-mono font-bold px-2.5 py-1 rounded-lg bg-black/60 border border-slate-800 text-white">
                  {crossfadeSeconds === 0 ? 'Off (Gapless)' : `${crossfadeSeconds}s`}
                </span>
              </div>
              <input
                type="range"
                aria-label="Crossfade Duration Seconds"
                min={0}
                max={6}
                step={1}
                value={crossfadeSeconds}
                onChange={(e) => onChangeCrossfadeSeconds(Number(e.target.value))}
                className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                style={{ accentColor: accentHex }}
              />
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* CATEGORY 2: VEHICLE SPEED COMPENSATED VOLUME & COCKPIT ERGONOMICS */}
      {/* ============================================================== */}
      {activeCategory === 'vehicle' && (
        <div className="space-y-6 animate-in fade-in duration-150">
          {/* Speed-Compensated Volume (SCV) */}
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
              <div className="flex items-center gap-3">
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center border"
                  style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
                >
                  <Gauge className="w-5 h-5" style={{ color: accentHex }} />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white">Speed-Compensated Volume (SDVC)</h3>
                  <p className="text-xs text-slate-400">
                    Scales volume dynamically with GPS speed to neutralize cabin wind and tire rumble
                  </p>
                </div>
              </div>

              {/* Live GPS Speed Badge */}
              <div className="flex items-center gap-2 bg-black/60 px-3 py-1.5 rounded-xl border border-slate-800">
                <Compass className="w-4 h-4 text-lime-400 animate-spin" style={{ animationDuration: '8s' }} />
                <span className="text-xs font-mono font-bold text-white">{currentGpsSpeed} km/h</span>
                <span className="text-[10px] font-mono text-slate-400">
                  (+{calculatedSpeedBoostDb} dB)
                </span>
              </div>
            </div>

            <div>
              <label className="text-xs font-semibold text-slate-300 block mb-2">Compensation Curve</label>
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
                {[
                  { id: 'off', label: 'Disabled', desc: 'No volume adjustments' },
                  { id: 'low', label: 'Low (+1dB/30km/h)', desc: 'For quiet luxury sedans' },
                  { id: 'medium', label: 'Medium (+2dB/30km/h)', desc: 'Balanced road noise' },
                  { id: 'high', label: 'High (+3dB/30km/h)', desc: 'SUVs, convertibles, highway' },
                ].map((curve) => {
                  const isSelected = scvSensitivity === curve.id;
                  return (
                    <button
                      key={curve.id}
                      onClick={() => onChangeScvSensitivity(curve.id as any)}
                      className={`p-3 rounded-xl border text-left transition-all active:scale-95 ${
                        isSelected
                          ? 'border-white bg-slate-800 text-white shadow'
                          : 'border-slate-800 bg-black/40 text-slate-400 hover:text-white'
                      }`}
                      style={isSelected ? { borderColor: accentHex } : {}}
                    >
                      <div className="font-bold text-xs" style={isSelected ? { color: accentHex } : {}}>
                        {curve.label}
                      </div>
                      <div className="text-[10px] text-slate-500 mt-0.5">{curve.desc}</div>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          {/* Touch & Display Ergonomics */}
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl divide-y divide-slate-800/60 overflow-hidden shadow-xl">
            {/* Cockpit Swipe Gestures */}
            <div className="p-4 sm:p-5 flex items-center justify-between gap-4">
              <div className="space-y-0.5">
                <div className="text-sm font-bold text-white">No-Look Cockpit Swipe Gestures</div>
                <p className="text-xs text-slate-400">
                  Swipe anywhere on Player to skip track (Left = Next, Right = Prev), 2-finger tap to Play/Pause.
                </p>
              </div>

              <button
                onClick={() => onToggleSwipeGestures(!enableSwipeGestures)}
                className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                  enableSwipeGestures ? 'bg-lime-400' : 'bg-slate-700'
                }`}
                style={enableSwipeGestures ? { backgroundColor: accentHex } : {}}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-black transition-transform ${
                    enableSwipeGestures ? 'translate-x-6' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            {/* Day / Tunnel / Night Modes */}
            <div className="p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <div className="text-sm font-bold text-white">Cockpit Day / Night Display Mode</div>
                <p className="text-xs text-slate-400">
                  Auto adapts to solar time or enforces pitch-black OLED to avoid windshield reflection.
                </p>
              </div>

              <div className="flex bg-black/60 p-1 rounded-xl border border-slate-800 text-xs">
                {[
                  { id: 'night', label: 'Night (OLED)', icon: Moon },
                  { id: 'auto', label: 'Auto (Solar)', icon: SunMoon },
                  { id: 'day', label: 'Day (High Contrast)', icon: Sun },
                ].map((m) => {
                  const Icon = m.icon;
                  const isSel = cockpitThemeMode === m.id;
                  return (
                    <button
                      key={m.id}
                      onClick={() => onChangeThemeMode(m.id as any)}
                      className={`px-3 py-1.5 rounded-lg font-bold flex items-center gap-1.5 transition-all ${
                        isSel ? 'text-black shadow' : 'text-slate-400 hover:text-white'
                      }`}
                      style={isSel ? { backgroundColor: accentHex } : {}}
                    >
                      <Icon className="w-3.5 h-3.5" />
                      {m.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Ambient Lighting Color */}
            <div className="p-4 sm:p-5 space-y-3">
              <div className="text-sm font-bold text-white">MBUX Ambient Lighting Accent</div>
              <div className="flex items-center gap-4 flex-wrap">
                {(Object.keys(accentPalette) as Array<keyof typeof accentPalette>).map((key) => {
                  const acc = accentPalette[key];
                  const isSelected = accentKey === key;
                  return (
                    <button
                      key={key}
                      onClick={() => onSetAccentKey(key)}
                      className="flex flex-col items-center gap-1.5 transition-transform active:scale-95"
                    >
                      <div
                        className="w-10 h-10 rounded-full border-2 transition-all flex items-center justify-center shadow-lg"
                        style={{
                          backgroundColor: acc.hex,
                          borderColor: isSelected ? '#ffffff' : 'transparent',
                          boxShadow: isSelected ? `0 0 16px ${acc.hex}` : 'none',
                        }}
                      >
                        {isSelected && <Check className="w-4 h-4 text-black stroke-[3]" />}
                      </div>
                      <span className={`text-[11px] ${isSelected ? 'font-bold text-white' : 'text-slate-400'}`}>
                        {acc.name}
                      </span>
                    </button>
                  );
                })}
              </div>
            </div>
          </div>

          {/* MediaTek 825X_Pro Octa-Core Hardware Optimization Card */}
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
              <div className="flex items-center gap-3">
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center border"
                  style={{ backgroundColor: `${accentHex}15`, borderColor: `${accentHex}30` }}
                >
                  <Zap className="w-5 h-5" style={{ color: accentHex }} />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-bold text-white">MediaTek 825X_Pro Hardware Boost</h3>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-mono bg-lime-950/60 text-lime-400 border border-lime-800/60">
                      Octa-Core / Mali-G52
                    </span>
                  </div>
                  <p className="text-xs text-slate-400">
                    Thermal throttling prevention & GPU compositor optimization for Android 9+ automotive headunits
                  </p>
                </div>
              </div>

              <button
                onClick={() => onToggleMediatekEcoMode(!mediatekEcoMode)}
                className={`w-12 h-6 rounded-full p-1 transition-colors ${
                  mediatekEcoMode ? 'bg-lime-400' : 'bg-slate-700'
                }`}
                style={mediatekEcoMode ? { backgroundColor: accentHex } : {}}
                title="Toggle MediaTek Hardware Optimization"
              >
                <div
                  className={`w-4 h-4 rounded-full bg-black transition-transform ${
                    mediatekEcoMode ? 'translate-x-6' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            {/* Hardware Benefit Badges */}
            <div className="grid grid-cols-1 sm:grid-cols-3 gap-2.5 text-xs">
              <div className="p-3 rounded-xl bg-black/40 border border-slate-800/80 space-y-1">
                <div className="font-semibold text-white flex items-center gap-1.5">
                  <Activity className="w-3.5 h-3.5 text-lime-400" />
                  30 FPS Canvas Cap
                </div>
                <div className="text-[11px] text-slate-400">
                  Saves ~50% GPU load on Mali-G52 & stops thermal throttling inside dash cavity
                </div>
              </div>

              <div className="p-3 rounded-xl bg-black/40 border border-slate-800/80 space-y-1">
                <div className="font-semibold text-white flex items-center gap-1.5">
                  <ShieldCheck className="w-3.5 h-3.5 text-cyan-400" />
                  Zero-Alloc Buffers
                </div>
                <div className="text-[11px] text-slate-400">
                  Pre-allocates typed arrays & eliminates Android V8 Garbage Collection micro-stutters
                </div>
              </div>

              <div className="p-3 rounded-xl bg-black/40 border border-slate-800/80 space-y-1">
                <div className="font-semibold text-white flex items-center gap-1.5">
                  <Gauge className="w-3.5 h-3.5 text-amber-400" />
                  DOM Windowing
                </div>
                <div className="text-[11px] text-slate-400">
                  Limits rendered song cards to 50 at a time for lag-free 60fps scrolling
                </div>
              </div>
            </div>
          </div>

          {/* Ignition Startup, Night Dimmer, Driver Safe UI & Standalone PWA */}
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl divide-y divide-slate-800/60 overflow-hidden shadow-xl">
            {/* Auto-Resume on Boot */}
            <div className="p-4 sm:p-5 flex items-center justify-between gap-4">
              <div className="space-y-0.5">
                <div className="text-sm font-bold text-white flex items-center gap-2">
                  <Power className="w-4 h-4" style={{ color: accentHex }} />
                  <span>Auto-Resume Last Session on Ignition / Boot</span>
                </div>
                <p className="text-xs text-slate-400">
                  Restores your last playing track, queue, shuffle/repeat state, and exact timestamp on startup. Turn off to start idle.
                </p>
              </div>
              <button
                type="button"
                aria-label="Toggle Auto-Resume on Boot"
                onClick={() => onToggleAutoResumeOnBoot(!autoResumeOnBoot)}
                className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                  autoResumeOnBoot ? 'bg-lime-400' : 'bg-slate-700'
                }`}
                style={autoResumeOnBoot ? { backgroundColor: accentHex } : {}}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-black transition-transform ${
                    autoResumeOnBoot ? 'translate-x-6' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            {/* Soft Volume Ramp */}
            {autoResumeOnBoot && (
              <div className="p-4 sm:p-5 flex items-center justify-between gap-4 bg-black/20">
                <div className="space-y-0.5">
                  <div className="text-sm font-bold text-white">Soft Cabin Volume Fade-In (1.5s)</div>
                  <p className="text-xs text-slate-400">
                    Gradually fades audio in from 12% to your set level on boot so cabin speakers never blast unexpectedly.
                  </p>
                </div>
                <button
                  type="button"
                  aria-label="Toggle Soft Volume Fade-In"
                  onClick={() => onToggleAutoResumeSoftRamp(!autoResumeSoftRamp)}
                  className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                    autoResumeSoftRamp ? 'bg-lime-400' : 'bg-slate-700'
                  }`}
                  style={autoResumeSoftRamp ? { backgroundColor: accentHex } : {}}
                >
                  <div
                    className={`w-4 h-4 rounded-full bg-black transition-transform ${
                      autoResumeSoftRamp ? 'translate-x-6' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>
            )}

            {/* Standalone Headunit PWA Launcher Mode */}
            <div className="p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div className="space-y-0.5">
                <div className="text-sm font-bold text-white flex items-center gap-2">
                  <Smartphone className="w-4 h-4" style={{ color: accentHex }} />
                  <span>Standalone Fullscreen Headunit App (PWA)</span>
                </div>
                <p className="text-xs text-slate-400">
                  Installs cleanly without Chrome address bars or browser tabs on Android headunits and iOS.
                </p>
              </div>
              <PWAInstallButton accentHex={accentHex} showGuideWhenUnavailable />
            </div>

            {/* Idle Cluster Screensaver Timeout */}
            <div className="p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <div className="text-sm font-bold text-white">Idle Cluster Screensaver</div>
                <p className="text-xs text-slate-400">
                  Automatically expands the Mercedes Instrument Cluster Visualizer fullscreen after inactivity while playing.
                </p>
              </div>
              <div className="flex bg-black/60 p-1 rounded-xl border border-slate-800 text-xs shrink-0">
                {[
                  { val: 0, label: 'Off' },
                  { val: 15, label: '15s' },
                  { val: 30, label: '30s' },
                  { val: 60, label: '60s' },
                ].map((opt) => {
                  const isSel = idleScreensaverSeconds === opt.val;
                  return (
                    <button
                      key={opt.val}
                      type="button"
                      onClick={() => onChangeIdleScreensaverSeconds(opt.val)}
                      className={`px-3 py-1.5 rounded-lg font-bold transition-all ${
                        isSel ? 'text-black shadow' : 'text-slate-400 hover:text-white'
                      }`}
                      style={isSel ? { backgroundColor: accentHex } : {}}
                    >
                      {opt.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Night Brightness Dimmer & Warm W203 Amber Filter */}
            <div className="p-4 sm:p-5 space-y-4">
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <div>
                    <div className="text-sm font-bold text-white">In-App Night Brightness Dimmer</div>
                    <p className="text-xs text-slate-400">Reduces screen glare on dark highways even below Android&apos;s hardware slider.</p>
                  </div>
                  <span className="text-xs font-mono font-bold text-white bg-black/60 px-2.5 py-1 rounded-lg border border-slate-800">
                    {nightBrightness}%
                  </span>
                </div>
                <input
                  type="range"
                  aria-label="In-App Night Brightness"
                  min={35}
                  max={100}
                  step={5}
                  value={nightBrightness}
                  onChange={(e) => onChangeNightBrightness(Number(e.target.value))}
                  className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                  style={{ accentColor: accentHex }}
                />
              </div>

              <div className="flex items-center justify-between gap-4 pt-2 border-t border-slate-800/50">
                <div className="space-y-0.5">
                  <div className="text-sm font-bold text-white flex items-center gap-2">
                    <Flame className="w-4 h-4 text-amber-400" />
                    <span>Warm W203 Amber Anti-Glare Tint</span>
                  </div>
                  <p className="text-xs text-slate-400">Adds a gentle amber night-vision wash to match factory Mercedes instrument illumination.</p>
                </div>
                <button
                  type="button"
                  aria-label="Toggle Warm Amber Night Filter"
                  onClick={() => onToggleWarmAmberFilter(!warmAmberFilter)}
                  className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                    warmAmberFilter ? 'bg-amber-400' : 'bg-slate-700'
                  }`}
                >
                  <div
                    className={`w-4 h-4 rounded-full bg-black transition-transform ${
                      warmAmberFilter ? 'translate-x-6' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>
            </div>

            {/* Driver Safe UI Lock (>= 10 km/h) */}
            <div className="p-4 sm:p-5 space-y-3">
              <div className="flex items-center justify-between gap-4">
                <div className="space-y-0.5">
                  <div className="text-sm font-bold text-white flex items-center gap-2">
                    <Lock className="w-4 h-4" style={{ color: accentHex }} />
                    <span>Driver Safe UI Lock (Motion &ge; 10 km/h)</span>
                  </div>
                  <p className="text-xs text-slate-400">
                    Switches Player buttons to oversized zero-look targets and locks Search keyboard typing while the car is moving.
                  </p>
                </div>
                <button
                  type="button"
                  aria-label="Toggle Driver Safe UI Lock"
                  onClick={() => onToggleDriverSafeMode(!driverSafeMode)}
                  className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                    driverSafeMode ? 'bg-lime-400' : 'bg-slate-700'
                  }`}
                  style={driverSafeMode ? { backgroundColor: accentHex } : {}}
                >
                  <div
                    className={`w-4 h-4 rounded-full bg-black transition-transform ${
                      driverSafeMode ? 'translate-x-6' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>

              {/* Live Hardware GPS Sensor Telemetry */}
              <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-slate-800/50 text-xs">
                <span className="text-slate-400">
                  Live Vehicle GPS Telemetry:{' '}
                  <strong className="text-slate-200 font-mono">
                    {gpsStatus === 'active'
                      ? `Locked (${currentGpsSpeed} km/h)`
                      : gpsStatus === 'waiting'
                      ? 'Waiting for satellite fix...'
                      : gpsStatus === 'denied'
                      ? 'Location Permission Denied'
                      : gpsStatus === 'off'
                      ? 'SCV Disabled'
                      : 'GPS Unavailable on Device'}
                  </strong>
                </span>
                <button
                  type="button"
                  onClick={onRequestGpsRefresh}
                  className="px-3 py-1.5 rounded-lg font-bold bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 transition-colors"
                >
                  Poll Live GPS
                </button>
              </div>
            </div>
          </div>

          {/* Steering Wheel CANBUS Key Learner */}
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800/60">
              <div className="flex items-center gap-3">
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center border"
                  style={{ backgroundColor: `${accentHex}15`, borderColor: `${accentHex}30` }}
                >
                  <Keyboard className="w-5 h-5" style={{ color: accentHex }} />
                </div>
                <div>
                  <h3 className="text-sm font-bold text-white">Steering Wheel Key Learner (CANBUS Remapper)</h3>
                  <p className="text-xs text-slate-400">
                    Tap Bind, then press any steering wheel or Android headunit media button to remap it.
                  </p>
                </div>
              </div>

              <button
                type="button"
                onClick={onResetSwcBindings}
                className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 transition-colors"
              >
                Reset Defaults
              </button>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
              {(Object.keys(STEERING_ACTION_LABELS) as SteeringAction[]).map((action) => {
                const isListening = listeningAction === action;
                return (
                  <div
                    key={action}
                    className="flex items-center justify-between gap-3 p-3 rounded-xl bg-black/40 border border-slate-800/80"
                  >
                    <div className="min-w-0">
                      <div className="text-xs font-bold text-white truncate">
                        {STEERING_ACTION_LABELS[action]}
                      </div>
                      <div className="text-[11px] font-mono text-slate-400 mt-0.5">
                        Key: <span className="text-slate-200">{formatSteeringKeyLabel(swcBindings[action])}</span>
                      </div>
                    </div>

                    <button
                      type="button"
                      onClick={() => setListeningAction(isListening ? null : action)}
                      className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all shrink-0 ${
                        isListening
                          ? 'bg-amber-400 text-black animate-pulse'
                          : 'bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700'
                      }`}
                    >
                      {isListening ? 'Press Button...' : 'Bind'}
                    </button>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* CATEGORY 3: PODCASTS, TALK SHOWS & SLEEP TIMER */}
      {/* ============================================================== */}
      {activeCategory === 'podcasts' && (
        <div className="space-y-6 animate-in fade-in duration-150">
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl divide-y divide-slate-800/60 overflow-hidden shadow-xl">
            {/* Podcast Mode Switch */}
            <div className="p-4 sm:p-5 flex items-center justify-between gap-4">
              <div className="space-y-0.5">
                <div className="text-sm font-bold text-white flex items-center gap-2">
                  <span>Specialized Podcast & Talk Show Mode</span>
                  <span className="text-[10px] px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-300 font-mono">
                    WoW Radio & Blue Plz
                  </span>
                </div>
                <p className="text-xs text-slate-400">
                  Adds dedicated <strong>-15s Replay</strong> and <strong>+30s Skip</strong> quick-jump buttons in the cockpit player.
                </p>
              </div>

              <button
                onClick={() => onTogglePodcastMode(!isPodcastMode)}
                className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                  isPodcastMode ? 'bg-lime-400' : 'bg-slate-700'
                }`}
                style={isPodcastMode ? { backgroundColor: accentHex } : {}}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-black transition-transform ${
                    isPodcastMode ? 'translate-x-6' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>

            {/* Playback Speed Rate */}
            <div className="p-4 sm:p-5 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <div className="text-sm font-bold text-white">Playback Speed Rate</div>
                <p className="text-xs text-slate-400">Natural pitch-corrected voice acceleration</p>
              </div>

              <div className="flex bg-black/60 p-1 rounded-xl border border-slate-800 text-xs">
                {[1.0, 1.25, 1.5, 1.75, 2.0].map((rate) => {
                  const isSel = playbackRate === rate;
                  return (
                    <button
                      key={rate}
                      onClick={() => onChangePlaybackRate(rate)}
                      className={`px-3 py-1.5 rounded-lg font-bold font-mono transition-all ${
                        isSel ? 'text-black shadow' : 'text-slate-400 hover:text-white'
                      }`}
                      style={isSel ? { backgroundColor: accentHex } : {}}
                    >
                      {rate}x
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Rest-Stop Sleep Timer with 10s Fade-Out */}
            <div className="p-4 sm:p-5 space-y-3">
              <div className="flex items-center justify-between">
                <div>
                  <div className="text-sm font-bold text-white">In-Car Rest Stop Sleep Timer</div>
                  <p className="text-xs text-slate-400">
                    Gradually fades volume to zero over 10 seconds before pausing playback
                  </p>
                </div>

                {sleepTimerRemainingSec !== null && sleepTimerRemainingSec > 0 && (
                  <div className="px-3 py-1 rounded-xl bg-amber-500/20 text-amber-300 font-mono text-xs font-bold border border-amber-500/30 flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5 animate-spin" />
                    <span>{formatTimerSec(sleepTimerRemainingSec)} left</span>
                  </div>
                )}
              </div>

              <div className="flex bg-black/60 p-1 rounded-xl border border-slate-800 text-xs flex-wrap gap-1">
                {[
                  { label: 'Off', val: null },
                  { label: '15 min', val: 15 },
                  { label: '30 min', val: 30 },
                  { label: '45 min', val: 45 },
                  { label: '60 min', val: 60 },
                ].map((item) => {
                  const isSel = sleepTimerMinutes === item.val;
                  return (
                    <button
                      key={item.label}
                      onClick={() => onSetSleepTimer(item.val)}
                      className={`px-3 py-1.5 rounded-lg font-bold transition-all ${
                        isSel ? 'text-black shadow' : 'text-slate-400 hover:text-white'
                      }`}
                      style={isSel ? { backgroundColor: accentHex } : {}}
                    >
                      {item.label}
                    </button>
                  );
                })}
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* CATEGORY 4: OFFLINE ROADTRIP STORAGE & BATCH SYNC */}
      {/* ============================================================== */}
      {activeCategory === 'offline' && (
        <div className="space-y-6 animate-in fade-in duration-150">
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-4 border-b border-slate-800/60">
              <div>
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <span>Vehicle IndexedDB Flash Storage</span>
                  <span className="text-xs font-mono font-normal text-slate-400">
                    ({offlineTrackCount} tracks • {formatBytes(offlineStorageBytes)})
                  </span>
                </h3>
                <p className="text-xs text-slate-400 mt-0.5">
                  Raw binary blobs stored locally on vehicle storage for 100% offline highway drives.
                </p>
              </div>

              <div className="flex items-center gap-2">
                <button
                  onClick={onBatchSyncCurrentFolder}
                  disabled={isBatchSyncing}
                  className="px-4 py-2 rounded-xl text-xs font-bold text-black flex items-center gap-2 transition-all shadow active:scale-95 disabled:opacity-50"
                  style={{ backgroundColor: accentHex }}
                >
                  <Download className={`w-4 h-4 ${isBatchSyncing ? 'animate-bounce' : ''}`} />
                  {isBatchSyncing ? 'Batch Syncing...' : 'Batch Sync Current Folder'}
                </button>

                {offlineTrackCount > 0 && (
                  <button
                    onClick={onClearOfflineStorage}
                    className="p-2 rounded-xl text-slate-400 hover:text-red-400 hover:bg-red-500/10 border border-slate-800 transition-colors"
                    title="Wipe offline vehicle cache"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                )}
              </div>
            </div>

            {/* Batch Sync Progress Bar */}
            {isBatchSyncing && batchSyncProgress && (
              <div className="p-4 rounded-xl bg-black/60 border border-slate-800 space-y-2">
                <div className="flex justify-between text-xs">
                  <span className="text-slate-300 font-medium truncate">
                    Downloading: <strong className="text-white">{batchSyncProgress.trackTitle}</strong>
                  </span>
                  <span className="text-lime-400 font-mono font-bold">
                    {batchSyncProgress.current} / {batchSyncProgress.total}
                  </span>
                </div>
                <div className="w-full h-2 bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full transition-all duration-200"
                    style={{
                      width: `${(batchSyncProgress.current / batchSyncProgress.total) * 100}%`,
                      backgroundColor: accentHex,
                    }}
                  />
                </div>
              </div>
            )}

            {/* Real Origin Storage Quota & Persistence */}
            <div className="flex flex-wrap items-center justify-between gap-3 pt-2">
              <div>
                <div className="text-sm font-semibold text-white">Headunit IndexedDB Flash Quota</div>
                <div className="text-xs text-slate-400 font-mono">
                  {storageQuota
                    ? `${formatBytes(storageQuota.usageBytes)} used of ${formatBytes(storageQuota.quotaBytes)} available (${
                        storageQuota.persisted ? 'Pinned Persistent' : 'Best-Effort'
                      })`
                    : 'Inspecting browser storage quota...'}
                </div>
              </div>
              {storageQuota && !storageQuota.persisted && (
                <button
                  type="button"
                  onClick={onRequestPersistentStorage}
                  className="px-3.5 py-2 rounded-xl text-xs font-bold bg-slate-800 hover:bg-slate-700 text-white border border-slate-700 transition-colors"
                >
                  Pin Persistent Storage
                </button>
              )}
            </div>

            {/* Smart Rolling Cache (Auto-Pin Favorites + Storage Cap) */}
            <div className="pt-4 border-t border-slate-800/60 space-y-4">
              <div className="flex items-center justify-between gap-4">
                <div className="space-y-0.5">
                  <div className="text-sm font-bold text-white">Smart Rolling Cache (Auto-Pin Favorites)</div>
                  <p className="text-xs text-slate-400">
                    Automatically pins favorited tracks offline and prunes oldest unstarred cached tracks when the storage cap is reached.
                  </p>
                </div>
                <button
                  type="button"
                  aria-label="Toggle Smart Rolling Cache"
                  onClick={() => onToggleSmartRollingCache(!smartRollingCache)}
                  className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                    smartRollingCache ? 'bg-lime-400' : 'bg-slate-700'
                  }`}
                  style={smartRollingCache ? { backgroundColor: accentHex } : {}}
                >
                  <div
                    className={`w-4 h-4 rounded-full bg-black transition-transform ${
                      smartRollingCache ? 'translate-x-6' : 'translate-x-0'
                    }`}
                  />
                </button>
              </div>

              <div className="flex items-center justify-between gap-3">
                <div>
                  <div className="text-sm font-semibold text-white">Offline Cache Storage Cap</div>
                  <div className="text-xs text-slate-400">Protected Favorites and current queue are kept when pruning</div>
                </div>
                <div className="flex bg-black/60 p-1 rounded-xl border border-slate-800 text-xs">
                  {[
                    { mb: 500, label: '500 MB' },
                    { mb: 1024, label: '1 GB' },
                    { mb: 2048, label: '2 GB' },
                  ].map((cap) => (
                    <button
                      key={cap.mb}
                      type="button"
                      onClick={() => onChangeOfflineCacheCapMb(cap.mb)}
                      className={`px-3 py-1 rounded-lg transition-colors font-bold ${
                        offlineCacheCapMb === cap.mb ? 'text-black shadow' : 'text-slate-400 hover:text-white'
                      }`}
                      style={offlineCacheCapMb === cap.mb ? { backgroundColor: accentHex } : {}}
                    >
                      {cap.label}
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* CATEGORY 5: NEXTCLOUD SYNC & HOTSPOT ROAMING */}
      {/* ============================================================== */}
      {activeCategory === 'cloud' && (
        <div className="space-y-6 animate-in fade-in duration-150">
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-5">
            <div className="flex items-center justify-between pb-4 border-b border-slate-800/60">
              <div>
                <div className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                  Nextcloud Server Endpoint
                </div>
                <div className="text-base font-bold text-white mt-0.5">
                  {isLoggedIn ? username : 'Not signed in'}
                </div>
                <div className="text-xs text-slate-400 font-mono mt-0.5">
                  cloud.zentryx.bg • /{musicFolder || 'Root'}
                </div>
              </div>

              <div className="flex items-center gap-2">
                {isLoggedIn && (
                  <button
                    onClick={onRescanMusic}
                    disabled={isScanning}
                    className="px-3.5 py-2 rounded-xl text-xs font-bold text-black flex items-center gap-1.5 transition-all shadow"
                    style={{ backgroundColor: accentHex }}
                  >
                    <RefreshCw className={`w-3.5 h-3.5 ${isScanning ? 'animate-spin' : ''}`} />
                    {isScanning ? 'Syncing...' : 'Rescan Files'}
                  </button>
                )}

                <button
                  onClick={isLoggedIn ? onSignOut : onOpenSignInModal}
                  className={`px-4 py-2 rounded-xl text-xs font-bold transition-colors ${
                    isLoggedIn
                      ? 'bg-red-500/15 hover:bg-red-500/25 text-red-300 border border-red-500/30'
                      : 'text-black'
                  }`}
                  style={!isLoggedIn ? { backgroundColor: accentHex } : {}}
                >
                  {isLoggedIn ? 'Sign Out' : 'Sign In'}
                </button>
              </div>
            </div>

            {/* Folder Browser Link */}
            {isLoggedIn && (
              <div className="flex items-center justify-between p-3.5 rounded-xl bg-black/40 border border-slate-800">
                <div className="flex items-center gap-2.5">
                  <FolderOpen className="w-4 h-4 text-slate-400" />
                  <span className="text-xs text-slate-300">Active Music Folder: <strong>/{musicFolder}</strong></span>
                </div>
                <button
                  onClick={onOpenFolderBrowser}
                  className="px-3 py-1.5 rounded-lg text-xs font-bold bg-slate-800 hover:bg-slate-700 text-white transition-colors"
                >
                  Browse Folders
                </button>
              </div>
            )}

            {/* Deadzone & Tunnel Guard */}
            <div className="pt-2 border-t border-slate-800/60 flex flex-col sm:flex-row sm:items-center justify-between gap-3">
              <div>
                <div className="text-sm font-bold text-white flex items-center gap-2">
                  <span>Deadzone & Tunnel Guard</span>
                  <span className="text-[10px] font-mono text-lime-400 px-2 py-0.5 rounded-full bg-lime-500/20">
                    Pre-buffer
                  </span>
                </div>
                <p className="text-xs text-slate-400">
                  Aggressive multi-track pre-buffering to prevent playback pauses during long tunnels and deadzones.
                </p>
              </div>

              <div className="flex bg-black/60 p-1 rounded-xl border border-slate-800 text-xs">
                {[
                  { id: 'standard', label: '1 Track Ahead' },
                  { id: 'aggressive', label: '3 Tracks Ahead' },
                ].map((mode) => {
                  const isSel = deadzoneGuardMode === mode.id;
                  return (
                    <button
                      key={mode.id}
                      onClick={() => onChangeDeadzoneGuard(mode.id as any)}
                      className={`px-3 py-1.5 rounded-lg font-bold transition-all ${
                        isSel ? 'text-black shadow' : 'text-slate-400 hover:text-white'
                      }`}
                      style={isSel ? { backgroundColor: accentHex } : {}}
                    >
                      {mode.label}
                    </button>
                  );
                })}
              </div>
            </div>

            {/* Live Header Range Metadata Collection */}
            <div className="pt-4 border-t border-slate-800/60 flex items-center justify-between gap-3">
              <div>
                <div className="text-sm font-bold text-white">
                  Auto-Fetch Track Tags & Artwork (HTTP Range)
                </div>
                <p className="text-xs text-slate-400">
                  Reads only the first ~64 KB header of playing MP3/FLAC/OGG/M4A files after audio buffers, caching ID3 tags & cover art in IndexedDB.
                </p>
              </div>
              <button
                type="button"
                aria-label="Toggle Auto-Fetch Track Tags & Artwork"
                onClick={() => onToggleAutoFetchMetadata(!autoFetchMetadata)}
                className={`w-12 h-6 rounded-full p-1 transition-colors shrink-0 ${
                  autoFetchMetadata ? 'bg-lime-400' : 'bg-slate-700'
                }`}
                style={autoFetchMetadata ? { backgroundColor: accentHex } : {}}
              >
                <div
                  className={`w-4 h-4 rounded-full bg-black transition-transform ${
                    autoFetchMetadata ? 'translate-x-6' : 'translate-x-0'
                  }`}
                />
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ============================================================== */}
      {/* CATEGORY 6: CONNECTION & WIRE LOGS (HTTP 500 INSPECTOR) */}
      {/* ============================================================== */}
      {activeCategory === 'diagnostics' && (
        <div className="space-y-6 animate-in fade-in duration-150">
          <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-5 sm:p-6 shadow-xl space-y-4">
            <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 border-b border-slate-800/80">
              <div className="flex items-center gap-3">
                <div
                  className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border"
                  style={{ backgroundColor: `${accentHex}18`, borderColor: `${accentHex}44` }}
                >
                  <Terminal className="w-5 h-5" style={{ color: accentHex }} />
                </div>
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-sm font-bold text-white tracking-tight">Connection & Stream Diagnostics</h3>
                    <span className="px-2 py-0.5 rounded-full text-[10px] font-mono font-semibold bg-red-500/10 text-red-400 border border-red-500/20">
                      HTTP 500 Inspector
                    </span>
                  </div>
                  <p className="text-[11px] text-slate-400 mt-0.5">
                    Real-time network wire logs directly from Nextcloud SabreDAV endpoints
                  </p>
                </div>
              </div>

              {/* Actions Toolbar */}
              <div className="flex items-center gap-2 flex-wrap">
                <button
                  onClick={onRunDiagnostics}
                  disabled={isDiagnosing}
                  className="px-3 py-1.5 rounded-xl text-xs font-bold text-black flex items-center gap-1.5 transition-all shadow active:scale-95 disabled:opacity-50"
                  style={{ backgroundColor: accentHex }}
                >
                  <Activity className={`w-3.5 h-3.5 ${isDiagnosing ? 'animate-spin' : ''}`} />
                  {isDiagnosing ? 'Diagnosing...' : 'Probe Endpoints'}
                </button>

                <button
                  onClick={onFetchServerProxyLogs}
                  disabled={isLoadingServerLogs}
                  className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 flex items-center gap-1.5 transition-colors disabled:opacity-50"
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${isLoadingServerLogs ? 'animate-spin' : ''}`} />
                  Wire Logs
                </button>

                <button
                  onClick={onCopyDiagnosticReport}
                  className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-[#070b14] hover:bg-slate-800 text-slate-300 border border-slate-800 flex items-center gap-1.5 transition-colors"
                >
                  {copiedReport ? (
                    <>
                      <Check className="w-3.5 h-3.5 text-lime-400" />
                      <span className="text-lime-400">Copied!</span>
                    </>
                  ) : (
                    <>
                      <Copy className="w-3.5 h-3.5 text-slate-400" />
                      <span>Copy Report</span>
                    </>
                  )}
                </button>

                <button
                  onClick={onClearDiagnostics}
                  className="p-1.5 rounded-xl text-slate-400 hover:text-red-400 hover:bg-red-500/10 border border-slate-800 transition-colors"
                  title="Clear logs"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>

            {/* Diagnostic Switcher Tabs */}
            <div className="flex items-center justify-between border-b border-slate-800/60 pb-2">
              <div className="flex items-center gap-2">
                <button
                  onClick={() => setDiagTab('client')}
                  className={`px-3 py-1 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 ${
                    diagTab === 'client' ? 'bg-slate-800 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <Activity className="w-3.5 h-3.5" />
                  Live Stream & Client Events
                  <span className="px-1.5 py-0.2 rounded-full text-[10px] bg-slate-900 font-mono text-slate-300">
                    {diagnosticLogs.length}
                  </span>
                </button>

                <button
                  onClick={() => {
                    setDiagTab('server');
                    onFetchServerProxyLogs();
                  }}
                  className={`px-3 py-1 rounded-lg text-xs font-semibold transition-all flex items-center gap-1.5 ${
                    diagTab === 'server' ? 'bg-slate-800 text-white shadow-sm' : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  <FileText className="w-3.5 h-3.5" />
                  Server Proxy Logs (/tmp/proxy.log)
                </button>
              </div>

              {diagTab === 'client' && (
                <div className="flex items-center gap-1">
                  {(['all', 'errors', 'success'] as const).map((filter) => {
                    const count =
                      filter === 'all'
                        ? diagnosticLogs.length
                        : filter === 'errors'
                        ? diagnosticLogs.filter((l) => l.type === 'ERROR' || (l.status && l.status >= 400)).length
                        : diagnosticLogs.filter((l) => l.type === 'SUCCESS' || (l.status && l.status < 400)).length;
                    return (
                      <button
                        key={filter}
                        onClick={() => setDiagFilter(filter)}
                        className={`px-2 py-0.5 rounded text-[10px] font-medium transition-colors ${
                          diagFilter === filter
                            ? 'bg-lime-400/20 text-lime-300 border border-lime-500/30 font-bold'
                            : 'text-slate-500 hover:text-slate-300'
                        }`}
                      >
                        {filter.toUpperCase()} ({count})
                      </button>
                    );
                  })}
                </div>
              )}
            </div>

            {/* Client Events List */}
            {diagTab === 'client' && (
              <div className="space-y-2 max-h-[420px] overflow-y-auto pr-1">
                {diagnosticLogs.length === 0 ? (
                  <div className="py-8 text-center space-y-2 border border-dashed border-slate-800 rounded-xl bg-black/20">
                    <Bug className="w-8 h-8 mx-auto text-slate-600" />
                    <div className="text-xs text-slate-400 font-medium">No live events captured yet</div>
                  </div>
                ) : (
                  diagnosticLogs
                    .filter((log) => {
                      if (diagFilter === 'errors') return log.type === 'ERROR' || (log.status && log.status >= 400);
                      if (diagFilter === 'success') return log.type === 'SUCCESS' || (log.status && log.status < 400);
                      return true;
                    })
                    .map((log) => {
                      const isExpanded = expandedLogId === log.id;
                      const isError = log.type === 'ERROR' || (log.status && log.status >= 400);
                      const isSuccess = log.type === 'SUCCESS' || (log.status && log.status < 400);

                      return (
                        <div
                          key={log.id}
                          className={`rounded-xl border p-3 font-mono text-xs transition-colors ${
                            isError
                              ? 'bg-red-950/20 border-red-500/30'
                              : isSuccess
                              ? 'bg-lime-950/20 border-lime-500/30'
                              : 'bg-black/40 border-slate-800/80'
                          }`}
                        >
                          <div className="flex items-start justify-between gap-2">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className="text-[10px] text-slate-500">{log.timestamp}</span>
                              {log.method && (
                                <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-slate-800 text-slate-200">
                                  {log.method}
                                </span>
                              )}
                              <span
                                className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                  isError ? 'bg-red-500/20 text-red-300' : 'bg-lime-500/20 text-lime-300'
                                }`}
                              >
                                {log.action}
                              </span>
                              {log.status && (
                                <span
                                  className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                    log.status >= 400 ? 'bg-red-500 text-black' : 'bg-lime-400 text-black'
                                  }`}
                                >
                                  HTTP {log.status}
                                </span>
                              )}
                            </div>

                            {(log.headers || log.body) && (
                              <button
                                onClick={() => setExpandedLogId(isExpanded ? null : log.id)}
                                className="text-[10px] px-2 py-0.5 rounded bg-slate-800 hover:bg-slate-700 text-slate-300 flex items-center gap-1 transition-colors"
                              >
                                <Code className="w-3 h-3" />
                                {isExpanded ? 'Hide' : 'Payload'}
                              </button>
                            )}
                          </div>

                          <div className="mt-1.5 space-y-1">
                            <div className="text-[11px] text-slate-300 truncate">
                              <span className="text-slate-500">Endpoint: </span>
                              {log.url}
                            </div>
                            {log.details && <div className="text-[11px] text-slate-400">{log.details}</div>}
                          </div>

                          {isExpanded && (log.headers || log.body) && (
                            <div className="mt-3 pt-3 border-t border-slate-800/80 space-y-2">
                              {log.headers && (
                                <pre className="p-2 rounded bg-black/80 border border-slate-800 text-[10px] text-slate-300 overflow-x-auto whitespace-pre-wrap">
                                  {JSON.stringify(log.headers, null, 2)}
                                </pre>
                              )}
                              {log.body && (
                                <pre className="p-2.5 rounded bg-black/90 border border-red-500/20 text-[10px] text-red-200 overflow-x-auto whitespace-pre-wrap max-h-48">
                                  {log.body}
                                </pre>
                              )}
                            </div>
                          )}
                        </div>
                      );
                    })
                )}
              </div>
            )}

            {/* Server Logs Tab */}
            {diagTab === 'server' && (
              <div className="space-y-2">
                <div className="flex items-center justify-between text-[11px] text-slate-400">
                  <span>Server Proxy Transactions (/tmp/proxy.log):</span>
                  <button
                    onClick={onFetchServerProxyLogs}
                    disabled={isLoadingServerLogs}
                    className="text-lime-400 hover:underline flex items-center gap-1 font-mono text-[10px]"
                  >
                    <RefreshCw className={`w-3 h-3 ${isLoadingServerLogs ? 'animate-spin' : ''}`} />
                    Refresh
                  </button>
                </div>
                <pre className="p-3.5 rounded-xl bg-black/90 border border-slate-800 text-[11px] font-mono text-slate-200 overflow-x-auto max-h-[380px] overflow-y-auto whitespace-pre-wrap leading-relaxed select-text">
                  {serverProxyLogs || 'No server proxy transactions recorded yet.'}
                </pre>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
