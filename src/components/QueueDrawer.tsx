import React from 'react';
import {
  ListMusic,
  X,
  Play,
  ArrowUp,
  ArrowDown,
  Trash2,
  Disc,
  Sparkles,
  Music,
} from 'lucide-react';
import { Track } from '../App';

interface QueueDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  queue: Track[];
  currentTrackIndex: number;
  onPlayQueueIndex: (index: number) => void;
  onMoveUp: (index: number) => void;
  onMoveDown: (index: number) => void;
  onRemoveFromQueue: (index: number) => void;
  onClearQueue: () => void;
  accentHex: string;
}

export const QueueDrawer: React.FC<QueueDrawerProps> = ({
  isOpen,
  onClose,
  queue,
  currentTrackIndex,
  onPlayQueueIndex,
  onMoveUp,
  onMoveDown,
  onRemoveFromQueue,
  onClearQueue,
  accentHex,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex justify-end bg-black/70 backdrop-blur-sm transition-opacity">
      <div className="w-full max-w-md h-full bg-[#070b14] border-l border-slate-800 flex flex-col shadow-2xl animate-in slide-in-from-right duration-200">
        {/* Drawer Header */}
        <div className="p-4 border-b border-slate-800 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ListMusic className="w-5 h-5" style={{ color: accentHex }} />
            <h2 className="text-base font-bold text-white">Up-Next Queue</h2>
            <span className="text-xs font-mono text-slate-400 bg-slate-800/80 px-2 py-0.5 rounded-full">
              {queue.length} tracks
            </span>
          </div>

          <div className="flex items-center gap-2">
            {queue.length > 1 && (
              <button
                onClick={onClearQueue}
                className="text-xs text-slate-400 hover:text-red-400 px-2 py-1 rounded-lg border border-slate-800 hover:border-red-900/50 transition-colors"
              >
                Clear
              </button>
            )}
            <button
              onClick={onClose}
              className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Queue Items List */}
        <div className="flex-1 overflow-y-auto p-4 space-y-2">
          {queue.length === 0 ? (
            <div className="h-64 flex flex-col items-center justify-center text-center p-6 text-slate-500 space-y-3">
              <Disc className="w-10 h-10 opacity-40" />
              <p className="text-sm">Queue is empty</p>
              <p className="text-xs text-slate-600">
                Browse tracks in Library and tap &quot;Play Next&quot; or &quot;Add to Queue&quot;.
              </p>
            </div>
          ) : (
            queue.map((track, idx) => {
              const isCurrent = idx === currentTrackIndex;
              return (
                <div
                  key={`${track.id}-${idx}`}
                  className={`flex items-center justify-between p-3 rounded-2xl border transition-all ${
                    isCurrent
                      ? 'bg-slate-900/90 border-slate-700 shadow-lg'
                      : 'bg-[#0d1527]/50 border-slate-800/60 hover:border-slate-700'
                  }`}
                  style={isCurrent ? { borderColor: `${accentHex}66` } : {}}
                >
                  <div
                    onClick={() => onPlayQueueIndex(idx)}
                    className="flex items-center gap-3 min-w-0 flex-1 cursor-pointer"
                  >
                    <div
                      className={`w-9 h-9 rounded-xl flex items-center justify-center shrink-0 border ${
                        isCurrent ? 'bg-black' : 'bg-slate-800/60 border-slate-700/50'
                      }`}
                      style={isCurrent ? { borderColor: accentHex } : {}}
                    >
                      {isCurrent ? (
                        <Sparkles className="w-4 h-4 animate-spin" style={{ color: accentHex }} />
                      ) : (
                        <span className="text-xs font-mono text-slate-400">{idx + 1}</span>
                      )}
                    </div>

                    <div className="min-w-0 flex-1">
                      <p
                        className={`text-xs font-bold truncate ${
                          isCurrent ? 'text-white' : 'text-slate-300'
                        }`}
                        style={isCurrent ? { color: accentHex } : {}}
                      >
                        {track.title}
                      </p>
                      <p className="text-[11px] text-slate-500 truncate">
                        {track.artist || 'Unknown'} • {track.duration || '0:00'}
                      </p>
                    </div>
                  </div>

                  {/* Reorder and Delete Controls */}
                  <div className="flex items-center gap-1 shrink-0 ml-2">
                    <button
                      onClick={() => onMoveUp(idx)}
                      disabled={idx === 0}
                      className="p-1 text-slate-500 hover:text-white disabled:opacity-20 transition-colors"
                      title="Move Up"
                    >
                      <ArrowUp className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => onMoveDown(idx)}
                      disabled={idx === queue.length - 1}
                      className="p-1 text-slate-500 hover:text-white disabled:opacity-20 transition-colors"
                      title="Move Down"
                    >
                      <ArrowDown className="w-3.5 h-3.5" />
                    </button>
                    <button
                      onClick={() => onRemoveFromQueue(idx)}
                      className="p-1 text-slate-500 hover:text-red-400 transition-colors"
                      title="Remove"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                </div>
              );
            })
          )}
        </div>

        {/* Drawer Footer */}
        <div className="p-3 border-t border-slate-800/80 bg-black/40 text-center">
          <p className="text-[11px] text-slate-500">
            Tip: Press <kbd className="bg-slate-800 text-slate-300 px-1.5 py-0.5 rounded text-[10px]">Q</kbd> anytime on your vehicle keyboard to toggle queue.
          </p>
        </div>
      </div>
    </div>
  );
};
