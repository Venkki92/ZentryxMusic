import { DspPreset16, DspSettings, sanitizeCustomPresets, sanitizeDspSettings } from './dspConsole';
import {
  DriverProfile,
  SteeringKeyBindings,
  SteeringLongPressBindings,
  sanitizeDriverProfiles,
  sanitizeSteeringBindings,
  sanitizeSteeringLongPressBindings,
} from './carConvenience';

export interface ZentryxBackupPayload {
  app: 'ZentryxMusic';
  schema: number;
  exportedAt: number;
  nextcloudUrl?: string;
  musicFolder?: string;
  accentColor?: string;
  cockpitThemeMode?: 'auto' | 'night' | 'day';
  nightBrightness?: number;
  amberFilterEnabled?: boolean;
  replayGainEnabled?: boolean;
  crossfadeSeconds?: number;
  sdvcSensitivity?: 'off' | 'low' | 'medium' | 'high';
  autoResumeOnBoot?: boolean;
  autoResumeSoftRamp?: boolean;
  autoPlayOnBluetooth?: boolean;
  duckOnReverseGear?: boolean;
  globalAudioSessionAttach?: boolean;
  playbackSpeed?: number;
  swipeGesturesEnabled?: boolean;
  idleScreensaverSeconds?: number;
  driverSafeMode?: boolean;
  smartRollingCache?: boolean;
  offlineCacheCapMb?: number;
  deadzoneGuardMode?: 'standard' | 'aggressive';
  dualPaneCockpit?: boolean;
  driveModeAutoSpeedKmH?: number;
  dspSettingsJson?: string;
  customDspPresetsJson?: string;
  dspSettings?: DspSettings;
  customDspPresets?: DspPreset16[];
  swcBindings?: SteeringKeyBindings;
  swcLongPressBindings?: SteeringLongPressBindings;
  driverProfiles?: DriverProfile[];
  activeDriverProfileId?: string;
}

export function buildSettingsBackupPayload(params: {
  nextcloudUrl: string;
  musicFolder: string;
  accentKey: string;
  cockpitThemeMode: 'auto' | 'night' | 'day';
  nightBrightness: number;
  warmAmberFilter: boolean;
  replayGainEnabled: boolean;
  crossfadeSeconds: number;
  scvSensitivity: 'off' | 'low' | 'medium' | 'high';
  autoResumeOnBoot: boolean;
  autoResumeSoftRamp: boolean;
  autoPlayOnBluetooth?: boolean;
  duckOnReverseGear?: boolean;
  globalAudioSessionAttach?: boolean;
  playbackRate: number;
  enableSwipeGestures: boolean;
  idleScreensaverSeconds: number;
  driverSafeMode: boolean;
  smartRollingCache: boolean;
  offlineCacheCapMb: number;
  deadzoneGuardMode: 'standard' | 'aggressive';
  dualPaneCockpit?: boolean;
  driveModeAutoSpeedKmH?: number;
  dspSettings: DspSettings;
  customDspPresets: DspPreset16[];
  swcBindings: SteeringKeyBindings;
  swcLongPressBindings?: SteeringLongPressBindings;
  driverProfiles?: DriverProfile[];
  activeDriverProfileId?: string;
}): ZentryxBackupPayload {
  return {
    app: 'ZentryxMusic',
    schema: 2,
    exportedAt: Date.now(),
    nextcloudUrl: params.nextcloudUrl,
    musicFolder: params.musicFolder,
    accentColor: params.accentKey,
    cockpitThemeMode: params.cockpitThemeMode,
    nightBrightness: Math.max(0.35, Math.min(1.0, params.nightBrightness / 100)),
    amberFilterEnabled: params.warmAmberFilter,
    replayGainEnabled: params.replayGainEnabled,
    crossfadeSeconds: Math.max(0, Math.min(8, Math.round(params.crossfadeSeconds))),
    sdvcSensitivity: params.scvSensitivity,
    autoResumeOnBoot: params.autoResumeOnBoot,
    autoResumeSoftRamp: params.autoResumeSoftRamp,
    autoPlayOnBluetooth: params.autoPlayOnBluetooth,
    duckOnReverseGear: params.duckOnReverseGear,
    globalAudioSessionAttach: params.globalAudioSessionAttach,
    playbackSpeed: params.playbackRate,
    swipeGesturesEnabled: params.enableSwipeGestures,
    idleScreensaverSeconds: params.idleScreensaverSeconds,
    driverSafeMode: params.driverSafeMode,
    smartRollingCache: params.smartRollingCache,
    offlineCacheCapMb: params.offlineCacheCapMb,
    deadzoneGuardMode: params.deadzoneGuardMode,
    dualPaneCockpit: params.dualPaneCockpit,
    driveModeAutoSpeedKmH: params.driveModeAutoSpeedKmH,
    dspSettingsJson: JSON.stringify(params.dspSettings),
    customDspPresetsJson: JSON.stringify(params.customDspPresets),
    dspSettings: params.dspSettings,
    customDspPresets: params.customDspPresets,
    swcBindings: params.swcBindings,
    swcLongPressBindings: params.swcLongPressBindings,
    driverProfiles: params.driverProfiles,
    activeDriverProfileId: params.activeDriverProfileId,
  };
}

export function parseSettingsBackupJson(rawJson: string): {
  accentKey?: 'LIME' | 'AMBER' | 'CYAN' | 'SILVER' | 'RED';
  cockpitThemeMode?: 'auto' | 'night' | 'day';
  nightBrightness?: number;
  warmAmberFilter?: boolean;
  replayGainEnabled?: boolean;
  crossfadeSeconds?: number;
  scvSensitivity?: 'off' | 'low' | 'medium' | 'high';
  autoResumeOnBoot?: boolean;
  autoResumeSoftRamp?: boolean;
  autoPlayOnBluetooth?: boolean;
  duckOnReverseGear?: boolean;
  globalAudioSessionAttach?: boolean;
  playbackRate?: number;
  enableSwipeGestures?: boolean;
  idleScreensaverSeconds?: number;
  driverSafeMode?: boolean;
  smartRollingCache?: boolean;
  offlineCacheCapMb?: number;
  deadzoneGuardMode?: 'standard' | 'aggressive';
  dualPaneCockpit?: boolean;
  driveModeAutoSpeedKmH?: number;
  dspSettings?: DspSettings;
  customDspPresets?: DspPreset16[];
  swcBindings?: SteeringKeyBindings;
  swcLongPressBindings?: SteeringLongPressBindings;
  driverProfiles?: DriverProfile[];
  activeDriverProfileId?: string;
} | null {
  try {
    const obj = JSON.parse(rawJson);
    if (!obj || typeof obj !== 'object') return null;

    const out: ReturnType<typeof parseSettingsBackupJson> = {};

    const rawAccent = typeof obj.accentColor === 'string' ? obj.accentColor.toUpperCase() : '';
    if (['LIME', 'AMBER', 'CYAN', 'SILVER', 'RED'].includes(rawAccent)) {
      out.accentKey = rawAccent as any;
    }

    if (obj.cockpitThemeMode === 'auto' || obj.cockpitThemeMode === 'night' || obj.cockpitThemeMode === 'day') {
      out.cockpitThemeMode = obj.cockpitThemeMode;
    }

    if (typeof obj.nightBrightness === 'number' && Number.isFinite(obj.nightBrightness)) {
      const pct = obj.nightBrightness <= 1.05 ? Math.round(obj.nightBrightness * 100) : Math.round(obj.nightBrightness);
      out.nightBrightness = Math.max(35, Math.min(100, pct));
    }

    if (typeof obj.amberFilterEnabled === 'boolean') out.warmAmberFilter = obj.amberFilterEnabled;
    if (typeof obj.replayGainEnabled === 'boolean') out.replayGainEnabled = obj.replayGainEnabled;
    if (typeof obj.crossfadeSeconds === 'number' && Number.isFinite(obj.crossfadeSeconds)) {
      out.crossfadeSeconds = Math.max(0, Math.min(8, Math.round(obj.crossfadeSeconds)));
    }

    if (typeof obj.sdvcSensitivity === 'string') {
      const sens = obj.sdvcSensitivity.toLowerCase();
      if (sens === 'off' || sens === 'low' || sens === 'medium' || sens === 'high') {
        out.scvSensitivity = sens;
      }
    }

    if (typeof obj.autoResumeOnBoot === 'boolean') out.autoResumeOnBoot = obj.autoResumeOnBoot;
    if (typeof obj.autoResumeSoftRamp === 'boolean') out.autoResumeSoftRamp = obj.autoResumeSoftRamp;
    if (typeof obj.autoPlayOnBluetooth === 'boolean') out.autoPlayOnBluetooth = obj.autoPlayOnBluetooth;
    if (typeof obj.duckOnReverseGear === 'boolean') out.duckOnReverseGear = obj.duckOnReverseGear;
    if (typeof obj.globalAudioSessionAttach === 'boolean') {
      out.globalAudioSessionAttach = obj.globalAudioSessionAttach;
    }
    if (typeof obj.playbackSpeed === 'number' && Number.isFinite(obj.playbackSpeed)) {
      out.playbackRate = Math.max(0.75, Math.min(2.0, obj.playbackSpeed));
    }
    if (typeof obj.swipeGesturesEnabled === 'boolean') out.enableSwipeGestures = obj.swipeGesturesEnabled;
    if (typeof obj.idleScreensaverSeconds === 'number' && [0, 15, 30, 60].includes(obj.idleScreensaverSeconds)) {
      out.idleScreensaverSeconds = obj.idleScreensaverSeconds;
    }
    if (typeof obj.driverSafeMode === 'boolean') out.driverSafeMode = obj.driverSafeMode;
    if (typeof obj.smartRollingCache === 'boolean') out.smartRollingCache = obj.smartRollingCache;
    if (typeof obj.offlineCacheCapMb === 'number' && [500, 1024, 2048].includes(obj.offlineCacheCapMb)) {
      out.offlineCacheCapMb = obj.offlineCacheCapMb;
    }
    if (obj.deadzoneGuardMode === 'standard' || obj.deadzoneGuardMode === 'aggressive') {
      out.deadzoneGuardMode = obj.deadzoneGuardMode;
    }
    if (typeof obj.dualPaneCockpit === 'boolean') {
      out.dualPaneCockpit = obj.dualPaneCockpit;
    }
    if (typeof obj.driveModeAutoSpeedKmH === 'number' && [0, 15, 30].includes(obj.driveModeAutoSpeedKmH)) {
      out.driveModeAutoSpeedKmH = obj.driveModeAutoSpeedKmH;
    }

    // Accept either nested JSON objects or Android-compatible serialized JSON strings
    let parsedDsp: any = obj.dspSettings;
    if (!parsedDsp && typeof obj.dspSettingsJson === 'string') {
      try {
        parsedDsp = JSON.parse(obj.dspSettingsJson);
      } catch {}
    }
    if (parsedDsp && typeof parsedDsp === 'object') {
      out.dspSettings = sanitizeDspSettings(parsedDsp);
    }

    let parsedPresets: any = obj.customDspPresets;
    if (!parsedPresets && typeof obj.customDspPresetsJson === 'string') {
      try {
        parsedPresets = JSON.parse(obj.customDspPresetsJson);
      } catch {}
    }
    if (Array.isArray(parsedPresets)) {
      out.customDspPresets = sanitizeCustomPresets(parsedPresets);
    }

    if (obj.swcBindings && typeof obj.swcBindings === 'object') {
      out.swcBindings = sanitizeSteeringBindings(obj.swcBindings);
    }
    if (obj.swcLongPressBindings && typeof obj.swcLongPressBindings === 'object') {
      out.swcLongPressBindings = sanitizeSteeringLongPressBindings(obj.swcLongPressBindings);
    }
    if (Array.isArray(obj.driverProfiles)) {
      out.driverProfiles = sanitizeDriverProfiles(obj.driverProfiles);
    }
    if (typeof obj.activeDriverProfileId === 'string' && obj.activeDriverProfileId.trim()) {
      out.activeDriverProfileId = obj.activeDriverProfileId.trim();
    }

    return out;
  } catch {
    return null;
  }
}
