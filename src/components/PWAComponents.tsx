import React, { useEffect, useState } from 'react';
import { Download, Smartphone, WifiOff, X } from 'lucide-react';

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: 'accepted' | 'dismissed'; platform: string }>;
}

export function usePWAInstall() {
  const [deferredPrompt, setDeferredPrompt] = useState<BeforeInstallPromptEvent | null>(null);
  const [isInstalled, setIsInstalled] = useState(false);
  const [isIOS, setIsIOS] = useState(false);

  useEffect(() => {
    const isStandalone =
      typeof window !== 'undefined' &&
      (window.matchMedia('(display-mode: standalone)').matches ||
        (window.navigator as unknown as { standalone?: boolean }).standalone === true);
    setIsInstalled(Boolean(isStandalone));

    const ua = typeof window !== 'undefined' ? window.navigator.userAgent.toLowerCase() : '';
    setIsIOS(/iphone|ipad|ipod/.test(ua));

    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };

    const handleAppInstalled = () => {
      setIsInstalled(true);
      setDeferredPrompt(null);
    };

    window.addEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
    window.addEventListener('appinstalled', handleAppInstalled);
    return () => {
      window.removeEventListener('beforeinstallprompt', handleBeforeInstallPrompt);
      window.removeEventListener('appinstalled', handleAppInstalled);
    };
  }, []);

  const install = async (): Promise<boolean> => {
    if (!deferredPrompt) return false;
    await deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      setIsInstalled(true);
      setDeferredPrompt(null);
      return true;
    }
    return false;
  };

  return {
    isInstallable: Boolean(deferredPrompt),
    isInstalled,
    isIOS,
    install,
  };
}

export function useOnlineStatus(): boolean {
  const [isOnline, setIsOnline] = useState(
    typeof navigator !== 'undefined' ? navigator.onLine : true
  );

  useEffect(() => {
    const onOnline = () => setIsOnline(true);
    const onOffline = () => setIsOnline(false);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    return () => {
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    };
  }, []);

  return isOnline;
}

export const PWAInstallButton: React.FC<{
  accentHex: string;
  compact?: boolean;
  showGuideWhenUnavailable?: boolean;
}> = ({ accentHex, compact = false, showGuideWhenUnavailable = false }) => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showGuideModal, setShowGuideModal] = useState(false);

  if (isInstalled) {
    return showGuideWhenUnavailable ? (
      <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs font-medium">
        <Smartphone className="w-3.5 h-3.5" />
        Installed Standalone
      </span>
    ) : null;
  }

  if (isInstallable) {
    return (
      <button
        type="button"
        onClick={() => void install()}
        className={`flex items-center gap-1.5 rounded-xl font-semibold text-black transition-transform active:scale-95 shadow-md ${
          compact ? 'px-2.5 py-1.5 text-xs' : 'px-3.5 py-2 text-xs'
        }`}
        style={{ backgroundColor: accentHex }}
        title="Install Zentryx Music to Headunit Launcher"
        aria-label="Install App"
      >
        <Download className="w-3.5 h-3.5" />
        <span>{compact ? 'Install' : 'Install Headunit App'}</span>
      </button>
    );
  }

  if (isIOS || showGuideWhenUnavailable) {
    return (
      <>
        <button
          type="button"
          onClick={() => setShowGuideModal(true)}
          className={`flex items-center gap-1.5 rounded-xl border border-slate-700 bg-slate-900/90 hover:bg-slate-800 text-slate-200 transition-colors ${
            compact ? 'px-2.5 py-1.5 text-xs' : 'px-3 py-2 text-xs font-medium'
          }`}
          aria-label={isIOS ? 'Install on iOS' : 'How to Install App'}
        >
          <Smartphone className="w-3.5 h-3.5" style={{ color: accentHex }} />
          <span>{isIOS ? 'Install on iOS' : 'Launcher Install'}</span>
        </button>

        {showGuideModal && (
          <div className="fixed inset-0 z-[100] flex items-center justify-center bg-black/75 p-4">
            <div className="w-full max-w-sm rounded-2xl bg-[#0d111a] border border-slate-800 p-5 shadow-2xl">
              <div className="flex items-center justify-between mb-3">
                <h3 className="text-base font-bold text-white flex items-center gap-2">
                  <Smartphone className="w-4 h-4" style={{ color: accentHex }} />
                  Add to Headunit / Home Screen
                </h3>
                <button
                  type="button"
                  onClick={() => setShowGuideModal(false)}
                  className="p-1 text-slate-400 hover:text-white rounded-lg"
                  aria-label="Close install guide"
                >
                  <X className="w-4 h-4" />
                </button>
              </div>
              {isIOS ? (
                <p className="text-xs text-slate-300 leading-relaxed">
                  1. Tap the <strong>Share</strong> icon in Safari.<br />
                  2. Scroll down and select <strong>Add to Home Screen</strong> for full-screen standalone launch.
                </p>
              ) : (
                <p className="text-xs text-slate-300 leading-relaxed">
                  1. Open this player directly in Chromium / Chrome on your Android headunit (outside embedded previews).<br />
                  2. Tap Chrome menu (<strong className="text-white">⋮</strong>) &rarr; <strong className="text-white">Install app</strong> / <strong className="text-white">Add to Home screen</strong> so Zentryx Music boots fullscreen from your launcher.
                </p>
              )}
              <button
                type="button"
                onClick={() => setShowGuideModal(false)}
                className="mt-4 w-full py-2.5 rounded-xl text-xs font-bold text-black"
                style={{ backgroundColor: accentHex }}
              >
                Got It
              </button>
            </div>
          </div>
        )}
      </>
    );
  }

  return null;
};

export const OfflineIndicator: React.FC = () => {
  const isOnline = useOnlineStatus();
  if (isOnline) return null;

  return (
    <div
      role="status"
      className="fixed bottom-4 left-4 z-50 flex items-center gap-2 rounded-xl bg-amber-500/95 px-3 py-1.5 text-xs font-bold text-slate-950 shadow-lg border border-amber-300/60"
    >
      <WifiOff className="w-3.5 h-3.5" />
      <span>Offline Mode — Playing Cached Tracks</span>
    </div>
  );
};

interface ApkVariantStatus {
  exists: boolean;
  filename: string;
  version?: string;
  versionCode?: number;
  sizeBytes: number;
  updatedAt: string | null;
  url: string;
}

interface AndroidApkStatusResponse {
  available: boolean;
  version: string;
  versionCode?: number;
  applicationId: string;
  stale?: boolean;
  building?: boolean;
  buildError?: string | null;
  release: ApkVariantStatus;
  debug: ApkVariantStatus;
}

function formatApkSize(bytes: number): string {
  if (!bytes || bytes <= 0) return '';
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

async function downloadVerifiedApk(
  url: string,
  filename: string,
  expectedSizeBytes: number,
  onProgress: (pct: number | null) => void
): Promise<void> {
  const response = await fetch(url, {
    method: 'GET',
    credentials: 'include',
    cache: 'no-store',
  });

  if (!response.ok) {
    throw new Error(`Server returned HTTP ${response.status}`);
  }

  const contentType = (response.headers.get('content-type') || '').toLowerCase();
  if (contentType.includes('text/html')) {
    throw new Error('Preview proxy returned HTML instead of the APK binary.');
  }

  const headerLen = Number(response.headers.get('content-length') || 0);
  const totalBytes = headerLen > 0 ? headerLen : expectedSizeBytes;
  const chunks: Uint8Array<ArrayBuffer>[] = [];
  let received = 0;

  if (response.body) {
    const reader = response.body.getReader();
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      if (value && value.byteLength > 0) {
        const copy = new Uint8Array(new ArrayBuffer(value.byteLength));
        copy.set(value);
        chunks.push(copy);
        received += value.byteLength;
        if (totalBytes > 0) {
          onProgress(Math.min(100, Math.round((received / totalBytes) * 100)));
        } else {
          onProgress(null);
        }
      }
    }
  } else {
    const buf = new Uint8Array(await response.arrayBuffer());
    chunks.push(buf);
    received = buf.byteLength;
    onProgress(100);
  }

  if (received < 100_000) {
    throw new Error(`Downloaded payload too small (${received} bytes) — not a valid APK.`);
  }

  const firstChunk = chunks[0];
  if (!firstChunk || firstChunk.length < 2 || firstChunk[0] !== 0x50 || firstChunk[1] !== 0x4b) {
    throw new Error('Downloaded file is missing the APK/ZIP signature (PK).');
  }

  const blob = new Blob(chunks, { type: 'application/vnd.android.package-archive' });
  const blobUrl = URL.createObjectURL(blob);
  const anchor = document.createElement('a');
  anchor.href = blobUrl;
  anchor.download = filename;
  anchor.style.display = 'none';
  document.body.appendChild(anchor);
  anchor.click();
  document.body.removeChild(anchor);
  window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000);
}

export const AndroidApkDownloadButton: React.FC<{
  accentHex: string;
  compact?: boolean;
}> = ({ accentHex, compact = false }) => {
  const [status, setStatus] = useState<AndroidApkStatusResponse | null>(null);
  const [downloadingVariant, setDownloadingVariant] = useState<'release' | 'debug' | null>(null);
  const [progressPct, setProgressPct] = useState<number | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  const [rebuildRequested, setRebuildRequested] = useState(false);

  const fetchStatus = async (): Promise<AndroidApkStatusResponse | null> => {
    try {
      const r = await fetch('/api/android-apk/status', { credentials: 'include', cache: 'no-store' });
      if (!r.ok) return null;
      return (await r.json()) as AndroidApkStatusResponse;
    } catch {
      return null;
    }
  };

  useEffect(() => {
    let active = true;
    void fetchStatus().then((data) => {
      if (active && data) setStatus(data);
    });
    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    if (!status?.building && !rebuildRequested) return;
    let active = true;
    const timer = window.setInterval(() => {
      void fetchStatus().then((data) => {
        if (!active || !data) return;
        setStatus(data);
        if (!data.building) {
          setRebuildRequested(false);
        }
      });
    }, 3000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [status?.building, rebuildRequested]);

  if (!status || !status.available) return null;

  const primaryVariant: 'release' | 'debug' = status.release.exists ? 'release' : 'debug';
  const primary = primaryVariant === 'release' ? status.release : status.debug;
  const displayVersion = primary.version || status.version;
  const displayBuild = primary.versionCode ?? status.versionCode ?? 1;

  const handleDownload = async (variant: 'release' | 'debug', item: ApkVariantStatus) => {
    if (downloadingVariant) return;
    setDownloadError(null);
    setDownloadingVariant(variant);
    setProgressPct(0);
    try {
      await downloadVerifiedApk(item.url, item.filename, item.sizeBytes, setProgressPct);
    } catch (err: any) {
      setDownloadError(err?.message || 'APK download failed');
    } finally {
      setDownloadingVariant(null);
      setProgressPct(null);
    }
  };

  const handleRebuild = async () => {
    if (status.building || rebuildRequested) return;
    setDownloadError(null);
    setRebuildRequested(true);
    try {
      const res = await fetch('/api/android-apk/rebuild', {
        method: 'POST',
        credentials: 'include',
      });
      if (!res.ok) {
        throw new Error(`Rebuild failed (${res.status})`);
      }
      const next = await fetchStatus();
      if (next) setStatus(next);
    } catch (err: any) {
      setRebuildRequested(false);
      setDownloadError(err?.message || 'Failed to trigger APK rebuild');
    }
  };

  if (compact) {
    const isBusy = downloadingVariant !== null;
    return (
      <button
        type="button"
        disabled={isBusy}
        onClick={() => void handleDownload(primaryVariant, primary)}
        className="flex items-center gap-1.5 rounded-xl px-2.5 py-1.5 text-xs font-bold text-black transition-transform active:scale-95 shadow-md whitespace-nowrap disabled:opacity-75 cursor-pointer"
        style={{ backgroundColor: accentHex }}
        title={
          downloadError
            ? downloadError
            : `Download Native Android APK v${displayVersion} #${displayBuild} (${formatApkSize(primary.sizeBytes)})`
        }
      >
        <Download className={`w-3.5 h-3.5 ${isBusy ? 'animate-bounce' : ''}`} />
        <span>
          {isBusy
            ? progressPct !== null
              ? `APK ${progressPct}%`
              : 'APK…'
            : status.building
              ? `APK Building…`
              : `APK v${displayVersion} #${displayBuild}${primary.sizeBytes > 0 ? ` · ${formatApkSize(primary.sizeBytes)}` : ''}`}
        </span>
      </button>
    );
  }

  return (
    <div className="flex flex-col items-end gap-1.5 shrink-0">
      <div className="flex flex-wrap items-center justify-end gap-2">
        {(status.stale || status.building) && (
          <button
            type="button"
            disabled={Boolean(status.building || rebuildRequested)}
            onClick={() => void handleRebuild()}
            className="flex items-center gap-1.5 rounded-xl px-3 py-2 text-xs font-semibold border border-amber-500/40 bg-amber-500/15 text-amber-300 hover:bg-amber-500/25 transition-colors disabled:opacity-70 cursor-pointer"
            title="Android source changed — rebuild APK and increment VERSION_CODE"
          >
            <span>{status.building || rebuildRequested ? 'Building APK…' : 'Update APK (+build)'}</span>
          </button>
        )}
        {status.release.exists && (
          <button
            type="button"
            disabled={downloadingVariant !== null}
            onClick={() => void handleDownload('release', status.release)}
            className="flex items-center gap-1.5 rounded-xl px-3.5 py-2 text-xs font-bold text-black transition-transform active:scale-95 shadow-md disabled:opacity-75 cursor-pointer"
            style={{ backgroundColor: accentHex }}
          >
            <Download className={`w-3.5 h-3.5 ${downloadingVariant === 'release' ? 'animate-bounce' : ''}`} />
            <span>
              {downloadingVariant === 'release'
                ? `Downloading… ${progressPct !== null ? `${progressPct}%` : ''}`
                : `Release v${status.release.version || displayVersion} (#${status.release.versionCode ?? displayBuild}) · ${formatApkSize(status.release.sizeBytes)}`}
            </span>
          </button>
        )}
        {status.debug.exists && (
          <button
            type="button"
            disabled={downloadingVariant !== null}
            onClick={() => void handleDownload('debug', status.debug)}
            className="flex items-center gap-1.5 rounded-xl px-3 py-2 text-xs font-medium border border-slate-700 bg-slate-900/90 hover:bg-slate-800 text-slate-200 transition-colors disabled:opacity-75 cursor-pointer"
          >
            <Download
              className={`w-3.5 h-3.5 ${downloadingVariant === 'debug' ? 'animate-bounce' : ''}`}
              style={{ color: accentHex }}
            />
            <span>
              {downloadingVariant === 'debug'
                ? `Downloading… ${progressPct !== null ? `${progressPct}%` : ''}`
                : `Debug v${status.debug.version || displayVersion} (#${status.debug.versionCode ?? displayBuild}) · ${formatApkSize(status.debug.sizeBytes)}`}
            </span>
          </button>
        )}
      </div>
      {status.buildError && (
        <span className="text-[11px] text-rose-400">Build error: {status.buildError}</span>
      )}
      {downloadError && (
        <span className="text-[11px] text-rose-400">{downloadError}</span>
      )}
    </div>
  );
};



