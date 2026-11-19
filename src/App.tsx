import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import {
  Music,
  Disc,
  Heart,
  Download,
  Mic,
  Play,
  Pause,
  SkipForward,
  SkipBack,
  Shuffle,
  Repeat,
  Volume2,
  VolumeX,
  Search,
  Settings,
  Cloud,
  CloudOff,
  Check,
  CheckCircle,
  RefreshCw,
  Eye,
  EyeOff,
  Folder,
  FolderOpen,
  FolderUp,
  FolderCheck,
  FolderSearch,
  FileAudio,
  ListMusic,
  AlertCircle,
  ExternalLink,
  KeyRound,
  Sparkles,
  ShieldCheck,
  ChevronLeft,
  ChevronRight,
  ArrowUp,
  Terminal,
  Activity,
  FileText,
  Copy,
  Trash2,
  Bug,
  Network,
  Code,
  Maximize2,
  Minimize2,
  Wifi,
  WifiOff,
  Plus,
  FolderPlus,
  Sliders,
  Gauge,
  Compass,
  Sun,
  Moon,
  SunMoon,
  Clock,
  Radio,
  RotateCcw,
  RotateCw,
  Power,
  Lock,
  Crosshair,
  HardDrive,
  Car,
  Headphones,
  Info,
  Columns2,
} from 'lucide-react';
import { parseAudioMetadata, estimateBitrateKbps } from './utils/id3Parser';
import {
  addToQueue,
  playNextInQueue,
  reorderQueue,
  removeFromQueue,
  resolveNextIndex,
  resolvePreviousIndex,
} from './utils/audioQueue';
import {
  fuzzyMatch,
  searchTracks,
  createPlaylist,
  addTrackToPlaylist,
  removeTrackFromPlaylist,
  reorderPlaylist,
  Playlist,
} from './utils/fuzzySearch';
import {
  saveOfflineTrack,
  getOfflineTrack,
  getAllOfflineTracks,
  deleteOfflineTrack,
  clearAllOfflineTracks,
  saveTrackProgress,
  getTrackProgress,
  getAllPlaylists,
  savePlaylistRecord,
  deletePlaylistRecord,
  getCachedTrackMetadata,
  getAllCachedTrackMetadata,
  saveCachedTrackMetadata,
  clearAllCachedTrackMetadata,
} from './utils/idbStorage';
import {
  extractTrackMetadataFromUrl,
  mergeTrackMetadata,
  needsMetadataFetch,
} from './utils/metadataCollector';
import {
  calculateSpeedVolumeBoost,
  calculateAttenuationMultiplier,
  calculateSleepTimerFade,
} from './utils/audioEqualizer';
import {
  serializeResumeSession,
  parseResumeSession,
  calculateSoftStartGain,
  isDriverSafeLockActive,
  calculateDimmerOverlayOpacity,
  DEFAULT_SWC_BINDINGS,
  DEFAULT_SWC_LONG_PRESS_BINDINGS,
  SteeringAction,
  SteeringKeyBindings,
  SteeringLongPressBindings,
  sanitizeSteeringBindings,
  sanitizeSteeringLongPressBindings,
  resolveSteeringAction,
  DriverProfile,
  DEFAULT_DRIVER_PROFILES,
  sanitizeDriverProfiles,
  RoadtripPool,
  buildRoadtripOfflinePlan,
  replayGainDbToMultiplier,
  calculateCrossfadeGains,
  selectOfflineCacheEvictions,
  recordTrackPlayStat,
  TrackPlayStat,
  buildSmartMixTracks,
  SmartMixType,
} from './utils/carConvenience';
import { AudioVisualizer } from './components/AudioVisualizer';
import { QueueDrawer } from './components/QueueDrawer';
import { PlaylistsModal } from './components/PlaylistsModal';
import { StickyMiniPlayer } from './components/StickyMiniPlayer';
import { FloatingCockpitBar } from './components/FloatingCockpitBar';
import { SettingsPanel } from './components/SettingsPanel';
import { LazyColumn } from './components/LazyColumn';
import { LazyAlbumArt } from './components/LazyAlbumArt';
import { CrossfadeAlbumArt } from './components/CrossfadeAlbumArt';
import { PWAInstallButton, AndroidApkDownloadButton, OfflineIndicator } from './components/PWAComponents';
import { buildTrackIndexMap } from './utils/mediatekOptimization';
import {
  AdaptiveCabinConfig,
  AdaptiveEvaluationState,
  CabinMicStatus,
  DEFAULT_ADAPTIVE_CABIN_CONFIG,
  INITIAL_ADAPTIVE_STATE,
  applyAdaptiveCabinOverlay,
  calculateAdaptiveDspDelta,
  evaluateAdaptiveCabinState,
  sanitizeAdaptiveCabinConfig,
  startCabinMicNoiseMonitor,
} from './utils/adaptiveCabinProfile';
import {
  authorizeSavedOfflineFolder,
  clearSavedOfflineFolder,
  isFolderPickerSupported,
  loadSavedOfflineFolder,
  pickOfflineFolder,
  saveTrackToSelectedFolder,
} from './utils/offlineFolder';
import {
  DEFAULT_MUSIC_FOLDERS,
  DEFAULT_MUSIC_FOLDER_STRING,
  addFolderToList,
  mergeDiscoveredTracks,
  normalizeFolderPath,
  parseScanFolders,
  removeFolderFromList,
  resolveInitialBrowserPath,
} from './utils/folderScanner';
import {
  AudioOutputRouteId,
  AUDIO_OUTPUT_ROUTES,
  DEFAULT_DSP_SETTINGS,
  DspPreset16,
  DspSettings,
  FACTORY_DSP_PRESETS,
  SURROUND_MODES,
  SurroundModeId,
  applyPresetToDsp,
  matchPresetForTrack,
  sanitizeCustomPresets,
  sanitizeDspSettings,
} from './utils/dspConsole';
import { HeadunitDspEngine } from './utils/dspAudioEngine';
import { isDaylightBySolar } from './utils/solarCalculator';
import {
  buildSettingsBackupPayload,
  parseSettingsBackupJson,
} from './utils/settingsBackup';
import {
  parseM3u8Paths,
  resolveMatchedTracksForPaths,
  resolvePlaylistNameFromM3u8,
  resolveTracksForPlaylist,
  serializeM3u8,
  serializePlaylistFileName,
} from './utils/m3uPlaylist';

export function safeBtoa(str: string): string {
  try {
    return btoa(unescape(encodeURIComponent(str)));
  } catch {
    return btoa(str);
  }
}

export interface Track {
  id: string;
  title: string;
  artist: string;
  album: string;
  year?: number;
  genre?: string;
  codec?: string;
  bitrateKbps?: number;
  sampleRateHz?: number;
  replayGainDb?: number;
  metadataHydrated?: boolean;
  duration: string;
  durationSeconds: number;
  sizeBytes: number;
  sizeFormatted: string;
  href: string;
  streamUrl: string;
  fileName: string;
  folderPath: string;
  contentType?: string;
  sourceType?: 'WEBDAV' | 'LOCAL';
  bitsPerSample?: number;
}

interface WebDAVFolder {
  href: string;
  name: string;
  path: string;
}

export interface DiagnosticLogEntry {
  id: string;
  timestamp: string;
  type: 'INFO' | 'SUCCESS' | 'WARN' | 'ERROR';
  action: string;
  url: string;
  method?: string;
  status?: number;
  statusText?: string;
  details?: string;
  headers?: Record<string, string>;
  body?: string;
}

type MenuTab = 'player' | 'library' | 'search' | 'settings';
type LibraryFilterType =
  | 'all'
  | 'albums'
  | 'artists'
  | 'folders'
  | 'recent'
  | 'most_played'
  | 'resume'
  | 'favorites'
  | 'downloaded'
  | 'playlists';
interface MenuNavEntry {
  tab: MenuTab;
  libraryFilter: LibraryFilterType;
  selectedAlbum: string | null;
  selectedArtist: string | null;
}

export default function App() {
  const [activeTab, setActiveTab] = useState<MenuTab>('player');
  const [navHistory, setNavHistory] = useState<MenuNavEntry[]>([
    { tab: 'player', libraryFilter: 'all', selectedAlbum: null, selectedArtist: null },
  ]);
  const [navHistoryIndex, setNavHistoryIndex] = useState(0);
  const navHistoryRef = useRef(navHistory);
  const navHistoryIndexRef = useRef(navHistoryIndex);
  navHistoryRef.current = navHistory;
  navHistoryIndexRef.current = navHistoryIndex;

  // Real Connection & Stream Diagnostics Logging State
  const [diagnosticLogs, setDiagnosticLogs] = useState<DiagnosticLogEntry[]>([]);
  const [isDiagnosing, setIsDiagnosing] = useState(false);
  const [diagTab, setDiagTab] = useState<'client' | 'server'>('client');
  const [diagFilter, setDiagFilter] = useState<'all' | 'errors' | 'success'>('all');
  const [serverProxyLogs, setServerProxyLogs] = useState<string>('');
  const [isLoadingServerLogs, setIsLoadingServerLogs] = useState(false);
  const [expandedLogId, setExpandedLogId] = useState<string | null>(null);
  const [copiedReport, setCopiedReport] = useState(false);

  // Account & Nextcloud Persistence State
  const [serverUrl, setServerUrl] = useState(() => localStorage.getItem('zentryx_nc_server') || 'https://cloud.zentryx.bg');
  const [username, setUsername] = useState(() => localStorage.getItem('zentryx_nc_user') || '');
  const [password, setPassword] = useState(() => localStorage.getItem('zentryx_nc_pass') || '');
  const [musicFolder, setMusicFolder] = useState(() => {
    const saved = localStorage.getItem('zentryx_nc_folder');
    const migrated = localStorage.getItem('zentryx_nc_folders_migrated_v2');
    if (!saved) return DEFAULT_MUSIC_FOLDER_STRING;
    if (!migrated && saved.trim().replace(/^\/+/, '').replace(/\/+$/, '') === 'Music') {
      localStorage.setItem('zentryx_nc_folder', DEFAULT_MUSIC_FOLDER_STRING);
      localStorage.setItem('zentryx_nc_folders_migrated_v2', '1');
      return DEFAULT_MUSIC_FOLDER_STRING;
    }
    return saved;
  });
  const [extraScanFolders, setExtraScanFolders] = useState<string[]>(() => {
    try {
      const raw = localStorage.getItem('zentryx_nc_extra_folders');
      if (!raw) return [];
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed.filter((x): x is string => typeof x === 'string' && x.trim().length > 0) : [];
    } catch {
      return [];
    }
  });
  const [isLoggedIn, setIsLoggedIn] = useState(() => Boolean(localStorage.getItem('zentryx_nc_user') && localStorage.getItem('zentryx_nc_pass')));

  const [showPassword, setShowPassword] = useState(false);
  const [showLoginModal, setShowLoginModal] = useState(false);
  const [loginMethod, setLoginMethod] = useState<'flow' | 'manual'>('flow');
  const [flowLoginUrl, setFlowLoginUrl] = useState<string | null>(null);
  const [flowToken, setFlowToken] = useState<string | null>(null);
  const [isFlowPolling, setIsFlowPolling] = useState(false);
  const [flowStatus, setFlowStatus] = useState<string>('');
  const pollIntervalRef = useRef<any>(null);
  const [isConnecting, setIsConnecting] = useState(false);
  const [loginError, setLoginError] = useState<string | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  // Interactive Folder Browser State
  const [showFolderBrowser, setShowFolderBrowser] = useState(false);
  const [browserCurrentPath, setBrowserCurrentPath] = useState(() => resolveInitialBrowserPath(undefined, localStorage.getItem('zentryx_nc_folder') || DEFAULT_MUSIC_FOLDER_STRING));
  const [browserFolders, setBrowserFolders] = useState<WebDAVFolder[]>([]);
  const [browserAudioFiles, setBrowserAudioFiles] = useState<Array<{ href: string; fileName: string; size: number; contentType: string }>>([]);
  const [isBrowserLoading, setIsBrowserLoading] = useState(false);
  const [browserError, setBrowserError] = useState<string | null>(null);
  const [manualPathInput, setManualPathInput] = useState(() => resolveInitialBrowserPath(undefined, localStorage.getItem('zentryx_nc_folder') || DEFAULT_MUSIC_FOLDER_STRING));

  // Step-by-Step Folder Scan Diagnostics State
  type FolderDiagStatus = 'RUNNING' | 'PASS' | 'WARN' | 'FAIL';
  interface FolderDiagEntry {
    id: string;
    stepTitle: string;
    targetPath: string;
    httpStatusCode: number | null;
    permissionsLabel: string;
    rawPermissions: string | null;
    detailMessage: string;
    latencyMs: number;
    status: FolderDiagStatus;
  }
  const [showScanDiagnosticsModal, setShowScanDiagnosticsModal] = useState(false);
  const [scanDiagnosticsRunning, setScanDiagnosticsRunning] = useState(false);
  const [scanDiagnosticsStepText, setScanDiagnosticsStepText] = useState('');
  const [scanDiagnosticsSummary, setScanDiagnosticsSummary] = useState<string | null>(null);
  const [scanDiagnosticsEntries, setScanDiagnosticsEntries] = useState<FolderDiagEntry[]>([]);
  const [scanDiagnosticsStartPath, setScanDiagnosticsStartPath] = useState<string>('');
  const scanDiagRunIdRef = useRef(0);

  // MBUX Ambient Accent Color
  const [accentKey, setAccentKey] = useState<'LIME' | 'AMBER' | 'CYAN' | 'SILVER' | 'RED'>(() => {
    return (localStorage.getItem('zentryx_accent') as any) || 'LIME';
  });

  const accentPalette = {
    LIME: { hex: '#a3e635', name: 'Electric Lime', border: 'rgba(163, 230, 53, 0.25)', bg: 'rgba(163, 230, 53, 0.12)' },
    AMBER: { hex: '#f59e0b', name: 'Amber Glow', border: 'rgba(245, 158, 11, 0.25)', bg: 'rgba(245, 158, 11, 0.12)' },
    CYAN: { hex: '#0082c9', name: 'Cyan Blue', border: 'rgba(0, 130, 201, 0.25)', bg: 'rgba(0, 130, 201, 0.12)' },
    SILVER: { hex: '#94a3b8', name: 'Silver Arrow', border: 'rgba(148, 163, 184, 0.25)', bg: 'rgba(148, 163, 184, 0.12)' },
    RED: { hex: '#ef4444', name: 'Sport Red', border: 'rgba(239, 68, 68, 0.25)', bg: 'rgba(239, 68, 68, 0.12)' },
  };

  const activeAccent = accentPalette[accentKey] || accentPalette.LIME;

  // Real Nextcloud Tracks Library State (Cached in LocalStorage)
  const [tracks, setTracks] = useState<Track[]>(() => {
    try {
      const saved = localStorage.getItem('zentryx_nc_tracks');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });

  const [isScanning, setIsScanning] = useState(false);
  const [scanProgress, setScanProgress] = useState<string>('');

  // Audio Playback State
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const webAudioCtxRef = useRef<AudioContext | null>(null);
  const webAudioBufferRef = useRef<AudioBuffer | null>(null);
  const webAudioSourceRef = useRef<AudioBufferSourceNode | null>(null);
  const webAudioGainRef = useRef<GainNode | null>(null);
  const webAudioAnalyserRef = useRef<AnalyserNode | null>(null);
  const dspEngineRef = useRef<HeadunitDspEngine | null>(null);
  const mediaElementSourceRef = useRef<MediaElementAudioSourceNode | null>(null);
  const analyserConnectedRef = useRef(false);
  const webAudioStartTimeRef = useRef<number>(0);
  const [isWebAudioActive, setIsWebAudioActive] = useState(false);
  const [activeAnalyserNode, setActiveAnalyserNode] = useState<AnalyserNode | null>(null);

  const [currentTrackIndex, setCurrentTrackIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [positionSeconds, setPositionSeconds] = useState(0);
  const [durationSeconds, setDurationSeconds] = useState(0);
  const [volume, setVolume] = useState(85);
  const [isMuted, setIsMuted] = useState(false);
  const [shuffle, setShuffle] = useState(false);
  const [repeatMode, setRepeatMode] = useState<'OFF' | 'ALL' | 'ONE'>('ALL');
  const [isVisualizerOpen, setIsVisualizerOpen] = useState(false);
  const [playbackError, setPlaybackError] = useState<string | null>(null);

  // Up-Next Play Queue State
  const [queue, setQueue] = useState<Track[]>(() => {
    try {
      const saved = localStorage.getItem('zentryx_nc_tracks');
      return saved ? JSON.parse(saved) : [];
    } catch {
      return [];
    }
  });
  const [isQueueOpen, setIsQueueOpen] = useState(false);

  // Playlists State
  const [playlists, setPlaylists] = useState<Playlist[]>([]);
  const [isPlaylistsOpen, setIsPlaylistsOpen] = useState(false);
  const [trackForPlaylist, setTrackForPlaylist] = useState<Track | null>(null);

  // Offline Storage Records (IndexedDB) & Optional Local Download Folder
  const [offlineTrackIds, setOfflineTrackIds] = useState<Set<string>>(new Set());
  const [offlineStorageBytes, setOfflineStorageBytes] = useState(0);
  const [offlineFolderHandle, setOfflineFolderHandle] = useState<any>(null);
  const [offlineFolderName, setOfflineFolderName] = useState<string | null>(null);
  const [offlineFolderPermission, setOfflineFolderPermission] = useState<'granted' | 'prompt' | 'denied'>('prompt');

  // Embedded Cover Art URLs
  const [coverArtUrls, setCoverArtUrls] = useState<Record<string, string>>({});

  // Kiosk / Fullscreen State & WakeLock
  const [isFullscreen, setIsFullscreen] = useState(false);
  const wakeLockRef = useRef<any>(null);

  // Network Roaming / Hotspot Flap Recovery
  const [isOnline, setIsOnline] = useState(() => typeof navigator !== 'undefined' ? navigator.onLine : true);
  const isRecoveringStreamRef = useRef(false);

  // Pre-cached Next Track Ref
  const prefetchedTrackIdRef = useRef<string | null>(null);
  const prefetchBlobUrlRef = useRef<{ id: string; url: string } | null>(null);

  // Vehicle & Offline Filter State
  const [offlineOnly, setOfflineOnly] = useState(false);
  const [storageQuota, setStorageQuota] = useState<{
    usageBytes: number;
    quotaBytes: number;
    persisted: boolean;
  } | null>(null);

  // Pro 16-Band ESP/DSP Console State
  const [dspSettings, setDspSettings] = useState<DspSettings>(() => {
    try {
      const saved = localStorage.getItem('zentryx_dsp_settings');
      if (saved) return sanitizeDspSettings(JSON.parse(saved));
    } catch {}
    return { ...DEFAULT_DSP_SETTINGS };
  });

  const [customDspPresets, setCustomDspPresets] = useState<DspPreset16[]>(() => {
    try {
      const saved = localStorage.getItem('zentryx_dsp_custom_presets');
      return saved ? sanitizeCustomPresets(JSON.parse(saved)) : [];
    } catch {
      return [];
    }
  });

  // A/B DSP Compare & Momentary Bypass
  const [activeCompareSlot, setActiveCompareSlot] = useState<'A' | 'B'>('A');
  const slotADspSnapshotRef = useRef<DspSettings | null>(null);
  const [slotBDspSnapshot, setSlotBDspSnapshot] = useState<DspSettings | null>(() => {
    try {
      const saved = localStorage.getItem('zentryx_dsp_slot_b');
      return saved ? sanitizeDspSettings(JSON.parse(saved)) : null;
    } catch {
      return null;
    }
  });
  const [isHoldBypassed, setIsHoldBypassed] = useState<boolean>(false);

  // Per-Output Audio Device Auto-Profile Routing
  const [activeOutputRoute, setActiveOutputRoute] = useState<AudioOutputRouteId>('headunit_speakers');
  const manualOverrideTrackIdsRef = useRef<Set<string>>(new Set());

  // Automated Cabin Noise & Speed Adaptive DSP Profile Switching
  const [adaptiveCabinConfig, setAdaptiveCabinConfig] = useState<AdaptiveCabinConfig>(() => {
    try {
      const saved = localStorage.getItem('zentryx_adaptive_cabin_dsp');
      if (saved) return sanitizeAdaptiveCabinConfig(JSON.parse(saved));
    } catch {}
    return { ...DEFAULT_ADAPTIVE_CABIN_CONFIG };
  });
  const [cabinNoiseDb, setCabinNoiseDb] = useState<number | null>(null);
  const [cabinMicStatus, setCabinMicStatus] = useState<CabinMicStatus>('off');
  const [adaptiveCabinState, setAdaptiveCabinState] = useState<AdaptiveEvaluationState>(INITIAL_ADAPTIVE_STATE);
  const latestCabinNoiseRef = useRef<number | null>(null);
  const latestSpeedKmHRef = useRef<number>(0);

  const adaptiveDspDelta = useMemo(
    () => calculateAdaptiveDspDelta(adaptiveCabinState.activeProfile, adaptiveCabinConfig),
    [adaptiveCabinState.activeProfile, adaptiveCabinConfig]
  );

  const effectiveDspSettings = useMemo(
    () => applyAdaptiveCabinOverlay(dspSettings, adaptiveDspDelta),
    [dspSettings, adaptiveDspDelta]
  );

  // Quick Attenuation / "Talk" Ducking (-20dB)
  const [isAttenuated, setIsAttenuated] = useState<boolean>(false);

  // Speed-Compensated Volume (SCV)
  const [scvSensitivity, setScvSensitivity] = useState<'off' | 'low' | 'medium' | 'high'>(() => {
    return (localStorage.getItem('zentryx_scv_sensitivity') as any) || 'medium';
  });
  const [currentGpsSpeed, setCurrentGpsSpeed] = useState<number>(0);
  const [gpsCoords, setGpsCoords] = useState<{ lat: number; lon: number } | null>(null);
  const [solarMinuteTick, setSolarMinuteTick] = useState<number>(() => Math.floor(Date.now() / 60000));
  const [gpsStatus, setGpsStatus] = useState<'active' | 'waiting' | 'denied' | 'unavailable' | 'off'>('waiting');
  const [gpsPollNonce, setGpsPollNonce] = useState<number>(0);

  // Cockpit Touch Gestures
  const [enableSwipeGestures, setEnableSwipeGestures] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_gestures') !== 'false';
  });
  const [gestureNotice, setGestureNotice] = useState<string | null>(null);
  const touchStartXRef = useRef<number | null>(null);
  const touchStartYRef = useRef<number | null>(null);

  // Cockpit Day / Night Theme Mode
  const [cockpitThemeMode, setCockpitThemeMode] = useState<'auto' | 'night' | 'day'>(() => {
    return (localStorage.getItem('zentryx_theme_mode') as any) || 'night';
  });

  // Podcast / Talk Show Mode & Playback Speed
  const [isPodcastMode, setIsPodcastMode] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_podcast_mode') === 'true';
  });
  const [playbackRate, setPlaybackRate] = useState<number>(() => {
    const saved = localStorage.getItem('zentryx_playback_rate');
    return saved ? Number(saved) : 1.0;
  });

  // In-Car Rest Stop Sleep Timer
  const [sleepTimerMinutes, setSleepTimerMinutes] = useState<number | null>(null);
  const [sleepTimerRemainingSec, setSleepTimerRemainingSec] = useState<number | null>(null);

  // Deadzone & Tunnel Guard
  const [deadzoneGuardMode, setDeadzoneGuardMode] = useState<'standard' | 'aggressive'>(() => {
    return (localStorage.getItem('zentryx_deadzone_guard') as any) || 'standard';
  });

  // MediaTek 825X_Pro Hardware Boost & Non-blocking Metadata Collection
  const [mediatekEcoMode, setMediatekEcoMode] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_mediatek_eco') !== 'false';
  });
  const [autoFetchMetadata, setAutoFetchMetadata] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_auto_metadata') !== 'false';
  });
  const lastTimeUpdateSecRef = useRef<number>(-1);
  const checkedMetaIdsRef = useRef<Set<string>>(new Set());
  const metadataAbortRef = useRef<AbortController | null>(null);
  const persistTracksTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Batch Syncing State
  const [isBatchSyncing, setIsBatchSyncing] = useState<boolean>(false);
  const [batchSyncProgress, setBatchSyncProgress] = useState<{ current: number; total: number; trackTitle: string } | null>(null);

  // Category 1: Auto-Resume on Boot + Soft Volume Ramp
  const [autoResumeOnBoot, setAutoResumeOnBoot] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_auto_resume') !== 'false';
  });
  const [autoResumeSoftRamp, setAutoResumeSoftRamp] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_soft_ramp') !== 'false';
  });
  const [softStartGain, setSoftStartGain] = useState<number>(1);
  const softStartRafRef = useRef<number | null>(null);
  const bootResumeCheckedRef = useRef<boolean>(false);
  const pendingResumeSeekRef = useRef<{ trackId: string; positionSeconds: number } | null>(null);
  const pendingSoftStartOnPlayRef = useRef<boolean>(false);

  // Category 2: Driving Ergonomics & Night Comfort
  const [idleScreensaverSeconds, setIdleScreensaverSeconds] = useState<number>(() => {
    const saved = localStorage.getItem('zentryx_idle_screensaver_sec');
    const parsed = saved !== null ? Number(saved) : 30;
    return [0, 15, 30, 60].includes(parsed) ? parsed : 30;
  });
  const [screensaverActive, setScreensaverActive] = useState<boolean>(false);
  const lastInteractionRef = useRef<number>(Date.now());

  const [nightBrightness, setNightBrightness] = useState<number>(() => {
    const saved = localStorage.getItem('zentryx_night_brightness');
    const parsed = saved !== null ? Number(saved) : 100;
    return Number.isFinite(parsed) ? Math.max(35, Math.min(100, parsed)) : 100;
  });
  const [warmAmberFilter, setWarmAmberFilter] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_amber_filter') === 'true';
  });

  const [driverSafeMode, setDriverSafeMode] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_driver_safe') !== 'false';
  });

  const [swcBindings, setSwcBindings] = useState<SteeringKeyBindings>(() => {
    try {
      const saved = localStorage.getItem('zentryx_swc_bindings');
      return saved ? sanitizeSteeringBindings(JSON.parse(saved)) : { ...DEFAULT_SWC_BINDINGS };
    } catch {
      return { ...DEFAULT_SWC_BINDINGS };
    }
  });
  const [swcLongPressBindings, setSwcLongPressBindings] = useState<SteeringLongPressBindings>(() => {
    try {
      const saved = localStorage.getItem('zentryx_swc_long_bindings');
      return saved
        ? sanitizeSteeringLongPressBindings(JSON.parse(saved))
        : { ...DEFAULT_SWC_LONG_PRESS_BINDINGS };
    } catch {
      return { ...DEFAULT_SWC_LONG_PRESS_BINDINGS };
    }
  });
  const isListeningSwcKeyRef = useRef<boolean>(false);

  // Multi-Profile Driver Slots
  const [driverProfiles, setDriverProfiles] = useState<DriverProfile[]>(() => {
    try {
      const saved = localStorage.getItem('zentryx_driver_profiles');
      return saved ? sanitizeDriverProfiles(JSON.parse(saved)) : DEFAULT_DRIVER_PROFILES.map((p) => ({ ...p }));
    } catch {
      return DEFAULT_DRIVER_PROFILES.map((p) => ({ ...p }));
    }
  });
  const [activeDriverProfileId, setActiveDriverProfileId] = useState<string>(() => {
    return localStorage.getItem('zentryx_active_driver_profile') || 'venki';
  });

  // Android Headunit Hardware Automation & Floating Cockpit PiP Bar
  const [autoPlayOnBluetooth, setAutoPlayOnBluetooth] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_bt_autoplay') !== 'false';
  });
  const [duckOnReverseGear, setDuckOnReverseGear] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_reverse_duck') !== 'false';
  });
  const [globalAudioSessionAttach, setGlobalAudioSessionAttach] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_global_eq') === 'true';
  });
  const [floatingMiniBarOpen, setFloatingMiniBarOpen] = useState<boolean>(false);

  // Category 3: ReplayGain, Crossfade, Smart Rolling Cache, Stats & Jump-to-Track
  const [replayGainEnabled, setReplayGainEnabled] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_replay_gain') !== 'false';
  });
  const [crossfadeSeconds, setCrossfadeSeconds] = useState<number>(() => {
    const saved = localStorage.getItem('zentryx_crossfade_sec');
    const parsed = saved !== null ? Number(saved) : 0;
    return Number.isFinite(parsed) ? Math.max(0, Math.min(8, parsed)) : 0;
  });
  const [crossfadeGain, setCrossfadeGain] = useState<number>(1);
  const crossfadeTriggeredForTrackRef = useRef<string | null>(null);
  const crossfadeIncomingRafRef = useRef<number | null>(null);

  const [smartRollingCache, setSmartRollingCache] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_rolling_cache') === 'true';
  });
  const [offlineCacheCapMb, setOfflineCacheCapMb] = useState<number>(() => {
    const saved = localStorage.getItem('zentryx_cache_cap_mb');
    const parsed = saved !== null ? Number(saved) : 1024;
    return [500, 1024, 2048].includes(parsed) ? parsed : 1024;
  });

  const [playStats, setPlayStats] = useState<Record<string, TrackPlayStat>>(() => {
    try {
      const saved = localStorage.getItem('zentryx_play_stats');
      return saved ? JSON.parse(saved) : {};
    } catch {
      return {};
    }
  });
  const [resumableProgressMap, setResumableProgressMap] = useState<
    Record<string, { positionSeconds: number; durationSeconds: number; updatedAt: number }>
  >(() => {
    try {
      const saved = localStorage.getItem('zentryx_resume_map');
      return saved ? JSON.parse(saved) : {};
    } catch {
      return {};
    }
  });
  const lastRecordedPlayTrackIdRef = useRef<string | null>(null);
  const [libraryScrollRequest, setLibraryScrollRequest] = useState<{ index: number; nonce: number } | null>(null);

  // General Headunit UI Usability Features
  const [dualPaneCockpit, setDualPaneCockpit] = useState<boolean>(() => {
    return localStorage.getItem('zentryx_dual_pane_cockpit') !== 'false';
  });
  const [driveModeAutoSpeedKmH, setDriveModeAutoSpeedKmH] = useState<number>(() => {
    const saved = localStorage.getItem('zentryx_drive_auto_speed');
    const parsed = saved !== null ? Number(saved) : 0;
    return [0, 15, 30].includes(parsed) ? parsed : 0;
  });
  const [isDriveModeOpen, setIsDriveModeOpen] = useState<boolean>(false);
  const driveModeDismissedAtSpeedRef = useRef<boolean>(false);
  const [isStreamInspectorOpen, setIsStreamInspectorOpen] = useState<boolean>(false);
  const [isVoiceListening, setIsVoiceListening] = useState<boolean>(false);
  const voiceRecognitionRef = useRef<any>(null);

  // Favorites & Downloaded Sets
  const [favorites, setFavorites] = useState<Set<string>>(() => {
    try {
      const saved = localStorage.getItem('zentryx_favorites');
      return new Set(saved ? JSON.parse(saved) : []);
    } catch {
      return new Set();
    }
  });

  const [downloadedTracks, setDownloadedTracks] = useState<Set<string>>(() => {
    try {
      const saved = localStorage.getItem('zentryx_downloaded');
      return new Set(saved ? JSON.parse(saved) : []);
    } catch {
      return new Set();
    }
  });

  const [downloadingId, setDownloadingId] = useState<string | null>(null);

  // Library & Search State
  const [libraryFilter, setLibraryFilter] = useState<LibraryFilterType>('all');
  const [selectedAlbum, setSelectedAlbum] = useState<string | null>(null);
  const [selectedArtist, setSelectedArtist] = useState<string | null>(null);
  const [selectedFolderPath, setSelectedFolderPath] = useState<string>('');
  const [selectedPlaylist, setSelectedPlaylist] = useState<Playlist | null>(null);
  const [isSyncingPlaylists, setIsSyncingPlaylists] = useState(false);
  const [isSyncingBackup, setIsSyncingBackup] = useState(false);
  const [librarySearchQuery, setLibrarySearchQuery] = useState('');
  const [searchQuery, setSearchQuery] = useState('');

  // Driver Safe Motion Lock & Astronomical Solar GPS Day/Night Mode
  const driverSafeActive = isDriverSafeLockActive(driverSafeMode, currentGpsSpeed, 10);
  const isDaylightCockpit = useMemo(() => {
    if (cockpitThemeMode === 'day') return true;
    if (cockpitThemeMode === 'night') return false;
    return isDaylightBySolar(gpsCoords?.lat, gpsCoords?.lon);
  }, [cockpitThemeMode, gpsCoords, solarMinuteTick]);

  // Current Track Pointer from active Queue (fallback to indexed tracks)
  const currentTrack: Track | undefined = queue[currentTrackIndex] || tracks[currentTrackIndex];

  // Persist Accent & Theme Mode
  useEffect(() => {
    localStorage.setItem('zentryx_accent', accentKey);
  }, [accentKey]);

  useEffect(() => {
    localStorage.setItem('zentryx_theme_mode', cockpitThemeMode);
  }, [cockpitThemeMode]);

  // Persist Favorites
  useEffect(() => {
    localStorage.setItem('zentryx_favorites', JSON.stringify(Array.from(favorites)));
  }, [favorites]);

  // Persist Downloads
  // Refresh offline storage metrics, cached metadata, and stored playlists on mount
  const refreshOfflineStorage = async () => {
    try {
      const records = await getAllOfflineTracks();
      const ids = new Set(records.map((r) => r.id));
      setOfflineTrackIds(ids);
      const totalBytes = records.reduce((acc, r) => acc + (r.blob ? r.blob.size : 0), 0);
      setOfflineStorageBytes(totalBytes);

      const covers: Record<string, string> = {};
      for (const rec of records) {
        if (rec.coverBlob) {
          covers[rec.id] = URL.createObjectURL(rec.coverBlob);
        }
      }

      // Hydrate persistent ID3/FLAC/OGG/M4A metadata cache from IndexedDB asynchronously
      const cachedMetaList = await getAllCachedTrackMetadata();
      if (cachedMetaList.length > 0) {
        const metaMap = new Map(cachedMetaList.map((m) => [m.id, m]));
        for (const m of cachedMetaList) {
          checkedMetaIdsRef.current.add(m.id);
          if (m.coverBlob && !covers[m.id]) {
            covers[m.id] = URL.createObjectURL(m.coverBlob);
          }
        }
        setTracks((prev) =>
          prev.map((t) => {
            const cached = metaMap.get(t.id);
            return cached ? mergeTrackMetadata(t, cached) : t;
          })
        );
        setQueue((prev) =>
          prev.map((t) => {
            const cached = metaMap.get(t.id);
            return cached ? mergeTrackMetadata(t, cached) : t;
          })
        );
      }

      if (Object.keys(covers).length > 0) {
        setCoverArtUrls((prev) => ({ ...covers, ...prev }));
      }
    } catch {}

    try {
      const storedPls = await getAllPlaylists();
      setPlaylists(storedPls);
    } catch {}

    try {
      if (typeof navigator !== 'undefined' && navigator.storage?.estimate) {
        const est = await navigator.storage.estimate();
        const persisted = navigator.storage.persisted ? await navigator.storage.persisted() : false;
        setStorageQuota({
          usageBytes: est.usage ?? 0,
          quotaBytes: est.quota ?? 0,
          persisted,
        });
      }
    } catch {}
  };

  useEffect(() => {
    refreshOfflineStorage();
    loadSavedOfflineFolder().then((saved) => {
      if (saved) {
        setOfflineFolderHandle(saved.handle);
        setOfflineFolderName(saved.name);
        setOfflineFolderPermission(saved.permission);
      }
    });
  }, []);

  const applyMenuNavEntry = useCallback((entry: MenuNavEntry) => {
    setActiveTab(entry.tab);
    setLibraryFilter(entry.libraryFilter);
    setSelectedAlbum(entry.selectedAlbum);
    setSelectedArtist(entry.selectedArtist);
  }, []);

  const navigateToMenu = useCallback(
    (patch: Partial<MenuNavEntry>) => {
      const curIdx = navHistoryIndexRef.current;
      const list = navHistoryRef.current;
      const cur = list[curIdx] || {
        tab: activeTab,
        libraryFilter,
        selectedAlbum,
        selectedArtist,
      };
      const next: MenuNavEntry = {
        tab: patch.tab ?? cur.tab,
        libraryFilter: patch.libraryFilter ?? cur.libraryFilter,
        selectedAlbum: patch.selectedAlbum !== undefined ? patch.selectedAlbum : cur.selectedAlbum,
        selectedArtist: patch.selectedArtist !== undefined ? patch.selectedArtist : cur.selectedArtist,
      };

      const same =
        cur.tab === next.tab &&
        cur.libraryFilter === next.libraryFilter &&
        cur.selectedAlbum === next.selectedAlbum &&
        cur.selectedArtist === next.selectedArtist;

      applyMenuNavEntry(next);
      if (same) return;

      const truncated = list.slice(0, curIdx + 1);
      const updated = [...truncated, next];
      setNavHistory(updated);
      setNavHistoryIndex(updated.length - 1);
    },
    [activeTab, libraryFilter, selectedAlbum, selectedArtist, applyMenuNavEntry]
  );

  const canMenuGoBack = navHistoryIndex > 0;
  const canMenuGoForward = navHistoryIndex < navHistory.length - 1;

  const handleMenuBack = useCallback(() => {
    const idx = navHistoryIndexRef.current;
    if (idx <= 0) return;
    const target = navHistoryRef.current[idx - 1];
    if (!target) return;
    setNavHistoryIndex(idx - 1);
    applyMenuNavEntry(target);
  }, [applyMenuNavEntry]);

  const handleMenuForward = useCallback(() => {
    const idx = navHistoryIndexRef.current;
    if (idx >= navHistoryRef.current.length - 1) return;
    const target = navHistoryRef.current[idx + 1];
    if (!target) return;
    setNavHistoryIndex(idx + 1);
    applyMenuNavEntry(target);
  }, [applyMenuNavEntry]);

  const handlePickOfflineFolder = async () => {
    try {
      const picked = await pickOfflineFolder();
      setOfflineFolderHandle(picked.handle);
      setOfflineFolderName(picked.name);
      setOfflineFolderPermission(picked.permission);
      setStatusMessage(`Offline download folder set to "${picked.name}".`);
      setTimeout(() => setStatusMessage(null), 3500);
    } catch (err: any) {
      if (err?.name === 'AbortError') return;
      setStatusMessage(err?.message || 'Could not open folder picker.');
      setTimeout(() => setStatusMessage(null), 4000);
    }
  };

  const handleAuthorizeOfflineFolder = async () => {
    const ok = await authorizeSavedOfflineFolder(offlineFolderHandle);
    setOfflineFolderPermission(ok ? 'granted' : 'denied');
    setStatusMessage(
      ok
        ? `Write permission verified for "${offlineFolderName}".`
        : 'Folder write permission was declined.'
    );
    setTimeout(() => setStatusMessage(null), 3000);
  };

  const handleClearOfflineFolder = async () => {
    await clearSavedOfflineFolder();
    setOfflineFolderHandle(null);
    setOfflineFolderName(null);
    setOfflineFolderPermission('prompt');
    setStatusMessage('Offline downloads reset to browser IndexedDB cache.');
    setTimeout(() => setStatusMessage(null), 3000);
  };

  const handleRequestPersistentStorage = async () => {
    try {
      if (typeof navigator !== 'undefined' && navigator.storage?.persist) {
        const granted = await navigator.storage.persist();
        await refreshOfflineStorage();
        setStatusMessage(
          granted
            ? 'Persistent storage pinned — Android/Chrome will protect offline tracks.'
            : 'Browser retained best-effort storage mode.'
        );
        setTimeout(() => setStatusMessage(null), 3000);
      }
    } catch {}
  };

  // Screen WakeLock API integration for driving
  useEffect(() => {
    const acquireWakeLock = async () => {
      try {
        if ('wakeLock' in navigator && isPlaying && !wakeLockRef.current) {
          wakeLockRef.current = await (navigator as any).wakeLock.request('screen');
        }
      } catch {}
    };

    const dropWakeLock = async () => {
      try {
        if (wakeLockRef.current) {
          await wakeLockRef.current.release();
          wakeLockRef.current = null;
        }
      } catch {}
    };

    if (isPlaying) {
      acquireWakeLock();
    } else {
      dropWakeLock();
    }

    return () => {
      dropWakeLock();
    };
  }, [isPlaying]);

  // Fullscreen change listener
  useEffect(() => {
    const onFsChange = () => setIsFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener('fullscreenchange', onFsChange);
    return () => document.removeEventListener('fullscreenchange', onFsChange);
  }, []);

  // Hotspot Network Roaming & Flap Auto-Recovery
  useEffect(() => {
    const onOnline = () => {
      setIsOnline(true);
      setStatusMessage('Hotspot connection restored. Re-syncing stream...');
      setTimeout(() => setStatusMessage(null), 3000);

      if (isPlaying && currentTrack && !isRecoveringStreamRef.current) {
        isRecoveringStreamRef.current = true;
        setTimeout(async () => {
          try {
            await startAudioPlayback(currentTrack);
          } finally {
            isRecoveringStreamRef.current = false;
          }
        }, 800);
      }
    };

    const onOffline = () => {
      setIsOnline(false);
      setStatusMessage('Hotspot connection dropped. Auto-reconnect armed.');
    };

    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, [isPlaying, currentTrack]);

  // In-Car Steering Wheel & MediaSession API Integration
  useEffect(() => {
    if (!('mediaSession' in navigator) || !currentTrack) return;
    const cover = coverArtUrls[currentTrack.id];
    const artwork = cover
      ? [{ src: cover, sizes: '512x512', type: 'image/jpeg' }]
      : [];

    navigator.mediaSession.metadata = new MediaMetadata({
      title: currentTrack.title,
      artist: currentTrack.artist || 'Nextcloud Music',
      album: currentTrack.album || 'Cockpit',
      artwork,
    });

    navigator.mediaSession.playbackState = isPlaying ? 'playing' : 'paused';

    navigator.mediaSession.setActionHandler('play', () => togglePlayPause());
    navigator.mediaSession.setActionHandler('pause', () => togglePlayPause());
    navigator.mediaSession.setActionHandler('previoustrack', () => handlePrevTrack());
    navigator.mediaSession.setActionHandler('nexttrack', () => handleNextTrack());
    navigator.mediaSession.setActionHandler('seekbackward', (details) => {
      const skip = details.seekOffset || 15;
      seekTo(Math.max(0, positionSeconds - skip));
    });
    navigator.mediaSession.setActionHandler('seekforward', (details) => {
      const skip = details.seekOffset || 15;
      seekTo(Math.min(durationSeconds, positionSeconds + skip));
    });
    navigator.mediaSession.setActionHandler('seekto', (details) => {
      if (typeof details.seekTime === 'number') {
        seekTo(details.seekTime);
      }
    });
  }, [currentTrack, isPlaying, positionSeconds, durationSeconds, coverArtUrls]);

  const handleCycleSurroundMode = () => {
    const order: SurroundModeId[] = [
      'off',
      'logic7_surround',
      'concert_hall',
      'studio_focused',
      'dolby_pro_logic_ii',
      'dolby_atmos_virtual',
      'virtual_surround_360',
    ];
    setDspSettings((prev) => {
      const idx = order.indexOf(prev.surroundMode ?? 'off');
      const nextMode = order[(idx + 1) % order.length];
      setStatusMessage(`Surround Mode: ${SURROUND_MODES[nextMode].label}`);
      setTimeout(() => setStatusMessage(null), 2200);
      return {
        ...prev,
        dspEnabled: true,
        surroundMode: nextMode,
        monoFoldDown: nextMode !== 'off' ? false : prev.monoFoldDown,
      };
    });
  };

  // In-Car Steering Wheel & Remappable CANBUS Shortcuts (Short-Press & Long-Press >= 550ms)
  useEffect(() => {
    const executeSwcAction = (swcAction: SteeringAction) => {
      if (swcAction === 'play_pause') {
        togglePlayPause();
      } else if (swcAction === 'next') {
        handleNextTrack();
      } else if (swcAction === 'prev') {
        handlePrevTrack();
      } else if (swcAction === 'mute') {
        setIsMuted((m) => !m);
      } else if (swcAction === 'duck') {
        handleToggleAttenuation();
      } else if (swcAction === 'visualizer') {
        setIsVisualizerOpen((v) => !v);
      } else if (swcAction === 'favorite') {
        if (currentTrack) toggleFavorite(currentTrack.id);
      } else if (swcAction === 'cruising_mix') {
        handlePlaySmartMix('cruising');
      } else if (swcAction === 'drive_mode') {
        setIsDriveModeOpen((d) => !d);
      } else if (swcAction === 'cycle_surround') {
        handleCycleSurroundMode();
      }
    };

    const activeHolds = new Map<string, { timer: number; triggeredLong: boolean; shortAction: SteeringAction | null }>();

    const handleKeyDown = (e: KeyboardEvent) => {
      if (isListeningSwcKeyRef.current) return;
      if (['INPUT', 'TEXTAREA'].includes((e.target as HTMLElement)?.tagName)) {
        return;
      }

      const keyId = e.code || e.key;
      const shortAction = resolveSteeringAction(e.key, e.code, swcBindings);
      const longAction = resolveSteeringAction(e.key, e.code, swcLongPressBindings);

      if (shortAction || longAction) {
        e.preventDefault();
        if (e.repeat) return;
        if (longAction) {
          if (activeHolds.has(keyId)) return;
          const timer = window.setTimeout(() => {
            const entry = activeHolds.get(keyId);
            if (entry) {
              entry.triggeredLong = true;
              executeSwcAction(longAction);
            }
          }, 550);
          activeHolds.set(keyId, { timer, triggeredLong: false, shortAction });
        } else if (shortAction) {
          executeSwcAction(shortAction);
        }
        return;
      }

      if (e.code === 'ArrowRight') {
        e.preventDefault();
        if (e.shiftKey) {
          seekTo(positionSeconds + 15);
        } else {
          handleNextTrack();
        }
      } else if (e.code === 'ArrowLeft') {
        e.preventDefault();
        if (e.shiftKey) {
          seekTo(positionSeconds - 15);
        } else {
          handlePrevTrack();
        }
      } else if (e.code === 'ArrowUp') {
        e.preventDefault();
        setVolume((v) => Math.min(100, v + 5));
        setIsMuted(false);
      } else if (e.code === 'ArrowDown') {
        e.preventDefault();
        setVolume((v) => Math.max(0, v - 5));
      } else if (e.key === 'f' || e.key === 'F') {
        e.preventDefault();
        toggleFullscreen();
      } else if (e.key === 'q' || e.key === 'Q') {
        e.preventDefault();
        setIsQueueOpen((o) => !o);
      }
    };

    const handleKeyUp = (e: KeyboardEvent) => {
      const keyId = e.code || e.key;
      const entry = activeHolds.get(keyId);
      if (!entry) return;
      window.clearTimeout(entry.timer);
      activeHolds.delete(keyId);
      if (!entry.triggeredLong && entry.shortAction) {
        executeSwcAction(entry.shortAction);
      }
    };

    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    return () => {
      activeHolds.forEach((entry) => window.clearTimeout(entry.timer));
      activeHolds.clear();
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
    };
  }, [positionSeconds, durationSeconds, isPlaying, currentTrackIndex, queue, swcBindings, swcLongPressBindings, currentTrack]);

  // Helpers: Format Seconds and Bytes
  const formatTime = (secs: number) => {
    if (isNaN(secs) || secs < 0) return '0:00';
    const m = Math.floor(secs / 60);
    const s = Math.floor(secs % 60);
    return `${m}:${s < 10 ? '0' : ''}${s}`;
  };

  const formatBytes = (bytes: number) => {
    if (!bytes || bytes <= 0) return '0 MB';
    return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  };

  // Real Diagnostic Logger: Captures actual network, audio, and proxy events
  const addDiagnosticLog = (entry: Omit<DiagnosticLogEntry, 'id' | 'timestamp'>) => {
    const now = new Date();
    const timeStr = now.toLocaleTimeString() + '.' + String(now.getMilliseconds()).padStart(3, '0');
    const newEntry: DiagnosticLogEntry = {
      ...entry,
      id: Math.random().toString(36).substring(2, 9),
      timestamp: timeStr,
    };
    setDiagnosticLogs((prev) => [newEntry, ...prev.slice(0, 75)]);
  };

  // Fetch real server-side proxy wire logs from /tmp/proxy.log
  const fetchServerProxyLogs = async () => {
    setIsLoadingServerLogs(true);
    try {
      const res = await fetch('/api/diagnostics/logs');
      if (res.ok) {
        const text = await res.text();
        setServerProxyLogs(text || 'No proxy wire transactions recorded yet.');
      } else {
        setServerProxyLogs(`HTTP ${res.status}: Failed to read server proxy log.`);
      }
    } catch (e: any) {
      setServerProxyLogs(`Error fetching proxy logs: ${e.message}`);
    } finally {
      setIsLoadingServerLogs(false);
    }
  };

  // Clear real server-side proxy wire logs
  const clearServerProxyLogs = async () => {
    try {
      await fetch('/api/diagnostics/clear');
      setServerProxyLogs('[Server proxy log cleared]');
      addDiagnosticLog({
        type: 'INFO',
        action: 'LOGS_CLEARED',
        url: '/api/diagnostics/clear',
        details: 'Server wire log was reset.',
      });
    } catch (e: any) {
      addDiagnosticLog({
        type: 'ERROR',
        action: 'CLEAR_FAILED',
        url: '/api/diagnostics/clear',
        details: `Failed to clear logs: ${e.message}`,
      });
    }
  };

  // Run comprehensive live diagnostic sequence to troubleshoot HTTP 500 error
  const runStreamDiagnostics = async () => {
    setIsDiagnosing(true);
    addDiagnosticLog({
      type: 'INFO',
      action: 'DIAGNOSTIC_START',
      url: 'cloud.zentryx.bg',
      details: `Starting comprehensive live diagnostic sequence for "${username || 'anonymous'}"...`,
    });

    const targetTrack = currentTrack || tracks[0];

    // STEP 1: Verify Nextcloud Server API status
    try {
      const t0 = performance.now();
      const statusRes = await fetch('/api/nextcloud/status.php');
      const dur = Math.round(performance.now() - t0);
      const json = await statusRes.json();
      addDiagnosticLog({
        type: statusRes.ok ? 'SUCCESS' : 'ERROR',
        action: 'SERVER_STATUS',
        method: 'GET',
        url: '/api/nextcloud/status.php',
        status: statusRes.status,
        statusText: statusRes.statusText,
        details: `Nextcloud version: ${json.versionstring || json.version || 'unknown'} (Maintenance: ${Boolean(json.maintenance)}) [${dur}ms]`,
        body: JSON.stringify(json, null, 2),
      });
    } catch (e: any) {
      addDiagnosticLog({
        type: 'ERROR',
        action: 'SERVER_STATUS',
        method: 'GET',
        url: '/api/nextcloud/status.php',
        details: `Status check failed: ${e.message}`,
      });
    }

    // STEP 2: Verify WebDAV authentication handshake
    if (username && password) {
      const authHeader = `Basic ${safeBtoa(`${username}:${password}`)}`;
      try {
        const t0 = performance.now();
        const testPath = `/api/nextcloud/remote.php/dav/files/${encodeURIComponent(username)}/`;
        const davRes = await fetch(testPath, {
          method: 'PROPFIND',
          headers: {
            Authorization: authHeader,
            Depth: '0',
            'Content-Type': 'application/xml; charset=utf-8',
          },
        });
        const dur = Math.round(performance.now() - t0);
        const xml = await davRes.text();
        const headersObj: Record<string, string> = {};
        davRes.headers.forEach((v, k) => { headersObj[k] = v; });

        addDiagnosticLog({
          type: (davRes.status === 200 || davRes.status === 207) ? 'SUCCESS' : 'ERROR',
          action: 'AUTH_PROPFIND',
          method: 'PROPFIND',
          url: testPath,
          status: davRes.status,
          statusText: davRes.statusText,
          details: `Depth 0 handshake returned HTTP ${davRes.status} in ${dur}ms`,
          headers: headersObj,
          body: xml.slice(0, 1500),
        });
      } catch (e: any) {
        addDiagnosticLog({
          type: 'ERROR',
          action: 'AUTH_PROPFIND',
          method: 'PROPFIND',
          url: `/api/nextcloud/remote.php/dav/files/${encodeURIComponent(username)}/`,
          details: `Auth handshake network error: ${e.message}`,
        });
      }
    } else {
      addDiagnosticLog({
        type: 'WARN',
        action: 'AUTH_PROPFIND',
        url: 'Nextcloud Credentials',
        details: 'Not currently signed in. Sign in first to test authenticated WebDAV endpoints.',
      });
    }

    // STEP 3 & 4: Live Probe on Current Track's Endpoints (Legacy WebDAV vs Canonical DAV Files)
    if (targetTrack) {
      const authHeader = username && password ? `Basic ${safeBtoa(`${username}:${password}`)}` : '';
      const candidates = getTrackCandidateUrls(targetTrack);

      for (let i = 0; i < candidates.length; i++) {
        const cand = candidates[i];
        const cleanUrl = cand.replace(/\?auth=[^&]*/g, '').replace(/&auth=[^&]*/g, '');
        const label = cand.includes('/api/audio-stream')
          ? 'Resilient Audio Pipeline'
          : cand.includes('/remote.php/webdav/')
          ? 'Legacy WebDAV Stream'
          : 'Canonical DAV Files Stream';
        const actionName = cand.includes('/api/audio-stream')
          ? 'PROBE_AUDIO_STREAM'
          : cand.includes('/remote.php/webdav/')
          ? 'PROBE_LEGACY_WEBDAV'
          : 'PROBE_DAV_FILES';

        try {
          const t0 = performance.now();
          const streamRes = await fetch(cleanUrl, {
            method: 'GET',
            headers: {
              ...(authHeader ? { Authorization: authHeader } : {}),
              Range: 'bytes=0-1024',
            },
          });
          const dur = Math.round(performance.now() - t0);
          const headersObj: Record<string, string> = {};
          streamRes.headers.forEach((v, k) => { headersObj[k] = v; });

          let bodySnippet = '';
          if (streamRes.status >= 400) {
            bodySnippet = await streamRes.text();
          } else {
            const ab = await streamRes.arrayBuffer();
            bodySnippet = `[Audio Stream Verified]: Received first ${ab.byteLength} bytes chunk successfully.`;
          }

          addDiagnosticLog({
            type: (streamRes.status === 200 || streamRes.status === 206) ? 'SUCCESS' : 'ERROR',
            action: actionName,
            method: 'GET (Range 0-1024)',
            url: cleanUrl,
            status: streamRes.status,
            statusText: streamRes.statusText,
            details: `[${label}] Track: "${targetTrack.title}" (${targetTrack.fileName}) returned HTTP ${streamRes.status} in ${dur}ms`,
            headers: headersObj,
            body: bodySnippet.slice(0, 2000),
          });
        } catch (e: any) {
          addDiagnosticLog({
            type: 'ERROR',
            action: actionName,
            method: 'GET',
            url: cleanUrl,
            details: `Failed to probe [${label}]: ${e.message}`,
          });
        }
      }
    } else {
      addDiagnosticLog({
        type: 'WARN',
        action: 'TRACK_PROBE',
        url: 'Music Library',
        details: 'No track currently selected. Rescan or select a track from Library to test file streaming.',
      });
    }

    // Refresh server proxy wire logs
    await fetchServerProxyLogs();

    addDiagnosticLog({
      type: 'INFO',
      action: 'DIAGNOSTIC_COMPLETE',
      url: 'cloud.zentryx.bg',
      details: 'Diagnostic sequence complete. Review response codes and headers in the logs below.',
    });
    setIsDiagnosing(false);
  };

  const copyDiagnosticReport = () => {
    const reportLines = [
      `=== ZENTRYX NEXTCLOUD STREAM DIAGNOSTIC REPORT ===`,
      `Generated: ${new Date().toISOString()}`,
      `User: ${username || 'anonymous'}`,
      `Server: ${serverUrl}`,
      `Folder: /${musicFolder || 'Root'}`,
      `Total Tracks Indexed: ${tracks.length}`,
      `Current Track: ${currentTrack ? `${currentTrack.title} (${currentTrack.fileName})` : 'None'}`,
      ``,
      `--- RECENT CLIENT & STREAM EVENTS (${diagnosticLogs.length}) ---`,
      ...diagnosticLogs.map(
        (l) =>
          `[${l.timestamp}] [${l.type}] ${l.action} ${l.method ? `[${l.method}]` : ''} ${l.status ? `(HTTP ${l.status})` : ''} ${l.url}\nDetails: ${l.details || 'N/A'}${
            l.body ? `\nBody: ${l.body.slice(0, 500)}` : ''
          }`
      ),
      ``,
      `--- RAW SERVER PROXY WIRE LOGS (/tmp/proxy.log) ---`,
      serverProxyLogs || '(No server proxy logs)',
    ];
    navigator.clipboard.writeText(reportLines.join('\n'));
    setCopiedReport(true);
    setTimeout(() => setCopiedReport(false), 2500);
  };

  // Auto-fetch server proxy logs whenever Settings tab is opened
  useEffect(() => {
    if (activeTab === 'settings') {
      fetchServerProxyLogs();
    }
  }, [activeTab]);

  // Helper: Parse Raw WebDAV Folder
  const fetchWebDAVFolder = async (folderPath: string, user: string, pass: string) => {
    const basicAuth = safeBtoa(`${user}:${pass}`);
    const cleanPath = folderPath.replace(/^\/+/, '').replace(/\/+$/, '');
    const encodedSegments = cleanPath ? cleanPath.split('/').map(encodeURIComponent).join('/') : '';
    const url = `/api/nextcloud/remote.php/dav/files/${encodeURIComponent(user)}${encodedSegments ? '/' + encodedSegments : ''}/`;

    const res = await fetch(url, {
      method: 'PROPFIND',
      headers: {
        Authorization: `Basic ${basicAuth}`,
        Depth: '1',
        'Content-Type': 'application/xml; charset=utf-8',
      },
    });

    if (!res.ok) {
      throw new Error(`WebDAV HTTP ${res.status}: ${res.statusText}`);
    }

    const xmlText = await res.text();
    const parser = new DOMParser();
    const xmlDoc = parser.parseFromString(xmlText, 'application/xml');

    const parseError = xmlDoc.querySelector('parsererror');
    if (parseError) {
      throw new Error('Failed to parse WebDAV XML response');
    }

    const responses = Array.from(xmlDoc.getElementsByTagNameNS('*', 'response'));
    const files: Array<{ href: string; fileName: string; size: number; contentType: string }> = [];
    const subfolders: WebDAVFolder[] = [];

    for (const r of responses) {
      const hrefEl = r.getElementsByTagNameNS('*', 'href')[0];
      if (!hrefEl || !hrefEl.textContent) continue;
      const rawHref = hrefEl.textContent;
      const decodedHref = decodeURIComponent(rawHref);

      const resType = r.getElementsByTagNameNS('*', 'resourcetype')[0];
      const isCollection = resType && resType.getElementsByTagNameNS('*', 'collection').length > 0;

      // Robust relative path extraction: matches anything after /files/<username>/ or /webdav/
      const match = decodedHref.match(/^.*\/files\/[^/]+\/?(.*)$/i) || decodedHref.match(/^.*\/webdav\/?(.*)$/i);
      const relPath = match ? match[1].replace(/^\/+/, '').replace(/\/+$/, '') : '';

      // Skip the queried folder itself
      const currentClean = cleanPath.toLowerCase();
      const targetRelClean = relPath.toLowerCase();
      if (targetRelClean === currentClean) {
        continue;
      }

      // Extract name from href or displayname
      const displayNameEl = r.getElementsByTagNameNS('*', 'displayname')[0];
      const name = displayNameEl?.textContent || decodedHref.replace(/\/+$/, '').split('/').pop() || '';

      if (isCollection) {
        if (name && !name.startsWith('.')) {
          subfolders.push({
            href: rawHref,
            name,
            path: relPath,
          });
        }
      } else {
        const ctEl = r.getElementsByTagNameNS('*', 'getcontenttype')[0];
        const contentType = ctEl?.textContent || '';
        const clEl = r.getElementsByTagNameNS('*', 'getcontentlength')[0];
        const size = clEl ? parseInt(clEl.textContent || '0', 10) : 0;

        const isAudio =
          contentType.startsWith('audio/') ||
          /\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|m4b|alac|aiff)$/i.test(name);

        if (isAudio) {
          files.push({
            href: rawHref,
            fileName: name,
            size,
            contentType,
          });
        }
      }
    }

    return { files, subfolders };
  };

  const decodeWebDavPermissions = (rawPermissions: string | null, httpStatus: number | null): string => {
    const raw = (rawPermissions || '').trim().toUpperCase();
    if (raw) {
      const caps: string[] = [];
      if (raw.includes('G')) caps.push('Read');
      if (raw.includes('W')) caps.push('Write');
      if (raw.includes('C')) caps.push('Create Files');
      if (raw.includes('K')) caps.push('Create Folders');
      if (raw.includes('D')) caps.push('Delete');
      if (raw.includes('N')) caps.push('Rename');
      if (raw.includes('V')) caps.push('Move');
      if (raw.includes('S')) caps.push('Shared');
      if (raw.includes('M')) caps.push('External Mount');
      const summary = caps.length > 0 ? caps.join(', ') : 'Accessible';
      return `${summary} [${raw}]`;
    }
    if (httpStatus === 207 || httpStatus === 200) return 'Readable (PROPFIND 207 OK)';
    if (httpStatus === 401) return 'Unauthorized (401 — check credentials / app password)';
    if (httpStatus === 403) return 'Forbidden (403 — folder access denied)';
    if (httpStatus === 404) return 'Not Found (404 — folder missing)';
    if (httpStatus === 405) return 'Method Not Allowed (405)';
    if (httpStatus !== null && httpStatus >= 500 && httpStatus < 600) return `Server Error (HTTP ${httpStatus})`;
    if (httpStatus === null) return 'Connection / SSL Failed';
    return `HTTP ${httpStatus}`;
  };

  const probeWebDavDirectory = async (
    folderPath: string,
    user: string,
    pass: string,
    depth: '0' | '1' = '1',
    forceLegacyWebdav = false
  ) => {
    const cleanPath = folderPath.replace(/^\/+/, '').replace(/\/+$/, '');
    const encodedSegments = cleanPath ? cleanPath.split('/').map(encodeURIComponent).join('/') : '';
    const basePrefix = forceLegacyWebdav
      ? '/api/nextcloud/remote.php/webdav'
      : `/api/nextcloud/remote.php/dav/files/${encodeURIComponent(user)}`;
    const url = `${basePrefix}${encodedSegments ? '/' + encodedSegments : ''}/`;

    const propfindBody = `<?xml version="1.0" encoding="utf-8"?>
<d:propfind xmlns:d="DAV:" xmlns:oc="http://owncloud.org/ns">
  <d:prop>
    <d:resourcetype/>
    <d:getcontentlength/>
    <d:getcontenttype/>
    <d:displayname/>
    <oc:permissions/>
  </d:prop>
</d:propfind>`;

    const t0 = performance.now();
    try {
      const res = await fetch(url, {
        method: 'PROPFIND',
        headers: {
          Authorization: `Basic ${safeBtoa(`${user}:${pass}`)}`,
          Depth: depth,
          'Content-Type': 'application/xml; charset=utf-8',
        },
        body: propfindBody,
      });
      const latencyMs = Math.round(performance.now() - t0);
      const davHeader = res.headers.get('dav');
      if (res.status !== 207 && !(res.status >= 200 && res.status < 300)) {
        return {
          url,
          httpStatus: res.status,
          selfPermissions: null as string | null,
          davHeader,
          subfolders: [] as Array<{ name: string; path: string; permissions: string | null }>,
          audioCount: 0,
          latencyMs,
          error: `HTTP ${res.status} ${res.statusText || ''}`.trim(),
        };
      }

      const xmlText = await res.text();
      const parser = new DOMParser();
      const xmlDoc = parser.parseFromString(xmlText, 'application/xml');
      const parseError = xmlDoc.querySelector('parsererror');
      if (parseError) {
        return {
          url,
          httpStatus: res.status,
          selfPermissions: null as string | null,
          davHeader,
          subfolders: [] as Array<{ name: string; path: string; permissions: string | null }>,
          audioCount: 0,
          latencyMs,
          error: 'XML response parse failed',
        };
      }

      let selfPermissions: string | null = null;
      const subfolders: Array<{ name: string; path: string; permissions: string | null }> = [];
      let audioCount = 0;

      const responses = Array.from(xmlDoc.getElementsByTagNameNS('*', 'response'));
      for (const r of responses) {
        const hrefEl = r.getElementsByTagNameNS('*', 'href')[0];
        if (!hrefEl || !hrefEl.textContent) continue;
        const decodedHref = decodeURIComponent(hrefEl.textContent);
        const match = decodedHref.match(/^.*\/files\/[^/]+\/?(.*)$/i) || decodedHref.match(/^.*\/webdav\/?(.*)$/i);
        const relPath = match ? match[1].replace(/^\/+/, '').replace(/\/+$/, '') : '';
        const permEl = r.getElementsByTagNameNS('*', 'permissions')[0];
        const perms = permEl?.textContent?.trim() || null;

        if (relPath.toLowerCase() === cleanPath.toLowerCase()) {
          if (perms) selfPermissions = perms;
          continue;
        }

        const resType = r.getElementsByTagNameNS('*', 'resourcetype')[0];
        const isCollection = Boolean(resType && resType.getElementsByTagNameNS('*', 'collection').length > 0);
        const displayNameEl = r.getElementsByTagNameNS('*', 'displayname')[0];
        const name = displayNameEl?.textContent || decodedHref.replace(/\/+$/, '').split('/').pop() || '';

        if (isCollection) {
          if (name && !name.startsWith('.')) {
            subfolders.push({ name, path: relPath, permissions: perms });
          }
        } else {
          const ctEl = r.getElementsByTagNameNS('*', 'getcontenttype')[0];
          const contentType = ctEl?.textContent || '';
          const isAudio =
            contentType.startsWith('audio/') ||
            /\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|m4b|alac|aiff)$/i.test(name);
          if (isAudio) audioCount += 1;
        }
      }

      return {
        url,
        httpStatus: res.status,
        selfPermissions,
        davHeader,
        subfolders,
        audioCount,
        latencyMs,
        error: null as string | null,
      };
    } catch (e: any) {
      return {
        url,
        httpStatus: null as number | null,
        selfPermissions: null as string | null,
        davHeader: null as string | null,
        subfolders: [] as Array<{ name: string; path: string; permissions: string | null }>,
        audioCount: 0,
        latencyMs: Math.round(performance.now() - t0),
        error: e?.message || 'Network request failed',
      };
    }
  };

  const runFolderScanDiagnostics = async (startPathOverride?: string) => {
    const runId = ++scanDiagRunIdRef.current;
    const rawTarget = startPathOverride !== undefined ? startPathOverride : (musicFolder || '');
    const startPath = rawTarget.startsWith('local:') ? (musicFolder || '') : rawTarget.replace(/^\/+/, '').replace(/\/+$/, '');
    setScanDiagnosticsStartPath(startPath);
    setShowScanDiagnosticsModal(true);
    setScanDiagnosticsRunning(true);
    setScanDiagnosticsSummary(null);
    setScanDiagnosticsEntries([]);
    setScanDiagnosticsStepText('Step 1: Verifying stored Nextcloud WebDAV credentials...');

    const entries: FolderDiagEntry[] = [];
    const pushOrUpdate = (entry: FolderDiagEntry) => {
      if (scanDiagRunIdRef.current !== runId) return;
      const idx = entries.findIndex((e) => e.id === entry.id);
      if (idx >= 0) entries[idx] = entry;
      else entries.push(entry);
      setScanDiagnosticsEntries([...entries]);
    };

    // Step 1: Credential check
    if (!serverUrl || !username || !password) {
      pushOrUpdate({
        id: 'step_config',
        stepTitle: 'Step 1 · Nextcloud Configuration',
        targetPath: serverUrl || 'Not configured',
        httpStatusCode: null,
        permissionsLabel: 'Missing credentials',
        rawPermissions: null,
        detailMessage: 'Server URL, username, or app password missing. Sign in via Nextcloud Account first.',
        latencyMs: 0,
        status: 'FAIL',
      });
      setScanDiagnosticsRunning(false);
      setScanDiagnosticsStepText('Diagnostics stopped: missing Nextcloud credentials');
      setScanDiagnosticsSummary('Failed at Step 1: Sign in with Nextcloud credentials first.');
      return;
    }

    pushOrUpdate({
      id: 'step_config',
      stepTitle: 'Step 1 · Nextcloud Configuration',
      targetPath: serverUrl,
      httpStatusCode: 200,
      permissionsLabel: 'Credentials Configured',
      rawPermissions: null,
      detailMessage: `User: ${username} • Start folder: /${startPath || 'Root'}`,
      latencyMs: 0,
      status: 'PASS',
    });

    // Step 2: WebDAV Root handshake (Depth: 0)
    setScanDiagnosticsStepText('Step 2: Checking Nextcloud WebDAV root handshake (PROPFIND Depth: 0)...');
    pushOrUpdate({
      id: 'step_root_probe',
      stepTitle: 'Step 2 · WebDAV Root Handshake',
      targetPath: `/remote.php/dav/files/${username}/`,
      httpStatusCode: null,
      permissionsLabel: 'Probing...',
      rawPermissions: null,
      detailMessage: 'Sending PROPFIND Depth: 0 request...',
      latencyMs: 0,
      status: 'RUNNING',
    });

    let useLegacy = false;
    let rootProbe = await probeWebDavDirectory('', username, password, '0', false);
    if (scanDiagRunIdRef.current !== runId) return;
    if (rootProbe.httpStatus === 404 || rootProbe.httpStatus === 405) {
      const legacyProbe = await probeWebDavDirectory('', username, password, '0', true);
      if (scanDiagRunIdRef.current !== runId) return;
      if (legacyProbe.httpStatus === 207 || (legacyProbe.httpStatus !== null && legacyProbe.httpStatus >= 200 && legacyProbe.httpStatus < 300)) {
        rootProbe = legacyProbe;
        useLegacy = true;
      }
    }

    const rootOk = rootProbe.httpStatus === 207 || (rootProbe.httpStatus !== null && rootProbe.httpStatus >= 200 && rootProbe.httpStatus < 300);
    pushOrUpdate({
      id: 'step_root_probe',
      stepTitle: 'Step 2 · WebDAV Root Handshake',
      targetPath: rootProbe.url.replace(/^\/api\/nextcloud/, ''),
      httpStatusCode: rootProbe.httpStatus,
      permissionsLabel: decodeWebDavPermissions(rootProbe.selfPermissions, rootProbe.httpStatus),
      rawPermissions: rootProbe.selfPermissions,
      detailMessage: rootOk
        ? `WebDAV connected (${rootProbe.davHeader || 'DAV endpoint ready'})`
        : (rootProbe.error || `Unexpected status HTTP ${rootProbe.httpStatus ?? 'ERR'}`),
      latencyMs: rootProbe.latencyMs,
      status: rootOk ? 'PASS' : 'FAIL',
    });

    if (!rootOk) {
      setScanDiagnosticsRunning(false);
      setScanDiagnosticsStepText('Diagnostics finished with root connection error');
      setScanDiagnosticsSummary(
        rootProbe.httpStatus === 401
          ? 'Authentication rejected (HTTP 401): Check username or regenerate Nextcloud App Password.'
          : `Root WebDAV endpoint unreachable (${rootProbe.httpStatus ? `HTTP ${rootProbe.httpStatus}` : rootProbe.error}).`
      );
      return;
    }

    // Step 3+: Walk target folder(s) and every subdirectory probed during a scan (BFS, cap 40 subdirectories)
    const diagRoots = startPath
      ? parseScanFolders(startPath, startPathOverride === undefined ? extraScanFolders : [])
      : [''];
    const queue: Array<{ path: string; knownPerms: string | null }> = diagRoots.map((p) => ({ path: p, knownPerms: null }));
    const visited = new Set<string>();
    const maxDirectories = 40;
    let fallbackQueued = false;
    let stepIndex = 3;

    while (queue.length > 0 && visited.size < maxDirectories) {
      if (scanDiagRunIdRef.current !== runId) return;
      const { path: currentPath, knownPerms } = queue.shift()!;
      const normKey = currentPath.toLowerCase();
      if (visited.has(normKey)) continue;
      visited.add(normKey);

      const displayPath = currentPath ? `/${currentPath}` : '/ (Root)';
      const entryId = `dir_${stepIndex}_${normKey || 'root'}`;
      const curStep = stepIndex++;
      setScanDiagnosticsStepText(`Step ${curStep}: Probing ${displayPath}...`);

      pushOrUpdate({
        id: entryId,
        stepTitle: `Step ${curStep} · Probe Directory`,
        targetPath: displayPath,
        httpStatusCode: null,
        permissionsLabel: decodeWebDavPermissions(knownPerms, null),
        rawPermissions: knownPerms,
        detailMessage: 'Sending PROPFIND Depth: 1...',
        latencyMs: 0,
        status: 'RUNNING',
      });

      const dirProbe = await probeWebDavDirectory(currentPath, username, password, '1', useLegacy);
      if (scanDiagRunIdRef.current !== runId) return;

      const rawPerms = dirProbe.selfPermissions || knownPerms;
      const permLabel = decodeWebDavPermissions(rawPerms, dirProbe.httpStatus);
      const isOk = dirProbe.httpStatus === 207 || (dirProbe.httpStatus !== null && dirProbe.httpStatus >= 200 && dirProbe.httpStatus < 300);

      let stepStatus: FolderDiagStatus = 'FAIL';
      let detail = '';
      if (isOk) {
        stepStatus = rawPerms && !rawPerms.toUpperCase().includes('G') ? 'WARN' : 'PASS';
        detail = `Found ${dirProbe.audioCount} audio file(s) and ${dirProbe.subfolders.length} subfolder(s)`;
        for (const sub of dirProbe.subfolders) {
          if (sub.path && !visited.has(sub.path.toLowerCase())) {
            queue.push({ path: sub.path, knownPerms: sub.permissions });
          }
        }
        if (currentPath && dirProbe.audioCount === 0 && dirProbe.subfolders.length === 0 && !fallbackQueued && !visited.has('')) {
          fallbackQueued = true;
          queue.push({ path: '', knownPerms: null });
        }
      } else {
        stepStatus = dirProbe.httpStatus === 404 && currentPath && !fallbackQueued ? 'WARN' : 'FAIL';
        detail =
          dirProbe.httpStatus === 404 && currentPath && !fallbackQueued
            ? `HTTP 404 on ${displayPath} — auto-fallback will probe / (Root)`
            : (dirProbe.error || `PROPFIND failed with HTTP ${dirProbe.httpStatus ?? 'ERR'}`);
        if (currentPath && !fallbackQueued && !visited.has('')) {
          fallbackQueued = true;
          queue.push({ path: '', knownPerms: null });
        }
      }

      pushOrUpdate({
        id: entryId,
        stepTitle: `Step ${curStep} · Probe Directory`,
        targetPath: displayPath,
        httpStatusCode: dirProbe.httpStatus,
        permissionsLabel: permLabel,
        rawPermissions: rawPerms,
        detailMessage: detail,
        latencyMs: dirProbe.latencyMs,
        status: stepStatus,
      });
    }

    const folderEntries = entries.filter((e) => e.id.startsWith('dir_'));
    const passCount = folderEntries.filter((e) => e.status === 'PASS').length;
    const warnCount = folderEntries.filter((e) => e.status === 'WARN').length;
    const failCount = folderEntries.filter((e) => e.status === 'FAIL').length;

    setScanDiagnosticsRunning(false);
    setScanDiagnosticsStepText('Scan Diagnostics Complete');
    setScanDiagnosticsSummary(
      `Probed ${folderEntries.length} directory(s): ${passCount} OK, ${warnCount} warning(s), ${failCount} failed.`
    );
  };

  // Scan Music Folders with multi-root defaults (WoW Radio + Music), deep subfolder support, and additive merge mode
  const scanNextcloudMusic = async (
    user: string,
    pass: string,
    startFolder: string = musicFolder,
    recursive: boolean = true,
    additive: boolean = false
  ) => {
    setIsScanning(true);
    setScanProgress(`Connecting to ${user}@cloud.zentryx.bg...`);
    setPlaybackError(null);

    const authParam = encodeURIComponent(safeBtoa(`${user}:${pass}`));
    const collectedTracks: Track[] = [];
    const visitedFolders = new Set<string>();
    const initialRoots = additive
      ? [startFolder.trim() === '/' ? '' : normalizeFolderPath(startFolder)]
      : startFolder.trim() === '' || startFolder.trim() === '/'
        ? ['']
        : parseScanFolders(startFolder, extraScanFolders);
    const folderQueue: string[] = [...initialRoots];

    try {
      while (folderQueue.length > 0 && visitedFolders.size < 250) {
        const currentFld = folderQueue.shift()!;
        const normKey = currentFld.toLowerCase();
        if (visitedFolders.has(normKey)) continue;
        visitedFolders.add(normKey);

        setScanProgress(`Scanning /${currentFld || 'Root'}...`);

        try {
          const { files, subfolders } = await fetchWebDAVFolder(currentFld, user, pass);

          // Parse audio files into tracks
          for (const f of files) {
            const rawName = f.fileName;
            const cleanFileName = rawName.replace(/\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|m4b|alac|aiff)$/i, '');

            // Intelligent tag parsing from path and filename
            let trackTitle = cleanFileName;
            let trackArtist = 'Unknown Artist';
            let trackAlbum = currentFld ? currentFld.split('/').pop() || 'Nextcloud' : 'Nextcloud';

            // Check if filename is "Artist - Title"
            if (cleanFileName.includes(' - ')) {
              const parts = cleanFileName.split(' - ');
              if (parts.length >= 2) {
                const first = parts[0].replace(/^\d+[\s.-]+/, '').trim();
                const second = parts.slice(1).join(' - ').trim();
                trackArtist = first || trackArtist;
                trackTitle = second || trackTitle;
              }
            } else {
              trackTitle = cleanFileName.replace(/^\d+[\s.-]+/, '').trim();

              const pathParts = currentFld.split('/').filter(Boolean);
              if (pathParts.length >= 2) {
                trackArtist = pathParts[pathParts.length - 2];
                trackAlbum = pathParts[pathParts.length - 1];
              } else if (pathParts.length === 1 && pathParts[0].toLowerCase() !== 'music') {
                trackArtist = pathParts[0];
              }
            }

            const cleanHref = f.href.replace(/^https?:\/\/[^\/]+/, '');
            const streamUrl = `/api/nextcloud${cleanHref.startsWith('/') ? cleanHref : '/' + cleanHref}?auth=${authParam}`;
            const id = f.href;

            collectedTracks.push({
              id,
              title: trackTitle || rawName,
              artist: trackArtist,
              album: trackAlbum,
              duration: '...',
              durationSeconds: 0,
              sizeBytes: f.size,
              sizeFormatted: formatBytes(f.size),
              href: f.href,
              streamUrl,
              fileName: rawName,
              folderPath: currentFld,
              sourceType: 'WEBDAV',
            });
          }

          // Enqueue child folders if recursive mode is active
          if (recursive) {
            for (const sub of subfolders) {
              if (sub.path && !visitedFolders.has(sub.path.toLowerCase())) {
                folderQueue.push(sub.path);
              }
            }
          }
        } catch {
          console.warn(`Folder /${currentFld || 'Root'} could not be read; continuing scan queue.`);
        }
      }

      // If full scan across all configured roots yielded 0 files, fall back to Nextcloud Root /
      if (!additive && collectedTracks.length === 0 && !visitedFolders.has('')) {
        setScanProgress('Scanning Root directory for audio files...');
        const fallbackQueue: string[] = [''];
        while (fallbackQueue.length > 0 && visitedFolders.size < 250) {
          const cur = fallbackQueue.shift()!;
          const key = cur.toLowerCase();
          if (visitedFolders.has(key)) continue;
          visitedFolders.add(key);
          try {
            const { files, subfolders } = await fetchWebDAVFolder(cur, user, pass);
            for (const f of files) {
              const clean = f.fileName.replace(/\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|m4b|alac|aiff)$/i, '');
              const cleanHref = f.href.replace(/^https?:\/\/[^\/]+/, '');
              collectedTracks.push({
                id: f.href,
                title: clean,
                artist: cur ? cur.split('/').pop() || 'Nextcloud Audio' : 'Nextcloud Audio',
                album: cur || 'cloud.zentryx.bg',
                duration: '...',
                durationSeconds: 0,
                sizeBytes: f.size,
                sizeFormatted: formatBytes(f.size),
                href: f.href,
                streamUrl: `/api/nextcloud${cleanHref.startsWith('/') ? cleanHref : '/' + cleanHref}?auth=${authParam}`,
                fileName: f.fileName,
                folderPath: cur,
                sourceType: 'WEBDAV',
              });
            }
            if (recursive) {
              for (const sub of subfolders) {
                const lower = sub.name.toLowerCase();
                if (cur !== '' || ['wow radio', 'music', 'audio', 'songs', 'media', 'radio', 'podcasts'].includes(lower)) {
                  if (sub.path && !visitedFolders.has(sub.path.toLowerCase())) {
                    fallbackQueue.push(sub.path);
                  }
                }
              }
            }
          } catch {
            // root scan fallback handled
          }
        }
      }

      // Deduplicate newly discovered tracks
      const uniqueDiscovered = Array.from(new Map(collectedTracks.map((t) => [t.id, t])).values());

      // Merge additively or replace cloud tracks while preserving local files
      const mergeMode = additive ? 'additive' : 'replace-cloud';
      const mergeResult = mergeDiscoveredTracks(tracks, uniqueDiscovered, mergeMode);

      setTracks(mergeResult.tracks);
      setQueue((prev) => (prev.length === 0 ? mergeResult.tracks : prev));
      localStorage.setItem(
        'zentryx_nc_tracks',
        JSON.stringify(mergeResult.tracks.filter((t) => t.sourceType !== 'LOCAL'))
      );
      localStorage.setItem('zentryx_nc_user', user);
      localStorage.setItem('zentryx_nc_pass', pass);
      if (!additive) {
        localStorage.setItem('zentryx_nc_folder', startFolder);
      }

      if (additive) {
        const displayTarget = `/${normalizeFolderPath(startFolder) || 'Root'}`;
        if (uniqueDiscovered.length > 0) {
          setStatusMessage(
            `Added ${mergeResult.newlyAddedCount} new track(s) from ${displayTarget} (${uniqueDiscovered.length} scanned • ${mergeResult.tracks.length} total in library)`
          );
          if (tracks.length === 0) setCurrentTrackIndex(0);
        } else {
          setStatusMessage(`No audio files found in ${displayTarget}. Your existing ${tracks.length} track(s) remain untouched.`);
        }
      } else if (mergeResult.tracks.length > 0) {
        const rootsLabel = initialRoots.map((r) => `/${r || 'Root'}`).join(', ');
        setStatusMessage(`Loaded ${mergeResult.tracks.length} track(s) from ${rootsLabel}`);
        setCurrentTrackIndex(0);
      } else {
        setStatusMessage(`No audio files found in ${initialRoots.map((r) => `/${r || 'Root'}`).join(', ')}. Browse folders to add your music directory.`);
        openFolderBrowser('');
      }
    } catch (e: any) {
      console.error('Scan error:', e);
      setStatusMessage(`Scan error: ${e.message}`);
      openFolderBrowser('');
    } finally {
      setIsScanning(false);
      setScanProgress('');
      setTimeout(() => setStatusMessage(null), 5000);
    }
  };

  // Manual Folder Browser Handlers
  const openFolderBrowser = async (initialPath?: string) => {
    const targetPath = resolveInitialBrowserPath(initialPath, musicFolder);
    setShowFolderBrowser(true);
    setBrowserCurrentPath(targetPath);
    setManualPathInput(targetPath);
    await loadBrowserFolder(targetPath);
  };

  const loadBrowserFolder = async (folderPath: string) => {
    if (!username || !password) {
      setBrowserError('Please sign in to Nextcloud first.');
      return;
    }
    setIsBrowserLoading(true);
    setBrowserError(null);
    setBrowserCurrentPath(folderPath);
    setManualPathInput(folderPath);

    try {
      const { files, subfolders } = await fetchWebDAVFolder(folderPath, username, password);
      setBrowserAudioFiles(files);
      setBrowserFolders(subfolders);
    } catch (e: any) {
      console.warn('Folder browser load error:', e);
      setBrowserError(`Could not open /${folderPath || 'Root'}: ${e.message}`);
      setBrowserFolders([]);
      setBrowserAudioFiles([]);
    } finally {
      setIsBrowserLoading(false);
    }
  };

  const navigateBrowserUp = () => {
    const clean = browserCurrentPath.replace(/^\/+/, '').replace(/\/+$/, '');
    if (!clean) return;
    const parts = clean.split('/');
    parts.pop();
    const parent = parts.join('/');
    loadBrowserFolder(parent);
  };

  const addAndScanBrowserFolder = async (folderPath: string, recursive: boolean = true) => {
    const clean = normalizeFolderPath(folderPath);
    const labelToStore = clean === '' ? '/' : clean;
    const baseFolders = parseScanFolders(musicFolder);
    const isAlreadyInDefaults = baseFolders.some((f) => f.toLowerCase() === clean.toLowerCase());
    if (!isAlreadyInDefaults) {
      const updated = addFolderToList(extraScanFolders, labelToStore);
      setExtraScanFolders(updated);
      localStorage.setItem('zentryx_nc_extra_folders', JSON.stringify(updated));
    }
    setShowFolderBrowser(false);
    await scanNextcloudMusic(username, password, labelToStore, recursive, true);
  };

  const removeExtraScanFolder = (folderPath: string) => {
    const updated = removeFolderFromList(extraScanFolders, folderPath);
    setExtraScanFolders(updated);
    localStorage.setItem('zentryx_nc_extra_folders', JSON.stringify(updated));
  };

  const selectAndScanBrowserFolder = async (folderPath: string, recursive: boolean = true) => {
    const clean = normalizeFolderPath(folderPath);
    const target = clean === '' ? '/' : clean;
    setMusicFolder(target);
    localStorage.setItem('zentryx_nc_folder', target);
    setShowFolderBrowser(false);
    await scanNextcloudMusic(username, password, target, recursive, false);
  };

  // Nextcloud Login Flow v2 Initiation & Polling
  const startLoginFlow = async () => {
    setIsConnecting(true);
    setLoginError(null);
    setFlowStatus('Contacting cloud.zentryx.bg...');
    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }

    try {
      const res = await fetch('/api/nextcloud/index.php/login/v2', {
        method: 'POST',
      });

      if (!res.ok) {
        throw new Error(`HTTP ${res.status}: ${res.statusText}`);
      }

      const data = await res.json();
      if (!data.login || !data.poll || !data.poll.token) {
        throw new Error('Invalid response from Nextcloud login service');
      }

      setFlowLoginUrl(data.login);
      setFlowToken(data.poll.token);
      setIsFlowPolling(true);
      setFlowStatus('Awaiting your approval on cloud.zentryx.bg...');

      // Start Polling Nextcloud
      const token = data.poll.token;
      pollIntervalRef.current = setInterval(async () => {
        try {
          const body = new URLSearchParams();
          body.append('token', token);

          const pollRes = await fetch('/api/nextcloud/index.php/login/v2/poll', {
            method: 'POST',
            headers: {
              'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: body.toString(),
          });

          if (pollRes.status === 200) {
            const creds = await pollRes.json();
            if (creds && creds.loginName && creds.appPassword) {
              if (pollIntervalRef.current) {
                clearInterval(pollIntervalRef.current);
                pollIntervalRef.current = null;
              }

              const newUsername = creds.loginName;
              const newPassword = creds.appPassword;

              setUsername(newUsername);
              setPassword(newPassword);
              setIsLoggedIn(true);
              setIsFlowPolling(false);
              setShowLoginModal(false);
              setIsConnecting(false);

              localStorage.setItem('zentryx_nc_user', newUsername);
              localStorage.setItem('zentryx_nc_pass', newPassword);
              localStorage.setItem('zentryx_nc_server', serverUrl);
              localStorage.setItem('zentryx_nc_folder', musicFolder);

              setStatusMessage(`Connected as ${newUsername}! Syncing Nextcloud music...`);
              await scanNextcloudMusic(newUsername, newPassword, musicFolder);
            }
          }
        } catch (pollErr) {
          console.warn('Nextcloud poll check:', pollErr);
        }
      }, 1500);
    } catch (err: any) {
      console.error('Login Flow error:', err);
      setLoginError(`1-Click login initialization failed: ${err.message}. You can also use the Manual tab.`);
      setIsFlowPolling(false);
    } finally {
      setIsConnecting(false);
    }
  };

  const stopLoginFlow = () => {
    if (pollIntervalRef.current) {
      clearInterval(pollIntervalRef.current);
      pollIntervalRef.current = null;
    }
    setIsFlowPolling(false);
    setFlowLoginUrl(null);
    setFlowToken(null);
  };

  // Perform Connection Check & Sign-in (Manual App Token / Password)
  const handleManualConnect = async () => {
    if (!username.trim() || !password.trim()) {
      setLoginError('Please enter your Nextcloud username and password/token.');
      return;
    }

    setIsConnecting(true);
    setLoginError(null);

    const cleanUser = username.trim();
    const cleanPass = password.trim();
    const authHeader = `Basic ${safeBtoa(`${cleanUser}:${cleanPass}`)}`;

    try {
      let resolvedUser = cleanUser;
      let authenticated = false;

      // 1. Resolve email to username via Nextcloud OCS User API
      try {
        const ocsRes = await fetch('/api/nextcloud/ocs/v1.php/cloud/user', {
          headers: {
            Authorization: authHeader,
            'OCS-APIREQUEST': 'true',
            Accept: 'application/json',
          },
        });

        if (ocsRes.ok) {
          const ocsData = await ocsRes.json();
          if (ocsData?.ocs?.meta?.statuscode === 100 && ocsData?.ocs?.data?.id) {
            resolvedUser = ocsData.ocs.data.id;
            authenticated = true;
          }
        } else if (ocsRes.status === 401) {
          authenticated = false;
        }
      } catch {
        // Fallback to WebDAV
      }

      // 2. Direct WebDAV verification
      if (!authenticated) {
        const testPath = `/api/nextcloud/remote.php/dav/files/${encodeURIComponent(cleanUser)}/`;
        const davRes = await fetch(testPath, {
          method: 'PROPFIND',
          headers: {
            Authorization: authHeader,
            Depth: '0',
            'Content-Type': 'application/xml; charset=utf-8',
          },
        });

        if (davRes.status === 200 || davRes.status === 207) {
          authenticated = true;
        } else if (davRes.status === 401) {
          setLoginError(
            'Authentication failed: Nextcloud rejected this password. In Nextcloud, WebDAV streaming typically requires an App Password rather than your primary web password. Please switch to the "1-Click Browser Login" tab (recommended), or create an App Password in Nextcloud -> Settings -> Security.'
          );
          setIsConnecting(false);
          return;
        }
      }

      // Successful credentials!
      setUsername(resolvedUser);
      setIsLoggedIn(true);
      setShowLoginModal(false);
      setIsConnecting(false);

      localStorage.setItem('zentryx_nc_user', resolvedUser);
      localStorage.setItem('zentryx_nc_pass', cleanPass);
      localStorage.setItem('zentryx_nc_server', serverUrl.trim());
      localStorage.setItem('zentryx_nc_folder', musicFolder.trim());

      setStatusMessage(`Connected as ${resolvedUser}! Loading files...`);
      await scanNextcloudMusic(resolvedUser, cleanPass, musicFolder.trim());
    } catch (err: any) {
      console.error('Login error:', err);
      // Fallback: save and attempt scan
      setIsLoggedIn(true);
      setShowLoginModal(false);
      setIsConnecting(false);
      localStorage.setItem('zentryx_nc_user', cleanUser);
      localStorage.setItem('zentryx_nc_pass', cleanPass);
      scanNextcloudMusic(cleanUser, cleanPass, musicFolder.trim());
    }
  };

  // Cleanup poll interval on unmount
  useEffect(() => {
    return () => {
      if (pollIntervalRef.current) {
        clearInterval(pollIntervalRef.current);
      }
    };
  }, []);

  const handleSignOut = () => {
    metadataAbortRef.current?.abort();
    checkedMetaIdsRef.current.clear();
    clearAllCachedTrackMetadata();
    localStorage.removeItem('zentryx_nc_user');
    localStorage.removeItem('zentryx_nc_pass');
    localStorage.removeItem('zentryx_nc_tracks');
    setIsLoggedIn(false);
    setUsername('');
    setPassword('');
    setTracks([]);
    if (audioRef.current) {
      audioRef.current.pause();
      audioRef.current.src = '';
    }
    setIsPlaying(false);
    setStatusMessage('Signed out. Nextcloud credentials cleared.');
    setTimeout(() => setStatusMessage(null), 3500);
  };

  // Helper: Generates resilient candidate streaming URLs (Local USB/Device Blob or WebDAV)
  const getTrackCandidateUrls = (track?: Track): string[] => {
    if (!track) return [];
    if (track.sourceType === 'LOCAL' || (track.streamUrl && track.streamUrl.startsWith('blob:'))) {
      return track.streamUrl ? [track.streamUrl] : [];
    }
    const auth = username && password ? safeBtoa(`${username}:${password}`) : '';
    const authQuery = auth ? `auth=${encodeURIComponent(auth)}` : '';

    // Multi-pass decode to obtain clean raw unencoded path segments
    let cleanFolder = (track.folderPath || '').replace(/^\/+/, '').replace(/\/+$/, '');
    try {
      while (cleanFolder.includes('%')) {
        const next = decodeURIComponent(cleanFolder);
        if (next === cleanFolder) break;
        cleanFolder = next;
      }
    } catch {}

    let cleanFileName = track.fileName || '';
    try {
      while (cleanFileName.includes('%')) {
        const next = decodeURIComponent(cleanFileName);
        if (next === cleanFileName) break;
        cleanFileName = next;
      }
    } catch {}

    const rawRelPath = cleanFolder ? `${cleanFolder}/${cleanFileName}` : cleanFileName;
    if (!rawRelPath) return [];

    const urls: string[] = [];

    // Candidate 1: Dedicated resilient Audio Streaming Pipeline (clean Range, keep-alive, inline disposition, CORS)
    const authParam = auth ? `&auth=${encodeURIComponent(auth)}` : '';
    urls.push(`/api/audio-stream?path=${encodeURIComponent(rawRelPath)}${authParam}`);

    // Candidate 2: Nextcloud legacy WebDAV endpoint (lightweight direct streaming for media players)
    const encodedSegments = rawRelPath.split('/').map(encodeURIComponent).join('/');
    urls.push(`/api/nextcloud/remote.php/webdav/${encodedSegments}${authQuery ? '?' + authQuery : ''}`);

    // Candidate 3: Canonical WebDAV DAV files URL
    if (username) {
      urls.push(`/api/nextcloud/remote.php/dav/files/${encodeURIComponent(username)}/${encodedSegments}${authQuery ? '?' + authQuery : ''}`);
    }

    // Candidate 4: Saved raw href from scan
    if (track.href) {
      const rawClean = track.href.replace(/^https?:\/\/[^\/]+/, '');
      const rawUrl = `/api/nextcloud${rawClean.startsWith('/') ? rawClean : '/' + rawClean}${authQuery ? '?' + authQuery : ''}`;
      if (!urls.includes(rawUrl)) {
        urls.push(rawUrl);
      }
    }

    return urls;
  };

  const getTrackStreamUrl = (track?: Track): string => {
    const list = getTrackCandidateUrls(track);
    return list[0] || '';
  };

  // Multi-chunk fetcher ensuring no single request exceeds Cloud Run GFE 32 MiB limit
  const fetchTrackBufferChunked = async (
    fetchUrl: string,
    authHdr: string
  ): Promise<{ buffer: ArrayBuffer; status: number; statusText: string }> => {
    const CHUNK_SIZE = 8 * 1024 * 1024; // 8 MiB per slice

    const firstRes = await fetch(fetchUrl, {
      cache: 'no-store',
      headers: {
        Authorization: authHdr,
        Range: `bytes=0-${CHUNK_SIZE - 1}`,
      },
    });

    if (!firstRes.ok && firstRes.status !== 206) {
      let errBody = '';
      try { errBody = await firstRes.text(); } catch {}
      const hMap: Record<string, string> = {};
      firstRes.headers.forEach((v, k) => { hMap[k] = v; });
      const errorObj: any = new Error(`HTTP ${firstRes.status}: ${firstRes.statusText}`);
      errorObj.status = firstRes.status;
      errorObj.statusText = firstRes.statusText;
      errorObj.headers = hMap;
      errorObj.body = errBody;
      throw errorObj;
    }

    const firstChunk = await firstRes.arrayBuffer();

    if (firstRes.status === 200) {
      return { buffer: firstChunk, status: 200, statusText: firstRes.statusText };
    }

    const contentRange = firstRes.headers.get('content-range') || '';
    const match = contentRange.match(/\/(\d+)$/);
    const totalSize = match ? parseInt(match[1], 10) : 0;

    if (!totalSize || totalSize <= firstChunk.byteLength) {
      return { buffer: firstChunk, status: firstRes.status, statusText: firstRes.statusText };
    }

    const complete = new Uint8Array(totalSize);
    complete.set(new Uint8Array(firstChunk), 0);
    let currentBytes = firstChunk.byteLength;

    while (currentBytes < totalSize) {
      const end = Math.min(currentBytes + CHUNK_SIZE - 1, totalSize - 1);
      setStatusMessage(`Buffering audio (${Math.round((currentBytes / totalSize) * 100)}%)...`);
      const chunkRes = await fetch(fetchUrl, {
        cache: 'no-store',
        headers: {
          Authorization: authHdr,
          Range: `bytes=${currentBytes}-${end}`,
        },
      });

      if (!chunkRes.ok && chunkRes.status !== 206) {
        throw new Error(`Chunk ${currentBytes}-${end} failed with HTTP ${chunkRes.status}`);
      }

      const chunkBuf = await chunkRes.arrayBuffer();
      complete.set(new Uint8Array(chunkBuf), currentBytes);
      currentBytes += chunkBuf.byteLength;
    }

    return { buffer: complete.buffer, status: 206, statusText: 'Partial Content (Multi-chunk Joined)' };
  };

  // Haptic touch feedback
  const triggerHaptic = () => {
    try {
      if (typeof navigator !== 'undefined' && 'vibrate' in navigator) {
        navigator.vibrate(20);
      }
    } catch {}
  };

  // Fullscreen Kiosk toggle
  const toggleFullscreen = () => {
    triggerHaptic();
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen?.().then(() => setIsFullscreen(true)).catch(() => {});
    } else {
      document.exitFullscreen?.().then(() => setIsFullscreen(false)).catch(() => {});
    }
  };

  // Seek helper
  const seekTo = (newPos: number) => {
    const clamped = Math.max(0, Math.min(newPos, durationSeconds || 999999));
    setPositionSeconds(clamped);
    if (audioRef.current && !isWebAudioActive) {
      audioRef.current.currentTime = clamped;
    } else if (isWebAudioActive && webAudioBufferRef.current && webAudioCtxRef.current) {
      try { webAudioSourceRef.current?.stop(); } catch {}
      const ctx = webAudioCtxRef.current;
      const src = ctx.createBufferSource();
      src.buffer = webAudioBufferRef.current;
      src.connect(webAudioGainRef.current || ctx.destination);
      src.start(0, clamped);
      webAudioStartTimeRef.current = ctx.currentTime - clamped;
      webAudioSourceRef.current = src;
    }
  };

  // Schedule debounced non-blocking localStorage mirror of enriched remote tracks
  const schedulePersistTracks = (updatedTracks: Track[]) => {
    if (persistTracksTimerRef.current) clearTimeout(persistTracksTimerRef.current);
    persistTracksTimerRef.current = setTimeout(() => {
      try {
        const remoteOnly = updatedTracks.filter((t) => t.sourceType !== 'LOCAL');
        localStorage.setItem('zentryx_nc_tracks', JSON.stringify(remoteOnly));
      } catch {}
    }, 800);
  };

  // Apply parsed or cached metadata immutably into React state without interrupting audio playback
  const applyEnrichedMetadata = (
    trackId: string,
    parsed: any,
    durationOverrideSec?: number,
    coverBlob?: Blob
  ) => {
    if (coverBlob && !coverArtUrls[trackId]) {
      const coverUrl = URL.createObjectURL(coverBlob);
      setCoverArtUrls((prev) => (prev[trackId] ? prev : { ...prev, [trackId]: coverUrl }));
    }

    setTracks((prev) => {
      let changed = false;
      const next = prev.map((t) => {
        if (t.id !== trackId) return t;
        changed = true;
        return mergeTrackMetadata(t, parsed, durationOverrideSec);
      });
      if (changed) schedulePersistTracks(next);
      return changed ? next : prev;
    });

    setQueue((prev) => {
      let changed = false;
      const next = prev.map((t) => {
        if (t.id !== trackId) return t;
        changed = true;
        return mergeTrackMetadata(t, parsed, durationOverrideSec);
      });
      return changed ? next : prev;
    });
  };

  // Non-blocking track metadata hydrator (reuses existing buffer if present, else two-stage 64KB Range peek)
  const hydrateTrackMetadata = async (
    target: Track,
    signal?: AbortSignal,
    existingBuffer?: ArrayBuffer
  ) => {
    if (!target) return;

    // 1. Check in-memory and IndexedDB cache first unless caller passed a fresh audio buffer
    if (!existingBuffer) {
      const cached = await getCachedTrackMetadata(target.id);
      if (!needsMetadataFetch(target, cached)) {
        checkedMetaIdsRef.current.add(target.id);
        if (cached) {
          applyEnrichedMetadata(target.id, cached, undefined, cached.coverBlob);
        }
        return;
      }
    }

    checkedMetaIdsRef.current.add(target.id);

    try {
      let parsed = existingBuffer
        ? parseAudioMetadata(existingBuffer, target.fileName)
        : null;

      if (!existingBuffer) {
        const candidates = getTrackCandidateUrls(target);
        if (candidates.length === 0) return;
        const authHeader = username && password ? `Basic ${safeBtoa(`${username}:${password}`)}` : '';
        parsed = await extractTrackMetadataFromUrl({
          trackId: target.id,
          url: candidates[0],
          authHeader,
          fileName: target.fileName,
          sizeBytes: target.sizeBytes,
          signal,
        });
      }

      if (signal?.aborted) return;

      let coverBlob: Blob | undefined;
      if (parsed?.coverImage) {
        coverBlob = new Blob([parsed.coverImage.data as any], {
          type: parsed.coverImage.mimeType,
        });
      }

      const yearNum =
        parsed?.year && /^\d{4}/.test(parsed.year) ? parseInt(parsed.year.slice(0, 4), 10) : undefined;
      const resolvedDuration = parsed?.durationSeconds || target.durationSeconds || undefined;
      const computedBitrate =
        parsed?.bitrateKbps || estimateBitrateKbps(target.sizeBytes, resolvedDuration);

      await saveCachedTrackMetadata({
        id: target.id,
        title: parsed?.title || undefined,
        artist: parsed?.artist || undefined,
        album: parsed?.album || undefined,
        year: yearNum,
        genre: parsed?.genre || undefined,
        codec: parsed?.codec || undefined,
        bitrateKbps: computedBitrate,
        sampleRateHz: parsed?.sampleRateHz || undefined,
        replayGainDb: parsed?.replayGainDb,
        durationSeconds: resolvedDuration,
        coverBlob,
        sizeBytes: target.sizeBytes,
        hasCheckedTag: true,
        updatedAt: Date.now(),
      });

      if (parsed || computedBitrate) {
        applyEnrichedMetadata(
          target.id,
          { ...(parsed || {}), bitrateKbps: computedBitrate },
          undefined,
          coverBlob
        );
      }
    } catch {
      // Aborted skips or transient offline errors are intentionally non-fatal
    }
  };

  // Prune old non-favorite/non-queued offline blobs when Smart Rolling Cache cap is exceeded
  const enforceOfflineCacheCap = async (favSet: Set<string>, capMb: number) => {
    if (!smartRollingCache) return;
    try {
      const records = await getAllOfflineTracks();
      const protectedQueueIds = new Set<string>();
      for (let i = 0; i < 3; i++) {
        const qTrack = queue[currentTrackIndex + i];
        if (qTrack) protectedQueueIds.add(qTrack.id);
      }
      const evictions = selectOfflineCacheEvictions(
        records.map((r) => ({
          id: r.id,
          sizeBytes: r.blob ? r.blob.size : 0,
          downloadedAt: r.downloadedAt || 0,
        })),
        favSet,
        protectedQueueIds,
        capMb * 1024 * 1024
      );
      if (evictions.length > 0) {
        for (const id of evictions) {
          await deleteOfflineTrack(id);
        }
        await refreshOfflineStorage();
      }
    } catch {}
  };

  // Offline Sync Manager
  const downloadTrackForOffline = async (target: Track) => {
    if (!target) return;
    triggerHaptic();
    setDownloadingId(target.id);
    setStatusMessage(`Downloading "${target.title}" for offline roadtrip...`);
    try {
      const candidates = getTrackCandidateUrls(target);
      const targetUrl = candidates[0];
      const authHeader = username && password ? `Basic ${safeBtoa(`${username}:${password}`)}` : '';

      const { buffer } = await fetchTrackBufferChunked(targetUrl, authHeader);
      const ext = (target.fileName || '').split('.').pop()?.toLowerCase();
      const mimeType = ext === 'flac' ? 'audio/flac' : 'audio/mpeg';
      const audioBlob = new Blob([buffer], { type: mimeType });

      // Multi-format tag + artwork extraction at zero extra network cost
      let coverBlob: Blob | undefined;
      try {
        const parsed = parseAudioMetadata(buffer, target.fileName);
        if (parsed?.coverImage) {
          coverBlob = new Blob([parsed.coverImage.data as any], { type: parsed.coverImage.mimeType });
        }
        await hydrateTrackMetadata(target, undefined, buffer);
      } catch {}

      await saveOfflineTrack(target, audioBlob, mimeType, coverBlob);
      let savedFolderMsg = '';
      if (offlineFolderHandle) {
        try {
          const diskRes = await saveTrackToSelectedFolder(
            offlineFolderHandle,
            target.fileName,
            target.artist,
            target.title,
            audioBlob
          );
          if (diskRes.savedToDisk) {
            savedFolderMsg = ` & folder "${offlineFolderName || 'Selected'}"`;
          }
        } catch {}
      }
      setOfflineTrackIds(prev => new Set(prev).add(target.id));
      setDownloadedTracks(prev => new Set(prev).add(target.id));
      setStatusMessage(`"${target.title}" saved to vehicle offline storage${savedFolderMsg}!`);
      await enforceOfflineCacheCap(favorites, offlineCacheCapMb);
    } catch (err: any) {
      setStatusMessage(`Offline sync notice: ${err.message}`);
    } finally {
      setDownloadingId(null);
      setTimeout(() => setStatusMessage(null), 3500);
      refreshOfflineStorage();
    }
  };

  // Tunnel & Deadzone Guard: Smart Next-Track Pre-caching (Gapless Playback & Tunnel resilience)
  const prefetchNextTrackChunk = async () => {
    if (queue.length === 0) return;
    const tracksToPrefetch = deadzoneGuardMode === 'aggressive' ? 3 : 1;
    let lookaheadIndex = currentTrackIndex;

    for (let i = 0; i < tracksToPrefetch; i++) {
      const nextIdx = resolveNextIndex(queue.length, lookaheadIndex, shuffle, repeatMode);
      if (nextIdx === -1 || nextIdx === currentTrackIndex) break;
      lookaheadIndex = nextIdx;
      const nextTrack = queue[nextIdx];
      if (!nextTrack) continue;

      if (i === 0 && prefetchedTrackIdRef.current === nextTrack.id) {
        if (tracksToPrefetch === 1) return;
        continue;
      }
      if (i === 0) {
        prefetchedTrackIdRef.current = nextTrack.id;
      }

      try {
        const isOff = await getOfflineTrack(nextTrack.id);
        if (isOff) continue;

        const candidateUrls = getTrackCandidateUrls(nextTrack);
        const targetUrl = candidateUrls[0];
        const authHeader = username && password ? `Basic ${safeBtoa(`${username}:${password}`)}` : '';
        const rangeHeader = deadzoneGuardMode === 'aggressive' ? 'bytes=0-16777215' : 'bytes=0-8388607';

        const res = await fetch(targetUrl, {
          headers: {
            Authorization: authHeader,
            Range: rangeHeader,
          },
          cache: 'no-store',
        });

        if (res.ok || res.status === 206) {
          const buf = await res.arrayBuffer();
          const ext = (nextTrack.fileName || '').split('.').pop()?.toLowerCase();
          const mimeType = ext === 'flac' ? 'audio/flac' : 'audio/mpeg';
          const blob = new Blob([buf], { type: mimeType });
          if (i === 0) {
            prefetchBlobUrlRef.current = { id: nextTrack.id, url: URL.createObjectURL(blob) };
          }
          if (deadzoneGuardMode === 'aggressive') {
            await saveOfflineTrack(nextTrack, blob, mimeType);
          }

          // Hydrate next track tags & artwork from already-downloaded prefetch chunk (zero extra bandwidth)
          hydrateTrackMetadata(nextTrack, undefined, buf);
        }
      } catch {}
    }
  };

  // Playlist handlers
  const handleCreatePlaylist = async (name: string, description?: string) => {
    triggerHaptic();
    const newPl = createPlaylist(name, description);
    await savePlaylistRecord(newPl);
    setPlaylists(prev => [...prev, newPl]);
    setStatusMessage(`Playlist "${newPl.name}" created!`);
    setTimeout(() => setStatusMessage(null), 3000);
  };

  const handleDeletePlaylist = async (id: string) => {
    triggerHaptic();
    await deletePlaylistRecord(id);
    setPlaylists(prev => prev.filter(p => p.id !== id));
    if (selectedPlaylist?.id === id) {
      setSelectedPlaylist(null);
    }
  };

  const handleAddTrackToPlaylist = async (playlistId: string, trackId: string) => {
    triggerHaptic();
    const pl = playlists.find(p => p.id === playlistId);
    if (!pl) return;
    const updated = addTrackToPlaylist(pl, trackId);
    await savePlaylistRecord(updated);
    setPlaylists(prev => prev.map(p => p.id === playlistId ? updated : p));
  };

  const handlePlayPlaylist = (pl: Playlist) => {
    triggerHaptic();
    const plTracks = pl.trackIds
      .map(id => tracks.find(t => t.id === id))
      .filter(Boolean) as Track[];
    if (plTracks.length === 0) {
      setStatusMessage(`Playlist "${pl.name}" has no tracks yet.`);
      setTimeout(() => setStatusMessage(null), 3000);
      return;
    }
    setQueue(plTracks);
    setCurrentTrackIndex(0);
    setPositionSeconds(0);
    startAudioPlayback(plTracks[0]);
    setActiveTab('player');
  };

  // Export .m3u8 playlist download
  const handleExportPlaylistM3u8 = (pl: Playlist) => {
    triggerHaptic();
    const plTracks = resolveTracksForPlaylist(pl, tracks);
    const content = serializeM3u8(pl.name, plTracks);
    const blob = new Blob([content], { type: 'application/x-mpegurl;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = serializePlaylistFileName(pl.name);
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    setStatusMessage(`Exported "${pl.name}" (${plTracks.length} tracks) as .m3u8`);
    setTimeout(() => setStatusMessage(null), 3000);
  };

  // Import .m3u8 playlist file from disk
  const handleImportM3u8File = async (file: File) => {
    try {
      const text = await file.text();
      const baseName = file.name.replace(/\.(m3u8?|txt)$/i, '') || 'Imported Playlist';
      const resolvedName = resolvePlaylistNameFromM3u8(text, baseName);
      const paths = parseM3u8Paths(text);
      const matched = resolveMatchedTracksForPaths(paths, tracks);
      const created: Playlist = {
        ...createPlaylist(resolvedName, `Imported (${matched.length} matched)`),
        trackIds: matched.map((t) => t.id),
      };
      await savePlaylistRecord(created);
      setPlaylists((prev) => [...prev, created]);
      setStatusMessage(`Imported playlist "${resolvedName}" (${matched.length} tracks matched)`);
      setTimeout(() => setStatusMessage(null), 3500);
    } catch (e: any) {
      setStatusMessage(`Could not import .m3u8: ${e?.message || 'Parse error'}`);
      setTimeout(() => setStatusMessage(null), 3500);
    }
  };

  // Two-way Nextcloud .m3u8 Playlist Sync (<musicFolder>/Playlists)
  const handleSyncPlaylistsCloud = async () => {
    if (!isLoggedIn || !username || !password) {
      setStatusMessage('Sign in to Nextcloud first to sync .m3u8 playlists.');
      setTimeout(() => setStatusMessage(null), 3000);
      return;
    }
    if (isSyncingPlaylists) return;
    setIsSyncingPlaylists(true);
    try {
      const basicAuth = safeBtoa(`${username}:${password}`);
      const primaryFolder = parseScanFolders(musicFolder).find((f) => f.toLowerCase() === 'music') ?? parseScanFolders(musicFolder)[0] ?? 'Music';
      const cleanRoot = normalizeFolderPath(primaryFolder);
      const playlistRelDir = cleanRoot ? `${cleanRoot}/Playlists` : 'Playlists';
      const dirSegs = playlistRelDir.split('/').filter(Boolean).map(encodeURIComponent).join('/');
      const davDirUrl = `/api/nextcloud/remote.php/dav/files/${encodeURIComponent(username)}/${dirSegs}/`;

      // Ensure /Playlists collection exists (MKCOL returns 201 Created or 405 Method Not Allowed if exists)
      await fetch(davDirUrl, {
        method: 'MKCOL',
        headers: { Authorization: `Basic ${basicAuth}` },
      }).catch(() => {});

      let pushed = 0;
      for (const pl of playlists) {
        const plTracks = resolveTracksForPlaylist(pl, tracks);
        const m3uText = serializeM3u8(pl.name, plTracks);
        const fileName = encodeURIComponent(serializePlaylistFileName(pl.name));
        const putRes = await fetch(`${davDirUrl}${fileName}`, {
          method: 'PUT',
          headers: {
            Authorization: `Basic ${basicAuth}`,
            'Content-Type': 'audio/x-mpegurl; charset=utf-8',
          },
          body: m3uText,
        });
        if (putRes.ok) pushed++;
      }

      // Pull any remote .m3u8 playlists from Nextcloud
      let pulled = 0;
      const propRes = await fetch(davDirUrl, {
        method: 'PROPFIND',
        headers: {
          Authorization: `Basic ${basicAuth}`,
          Depth: '1',
        },
      });
      if (propRes.ok) {
        const xmlText = await propRes.text();
        const xmlDoc = new DOMParser().parseFromString(xmlText, 'application/xml');
        const responses = Array.from(xmlDoc.getElementsByTagNameNS('*', 'response'));
        const existingLower = new Set(playlists.map((p) => p.name.trim().toLowerCase()));
        const importedList: Playlist[] = [];

        for (const r of responses) {
          const href = r.getElementsByTagNameNS('*', 'href')[0]?.textContent || '';
          const decoded = decodeURIComponent(href);
          const nameFromHref = decoded.replace(/\/+$/, '').split('/').pop() || '';
          if (!/\.(m3u8?)$/i.test(nameFromHref)) continue;

          const getUrl = `/api/nextcloud${href.startsWith('/') ? href : '/' + href}`;
          const fileRes = await fetch(getUrl, {
            headers: { Authorization: `Basic ${basicAuth}` },
          });
          if (!fileRes.ok) continue;
          const m3uBody = await fileRes.text();
          const plName = resolvePlaylistNameFromM3u8(
            m3uBody,
            nameFromHref.replace(/\.(m3u8?)$/i, '')
          );
          if (existingLower.has(plName.trim().toLowerCase())) continue;

          const matched = resolveMatchedTracksForPaths(parseM3u8Paths(m3uBody), tracks);
          const created: Playlist = {
            ...createPlaylist(plName, 'Synced from Nextcloud .m3u8'),
            trackIds: matched.map((t) => t.id),
          };
          await savePlaylistRecord(created);
          importedList.push(created);
          existingLower.add(plName.trim().toLowerCase());
          pulled++;
        }

        if (importedList.length > 0) {
          setPlaylists((prev) => [...prev, ...importedList]);
        }
      }

      setStatusMessage(`Nextcloud .m3u8 sync complete: ${pushed} pushed, ${pulled} imported.`);
    } catch (e: any) {
      setStatusMessage(`Playlist sync failed: ${e?.message || 'Network error'}`);
    } finally {
      setIsSyncingPlaylists(false);
      setTimeout(() => setStatusMessage(null), 4000);
    }
  };

  // Local USB / MicroSD / Device Folder Scanner (File System Access API + FileList fallback)
  const processScannedLocalFiles = async (entries: Array<{ file: File; relPath: string }>, rootName: string) => {
    const audioRegex = /\.(mp3|flac|wav|m4a|aac|ogg|opus|wma|alac|aiff)$/i;
    const valid = entries.filter((e) => e.file.type.startsWith('audio/') || audioRegex.test(e.file.name));
    if (valid.length === 0) {
      setStatusMessage(`No supported audio files found in "${rootName}".`);
      setTimeout(() => setStatusMessage(null), 3500);
      return;
    }

    setStatusMessage(`Scanning ${valid.length} local USB/device audio files...`);
    const localTracks: Track[] = [];
    const newCoverUrls: Record<string, string> = {};

    for (const { file, relPath } of valid) {
      const cleanRel = relPath.replace(/^\/+/, '');
      const folderPart = cleanRel.includes('/') ? cleanRel.slice(0, cleanRel.lastIndexOf('/')) : rootName;
      const trackId = `local_${safeBtoa(`${cleanRel}:${file.size}`).replace(/[^a-zA-Z0-9_-]/g, '').slice(0, 48)}`;
      const objectUrl = URL.createObjectURL(file);

      let parsedMeta: any = null;
      try {
        const headerBuf = await file.slice(0, 131072).arrayBuffer();
        parsedMeta = parseAudioMetadata(headerBuf, file.name);
        if (parsedMeta?.coverImage) {
          const coverBlob = new Blob([parsedMeta.coverImage.data as any], { type: parsedMeta.coverImage.mimeType });
          newCoverUrls[trackId] = URL.createObjectURL(coverBlob);
        }
      } catch {}

      const fallbackTitle = file.name.replace(/\.[^/.]+$/, '').replace(/[_]+/g, ' ');
      const durSec = parsedMeta?.durationSeconds || 0;
      const yearNum =
        parsedMeta?.year && /^\d{4}/.test(parsedMeta.year)
          ? parseInt(parsedMeta.year.slice(0, 4), 10)
          : undefined;

      localTracks.push({
        id: trackId,
        title: parsedMeta?.title || fallbackTitle,
        artist: parsedMeta?.artist || 'Local Audio',
        album: parsedMeta?.album || rootName || 'USB Storage',
        year: yearNum,
        genre: parsedMeta?.genre,
        codec: parsedMeta?.codec || file.name.split('.').pop()?.toUpperCase(),
        sampleRateHz: parsedMeta?.sampleRateHz,
        replayGainDb: parsedMeta?.replayGainDb,
        metadataHydrated: Boolean(parsedMeta),
        duration: durSec > 0 ? formatTime(durSec) : 'Local',
        durationSeconds: durSec,
        sizeBytes: file.size,
        sizeFormatted: formatBytes(file.size),
        href: cleanRel,
        streamUrl: objectUrl,
        fileName: file.name,
        folderPath: folderPart.startsWith('Local/') ? folderPart : `Local/${folderPart}`,
        contentType: file.type || 'audio/mpeg',
        sourceType: 'LOCAL',
      });
    }

    if (Object.keys(newCoverUrls).length > 0) {
      setCoverArtUrls((prev) => ({ ...prev, ...newCoverUrls }));
    }

    setTracks((prev) => {
      const merged = new Map(prev.map((t) => [t.id, t]));
      for (const lt of localTracks) {
        merged.set(lt.id, lt);
      }
      return Array.from(merged.values());
    });
    setQueue((prev) => (prev.length === 0 ? localTracks : prev));
    setStatusMessage(`Loaded ${localTracks.length} local tracks from "${rootName}"!`);
    setTimeout(() => setStatusMessage(null), 4000);
  };

  const handleScanLocalFolder = async () => {
    const picker = (window as any).showDirectoryPicker;
    if (typeof picker !== 'function') {
      setStatusMessage('Use the "Select Files" button to choose local USB/device folders in this browser.');
      setTimeout(() => setStatusMessage(null), 3500);
      return;
    }
    try {
      const rootHandle = await picker({ mode: 'read' });
      const collected: Array<{ file: File; relPath: string }> = [];
      const walk = async (dirHandle: any, prefix: string) => {
        for await (const [name, handle] of dirHandle.entries()) {
          if (handle.kind === 'file') {
            const file = await handle.getFile();
            collected.push({ file, relPath: `${prefix}/${name}` });
          } else if (handle.kind === 'directory' && !name.startsWith('.')) {
            await walk(handle, `${prefix}/${name}`);
          }
        }
      };
      await walk(rootHandle, rootHandle.name || 'USB');
      await processScannedLocalFiles(collected, rootHandle.name || 'USB');
    } catch (err: any) {
      if (err?.name !== 'AbortError') {
        setStatusMessage(`Local scan failed: ${err?.message || 'Permission denied'}`);
        setTimeout(() => setStatusMessage(null), 3500);
      }
    }
  };

  const handleScanLocalInputFiles = async (fileList: FileList | null) => {
    if (!fileList || fileList.length === 0) return;
    const files = Array.from(fileList);
    const firstRel = (files[0] as any).webkitRelativePath || files[0].name;
    const rootName = firstRel.includes('/') ? firstRel.split('/')[0] : 'Local Audio';
    const entries = files.map((file) => ({
      file,
      relPath: (file as any).webkitRelativePath || `${rootName}/${file.name}`,
    }));
    await processScannedLocalFiles(entries, rootName);
  };

  // Batch Album Offline Pin & Pin All Favorites Handlers
  const runBatchDownloadTracks = async (targets: Track[], label: string) => {
    if (targets.length === 0 || isBatchSyncing) return;
    const pending = targets.filter((t) => t.sourceType !== 'LOCAL' && !offlineTrackIds.has(t.id));
    if (pending.length === 0) {
      setStatusMessage(`All tracks in ${label} are already saved offline!`);
      setTimeout(() => setStatusMessage(null), 3000);
      return;
    }
    setIsBatchSyncing(true);
    for (let i = 0; i < pending.length; i++) {
      const t = pending[i];
      setBatchSyncProgress({ current: i + 1, total: pending.length, trackTitle: t.title });
      try {
        await downloadTrackForOffline(t);
      } catch {}
    }
    setIsBatchSyncing(false);
    setBatchSyncProgress(null);
    await refreshOfflineStorage();
    setStatusMessage(`Pinned ${pending.length} tracks from ${label} for offline driving!`);
    setTimeout(() => setStatusMessage(null), 3500);
  };

  const handleDownloadAlbum = (albumName: string, albumTracks: Track[]) => {
    triggerHaptic();
    runBatchDownloadTracks(albumTracks, `album "${albumName}"`);
  };

  const handlePinAllFavorites = () => {
    triggerHaptic();
    const favTracks = tracks.filter((t) => favorites.has(t.id));
    if (favTracks.length === 0) {
      setStatusMessage('Mark some tracks with ❤️ Favorite first.');
      setTimeout(() => setStatusMessage(null), 3000);
      return;
    }
    runBatchDownloadTracks(favTracks, 'Favorites');
  };

  const handlePinFolderOffline = (folderPrefix: string, explicitTracks?: Track[], customLabel?: string) => {
    triggerHaptic();
    const cleanPrefix = folderPrefix.replace(/^\/+/, '').replace(/\/+$/, '');
    const matching =
      explicitTracks && explicitTracks.length > 0
        ? explicitTracks
        : tracks.filter((t) => {
            const fp = (t.folderPath || '').replace(/^\/+/, '').replace(/\/+$/, '');
            if (!cleanPrefix) return true;
            return fp === cleanPrefix || fp.startsWith(`${cleanPrefix}/`);
          });
    if (matching.length === 0) {
      setStatusMessage(
        cleanPrefix
          ? `No scanned tracks found under /${cleanPrefix}. Scan the folder first.`
          : 'No tracks available to pin offline.'
      );
      setTimeout(() => setStatusMessage(null), 3500);
      return;
    }
    runBatchDownloadTracks(matching, customLabel || (cleanPrefix ? `folder "/${cleanPrefix}"` : 'Root Folder'));
  };

  // Portable Tune Backup (.json Export/Import & Nextcloud /ZentryxBackup/zentryx-tune.json)
  const applyRestoredBackup = (parsed: ReturnType<typeof parseSettingsBackupJson>) => {
    if (!parsed) {
      setStatusMessage('Invalid Zentryx tune backup JSON.');
      setTimeout(() => setStatusMessage(null), 3500);
      return;
    }
    if (parsed.accentKey) setAccentKey(parsed.accentKey);
    if (parsed.cockpitThemeMode) setCockpitThemeMode(parsed.cockpitThemeMode);
    if (parsed.nightBrightness !== undefined) setNightBrightness(parsed.nightBrightness);
    if (parsed.warmAmberFilter !== undefined) setWarmAmberFilter(parsed.warmAmberFilter);
    if (parsed.replayGainEnabled !== undefined) setReplayGainEnabled(parsed.replayGainEnabled);
    if (parsed.crossfadeSeconds !== undefined) setCrossfadeSeconds(parsed.crossfadeSeconds);
    if (parsed.scvSensitivity) setScvSensitivity(parsed.scvSensitivity);
    if (parsed.autoResumeOnBoot !== undefined) setAutoResumeOnBoot(parsed.autoResumeOnBoot);
    if (parsed.autoResumeSoftRamp !== undefined) setAutoResumeSoftRamp(parsed.autoResumeSoftRamp);
    if (parsed.autoPlayOnBluetooth !== undefined) setAutoPlayOnBluetooth(parsed.autoPlayOnBluetooth);
    if (parsed.duckOnReverseGear !== undefined) setDuckOnReverseGear(parsed.duckOnReverseGear);
    if (parsed.globalAudioSessionAttach !== undefined) setGlobalAudioSessionAttach(parsed.globalAudioSessionAttach);
    if (parsed.playbackRate !== undefined) setPlaybackRate(parsed.playbackRate);
    if (parsed.enableSwipeGestures !== undefined) setEnableSwipeGestures(parsed.enableSwipeGestures);
    if (parsed.idleScreensaverSeconds !== undefined) setIdleScreensaverSeconds(parsed.idleScreensaverSeconds);
    if (parsed.driverSafeMode !== undefined) setDriverSafeMode(parsed.driverSafeMode);
    if (parsed.smartRollingCache !== undefined) setSmartRollingCache(parsed.smartRollingCache);
    if (parsed.offlineCacheCapMb !== undefined) setOfflineCacheCapMb(parsed.offlineCacheCapMb);
    if (parsed.deadzoneGuardMode) setDeadzoneGuardMode(parsed.deadzoneGuardMode);
    if (parsed.dualPaneCockpit !== undefined) setDualPaneCockpit(parsed.dualPaneCockpit);
    if (parsed.driveModeAutoSpeedKmH !== undefined) setDriveModeAutoSpeedKmH(parsed.driveModeAutoSpeedKmH);
    if (parsed.dspSettings) setDspSettings(parsed.dspSettings);
    if (parsed.customDspPresets) setCustomDspPresets(parsed.customDspPresets);
    if (parsed.swcBindings) setSwcBindings(parsed.swcBindings);
    if (parsed.swcLongPressBindings) setSwcLongPressBindings(parsed.swcLongPressBindings);
    if (parsed.driverProfiles) setDriverProfiles(parsed.driverProfiles);
    if (parsed.activeDriverProfileId) setActiveDriverProfileId(parsed.activeDriverProfileId);
    setStatusMessage('DSP curves, EQ presets & cockpit tune restored!');
    setTimeout(() => setStatusMessage(null), 3500);
  };

  const handleSelectDriverProfile = (profileId: string) => {
    const profile = driverProfiles.find((p) => p.id === profileId);
    if (!profile) return;
    triggerHaptic();
    setActiveDriverProfileId(profile.id);
    setAccentKey(profile.accentKey);
    setCockpitThemeMode(profile.cockpitThemeMode);
    setNightBrightness(profile.nightBrightness);
    setWarmAmberFilter(profile.warmAmberFilter);
    const allPresets = [...FACTORY_DSP_PRESETS, ...customDspPresets];
    const preset = allPresets.find((p) => p.id === profile.selectedPresetId);
    setDspSettings((prev) => {
      const base = preset ? applyPresetToDsp(prev, preset) : prev;
      return {
        ...base,
        listeningStage: profile.listeningStage,
        leftDelayMs: profile.leftDelayMs,
        rightDelayMs: profile.rightDelayMs,
        balance: profile.balance,
        fader: profile.fader,
        surroundMode: profile.surroundMode,
        surroundIntensity: profile.surroundIntensity,
        subTrimDb: profile.subTrimDb,
      };
    });
    setStatusMessage(`Loaded Driver Profile: ${profile.name}`);
    setTimeout(() => setStatusMessage(null), 2500);
  };

  const handleSaveCurrentToDriverProfile = (profileId: string) => {
    triggerHaptic();
    setDriverProfiles((prev) =>
      prev.map((p) =>
        p.id === profileId
          ? {
              ...p,
              accentKey,
              cockpitThemeMode,
              nightBrightness,
              warmAmberFilter,
              listeningStage: dspSettings.listeningStage,
              leftDelayMs: dspSettings.leftDelayMs,
              rightDelayMs: dspSettings.rightDelayMs,
              balance: dspSettings.balance,
              fader: dspSettings.fader,
              surroundMode: dspSettings.surroundMode,
              surroundIntensity: dspSettings.surroundIntensity,
              selectedPresetId: dspSettings.selectedPresetId,
              subTrimDb: dspSettings.subTrimDb,
            }
          : p
      )
    );
    const targetName = driverProfiles.find((p) => p.id === profileId)?.name || 'Active';
    setStatusMessage(`Saved current cockpit & DSP tune to "${targetName}"`);
    setTimeout(() => setStatusMessage(null), 2500);
  };

  const handleBuildRoadtripPlan = useCallback(
    (hours: number, pool: RoadtripPool) =>
      buildRoadtripOfflinePlan(tracks, hours, pool, favorites, playStats, offlineTrackIds),
    [tracks, favorites, playStats, offlineTrackIds]
  );

  const handlePinRoadtripOffline = (hours: number, pool: RoadtripPool) => {
    const plan = buildRoadtripOfflinePlan(tracks, hours, pool, favorites, playStats, offlineTrackIds);
    const missing = plan.tracks.filter((t) => !offlineTrackIds.has(t.id));
    if (missing.length === 0) {
      setStatusMessage(`All ${plan.tracks.length} tracks for your ${hours}h roadtrip are already cached offline!`);
      setTimeout(() => setStatusMessage(null), 3000);
      return;
    }
    runBatchDownloadTracks(missing, `${hours}h Roadtrip (${plan.tracks.length} tracks)`);
  };

  const buildCurrentBackupJson = () =>
    JSON.stringify(
      buildSettingsBackupPayload({
        nextcloudUrl: serverUrl,
        musicFolder,
        accentKey,
        cockpitThemeMode,
        nightBrightness,
        warmAmberFilter,
        replayGainEnabled,
        crossfadeSeconds,
        scvSensitivity,
        autoResumeOnBoot,
        autoResumeSoftRamp,
        autoPlayOnBluetooth,
        duckOnReverseGear,
        globalAudioSessionAttach,
        playbackRate,
        enableSwipeGestures,
        idleScreensaverSeconds,
        driverSafeMode,
        smartRollingCache,
        offlineCacheCapMb,
        deadzoneGuardMode,
        dualPaneCockpit,
        driveModeAutoSpeedKmH,
        dspSettings,
        customDspPresets,
        swcBindings,
        swcLongPressBindings,
        driverProfiles,
        activeDriverProfileId,
      }),
      null,
      2
    );

  const handleExportBackupJson = () => {
    triggerHaptic();
    const json = buildCurrentBackupJson();
    const blob = new Blob([json], { type: 'application/json;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'zentryx-tune.json';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    setStatusMessage('Exported zentryx-tune.json');
    setTimeout(() => setStatusMessage(null), 3000);
  };

  const handleImportBackupJson = async (file: File) => {
    try {
      const text = await file.text();
      applyRestoredBackup(parseSettingsBackupJson(text));
    } catch {
      setStatusMessage('Could not read backup JSON file.');
      setTimeout(() => setStatusMessage(null), 3000);
    }
  };

  const handlePushBackupCloud = async () => {
    if (!isLoggedIn || !username || !password) return;
    setIsSyncingBackup(true);
    try {
      const basicAuth = safeBtoa(`${username}:${password}`);
      const folderUrl = `/api/nextcloud/remote.php/dav/files/${encodeURIComponent(username)}/ZentryxBackup/`;
      await fetch(folderUrl, { method: 'MKCOL', headers: { Authorization: `Basic ${basicAuth}` } }).catch(() => {});
      const putRes = await fetch(`${folderUrl}zentryx-tune.json`, {
        method: 'PUT',
        headers: {
          Authorization: `Basic ${basicAuth}`,
          'Content-Type': 'application/json; charset=utf-8',
        },
        body: buildCurrentBackupJson(),
      });
      if (!putRes.ok) throw new Error(`HTTP ${putRes.status}`);
      setStatusMessage('Saved tune backup to Nextcloud /ZentryxBackup/zentryx-tune.json');
    } catch (e: any) {
      setStatusMessage(`Cloud backup failed: ${e?.message || 'Network error'}`);
    } finally {
      setIsSyncingBackup(false);
      setTimeout(() => setStatusMessage(null), 3500);
    }
  };

  const handlePullBackupCloud = async () => {
    if (!isLoggedIn || !username || !password) return;
    setIsSyncingBackup(true);
    try {
      const basicAuth = safeBtoa(`${username}:${password}`);
      const fileUrl = `/api/nextcloud/remote.php/dav/files/${encodeURIComponent(username)}/ZentryxBackup/zentryx-tune.json`;
      const res = await fetch(fileUrl, { headers: { Authorization: `Basic ${basicAuth}` } });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const text = await res.text();
      applyRestoredBackup(parseSettingsBackupJson(text));
    } catch (e: any) {
      setStatusMessage(`Cloud restore failed: ${e?.message || 'Not found'}`);
    } finally {
      setIsSyncingBackup(false);
      setTimeout(() => setStatusMessage(null), 3500);
    }
  };

  // Up-Next Queue Handlers
  const handlePlayQueueIndex = (index: number) => {
    triggerHaptic();
    if (index >= 0 && index < queue.length) {
      playTrackAtIndex(index);
    }
  };

  const handleMoveQueueUp = (index: number) => {
    triggerHaptic();
    if (index <= 0) return;
    setQueue(prev => reorderQueue(prev, index, index - 1));
    if (currentTrackIndex === index) setCurrentTrackIndex(index - 1);
    else if (currentTrackIndex === index - 1) setCurrentTrackIndex(index);
  };

  const handleMoveQueueDown = (index: number) => {
    triggerHaptic();
    if (index >= queue.length - 1) return;
    setQueue(prev => reorderQueue(prev, index, index + 1));
    if (currentTrackIndex === index) setCurrentTrackIndex(index + 1);
    else if (currentTrackIndex === index + 1) setCurrentTrackIndex(index);
  };

  const handleRemoveFromQueue = (index: number) => {
    triggerHaptic();
    setQueue(prev => removeFromQueue(prev, index));
    if (index < currentTrackIndex) {
      setCurrentTrackIndex(prev => Math.max(0, prev - 1));
    }
  };

  const handleClearQueue = () => {
    triggerHaptic();
    if (currentTrack) {
      setQueue([currentTrack]);
      setCurrentTrackIndex(0);
    } else {
      setQueue([]);
      setCurrentTrackIndex(0);
    }
  };

  const handleAddToQueue = (track: Track) => {
    triggerHaptic();
    setQueue(prev => addToQueue(prev, track));
    setStatusMessage(`Added "${track.title}" to Up-Next Queue`);
    setTimeout(() => setStatusMessage(null), 2500);
  };

  const handlePlayNextInQueue = (track: Track) => {
    triggerHaptic();
    setQueue(prev => playNextInQueue(prev, currentTrackIndex, track));
    setStatusMessage(`Will play "${track.title}" next`);
    setTimeout(() => setStatusMessage(null), 2500);
  };

  // Audio Playback Transport Handlers with Multi-Endpoint Resilience and Fallback
  const ensureAudioAnalyser = useCallback(async (): Promise<AnalyserNode | null> => {
    try {
      const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
      if (!AudioCtxClass) return null;

      const ctx = webAudioCtxRef.current || new AudioCtxClass();
      webAudioCtxRef.current = ctx;

      if (ctx.state === 'suspended') {
        await ctx.resume().catch(() => {});
      }

      let analyser = webAudioAnalyserRef.current;
      if (!analyser) {
        analyser = ctx.createAnalyser();
        analyser.fftSize = 512; // 256 bins (~86 Hz/bin at 44.1kHz)
        analyser.minDecibels = -85;
        analyser.maxDecibels = -18;
        analyser.smoothingTimeConstant = 0.62;
        webAudioAnalyserRef.current = analyser;
      }

      let dspEngine = dspEngineRef.current;
      if (!dspEngine || dspEngine.ctx !== ctx) {
        dspEngine = new HeadunitDspEngine(ctx);
        dspEngine.applySettings(effectiveDspSettings, isMuted ? 0 : volume, currentGpsSpeed);
        dspEngine.output.connect(analyser);
        dspEngineRef.current = dspEngine;
      }

      if (!analyserConnectedRef.current) {
        analyser.connect(ctx.destination);
        analyserConnectedRef.current = true;
      }

      if (audioRef.current && !mediaElementSourceRef.current) {
        const mediaSrc = ctx.createMediaElementSource(audioRef.current);
        mediaSrc.connect(dspEngine.input);
        mediaElementSourceRef.current = mediaSrc;
      }

      setActiveAnalyserNode((prev) => (prev === analyser ? prev : analyser));
      return analyser;
    } catch (err) {
      console.warn('Web Audio analyser setup skipped:', err);
      return null;
    }
  }, []);

  const triggerSoftStartRamp = useCallback(() => {
    if (!autoResumeSoftRamp) {
      setSoftStartGain(1);
      return;
    }
    if (softStartRafRef.current !== null) {
      cancelAnimationFrame(softStartRafRef.current);
      softStartRafRef.current = null;
    }
    const startTs = performance.now();
    const durationMs = 1500;
    setSoftStartGain(calculateSoftStartGain(0, durationMs));

    const step = (now: number) => {
      const elapsed = now - startTs;
      const g = calculateSoftStartGain(elapsed, durationMs);
      setSoftStartGain(g);
      if (elapsed < durationMs) {
        softStartRafRef.current = requestAnimationFrame(step);
      } else {
        softStartRafRef.current = null;
        setSoftStartGain(1);
      }
    };
    softStartRafRef.current = requestAnimationFrame(step);
  }, [autoResumeSoftRamp]);

  const triggerCrossfadeIncomingRamp = useCallback((durationSec: number) => {
    if (crossfadeIncomingRafRef.current !== null) {
      cancelAnimationFrame(crossfadeIncomingRafRef.current);
      crossfadeIncomingRafRef.current = null;
    }
    if (durationSec <= 0) {
      setCrossfadeGain(1);
      return;
    }
    const rampMs = Math.min(1800, Math.max(400, durationSec * 450));
    const startTs = performance.now();
    setCrossfadeGain(0.15);

    const step = (now: number) => {
      const t = Math.min(1, (now - startTs) / rampMs);
      const { incomingGain } = calculateCrossfadeGains(t);
      setCrossfadeGain(Math.max(0.15, incomingGain));
      if (t < 1) {
        crossfadeIncomingRafRef.current = requestAnimationFrame(step);
      } else {
        crossfadeIncomingRafRef.current = null;
        setCrossfadeGain(1);
      }
    };
    crossfadeIncomingRafRef.current = requestAnimationFrame(step);
  }, []);

  const startAudioPlayback = async (targetTrack: Track, seekOnStartSeconds?: number) => {
    if (!audioRef.current || !targetTrack) return;
    setPlaybackError(null);
    crossfadeTriggeredForTrackRef.current = null;
    if (crossfadeSeconds === 0) {
      setCrossfadeGain(1);
    }

    if (seekOnStartSeconds && seekOnStartSeconds > 0) {
      pendingResumeSeekRef.current = { trackId: targetTrack.id, positionSeconds: seekOnStartSeconds };
    }

    if (lastRecordedPlayTrackIdRef.current !== targetTrack.id) {
      lastRecordedPlayTrackIdRef.current = targetTrack.id;
      setPlayStats((prev) => recordTrackPlayStat(prev, targetTrack.id));
    }

    if (pendingSoftStartOnPlayRef.current) {
      pendingSoftStartOnPlayRef.current = false;
      triggerSoftStartRamp();
    }

    // Ensure HTML5 <audio> element is bridged to live FFT AnalyserNode
    await ensureAudioAnalyser();

    // Stop any in-flight Web Audio source
    if (webAudioSourceRef.current) {
      try { webAudioSourceRef.current.stop(); } catch {}
      webAudioSourceRef.current = null;
    }
    setIsWebAudioActive(false);

    const audio = audioRef.current;
    const candidates = getTrackCandidateUrls(targetTrack);
    if (candidates.length === 0) return;

    addDiagnosticLog({
      type: 'INFO',
      action: 'PLAY_REQUEST',
      url: targetTrack.fileName,
      details: `Starting playback sequence for "${targetTrack.title}" (${targetTrack.sizeFormatted}) across ${candidates.length} candidate endpoints`,
    });

    // 0a. Check IndexedDB Offline Storage (Zero network latency for roadtrips)
    try {
      const offlineRec = await getOfflineTrack(targetTrack.id);
      if (offlineRec && offlineRec.blob) {
        const offUrl = URL.createObjectURL(offlineRec.blob);
        audio.pause();
        audio.removeAttribute('src');
        audio.load();
        audio.dataset.trackId = targetTrack.id;
        audio.dataset.trackUrl = offUrl;
        audio.src = offUrl;
        audio.load();
        await audio.play();
        setIsPlaying(true);
        setPlaybackError(null);
        setStatusMessage(`Playing from Vehicle Offline Storage (0ms network delay)`);
        setTimeout(() => setStatusMessage(null), 2500);
        addDiagnosticLog({
          type: 'SUCCESS',
          action: 'PLAYING_OFFLINE',
          url: targetTrack.fileName,
          details: `Direct playback from IndexedDB vehicle flash storage!`,
        });
        return;
      }
    } catch {}

    // 0b. Check if track has been pre-cached in memory for gapless transition
    if (prefetchBlobUrlRef.current && prefetchBlobUrlRef.current.id === targetTrack.id) {
      try {
        const preUrl = prefetchBlobUrlRef.current.url;
        audio.pause();
        audio.removeAttribute('src');
        audio.load();
        audio.dataset.trackId = targetTrack.id;
        audio.dataset.trackUrl = preUrl;
        audio.src = preUrl;
        audio.load();
        await audio.play();
        setIsPlaying(true);
        setPlaybackError(null);
        addDiagnosticLog({
          type: 'SUCCESS',
          action: 'PLAYING_GAPLESS',
          url: targetTrack.fileName,
          details: `Immediate gapless playback from smart pre-cache!`,
        });
        return;
      } catch {}
    }

    // 0c. Direct Local USB / Device Blob URL playback (zero network request)
    if (targetTrack.sourceType === 'LOCAL' || (targetTrack.streamUrl && targetTrack.streamUrl.startsWith('blob:'))) {
      try {
        audio.pause();
        audio.removeAttribute('src');
        audio.load();
        audio.dataset.trackId = targetTrack.id;
        audio.dataset.trackUrl = targetTrack.streamUrl;
        audio.src = targetTrack.streamUrl;
        audio.load();
        await audio.play();
        setIsPlaying(true);
        setPlaybackError(null);
        setStatusMessage(null);
        return;
      } catch (localErr: any) {
        setPlaybackError(`Local audio playback failed: ${localErr?.message || 'Unsupported format'}`);
        return;
      }
    }

    // 1. Direct HTML5 Media Streaming attempt with multi-candidate fallback
    for (let i = 0; i < candidates.length; i++) {
      const targetUrl = candidates[i];
      const endpointName = targetUrl.includes('/api/audio-stream')
        ? 'Resilient Audio Pipeline'
        : targetUrl.includes('/remote.php/webdav/')
        ? 'Legacy WebDAV'
        : 'Canonical DAV Files';

      addDiagnosticLog({
        type: 'INFO',
        action: `DIRECT_TRY_${i + 1}`,
        url: targetUrl,
        details: `Trying direct media stream on [${endpointName}]`,
      });

      try {
        // Complete reset of audio element to clear any previous error state
        audio.pause();
        audio.removeAttribute('src');
        audio.load();

        audio.dataset.trackId = targetTrack.id;
        audio.dataset.trackUrl = targetUrl;
        audio.src = targetUrl;
        audio.load();

        // Wait for playback to begin or signal error with realistic buffering allowances
        await new Promise<void>((resolve, reject) => {
          let done = false;
          let timer: any = null;

          const cleanup = () => {
            audio.removeEventListener('playing', onPlaying);
            audio.removeEventListener('canplay', onCanPlay);
            audio.removeEventListener('loadeddata', onLoadedData);
            audio.removeEventListener('loadedmetadata', onLoadedMetadata);
            audio.removeEventListener('error', onError);
            clearTimeout(timer);
          };

          const onPlaying = () => {
            if (!done) {
              done = true;
              cleanup();
              resolve();
            }
          };

          const onCanPlay = () => {
            audio.play().then(() => {
              if (!done) {
                done = true;
                cleanup();
                resolve();
              }
            }).catch((e) => {
              if (e.name === 'NotAllowedError') {
                if (!done) {
                  done = true;
                  cleanup();
                  reject(e);
                }
              }
            });
          };

          const onLoadedData = () => {
            audio.play().catch(() => {});
          };

          const onLoadedMetadata = () => {
            setStatusMessage(`Buffering "${targetTrack.title}" (${targetTrack.sizeFormatted})...`);
            audio.play().catch(() => {});
          };

          const onError = () => {
            if (!done) {
              done = true;
              const mediaErr = audio.error;
              cleanup();
              reject(new Error(mediaErr ? `Media error code ${mediaErr.code} (${mediaErr.message || 'decode failed'})` : 'Audio source failed'));
            }
          };

          // Generous timeout for large files (e.g. 50MB+ podcasts/tracks over network)
          const timeoutMs = targetUrl.includes('/api/audio-stream') ? 25000 : 8000;
          timer = setTimeout(() => {
            if (!done) {
              // If audio has loaded metadata (readyState >= 1) or is playing, do NOT abort!
              if (!audio.paused && audio.currentTime > 0) {
                done = true;
                cleanup();
                resolve();
              } else if (audio.readyState >= 1) {
                // Metadata received successfully; still buffering audio frames, give more time
                audio.play().then(() => {
                  if (!done) {
                    done = true;
                    cleanup();
                    resolve();
                  }
                }).catch(() => {
                  timer = setTimeout(() => {
                    if (!done) {
                      done = true;
                      cleanup();
                      reject(new Error('Audio buffering timed out after 35s'));
                    }
                  }, 10000);
                });
              } else {
                done = true;
                cleanup();
                reject(new Error('Direct streaming connection timed out'));
              }
            }
          }, timeoutMs);

          audio.addEventListener('playing', onPlaying, { once: true });
          audio.addEventListener('canplay', onCanPlay);
          audio.addEventListener('loadeddata', onLoadedData);
          audio.addEventListener('loadedmetadata', onLoadedMetadata);
          audio.addEventListener('error', onError, { once: true });

          audio.play().then(() => {
            if (!done) {
              done = true;
              cleanup();
              resolve();
            }
          }).catch((err: any) => {
            if (err.name === 'NotAllowedError') {
              if (!done) {
                done = true;
                cleanup();
                reject(err);
              }
            }
          });
        });

        setIsPlaying(true);
        setPlaybackError(null);
        setStatusMessage(null);
        addDiagnosticLog({
          type: 'SUCCESS',
          action: 'PLAYING_DIRECT',
          url: targetUrl,
          details: `Direct playback started successfully via [${endpointName}]!`,
        });
        return; // Successfully playing!
      } catch (directErr: any) {
        if (directErr.name === 'AbortError') return;
        audio.pause();
        audio.removeAttribute('src');
        audio.load();
        const errDetails = audio.error ? ` (media error code ${audio.error.code})` : '';
        addDiagnosticLog({
          type: 'WARN',
          action: `DIRECT_TRY_${i + 1}_FAILED`,
          url: targetUrl,
          details: `Direct play notice on [${endpointName}]: ${directErr.message || 'Media source error'}${errDetails}. Switching to next candidate...`,
        });
      }
    }

    // 2. Authenticated Buffer Fallback (fetch blob & Web Audio) if direct playback hit restrictions
    setStatusMessage(`Buffering "${targetTrack.title}" from Nextcloud...`);
    const authHeader = `Basic ${safeBtoa(`${username}:${password}`)}`;

    for (let i = 0; i < candidates.length; i++) {
      const targetUrl = candidates[i];
      // Keep auth query parameter on /api/audio-stream so server-side middleware has credentials
      const cleanFetchUrl = targetUrl.includes('/api/audio-stream')
        ? targetUrl
        : targetUrl.replace(/\?auth=[^&]*/g, '').replace(/&auth=[^&]*/g, '');

      const endpointName = targetUrl.includes('/api/audio-stream')
        ? 'Resilient Audio Pipeline'
        : targetUrl.includes('/remote.php/webdav/')
        ? 'Legacy WebDAV'
        : 'Canonical DAV Files';

      addDiagnosticLog({
        type: 'INFO',
        action: `BUFFER_FETCH_${i + 1}`,
        method: 'GET',
        url: cleanFetchUrl,
        details: `Attempting authenticated buffer fetch on [${endpointName}]`,
      });

      try {
        const { buffer: arrayBuffer, status: fetchStatus, statusText: fetchStatusText } = await fetchTrackBufferChunked(
          cleanFetchUrl,
          authHeader
        );

        if (arrayBuffer.byteLength < 1000) {
          throw new Error(`Incomplete audio payload received (${arrayBuffer.byteLength} bytes)`);
        }

        // Extract multi-format tags & embedded artwork from already-downloaded audio buffer
        try {
          await hydrateTrackMetadata(targetTrack, undefined, arrayBuffer);
        } catch {}
        const ext = (targetTrack.fileName || '').split('.').pop()?.toLowerCase();
        const mimeType =
          ext === 'flac'
            ? 'audio/flac'
            : ext === 'ogg' || ext === 'opus'
            ? 'audio/ogg'
            : ext === 'wav'
            ? 'audio/wav'
            : ext === 'm4a' || ext === 'aac'
            ? 'audio/mp4'
            : 'audio/mpeg';

        // Attempt 2a: Play via Blob URL on HTML5 Audio with clean reset
        try {
          const audioBlob = new Blob([arrayBuffer], { type: mimeType });
          const blobUrl = URL.createObjectURL(audioBlob);

          audio.pause();
          audio.removeAttribute('src');
          audio.load();

          audio.dataset.trackId = targetTrack.id;
          audio.dataset.trackUrl = blobUrl;
          audio.src = blobUrl;
          audio.load();

          await audio.play();
          setIsPlaying(true);
          setPlaybackError(null);
          setStatusMessage(null);
          addDiagnosticLog({
            type: 'SUCCESS',
            action: 'BUFFER_BLOB_OK',
            method: 'GET',
            url: cleanFetchUrl,
            status: fetchStatus,
            statusText: fetchStatusText,
            details: `Successfully fetched track blob (${formatBytes(audioBlob.size)}) via [${endpointName}] and started playback!`,
          });
          return; // Successfully playing buffered blob!
        } catch (blobErr: any) {
          addDiagnosticLog({
            type: 'WARN',
            action: `BLOB_PLAY_${i + 1}_ERROR`,
            url: cleanFetchUrl,
            details: `Audio element rejected blob stream: ${blobErr.message}. Trying direct Web Audio API decoder...`,
          });
        }

        // Attempt 2b: Invincible Web Audio API fallback (decodes audio frames in memory)
        try {
          const AudioCtxClass = window.AudioContext || (window as any).webkitAudioContext;
          if (AudioCtxClass) {
            const ctx = webAudioCtxRef.current || new AudioCtxClass();
            webAudioCtxRef.current = ctx;
            if (ctx.state === 'suspended') {
              await ctx.resume();
            }

            const decodedAudio = await ctx.decodeAudioData(arrayBuffer.slice(0));
            webAudioBufferRef.current = decodedAudio;

            if (webAudioSourceRef.current) {
              try { webAudioSourceRef.current.stop(); } catch {}
            }

            const analyser = await ensureAudioAnalyser();

            const gainNode = ctx.createGain();
            gainNode.gain.value = isMuted ? 0 : volume / 100;
            if (dspEngineRef.current && dspEngineRef.current.ctx === ctx) {
              gainNode.connect(dspEngineRef.current.input);
            } else if (analyser) {
              gainNode.connect(analyser);
            } else {
              gainNode.connect(ctx.destination);
            }
            webAudioGainRef.current = gainNode;

            const sourceNode = ctx.createBufferSource();
            sourceNode.buffer = decodedAudio;
            sourceNode.connect(gainNode);
            sourceNode.start(0);

            webAudioSourceRef.current = sourceNode;
            webAudioStartTimeRef.current = ctx.currentTime;
            setIsWebAudioActive(true);
            setIsPlaying(true);
            setPlaybackError(null);
            setStatusMessage(null);

            const trackDur = Math.floor(decodedAudio.duration);
            setDurationSeconds(trackDur);
            if (targetTrack) {
              targetTrack.duration = formatTime(trackDur);
              targetTrack.durationSeconds = trackDur;
            }

            sourceNode.onended = () => {
              if (repeatMode === 'ONE') {
                startAudioPlayback(targetTrack);
              } else {
                handleNextTrack();
              }
            };

            addDiagnosticLog({
              type: 'SUCCESS',
              action: 'WEB_AUDIO_OK',
              url: cleanFetchUrl,
              status: fetchStatus,
              statusText: fetchStatusText,
              details: `Decoded and streaming "${targetTrack.title}" (${trackDur}s, ${formatBytes(arrayBuffer.byteLength)}) via Web Audio API!`,
            });
            return; // Successfully playing via Web Audio!
          }
        } catch (webAudioErr: any) {
          addDiagnosticLog({
            type: 'WARN',
            action: `WEB_AUDIO_ERROR`,
            url: cleanFetchUrl,
            details: `Web Audio decode error: ${webAudioErr.message}`,
          });
        }
      } catch (buffErr: any) {
        addDiagnosticLog({
          type: 'ERROR',
          action: `BUFFER_FETCH_${i + 1}_ERROR`,
          method: 'GET',
          url: cleanFetchUrl,
          status: buffErr.status || 500,
          statusText: buffErr.statusText || 'Fetch Error',
          details: `Nextcloud server returned HTTP ${buffErr.status || 500} on [${endpointName}]: ${buffErr.message}`,
          headers: buffErr.headers,
          body: (buffErr.body || '').slice(0, 2000),
        });
      }
    }

    // Refresh server proxy wire logs to show the user exact wire output
    await fetchServerProxyLogs();

    // If all candidates failed:
    setIsPlaying(false);
    setPlaybackError(`Streaming error. Captured server error in Settings -> Connection Diagnostics.`);
    setStatusMessage(null);
    addDiagnosticLog({
      type: 'ERROR',
      action: 'PLAYBACK_TERMINATED',
      url: targetTrack.fileName,
      details: `All candidate streaming endpoints failed. Open the Settings tab to inspect the server response body and wire logs.`,
    });
  };

  const playTrackAtIndex = async (idx: number) => {
    if (idx < 0 || idx >= queue.length) return;
    triggerHaptic();
    setCurrentTrackIndex(idx);
    setPositionSeconds(0);
    const target = queue[idx];
    if (target) {
      await startAudioPlayback(target);
    }
  };

  const togglePlayPause = async () => {
    if (!currentTrack) return;
    triggerHaptic();

    if (isPlaying) {
      if (webAudioSourceRef.current) {
        try { webAudioSourceRef.current.stop(); } catch {}
        webAudioSourceRef.current = null;
      }
      if (audioRef.current) {
        audioRef.current.pause();
      }
      setIsPlaying(false);
    } else {
      // 1. Direct HTML5 Audio element resume (preserves currentTime in place without resetting)
      if (
        audioRef.current &&
        audioRef.current.dataset.trackId === currentTrack.id &&
        audioRef.current.src &&
        !audioRef.current.error
      ) {
        try {
          await ensureAudioAnalyser();
          if (pendingResumeSeekRef.current && pendingResumeSeekRef.current.trackId === currentTrack.id) {
            audioRef.current.currentTime = pendingResumeSeekRef.current.positionSeconds;
            pendingResumeSeekRef.current = null;
          }
          if (pendingSoftStartOnPlayRef.current) {
            pendingSoftStartOnPlayRef.current = false;
            triggerSoftStartRamp();
          }
          await audioRef.current.play();
          setIsPlaying(true);
          return;
        } catch (err: any) {
          console.warn('Audio resume failed, falling back to full stream sequence:', err);
        }
      }

      // 2. Web Audio decoded buffer resume from exact paused position
      if (isWebAudioActive && webAudioBufferRef.current && webAudioCtxRef.current) {
        try {
          const ctx = webAudioCtxRef.current;
          if (ctx.state === 'suspended') {
            await ctx.resume();
          }
          const src = ctx.createBufferSource();
          src.buffer = webAudioBufferRef.current;
          src.connect(webAudioGainRef.current || ctx.destination);
          src.start(0, positionSeconds);
          webAudioStartTimeRef.current = ctx.currentTime - positionSeconds;
          webAudioSourceRef.current = src;
          if (pendingSoftStartOnPlayRef.current) {
            pendingSoftStartOnPlayRef.current = false;
            triggerSoftStartRamp();
          }
          setIsPlaying(true);
          src.onended = () => {
            if (repeatMode === 'ONE') {
              startAudioPlayback(currentTrack);
            } else {
              handleNextTrack();
            }
          };
          return;
        } catch (webAudioResumeErr: any) {
          console.warn('Web Audio resume failed, falling back to full stream sequence:', webAudioResumeErr);
        }
      }

      // 3. Fallback: Full playback sequence if track was not already loaded in memory
      await startAudioPlayback(currentTrack, positionSeconds > 0 ? positionSeconds : undefined);
    }
  };

  const handleNextTrack = (withCrossfadeRamp = false) => {
    if (queue.length === 0) return;
    triggerHaptic();
    if (withCrossfadeRamp && crossfadeSeconds > 0) {
      triggerCrossfadeIncomingRamp(crossfadeSeconds);
    } else {
      setCrossfadeGain(1);
    }
    if (repeatMode === 'ONE' && currentTrack) {
      startAudioPlayback(currentTrack);
      return;
    }

    const nextIdx = resolveNextIndex(queue.length, currentTrackIndex, shuffle, repeatMode);
    if (nextIdx !== -1) {
      playTrackAtIndex(nextIdx);
    }
  };

  const handlePrevTrack = () => {
    if (queue.length === 0) return;
    triggerHaptic();
    const prevIdx = resolvePreviousIndex(queue.length, currentTrackIndex, positionSeconds);
    if (prevIdx === currentTrackIndex) {
      if (audioRef.current && !isWebAudioActive) {
        audioRef.current.currentTime = 0;
      }
      setPositionSeconds(0);
    } else {
      playTrackAtIndex(prevIdx);
    }
  };

  // Attenuation / "Talk" mode toggle (-20dB)
  const handleToggleAttenuation = () => {
    triggerHaptic();
    setIsAttenuated((prev) => {
      const next = !prev;
      setStatusMessage(next ? 'Cockpit Attenuation Active (-20 dB)' : 'Volume Restored');
      setTimeout(() => setStatusMessage(null), 2000);
      return next;
    });
  };

  // Synchronize Audio volume with Speed Boost, Attenuation, Sleep Fade, ReplayGain, Soft-Start & Crossfade
  useEffect(() => {
    let vol = isMuted ? 0 : volume / 100;
    // Apply speed compensation boost
    const speedBoost = calculateSpeedVolumeBoost(currentGpsSpeed, scvSensitivity);
    vol = Math.min(1.0, vol * speedBoost.linearMultiplier);
    // Apply -20dB Attenuation
    if (isAttenuated) {
      vol = vol * calculateAttenuationMultiplier(true, -20);
    }
    // Apply Sleep Timer fade-out if in last 10 seconds
    if (sleepTimerRemainingSec !== null && sleepTimerRemainingSec <= 10) {
      vol = vol * calculateSleepTimerFade(sleepTimerRemainingSec);
    }
    // Apply ReplayGain track loudness normalization
    vol = vol * replayGainDbToMultiplier(currentTrack?.replayGainDb, replayGainEnabled);
    // Apply Boot Soft-Start Ramp & Crossfade Gain
    vol = vol * softStartGain * crossfadeGain;

    if (audioRef.current) {
      audioRef.current.volume = Math.max(0, Math.min(1, vol));
    }
    if (webAudioGainRef.current) {
      webAudioGainRef.current.gain.value = Math.max(0, Math.min(1, vol));
    }
  }, [
    volume,
    isMuted,
    currentGpsSpeed,
    scvSensitivity,
    isAttenuated,
    sleepTimerRemainingSec,
    replayGainEnabled,
    currentTrack?.replayGainDb,
    softStartGain,
    crossfadeGain,
  ]);

  // Synchronize playback speed
  useEffect(() => {
    if (audioRef.current) {
      audioRef.current.playbackRate = playbackRate;
    }
  }, [playbackRate]);

  // Solar auto theme 1-minute tick timer
  useEffect(() => {
    if (cockpitThemeMode !== 'auto') return;
    const id = setInterval(() => setSolarMinuteTick(Math.floor(Date.now() / 60000)), 60000);
    return () => clearInterval(id);
  }, [cockpitThemeMode]);

  // Real GPS Speed & Position Tracker for SDVC + NOAA Astronomical Solar Night Mode
  useEffect(() => {
    if (scvSensitivity === 'off' && cockpitThemeMode !== 'auto') {
      setCurrentGpsSpeed(0);
      setGpsStatus('off');
      return;
    }
    if (typeof navigator === 'undefined' || !navigator.geolocation) {
      setCurrentGpsSpeed(0);
      setGpsStatus('unavailable');
      return;
    }

    setGpsStatus('waiting');
    let watchId: number | null = null;
    const handlePos = (pos: GeolocationPosition) => {
      setGpsStatus('active');
      if (Number.isFinite(pos.coords.latitude) && Number.isFinite(pos.coords.longitude)) {
        setGpsCoords({ lat: pos.coords.latitude, lon: pos.coords.longitude });
      }
      if (pos.coords.speed !== null && !isNaN(pos.coords.speed)) {
        const kmh = Math.round(pos.coords.speed * 3.6);
        setCurrentGpsSpeed(Math.max(0, kmh));
      } else {
        setCurrentGpsSpeed(0);
      }
    };
    const handleErr = (err: GeolocationPositionError) => {
      if (err.code === 1) {
        setGpsStatus('denied');
      } else if (err.code === 2) {
        setGpsStatus('unavailable');
      } else {
        setGpsStatus('waiting');
      }
    };

    try {
      if (gpsPollNonce > 0) {
        navigator.geolocation.getCurrentPosition(handlePos, handleErr, {
          enableHighAccuracy: true,
          maximumAge: 0,
          timeout: 8000,
        });
      }
      watchId = navigator.geolocation.watchPosition(handlePos, handleErr, {
        enableHighAccuracy: true,
        maximumAge: 3000,
      });
    } catch {
      setGpsStatus('unavailable');
    }

    return () => {
      if (watchId !== null) navigator.geolocation.clearWatch(watchId);
    };
  }, [scvSensitivity, cockpitThemeMode, gpsPollNonce]);

  // Sleep Timer Countdown & Volume Fade-out effect
  useEffect(() => {
    if (sleepTimerMinutes === null) {
      setSleepTimerRemainingSec(null);
      return;
    }

    setSleepTimerRemainingSec(sleepTimerMinutes * 60);
    const interval = setInterval(() => {
      setSleepTimerRemainingSec((prev) => {
        if (prev === null || prev <= 1) {
          if (isPlaying) {
            togglePlayPause();
          }
          setStatusMessage('Sleep timer expired. Playback paused.');
          setTimeout(() => setStatusMessage(null), 3000);
          setSleepTimerMinutes(null);
          return null;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(interval);
  }, [sleepTimerMinutes]);

  // Batch Syncing for Offline Roadtrips
  const handleBatchSyncCurrentFolder = async () => {
    if (tracks.length === 0 || isBatchSyncing) return;
    triggerHaptic();
    setIsBatchSyncing(true);

    const unsynced = tracks.filter((t) => !offlineTrackIds.has(t.id));
    if (unsynced.length === 0) {
      setStatusMessage('All tracks in this collection are already cached offline!');
      setIsBatchSyncing(false);
      setTimeout(() => setStatusMessage(null), 3000);
      return;
    }

    setStatusMessage(`Batch syncing ${unsynced.length} tracks to vehicle storage...`);

    for (let i = 0; i < unsynced.length; i++) {
      const t = unsynced[i];
      setBatchSyncProgress({ current: i + 1, total: unsynced.length, trackTitle: t.title });
      try {
        await downloadTrackForOffline(t);
      } catch (e) {
        console.warn(`Failed syncing "${t.title}":`, e);
      }
    }

    setIsBatchSyncing(false);
    setBatchSyncProgress(null);
    await refreshOfflineStorage();
    setStatusMessage(`Batch sync complete! ${unsynced.length} tracks ready for offline driving.`);
    setTimeout(() => setStatusMessage(null), 3500);
  };

  const handleClearOfflineStorage = async () => {
    triggerHaptic();
    await clearAllOfflineTracks();
    await refreshOfflineStorage();
    setStatusMessage('Vehicle offline storage cleared.');
    setTimeout(() => setStatusMessage(null), 3000);
  };

  // No-Look Cockpit Swipe Gestures
  const handleTouchStart = (e: React.TouchEvent) => {
    if (!enableSwipeGestures) return;
    if (e.touches.length === 2) {
      triggerHaptic();
      togglePlayPause();
      setGestureNotice(isPlaying ? '⏸ PAUSE' : '▶ PLAY');
      setTimeout(() => setGestureNotice(null), 1500);
      return;
    }
    if (e.touches.length === 1) {
      touchStartXRef.current = e.touches[0].clientX;
      touchStartYRef.current = e.touches[0].clientY;
    }
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (!enableSwipeGestures || touchStartXRef.current === null || touchStartYRef.current === null) return;
    const deltaX = e.changedTouches[0].clientX - touchStartXRef.current;
    const deltaY = e.changedTouches[0].clientY - touchStartYRef.current;

    if (Math.abs(deltaX) > 60 && Math.abs(deltaY) < 60) {
      triggerHaptic();
      if (deltaX < 0) {
        handleNextTrack();
        setGestureNotice('⏭ NEXT TRACK');
      } else {
        handlePrevTrack();
        setGestureNotice('⏮ PREVIOUS TRACK');
      }
      setTimeout(() => setGestureNotice(null), 1500);
    }
    touchStartXRef.current = null;
    touchStartYRef.current = null;
  };

  // Web Audio position tracker while playing
  useEffect(() => {
    let interval: any = null;
    if (isPlaying && isWebAudioActive) {
      interval = setInterval(() => {
        if (webAudioCtxRef.current && isPlaying) {
          const currentPos = Math.floor(webAudioCtxRef.current.currentTime - webAudioStartTimeRef.current);
          if (currentPos >= 0) {
            setPositionSeconds(currentPos);
          }
          if (durationSeconds > 0 && currentPos >= durationSeconds) {
            handleNextTrack();
          }
        }
      }, 500);
    }
    return () => clearInterval(interval);
  }, [isPlaying, isWebAudioActive, durationSeconds]);

  // Toggle Favorite (plus optional Smart Rolling Auto-Pin Offline)
  const toggleFavorite = (trackId: string) => {
    setFavorites((prev) => {
      const next = new Set(prev);
      const isAdding = !next.has(trackId);
      if (isAdding) {
        next.add(trackId);
      } else {
        next.delete(trackId);
      }
      if (isAdding && smartRollingCache && isLoggedIn && isOnline && !offlineTrackIds.has(trackId)) {
        const favTrack = tracks.find((t) => t.id === trackId);
        if (favTrack) {
          setTimeout(() => {
            downloadTrackForOffline(favTrack).catch(() => {});
          }, 50);
        }
      }
      return next;
    });
  };

  // Toggle Download / Offline Pinning
  const toggleDownload = (trackId: string) => {
    if (downloadedTracks.has(trackId)) {
      setDownloadedTracks((prev) => {
        const next = new Set(prev);
        next.delete(trackId);
        return next;
      });
    } else {
      setDownloadingId(trackId);
      // Find track
      const track = tracks.find((t) => t.id === trackId);
      if (track) {
        // Trigger actual download in background
        const a = document.createElement('a');
        a.href = getTrackStreamUrl(track);
        a.download = track.fileName;
        document.body.appendChild(a);
        // Mark as cached
        setTimeout(() => {
          setDownloadedTracks((prev) => new Set(prev).add(trackId));
          setDownloadingId(null);
        }, 800);
      }
    }
  };

  // Derived Dynamic Albums and Artists Lists (Zero dummy data)
  const albumsList = useMemo(() => {
    const map = new Map<string, { name: string; artist: string; count: number; tracks: Track[] }>();
    for (const t of tracks) {
      const key = `${t.artist} - ${t.album}`;
      if (!map.has(key)) {
        map.set(key, { name: t.album, artist: t.artist, count: 1, tracks: [t] });
      } else {
        const entry = map.get(key)!;
        entry.count++;
        entry.tracks.push(t);
      }
    }
    return Array.from(map.values());
  }, [tracks]);

  const artistsList = useMemo(() => {
    const map = new Map<string, { name: string; count: number; tracks: Track[] }>();
    for (const t of tracks) {
      if (!map.has(t.artist)) {
        map.set(t.artist, { name: t.artist, count: 1, tracks: [t] });
      } else {
        const entry = map.get(t.artist)!;
        entry.count++;
        entry.tracks.push(t);
      }
    }
    return Array.from(map.values());
  }, [tracks]);

  // Total Library Size
  const totalLibraryBytes = useMemo(() => {
    return tracks.reduce((acc, t) => acc + (t.sizeBytes || 0), 0);
  }, [tracks]);

  // Hierarchical Folder Browser entries derived for current selectedFolderPath
  const folderBrowserData = useMemo(() => {
    const cleanPrefix = selectedFolderPath.replace(/^\/+/, '').replace(/\/+$/, '');
    const subFolderCounts = new Map<string, number>();
    const directTracks: Track[] = [];
    const q = librarySearchQuery.trim().toLowerCase();

    for (const t of tracks) {
      const rawFolder = (t.folderPath || '').replace(/^\/+/, '').replace(/\/+$/, '');
      if (cleanPrefix) {
        if (rawFolder === cleanPrefix) {
          directTracks.push(t);
        } else if (rawFolder.startsWith(`${cleanPrefix}/`)) {
          const rest = rawFolder.slice(cleanPrefix.length + 1);
          const nextSeg = rest.split('/')[0];
          if (nextSeg) {
            const fullChild = `${cleanPrefix}/${nextSeg}`;
            subFolderCounts.set(fullChild, (subFolderCounts.get(fullChild) || 0) + 1);
          }
        }
      } else {
        if (!rawFolder) {
          directTracks.push(t);
        } else {
          const firstSeg = rawFolder.split('/')[0];
          if (firstSeg) {
            subFolderCounts.set(firstSeg, (subFolderCounts.get(firstSeg) || 0) + 1);
          }
        }
      }
    }

    const subFolders = Array.from(subFolderCounts.entries())
      .map(([fullPath, count]) => ({
        fullPath,
        name: fullPath.split('/').pop() || fullPath,
        count,
        isLocal: fullPath === 'Local' || fullPath.startsWith('Local/'),
      }))
      .filter((f) => !q || f.name.toLowerCase().includes(q))
      .sort((a, b) => a.name.localeCompare(b.name));

    const filteredDirect = directTracks.filter((t) => {
      if (!q) return true;
      return (
        t.title.toLowerCase().includes(q) ||
        t.artist.toLowerCase().includes(q) ||
        t.album.toLowerCase().includes(q) ||
        t.fileName.toLowerCase().includes(q)
      );
    });

    return { subFolders, directTracks: filteredDirect };
  }, [tracks, selectedFolderPath, librarySearchQuery]);

  const resumableTracksCount = useMemo(() => {
    return tracks.filter((track) => {
      const rec = resumableProgressMap[track.id];
      if (!rec || rec.positionSeconds < 10) return false;
      const dur = rec.durationSeconds || track.durationSeconds;
      if (dur > 0 && rec.positionSeconds >= Math.max(dur - 5, 10)) return false;
      return true;
    }).length;
  }, [tracks, resumableProgressMap]);

  // Filtered tracks for Library view
  const displayedTracks = useMemo(() => {
    const q = librarySearchQuery.trim().toLowerCase();
    const filtered = tracks.filter((track) => {
      if (offlineOnly && !downloadedTracks.has(track.id) && !offlineTrackIds.has(track.id)) return false;
      if (selectedAlbum && track.album !== selectedAlbum) return false;
      if (selectedArtist && track.artist !== selectedArtist) return false;
      if (libraryFilter === 'favorites' && !favorites.has(track.id)) return false;
      if (libraryFilter === 'downloaded' && !downloadedTracks.has(track.id) && !offlineTrackIds.has(track.id)) return false;
      if (libraryFilter === 'playlists' && selectedPlaylist && !selectedPlaylist.trackIds.includes(track.id)) return false;
      if (libraryFilter === 'recent' && !(playStats[track.id]?.lastPlayedAt > 0)) return false;
      if (libraryFilter === 'most_played' && !(playStats[track.id]?.playCount > 0)) return false;
      if (libraryFilter === 'resume') {
        const rec = resumableProgressMap[track.id];
        if (!rec || rec.positionSeconds < 10) return false;
        const dur = rec.durationSeconds || track.durationSeconds;
        if (dur > 0 && rec.positionSeconds >= Math.max(dur - 5, 10)) return false;
      }
      if (q) {
        return (
          track.title.toLowerCase().includes(q) ||
          track.artist.toLowerCase().includes(q) ||
          track.album.toLowerCase().includes(q) ||
          track.fileName.toLowerCase().includes(q)
        );
      }
      return true;
    });

    if (libraryFilter === 'recent') {
      return [...filtered].sort(
        (a, b) => (playStats[b.id]?.lastPlayedAt || 0) - (playStats[a.id]?.lastPlayedAt || 0)
      );
    }
    if (libraryFilter === 'most_played') {
      return [...filtered].sort(
        (a, b) =>
          (playStats[b.id]?.playCount || 0) - (playStats[a.id]?.playCount || 0) ||
          (playStats[b.id]?.lastPlayedAt || 0) - (playStats[a.id]?.lastPlayedAt || 0)
      );
    }
    if (libraryFilter === 'resume') {
      return [...filtered].sort(
        (a, b) => (resumableProgressMap[b.id]?.updatedAt || 0) - (resumableProgressMap[a.id]?.updatedAt || 0)
      );
    }
    return filtered;
  }, [
    tracks,
    offlineOnly,
    downloadedTracks,
    offlineTrackIds,
    selectedAlbum,
    selectedArtist,
    libraryFilter,
    favorites,
    selectedPlaylist,
    librarySearchQuery,
    playStats,
    resumableProgressMap,
  ]);

  const filteredAlbumsList = useMemo(() => {
    const q = librarySearchQuery.trim().toLowerCase();
    if (!q) return albumsList;
    return albumsList.filter(
      (album) =>
        album.name.toLowerCase().includes(q) ||
        album.artist.toLowerCase().includes(q) ||
        album.tracks.some((t) => t.title.toLowerCase().includes(q))
    );
  }, [albumsList, librarySearchQuery]);

  const filteredArtistsList = useMemo(() => {
    const q = librarySearchQuery.trim().toLowerCase();
    if (!q) return artistsList;
    return artistsList.filter(
      (artist) =>
        artist.name.toLowerCase().includes(q) ||
        artist.tracks.some(
          (t) => t.title.toLowerCase().includes(q) || t.album.toLowerCase().includes(q)
        )
    );
  }, [artistsList, librarySearchQuery]);

  // O(1) Track Index Map for MediaTek Cortex-A55 RAM optimization
  const trackIndexMap = useMemo(() => buildTrackIndexMap(tracks), [tracks]);
  const localTrackCount = useMemo(() => tracks.filter((t) => t.sourceType === 'LOCAL').length, [tracks]);

  // Persist MediaTek Hardware Optimization & Auto-Metadata modes
  useEffect(() => {
    localStorage.setItem('zentryx_mediatek_eco', mediatekEcoMode ? 'true' : 'false');
  }, [mediatekEcoMode]);

  useEffect(() => {
    localStorage.setItem('zentryx_auto_metadata', autoFetchMetadata ? 'true' : 'false');
  }, [autoFetchMetadata]);

  // Deferred Non-Blocking Metadata Collection for Actively Playing Track
  useEffect(() => {
    if (!currentTrack || !isPlaying || !autoFetchMetadata || !isOnline) return;
    if (checkedMetaIdsRef.current.has(currentTrack.id) && currentTrack.metadataHydrated) {
      return;
    }

    metadataAbortRef.current?.abort();
    const controller = new AbortController();
    metadataAbortRef.current = controller;

    // Defer 450ms + requestIdleCallback so audio playback buffering has 100% network & CPU priority
    const win = window as any;
    let idleId: number | null = null;
    const timerId = setTimeout(() => {
      if (controller.signal.aborted) return;
      if (typeof win.requestIdleCallback === 'function') {
        idleId = win.requestIdleCallback(
          () => {
            hydrateTrackMetadata(currentTrack, controller.signal);
          },
          { timeout: 1500 }
        );
      } else {
        hydrateTrackMetadata(currentTrack, controller.signal);
      }
    }, 450);

    return () => {
      clearTimeout(timerId);
      if (idleId !== null && typeof win.cancelIdleCallback === 'function') {
        win.cancelIdleCallback(idleId);
      }
      controller.abort();
    };
  }, [currentTrack?.id, isPlaying, autoFetchMetadata, isOnline]);

  // Initial Auto-scan if logged in but tracks empty
  useEffect(() => {
    if (isLoggedIn && username && password && tracks.length === 0 && !isScanning) {
      scanNextcloudMusic(username, password, musicFolder);
    }
  }, []);

  // Persist Category 1-3 QoL Preferences
  useEffect(() => {
    localStorage.setItem('zentryx_auto_resume', autoResumeOnBoot ? 'true' : 'false');
  }, [autoResumeOnBoot]);

  useEffect(() => {
    localStorage.setItem('zentryx_soft_ramp', autoResumeSoftRamp ? 'true' : 'false');
  }, [autoResumeSoftRamp]);

  useEffect(() => {
    localStorage.setItem('zentryx_idle_screensaver_sec', String(idleScreensaverSeconds));
  }, [idleScreensaverSeconds]);

  useEffect(() => {
    localStorage.setItem('zentryx_night_brightness', String(nightBrightness));
  }, [nightBrightness]);

  useEffect(() => {
    localStorage.setItem('zentryx_amber_filter', warmAmberFilter ? 'true' : 'false');
  }, [warmAmberFilter]);

  useEffect(() => {
    localStorage.setItem('zentryx_driver_safe', driverSafeMode ? 'true' : 'false');
  }, [driverSafeMode]);

  useEffect(() => {
    try {
      localStorage.setItem('zentryx_swc_bindings', JSON.stringify(swcBindings));
      localStorage.setItem('zentryx_swc_long_bindings', JSON.stringify(swcLongPressBindings));
    } catch {}
  }, [swcBindings, swcLongPressBindings]);

  useEffect(() => {
    try {
      localStorage.setItem('zentryx_driver_profiles', JSON.stringify(driverProfiles));
      localStorage.setItem('zentryx_active_driver_profile', activeDriverProfileId);
    } catch {}
  }, [driverProfiles, activeDriverProfileId]);

  useEffect(() => {
    localStorage.setItem('zentryx_bt_autoplay', autoPlayOnBluetooth ? 'true' : 'false');
    localStorage.setItem('zentryx_reverse_duck', duckOnReverseGear ? 'true' : 'false');
    localStorage.setItem('zentryx_global_eq', globalAudioSessionAttach ? 'true' : 'false');
  }, [autoPlayOnBluetooth, duckOnReverseGear, globalAudioSessionAttach]);

  useEffect(() => {
    localStorage.setItem('zentryx_replay_gain', replayGainEnabled ? 'true' : 'false');
  }, [replayGainEnabled]);

  useEffect(() => {
    localStorage.setItem('zentryx_crossfade_sec', String(crossfadeSeconds));
    if (crossfadeSeconds === 0) setCrossfadeGain(1);
  }, [crossfadeSeconds]);

  useEffect(() => {
    localStorage.setItem('zentryx_rolling_cache', smartRollingCache ? 'true' : 'false');
    if (smartRollingCache) {
      enforceOfflineCacheCap(favorites, offlineCacheCapMb);
    }
  }, [smartRollingCache, offlineCacheCapMb]);

  useEffect(() => {
    localStorage.setItem('zentryx_cache_cap_mb', String(offlineCacheCapMb));
  }, [offlineCacheCapMb]);

  useEffect(() => {
    latestCabinNoiseRef.current = cabinNoiseDb;
  }, [cabinNoiseDb]);

  useEffect(() => {
    latestSpeedKmHRef.current = currentGpsSpeed;
  }, [currentGpsSpeed]);

  useEffect(() => {
    try {
      localStorage.setItem('zentryx_adaptive_cabin_dsp', JSON.stringify(adaptiveCabinConfig));
    } catch {}
  }, [adaptiveCabinConfig]);

  // Live Cabin Microphone Noise Monitor (automatically releases mic hardware when toggled off or in Speed-only mode)
  useEffect(() => {
    if (!adaptiveCabinConfig.enabled || adaptiveCabinConfig.source === 'speed') {
      setCabinNoiseDb(null);
      setCabinMicStatus('off');
      return;
    }

    const handle = startCabinMicNoiseMonitor({
      onNoiseDb: (db) => setCabinNoiseDb(db),
      onStatusChange: (status) => setCabinMicStatus(status),
      sampleIntervalMs: 250,
    });

    return () => {
      handle.stop();
      setCabinNoiseDb(null);
    };
  }, [adaptiveCabinConfig.enabled, adaptiveCabinConfig.source]);

  // Automated Cabin Noise & Speed Profile State Machine Evaluator
  useEffect(() => {
    if (!adaptiveCabinConfig.enabled) {
      setAdaptiveCabinState({
        ...INITIAL_ADAPTIVE_STATE,
        triggerReason: 'disabled',
      });
      return;
    }

    const tick = () => {
      setAdaptiveCabinState((prev) =>
        evaluateAdaptiveCabinState(prev, adaptiveCabinConfig, {
          cabinNoiseDb: latestCabinNoiseRef.current,
          speedKmH: latestSpeedKmHRef.current,
          nowMs: Date.now(),
        })
      );
    };

    tick();
    const id = window.setInterval(tick, 400);
    return () => window.clearInterval(id);
  }, [adaptiveCabinConfig]);

  useEffect(() => {
    try {
      localStorage.setItem('zentryx_dsp_settings', JSON.stringify(dspSettings));
    } catch {}
    dspEngineRef.current?.applySettings(
      isHoldBypassed ? { ...effectiveDspSettings, dspEnabled: false } : effectiveDspSettings,
      isMuted ? 0 : volume,
      currentGpsSpeed
    );
  }, [dspSettings, effectiveDspSettings, volume, isMuted, isHoldBypassed, currentGpsSpeed]);

  useEffect(() => {
    try {
      localStorage.setItem('zentryx_dsp_custom_presets', JSON.stringify(customDspPresets));
    } catch {}
  }, [customDspPresets]);

  useEffect(() => {
    try {
      localStorage.setItem('zentryx_play_stats', JSON.stringify(playStats));
    } catch {}
  }, [playStats]);

  useEffect(() => {
    try {
      localStorage.setItem('zentryx_resume_map', JSON.stringify(resumableProgressMap));
    } catch {}
  }, [resumableProgressMap]);

  useEffect(() => {
    localStorage.setItem('zentryx_dual_pane_cockpit', dualPaneCockpit ? 'true' : 'false');
  }, [dualPaneCockpit]);

  useEffect(() => {
    localStorage.setItem('zentryx_drive_auto_speed', String(driveModeAutoSpeedKmH));
  }, [driveModeAutoSpeedKmH]);

  // Apply preset helper (shared by output device auto-profiles & auto-genre matching)
  const applyDspPresetById = useCallback(
    (presetId: string, base: DspSettings): DspSettings | null => {
      const preset = [...FACTORY_DSP_PRESETS, ...customDspPresets].find((p) => p.id === presetId);
      if (!preset) return null;
      return applyPresetToDsp(base, preset);
    },
    [customDspPresets]
  );

  // Per-Output Audio Device Auto-Profile detection (Headunit / Bluetooth A2DP / USB DAC)
  const detectAudioOutputRoute = useCallback(async () => {
    try {
      if (!navigator.mediaDevices?.enumerateDevices) return;
      const devices = await navigator.mediaDevices.enumerateDevices();
      const outputs = devices.filter((d) => d.kind === 'audiooutput');
      const labels = outputs.map((d) => (d.label || '').toLowerCase()).join(' | ');
      let detected: AudioOutputRouteId = 'headunit_speakers';
      if (/bluetooth|a2dp|hands-free|airpods|buds|car audio bt/.test(labels)) {
        detected = 'bluetooth_a2dp';
      } else if (/usb|dac|headphone|aux|line out|external/.test(labels)) {
        detected = 'usb_dac_aux';
      }
      setActiveOutputRoute((prev) => {
        if (prev !== detected && dspSettings.autoSwitchDevicePreset) {
          const targetPresetId = dspSettings.devicePresetMap?.[detected];
          if (targetPresetId) {
            setDspSettings((curr) => applyDspPresetById(targetPresetId, curr) || curr);
          }
        }
        return detected;
      });
    } catch {}
  }, [dspSettings.autoSwitchDevicePreset, dspSettings.devicePresetMap, applyDspPresetById]);

  useEffect(() => {
    detectAudioOutputRoute();
    const md = navigator.mediaDevices;
    if (!md?.addEventListener) return;
    md.addEventListener('devicechange', detectAudioOutputRoute);
    return () => md.removeEventListener('devicechange', detectAudioOutputRoute);
  }, [detectAudioOutputRoute]);

  const handleSelectOutputRoute = useCallback(
    (route: AudioOutputRouteId) => {
      setActiveOutputRoute(route);
      if (dspSettings.autoSwitchDevicePreset) {
        const mappedId = dspSettings.devicePresetMap?.[route];
        const applied = mappedId ? applyDspPresetById(mappedId, dspSettings) : null;
        if (applied) {
          setDspSettings(applied);
          const routeLabel = AUDIO_OUTPUT_ROUTES.find((r) => r.id === route)?.label || route;
          setStatusMessage(`Output switched to ${routeLabel} · Preset: ${mappedId.toUpperCase()}`);
          setTimeout(() => setStatusMessage(null), 2500);
        }
      }
    },
    [dspSettings, applyDspPresetById]
  );

  // Auto-Preset by Track Genre or Folder (respects per-track manual overrides)
  useEffect(() => {
    if (!currentTrack || !dspSettings.autoGenrePresetEnabled) return;
    if (manualOverrideTrackIdsRef.current.has(currentTrack.id)) return;
    const matchedId = matchPresetForTrack(currentTrack.genre, currentTrack.folderPath, currentTrack.title);
    if (!matchedId || matchedId === dspSettings.selectedPresetId) return;
    setDspSettings((prev) => applyDspPresetById(matchedId, prev) || prev);
  }, [currentTrack?.id, currentTrack?.genre, currentTrack?.folderPath, dspSettings.autoGenrePresetEnabled, applyDspPresetById]);

  // A/B Profile Compare & Momentary DSP Bypass Handlers
  const handleSnapshotSlotB = useCallback(() => {
    triggerHaptic();
    const snap = sanitizeDspSettings({ ...dspSettings, bands16: [...dspSettings.bands16] });
    setSlotBDspSnapshot(snap);
    try {
      localStorage.setItem('zentryx_dsp_slot_b', JSON.stringify(snap));
    } catch {}
    setStatusMessage('Snapshotted current DSP tune into Compare Slot B.');
    setTimeout(() => setStatusMessage(null), 2500);
  }, [dspSettings]);

  const handleToggleCompareSlot = useCallback(() => {
    triggerHaptic();
    if (!slotBDspSnapshot) {
      handleSnapshotSlotB();
      return;
    }
    if (activeCompareSlot === 'A') {
      slotADspSnapshotRef.current = sanitizeDspSettings({ ...dspSettings, bands16: [...dspSettings.bands16] });
      setDspSettings(sanitizeDspSettings(slotBDspSnapshot));
      setActiveCompareSlot('B');
    } else {
      const slotA = slotADspSnapshotRef.current;
      if (slotA) {
        setDspSettings(sanitizeDspSettings(slotA));
      }
      setActiveCompareSlot('A');
    }
  }, [activeCompareSlot, slotBDspSnapshot, dspSettings, handleSnapshotSlotB]);

  const handleDspSettingsFromPanel = useCallback(
    (updater: DspSettings | ((prev: DspSettings) => DspSettings)) => {
      if (currentTrack?.id) {
        manualOverrideTrackIdsRef.current.add(currentTrack.id);
      }
      setDspSettings((prev) => {
        const next = typeof updater === 'function' ? updater(prev) : updater;
        if (activeCompareSlot === 'B') {
          setSlotBDspSnapshot(next);
          try {
            localStorage.setItem('zentryx_dsp_slot_b', JSON.stringify(next));
          } catch {}
        } else {
          slotADspSnapshotRef.current = next;
        }
        return next;
      });
    },
    [currentTrack?.id, activeCompareSlot]
  );

  // Speed Auto-Engage Drive Mode HUD
  useEffect(() => {
    if (driveModeAutoSpeedKmH <= 0) return;
    if (currentGpsSpeed >= driveModeAutoSpeedKmH && isPlaying) {
      if (!driveModeDismissedAtSpeedRef.current && !isDriveModeOpen) {
        setIsDriveModeOpen(true);
      }
    } else if (currentGpsSpeed < Math.max(5, driveModeAutoSpeedKmH - 5)) {
      driveModeDismissedAtSpeedRef.current = false;
    }
  }, [currentGpsSpeed, driveModeAutoSpeedKmH, isPlaying, isDriveModeOpen]);

  // Hands-Free Web Speech API Voice Search ("play <query>" or filter query)
  const handleStartVoiceSearch = useCallback(() => {
    triggerHaptic();
    const SpeechRec = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (!SpeechRec) {
      setStatusMessage('Voice search is not supported in this browser — use Search or native Android APK.');
      setTimeout(() => setStatusMessage(null), 3500);
      return;
    }
    if (isVoiceListening && voiceRecognitionRef.current) {
      try {
        voiceRecognitionRef.current.stop();
      } catch {}
      setIsVoiceListening(false);
      return;
    }
    try {
      const recognition = new SpeechRec();
      recognition.lang = navigator.language || 'en-US';
      recognition.interimResults = false;
      recognition.maxAlternatives = 1;
      voiceRecognitionRef.current = recognition;
      setIsVoiceListening(true);
      setStatusMessage('Listening for voice command (say "Play <song>" or artist)...');

      recognition.onresult = (event: any) => {
        const transcript = (event?.results?.[0]?.[0]?.transcript || '').trim().replace(/[.!?]+$/, '');
        setIsVoiceListening(false);
        if (!transcript) return;
        const playMatch = transcript.match(/^(?:play|listen to|start)\s+(.+)$/i);
        const queryText = playMatch ? playMatch[1].trim() : transcript;
        setSearchQuery(queryText);
        setLibrarySearchQuery(queryText);

        if (playMatch && tracks.length > 0) {
          const matches = searchTracks(tracks, queryText);
          if (matches.length > 0) {
            const top = matches[0];
            const qIdx = queue.findIndex((t) => t.id === top.id);
            if (qIdx !== -1) {
              playTrackAtIndex(qIdx);
            } else {
              setQueue((prev) => [top, ...prev]);
              setCurrentTrackIndex(0);
              startAudioPlayback(top);
            }
            navigateToMenu({ tab: 'player' });
            setStatusMessage(`Voice playing "${top.title}"`);
            setTimeout(() => setStatusMessage(null), 3000);
            return;
          }
        }
        navigateToMenu({ tab: 'search' });
        setStatusMessage(`Voice search: "${queryText}"`);
        setTimeout(() => setStatusMessage(null), 3000);
      };
      recognition.onerror = () => {
        setIsVoiceListening(false);
        setStatusMessage('Voice capture cancelled or unavailable.');
        setTimeout(() => setStatusMessage(null), 2500);
      };
      recognition.onend = () => {
        setIsVoiceListening(false);
      };
      recognition.start();
    } catch {
      setIsVoiceListening(false);
      setStatusMessage('Could not start voice microphone.');
      setTimeout(() => setStatusMessage(null), 2500);
    }
  }, [isVoiceListening, tracks, queue, navigateToMenu]);

  // Boot Ignition Auto-Resume Restoration (runs BEFORE session snapshot writer so initial render never clobbers zentryx_last_session)
  useEffect(() => {
    if (bootResumeCheckedRef.current || tracks.length === 0) return;
    bootResumeCheckedRef.current = true;

    if (!autoResumeOnBoot) return;
    const session = parseResumeSession(localStorage.getItem('zentryx_last_session'));
    if (!session) return;

    const mapById = new Map(tracks.map((t) => [t.id, t]));
    const restoredQueue =
      session.queueIds.length > 0
        ? (session.queueIds.map((id) => mapById.get(id)).filter(Boolean) as Track[])
        : tracks;
    const activeQueue = restoredQueue.length > 0 ? restoredQueue : tracks;
    const targetIdx = activeQueue.findIndex((t) => t.id === session.trackId);
    if (targetIdx === -1) return;

    setQueue(activeQueue);
    setCurrentTrackIndex(targetIdx);
    setShuffle(session.shuffle);
    setRepeatMode(session.repeatMode);
    setPositionSeconds(session.positionSeconds);
    const targetTrack = activeQueue[targetIdx];
    if (targetTrack.durationSeconds) {
      setDurationSeconds(targetTrack.durationSeconds);
    }

    pendingResumeSeekRef.current = {
      trackId: targetTrack.id,
      positionSeconds: session.positionSeconds,
    };

    if (session.wasPlaying) {
      pendingSoftStartOnPlayRef.current = autoResumeSoftRamp;
      startAudioPlayback(targetTrack, session.positionSeconds).catch(() => {});
    } else if (autoResumeSoftRamp) {
      pendingSoftStartOnPlayRef.current = true;
    }
  }, [tracks.length, autoResumeOnBoot]);

  // Persist Last Ignition Session for Auto-Resume
  const saveSessionSnapshot = useCallback(
    (wasPlayingOverride?: boolean) => {
      if (!bootResumeCheckedRef.current || !currentTrack) return;
      try {
        const serialized = serializeResumeSession({
          trackId: currentTrack.id,
          positionSeconds,
          queueIds: queue.map((t) => t.id),
          shuffle,
          repeatMode,
          wasPlaying: wasPlayingOverride !== undefined ? wasPlayingOverride : isPlaying,
          savedAt: Date.now(),
        });
        localStorage.setItem('zentryx_last_session', serialized);
      } catch {}
    },
    [currentTrack, positionSeconds, queue, shuffle, repeatMode, isPlaying]
  );

  useEffect(() => {
    saveSessionSnapshot();
  }, [currentTrack?.id, isPlaying, shuffle, repeatMode, queue.length]);

  useEffect(() => {
    const onUnload = () => saveSessionSnapshot();
    window.addEventListener('beforeunload', onUnload);
    window.addEventListener('pagehide', onUnload);
    return () => {
      window.removeEventListener('beforeunload', onUnload);
      window.removeEventListener('pagehide', onUnload);
    };
  }, [saveSessionSnapshot]);

  // Idle Cluster Screensaver Timer (expands Visualizer Fullscreen while playing cleanly)
  useEffect(() => {
    const markInteraction = () => {
      lastInteractionRef.current = Date.now();
    };
    window.addEventListener('pointerdown', markInteraction, { passive: true });
    window.addEventListener('keydown', markInteraction, { passive: true });
    window.addEventListener('wheel', markInteraction, { passive: true });
    window.addEventListener('touchstart', markInteraction, { passive: true });
    return () => {
      window.removeEventListener('pointerdown', markInteraction);
      window.removeEventListener('keydown', markInteraction);
      window.removeEventListener('wheel', markInteraction);
      window.removeEventListener('touchstart', markInteraction);
    };
  }, []);

  useEffect(() => {
    if (!isPlaying || idleScreensaverSeconds <= 0) return;
    const interval = setInterval(() => {
      if (Date.now() - lastInteractionRef.current >= idleScreensaverSeconds * 1000) {
        setIsVisualizerOpen(true);
        setScreensaverActive(true);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [isPlaying, idleScreensaverSeconds]);

  // Smart Mixes & One-Tap Jump to Playing Track Handlers
  const handlePlaySmartMix = (mixType: SmartMixType) => {
    triggerHaptic();
    const mixTracks = buildSmartMixTracks(tracks, mixType, playStats, favorites, 40);
    if (mixTracks.length === 0) {
      setStatusMessage(
        mixType === 'recent'
          ? 'No recently played tracks yet. Start listening to build history!'
          : mixType === 'most_played'
          ? 'No play counts recorded yet.'
          : 'No tracks available for Cruising Mix.'
      );
      setTimeout(() => setStatusMessage(null), 3000);
      return;
    }
    const labels: Record<SmartMixType, string> = {
      cruising: 'Cruising Mix',
      recent: 'Recently Played Mix',
      most_played: 'Most Played Mix',
    };
    setQueue(mixTracks);
    setCurrentTrackIndex(0);
    setPositionSeconds(0);
    startAudioPlayback(mixTracks[0]);
    setActiveTab('player');
    setStatusMessage(`${labels[mixType]} started (${mixTracks.length} tracks)`);
    setTimeout(() => setStatusMessage(null), 3000);
  };

  const handleJumpToPlayingTrack = () => {
    if (!currentTrack) return;
    triggerHaptic();
    setLibraryFilter('all');
    setSelectedAlbum(null);
    setSelectedArtist(null);
    setSearchQuery('');
    const idx = tracks.findIndex((t) => t.id === currentTrack.id);
    if (idx !== -1) {
      setTimeout(() => {
        setLibraryScrollRequest({ index: idx, nonce: Date.now() });
      }, 20);
    }
  };

  return (
    <div
      className={`min-h-screen text-slate-100 flex flex-col font-sans select-none selection:bg-[#a3e635] selection:text-black transition-colors duration-300 ${
        isDaylightCockpit ? 'bg-[#0f172a]' : 'bg-[#020617]'
      }`}
    >
      {/* Hidden Real HTML5 Audio Element without CORS restrictions */}
      <audio
        ref={audioRef}
        preload="metadata"
        onPlay={() => {
          setIsPlaying(true);
          ensureAudioAnalyser();
        }}
        onPause={() => setIsPlaying(false)}
        onTimeUpdate={() => {
          if (audioRef.current) {
            const exactCur = audioRef.current.currentTime;
            const dur = durationSeconds || (!isNaN(audioRef.current.duration) ? audioRef.current.duration : 0);

            // Smooth crossfade envelope in final seconds (before 1 Hz UI throttle)
            if (
              crossfadeSeconds > 0 &&
              currentTrack &&
              repeatMode !== 'ONE' &&
              dur > crossfadeSeconds + 2 &&
              resolveNextIndex(queue.length, currentTrackIndex, shuffle, repeatMode) !== -1
            ) {
              const remaining = dur - exactCur;
              if (remaining <= crossfadeSeconds && remaining > 0.25) {
                const ratio = 1 - remaining / crossfadeSeconds;
                setCrossfadeGain(calculateCrossfadeGains(ratio).outgoingGain);
              } else if (remaining <= 0.25 && crossfadeTriggeredForTrackRef.current !== currentTrack.id) {
                crossfadeTriggeredForTrackRef.current = currentTrack.id;
                handleNextTrack(true);
                return;
              }
            }

            const cur = Math.floor(exactCur);
            // Throttle React state updates to 1 Hz to eliminate redundant re-renders on MediaTek Octa-Core
            if (cur !== lastTimeUpdateSecRef.current) {
              lastTimeUpdateSecRef.current = cur;
              setPositionSeconds(cur);

              // Auto-bookmark timestamp & ignition resume snapshot every 5 seconds
              if (currentTrack && cur > 0 && cur % 5 === 0) {
                saveTrackProgress(currentTrack.id, cur, durationSeconds);
                setResumableProgressMap((prev) => ({
                  ...prev,
                  [currentTrack.id]: {
                    positionSeconds: cur,
                    durationSeconds: durationSeconds || currentTrack.durationSeconds || 0,
                    updatedAt: Date.now(),
                  },
                }));
                try {
                  localStorage.setItem(
                    'zentryx_last_session',
                    serializeResumeSession({
                      trackId: currentTrack.id,
                      positionSeconds: cur,
                      queueIds: queue.map((t) => t.id),
                      shuffle,
                      repeatMode,
                      wasPlaying: isPlaying,
                      savedAt: Date.now(),
                    })
                  );
                } catch {}
              }

              // Smart Next-Track Pre-caching (at >80% completion or last 25s)
              if (durationSeconds > 15 && (cur / durationSeconds > 0.8 || durationSeconds - cur < 25)) {
                prefetchNextTrackChunk();
              }
            }
          }
        }}
        onLoadedMetadata={() => {
          if (audioRef.current && !isNaN(audioRef.current.duration)) {
            const dur = Math.floor(audioRef.current.duration);
            setDurationSeconds(dur);
            if (
              pendingResumeSeekRef.current &&
              currentTrack &&
              pendingResumeSeekRef.current.trackId === currentTrack.id
            ) {
              const resumePos = Math.min(dur > 1 ? dur - 1 : dur, pendingResumeSeekRef.current.positionSeconds);
              if (resumePos > 0) {
                audioRef.current.currentTime = resumePos;
                setPositionSeconds(Math.floor(resumePos));
              }
              pendingResumeSeekRef.current = null;
            }
            // Immutably hydrate duration & bitrate into tracks and queue state
            if (currentTrack && dur > 0) {
              const estBitrate = estimateBitrateKbps(currentTrack.sizeBytes, dur);
              applyEnrichedMetadata(currentTrack.id, { bitrateKbps: estBitrate }, dur);
            }
          }
        }}
        onEnded={() => handleNextTrack(crossfadeSeconds > 0)}
        onError={(e) => {
          const err = audioRef.current?.error;
          console.warn('Audio element notice:', err ? `${err.code}: ${err.message}` : e);
        }}
      />

      {/* Sleek Minimalist Top Navigation */}
      <header className="border-b border-slate-800/80 bg-[#020617]/90 backdrop-blur sticky top-0 z-40">
        <div className="headunit-header-bar max-w-6xl mx-auto px-3 sm:px-4 min-h-16 py-2 flex flex-wrap items-center justify-between gap-2">
          {/* Logo & Brand + Headunit Menu Back/Forward Buttons */}
          <div className="flex items-center gap-2 sm:gap-2.5 shrink-0">
            <div className="flex items-center gap-1 mr-1">
              <button
                type="button"
                onClick={handleMenuBack}
                disabled={!canMenuGoBack}
                aria-label="Navigate Back"
                title="Back to previous menu"
                className={`w-9 h-9 rounded-xl flex items-center justify-center border transition-all active:scale-95 ${
                  canMenuGoBack
                    ? 'bg-[#0b1120] border-slate-700 text-white hover:border-slate-500 hover:bg-slate-800'
                    : 'bg-[#0b1120]/50 border-slate-800/60 text-slate-600 cursor-not-allowed'
                }`}
              >
                <ChevronLeft className="w-4 h-4" />
              </button>
              <button
                type="button"
                onClick={handleMenuForward}
                disabled={!canMenuGoForward}
                aria-label="Navigate Forward"
                title="Forward to next menu"
                className={`w-9 h-9 rounded-xl flex items-center justify-center border transition-all active:scale-95 ${
                  canMenuGoForward
                    ? 'bg-[#0b1120] border-slate-700 text-white hover:border-slate-500 hover:bg-slate-800'
                    : 'bg-[#0b1120]/50 border-slate-800/60 text-slate-600 cursor-not-allowed'
                }`}
              >
                <ChevronRight className="w-4 h-4" />
              </button>
            </div>
            <div
              className="w-8 h-8 sm:w-9 sm:h-9 rounded-xl flex items-center justify-center font-bold text-black shadow-md transition-colors"
              style={{ backgroundColor: activeAccent.hex }}
            >
              <Music className="w-4 h-4 sm:w-5 sm:h-5 stroke-[2.5]" />
            </div>
            <div>
              <span className="font-extrabold text-base sm:text-lg tracking-tight text-white whitespace-nowrap">
                ZENTRYX <span style={{ color: activeAccent.hex }}>MUSIC</span>
              </span>
            </div>
          </div>

          {/* Clean Navigation Tabs */}
          <nav className="order-3 lg:order-2 w-full lg:w-auto flex items-center justify-between lg:justify-start bg-[#0b1120] p-1 rounded-xl border border-slate-800 text-xs font-semibold overflow-x-auto">
            <button
              onClick={() => navigateToMenu({ tab: 'player' })}
              className={`px-3.5 py-1.5 rounded-lg transition-all ${
                activeTab === 'player' ? 'text-black font-bold shadow' : 'text-slate-400 hover:text-white'
              }`}
              style={activeTab === 'player' ? { backgroundColor: activeAccent.hex } : {}}
            >
              Player
            </button>

            <button
              onClick={() => {
                setSelectedPlaylist(null);
                navigateToMenu({
                  tab: 'library',
                  libraryFilter: 'all',
                  selectedAlbum: null,
                  selectedArtist: null,
                });
              }}
              className={`px-3.5 py-1.5 rounded-lg transition-all ${
                activeTab === 'library' ? 'text-black font-bold shadow' : 'text-slate-400 hover:text-white'
              }`}
              style={activeTab === 'library' ? { backgroundColor: activeAccent.hex } : {}}
            >
              Library {tracks.length > 0 ? `(${tracks.length})` : ''}
            </button>

            <button
              onClick={() => {
                triggerHaptic();
                setIsPlaylistsOpen(true);
              }}
              className="px-3 py-1.5 rounded-lg text-slate-400 hover:text-white transition-all flex items-center gap-1.5"
            >
              <FolderPlus className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
              <span className="hidden sm:inline">Playlists</span> ({playlists.length})
            </button>

            <button
              onClick={() => navigateToMenu({ tab: 'search' })}
              className={`px-3.5 py-1.5 rounded-lg transition-all ${
                activeTab === 'search' ? 'text-black font-bold shadow' : 'text-slate-400 hover:text-white'
              }`}
              style={activeTab === 'search' ? { backgroundColor: activeAccent.hex } : {}}
            >
              Search
            </button>

            <button
              onClick={() => navigateToMenu({ tab: 'settings' })}
              className={`px-3.5 py-1.5 rounded-lg transition-all ${
                activeTab === 'settings' ? 'text-black font-bold shadow' : 'text-slate-400 hover:text-white'
              }`}
              style={activeTab === 'settings' ? { backgroundColor: activeAccent.hex } : {}}
            >
              Settings
            </button>
          </nav>

          {/* Quick Vehicle Status & Actions */}
          <div className="order-2 lg:order-3 flex items-center gap-1.5 sm:gap-2 shrink-0">
            {/* Hotspot Roaming Status */}
            <div
              className="hidden sm:flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl bg-[#0b1120] border border-slate-800 text-[11px] text-slate-400"
              title={isOnline ? 'Vehicle Hotspot Connected' : 'Hotspot Roaming Disconnected (Auto-Reconnect Armed)'}
            >
              {isOnline ? (
                <Wifi className="w-3.5 h-3.5 text-lime-400" />
              ) : (
                <WifiOff className="w-3.5 h-3.5 text-red-400 animate-pulse" />
              )}
              <span className="hidden md:inline font-mono">{isOnline ? 'Hotspot OK' : 'Roaming'}</span>
            </div>

            {/* Queue Toggle Button */}
            <button
              onClick={() => {
                triggerHaptic();
                setIsQueueOpen(!isQueueOpen);
              }}
              className="p-2 rounded-xl bg-[#0b1120] hover:bg-slate-800 border border-slate-800 text-slate-300 hover:text-white transition-all active:scale-95 relative"
              title="Open Up-Next Queue (Q)"
            >
              <ListMusic className="w-4 h-4" style={{ color: activeAccent.hex }} />
              {queue.length > 0 && (
                <span className="absolute -top-1 -right-1 text-[9px] font-bold bg-black border border-slate-700 px-1 rounded-full">
                  {queue.length}
                </span>
              )}
            </button>

            {/* Hands-Free Voice Search */}
            <button
              type="button"
              onClick={handleStartVoiceSearch}
              className={`p-2 rounded-xl border transition-all active:scale-95 ${
                isVoiceListening
                  ? 'bg-red-500/20 border-red-500 text-red-400 animate-pulse'
                  : 'bg-[#0b1120] hover:bg-slate-800 border-slate-800 text-slate-300 hover:text-white'
              }`}
              title='Voice Search (say "Play <song>")'
            >
              <Mic className="w-4 h-4" style={!isVoiceListening ? { color: activeAccent.hex } : undefined} />
            </button>

            {/* Simplified Drive Mode HUD Toggle */}
            <button
              type="button"
              onClick={() => {
                triggerHaptic();
                driveModeDismissedAtSpeedRef.current = false;
                setIsDriveModeOpen(true);
              }}
              className="hidden sm:flex items-center gap-1.5 px-2.5 py-1.5 rounded-xl bg-[#0b1120] hover:bg-slate-800 border border-slate-800 text-xs font-bold text-slate-200 hover:text-white transition-all active:scale-95"
              title="Launch High-Contrast Simplified Drive HUD"
            >
              <Car className="w-4 h-4" style={{ color: activeAccent.hex }} />
              <span className="hidden md:inline">Drive</span>
            </button>

            {/* Download Native Android APK */}
            <AndroidApkDownloadButton accentHex={activeAccent.hex} compact />

            {/* Install Headunit PWA Button */}
            <PWAInstallButton accentHex={activeAccent.hex} compact />

            {/* Fullscreen Kiosk Mode Toggle */}
            <button
              onClick={toggleFullscreen}
              className="p-2 rounded-xl bg-[#0b1120] hover:bg-slate-800 border border-slate-800 text-slate-300 hover:text-white transition-all active:scale-95"
              title={isFullscreen ? 'Exit Fullscreen' : 'Fullscreen Kiosk Mode (F)'}
            >
              {isFullscreen ? <Minimize2 className="w-4 h-4" /> : <Maximize2 className="w-4 h-4" />}
            </button>

            {/* Account Status Pill */}
            <button
              onClick={() => setShowLoginModal(true)}
              className="flex items-center gap-2 bg-[#0b1120] hover:bg-slate-800/80 px-3 py-1.5 rounded-xl border border-slate-800 text-xs transition-colors"
            >
              <span
                className="w-2 h-2 rounded-full"
                style={{ backgroundColor: isLoggedIn ? activeAccent.hex : '#f59e0b' }}
              />
              <span className="text-slate-300 font-medium truncate max-w-[100px]">
                {isLoggedIn ? username : 'Sign In'}
              </span>
            </button>
          </div>
        </div>
      </header>

      {/* Global Status Banner */}
      {statusMessage && (
        <div className="bg-[#0b1120] border-b border-slate-800 px-4 py-2 text-center text-xs text-lime-400 font-medium">
          {statusMessage}
        </div>
      )}

      {/* Main Viewport */}
      <main
        className={`headunit-main-viewport flex-1 min-w-0 ${
          dualPaneCockpit ? 'max-w-7xl' : 'max-w-6xl'
        } w-full mx-auto px-4 py-6 flex flex-col justify-center`}
      >
        {/* VIEW 1: PLAYER SCREEN (MBUX COCKPIT + OPTIONAL DUAL-PANE WIDESCREEN) */}
        {activeTab === 'player' && (
          <div className={`w-full mx-auto ${dualPaneCockpit && tracks.length > 0 ? 'max-w-7xl grid grid-cols-1 xl:grid-cols-12 gap-6 items-start' : 'max-w-4xl'}`}>
            <div className={dualPaneCockpit && tracks.length > 0 ? 'xl:col-span-8' : 'w-full'}>
            {tracks.length === 0 ? (
              /* Empty State: Prompt User to Sync or Sign In */
              <div
                className="bg-[#0b1120] border rounded-3xl p-8 sm:p-12 text-center shadow-2xl relative overflow-hidden space-y-5"
                style={{ borderColor: activeAccent.border }}
              >
                <div
                  className="w-20 h-20 rounded-3xl mx-auto flex items-center justify-center border"
                  style={{ backgroundColor: activeAccent.bg, borderColor: activeAccent.border }}
                >
                  <Music className="w-10 h-10" style={{ color: activeAccent.hex }} />
                </div>

                <div className="space-y-2 max-w-md mx-auto">
                  <h2 className="text-xl font-bold text-white">No Nextcloud Songs Loaded</h2>
                  <p className="text-xs text-slate-400 leading-relaxed">
                    {isLoggedIn
                      ? `Connected as ${username} to cloud.zentryx.bg. Default folders: ${parseScanFolders(musicFolder, extraScanFolders).map((f) => `/${f || 'Root'}`).join(', ')}.`
                      : 'Sign in with your Nextcloud account to stream your personal music collection directly in your cockpit.'}
                  </p>
                </div>

                <div className="flex items-center justify-center gap-3 pt-2 flex-wrap">
                  {isLoggedIn ? (
                    <>
                      <button
                        onClick={() => openFolderBrowser('')}
                        className="px-5 py-3 rounded-2xl font-bold text-xs uppercase tracking-wider text-black flex items-center gap-2 transition-all shadow-lg active:scale-95"
                        style={{ backgroundColor: activeAccent.hex }}
                      >
                        <FolderOpen className="w-4 h-4" />
                        Browse &amp; Add Folder
                      </button>

                      <button
                        onClick={() => scanNextcloudMusic(username, password, musicFolder, true)}
                        disabled={isScanning}
                        className="px-4 py-3 rounded-2xl font-bold text-xs text-white bg-slate-800 hover:bg-slate-700 border border-slate-700 flex items-center gap-2 transition-colors"
                      >
                        <RefreshCw className={`w-4 h-4 ${isScanning ? 'animate-spin' : ''}`} />
                        {isScanning ? scanProgress || 'Scanning...' : 'Scan WoW Radio & Music'}
                      </button>

                      <button
                        onClick={() => scanNextcloudMusic(username, password, '', true)}
                        disabled={isScanning}
                        className="px-4 py-3 rounded-2xl font-bold text-xs text-slate-300 bg-black/60 border border-slate-800 hover:border-slate-700 flex items-center gap-2 transition-colors"
                      >
                        <FolderSearch className="w-4 h-4" />
                        Scan Root (/)
                      </button>
                    </>
                  ) : (
                    <button
                      onClick={() => setShowLoginModal(true)}
                      className="px-6 py-3 rounded-2xl font-bold text-xs uppercase tracking-wider text-black flex items-center gap-2 transition-all shadow-lg active:scale-95"
                      style={{ backgroundColor: activeAccent.hex }}
                    >
                      <Cloud className="w-4 h-4" />
                      Sign In to Nextcloud
                    </button>
                  )}
                </div>
              </div>
            ) : (
              /* Active Player Cockpit */
              <div
                onTouchStart={handleTouchStart}
                onTouchEnd={handleTouchEnd}
                className={`headunit-player-card border rounded-3xl p-6 sm:p-8 shadow-2xl relative overflow-hidden ${
                  isDaylightCockpit ? 'bg-[#162033]' : 'bg-[#0b1120]'
                }`}
                style={{ borderColor: activeAccent.border }}
              >
                {/* Floating Gesture Notice (Swipe Left/Right, 2-Finger Tap) */}
                {gestureNotice && (
                  <div className="absolute top-4 left-1/2 -translate-x-1/2 z-30 px-4 py-2 rounded-2xl bg-black/90 border border-slate-700 text-white font-extrabold text-xs tracking-wider shadow-2xl flex items-center gap-2 animate-in fade-in zoom-in-95 duration-150">
                    <Sparkles className="w-4 h-4" style={{ color: activeAccent.hex }} />
                    <span>{gestureNotice}</span>
                  </div>
                )}

                <div className="headunit-player-grid grid grid-cols-1 md:grid-cols-12 gap-8 items-center">
                  {/* Left Pane (5/12): Album Art with Embedded ID3 Support or Real Audio Visualizer */}
                  <div className="md:col-span-5 flex flex-col items-center justify-center text-center">
                    {isVisualizerOpen ? (
                      <div className="headunit-artwork-box w-64 h-64 sm:w-72 sm:h-72 rounded-3xl bg-gradient-to-b from-[#101726] to-[#05080f] border-2 border-slate-700/80 p-2 shadow-[inset_0_1px_4px_rgba(255,255,255,0.15),0_12px_30px_rgba(0,0,0,0.85)] relative flex flex-col justify-center overflow-hidden">
                        <AudioVisualizer
                          isPlaying={isPlaying}
                          accentHex={activeAccent.hex}
                          analyserNode={activeAnalyserNode}
                          trackTitle={currentTrack?.title}
                          trackArtist={currentTrack?.artist}
                          trackAlbum={currentTrack?.album}
                          positionSeconds={positionSeconds}
                          durationSeconds={durationSeconds}
                          currentSpeed={currentGpsSpeed}
                          ecoMode={mediatekEcoMode}
                          onTogglePlay={togglePlayPause}
                          onPrevTrack={handlePrevTrack}
                          onNextTrack={() => handleNextTrack()}
                          onClose={() => {
                            lastInteractionRef.current = Date.now();
                            setScreensaverActive(false);
                            setIsVisualizerOpen(false);
                          }}
                          externalFullscreen={screensaverActive}
                          onExternalFullscreenExit={() => {
                            lastInteractionRef.current = Date.now();
                            setScreensaverActive(false);
                          }}
                        />
                      </div>
                    ) : (
                      <div
                        className="headunit-artwork-box w-64 h-64 sm:w-72 sm:h-72 rounded-3xl bg-gradient-to-b from-[#131d33] to-[#070b14] border flex flex-col items-center justify-center shadow-2xl relative overflow-hidden group"
                        style={{ borderColor: activeAccent.border }}
                      >
                        <CrossfadeAlbumArt
                          src={currentTrack ? coverArtUrls[currentTrack.id] : null}
                          alt={currentTrack?.title || 'Album Artwork'}
                          trackKey={currentTrack?.id}
                          durationMs={300}
                          className="w-full h-full"
                          imgClassName="w-full h-full object-cover transition-transform duration-700 group-hover:scale-105"
                          fallback={
                            <div className="relative flex items-center justify-center">
                              <Disc
                                className={`w-28 h-28 opacity-75 transition-transform duration-1000 ${
                                  isPlaying ? 'animate-spin' : ''
                                }`}
                                style={{
                                  color: activeAccent.hex,
                                  animationDuration: '6s',
                                }}
                              />
                              <div
                                className="absolute w-8 h-8 rounded-full border-2 border-black/80 flex items-center justify-center"
                                style={{ backgroundColor: activeAccent.hex }}
                              >
                                <div className="w-2.5 h-2.5 rounded-full bg-black" />
                              </div>
                            </div>
                          }
                        />

                        {/* Top Badges */}
                        <div className="absolute top-3 left-3 right-3 flex items-center justify-between pointer-events-none">
                          {currentTrack && offlineTrackIds.has(currentTrack.id) ? (
                            <span className="px-2.5 py-1 rounded-full bg-emerald-500/90 text-black font-extrabold text-[10px] tracking-wider uppercase shadow flex items-center gap-1">
                              <Check className="w-3 h-3 stroke-[3]" />
                              Offline Ready
                            </span>
                          ) : (
                            <span className="px-2.5 py-1 rounded-full bg-black/75 backdrop-blur border border-slate-800 text-[10px] font-mono text-slate-300">
                              Nextcloud Stream
                            </span>
                          )}

                          {(currentTrack?.codec || currentTrack?.contentType) && (
                            <span className="px-2 py-0.5 rounded-full bg-black/75 backdrop-blur border border-slate-800 text-[10px] font-mono text-slate-300">
                              {currentTrack.codec || currentTrack.contentType?.replace('audio/', '').toUpperCase()}
                            </span>
                          )}
                        </div>

                        {currentTrack && (
                          <div className="absolute bottom-3 px-3 py-1 rounded-full bg-black/80 backdrop-blur border border-slate-800 text-[11px] font-mono text-slate-300 flex items-center gap-1.5 shadow">
                            <FileAudio className="w-3 h-3 text-slate-400" />
                            <span>{currentTrack.sizeFormatted}</span>
                            {currentTrack.bitrateKbps && (
                              <>
                                <span className="text-slate-600">•</span>
                                <span>{currentTrack.bitrateKbps} kbps</span>
                              </>
                            )}
                            {currentTrack.sampleRateHz && (
                              <>
                                <span className="text-slate-600">•</span>
                                <span>{(currentTrack.sampleRateHz / 1000).toFixed(1)} kHz</span>
                              </>
                            )}
                          </div>
                        )}
                      </div>
                    )}

                    {/* Quick Toggles under Art */}
                    <div className="flex items-center gap-2 mt-4 flex-wrap justify-center">
                      <button
                        onClick={() => {
                          triggerHaptic();
                          setIsVisualizerOpen(!isVisualizerOpen);
                        }}
                        className={`px-3 py-2 rounded-xl text-xs font-semibold border transition-all flex items-center gap-1.5 active:scale-95 ${
                          isVisualizerOpen ? 'bg-black text-white shadow-lg' : 'text-slate-400 border-slate-800 hover:text-white bg-slate-900/60'
                        }`}
                        style={isVisualizerOpen ? { borderColor: activeAccent.hex, color: activeAccent.hex } : {}}
                        title="Toggle Live Audio Visualizer"
                      >
                        <Activity className="w-3.5 h-3.5" />
                        Spectrum
                      </button>

                      <button
                        onClick={() => {
                          triggerHaptic();
                          setIsQueueOpen(true);
                        }}
                        className="px-3 py-2 rounded-xl text-xs font-semibold border border-slate-800 bg-slate-900/60 text-slate-300 hover:text-white transition-all flex items-center gap-1.5 active:scale-95 shadow"
                        title="Open Up-Next Play Queue"
                      >
                        <ListMusic className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                        Queue ({queue.length})
                      </button>

                      {currentTrack && (
                        <button
                          onClick={() => {
                            triggerHaptic();
                            setTrackForPlaylist(currentTrack);
                            setIsPlaylistsOpen(true);
                          }}
                          className="px-3 py-2 rounded-xl text-xs font-semibold border border-slate-800 bg-slate-900/60 text-slate-300 hover:text-white transition-all flex items-center gap-1.5 active:scale-95 shadow"
                          title="Add to Playlist"
                        >
                          <Plus className="w-3.5 h-3.5 text-lime-400" />
                          Playlist
                        </button>
                      )}

                      {currentTrack && (
                        <button
                          onClick={() => {
                            triggerHaptic();
                            toggleFavorite(currentTrack.id);
                          }}
                          className="p-2 rounded-xl border border-slate-800 bg-slate-900/60 hover:border-slate-700 transition-all active:scale-95"
                          style={{ color: favorites.has(currentTrack.id) ? activeAccent.hex : '#64748b' }}
                          title={favorites.has(currentTrack.id) ? 'Remove Favorite' : 'Add Favorite'}
                        >
                          <Heart
                            className="w-4 h-4"
                            fill={favorites.has(currentTrack.id) ? activeAccent.hex : 'none'}
                          />
                        </button>
                      )}

                      {currentTrack && (
                        <button
                          onClick={() => {
                            triggerHaptic();
                            downloadTrackForOffline(currentTrack);
                          }}
                          className="p-2 rounded-xl border border-slate-800 bg-slate-900/60 hover:border-slate-700 transition-all active:scale-95"
                          style={{ color: offlineTrackIds.has(currentTrack.id) ? activeAccent.hex : '#94a3b8' }}
                          title={offlineTrackIds.has(currentTrack.id) ? 'Saved for Offline Roadtrips (Cached)' : 'Download Track for Offline Roadtrips'}
                        >
                          {downloadingId === currentTrack.id ? (
                            <RefreshCw className="w-4 h-4 animate-spin text-lime-400" />
                          ) : offlineTrackIds.has(currentTrack.id) ? (
                            <CheckCircle className="w-4 h-4" />
                          ) : (
                            <Download className="w-4 h-4" />
                          )}
                        </button>
                      )}
                    </div>
                  </div>

                  {/* Right Pane (7/12): Driving Typography, Seekbar, Fitts's Law Transport Controls */}
                  <div className="md:col-span-7 flex flex-col justify-center space-y-5">
                    {/* Track Info & Dynamic Cockpit Badges */}
                    <div>
                      <div className="flex items-center gap-2 text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1.5 flex-wrap">
                        <Cloud className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                        <span>cloud.zentryx.bg</span>
                        <span className="text-slate-600">•</span>
                        <span>Track {currentTrackIndex + 1} of {queue.length}</span>

                        {/* Active Dynamic Badges */}
                        {isAttenuated && (
                          <span className="px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 font-bold text-[10px] animate-pulse border border-amber-500/40">
                            DUCK (-20dB)
                          </span>
                        )}

                        {playbackRate !== 1.0 && (
                          <span className="px-2 py-0.5 rounded-full bg-blue-500/20 text-blue-300 font-mono text-[10px] border border-blue-500/30">
                            {playbackRate}x Speed
                          </span>
                        )}

                        {sleepTimerRemainingSec !== null && sleepTimerRemainingSec > 0 && (
                          <span className="px-2 py-0.5 rounded-full bg-purple-500/20 text-purple-300 font-mono text-[10px] border border-purple-500/30 flex items-center gap-1">
                            <Clock className="w-3 h-3" />
                            {Math.ceil(sleepTimerRemainingSec / 60)}m sleep
                          </span>
                        )}

                        {dspSettings.dspEnabled && (
                          <span className="px-2 py-0.5 rounded-full bg-lime-500/20 text-lime-400 font-mono text-[10px] border border-lime-500/30 whitespace-nowrap">
                            DSP ACTIVE · {dspSettings.selectedPresetId === 'custom' ? 'CUSTOM' : dspSettings.selectedPresetId.toUpperCase()}
                          </span>
                        )}

                        {replayGainEnabled && currentTrack?.replayGainDb !== undefined && (
                          <span className="px-2 py-0.5 rounded-full bg-cyan-500/15 text-cyan-300 font-mono text-[10px] border border-cyan-500/30">
                            RG {currentTrack.replayGainDb > 0 ? `+${currentTrack.replayGainDb.toFixed(1)}` : currentTrack.replayGainDb.toFixed(1)} dB
                          </span>
                        )}

                        {crossfadeSeconds > 0 && (
                          <span className="px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 font-mono text-[10px] border border-slate-700">
                            XFADE {crossfadeSeconds}s
                          </span>
                        )}

                        {driverSafeActive && (
                          <span className="px-2 py-0.5 rounded-full bg-amber-500/20 text-amber-300 font-mono text-[10px] border border-amber-500/40 flex items-center gap-1">
                            <Lock className="w-3 h-3" />
                            SAFE UI ({currentGpsSpeed} km/h)
                          </span>
                        )}
                      </div>

                      <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight leading-snug line-clamp-2" title={currentTrack?.title}>
                        {currentTrack?.title || 'No Track Selected'}
                      </h1>
                      <p className="text-base text-slate-300 font-medium mt-1 truncate">
                        {currentTrack?.artist || 'Unknown Artist'} <span className="text-slate-600">•</span>{' '}
                        {currentTrack?.album || (currentTrack?.sourceType === 'LOCAL' ? 'USB / Local Audio' : 'Nextcloud Music')}
                        {currentTrack?.year ? ` (${currentTrack.year})` : ''}
                      </p>
                      {currentTrack && (
                        <button
                          type="button"
                          onClick={() => {
                            triggerHaptic();
                            setIsStreamInspectorOpen(true);
                          }}
                          title="Tap to inspect full audio stream, codec & DSP signal path"
                          className="mt-2 inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-black/50 hover:bg-slate-900 border border-slate-800/80 hover:border-slate-600 text-[11px] font-mono text-slate-300 transition-colors active:scale-95"
                        >
                          <Info className="w-3 h-3 shrink-0" style={{ color: activeAccent.hex }} />
                          <span style={{ color: activeAccent.hex }} className="font-bold">
                            {currentTrack.codec || currentTrack.fileName.split('.').pop()?.toUpperCase() || 'AUDIO'}
                          </span>
                          {currentTrack.sampleRateHz && (
                            <>
                              <span className="text-slate-600">·</span>
                              <span>
                                {currentTrack.sampleRateHz % 1000 === 0
                                  ? `${currentTrack.sampleRateHz / 1000} kHz`
                                  : `${(currentTrack.sampleRateHz / 1000).toFixed(1)} kHz`}
                              </span>
                            </>
                          )}
                          {currentTrack.bitrateKbps && (
                            <>
                              <span className="text-slate-600">·</span>
                              <span>{currentTrack.bitrateKbps} kbps</span>
                            </>
                          )}
                          <span className="text-slate-600">·</span>
                          <span>{currentTrack.sourceType === 'LOCAL' ? 'USB/LOCAL' : 'WEBDAV'}</span>
                          {replayGainEnabled && currentTrack.replayGainDb !== undefined && (
                            <>
                              <span className="text-slate-600">·</span>
                              <span className="text-cyan-300">
                                RG {currentTrack.replayGainDb >= 0 ? `+${currentTrack.replayGainDb.toFixed(1)}` : currentTrack.replayGainDb.toFixed(1)}dB
                              </span>
                            </>
                          )}
                        </button>
                      )}
                    </div>

                    {playbackError && (
                      <div className="flex items-center gap-2 p-3 bg-red-950/40 border border-red-800/40 rounded-2xl text-xs text-red-400 shadow">
                        <AlertCircle className="w-4 h-4 shrink-0" />
                        <span>{playbackError}</span>
                      </div>
                    )}

                    {/* High-Contrast In-Car Seekbar */}
                    <div className="space-y-2">
                      <input
                        type="range"
                        min={0}
                        max={durationSeconds || 100}
                        value={positionSeconds}
                        onChange={(e) => {
                          const newPos = Number(e.target.value);
                          seekTo(newPos);
                        }}
                        className="w-full h-2.5 bg-slate-800 rounded-lg cursor-pointer"
                        style={{ accentColor: activeAccent.hex }}
                      />
                      <div className="flex justify-between text-xs text-slate-300 font-mono font-semibold">
                        <span>{formatTime(positionSeconds)}</span>
                        <span>{formatTime(durationSeconds)}</span>
                      </div>
                    </div>

                    {/* Fitts's Law Cockpit Transport Controls */}
                    <div className="flex items-center justify-center gap-3 sm:gap-5 pt-1 flex-wrap">
                      {/* Shuffle Button (48px) */}
                      <button
                        onClick={() => {
                          triggerHaptic();
                          setShuffle(!shuffle);
                        }}
                        className="w-12 h-12 rounded-2xl bg-black/50 border border-slate-800 hover:border-slate-700 flex items-center justify-center transition-all active:scale-95 shadow shrink-0"
                        style={shuffle ? { color: activeAccent.hex, borderColor: activeAccent.hex } : { color: '#64748b' }}
                        title={shuffle ? 'Shuffle Active' : 'Shuffle Off'}
                      >
                        <Shuffle className="w-5 h-5" />
                      </button>

                      {/* Podcast -15s Replay Button (When Podcast Mode is active) */}
                      {isPodcastMode && (
                        <button
                          onClick={() => {
                            triggerHaptic();
                            seekTo(Math.max(0, positionSeconds - 15));
                          }}
                          className="w-12 h-12 rounded-2xl bg-slate-900 border border-slate-700 hover:border-slate-500 text-white flex flex-col items-center justify-center transition-all active:scale-95 shadow shrink-0"
                          title="Replay 15 Seconds"
                        >
                          <RotateCcw className="w-4 h-4 text-slate-300" />
                          <span className="text-[9px] font-mono font-bold text-slate-400">15s</span>
                        </button>
                      )}

                      {/* Previous Track */}
                      <button
                        onClick={handlePrevTrack}
                        className={`${
                          driverSafeActive ? 'w-16 h-16' : 'w-14 h-14'
                        } rounded-2xl bg-slate-900/90 hover:bg-slate-800 text-white flex items-center justify-center transition-all active:scale-95 border border-slate-700/80 shadow-lg shrink-0`}
                        title="Previous Track (Steering / Headunit)"
                      >
                        <SkipBack className="w-6 h-6" />
                      </button>

                      {/* Central Play/Pause */}
                      <button
                        onClick={togglePlayPause}
                        className={`${
                          driverSafeActive ? 'w-24 h-24 ring-2 ring-white/40' : 'w-20 h-20'
                        } rounded-3xl text-black flex items-center justify-center transition-all shadow-2xl active:scale-95 shrink-0`}
                        style={{
                          backgroundColor: activeAccent.hex,
                          boxShadow: `0 0 30px ${activeAccent.hex}44`,
                        }}
                        title={isPlaying ? 'Pause (Space / Steering)' : 'Play (Space / Steering)'}
                      >
                        {isPlaying ? <Pause className="w-8 h-8 stroke-[2.5]" /> : <Play className="w-8 h-8 ml-1 stroke-[2.5]" />}
                      </button>

                      {/* Next Track */}
                      <button
                        onClick={() => handleNextTrack()}
                        className={`${
                          driverSafeActive ? 'w-16 h-16' : 'w-14 h-14'
                        } rounded-2xl bg-slate-900/90 hover:bg-slate-800 text-white flex items-center justify-center transition-all active:scale-95 border border-slate-700/80 shadow-lg shrink-0`}
                        title="Next Track (Steering / Headunit)"
                      >
                        <SkipForward className="w-6 h-6" />
                      </button>

                      {/* Podcast +30s Skip Button (When Podcast Mode is active) */}
                      {isPodcastMode && (
                        <button
                          onClick={() => {
                            triggerHaptic();
                            seekTo(Math.min(durationSeconds, positionSeconds + 30));
                          }}
                          className="w-12 h-12 rounded-2xl bg-slate-900 border border-slate-700 hover:border-slate-500 text-white flex flex-col items-center justify-center transition-all active:scale-95 shadow shrink-0"
                          title="Skip Forward 30 Seconds"
                        >
                          <RotateCw className="w-4 h-4 text-slate-300" />
                          <span className="text-[9px] font-mono font-bold text-slate-400">30s</span>
                        </button>
                      )}

                      {/* Repeat Mode (48px) */}
                      <button
                        onClick={() => {
                          triggerHaptic();
                          setRepeatMode(repeatMode === 'OFF' ? 'ALL' : repeatMode === 'ALL' ? 'ONE' : 'OFF');
                        }}
                        className="w-12 h-12 rounded-2xl bg-black/50 border border-slate-800 hover:border-slate-700 flex items-center justify-center transition-all active:scale-95 shadow relative shrink-0"
                        style={repeatMode !== 'OFF' ? { color: activeAccent.hex, borderColor: activeAccent.hex } : { color: '#64748b' }}
                        title={`Repeat Mode: ${repeatMode}`}
                      >
                        <Repeat className="w-5 h-5" />
                        {repeatMode === 'ONE' && (
                          <span className="absolute top-1.5 right-1.5 text-[9px] font-bold bg-black rounded-full px-1 border border-slate-700">
                            1
                          </span>
                        )}
                      </button>
                    </div>

                    {/* Volume Slider with Quick Attenuation & Mute Toggle */}
                    <div className="flex items-center gap-3 pt-1">
                      <button
                        onClick={() => {
                          triggerHaptic();
                          setIsMuted(!isMuted);
                        }}
                        className="p-2 rounded-xl text-slate-400 hover:text-white transition-colors"
                        title={isMuted ? 'Unmute' : 'Mute'}
                      >
                        {isMuted || volume === 0 ? <VolumeX className="w-5 h-5" /> : <Volume2 className="w-5 h-5" />}
                      </button>
                      <input
                        type="range"
                        min={0}
                        max={100}
                        value={isMuted ? 0 : volume}
                        onChange={(e) => {
                          setVolume(Number(e.target.value));
                          setIsMuted(false);
                        }}
                        className="w-full h-1.5 bg-slate-800 rounded-lg cursor-pointer"
                        style={{ accentColor: activeAccent.hex }}
                      />
                      <span className="text-xs text-slate-300 font-mono font-bold w-12 text-right">
                        {isMuted ? 'MUTE' : `${volume}%`}
                      </span>

                      {/* Quick Talk Duck Button in Transport */}
                      <button
                        onClick={handleToggleAttenuation}
                        className={`px-2.5 py-1.5 rounded-xl text-[11px] font-bold transition-all shrink-0 active:scale-95 flex items-center gap-1 border ${
                          isAttenuated
                            ? 'bg-amber-400 text-black border-amber-300 shadow'
                            : 'bg-black/60 text-slate-400 border-slate-800 hover:text-white'
                        }`}
                        title="Quick Duck -20dB for passenger chat or drive-thru (Shortcut: T)"
                      >
                        <VolumeX className="w-3.5 h-3.5" />
                        <span>{isAttenuated ? 'Ducked' : 'Duck'}</span>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            )}
            </div>

            {/* Dual-Pane Widescreen Companion Rail on Player View (Up Next & Quick Shortcuts) */}
            {dualPaneCockpit && tracks.length > 0 && (
              <aside className="hidden xl:flex xl:col-span-4 flex-col bg-[#0b1120] border border-slate-800/80 rounded-3xl p-4 shadow-2xl space-y-3 max-h-[560px]">
                <div className="flex items-center justify-between pb-2 border-b border-slate-800/80">
                  <div className="flex items-center gap-2">
                    <Columns2 className="w-4 h-4" style={{ color: activeAccent.hex }} />
                    <span className="text-xs font-bold uppercase tracking-wider text-white">Up Next Cockpit</span>
                  </div>
                  <button
                    type="button"
                    onClick={() => navigateToMenu({ tab: 'library' })}
                    className="text-[11px] font-bold hover:underline"
                    style={{ color: activeAccent.hex }}
                  >
                    Open Library →
                  </button>
                </div>
                <div className="flex items-center gap-1.5 flex-wrap">
                  <button
                    type="button"
                    onClick={() => handlePlaySmartMix('recent')}
                    className="px-2.5 py-1 rounded-lg bg-black/50 border border-slate-800 hover:border-slate-600 text-[11px] font-semibold text-slate-200"
                  >
                    Recent
                  </button>
                  <button
                    type="button"
                    onClick={() => handlePlaySmartMix('most_played')}
                    className="px-2.5 py-1 rounded-lg bg-black/50 border border-slate-800 hover:border-slate-600 text-[11px] font-semibold text-slate-200"
                  >
                    Most Played
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      navigateToMenu({ tab: 'library', libraryFilter: 'resume' });
                    }}
                    className="px-2.5 py-1 rounded-lg bg-black/50 border border-slate-800 hover:border-slate-600 text-[11px] font-semibold text-slate-200"
                  >
                    Resume ({resumableTracksCount})
                  </button>
                </div>
                <div className="flex-1 overflow-y-auto space-y-1.5 pr-1">
                  {queue.slice(currentTrackIndex, currentTrackIndex + 15).map((qTrack, offset) => {
                    const idx = currentTrackIndex + offset;
                    const active = offset === 0;
                    return (
                      <div
                        key={`${qTrack.id}-${idx}`}
                        onClick={() => playTrackAtIndex(idx)}
                        className={`p-2.5 rounded-xl flex items-center gap-2.5 cursor-pointer border transition-colors ${
                          active
                            ? 'bg-white/[0.08] border-slate-600'
                            : 'bg-black/35 border-slate-800/60 hover:bg-slate-900/70'
                        }`}
                      >
                        <LazyAlbumArt
                          src={coverArtUrls[qTrack.id]}
                          alt={qTrack.album || qTrack.title}
                          accentHex={activeAccent.hex}
                          isCurrent={active}
                          variant="row"
                          className="w-9 h-9"
                        />
                        <div className="min-w-0 flex-1">
                          <div
                            className="text-xs font-bold truncate"
                            style={{ color: active ? activeAccent.hex : '#ffffff' }}
                          >
                            {qTrack.title}
                          </div>
                          <div className="text-[10px] text-slate-400 truncate">{qTrack.artist}</div>
                        </div>
                        <span className="text-[10px] font-mono text-slate-500 shrink-0">{qTrack.duration}</span>
                      </div>
                    );
                  })}
                </div>
              </aside>
            )}
          </div>
        )}

        {/* VIEW 2: LIBRARY SCREEN (WITH OPTIONAL DUAL-PANE COCKPIT PIN) */}
        {activeTab === 'library' && (
          <div className={`w-full mx-auto ${dualPaneCockpit && currentTrack ? 'max-w-7xl grid grid-cols-1 xl:grid-cols-12 gap-6 items-start' : 'max-w-4xl'}`}>
            {dualPaneCockpit && currentTrack && (
              <aside
                className="hidden xl:flex xl:col-span-5 flex-col bg-[#0b1120] border rounded-3xl p-5 shadow-2xl space-y-4 sticky top-20"
                style={{ borderColor: activeAccent.border }}
              >
                <div className="flex items-center justify-between">
                  <span className="text-[11px] font-bold uppercase tracking-wider text-slate-400 flex items-center gap-1.5">
                    <Columns2 className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                    Dual-Pane Cockpit
                  </span>
                  <div className="flex items-center gap-1.5">
                    <button
                      type="button"
                      onClick={() => setIsStreamInspectorOpen(true)}
                      className="px-2 py-1 rounded-lg bg-black/50 border border-slate-800 text-[10px] font-mono text-slate-300 hover:text-white flex items-center gap-1"
                    >
                      <Info className="w-3 h-3" style={{ color: activeAccent.hex }} />
                      {currentTrack.codec || 'AUDIO'}
                    </button>
                    <button
                      type="button"
                      onClick={() => navigateToMenu({ tab: 'player' })}
                      className="px-2 py-1 rounded-lg bg-slate-800 text-[10px] font-bold text-white"
                    >
                      Expand
                    </button>
                  </div>
                </div>
                <div className="flex items-center gap-4">
                  <div
                    className="w-28 h-28 rounded-2xl bg-black/60 border flex items-center justify-center overflow-hidden shrink-0"
                    style={{ borderColor: activeAccent.border }}
                  >
                    <CrossfadeAlbumArt
                      src={coverArtUrls[currentTrack.id]}
                      alt={currentTrack.title}
                      trackKey={currentTrack.id}
                      durationMs={300}
                      className="w-full h-full"
                      fallback={
                        <Disc className={`w-12 h-12 ${isPlaying ? 'animate-spin' : ''}`} style={{ color: activeAccent.hex }} />
                      }
                    />
                  </div>
                  <div className="min-w-0 flex-1 space-y-1">
                    <div className="text-lg font-extrabold text-white line-clamp-2 leading-tight">
                      {currentTrack.title}
                    </div>
                    <div className="text-xs text-slate-300 truncate">{currentTrack.artist}</div>
                    <div className="text-[11px] text-slate-500 truncate">{currentTrack.album}</div>
                  </div>
                </div>
                <div className="space-y-1">
                  <input
                    type="range"
                    min={0}
                    max={durationSeconds || 100}
                    value={positionSeconds}
                    onChange={(e) => seekTo(Number(e.target.value))}
                    className="w-full h-2 bg-slate-800 rounded-lg cursor-pointer"
                    style={{ accentColor: activeAccent.hex }}
                  />
                  <div className="flex justify-between text-[11px] font-mono text-slate-400">
                    <span>{formatTime(positionSeconds)}</span>
                    <span>{formatTime(durationSeconds)}</span>
                  </div>
                </div>
                <div className="flex items-center justify-center gap-3">
                  <button
                    type="button"
                    onClick={handlePrevTrack}
                    className="w-12 h-12 rounded-2xl bg-slate-900 border border-slate-700 text-white flex items-center justify-center active:scale-95"
                  >
                    <SkipBack className="w-5 h-5" />
                  </button>
                  <button
                    type="button"
                    onClick={togglePlayPause}
                    className="w-16 h-16 rounded-2xl text-black flex items-center justify-center shadow-lg active:scale-95"
                    style={{ backgroundColor: activeAccent.hex }}
                  >
                    {isPlaying ? <Pause className="w-7 h-7 stroke-[2.5]" /> : <Play className="w-7 h-7 ml-0.5 stroke-[2.5]" />}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleNextTrack()}
                    className="w-12 h-12 rounded-2xl bg-slate-900 border border-slate-700 text-white flex items-center justify-center active:scale-95"
                  >
                    <SkipForward className="w-5 h-5" />
                  </button>
                </div>
              </aside>
            )}

            <div className={`${dualPaneCockpit && currentTrack ? 'xl:col-span-7' : 'w-full'} space-y-4`}>
            {/* Real-Time Library Search Bar (matches Search tab style) */}
            {driverSafeActive && (
              <div className="p-3.5 rounded-2xl bg-amber-500/15 border border-amber-500/40 text-amber-200 text-xs flex items-center gap-2.5 shadow">
                <Lock className="w-4 h-4 text-amber-400 shrink-0" />
                <span>
                  <strong>Driver Safe UI Lock Active ({currentGpsSpeed} km/h):</strong> Keyboard text search is paused while the vehicle is in motion (&ge; 10 km/h).
                </span>
              </div>
            )}
            <div className="relative flex items-center gap-2">
              <div className="relative flex-1">
                <Search className="w-5 h-5 text-slate-400 absolute left-4 top-4" />
                <input
                  type="text"
                  readOnly={driverSafeActive}
                  value={librarySearchQuery}
                  onChange={(e) => {
                    if (!driverSafeActive) setLibrarySearchQuery(e.target.value);
                  }}
                  placeholder={
                    driverSafeActive
                      ? 'Typing locked while driving (>= 10 km/h)...'
                      : 'Filter library by artist, album, or title...'
                  }
                  className={`w-full bg-[#0b1120] border border-slate-800 rounded-2xl pl-12 pr-10 py-3.5 text-base text-white placeholder-slate-500 focus:outline-none focus:border-slate-600 transition-colors shadow-lg ${
                    driverSafeActive ? 'opacity-60 cursor-not-allowed' : ''
                  }`}
                />
                {librarySearchQuery && (
                  <button
                    onClick={() => setLibrarySearchQuery('')}
                    className="absolute right-4 top-3.5 text-slate-400 hover:text-white p-1"
                    aria-label="Clear library search"
                  >
                    ✕
                  </button>
                )}
              </div>
              <button
                type="button"
                onClick={handleStartVoiceSearch}
                className={`p-3.5 rounded-2xl border transition-all active:scale-95 shrink-0 ${
                  isVoiceListening
                    ? 'bg-red-500/20 border-red-500 text-red-400 animate-pulse'
                    : 'bg-[#0b1120] border-slate-800 text-slate-300 hover:text-white'
                }`}
                title="Hands-Free Voice Filter"
              >
                <Mic className="w-5 h-5" style={!isVoiceListening ? { color: activeAccent.hex } : undefined} />
              </button>
            </div>

            {/* Library Header Bar */}
            <div className="flex items-center justify-between flex-wrap gap-3 pb-1 border-b border-slate-800/80">
              <div>
                <h2 className="text-base font-bold text-white flex items-center gap-2">
                  <span>Nextcloud Collection</span>
                  <span className="text-xs font-normal text-slate-400 font-mono">
                    ({librarySearchQuery.trim() ? `${displayedTracks.length} / ${tracks.length}` : tracks.length} tracks • {formatBytes(totalLibraryBytes)})
                  </span>
                </h2>
              </div>

              {/* Action Buttons */}
              <div className="flex items-center gap-2 flex-wrap">
                {currentTrack && (
                  <button
                    onClick={handleJumpToPlayingTrack}
                    className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-[#0b1120] text-slate-200 border border-slate-700 hover:text-white flex items-center gap-1.5 transition-colors active:scale-95"
                    title="Scroll library directly to currently playing track"
                  >
                    <Crosshair className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                    Jump to Playing
                  </button>
                )}

                <button
                  onClick={() => openFolderBrowser('')}
                  className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-[#0b1120] text-slate-200 border border-slate-700 hover:text-white flex items-center gap-1.5 transition-colors"
                  title="Manually browse and add a specific Nextcloud or Local folder to your library"
                >
                  <FolderOpen className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                  Add / Scan Folder
                </button>

                <button
                  onClick={() => scanNextcloudMusic(username, password, musicFolder, true)}
                  disabled={isScanning}
                  className="px-3 py-1.5 rounded-xl text-xs font-semibold text-black flex items-center gap-1.5 transition-all shadow"
                  style={{ backgroundColor: activeAccent.hex }}
                >
                  <RefreshCw className={`w-3.5 h-3.5 ${isScanning ? 'animate-spin' : ''}`} />
                  {isScanning ? 'Syncing...' : 'Sync Nextcloud'}
                </button>
              </div>
            </div>

            {/* Filter Pills & Smart Mix Quick Launch */}
            <div className="flex items-center justify-between flex-wrap gap-3">
              <div className="flex items-center gap-2 overflow-x-auto py-1">
                {(
                  ['all', 'recent', 'most_played', 'resume', 'albums', 'artists', 'folders', 'playlists', 'favorites', 'downloaded'] as const
                ).map((filter) => (
                  <button
                    key={filter}
                    onClick={() => {
                      setLibraryFilter(filter);
                      setSelectedAlbum(null);
                      setSelectedArtist(null);
                      if (filter !== 'playlists') setSelectedPlaylist(null);
                    }}
                    className={`px-3 py-1.5 rounded-xl text-xs font-semibold capitalize transition-all shrink-0 ${
                      libraryFilter === filter
                        ? 'text-black font-bold shadow'
                        : 'bg-[#0b1120] text-slate-400 border border-slate-800 hover:text-white'
                    }`}
                    style={libraryFilter === filter ? { backgroundColor: activeAccent.hex } : {}}
                  >
                    {filter === 'all'
                      ? `All Songs (${tracks.length})`
                      : filter === 'recent'
                      ? 'Recently Played'
                      : filter === 'most_played'
                      ? 'Most Played'
                      : filter === 'resume'
                      ? `Resume (${resumableTracksCount})`
                      : filter === 'albums'
                      ? `Albums (${albumsList.length})`
                      : filter === 'artists'
                      ? `Artists (${artistsList.length})`
                      : filter === 'folders'
                      ? 'Folders'
                      : filter === 'playlists'
                      ? `Playlists (${playlists.length})`
                      : filter === 'favorites'
                      ? `Favorites (${favorites.size})`
                      : `Downloaded (${downloadedTracks.size})`}
                  </button>
                ))}
              </div>

              <button
                onClick={() => setOfflineOnly(!offlineOnly)}
                className={`px-3 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 border transition-all ${
                  offlineOnly ? 'bg-black text-white' : 'bg-[#0b1120] text-slate-400 border-slate-800 hover:text-white'
                }`}
                style={offlineOnly ? { borderColor: activeAccent.hex, color: activeAccent.hex } : {}}
              >
                {offlineOnly ? <CloudOff className="w-3.5 h-3.5" /> : <Cloud className="w-3.5 h-3.5" />}
                Offline Only
              </button>
            </div>

            {/* Favorites One-Tap Pin All Offline Action Banner */}
            {libraryFilter === 'favorites' && favorites.size > 0 && (
              <div className="flex items-center justify-between gap-3 p-3 rounded-2xl bg-[#0b1120] border border-slate-800">
                <div className="text-xs text-slate-300">
                  <strong className="text-white">{favorites.size} Favorite tracks</strong> ready to pin for deadzone roadtrips.
                </div>
                <button
                  type="button"
                  disabled={isBatchSyncing}
                  onClick={handlePinAllFavorites}
                  className="px-3.5 py-1.5 rounded-xl text-xs font-bold text-black flex items-center gap-1.5 transition-all active:scale-95 disabled:opacity-50"
                  style={{ backgroundColor: activeAccent.hex }}
                >
                  <Download className="w-3.5 h-3.5" />
                  {isBatchSyncing ? 'Pinning...' : 'Pin All Favorites Offline'}
                </button>
              </div>
            )}

            {/* Batch Download Live Progress Banner */}
            {isBatchSyncing && batchSyncProgress && (
              <div className="p-3 rounded-2xl bg-[#0b1120] border border-slate-800 space-y-1.5">
                <div className="flex items-center justify-between text-xs">
                  <span className="text-slate-300 truncate">
                    Downloading: <strong className="text-white">{batchSyncProgress.trackTitle}</strong>
                  </span>
                  <span className="font-mono font-bold" style={{ color: activeAccent.hex }}>
                    {batchSyncProgress.current}/{batchSyncProgress.total}
                  </span>
                </div>
                <div className="w-full h-1.5 bg-slate-800 rounded-full overflow-hidden">
                  <div
                    className="h-full transition-all duration-200"
                    style={{
                      width: `${(batchSyncProgress.current / batchSyncProgress.total) * 100}%`,
                      backgroundColor: activeAccent.hex,
                    }}
                  />
                </div>
              </div>
            )}

            {/* One-Tap Smart Mixes */}
            {tracks.length > 0 && (
              <div className="flex items-center gap-2 flex-wrap">
                <span className="text-[11px] font-bold uppercase tracking-wider text-slate-500 mr-1">
                  Smart Mixes:
                </span>
                <button
                  onClick={() => handlePlaySmartMix('cruising')}
                  className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-[#0b1120] hover:bg-slate-900 text-slate-200 border border-slate-800 hover:border-slate-600 flex items-center gap-1.5 transition-all active:scale-95"
                >
                  <Sparkles className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                  Cruising Mix
                </button>
                <button
                  onClick={() => handlePlaySmartMix('recent')}
                  className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-[#0b1120] hover:bg-slate-900 text-slate-200 border border-slate-800 hover:border-slate-600 flex items-center gap-1.5 transition-all active:scale-95"
                >
                  <Clock className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                  Recently Played
                </button>
                <button
                  onClick={() => handlePlaySmartMix('most_played')}
                  className="px-3 py-1.5 rounded-xl text-xs font-semibold bg-[#0b1120] hover:bg-slate-900 text-slate-200 border border-slate-800 hover:border-slate-600 flex items-center gap-1.5 transition-all active:scale-95"
                >
                  <Activity className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                  Most Played
                </button>
              </div>
            )}

            {/* Active Filter Breadcrumb */}
            {(selectedAlbum || selectedArtist || selectedPlaylist) && (
              <div className="flex items-center gap-2 bg-[#0b1120] px-3 py-1.5 rounded-xl border border-slate-800 text-xs">
                <span className="text-slate-400">Filtering by:</span>
                <span className="font-bold text-white">
                  {selectedAlbum || selectedArtist || selectedPlaylist?.name}
                </span>
                <button
                  onClick={() => {
                    setSelectedAlbum(null);
                    setSelectedArtist(null);
                    setSelectedPlaylist(null);
                  }}
                  className="ml-auto text-slate-400 hover:text-white text-xs px-2 py-0.5 rounded bg-black/40"
                >
                  Clear Filter ✕
                </button>
              </div>
            )}

            {/* Content: Folders / Playlists / Albums Grid / Artists List / Songs List */}
            {libraryFilter === 'folders' ? (
              <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-4 space-y-3 shadow-2xl">
                <div className="flex items-center justify-between gap-2 pb-2 border-b border-slate-800/60">
                  <div className="flex items-center gap-2 text-xs font-mono text-slate-300 truncate">
                    <Folder className="w-4 h-4 shrink-0" style={{ color: activeAccent.hex }} />
                    <span className="truncate">/{selectedFolderPath || 'Root'}</span>
                  </div>
                  <div className="flex items-center gap-2 shrink-0 flex-wrap justify-end">
                    <button
                      type="button"
                      disabled={isBatchSyncing}
                      onClick={() => handlePinFolderOffline(selectedFolderPath)}
                      className="px-2.5 py-1 rounded-lg text-[11px] font-bold text-black flex items-center gap-1 disabled:opacity-50"
                      style={{ backgroundColor: activeAccent.hex }}
                      title="Download all tracks in this folder for offline playback"
                    >
                      <Download className="w-3 h-3" />
                      Pin Folder Offline
                    </button>
                    <button
                      type="button"
                      onClick={() => runFolderScanDiagnostics(selectedFolderPath && !selectedFolderPath.startsWith('local:') ? selectedFolderPath : musicFolder)}
                      className="px-2.5 py-1 rounded-lg text-[11px] font-bold bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 flex items-center gap-1"
                    >
                      <Network className="w-3 h-3" style={{ color: activeAccent.hex }} />
                      Scan Diagnostics
                    </button>
                    <button
                      type="button"
                      onClick={handleScanLocalFolder}
                      className="px-2.5 py-1 rounded-lg text-[11px] font-bold bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 flex items-center gap-1"
                    >
                      <HardDrive className="w-3 h-3" style={{ color: activeAccent.hex }} />
                      Add USB Folder
                    </button>
                    {selectedFolderPath && (
                      <button
                        type="button"
                        onClick={() => {
                          const idx = selectedFolderPath.lastIndexOf('/');
                          setSelectedFolderPath(idx > 0 ? selectedFolderPath.slice(0, idx) : '');
                        }}
                        className="px-2.5 py-1 rounded-lg text-[11px] font-bold bg-black/60 text-slate-300 hover:text-white border border-slate-700 flex items-center gap-1"
                      >
                        <ArrowUp className="w-3 h-3" />
                        Up
                      </button>
                    )}
                  </div>
                </div>

                {folderBrowserData.subFolders.length === 0 && folderBrowserData.directTracks.length === 0 ? (
                  <div className="py-10 text-center text-xs text-slate-500">
                    No folders or tracks found here. Scan Nextcloud or add a local USB folder.
                  </div>
                ) : (
                  <div className="space-y-1.5 max-h-[460px] overflow-y-auto pr-1">
                    {folderBrowserData.subFolders.map((sub) => (
                      <div
                        key={sub.fullPath}
                        onClick={() => setSelectedFolderPath(sub.fullPath)}
                        className="w-full p-3 rounded-xl bg-black/40 hover:bg-slate-900 border border-slate-800/70 flex items-center justify-between text-left transition-colors cursor-pointer gap-2"
                      >
                        <div className="flex items-center gap-2.5 min-w-0">
                          {sub.isLocal ? (
                            <HardDrive className="w-4 h-4 shrink-0 text-cyan-400" />
                          ) : (
                            <Folder className="w-4 h-4 shrink-0" style={{ color: activeAccent.hex }} />
                          )}
                          <span className="text-sm font-semibold text-white truncate">{sub.name}</span>
                        </div>
                        <div className="flex items-center gap-2 shrink-0">
                          <span className="text-xs font-mono text-slate-400">{sub.count} tracks</span>
                          {!sub.isLocal && (
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                handlePinFolderOffline(sub.fullPath);
                              }}
                              className="p-1.5 rounded-lg bg-slate-900 hover:bg-slate-800 border border-slate-700 text-slate-200"
                              title={`Pin entire "${sub.name}" folder offline`}
                            >
                              <Download className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                            </button>
                          )}
                        </div>
                      </div>
                    ))}
                    {folderBrowserData.directTracks.map((track) => {
                      const originalIndex = trackIndexMap.get(track.id) ?? -1;
                      const isCurrent = currentTrackIndex === originalIndex;
                      return (
                        <div
                          key={track.id}
                          onClick={() => {
                            if (originalIndex !== -1) playTrackAtIndex(originalIndex);
                            navigateToMenu({ tab: 'player' });
                          }}
                          className={`p-3 rounded-xl flex items-center justify-between cursor-pointer border transition-colors gap-2 ${
                            isCurrent
                              ? 'bg-white/[0.07] border-slate-600'
                              : 'bg-black/25 border-slate-800/50 hover:bg-slate-900/60'
                          }`}
                        >
                          <div className="min-w-0 pr-2 flex-1">
                            <div
                              className="text-sm font-semibold truncate"
                              style={{ color: isCurrent ? activeAccent.hex : '#ffffff' }}
                            >
                              {track.title}
                            </div>
                            <div className="text-[11px] text-slate-400 truncate flex items-center gap-1.5">
                              <span>{track.artist} • {track.fileName}</span>
                              {track.sourceType === 'LOCAL' && (
                                <span className="px-1.5 py-0.2 rounded bg-cyan-500/20 text-cyan-300 font-mono text-[9px]">
                                  USB
                                </span>
                              )}
                            </div>
                          </div>
                          <div className="flex items-center gap-1.5 shrink-0">
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                handlePlayNextInQueue(track);
                              }}
                              className="px-2 py-1 text-[10px] font-bold rounded-lg bg-slate-900 border border-slate-700 text-slate-200 hover:text-white"
                              title="Play Next"
                            >
                              Next
                            </button>
                            <button
                              type="button"
                              onClick={(e) => {
                                e.stopPropagation();
                                handleAddToQueue(track);
                              }}
                              className="p-1.5 rounded-lg bg-slate-900 border border-slate-700 text-slate-300 hover:text-white"
                              title="Add to Queue"
                            >
                              <ListMusic className="w-3.5 h-3.5" />
                            </button>
                            <span className="text-xs font-mono text-slate-500">{track.duration}</span>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                )}
              </div>
            ) : libraryFilter === 'playlists' && !selectedPlaylist ? (
              <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-4 space-y-3 shadow-2xl">
                <div className="flex items-center justify-between gap-2 flex-wrap pb-2 border-b border-slate-800/60">
                  <span className="text-xs font-bold text-slate-300">
                    Saved Vehicle Playlists ({playlists.length})
                  </span>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setIsPlaylistsOpen(true)}
                      className="px-3 py-1.5 rounded-xl text-xs font-bold bg-slate-800 hover:bg-slate-700 text-white border border-slate-700 flex items-center gap-1"
                    >
                      <Plus className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                      Manage / Import .m3u8
                    </button>
                    {isLoggedIn && (
                      <button
                        type="button"
                        disabled={isSyncingPlaylists}
                        onClick={handleSyncPlaylistsCloud}
                        className="px-3 py-1.5 rounded-xl text-xs font-bold text-black flex items-center gap-1 disabled:opacity-50"
                        style={{ backgroundColor: activeAccent.hex }}
                      >
                        <Cloud className="w-3.5 h-3.5" />
                        {isSyncingPlaylists ? 'Syncing...' : 'Sync .m3u8'}
                      </button>
                    )}
                  </div>
                </div>
                {playlists.length === 0 ? (
                  <div className="py-10 text-center text-xs text-slate-500">
                    No playlists yet. Click "Manage / Import .m3u8" to create or import one.
                  </div>
                ) : (
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                    {playlists.map((pl) => (
                      <div
                        key={pl.id}
                        onClick={() => setSelectedPlaylist(pl)}
                        className="p-3.5 rounded-xl bg-black/40 border border-slate-800 hover:border-slate-700 flex items-center justify-between cursor-pointer"
                      >
                        <div className="min-w-0 pr-2">
                          <div className="text-sm font-bold text-white truncate">{pl.name}</div>
                          <div className="text-[11px] text-slate-400">{pl.trackIds.length} tracks</div>
                        </div>
                        <div className="flex items-center gap-1.5 shrink-0">
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              handlePlayPlaylist(pl);
                            }}
                            className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-white"
                            title="Play Playlist"
                          >
                            <Play className="w-3.5 h-3.5" />
                          </button>
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleExportPlaylistM3u8(pl);
                            }}
                            className="p-2 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 border border-slate-800"
                            title="Export .m3u8"
                          >
                            <Download className="w-3.5 h-3.5" />
                          </button>
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            ) : libraryFilter === 'albums' && !selectedAlbum ? (
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
                {filteredAlbumsList.length === 0 ? (
                  <div className="col-span-full p-8 text-center text-slate-500 text-sm">
                    {librarySearchQuery.trim()
                      ? `No albums found matching "${librarySearchQuery}".`
                      : 'No albums found in Nextcloud library.'}
                  </div>
                ) : (
                  filteredAlbumsList.map((album, idx) => {
                    const albumCover = album.tracks.map((t) => coverArtUrls[t.id]).find(Boolean);
                    return (
                      <div
                        key={idx}
                        onClick={() => {
                          navigateToMenu({
                            tab: 'library',
                            libraryFilter: 'all',
                            selectedAlbum: album.name,
                            selectedArtist: null,
                          });
                        }}
                        className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-4 hover:border-slate-700 transition-colors cursor-pointer group"
                      >
                        <LazyAlbumArt
                          src={albumCover}
                          alt={album.name}
                          accentHex={activeAccent.hex}
                          variant="album"
                          className="mb-3"
                        />
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <div className="font-bold text-sm text-white truncate">{album.name}</div>
                            <div className="text-xs text-slate-400 mt-0.5 truncate">{album.artist}</div>
                            <div className="text-[11px] text-slate-500 mt-1">{album.count} tracks</div>
                          </div>
                          <button
                            type="button"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleDownloadAlbum(album.name, album.tracks);
                            }}
                            title={`Download all tracks in ${album.name} for offline`}
                            className="p-2 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 shrink-0"
                          >
                            <Download className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                          </button>
                        </div>
                      </div>
                    );
                  })
                )}
              </div>
            ) : libraryFilter === 'artists' && !selectedArtist ? (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-3">
                {filteredArtistsList.length === 0 ? (
                  <div className="col-span-full p-8 text-center text-slate-500 text-sm">
                    {librarySearchQuery.trim()
                      ? `No artists found matching "${librarySearchQuery}".`
                      : 'No artists found in Nextcloud library.'}
                  </div>
                ) : (
                  filteredArtistsList.map((artist, idx) => (
                    <div
                      key={idx}
                      onClick={() => {
                        navigateToMenu({
                          tab: 'library',
                          libraryFilter: 'all',
                          selectedArtist: artist.name,
                          selectedAlbum: null,
                        });
                      }}
                      className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-4 hover:border-slate-700 transition-colors cursor-pointer flex items-center gap-3"
                    >
                      <div
                        className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border"
                        style={{ backgroundColor: activeAccent.bg, borderColor: activeAccent.border }}
                      >
                        <Music className="w-5 h-5" style={{ color: activeAccent.hex }} />
                      </div>
                      <div className="truncate flex-1">
                        <div className="font-bold text-sm text-white truncate">{artist.name}</div>
                        <div className="text-[11px] text-slate-400 mt-0.5">{artist.count} tracks</div>
                      </div>
                    </div>
                  ))
                )}
              </div>
            ) : (
              /* Custom Virtualized LazyColumn with Fixed Item Heights & DOM Recycling */
              <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl overflow-hidden shadow-2xl">
                <LazyColumn<Track>
                  items={displayedTracks}
                  itemHeight={68}
                  overscan={mediatekEcoMode ? 3 : 6}
                  enableFadeIn={true}
                  fadeDurationMs={mediatekEcoMode ? 170 : 220}
                  scrollRequest={libraryScrollRequest}
                  accentHex={activeAccent.hex}
                  keyExtractor={(track: Track) => track.id}
                  className="h-[calc(100dvh-265px)] min-h-[240px] max-h-[580px]"
                  emptyPlaceholder={
                    <div className="p-8 text-center text-slate-400 text-sm space-y-3">
                      <p>No audio files found matching this filter.</p>
                      {isLoggedIn && (
                        <button
                          onClick={() => scanNextcloudMusic(username, password, musicFolder)}
                          className="px-4 py-2 rounded-xl text-xs font-bold text-black transition-all active:scale-95"
                          style={{ backgroundColor: activeAccent.hex }}
                        >
                          Rescan Nextcloud Library
                        </button>
                      )}
                    </div>
                  }
                  renderItem={(track: Track, _index, isFastScrolling) => {
                    const originalIndex = trackIndexMap.get(track.id) ?? -1;
                    const isCurrent = currentTrackIndex === originalIndex;
                    const isFav = favorites.has(track.id);
                    const isOffline = offlineTrackIds.has(track.id);

                    return (
                      <div
                        onClick={() => {
                          const resumeRec = libraryFilter === 'resume' ? resumableProgressMap[track.id] : undefined;
                          if (originalIndex !== -1) {
                            if (resumeRec && resumeRec.positionSeconds >= 10) {
                              setCurrentTrackIndex(originalIndex);
                              setPositionSeconds(resumeRec.positionSeconds);
                              startAudioPlayback(track, resumeRec.positionSeconds);
                            } else {
                              playTrackAtIndex(originalIndex);
                            }
                          }
                          navigateToMenu({ tab: 'player' });
                        }}
                        className={`h-full px-3.5 flex items-center justify-between cursor-pointer border-b border-slate-800/50 transition-colors select-none ${
                          isCurrent ? 'bg-white/[0.07]' : 'hover:bg-slate-900/60 active:bg-slate-800/50'
                        }`}
                      >
                        <div className="flex items-center gap-3 flex-1 min-w-0 pr-2">
                          <LazyAlbumArt
                            src={coverArtUrls[track.id]}
                            alt={track.album || track.title}
                            accentHex={activeAccent.hex}
                            isCurrent={isCurrent}
                            variant="row"
                          />
                          <div className="truncate flex-1">
                            <div
                              className="font-semibold text-sm truncate leading-snug"
                              style={{ color: isCurrent ? activeAccent.hex : '#ffffff' }}
                            >
                              {track.title}
                            </div>
                            <div className="text-[11px] text-slate-400 truncate mt-0.5 flex items-center gap-1.5">
                              <span>
                                {track.artist} <span className="text-slate-600">•</span> {track.album}
                              </span>
                              {track.sourceType === 'LOCAL' && (
                                <span className="px-1.5 py-0.2 rounded bg-cyan-500/20 text-cyan-300 font-mono text-[9px] shrink-0">
                                  USB
                                </span>
                              )}
                              {libraryFilter === 'most_played' && playStats[track.id]?.playCount ? (
                                <span className="px-1.5 py-0.2 rounded bg-amber-500/20 text-amber-300 font-mono text-[9px] shrink-0">
                                  {playStats[track.id].playCount}× plays
                                </span>
                              ) : null}
                              {libraryFilter === 'resume' && resumableProgressMap[track.id] ? (
                                <span className="px-1.5 py-0.2 rounded bg-emerald-500/20 text-emerald-300 font-mono text-[9px] shrink-0">
                                  Resume {formatTime(resumableProgressMap[track.id].positionSeconds)}
                                </span>
                              ) : null}
                            </div>
                          </div>
                        </div>

                        {/* Actions (optimized for automotive headunit touch targets) */}
                        <div className="flex items-center gap-1 sm:gap-1.5 shrink-0">
                          {!isFastScrolling && (
                            <>
                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  triggerHaptic();
                                  handlePlayNextInQueue(track);
                                }}
                                className="inline-flex px-2 py-1 text-[10px] sm:text-[11px] font-semibold rounded-lg bg-slate-900/80 border border-slate-800 hover:border-slate-700 text-slate-300 hover:text-white transition-all active:scale-95"
                                title="Play Next in Queue"
                              >
                                Play Next
                              </button>

                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  triggerHaptic();
                                  handleAddToQueue(track);
                                }}
                                className="p-2 text-slate-400 hover:text-white rounded-lg border border-slate-800/80 hover:border-slate-700 transition-colors active:scale-95"
                                title="Add to Up-Next Queue"
                              >
                                <ListMusic className="w-4 h-4" />
                              </button>

                              <button
                                onClick={(e) => {
                                  e.stopPropagation();
                                  triggerHaptic();
                                  setTrackForPlaylist(track);
                                  setIsPlaylistsOpen(true);
                                }}
                                className="p-2 text-slate-400 hover:text-white rounded-lg border border-slate-800/80 hover:border-slate-700 transition-colors active:scale-95"
                                title="Add to Playlist"
                              >
                                <Plus className="w-4 h-4 text-lime-400" />
                              </button>
                            </>
                          )}

                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              triggerHaptic();
                              toggleFavorite(track.id);
                            }}
                            className="p-2 text-slate-500 hover:text-white transition-colors"
                            style={{ color: isFav ? activeAccent.hex : undefined }}
                            title="Favorite"
                          >
                            <Heart className="w-4 h-4" fill={isFav ? activeAccent.hex : 'none'} />
                          </button>

                          <button
                            onClick={(e) => {
                              e.stopPropagation();
                              triggerHaptic();
                              downloadTrackForOffline(track);
                            }}
                            className="p-2 text-slate-500 hover:text-white transition-colors"
                            style={{ color: isOffline ? activeAccent.hex : undefined }}
                            title={isOffline ? 'Offline Cached (Ready for roadtrips)' : 'Download for Offline Roadtrips'}
                          >
                            {downloadingId === track.id ? (
                              <RefreshCw className="w-4 h-4 animate-spin text-lime-400" />
                            ) : isOffline ? (
                              <CheckCircle className="w-4 h-4" />
                            ) : (
                              <Download className="w-4 h-4" />
                            )}
                          </button>

                          <span className="text-xs text-slate-400 font-mono w-14 text-right hidden sm:inline-block">
                            {track.sizeFormatted}
                          </span>
                        </div>
                      </div>
                    );
                  }}
                />
              </div>
            )}
            </div>
          </div>
        )}

        {/* VIEW 3: INSTANT FUZZY SEARCH */}
        {activeTab === 'search' && (
          <div className="w-full max-w-3xl mx-auto space-y-4 pb-20">
            {driverSafeActive && (
              <div className="p-3.5 rounded-2xl bg-amber-500/15 border border-amber-500/40 text-amber-200 text-xs flex items-center gap-2.5 shadow">
                <Lock className="w-4 h-4 text-amber-400 shrink-0" />
                <span>
                  <strong>Driver Safe UI Lock Active ({currentGpsSpeed} km/h):</strong> Keyboard text search is paused while the vehicle is in motion (&ge; 10 km/h).
                </span>
              </div>
            )}
            <div className="relative flex items-center gap-2">
              <div className="relative flex-1">
                <Search className="w-5 h-5 text-slate-400 absolute left-4 top-4" />
                <input
                  type="text"
                  readOnly={driverSafeActive}
                  value={searchQuery}
                  onChange={(e) => {
                    if (!driverSafeActive) setSearchQuery(e.target.value);
                  }}
                  placeholder={
                    driverSafeActive
                      ? 'Typing locked while driving (>= 10 km/h)...'
                      : 'Fuzzy search songs, artists, albums, or file names...'
                  }
                  className={`w-full bg-[#0b1120] border border-slate-800 rounded-2xl pl-12 pr-10 py-3.5 text-base text-white placeholder-slate-500 focus:outline-none focus:border-slate-600 transition-colors shadow-lg ${
                    driverSafeActive ? 'opacity-60 cursor-not-allowed' : ''
                  }`}
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery('')}
                    className="absolute right-4 top-3.5 text-slate-400 hover:text-white p-1"
                  >
                    ✕
                  </button>
                )}
              </div>
              <button
                type="button"
                onClick={handleStartVoiceSearch}
                className={`p-3.5 rounded-2xl border transition-all active:scale-95 shrink-0 ${
                  isVoiceListening
                    ? 'bg-red-500/20 border-red-500 text-red-400 animate-pulse'
                    : 'bg-[#0b1120] border-slate-800 text-slate-300 hover:text-white'
                }`}
                title='Hands-Free Voice Search (say "Play <song>")'
              >
                <Mic className="w-5 h-5" style={!isVoiceListening ? { color: activeAccent.hex } : undefined} />
              </button>
            </div>

            {searchQuery.trim() ? (
              <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl divide-y divide-slate-800/60 overflow-hidden shadow-xl">
                {(() => {
                  const results = searchTracks(tracks, searchQuery);
                  if (results.length === 0) {
                    return (
                      <div className="p-10 text-center text-slate-500 text-sm">
                        No matches found for "{searchQuery}". Try a broader term.
                      </div>
                    );
                  }
                  return results.map((track) => {
                    const isCurrent = currentTrack?.id === track.id;
                    const isFav = favorites.has(track.id);
                    const isOffline = offlineTrackIds.has(track.id);

                    return (
                      <div
                        key={track.id}
                        className="p-3.5 flex items-center justify-between hover:bg-slate-900/60 transition-colors gap-3 group"
                      >
                        <div
                          onClick={() => {
                            triggerHaptic();
                            const qIdx = queue.findIndex((t) => t.id === track.id);
                            if (qIdx !== -1) {
                              playTrackAtIndex(qIdx);
                            } else {
                              setQueue((prev) => [track, ...prev]);
                              setCurrentTrackIndex(0);
                              startAudioPlayback(track);
                            }
                            setActiveTab('player');
                          }}
                          className="flex items-center gap-3.5 flex-1 min-w-0 cursor-pointer"
                        >
                          <LazyAlbumArt
                            src={coverArtUrls[track.id]}
                            alt={track.album || track.title}
                            accentHex={activeAccent.hex}
                            isCurrent={isCurrent}
                            variant="row"
                            className="w-11 h-11"
                          />
                          <div className="truncate">
                            <div
                              className="font-bold text-sm truncate"
                              style={{ color: isCurrent ? activeAccent.hex : '#ffffff' }}
                            >
                              {track.title}
                            </div>
                            <div className="text-xs text-slate-400 truncate flex items-center gap-1.5 mt-0.5">
                              <span>{track.artist}</span>
                              <span className="text-slate-600">•</span>
                              <span>{track.album}</span>
                              {isOffline && (
                                <span className="text-[10px] bg-emerald-500/20 text-emerald-400 font-bold px-1.5 py-0.5 rounded border border-emerald-500/30">
                                  OFFLINE
                                </span>
                              )}
                            </div>
                          </div>
                        </div>

                        {/* Search Action Buttons (Play Now, Play Next, Queue, Playlist, Offline) */}
                        <div className="flex items-center gap-1.5 shrink-0">
                          <button
                            onClick={() => {
                              triggerHaptic();
                              handlePlayNextInQueue(track);
                            }}
                            className="px-2.5 py-1.5 text-xs font-semibold rounded-lg bg-slate-900/80 border border-slate-800 hover:border-slate-700 text-slate-300 hover:text-white transition-all active:scale-95"
                            title="Play Next in Queue"
                          >
                            Play Next
                          </button>

                          <button
                            onClick={() => {
                              triggerHaptic();
                              handleAddToQueue(track);
                            }}
                            className="p-2 text-slate-400 hover:text-white rounded-lg border border-slate-800/80 hover:border-slate-700 transition-colors active:scale-95"
                            title="Add to Up-Next Queue"
                          >
                            <ListMusic className="w-4 h-4" />
                          </button>

                          <button
                            onClick={() => {
                              triggerHaptic();
                              setTrackForPlaylist(track);
                              setIsPlaylistsOpen(true);
                            }}
                            className="p-2 text-slate-400 hover:text-white rounded-lg border border-slate-800/80 hover:border-slate-700 transition-colors active:scale-95"
                            title="Add to Playlist"
                          >
                            <Plus className="w-4 h-4 text-lime-400" />
                          </button>

                          <button
                            onClick={() => {
                              triggerHaptic();
                              downloadTrackForOffline(track);
                            }}
                            className="p-2 text-slate-400 hover:text-white rounded-lg border border-slate-800/80 hover:border-slate-700 transition-colors active:scale-95"
                            style={{ color: isOffline ? activeAccent.hex : undefined }}
                            title={isOffline ? 'Offline Cached' : 'Download for Offline Roadtrips'}
                          >
                            {downloadingId === track.id ? (
                              <RefreshCw className="w-4 h-4 animate-spin text-lime-400" />
                            ) : isOffline ? (
                              <CheckCircle className="w-4 h-4" />
                            ) : (
                              <Download className="w-4 h-4" />
                            )}
                          </button>
                        </div>
                      </div>
                    );
                  });
                })()}
              </div>
            ) : (
              <div className="bg-[#0b1120] border border-slate-800/80 rounded-2xl p-8 text-center text-slate-400 space-y-3">
                <Search className="w-8 h-8 mx-auto text-slate-600" />
                <div className="text-sm font-semibold text-white">Instant In-Car Search</div>
                <div className="text-xs text-slate-500 max-w-sm mx-auto">
                  Type any song title, artist, or album. Supports typo-tolerant fuzzy matching across {tracks.length} synced Nextcloud audio files.
                </div>
              </div>
            )}
          </div>
        )}

        {/* VIEW 4: POLISHED AUTOMOTIVE CONSOLE SETTINGS */}
        {activeTab === "settings" && (
          <SettingsPanel
            accentHex={activeAccent.hex}
            accentKey={accentKey}
            onSetAccentKey={setAccentKey}
            accentPalette={accentPalette}

            dspSettings={dspSettings}
            onChangeDspSettings={handleDspSettingsFromPanel}
            customDspPresets={customDspPresets}
            activeCompareSlot={activeCompareSlot}
            slotBPresetName={slotBDspSnapshot?.selectedPresetId || null}
            onToggleCompareSlot={handleToggleCompareSlot}
            onSnapshotSlotB={handleSnapshotSlotB}
            onHoldBypassChange={setIsHoldBypassed}
            activeOutputRoute={activeOutputRoute}
            onSelectOutputRoute={handleSelectOutputRoute}
            adaptiveCabinConfig={adaptiveCabinConfig}
            onChangeAdaptiveCabinConfig={setAdaptiveCabinConfig}
            adaptiveCabinState={adaptiveCabinState}
            adaptiveDspDelta={adaptiveDspDelta}
            cabinNoiseDb={cabinNoiseDb}
            cabinMicStatus={cabinMicStatus}
            dualPaneCockpit={dualPaneCockpit}
            onToggleDualPaneCockpit={setDualPaneCockpit}
            driveModeAutoSpeedKmH={driveModeAutoSpeedKmH}
            onChangeDriveModeAutoSpeedKmH={setDriveModeAutoSpeedKmH}
            onSaveCustomDspPreset={(name: string) => {
              const id = `custom_${Date.now()}`;
              const preset: DspPreset16 = {
                id,
                name,
                description: 'Saved Headunit DSP Tune',
                bands16: [...dspSettings.bands16],
                bassBoostDb: dspSettings.bassBoostDb,
                preampDb: dspSettings.preampDb,
                isCustom: true,
              };
              setCustomDspPresets((prev) => [...prev, preset]);
              setDspSettings((prev) => ({ ...prev, selectedPresetId: id, dspEnabled: true }));
              setStatusMessage(`Saved DSP Preset "${name}"`);
              setTimeout(() => setStatusMessage(null), 2500);
            }}
            onDeleteCustomDspPreset={(presetId: string) => {
              setCustomDspPresets((prev) => prev.filter((p) => p.id !== presetId));
              setDspSettings((prev) =>
                prev.selectedPresetId === presetId ? { ...prev, selectedPresetId: 'custom' } : prev
              );
            }}
            masterVolume={isMuted ? 0 : volume}

            isAttenuated={isAttenuated}
            onToggleAttenuation={handleToggleAttenuation}

            scvSensitivity={scvSensitivity}
            onChangeScvSensitivity={setScvSensitivity}
            currentGpsSpeed={currentGpsSpeed}
            calculatedSpeedBoostDb={calculateSpeedVolumeBoost(currentGpsSpeed, scvSensitivity).dbBoost}

            enableSwipeGestures={enableSwipeGestures}
            onToggleSwipeGestures={setEnableSwipeGestures}

            mediatekEcoMode={mediatekEcoMode}
            onToggleMediatekEcoMode={setMediatekEcoMode}

            cockpitThemeMode={cockpitThemeMode}
            onChangeThemeMode={setCockpitThemeMode}
            gpsStatus={gpsStatus}
            onRequestGpsRefresh={() => setGpsPollNonce((n) => n + 1)}

            isPodcastMode={isPodcastMode}
            onTogglePodcastMode={setIsPodcastMode}
            playbackRate={playbackRate}
            onChangePlaybackRate={setPlaybackRate}

            sleepTimerMinutes={sleepTimerMinutes}
            sleepTimerRemainingSec={sleepTimerRemainingSec}
            onSetSleepTimer={setSleepTimerMinutes}

            deadzoneGuardMode={deadzoneGuardMode}
            onChangeDeadzoneGuard={setDeadzoneGuardMode}

            autoFetchMetadata={autoFetchMetadata}
            onToggleAutoFetchMetadata={setAutoFetchMetadata}

            isLoggedIn={isLoggedIn}
            username={username}
            musicFolder={musicFolder}
            onOpenFolderBrowser={() => setShowFolderBrowser(true)}
            onRescanMusic={() => scanNextcloudMusic(username, password, musicFolder)}
            isScanning={isScanning}
            onSignOut={handleSignOut}
            onOpenSignInModal={() => setShowLoginModal(true)}
            serverUrl={serverUrl}

            offlineStorageBytes={offlineStorageBytes}
            offlineTrackCount={offlineTrackIds.size}
            onBatchSyncCurrentFolder={handleBatchSyncCurrentFolder}
            isBatchSyncing={isBatchSyncing}
            batchSyncProgress={batchSyncProgress}
            onClearOfflineStorage={handleClearOfflineStorage}
            storageQuota={storageQuota}
            onRequestPersistentStorage={handleRequestPersistentStorage}
            offlineFolderName={offlineFolderName}
            offlineFolderHasHandle={Boolean(offlineFolderHandle)}
            offlineFolderPermission={offlineFolderPermission}
            isFolderPickerSupported={isFolderPickerSupported()}
            onPickOfflineFolder={handlePickOfflineFolder}
            onAuthorizeOfflineFolder={handleAuthorizeOfflineFolder}
            onClearOfflineFolder={handleClearOfflineFolder}

            autoResumeOnBoot={autoResumeOnBoot}
            onToggleAutoResumeOnBoot={setAutoResumeOnBoot}
            autoResumeSoftRamp={autoResumeSoftRamp}
            onToggleAutoResumeSoftRamp={setAutoResumeSoftRamp}

            idleScreensaverSeconds={idleScreensaverSeconds}
            onChangeIdleScreensaverSeconds={setIdleScreensaverSeconds}
            nightBrightness={nightBrightness}
            onChangeNightBrightness={setNightBrightness}
            warmAmberFilter={warmAmberFilter}
            onToggleWarmAmberFilter={setWarmAmberFilter}
            driverSafeMode={driverSafeMode}
            onToggleDriverSafeMode={setDriverSafeMode}
            swcBindings={swcBindings}
            onUpdateSwcBinding={(action: SteeringAction, keyBinding: string) => {
              setSwcBindings((prev) => ({ ...prev, [action]: keyBinding }));
              setStatusMessage(`Bound Steering Tap "${keyBinding}" to ${action}`);
              setTimeout(() => setStatusMessage(null), 2500);
            }}
            swcLongPressBindings={swcLongPressBindings}
            onUpdateSwcLongPressBinding={(action: SteeringAction, keyBinding: string) => {
              setSwcLongPressBindings((prev) => ({ ...prev, [action]: keyBinding }));
              setStatusMessage(`Bound Steering Long-Press "${keyBinding}" to ${action}`);
              setTimeout(() => setStatusMessage(null), 2500);
            }}
            onResetSwcBindings={() => {
              setSwcBindings({ ...DEFAULT_SWC_BINDINGS });
              setSwcLongPressBindings({ ...DEFAULT_SWC_LONG_PRESS_BINDINGS });
              setStatusMessage('Steering wheel short & long-press bindings reset.');
              setTimeout(() => setStatusMessage(null), 2500);
            }}
            onListeningSwcChange={(listening: boolean) => {
              isListeningSwcKeyRef.current = listening;
            }}

            driverProfiles={driverProfiles}
            activeDriverProfileId={activeDriverProfileId}
            onSelectDriverProfile={handleSelectDriverProfile}
            onSaveCurrentToDriverProfile={handleSaveCurrentToDriverProfile}

            autoPlayOnBluetooth={autoPlayOnBluetooth}
            onToggleAutoPlayOnBluetooth={setAutoPlayOnBluetooth}
            duckOnReverseGear={duckOnReverseGear}
            onToggleDuckOnReverseGear={setDuckOnReverseGear}
            globalAudioSessionAttach={globalAudioSessionAttach}
            onToggleGlobalAudioSessionAttach={setGlobalAudioSessionAttach}
            floatingMiniBarOpen={floatingMiniBarOpen}
            onToggleFloatingMiniBar={setFloatingMiniBarOpen}
            getAudioContext={() => webAudioCtxRef.current}

            onBuildRoadtripPlan={handleBuildRoadtripPlan}
            onPinRoadtripOffline={handlePinRoadtripOffline}

            replayGainEnabled={replayGainEnabled}
            onToggleReplayGain={setReplayGainEnabled}
            crossfadeSeconds={crossfadeSeconds}
            onChangeCrossfadeSeconds={setCrossfadeSeconds}
            smartRollingCache={smartRollingCache}
            onToggleSmartRollingCache={setSmartRollingCache}
            offlineCacheCapMb={offlineCacheCapMb}
            onChangeOfflineCacheCapMb={setOfflineCacheCapMb}

            localTrackCount={localTrackCount}
            onScanLocalFolder={handleScanLocalFolder}
            onScanLocalInputFiles={handleScanLocalInputFiles}
            onExportBackupJson={handleExportBackupJson}
            onImportBackupJson={handleImportBackupJson}
            onPushBackupCloud={handlePushBackupCloud}
            onPullBackupCloud={handlePullBackupCloud}
            isSyncingBackup={isSyncingBackup}

            diagnosticLogs={diagnosticLogs}
            onRunDiagnostics={runStreamDiagnostics}
            isDiagnosing={isDiagnosing}
            serverProxyLogs={serverProxyLogs}
            onFetchServerProxyLogs={fetchServerProxyLogs}
            isLoadingServerLogs={isLoadingServerLogs}
            onClearDiagnostics={() => {
              setDiagnosticLogs([]);
              clearServerProxyLogs();
            }}
            onCopyDiagnosticReport={copyDiagnosticReport}
            copiedReport={copiedReport}
          />
        )}
      </main>

      {/* INTERACTIVE NEXTCLOUD FOLDER BROWSER MODAL */}
      {showFolderBrowser && (
        <div className="fixed inset-0 bg-black/85 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div
            className="w-full max-w-xl bg-[#0b1120] border rounded-3xl p-6 shadow-2xl flex flex-col max-h-[85vh] space-y-4"
            style={{ borderColor: activeAccent.border }}
          >
            {/* Header */}
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2.5">
                <FolderOpen className="w-5 h-5" style={{ color: activeAccent.hex }} />
                <div>
                  <h3 className="text-base font-bold text-white">Nextcloud Folder Browser</h3>
                  <p className="text-[11px] text-slate-400">Select where your music files or albums are located</p>
                </div>
              </div>
              <button
                onClick={() => setShowFolderBrowser(false)}
                className="text-slate-400 hover:text-white text-sm p-1"
              >
                ✕
              </button>
            </div>

            {/* Quick Path Input, Default Folder Shortcuts & Breadcrumbs */}
            <div className="space-y-2.5">
              {/* Quick-jump to default & added folders */}
              <div className="flex items-center gap-1.5 flex-wrap">
                <span className="text-[10px] font-bold uppercase tracking-wider text-slate-500 mr-1">Defaults:</span>
                {DEFAULT_MUSIC_FOLDERS.map((defFolder) => (
                  <button
                    key={defFolder}
                    type="button"
                    onClick={() => loadBrowserFolder(defFolder)}
                    className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition-colors ${
                      normalizeFolderPath(browserCurrentPath).toLowerCase() === defFolder.toLowerCase()
                        ? 'text-black border-transparent font-bold'
                        : 'bg-black/50 text-slate-300 border-slate-800 hover:border-slate-600'
                    }`}
                    style={
                      normalizeFolderPath(browserCurrentPath).toLowerCase() === defFolder.toLowerCase()
                        ? { backgroundColor: activeAccent.hex }
                        : {}
                    }
                  >
                    /{defFolder}
                  </button>
                ))}
                <button
                  type="button"
                  onClick={() => loadBrowserFolder('')}
                  className={`px-2.5 py-1 rounded-lg text-[11px] font-semibold border transition-colors ${
                    normalizeFolderPath(browserCurrentPath) === ''
                      ? 'text-black border-transparent font-bold'
                      : 'bg-black/50 text-slate-300 border-slate-800 hover:border-slate-600'
                  }`}
                  style={normalizeFolderPath(browserCurrentPath) === '' ? { backgroundColor: activeAccent.hex } : {}}
                >
                  / (Root)
                </button>
                {extraScanFolders.map((extra) => (
                  <span
                    key={extra}
                    className="inline-flex items-center gap-1 px-2 py-1 rounded-lg text-[11px] font-semibold bg-slate-900 border border-slate-700 text-slate-200"
                  >
                    <button
                      type="button"
                      onClick={() => loadBrowserFolder(extra === '/' ? '' : extra)}
                      className="hover:underline font-mono"
                    >
                      /{extra === '/' ? '' : extra}
                    </button>
                    <button
                      type="button"
                      onClick={() => removeExtraScanFolder(extra)}
                      className="text-slate-500 hover:text-red-400 pl-0.5"
                      title="Remove from auto-scan list"
                    >
                      ×
                    </button>
                  </span>
                ))}
              </div>

              <div className="flex gap-2">
                <div className="relative flex-1">
                  <span className="absolute left-3 top-2 text-xs text-slate-500 font-mono">/</span>
                  <input
                    type="text"
                    value={manualPathInput}
                    onChange={(e) => setManualPathInput(e.target.value)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        loadBrowserFolder(manualPathInput.trim());
                      }
                    }}
                    placeholder="e.g. WoW Radio, Music, Podcasts, or empty for root"
                    className="w-full bg-black/60 border border-slate-800 rounded-xl pl-6 pr-3 py-1.5 text-xs text-white focus:outline-none focus:border-slate-600 font-mono"
                  />
                </div>
                <button
                  onClick={() => loadBrowserFolder(manualPathInput.trim())}
                  className="px-3.5 py-1.5 rounded-xl text-xs font-bold bg-slate-800 hover:bg-slate-700 text-white transition-colors"
                >
                  Open
                </button>
              </div>

              {/* Navigation controls & Breadcrumbs */}
              <div className="flex items-center justify-between gap-2 text-xs text-slate-400 pt-1">
                <div className="flex items-center gap-1.5 overflow-x-auto py-1">
                  <button
                    onClick={() => loadBrowserFolder('')}
                    className="hover:text-white underline decoration-dotted font-mono shrink-0"
                  >
                    Root (/)
                  </button>
                  {browserCurrentPath.split('/').filter(Boolean).map((seg, idx, arr) => {
                    const segPath = arr.slice(0, idx + 1).join('/');
                    const isLast = idx === arr.length - 1;
                    return (
                      <React.Fragment key={idx}>
                        <ChevronRight className="w-3 h-3 text-slate-600 shrink-0" />
                        <button
                          onClick={() => loadBrowserFolder(segPath)}
                          className={`shrink-0 font-mono ${
                            isLast ? 'text-white font-bold' : 'hover:text-white underline decoration-dotted'
                          }`}
                        >
                          {seg}
                        </button>
                      </React.Fragment>
                    );
                  })}
                </div>

                {browserCurrentPath && (
                  <button
                    onClick={navigateBrowserUp}
                    className="px-2.5 py-1 rounded-lg bg-black/60 border border-slate-800 text-[11px] text-slate-300 hover:text-white flex items-center gap-1 shrink-0"
                  >
                    <ArrowUp className="w-3 h-3" />
                    Up (..)
                  </button>
                )}
              </div>
            </div>

            {/* Folder Contents Display */}
            <div className="flex-1 overflow-y-auto min-h-[220px] bg-black/40 rounded-2xl border border-slate-800/80 p-2 divide-y divide-slate-800/40 custom-scrollbar">
              {isBrowserLoading ? (
                <div className="p-8 text-center text-xs text-slate-400 flex flex-col items-center justify-center gap-2">
                  <RefreshCw className="w-5 h-5 animate-spin" style={{ color: activeAccent.hex }} />
                  <span>Loading directory contents from cloud.zentryx.bg...</span>
                </div>
              ) : browserError ? (
                <div className="p-6 text-center space-y-3">
                  <div className="text-xs text-red-400">{browserError}</div>
                  <button
                    onClick={() => loadBrowserFolder('')}
                    className="px-3 py-1.5 rounded-xl text-xs font-bold bg-slate-800 text-white"
                  >
                    Return to Root (/)
                  </button>
                </div>
              ) : browserFolders.length === 0 && browserAudioFiles.length === 0 ? (
                <div className="p-8 text-center text-xs text-slate-500">
                  No folders or audio files found in this directory.
                </div>
              ) : (
                <>
                  {/* Folders */}
                  {browserFolders.map((fld, idx) => (
                    <div
                      key={`dir-${idx}`}
                      className="p-2.5 rounded-xl hover:bg-slate-800/50 flex items-center justify-between transition-colors group gap-2"
                    >
                      <button
                        type="button"
                        onClick={() => loadBrowserFolder(fld.path)}
                        className="flex items-center gap-2.5 min-w-0 flex-1 text-left"
                      >
                        <Folder className="w-4 h-4 shrink-0" style={{ color: activeAccent.hex }} />
                        <span className="font-semibold text-xs text-slate-200 group-hover:text-white truncate">
                          {fld.name}
                        </span>
                      </button>
                      <div className="flex items-center gap-1.5 shrink-0">
                        <button
                          type="button"
                          onClick={() => addAndScanBrowserFolder(fld.path, true)}
                          className="px-2.5 py-1 rounded-lg text-[11px] font-bold text-black transition-all"
                          style={{ backgroundColor: activeAccent.hex }}
                          title={`Scan /${fld.path} and add its songs to your existing library`}
                        >
                          + Add
                        </button>
                        <button
                          type="button"
                          onClick={() => loadBrowserFolder(fld.path)}
                          className="flex items-center gap-0.5 px-2 py-1 rounded-lg text-[11px] text-slate-400 hover:text-white bg-black/40 border border-slate-800"
                        >
                          <span>Open</span>
                          <ChevronRight className="w-3.5 h-3.5" />
                        </button>
                      </div>
                    </div>
                  ))}

                  {/* Audio files */}
                  {browserAudioFiles.map((file, idx) => (
                    <div
                      key={`file-${idx}`}
                      className="p-2.5 rounded-xl flex items-center justify-between text-xs text-slate-300"
                    >
                      <div className="flex items-center gap-2.5 min-w-0 pr-2">
                        <FileAudio className="w-4 h-4 text-slate-400 shrink-0" />
                        <span className="truncate">{file.fileName}</span>
                      </div>
                      <span className="text-[11px] text-slate-500 font-mono shrink-0">
                        {formatBytes(file.size)}
                      </span>
                    </div>
                  ))}
                </>
              )}
            </div>

            {/* Selection Status & Actions */}
            <div className="pt-3 border-t border-slate-800 space-y-3">
              <div className="flex items-center justify-between gap-2 text-xs text-slate-400 flex-wrap">
                <div className="flex items-center gap-2 min-w-0">
                  <span>Selected Directory:</span>
                  <span className="font-mono text-white font-semibold truncate">/{browserCurrentPath || 'Root'}</span>
                </div>
                <div className="flex items-center gap-1.5 shrink-0 flex-wrap">
                  <button
                    type="button"
                    onClick={() => {
                      setShowFolderBrowser(false);
                      handleScanLocalFolder();
                    }}
                    className="px-3 py-1.5 rounded-xl font-bold text-[11px] text-slate-200 bg-black/60 hover:bg-slate-800 border border-slate-700 flex items-center gap-1.5 shrink-0 transition-colors"
                    title="Pick a local or USB folder and merge its audio files into your library"
                  >
                    <HardDrive className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                    + Local / USB Folder
                  </button>
                  <button
                    type="button"
                    disabled={isBatchSyncing}
                    onClick={() => handlePinFolderOffline(browserCurrentPath)}
                    className="px-3 py-1.5 rounded-xl font-bold text-[11px] text-black flex items-center gap-1.5 shrink-0 disabled:opacity-50"
                    style={{ backgroundColor: activeAccent.hex }}
                  >
                    <Download className="w-3.5 h-3.5" />
                    Pin Offline
                  </button>
                  <button
                    type="button"
                    onClick={() => runFolderScanDiagnostics(browserCurrentPath)}
                    className="px-3 py-1.5 rounded-xl font-bold text-[11px] text-white bg-slate-800 hover:bg-slate-700 border border-slate-700 flex items-center gap-1.5 shrink-0 transition-colors"
                  >
                    <Network className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                    Scan Diagnostics
                  </button>
                </div>
              </div>

              <div className="flex flex-col sm:flex-row gap-2">
                <button
                  onClick={() => addAndScanBrowserFolder(browserCurrentPath, true)}
                  disabled={isBrowserLoading}
                  className="flex-1 py-3 rounded-xl font-bold text-xs uppercase tracking-wider text-black flex items-center justify-center gap-2 transition-all shadow"
                  style={{ backgroundColor: activeAccent.hex }}
                  title="Scan this folder & subfolders and append tracks to your existing discovered library"
                >
                  <FolderCheck className="w-4 h-4" />
                  + Scan &amp; Add Folder to Library
                </button>

                <button
                  onClick={() => selectAndScanBrowserFolder(browserCurrentPath, true)}
                  disabled={isBrowserLoading}
                  className="px-4 py-3 rounded-xl font-bold text-xs text-slate-200 bg-slate-800 hover:bg-slate-700 border border-slate-700 transition-colors"
                  title="Replace the primary scan folder with only this folder"
                >
                  Replace Only
                </button>

                <button
                  onClick={() => addAndScanBrowserFolder(browserCurrentPath, false)}
                  disabled={isBrowserLoading}
                  className="px-3.5 py-3 rounded-xl font-bold text-xs text-slate-300 bg-black/60 border border-slate-800 hover:border-slate-700 transition-colors"
                  title="Add only audio files directly inside this directory (no subfolders)"
                >
                  + Direct Only
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* NEXTCLOUD WEBDAV FOLDER SCAN DIAGNOSTICS MODAL */}
      {showScanDiagnosticsModal && (
        <div className="fixed inset-0 bg-black/85 backdrop-blur-sm z-[60] flex items-center justify-center p-4">
          <div
            className="w-full max-w-2xl bg-[#0b1120] border rounded-3xl p-6 shadow-2xl flex flex-col max-h-[88vh] space-y-4"
            style={{ borderColor: activeAccent.border }}
          >
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2.5 min-w-0">
                <Network className="w-5 h-5 shrink-0" style={{ color: activeAccent.hex }} />
                <div className="min-w-0">
                  <h3 className="text-base font-bold text-white">Nextcloud WebDAV Scan Diagnostics</h3>
                  <p className="text-[11px] text-slate-400 truncate">
                    {scanDiagnosticsRunning
                      ? scanDiagnosticsStepText
                      : 'Step-by-step HTTP status codes & Nextcloud folder permissions'}
                  </p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => {
                  scanDiagRunIdRef.current += 1;
                  setScanDiagnosticsRunning(false);
                  setShowScanDiagnosticsModal(false);
                }}
                className="text-slate-400 hover:text-white text-sm p-1"
              >
                ✕
              </button>
            </div>

            {scanDiagnosticsRunning && (
              <div className="w-full h-1 bg-slate-800 rounded-full overflow-hidden">
                <div
                  className="h-full w-1/2 animate-pulse rounded-full"
                  style={{ backgroundColor: activeAccent.hex }}
                />
              </div>
            )}

            {scanDiagnosticsSummary && (
              <div
                className={`px-3.5 py-2.5 rounded-xl text-xs font-semibold border ${
                  scanDiagnosticsEntries.some((e) => e.status === 'FAIL')
                    ? 'bg-red-500/10 border-red-500/40 text-red-300'
                    : scanDiagnosticsEntries.some((e) => e.status === 'WARN')
                    ? 'bg-amber-500/10 border-amber-500/40 text-amber-300'
                    : 'bg-emerald-500/10 border-emerald-500/40 text-emerald-300'
                }`}
              >
                {scanDiagnosticsSummary}
              </div>
            )}

            <div className="flex-1 overflow-y-auto space-y-2 pr-1 min-h-[180px]">
              {scanDiagnosticsEntries.map((entry) => {
                const badgeClasses =
                  entry.status === 'PASS'
                    ? 'bg-emerald-500/15 text-emerald-300 border-emerald-500/40'
                    : entry.status === 'WARN'
                    ? 'bg-amber-500/15 text-amber-300 border-amber-500/40'
                    : entry.status === 'FAIL'
                    ? 'bg-red-500/15 text-red-300 border-red-500/40'
                    : 'bg-slate-700/40 text-slate-200 border-slate-600/50';
                const httpLabel =
                  entry.httpStatusCode !== null
                    ? `HTTP ${entry.httpStatusCode}`
                    : entry.status === 'RUNNING'
                    ? '...'
                    : 'ERR';
                return (
                  <div
                    key={entry.id}
                    className="p-3 rounded-xl bg-black/50 border border-slate-800/80 space-y-1"
                  >
                    <div className="flex items-center justify-between gap-2">
                      <span className="text-xs font-bold text-white truncate">{entry.stepTitle}</span>
                      <div className="flex items-center gap-2 shrink-0">
                        {entry.latencyMs > 0 && (
                          <span className="text-[10px] font-mono text-slate-500">{entry.latencyMs} ms</span>
                        )}
                        <span className={`px-2 py-0.5 rounded text-[10px] font-mono font-bold border ${badgeClasses}`}>
                          {httpLabel}
                        </span>
                      </div>
                    </div>
                    <div className="text-[11px] font-mono truncate" style={{ color: activeAccent.hex }}>
                      {entry.targetPath}
                    </div>
                    <div className="text-[11px] text-sky-300 font-medium">
                      Permissions: {entry.permissionsLabel}
                    </div>
                    <div className="text-[11px] text-slate-400">{entry.detailMessage}</div>
                  </div>
                );
              })}
            </div>

            <div className="pt-2 border-t border-slate-800 flex items-center justify-end gap-2">
              <button
                type="button"
                disabled={scanDiagnosticsRunning}
                onClick={() => runFolderScanDiagnostics(scanDiagnosticsStartPath)}
                className="px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 hover:bg-slate-700 disabled:opacity-50 text-white border border-slate-700 flex items-center gap-1.5"
              >
                <RefreshCw className={`w-3.5 h-3.5 ${scanDiagnosticsRunning ? 'animate-spin' : ''}`} />
                Re-run Check
              </button>
              <button
                type="button"
                onClick={() => {
                  scanDiagRunIdRef.current += 1;
                  setScanDiagnosticsRunning(false);
                  setShowScanDiagnosticsModal(false);
                }}
                className="px-4 py-2 rounded-xl text-xs font-bold text-black"
                style={{ backgroundColor: activeAccent.hex }}
              >
                Close
              </button>
            </div>
          </div>
        </div>
      )}

      {/* LOGIN MODAL */}
      {showLoginModal && (
        <div className="fixed inset-0 bg-black/80 backdrop-blur-sm z-50 flex items-center justify-center p-4">
          <div
            className="w-full max-w-lg bg-[#0b1120] border rounded-3xl p-6 shadow-2xl space-y-5"
            style={{ borderColor: activeAccent.border }}
          >
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-2.5">
                <Cloud className="w-5 h-5" style={{ color: activeAccent.hex }} />
                <h3 className="text-lg font-bold text-white">Nextcloud Account</h3>
              </div>
              <button
                onClick={() => {
                  stopLoginFlow();
                  setShowLoginModal(false);
                }}
                className="text-slate-400 hover:text-white text-sm"
              >
                ✕
              </button>
            </div>

            {/* Auth Method Tabs */}
            <div className="grid grid-cols-2 p-1 bg-black/60 rounded-xl border border-slate-800 text-xs font-semibold">
              <button
                onClick={() => setLoginMethod('flow')}
                className={`py-2 rounded-lg transition-colors flex items-center justify-center gap-1.5 ${
                  loginMethod === 'flow' ? 'text-black font-bold' : 'text-slate-400 hover:text-white'
                }`}
                style={loginMethod === 'flow' ? { backgroundColor: activeAccent.hex } : {}}
              >
                <Sparkles className="w-3.5 h-3.5" />
                1-Click Browser Login
              </button>
              <button
                onClick={() => setLoginMethod('manual')}
                className={`py-2 rounded-lg transition-colors flex items-center justify-center gap-1.5 ${
                  loginMethod === 'manual' ? 'text-black font-bold' : 'text-slate-400 hover:text-white'
                }`}
                style={loginMethod === 'manual' ? { backgroundColor: activeAccent.hex } : {}}
              >
                <KeyRound className="w-3.5 h-3.5" />
                App Token / Password
              </button>
            </div>

            {loginMethod === 'flow' ? (
              /* TAB 1: 1-Click Login Flow v2 */
              <div className="space-y-4">
                <div className="p-4 rounded-2xl bg-black/40 border border-slate-800 text-xs text-slate-300 space-y-2">
                  <div className="font-semibold text-white flex items-center gap-1.5">
                    <ShieldCheck className="w-4 h-4 text-lime-400" />
                    Official Nextcloud Seamless Authorization
                  </div>
                  <p className="text-slate-400 text-[11px] leading-relaxed">
                    Log in directly via <span className="font-mono text-slate-300">cloud.zentryx.bg</span> in your browser. Nextcloud will generate a dedicated app token automatically. Works with 2FA, SSO, and standard passwords without password mismatch.
                  </p>
                </div>

                {!flowLoginUrl ? (
                  <button
                    onClick={startLoginFlow}
                    disabled={isConnecting}
                    className="w-full py-3.5 rounded-xl font-bold text-xs uppercase tracking-wider text-black flex items-center justify-center gap-2 transition-all shadow-lg active:scale-95"
                    style={{ backgroundColor: activeAccent.hex }}
                  >
                    {isConnecting ? <RefreshCw className="w-4 h-4 animate-spin" /> : <Cloud className="w-4 h-4" />}
                    {isConnecting ? 'Initializing Nextcloud...' : 'Start 1-Click Authorization'}
                  </button>
                ) : (
                  <div className="space-y-3.5 p-4 bg-black/60 border border-slate-800 rounded-2xl">
                    <div className="flex items-center gap-2 text-xs text-lime-400 font-medium">
                      <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                      <span>{flowStatus || 'Waiting for you to grant access in Nextcloud...'}</span>
                    </div>

                    <div className="text-xs text-slate-300 space-y-1">
                      <div>1. Click below to open Nextcloud in a new browser tab.</div>
                      <div>2. Click <strong className="text-white">"Grant access"</strong> to authorize Zentryx Music.</div>
                      <div className="text-[11px] text-slate-400">3. This window will automatically detect approval and load your files!</div>
                    </div>

                    <a
                      href={flowLoginUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="w-full py-3 rounded-xl font-bold text-xs uppercase tracking-wider text-black flex items-center justify-center gap-2 transition-all shadow block text-center"
                      style={{ backgroundColor: activeAccent.hex }}
                    >
                      <ExternalLink className="w-4 h-4" />
                      Open Nextcloud Authorization Page
                    </a>

                    <div className="pt-1 flex items-center justify-between text-[11px] text-slate-500">
                      <span>cloud.zentryx.bg</span>
                      <button
                        onClick={stopLoginFlow}
                        className="text-red-400 hover:text-red-300 transition-colors"
                      >
                        Cancel
                      </button>
                    </div>
                  </div>
                )}
              </div>
            ) : (
              /* TAB 2: Manual Credentials / App Password */
              <div className="space-y-3.5">
                <div>
                  <label className="block text-xs text-slate-400 mb-1">Server URL</label>
                  <input
                    type="text"
                    value={serverUrl}
                    onChange={(e) => setServerUrl(e.target.value)}
                    className="w-full bg-black/60 border border-slate-800 rounded-xl px-3.5 py-2.5 text-xs text-white focus:outline-none focus:border-slate-600"
                  />
                </div>

                <div>
                  <label className="block text-xs text-slate-400 mb-1">Username or Email</label>
                  <input
                    type="text"
                    value={username}
                    onChange={(e) => setUsername(e.target.value)}
                    className="w-full bg-black/60 border border-slate-800 rounded-xl px-3.5 py-2.5 text-xs text-white focus:outline-none focus:border-slate-600"
                    placeholder="Nextcloud username or email"
                  />
                </div>

                <div>
                  <div className="flex items-center justify-between mb-1">
                    <label className="block text-xs text-slate-400">App Password or Token</label>
                    <span className="text-[10px] text-slate-500">Nextcloud App Password recommended</span>
                  </div>
                  <div className="relative">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(e) => setPassword(e.target.value)}
                      className="w-full bg-black/60 border border-slate-800 rounded-xl px-3.5 py-2.5 pr-10 text-xs text-white focus:outline-none focus:border-slate-600"
                      placeholder="Password or Nextcloud App Token"
                    />
                    <button
                      type="button"
                      onClick={() => setShowPassword(!showPassword)}
                      className="absolute right-3 top-2.5 text-slate-400 hover:text-white"
                    >
                      {showPassword ? <EyeOff className="w-4 h-4" /> : <Eye className="w-4 h-4" />}
                    </button>
                  </div>
                  <p className="text-[10px] text-slate-500 mt-1 leading-normal">
                    Tip: If your web password fails, generate an App Password in Nextcloud: <strong>Settings &gt; Security &gt; Devices &amp; client credentials &gt; Create new app password</strong>, or use the 1-Click tab.
                  </p>
                </div>

                <div>
                  <label className="block text-xs text-slate-400 mb-1">Default Music Folders (comma-separated)</label>
                  <input
                    type="text"
                    value={musicFolder}
                    onChange={(e) => setMusicFolder(e.target.value)}
                    className="w-full bg-black/60 border border-slate-800 rounded-xl px-3.5 py-2.5 text-xs text-white focus:outline-none focus:border-slate-600"
                    placeholder="WoW Radio, Music (or / for root)"
                  />
                </div>

                <div className="pt-2">
                  <button
                    onClick={handleManualConnect}
                    disabled={isConnecting}
                    className="w-full py-3 rounded-xl font-bold text-xs uppercase tracking-wider text-black flex items-center justify-center gap-2 transition-all shadow"
                    style={{ backgroundColor: activeAccent.hex }}
                  >
                    {isConnecting ? <RefreshCw className="w-4 h-4 animate-spin" /> : null}
                    {isConnecting ? 'Verifying & Syncing Files...' : 'Verify & Load Files'}
                  </button>
                </div>
              </div>
            )}

            {loginError && (
              <div className="text-xs text-red-400 bg-red-950/50 border border-red-800/50 p-3 rounded-xl leading-relaxed">
                {loginError}
              </div>
            )}
          </div>
        </div>
      )}

      {/* AUDIO STREAM / CODEC & SIGNAL PATH INSPECTOR SHEET */}
      {isStreamInspectorOpen && currentTrack && (
        <div className="fixed inset-0 bg-black/85 backdrop-blur-sm z-[70] flex items-center justify-center p-4">
          <div
            className="w-full max-w-lg bg-[#0b1120] border rounded-3xl p-6 shadow-2xl space-y-4"
            style={{ borderColor: activeAccent.border }}
          >
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <div className="flex items-center gap-2.5 min-w-0">
                <Headphones className="w-5 h-5 shrink-0" style={{ color: activeAccent.hex }} />
                <div className="min-w-0">
                  <h3 className="text-base font-bold text-white">Audio Stream &amp; DSP Signal Path</h3>
                  <p className="text-[11px] text-slate-400 truncate">{currentTrack.fileName}</p>
                </div>
              </div>
              <button
                type="button"
                onClick={() => setIsStreamInspectorOpen(false)}
                className="text-slate-400 hover:text-white text-sm p-1"
              >
                ✕
              </button>
            </div>

            <div className="grid grid-cols-2 gap-2.5 text-xs">
              <div className="p-3 rounded-2xl bg-black/50 border border-slate-800/80">
                <div className="text-[10px] text-slate-500 uppercase">Codec / Format</div>
                <div className="font-mono font-bold text-white mt-0.5">
                  {currentTrack.codec || currentTrack.fileName.split('.').pop()?.toUpperCase() || 'AUDIO'}
                </div>
              </div>
              <div className="p-3 rounded-2xl bg-black/50 border border-slate-800/80">
                <div className="text-[10px] text-slate-500 uppercase">Sample Rate / Bitrate</div>
                <div className="font-mono font-bold text-white mt-0.5">
                  {currentTrack.sampleRateHz ? `${(currentTrack.sampleRateHz / 1000).toFixed(1)} kHz` : 'Standard'} ·{' '}
                  {currentTrack.bitrateKbps ? `${currentTrack.bitrateKbps} kbps` : 'VBR/Lossless'}
                </div>
              </div>
              <div className="p-3 rounded-2xl bg-black/50 border border-slate-800/80">
                <div className="text-[10px] text-slate-500 uppercase">Source Transport</div>
                <div className="font-mono font-bold text-white mt-0.5">
                  {offlineTrackIds.has(currentTrack.id)
                    ? 'Offline Cache (0ms)'
                    : currentTrack.sourceType === 'LOCAL'
                    ? 'Local USB Storage'
                    : 'Nextcloud WebDAV'}
                </div>
              </div>
              <div className="p-3 rounded-2xl bg-black/50 border border-slate-800/80">
                <div className="text-[10px] text-slate-500 uppercase">Output Route</div>
                <div className="font-mono font-bold text-white mt-0.5">
                  {AUDIO_OUTPUT_ROUTES.find((r) => r.id === activeOutputRoute)?.label || activeOutputRoute}
                </div>
              </div>
              <div className="p-3 rounded-2xl bg-black/50 border border-slate-800/80">
                <div className="text-[10px] text-slate-500 uppercase">DSP Pipeline</div>
                <div className="font-mono font-bold mt-0.5" style={{ color: dspSettings.dspEnabled && !isHoldBypassed ? activeAccent.hex : '#94a3b8' }}>
                  {isHoldBypassed ? 'MOMENTARY BYPASS' : dspSettings.dspEnabled ? `ACTIVE (${dspSettings.selectedPresetId.toUpperCase()})` : 'BYPASSED'}
                </div>
              </div>
              <div className="p-3 rounded-2xl bg-black/50 border border-slate-800/80">
                <div className="text-[10px] text-slate-500 uppercase">Crossovers &amp; Limiter</div>
                <div className="font-mono font-bold text-white mt-0.5">
                  HPF {dspSettings.hpfCutoffHz || 'OFF'}Hz · LPF {dspSettings.subLpfHz || 'OFF'}Hz · {dspSettings.peakLimiterCeilingDb}dB
                </div>
              </div>
              <div className="p-3 rounded-2xl bg-black/50 border border-slate-800/80">
                <div className="text-[10px] text-slate-500 uppercase">Stage &amp; Sub Trim</div>
                <div className="font-mono font-bold text-white mt-0.5">
                  Bal {dspSettings.balance} / Fad {dspSettings.fader} · Sub {dspSettings.subTrimDb >= 0 ? `+${dspSettings.subTrimDb}` : dspSettings.subTrimDb}dB ({dspSettings.subPhaseInverted ? '180°' : '0°'})
                </div>
              </div>
              <div className="p-3 rounded-2xl bg-black/50 border border-slate-800/80">
                <div className="text-[10px] text-slate-500 uppercase">Dynamics / ReplayGain</div>
                <div className="font-mono font-bold text-white mt-0.5">
                  {dspSettings.monoFoldDown ? 'Mono Sum' : 'Stereo'} ·{' '}
                  {currentTrack.replayGainDb !== undefined ? `RG ${currentTrack.replayGainDb.toFixed(1)}dB` : 'No RG tag'}
                </div>
              </div>
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-slate-800">
              <button
                type="button"
                onClick={() => {
                  setIsStreamInspectorOpen(false);
                  navigateToMenu({ tab: 'settings' });
                }}
                className="px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 hover:bg-slate-700 text-white border border-slate-700 flex items-center gap-1.5"
              >
                <Sliders className="w-3.5 h-3.5" style={{ color: activeAccent.hex }} />
                Open DSP Console
              </button>
              <button
                type="button"
                onClick={() => setIsStreamInspectorOpen(false)}
                className="px-4 py-2 rounded-xl text-xs font-bold text-black"
                style={{ backgroundColor: activeAccent.hex }}
              >
                Done
              </button>
            </div>
          </div>
        </div>
      )}

      {/* DRIVE MODE SIMPLIFIED LARGE-TARGET HUD OVERLAY */}
      {isDriveModeOpen && (
        <div className="fixed inset-0 z-[80] bg-[#020617] flex flex-col justify-between p-6 sm:p-10">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-3">
              <Car className="w-6 h-6" style={{ color: activeAccent.hex }} />
              <span className="text-sm font-extrabold uppercase tracking-widest text-white">Drive Mode HUD</span>
              <span className="px-3 py-1 rounded-xl bg-[#0b1120] border border-slate-800 text-xs font-mono font-bold text-slate-200">
                {currentGpsSpeed} km/h
              </span>
            </div>
            <button
              type="button"
              onClick={() => {
                driveModeDismissedAtSpeedRef.current = true;
                setIsDriveModeOpen(false);
              }}
              className="px-5 py-3 rounded-2xl bg-slate-900 hover:bg-slate-800 border border-slate-700 text-sm font-bold text-white"
            >
              Exit Drive Mode ✕
            </button>
          </div>

          <div className="max-w-4xl w-full mx-auto flex flex-col items-center text-center space-y-6">
            {currentTrack && (
              <>
                <div className="flex items-center gap-6">
                  <div
                    className="w-28 h-28 sm:w-36 sm:h-36 rounded-3xl bg-[#0b1120] border-2 overflow-hidden shrink-0"
                    style={{ borderColor: activeAccent.hex }}
                  >
                    <CrossfadeAlbumArt
                      src={coverArtUrls[currentTrack.id]}
                      alt={currentTrack.title}
                      trackKey={currentTrack.id}
                      durationMs={300}
                      className="w-full h-full"
                      fallback={
                        <Disc className={`w-16 h-16 ${isPlaying ? 'animate-spin' : ''}`} style={{ color: activeAccent.hex }} />
                      }
                    />
                  </div>
                  <div className="text-left min-w-0 max-w-xl">
                    <h2 className="text-3xl sm:text-5xl font-extrabold text-white line-clamp-2 leading-tight">
                      {currentTrack.title}
                    </h2>
                    <p className="text-xl sm:text-2xl text-slate-300 font-semibold mt-2 truncate">
                      {currentTrack.artist}
                    </p>
                  </div>
                </div>

                <div className="w-full max-w-2xl space-y-2">
                  <div className="w-full h-3 bg-slate-800 rounded-full overflow-hidden">
                    <div
                      className="h-full rounded-full transition-all"
                      style={{
                        width: `${durationSeconds > 0 ? Math.min(100, (positionSeconds / durationSeconds) * 100) : 0}%`,
                        backgroundColor: activeAccent.hex,
                      }}
                    />
                  </div>
                  <div className="flex justify-between text-base font-mono font-bold text-slate-300">
                    <span>{formatTime(positionSeconds)}</span>
                    <span>{formatTime(durationSeconds)}</span>
                  </div>
                </div>
              </>
            )}

            <div className="flex items-center justify-center gap-6 sm:gap-10 pt-4">
              <button
                type="button"
                onClick={handlePrevTrack}
                className="w-24 h-24 sm:w-28 sm:h-28 rounded-3xl bg-slate-900 border-2 border-slate-700 text-white flex items-center justify-center active:scale-95 shadow-2xl"
              >
                <SkipBack className="w-11 h-11" />
              </button>

              <button
                type="button"
                onClick={togglePlayPause}
                className="w-32 h-32 sm:w-36 sm:h-36 rounded-[2rem] text-black flex items-center justify-center active:scale-95 shadow-2xl"
                style={{ backgroundColor: activeAccent.hex }}
              >
                {isPlaying ? <Pause className="w-14 h-14 stroke-[2.5]" /> : <Play className="w-14 h-14 ml-1 stroke-[2.5]" />}
              </button>

              <button
                type="button"
                onClick={() => handleNextTrack()}
                className="w-24 h-24 sm:w-28 sm:h-28 rounded-3xl bg-slate-900 border-2 border-slate-700 text-white flex items-center justify-center active:scale-95 shadow-2xl"
              >
                <SkipForward className="w-11 h-11" />
              </button>
            </div>
          </div>

          <div className="flex items-center justify-center gap-4">
            <button
              type="button"
              onClick={handleToggleAttenuation}
              className={`px-6 py-4 rounded-2xl text-sm font-bold border flex items-center gap-2 ${
                isAttenuated
                  ? 'bg-amber-400 text-black border-amber-300'
                  : 'bg-[#0b1120] text-slate-200 border-slate-800'
              }`}
            >
              <VolumeX className="w-5 h-5" />
              {isAttenuated ? 'Un-Duck Volume' : 'Talk Duck (-20dB)'}
            </button>
            <button
              type="button"
              onClick={handleStartVoiceSearch}
              className="px-6 py-4 rounded-2xl text-sm font-bold bg-[#0b1120] border border-slate-800 text-white flex items-center gap-2"
            >
              <Mic className="w-5 h-5" style={{ color: activeAccent.hex }} />
              Voice Command
            </button>
          </div>
        </div>
      )}

      {/* PERSISTENT STICKY MINI-PLAYER (Hidden on Player view & on xl Dual-Pane Library view) */}
      {activeTab !== 'player' && currentTrack && (
        <div className={dualPaneCockpit && activeTab === 'library' ? 'xl:hidden' : ''}>
          <StickyMiniPlayer
            currentTrack={currentTrack}
            isPlaying={isPlaying}
            positionSeconds={positionSeconds}
            durationSeconds={durationSeconds}
            onTogglePlayPause={togglePlayPause}
            onNextTrack={() => handleNextTrack()}
            onOpenPlayer={() => {
              triggerHaptic();
              setActiveTab('player');
            }}
            accentHex={activeAccent.hex}
            coverUrl={coverArtUrls[currentTrack.id]}
          />
        </div>
      )}

      {/* UP-NEXT PLAY QUEUE SLIDE-OVER DRAWER */}
      <QueueDrawer
        isOpen={isQueueOpen}
        onClose={() => setIsQueueOpen(false)}
        queue={queue}
        currentTrackIndex={currentTrackIndex}
        onPlayQueueIndex={handlePlayQueueIndex}
        onMoveUp={handleMoveQueueUp}
        onMoveDown={handleMoveQueueDown}
        onRemoveFromQueue={handleRemoveFromQueue}
        onClearQueue={handleClearQueue}
        accentHex={activeAccent.hex}
      />

      {/* PLAYLISTS CREATION & ASSIGNMENT MODAL */}
      <PlaylistsModal
        isOpen={isPlaylistsOpen}
        onClose={() => {
          setIsPlaylistsOpen(false);
          setTrackForPlaylist(null);
        }}
        playlists={playlists}
        onCreatePlaylist={handleCreatePlaylist}
        onDeletePlaylist={handleDeletePlaylist}
        onPlayPlaylist={handlePlayPlaylist}
        onAddTrackToPlaylist={handleAddTrackToPlaylist}
        onExportM3u8={handleExportPlaylistM3u8}
        onImportM3u8File={handleImportM3u8File}
        onSyncPlaylistsCloud={handleSyncPlaylistsCloud}
        isSyncingPlaylists={isSyncingPlaylists}
        isLoggedIn={isLoggedIn}
        trackToAdd={trackForPlaylist}
        accentHex={activeAccent.hex}
      />

      {/* DRAGGABLE FLOATING TRANSLUCENT COCKPIT PiP AUDIO BAR */}
      <FloatingCockpitBar
        visible={floatingMiniBarOpen}
        title={currentTrack?.title || 'Ready to Play'}
        artist={currentTrack?.artist || 'Zentryx Music'}
        coverUrl={currentTrack ? coverArtUrls[currentTrack.id] : undefined}
        isPlaying={isPlaying}
        isDucked={isAttenuated}
        surroundBadge={
          dspSettings.surroundMode !== 'off'
            ? SURROUND_MODES[dspSettings.surroundMode]?.shortLabel
            : undefined
        }
        accentHex={activeAccent.hex}
        onTogglePlayPause={togglePlayPause}
        onPrev={handlePrevTrack}
        onNext={() => handleNextTrack()}
        onToggleDuck={handleToggleAttenuation}
        onExpandPlayer={() => {
          triggerHaptic();
          setActiveTab('player');
        }}
        onClose={() => setFloatingMiniBarOpen(false)}
      />

      {/* PWA OFFLINE INDICATOR */}
      <OfflineIndicator />

      {/* NIGHT BRIGHTNESS DIMMER & WARM AMBER ANTI-GLARE OVERLAY */}
      {(nightBrightness < 100 || warmAmberFilter) && (
        <div
          aria-hidden="true"
          data-testid="night-dimmer-overlay"
          className="fixed inset-0 z-[90] pointer-events-none transition-opacity duration-200"
          style={{
            backgroundColor: warmAmberFilter ? 'rgba(24, 14, 2, 1)' : 'rgba(2, 6, 23, 1)',
            opacity: Math.max(
              warmAmberFilter ? 0.14 : 0,
              calculateDimmerOverlayOpacity(nightBrightness)
            ),
          }}
        />
      )}
    </div>
  );
}
