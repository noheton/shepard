/** Inferno and viridis colormaps for 3D trace rendering (TPL2b / Trace3D). */

type RGB = [number, number, number];

const INFERNO_STOPS: [number, RGB][] = [
  [0.0,  [0.001, 0.000, 0.014]],
  [0.25, [0.341, 0.064, 0.429]],
  [0.5,  [0.725, 0.280, 0.198]],
  [0.75, [0.962, 0.619, 0.042]],
  [1.0,  [0.988, 1.000, 0.644]],
];

const VIRIDIS_STOPS: [number, RGB][] = [
  [0.0,  [0.267, 0.005, 0.329]],
  [0.25, [0.230, 0.322, 0.546]],
  [0.5,  [0.128, 0.566, 0.551]],
  [0.75, [0.370, 0.789, 0.383]],
  [1.0,  [0.994, 0.906, 0.144]],
];

const PLASMA_STOPS: [number, RGB][] = [
  [0.0,  [0.050, 0.030, 0.528]],
  [0.25, [0.460, 0.046, 0.659]],
  [0.5,  [0.798, 0.125, 0.462]],
  [0.75, [0.973, 0.463, 0.132]],
  [1.0,  [0.940, 0.975, 0.131]],
];

export type ColormapName = "inferno" | "viridis" | "plasma";

function interpolateStops(stops: [number, RGB][], t: number): RGB {
  const clamped = Math.max(0, Math.min(1, t));
  for (let i = 1; i < stops.length; i++) {
    const [t0, c0] = stops[i - 1];
    const [t1, c1] = stops[i];
    if (clamped <= t1) {
      const alpha = (clamped - t0) / (t1 - t0);
      return [
        c0[0] + alpha * (c1[0] - c0[0]),
        c0[1] + alpha * (c1[1] - c0[1]),
        c0[2] + alpha * (c1[2] - c0[2]),
      ];
    }
  }
  return stops[stops.length - 1][1];
}

/** Map t ∈ [0, 1] to an RGB triple ∈ [0, 1]³ using the named colormap. */
export function colormapRgb(t: number, name: ColormapName = "inferno"): RGB {
  const stops =
    name === "viridis" ? VIRIDIS_STOPS
    : name === "plasma" ? PLASMA_STOPS
    : INFERNO_STOPS;
  return interpolateStops(stops, t);
}

/** Normalize an array of values to [0, 1]. Returns [0.5, …] if all equal. */
export function normalizeValues(values: number[]): number[] {
  if (values.length === 0) return [];
  const min = Math.min(...values);
  const max = Math.max(...values);
  if (max === min) return values.map(() => 0.5);
  return values.map(v => (v - min) / (max - min));
}

/** Linear interpolation into a sorted array of [timestamp, value] pairs. */
export function lerpSeries(pts: [number, number][], t: number): number {
  if (pts.length === 0) return 0;
  if (t <= pts[0][0]) return pts[0][1];
  if (t >= pts[pts.length - 1][0]) return pts[pts.length - 1][1];
  let lo = 0;
  let hi = pts.length - 1;
  while (lo < hi - 1) {
    const mid = (lo + hi) >> 1;
    if (pts[mid][0] <= t) lo = mid;
    else hi = mid;
  }
  const [t0, v0] = pts[lo];
  const [t1, v1] = pts[hi];
  return v0 + ((t - t0) / (t1 - t0)) * (v1 - v0);
}
