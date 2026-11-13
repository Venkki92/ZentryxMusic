import React, { useState } from 'react';
import {
  ListMusic,
  Plus,
  Play,
  Trash2,
  X,
  Check,
  Disc,
  FolderPlus,
} from 'lucide-react';
import { Playlist } from '../utils/fuzzySearch';
import { Track } from '../App';

interface PlaylistsModalProps {
  isOpen: boolean;
  onClose: () => void;
  playlists: Playlist[];
  onCreatePlaylist: (name: string, description?: string) => void;
  onDeletePlaylist: (id: string) => void;
  onPlayPlaylist: (playlist: Playlist) => void;
  onAddTrackToPlaylist?: (playlistId: string, trackId: string) => void;
  trackToAdd?: Track | null;
  accentHex: string;
}

export const PlaylistsModal: React.FC<PlaylistsModalProps> = ({
  isOpen,
  onClose,
  playlists,
  onCreatePlaylist,
  onDeletePlaylist,
  onPlayPlaylist,
  onAddTrackToPlaylist,
  trackToAdd,
  accentHex,
}) => {
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [newTitle, setNewTitle] = useState('');
  const [newDesc, setNewDesc] = useState('');
  const [addedNotice, setAddedNotice] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleCreateSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTitle.trim()) return;
    onCreatePlaylist(newTitle.trim(), newDesc.trim());
    setNewTitle('');
    setNewDesc('');
    setShowCreateForm(false);
  };

  const handleSelectPlaylist = (pl: Playlist) => {
    if (trackToAdd && onAddTrackToPlaylist) {
      onAddTrackToPlaylist(pl.id, trackToAdd.id);
      setAddedNotice(`Added "${trackToAdd.title}" to "${pl.name}"`);
      setTimeout(() => {
        setAddedNotice(null);
        onClose();
      }, 1200);
    } else {
      onPlayPlaylist(pl);
      onClose();
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/75 backdrop-blur-sm animate-in fade-in duration-150">
      <div className="w-full max-w-lg bg-[#0b1120] border border-slate-800 rounded-3xl p-6 shadow-2xl relative space-y-5">
        {/* Header */}
        <div className="flex items-center justify-between pb-3 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <div
              className="w-10 h-10 rounded-2xl flex items-center justify-center border"
              style={{ backgroundColor: `${accentHex}1a`, borderColor: `${accentHex}40` }}
            >
              <ListMusic className="w-5 h-5" style={{ color: accentHex }} />
            </div>
            <div>
              <h2 className="text-lg font-bold text-white">
                {trackToAdd ? 'Add to Playlist' : 'Vehicle Playlists'}
              </h2>
              <p className="text-xs text-slate-400">
                {trackToAdd
                  ? `Choose a playlist for "${trackToAdd.title}"`
                  : 'Curate your custom Nextcloud cruising sets'}
              </p>
            </div>
          </div>

          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {addedNotice && (
          <div className="p-3 bg-lime-950/60 border border-lime-700/60 rounded-xl text-xs text-lime-400 flex items-center gap-2">
            <Check className="w-4 h-4 shrink-0" />
            <span>{addedNotice}</span>
          </div>
        )}

        {/* Create Playlist Toggle & Form */}
        {!showCreateForm ? (
          <button
            onClick={() => setShowCreateForm(true)}
            className="w-full py-2.5 px-4 rounded-xl border border-dashed border-slate-700 hover:border-slate-500 text-xs font-semibold text-slate-300 flex items-center justify-center gap-2 transition-all hover:bg-slate-900/40"
          >
            <Plus className="w-4 h-4" style={{ color: accentHex }} />
            <span>Create New Playlist</span>
          </button>
        ) : (
          <form
            onSubmit={handleCreateSubmit}
            className="bg-black/40 border border-slate-800 rounded-2xl p-4 space-y-3"
          >
            <div className="space-y-1">
              <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                Playlist Name
              </label>
              <input
                type="text"
                required
                placeholder="e.g. Highway Cruising, Gym Jams, WoW Radio..."
                value={newTitle}
                onChange={(e) => setNewTitle(e.target.value)}
                className="w-full bg-[#070b14] border border-slate-700 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-lime-400"
              />
            </div>

            <div className="space-y-1">
              <label className="text-[11px] font-semibold text-slate-400 uppercase tracking-wider">
                Description (Optional)
              </label>
              <input
                type="text"
                placeholder="Brief description..."
                value={newDesc}
                onChange={(e) => setNewDesc(e.target.value)}
                className="w-full bg-[#070b14] border border-slate-700 rounded-xl px-3 py-2 text-xs text-white focus:outline-none focus:border-lime-400"
              />
            </div>

            <div className="flex items-center justify-end gap-2 pt-1">
              <button
                type="button"
                onClick={() => setShowCreateForm(false)}
                className="px-3 py-1.5 text-xs text-slate-400 hover:text-white"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-1.5 rounded-xl font-bold text-xs text-black transition-transform active:scale-95 shadow"
                style={{ backgroundColor: accentHex }}
              >
                Save Playlist
              </button>
            </div>
          </form>
        )}

        {/* Playlists List */}
        <div className="max-h-72 overflow-y-auto space-y-2 pr-1">
          {playlists.length === 0 ? (
            <div className="py-8 text-center text-slate-500 space-y-2">
              <FolderPlus className="w-8 h-8 mx-auto opacity-40" />
              <p className="text-xs">No custom playlists created yet.</p>
            </div>
          ) : (
            playlists.map((pl) => (
              <div
                key={pl.id}
                className="flex items-center justify-between p-3 rounded-2xl bg-black/40 border border-slate-800 hover:border-slate-700 transition-all group"
              >
                <div
                  onClick={() => handleSelectPlaylist(pl)}
                  className="flex items-center gap-3 min-w-0 flex-1 cursor-pointer"
                >
                  <div
                    className="w-10 h-10 rounded-xl flex items-center justify-center shrink-0 border"
                    style={{ backgroundColor: `${accentHex}1a`, borderColor: `${accentHex}33` }}
                  >
                    <Disc className="w-5 h-5" style={{ color: accentHex }} />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="text-xs font-bold text-white truncate group-hover:text-lime-300">
                      {pl.name}
                    </p>
                    <p className="text-[11px] text-slate-500 truncate">
                      {pl.trackIds.length} tracks {pl.description ? `• ${pl.description}` : ''}
                    </p>
                  </div>
                </div>

                <div className="flex items-center gap-2 shrink-0 ml-2">
                  {!trackToAdd && (
                    <button
                      onClick={() => {
                        onPlayPlaylist(pl);
                        onClose();
                      }}
                      className="p-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-white transition-colors"
                      title="Play Playlist"
                    >
                      <Play className="w-3.5 h-3.5" />
                    </button>
                  )}
                  <button
                    onClick={() => onDeletePlaylist(pl.id)}
                    className="p-2 rounded-xl text-slate-500 hover:text-red-400 hover:bg-red-950/40 transition-colors"
                    title="Delete Playlist"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
