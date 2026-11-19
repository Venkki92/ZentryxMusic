import React from 'react';
import { Play, Pause, SkipForward, Disc, Cloud } from 'lucide-react';
import { Track } from '../App';
import { CrossfadeAlbumArt } from './CrossfadeAlbumArt';

interface StickyMiniPlayerProps {
  currentTrack?: Track;
  isPlaying: boolean;
  positionSeconds: number;
  durationSeconds: number;
  onTogglePlayPause: () => void;
  onNextTrack: () => void;
  onOpenPlayer: () => void;
  accentHex: string;
  coverUrl?: string | null;
}

export const StickyMiniPlayer: React.FC<StickyMiniPlayerProps> = ({
  currentTrack,
  isPlaying,
  positionSeconds,
  durationSeconds,
  onTogglePlayPause,
  onNextTrack,
  onOpenPlayer,
  accentHex,
  coverUrl,
}) => {
  if (!currentTrack) return null;

  const progressPercent = durationSeconds > 0 ? Math.min(100, (positionSeconds / durationSeconds) * 100) : 0;

  return (
    <div className="fixed bottom-0 left-0 right-0 z-40 bg-[#070b14]/95 backdrop-blur-xl border-t border-slate-800/90 shadow-[0_-10px_25px_-5px_rgba(0,0,0,0.7)]">
      {/* Real-time Progress Strip */}
      <div className="w-full h-1 bg-slate-800/80 overflow-hidden">
        <div
          className="h-full transition-all duration-300"
          style={{ width: `${progressPercent}%`, backgroundColor: accentHex }}
        />
      </div>

      <div className="max-w-6xl mx-auto px-4 py-2.5 flex items-center justify-between gap-4">
        {/* Track Info (clickable to jump back to full Cockpit Player) */}
        <div
          onClick={onOpenPlayer}
          className="flex items-center gap-3 min-w-0 flex-1 cursor-pointer group"
        >
          <div
            className="w-12 h-12 rounded-xl overflow-hidden bg-black/60 border flex items-center justify-center shrink-0 shadow-md group-hover:scale-105 transition-transform"
            style={{ borderColor: `${accentHex}66` }}
          >
            <CrossfadeAlbumArt
              src={coverUrl}
              alt={currentTrack.title || 'Cover'}
              trackKey={currentTrack.id}
              durationMs={300}
              className="w-full h-full"
              fallback={
                <Disc
                  className={`w-6 h-6 transition-transform ${isPlaying ? 'animate-spin' : ''}`}
                  style={{ color: accentHex, animationDuration: '6s' }}
                />
              }
            />
          </div>

          <div className="min-w-0 flex-1">
            <div className="flex items-center gap-1.5 text-[10px] uppercase font-mono text-slate-400">
              <Cloud className="w-3 h-3" style={{ color: accentHex }} />
              <span className="truncate">Now Playing</span>
            </div>
            <p className="text-xs sm:text-sm font-bold text-white truncate group-hover:text-lime-300 transition-colors">
              {currentTrack.title}
            </p>
            <p className="text-[11px] text-slate-400 truncate">
              {currentTrack.artist || 'Nextcloud'} • {currentTrack.duration || '...'}
            </p>
          </div>
        </div>

        {/* Driving Ergonomic Quick Controls (Fitts's Law Hit Targets) */}
        <div className="flex items-center gap-2 sm:gap-3 shrink-0">
          <button
            onClick={onTogglePlayPause}
            className="w-13 h-13 rounded-2xl text-black flex items-center justify-center shadow-lg transition-transform active:scale-95"
            style={{ backgroundColor: accentHex }}
            title={isPlaying ? 'Pause' : 'Play'}
          >
            {isPlaying ? (
              <Pause className="w-6 h-6" />
            ) : (
              <Play className="w-6 h-6 ml-0.5" />
            )}
          </button>

          <button
            onClick={onNextTrack}
            className="w-11 h-11 rounded-2xl bg-black/60 hover:bg-black text-white flex items-center justify-center border border-slate-800 transition-transform active:scale-95"
            title="Next Track"
          >
            <SkipForward className="w-5 h-5" />
          </button>
        </div>
      </div>
    </div>
  );
};
