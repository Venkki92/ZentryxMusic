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
