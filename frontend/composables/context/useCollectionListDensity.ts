/**
 * UX-WALK-2026-05-29-05 — Collections list density toggle.
 *
 * Persists the user's preferred row density to `localStorage` under
 * `"shepard:collections-list-density"` so it survives page reloads.
 *
 * Values mirror Vuetify's `density` prop on `v-data-table`:
 *   - "compact"     — tightest rows, ~2× more collections visible at 4K
 *   - "comfortable" — Vuetify default (medium)
 *   - "default"     — tallest rows (most whitespace)
 *
 * Pattern: same module-scope singleton as `usePinnedChannels` so every
 * consumer (toggle in header, table in body) stays in sync without props.
 */
import { ref, readonly } from "vue";

export type CollectionListDensity = "compact" | "comfortable" | "default";

const STORAGE_KEY = "shepard:collections-list-density";
const DEFAULT_DENSITY: CollectionListDensity = "comfortable";
const VALID_VALUES: CollectionListDensity[] = ["compact", "comfortable", "default"];

function readStored(): CollectionListDensity {
  if (typeof localStorage === "undefined") return DEFAULT_DENSITY;
  const raw = localStorage.getItem(STORAGE_KEY);
  if (raw && (VALID_VALUES as string[]).includes(raw)) {
    return raw as CollectionListDensity;
  }
  return DEFAULT_DENSITY;
}

// Module-scope singleton so the toggle and the table share one ref.
const _density = ref<CollectionListDensity>(readStored());

export function useCollectionListDensity() {
  function setDensity(value: CollectionListDensity): void {
    _density.value = value;
    if (typeof localStorage !== "undefined") {
      localStorage.setItem(STORAGE_KEY, value);
    }
  }

  return {
    density: readonly(_density),
    setDensity,
    DENSITY_OPTIONS: VALID_VALUES,
  };
}
