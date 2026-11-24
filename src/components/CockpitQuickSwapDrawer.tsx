import React, { useState } from 'react';
import {
  Disc,
  Folder,
  Play,
  RefreshCw,
  Wifi,
  WifiOff,
  X,
  Check,
  Pin,
} from 'lucide-react';
import { SwapGroupMode, SwapMediaGroup } from '../utils/w203Cockpit';
import { Track } from '../App';

interface CockpitQuickSwapDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  accentHex: string;
  mode: SwapGroupMode;
  onChangeMode: (mode: SwapGroupMode) => void;
  folderGroups: SwapMediaGroup<Track>[];
  albumGroups: SwapMediaGroup<Track>[];
  activeGroupId: string | null;
  onPlayGroup: (group: SwapMediaGroup<Track>) => void;
  onTogglePinFolder: (folderKey: string) => void;
  wifiAutoCacheEnabled: boolean;
  wifiSyncStatus: string;
  isWifiSyncing: boolean;
  onSyncPinnedNow: () => void;
}

export const CockpitQuickSwapDrawer: React.FC<CockpitQuickSwapDrawerProps> = ({
  isOpen,
  onClose,
  accentHex,
  mode,
  onChangeMode,
  folderGroups,
  albumGroups,
  activeGroupId,
  onPlayGroup,
  onTogglePinFolder,
  wifiAutoCacheEnabled,
  wifiSyncStatus,
  isWifiSyncing,
  onSyncPinnedNow,
}) => {
  const [offlineOnly, setOfflineOnly] = useState(false);

  if (!isOpen) return null;

  const baseGroups = mode === 'folders' ? folderGroups : albumGroups;
  const groups = offlineOnly ? baseGroups.filter((g) => g.cachedCount > 0) : baseGroups;

  return (
    <div
      className="fixed inset-0 z-50 bg-black/75 backdrop-blur-xs flex items-center justify-end"
      role="dialog"
      aria-modal="true"
      aria-label="Cockpit Quick-Swap Drawer"
      onClick={onClose}
    >
      <div
        className="w-full max-w-lg h-full bg-[#090d18] w203-surface-card border-l border-slate-800 shadow-2xl flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="p-4 border-b border-slate-800/80 flex items-center justify-between gap-3">
          <div className="min-w-0">
            <h2 className="text-base font-extrabold text-white tracking-tight">
              Cockpit Quick-Swap
            </h2>
            <p className="text-xs text-slate-400 truncate">
              1-tap Nextcloud folder &amp; album switching · Home Wi-Fi offline pins
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close Quick-Swap Drawer"
            className="min-h-[44px] min-w-[44px] rounded-xl bg-black/60 border border-slate-800 hover:border-slate-600 text-slate-300 hover:text-white flex items-center justify-center shrink-0"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Mode Switcher + Offline Filter */}
        <div className="p-4 pb-3 border-b border-slate-800/60 space-y-3">
          <div className="flex items-center gap-2">
            <div className="grid grid-cols-2 gap-1 p-1 rounded-xl bg-black/60 border border-slate-800 flex-1">
              <button
                type="button"
                onClick={() => onChangeMode('folders')}
                className={`min-h-[44px] px-3 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5 whitespace-nowrap transition-colors ${
                  mode === 'folders' ? 'text-black' : 'text-slate-300 hover:text-white'
                }`}
                style={mode === 'folders' ? { backgroundColor: accentHex } : undefined}
              >
                <Folder className="w-4 h-4 shrink-0" />
                <span>Folders ({folderGroups.length})</span>
              </button>
              <button
                type="button"
                onClick={() => onChangeMode('albums')}
                className={`min-h-[44px] px-3 rounded-lg text-xs font-bold flex items-center justify-center gap-1.5 whitespace-nowrap transition-colors ${
                  mode === 'albums' ? 'text-black' : 'text-slate-300 hover:text-white'
                }`}
                style={mode === 'albums' ? { backgroundColor: accentHex } : undefined}
              >
                <Disc className="w-4 h-4 shrink-0" />
                <span>Albums ({albumGroups.length})</span>
              </button>
            </div>

            <button
              type="button"
              onClick={() => setOfflineOnly((v) => !v)}
              className={`min-h-[44px] px-3 rounded-xl text-xs font-bold border whitespace-nowrap transition-colors ${
                offlineOnly
                  ? 'bg-emerald-500/20 border-emerald-400 text-emerald-300'
                  : 'bg-black/50 border-slate-800 text-slate-300 hover:text-white'
              }`}
            >
              Offline Ready
            </button>
          </div>

          {/* Home Wi-Fi Auto-Cache Status Strip */}
          <div className="flex items-center justify-between gap-2 px-3 py-2 rounded-xl bg-black/50 border border-slate-800/80 text-xs">
            <div className="flex items-center gap-2 min-w-0">
              {wifiAutoCacheEnabled ? (
                <Wifi className="w-4 h-4 shrink-0" style={{ color: accentHex }} />
              ) : (
                <WifiOff className="w-4 h-4 shrink-0 text-slate-500" />
              )}
              <span className="text-slate-300 truncate font-mono text-[11px]">{wifiSyncStatus}</span>
            </div>
            <button
              type="button"
              disabled={isWifiSyncing}
              onClick={onSyncPinnedNow}
              className="min-h-[36px] px-3 rounded-lg text-[11px] font-bold text-black flex items-center gap-1.5 whitespace-nowrap shrink-0 disabled:opacity-50"
              style={{ backgroundColor: accentHex }}
            >
              <RefreshCw className={`w-3.5 h-3.5 ${isWifiSyncing ? 'animate-spin' : ''}`} />
              <span>{isWifiSyncing ? 'Syncing…' : 'Sync Pins'}</span>
            </button>
          </div>
        </div>

        {/* Group List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-2">
          {groups.length === 0 ? (
            <div className="p-8 text-center text-sm text-slate-400">
              {offlineOnly
                ? 'No offline-cached folders yet. Pin a folder to auto-cache it on Wi-Fi.'
                : 'No Nextcloud folders or albums loaded yet.'}
            </div>
          ) : (
            groups.map((group) => {
              const isActive = activeGroupId === group.id;
              const isFullyCached = group.tracks.length > 0 && group.cachedCount >= group.tracks.length;
              return (
                <div
                  key={`${mode}-${group.id}-${group.name}`}
                  className={`p-3 rounded-2xl border flex items-center gap-3 transition-colors ${
                    isActive
                      ? 'bg-slate-900/90 border-slate-500'
                      : 'bg-black/40 border-slate-800/80 hover:border-slate-700'
                  }`}
                  style={isActive ? { borderColor: accentHex } : undefined}
                >
                  <button
                    type="button"
                    onClick={() => {
                      onPlayGroup(group);
                      onClose();
                    }}
                    className="flex items-center gap-3 flex-1 min-w-0 text-left min-h-[48px]"
                  >
                    <div
                      className="w-11 h-11 rounded-xl flex items-center justify-center border shrink-0"
                      style={
                        isActive
                          ? { backgroundColor: `${accentHex}24`, borderColor: accentHex, color: accentHex }
                          : { backgroundColor: 'rgba(15,23,42,0.8)', borderColor: '#1e293b', color: '#94a3b8' }
                      }
                    >
                      {isActive ? <Play className="w-5 h-5 fill-current" /> : mode === 'folders' ? <Folder className="w-5 h-5" /> : <Disc className="w-5 h-5" />}
                    </div>
                    <div className="min-w-0 flex-1">
                      <div
                        className="text-sm font-bold truncate"
                        style={{ color: isActive ? accentHex : '#ffffff' }}
                      >
                        {group.name}
                      </div>
                      <div className="text-[11px] text-slate-400 truncate font-mono tabular-nums">
                        {group.subtitle} · {group.tracks.length} tracks · {group.cachedCount}/{group.tracks.length} offline
                      </div>
                    </div>
                  </button>

                  {/* Per-Folder Home Wi-Fi Auto-Cache Pin Button */}
                  <button
                    type="button"
                    onClick={() => onTogglePinFolder(group.id)}
                    aria-label={
                      group.isPinnedForWifiCache
                        ? `Unpin ${group.name} from Home Wi-Fi Auto-Cache`
                        : `Pin ${group.name} for Home Wi-Fi Auto-Cache`
                    }
                    title={
                      group.isPinnedForWifiCache
                        ? 'Pinned for Home Wi-Fi Auto-Cache (tap to unpin)'
                        : 'Pin folder for automatic Home Wi-Fi offline caching'
                    }
                    className={`min-h-[44px] px-3 rounded-xl border text-xs font-bold flex items-center gap-1.5 whitespace-nowrap shrink-0 transition-colors ${
                      group.isPinnedForWifiCache
                        ? 'text-black border-transparent'
                        : 'bg-black/60 border-slate-800 text-slate-300 hover:text-white hover:border-slate-600'
                    }`}
                    style={group.isPinnedForWifiCache ? { backgroundColor: accentHex } : undefined}
                  >
                    {isFullyCached ? <Check className="w-3.5 h-3.5" /> : <Pin className="w-3.5 h-3.5" />}
                    <span>{group.isPinnedForWifiCache ? 'Pinned' : 'Pin'}</span>
                  </button>
                </div>
              );
            })
          )}
        </div>
      </div>
    </div>
  );
};
