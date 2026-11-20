import React from 'react';
import {
  Gauge,
  Heart,
  Pause,
  Play,
  Repeat,
  Shuffle,
  SkipBack,
  SkipForward,
  Volume2,
  VolumeX,
  Waves,
} from 'lucide-react';

export type CockpitHudKind =
  | 'play'
  | 'pause'
  | 'next'
  | 'prev'
  | 'volume'
  | 'mute'
  | 'duck'
  | 'surround'
  | 'adaptive'
  | 'shuffle'
  | 'repeat'
  | 'favorite';

export interface CockpitHudEvent {
  id: number;
  kind: CockpitHudKind;
  label: string;
  detail?: string;
  /** Optional 0..100 meter bar (used for live volume adjustments) */
  levelPercent?: number;
}

interface CockpitHudOverlayProps {
  event: CockpitHudEvent | null;
  accentHex: string;
  /** Compact variant for artwork-centered overlay, or larger for fullscreen Drive Mode */
  size?: 'artwork' | 'drive';
}

function renderHudIcon(kind: CockpitHudKind, accentHex: string, iconClass: string) {
  switch (kind) {
    case 'play':
      return <Play className={`${iconClass} ml-0.5`} style={{ color: accentHex }} />;
    case 'pause':
      return <Pause className={iconClass} style={{ color: accentHex }} />;
    case 'next':
      return <SkipForward className={iconClass} style={{ color: accentHex }} />;
    case 'prev':
      return <SkipBack className={iconClass} style={{ color: accentHex }} />;
    case 'volume':
      return <Volume2 className={iconClass} style={{ color: accentHex }} />;
    case 'mute':
      return <VolumeX className={iconClass} style={{ color: '#f87171' }} />;
    case 'duck':
      return <VolumeX className={iconClass} style={{ color: '#fbbf24' }} />;
    case 'surround':
      return <Waves className={iconClass} style={{ color: accentHex }} />;
    case 'adaptive':
      return <Gauge className={iconClass} style={{ color: accentHex }} />;
    case 'shuffle':
      return <Shuffle className={iconClass} style={{ color: accentHex }} />;
    case 'repeat':
      return <Repeat className={iconClass} style={{ color: accentHex }} />;
    case 'favorite':
      return <Heart className={iconClass} style={{ color: accentHex }} fill={accentHex} />;
  }
}

/**
 * Non-intrusive automotive HUD confirmation overlay centered on album artwork (or Drive Mode).
 * Uses GPU-only opacity/transform transitions so driving feedback settles in <= 150ms.
 */
export const CockpitHudOverlay: React.FC<CockpitHudOverlayProps> = ({
  event,
  accentHex,
  size = 'artwork',
}) => {
  if (!event) return null;

  const isDrive = size === 'drive';
  const iconClass = isDrive ? 'w-9 h-9 stroke-[2.25]' : 'w-7 h-7 stroke-[2.25]';

  return (
    <div
      key={event.id}
      role="status"
      aria-live="polite"
      className="absolute inset-0 z-30 flex items-center justify-center pointer-events-none p-4"
    >
      <div
        className={`flex flex-col items-center justify-center text-center rounded-2xl bg-black/82 backdrop-blur-md border shadow-2xl transition-all duration-150 ${
          isDrive ? 'min-w-[200px] max-w-[280px] px-6 py-5 gap-2.5' : 'min-w-[144px] max-w-[224px] px-4 py-3.5 gap-2'
        }`}
        style={{
          borderColor:
            event.kind === 'duck'
              ? 'rgba(251, 191, 36, 0.48)'
              : event.kind === 'mute'
                ? 'rgba(248, 113, 113, 0.45)'
                : `${accentHex}55`,
          boxShadow: `0 12px 32px rgba(0,0,0,0.65), 0 0 24px ${accentHex}22`,
        }}
      >
        <div
          className={`rounded-xl flex items-center justify-center ${
            isDrive ? 'w-14 h-14' : 'w-11 h-11'
          }`}
          style={{
            backgroundColor:
              event.kind === 'duck'
                ? 'rgba(251, 191, 36, 0.14)'
                : event.kind === 'mute'
                  ? 'rgba(248, 113, 113, 0.14)'
                  : `${accentHex}1c`,
          }}
        >
          {renderHudIcon(event.kind, accentHex, iconClass)}
        </div>

        <div className="w-full space-y-0.5">
          <div
            className={`font-bold text-white tracking-tight leading-tight truncate ${
              isDrive ? 'text-base' : 'text-xs'
            }`}
          >
            {event.label}
          </div>
          {event.detail && (
            <div
              className={`font-mono tabular-nums text-slate-300 truncate ${
                isDrive ? 'text-xs' : 'text-[11px]'
              }`}
            >
              {event.detail}
            </div>
          )}
        </div>

        {typeof event.levelPercent === 'number' && (
          <div className="w-full h-1.5 rounded-full bg-slate-800 overflow-hidden mt-0.5">
            <div
              className="h-full rounded-full transition-[width] duration-100"
              style={{
                width: `${Math.max(0, Math.min(100, event.levelPercent))}%`,
                backgroundColor: event.kind === 'mute' ? '#f87171' : accentHex,
              }}
            />
          </div>
        )}
      </div>
    </div>
  );
};
