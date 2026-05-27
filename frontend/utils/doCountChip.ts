/**
 * UI-011b — colour-band chip utility for the `# DOs` column on the
 * collections list.
 *
 * Buckets:
 *   0          → grey  ("default")   — empty collection
 *   1–10       → green ("success")   — small
 *   11–100     → blue  ("info")      — medium
 *   101–1000   → orange("warning")   — large
 *   > 1000     → red   ("error")     — very large
 *
 * Uses Vuetify semantic colour tokens so the chip respects the active theme.
 */

export type DoCountColor = "default" | "success" | "info" | "warning" | "error";

export interface DoCountChip {
  /** Vuetify colour token to pass as `:color` on `<v-chip>`. */
  color: DoCountColor;
  /** Human-readable label (same as the raw number, kept for completeness). */
  label: string;
}

/**
 * Map a raw DataObject count to a `{ color, label }` descriptor for a
 * Vuetify `<v-chip>`.
 */
export function useDoCountChip(count: number): DoCountChip {
  const label = String(count);
  if (count === 0) return { color: "default", label };
  if (count <= 10) return { color: "success", label };
  if (count <= 100) return { color: "info", label };
  if (count <= 1000) return { color: "warning", label };
  return { color: "error", label };
}
