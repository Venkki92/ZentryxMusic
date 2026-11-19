/**
 * Astronomical NOAA Solar Elevation calculator for GPS-aware automatic
 * Cockpit Day / Night Mode switching (Mercedes S203 headunit comfort).
 */

const DEG_TO_RAD = Math.PI / 180;
const RAD_TO_DEG = 180 / Math.PI;

export function calculateSolarElevationDegrees(
  latitude: number,
  longitude: number,
  timeMs: number = Date.now()
): number {
  const d = new Date(timeMs);
  const startOfYearUtc = Date.UTC(d.getUTCFullYear(), 0, 1, 0, 0, 0);
  const dayOfYear = Math.floor((timeMs - startOfYearUtc) / 86_400_000) + 1;
  const hourUtc =
    d.getUTCHours() + d.getUTCMinutes() / 60 + d.getUTCSeconds() / 3600;

  // Fractional year gamma in radians
  const gamma = ((2 * Math.PI) / 365) * (dayOfYear - 1 + (hourUtc - 12) / 24);

  // Equation of time (minutes)
  const eqTime =
    229.18 *
    (0.000075 +
      0.001868 * Math.cos(gamma) -
      0.032077 * Math.sin(gamma) -
      0.014615 * Math.cos(2 * gamma) -
      0.040849 * Math.sin(2 * gamma));

  // Solar declination angle (radians)
  const decl =
    0.006918 -
    0.399912 * Math.cos(gamma) +
    0.070257 * Math.sin(gamma) -
    0.006758 * Math.cos(2 * gamma) +
    0.000907 * Math.sin(2 * gamma) -
    0.002697 * Math.cos(3 * gamma) +
    0.00148 * Math.sin(3 * gamma);

  const trueSolarTimeMin = (((hourUtc * 60 + eqTime + 4 * longitude) % 1440) + 1440) % 1440;
  const hourAngleRad = (trueSolarTimeMin / 4 - 180) * DEG_TO_RAD;

  const latRad = latitude * DEG_TO_RAD;
  const cosZenith = Math.max(
    -1,
    Math.min(
      1,
      Math.sin(latRad) * Math.sin(decl) +
        Math.cos(latRad) * Math.cos(decl) * Math.cos(hourAngleRad)
    )
  );
  const zenithDeg = Math.acos(cosZenith) * RAD_TO_DEG;
  return 90 - zenithDeg;
}

/**
 * Returns true when sun elevation is above civil twilight horizon (-0.833°).
 * Falls back cleanly to local clock window (06:30–19:30) without a GPS fix.
 */
export function isDaylightBySolar(
  latitude: number | null | undefined,
  longitude: number | null | undefined,
  timeMs: number = Date.now()
): boolean {
  if (
    typeof latitude === 'number' &&
    Number.isFinite(latitude) &&
    typeof longitude === 'number' &&
    Number.isFinite(longitude)
  ) {
    return calculateSolarElevationDegrees(latitude, longitude, timeMs) > -0.833;
  }
  const now = new Date(timeMs);
  const mins = now.getHours() * 60 + now.getMinutes();
  return mins >= 6 * 60 + 30 && mins <= 19 * 60 + 30;
}
