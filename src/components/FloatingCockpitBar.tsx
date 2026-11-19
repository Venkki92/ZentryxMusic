import React, { useRef, useState } from 'react';
import {
  Play,
  Pause,
  SkipBack,
  SkipForward,
  VolumeX,
  Volume2,
  X,
  GripHorizontal,
  Maximize2,
  Disc3,
} from 'lucide-react';

interface FloatingCockpitBarProps {
  visible: boolean;
  title: string;
  artist: string;
  coverUrl?: string;
  isPlaying: boolean;
  isDucked: boolean;
  surroundBadge?: string;
  accentHex: string;
  onTogglePlayPause: () => void;
  onPrev: () => void;
  onNext: () => void;
  onToggleDuck: () => void;
  onExpandPlayer: () => void;
  onClose: () => void;
}

export const FloatingCockpitBar: React.FC<FloatingCockpitBarProps> = ({
  visible,
  title,
  artist,
  coverUrl,
  isPlaying,
  isDucked,
  surroundBadge,
  accentHex,
  onTogglePlayPause,
  onPrev,
  onNext,
  onToggleDuck,
  onExpandPlayer,
  onClose,
}) => {
  const [offset, setOffset] = useState<{ x: number; y: number }>({ x: 0, y: 0 });
  const dragRef = useRef<{ startX: number; startY: number; origX: number; origY: number } | null>(null);

  if (!visible) return null;

  return (
    <div
      role="region"
      aria-label="Floating Cockpit Audio Bar"
      className="fixed bottom-24 right-4 sm:right-6 z-[85] w-[340px] max-w-[calc(100vw-2rem)] rounded-2xl bg-[#070b14]/90 backdrop-blur-xl border border-slate-700/80 shadow-2xl p-2.5 select-none"
      style={{
        transform: `translate3d(${offset.x}px, ${offset.y}px, 0)`,
        boxShadow: `0 18px 42px rgba(0,0,0,0.65), 0 0 0 1px ${accentHex}33`,
      }}
    >
      {/* Drag Handle Strip */}
      <div
        onPointerDown={(e) => {
          (e.currentTarget as HTMLDivElement).setPointerCapture?.(e.pointerId);
          dragRef.current = {
            startX: e.clientX,
            startY: e.clientY,
            origX: offset.x,
            origY: offset.y,
          };
        }}
        onPointerMove={(e) => {
          if (!dragRef.current) return;
          setOffset({
            x: dragRef.current.origX + (e.clientX - dragRef.current.startX),
            y: dragRef.current.origY + (e.clientY - dragRef.current.startY),
          });
        }}
        onPointerUp={() => {
          dragRef.current = null;
        }}
        onPointerCancel={() => {
          dragRef.current = null;
        }}
        className="flex items-center justify-between px-1 pb-2 mb-1.5 border-b border-slate-800/80 cursor-grab active:cursor-grabbing touch-none"
      >
        <div className="flex items-center gap-1.5 text-[10px] font-mono uppercase tracking-wider text-slate-400">
          <GripHorizontal className="w-3.5 h-3.5 text-slate-500" />
          <span>Cockpit PiP Bar</span>
          {surroundBadge && (
            <span
              className="px-1.5 py-0.2 rounded font-bold"
              style={{ backgroundColor: `${accentHex}22`, color: accentHex }}
            >
              {surroundBadge}
            </span>
          )}
        </div>
        <div className="flex items-center gap-1">
          <button
            type="button"
            onClick={onExpandPlayer}
            className="w-7 h-7 rounded-lg flex items-center justify-center text-slate-400 hover:text-white hover:bg-slate-800"
            title="Return to Player"
          >
            <Maximize2 className="w-3.5 h-3.5" />
          </button>
          <button
            type="button"
            onClick={onClose}
            className="w-7 h-7 rounded-lg flex items-center justify-center text-slate-400 hover:text-red-400 hover:bg-slate-800"
            title="Close Floating Bar"
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </div>
      </div>

      {/* Track Row + Transport Controls */}
      <div className="flex items-center gap-2.5">
        <div className="w-11 h-11 rounded-xl bg-slate-900 border border-slate-800 overflow-hidden shrink-0 flex items-center justify-center">
          {coverUrl ? (
            <img src={coverUrl} alt="" className="w-full h-full object-cover" />
          ) : (
            <Disc3 className="w-5 h-5 text-slate-500" />
          )}
        </div>

        <div className="min-w-0 flex-1">
          <div className="text-xs font-bold text-white truncate">{title || 'No track loaded'}</div>
          <div className="text-[11px] text-slate-400 truncate">{artist || 'Zentryx Cockpit'}</div>
        </div>
      </div>

      <div className="grid grid-cols-4 gap-1.5 mt-2.5">
        <button
          type="button"
          onClick={onPrev}
          aria-label="Previous Track"
          className="min-h-[44px] rounded-xl bg-slate-900/90 border border-slate-800 text-slate-200 hover:text-white flex items-center justify-center active:scale-95 transition-transform"
        >
          <SkipBack className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={onTogglePlayPause}
          aria-label={isPlaying ? 'Pause' : 'Play'}
          className="min-h-[44px] rounded-xl text-black flex items-center justify-center active:scale-95 transition-transform font-bold shadow"
          style={{ backgroundColor: accentHex }}
        >
          {isPlaying ? <Pause className="w-4 h-4 fill-current" /> : <Play className="w-4 h-4 fill-current ml-0.5" />}
        </button>
        <button
          type="button"
          onClick={onNext}
          aria-label="Next Track"
          className="min-h-[44px] rounded-xl bg-slate-900/90 border border-slate-800 text-slate-200 hover:text-white flex items-center justify-center active:scale-95 transition-transform"
        >
          <SkipForward className="w-4 h-4" />
        </button>
        <button
          type="button"
          onClick={onToggleDuck}
          aria-label="Toggle Talk Duck"
          className={`min-h-[44px] rounded-xl border flex items-center justify-center active:scale-95 transition-transform ${
            isDucked
              ? 'bg-amber-400 text-black border-amber-300'
              : 'bg-slate-900/90 border-slate-800 text-slate-300 hover:text-white'
          }`}
          title="Talk Duck (-20 dB)"
        >
          {isDucked ? <VolumeX className="w-4 h-4" /> : <Volume2 className="w-4 h-4" />}
        </button>
      </div>
    </div>
  );
};
