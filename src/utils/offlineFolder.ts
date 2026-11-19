import { deleteSettingHandle, getSettingHandle, saveSettingHandle } from './idbStorage';

const OFFLINE_DIR_HANDLE_KEY = 'offline_download_directory_handle';
const OFFLINE_DIR_NAME_STORAGE_KEY = 'zentryx_offline_folder_name';

export interface SelectedOfflineFolder {
  handle: any;
  name: string;
  permission: 'granted' | 'prompt' | 'denied';
}

export function isFolderPickerSupported(): boolean {
  return typeof window !== 'undefined' && typeof (window as any).showDirectoryPicker === 'function';
}

export function getSavedOfflineFolderLabel(): string | null {
  try {
    return localStorage.getItem(OFFLINE_DIR_NAME_STORAGE_KEY);
  } catch {
    return null;
  }
}

export async function loadSavedOfflineFolder(): Promise<SelectedOfflineFolder | null> {
  const handle = await getSettingHandle<any>(OFFLINE_DIR_HANDLE_KEY);
  if (!handle) {
    const label = getSavedOfflineFolderLabel();
    return label ? { handle: null, name: label, permission: 'prompt' } : null;
  }

  let permission: 'granted' | 'prompt' | 'denied' = 'prompt';
  try {
    if (typeof handle.queryPermission === 'function') {
      permission = await handle.queryPermission({ mode: 'readwrite' });
    }
  } catch {
    permission = 'prompt';
  }

  const name = handle.name || getSavedOfflineFolderLabel() || 'Selected Folder';
  return { handle, name, permission };
}

export async function pickOfflineFolder(): Promise<SelectedOfflineFolder> {
  if (!isFolderPickerSupported()) {
    throw new Error(
      'Directory picker is not available in this embedded view or browser. Open in Chrome/Edge or use the Android APK SAF folder picker.'
    );
  }

  const handle = await (window as any).showDirectoryPicker({
    id: 'zentryx-offline-tracks',
    mode: 'readwrite',
  });

  let permission: 'granted' | 'prompt' | 'denied' = 'granted';
  if (typeof handle.requestPermission === 'function') {
    permission = await handle.requestPermission({ mode: 'readwrite' });
  }
  if (permission !== 'granted') {
    throw new Error('Write permission was not granted for the selected folder.');
  }

  const name = handle.name || 'Offline Music';
  await saveSettingHandle(OFFLINE_DIR_HANDLE_KEY, handle);
  try {
    localStorage.setItem(OFFLINE_DIR_NAME_STORAGE_KEY, name);
  } catch {}

  return { handle, name, permission };
}

export async function authorizeSavedOfflineFolder(handle: any): Promise<boolean> {
  if (!handle) return false;
  try {
    if (typeof handle.requestPermission === 'function') {
      const perm = await handle.requestPermission({ mode: 'readwrite' });
      return perm === 'granted';
    }
    return true;
  } catch {
    return false;
  }
}

export async function clearSavedOfflineFolder(): Promise<void> {
  await deleteSettingHandle(OFFLINE_DIR_HANDLE_KEY);
  try {
    localStorage.removeItem(OFFLINE_DIR_NAME_STORAGE_KEY);
  } catch {}
}

export function sanitizeAudioFileName(fileName: string, artist: string, title: string): string {
  const extMatch = (fileName || '').match(/\.(mp3|flac|m4a|aac|ogg|opus|wav|wma|m4b|alac|aiff)$/i);
  const ext = extMatch ? extMatch[0].toLowerCase() : '.mp3';
  const base = (fileName ? fileName.replace(/\.[^/.]+$/, '') : `${artist} - ${title}`)
    .replace(/[<>:"/\\|?*\x00-\x1f]/g, '_')
    .trim()
    .slice(0, 120);
  return `${base || 'track'}${ext}`;
}

export async function saveTrackToSelectedFolder(
  handle: any,
  fileName: string,
  artist: string,
  title: string,
  blob: Blob
): Promise<{ savedToDisk: boolean; writtenFileName?: string }> {
  if (!handle) return { savedToDisk: false };

  let perm = 'granted';
  try {
    if (typeof handle.queryPermission === 'function') {
      perm = await handle.queryPermission({ mode: 'readwrite' });
    }
    if (perm !== 'granted' && typeof handle.requestPermission === 'function') {
      perm = await handle.requestPermission({ mode: 'readwrite' });
    }
  } catch {
    return { savedToDisk: false };
  }

  if (perm !== 'granted') return { savedToDisk: false };

  const safeName = sanitizeAudioFileName(fileName, artist, title);
  const fileHandle = await handle.getFileHandle(safeName, { create: true });
  const writable = await fileHandle.createWritable();
  await writable.write(blob);
  await writable.close();
  return { savedToDisk: true, writtenFileName: safeName };
}
