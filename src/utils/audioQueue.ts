export interface QueueTrack {
  id: string;
  title: string;
  artist: string;
  album: string;
  duration: string;
  durationSeconds: number;
  sizeBytes: number;
  sizeFormatted: string;
  href: string;
  streamUrl: string;
  fileName: string;
  folderPath: string;
}

export function addToQueue(queue: QueueTrack[], track: QueueTrack): QueueTrack[] {
  // Avoid immediate consecutive duplicates
  if (queue.length > 0 && queue[queue.length - 1].id === track.id) {
    return queue;
  }
  return [...queue, track];
}

export function playNextInQueue(
  queue: QueueTrack[],
  currentIndex: number,
  track: QueueTrack
): QueueTrack[] {
  const nextQueue = [...queue];
  // Insert immediately after the current index
  const insertIndex = Math.max(0, currentIndex + 1);
  nextQueue.splice(insertIndex, 0, track);
  return nextQueue;
}

export function reorderQueue(
  queue: QueueTrack[],
  fromIndex: number,
  toIndex: number
): QueueTrack[] {
  if (
    fromIndex < 0 ||
    fromIndex >= queue.length ||
    toIndex < 0 ||
    toIndex >= queue.length ||
    fromIndex === toIndex
  ) {
    return queue;
  }
  const result = [...queue];
  const [removed] = result.splice(fromIndex, 1);
  result.splice(toIndex, 0, removed);
  return result;
}

export function removeFromQueue(queue: QueueTrack[], indexToRemove: number): QueueTrack[] {
  if (indexToRemove < 0 || indexToRemove >= queue.length) {
    return queue;
  }
  return queue.filter((_, idx) => idx !== indexToRemove);
}

export function resolveNextIndex(
  queueLength: number,
  currentIndex: number,
  shuffle: boolean,
  repeatMode: 'OFF' | 'ALL' | 'ONE'
): number {
  if (queueLength === 0) return -1;
  if (repeatMode === 'ONE') return currentIndex;
  if (shuffle) {
    if (queueLength === 1) return 0;
    let next = Math.floor(Math.random() * queueLength);
    if (next === currentIndex) {
      next = (next + 1) % queueLength;
    }
    return next;
  }
  if (currentIndex < queueLength - 1) {
    return currentIndex + 1;
  }
  return repeatMode === 'ALL' ? 0 : -1;
}

export function resolvePreviousIndex(
  queueLength: number,
  currentIndex: number,
  currentPositionSec: number
): number {
  if (queueLength === 0) return -1;
  // If track has been playing for more than 3 seconds, restart current track
  if (currentPositionSec > 3) {
    return currentIndex;
  }
  return currentIndex > 0 ? currentIndex - 1 : queueLength - 1;
}
