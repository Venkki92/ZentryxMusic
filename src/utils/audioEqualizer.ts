// Automotive Cabin Equalizer, Speed-Compensated Volume, and Acoustic Presets

export interface EqPreset {
  id: string;
  name: string;
  description: string;
  // Gains in dB for 60Hz, 250Hz, 1kHz, 4kHz, 12kHz
  bands: [number, number, number, number, number];
}

export const EQ_FREQUENCIES = [60, 250, 1000, 4000, 12000] as const;

export const EQ_PRESETS: EqPreset[] = [
  {
    id: 'flat',
    name: 'Flat / Studio Direct',
    description: 'Uncolored, neutral frequency response for hi-fi recordings',
    bands: [0, 0, 0, 0, 0],
  },
  {
    id: 'cabin_bass',
    name: 'Cabin Bass Slam',
    description: 'Deep low-end rumble tuned for automotive door subwoofers',
    bands: [6, 4, -1, 1, 3],
  },
  {
    id: 'podcast_clarity',
    name: 'Podcast & Vocal Clarity',
    description: 'Cuts low-frequency road rumble and elevates speech intelligibility',
    bands: [-5, -2, 4, 5, 2],
  },
  {
    id: 'highway_cruiser',
    name: 'Highway Cruiser',
    description: 'Compensates for tire drone and wind turbulence at 100+ km/h',
    bands: [5, 2, 2, 3, 4],
  },
  {
    id: 'rock_metal',
    name: 'Rock & Guitars',
    description: 'Aggressive mid-punch and crisp high cymbal resonance',
    bands: [4, 2, -2, 4, 5],
  },
  {
    id: 'club_electronic',
    name: 'Club & Electronic',
    description: 'Tight sub-kick with bright, airy top end',
    bands: [7, 3, -1, 3, 6],
  },
];

/**
 * Calculates volume compensation factor based on vehicle speed in km/h.
 * Sensitivity:
 * - 'off': 0 dB
 * - 'low': +1 dB per 30 km/h (capped at +3 dB)
 * - 'medium': +2 dB per 30 km/h (capped at +6 dB)
 * - 'high': +3 dB per 30 km/h (capped at +9 dB)
 */
export function calculateSpeedVolumeBoost(
  speedKmH: number,
  sensitivity: 'off' | 'low' | 'medium' | 'high'
): { dbBoost: number; linearMultiplier: number } {
  if (sensitivity === 'off' || speedKmH <= 15) {
    return { dbBoost: 0, linearMultiplier: 1.0 };
  }

  const effectiveSpeed = Math.max(0, speedKmH - 15); // Don't boost while crawling in driveway
  let dbPer30: number;
  let maxDb: number;

  switch (sensitivity) {
    case 'low':
      dbPer30 = 1.0;
      maxDb = 3.0;
      break;
    case 'medium':
      dbPer30 = 2.0;
      maxDb = 6.0;
      break;
    case 'high':
      dbPer30 = 3.0;
      maxDb = 9.0;
      break;
    default:
      return { dbBoost: 0, linearMultiplier: 1.0 };
  }

  const rawDb = (effectiveSpeed / 30) * dbPer30;
  const clampedDb = Math.min(rawDb, maxDb);
  const linearMultiplier = Math.pow(10, clampedDb / 20);

  return {
    dbBoost: Math.round(clampedDb * 10) / 10,
    linearMultiplier: Math.round(linearMultiplier * 100) / 100,
  };
}

/**
 * Validates and clamps individual EQ band gain (-12 dB to +12 dB)
 */
export function clampEqGain(gain: number): number {
  if (isNaN(gain)) return 0;
  return Math.max(-12, Math.min(12, Math.round(gain * 2) / 2));
}

/**
 * Attenuation gain calculation (e.g. -20dB for "Talk/Duck" button)
 */
export function calculateAttenuationMultiplier(isAttenuated: boolean, attenuationDb: number = -20): number {
  if (!isAttenuated) return 1.0;
  return Math.pow(10, attenuationDb / 20);
}

/**
 * Sleep timer expiration checker and fade-out volume multiplier
 * Last 10 seconds perform smooth cosine or linear fade-out to 0
 */
export function calculateSleepTimerFade(secondsRemaining: number | null): number {
  if (secondsRemaining === null || secondsRemaining <= 0) return 0.0;
  if (secondsRemaining > 10) return 1.0;
  return Math.max(0, Math.min(1, secondsRemaining / 10));
}
