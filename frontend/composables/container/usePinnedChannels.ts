/**
 * UX-PIN1 — Pinned channel tiles for PersonalDigest.
 *
 * Persists pinned channel identities to `localStorage` under the key
 * `shepard:pinnedChannels`.  Every caller shares the same module-level ref,
 * so a pin from the TimeseriesMeasurementsTable is visible on PersonalDigest
 * without a page reload (follows the `useWatchedCollections` singleton pattern).
 *
 * Backend preference sync is deferred to UX-PIN1b; localStorage provides
 * instant reactivity with zero network cost.
 */
import { ref, readonly } from "vue";

const STORAGE_KEY = "shepard:pinnedChannels";

export interface PinnedChannel {
  /** Stable single-field channel identity (UUID v4+, via TS-IDc). */
  shepardId: string;
  /** Postgres serial id of the owning TimeseriesContainer (V1-EXCEPTION: nav-only). */
  containerId: number;
  /** appId of the owning TimeseriesContainer (for /v2/ data fetch after APISIMP-TSCONT-APPID-KEY). */
  containerAppId?: string;
  /** Human-readable label (e.g. "LPT-001 · rms_g"). */
  channelName: string;
  /**
   * Optional route back to the source container so the tile can offer a
   * navigation link.  Stored as an absolute path string, e.g.
   * `/containers/timeseries/42`.
   */
  containerPath?: string;
}

// ── Module-scope singleton state ──────────────────────────────────────────────

function loadFromStorage(): PinnedChannel[] {
  if (typeof localStorage === "undefined") return [];
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as PinnedChannel[]) : [];
  } catch {
    return [];
  }
}

function persist(channels: PinnedChannel[]): void {
  if (typeof localStorage === "undefined") return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(channels));
  } catch {
    // storage quota or privacy mode — silently ignore
  }
}

// The single ref shared by all callers.
const _pinnedChannels = ref<PinnedChannel[]>(loadFromStorage());

// ── Public API ────────────────────────────────────────────────────────────────

export function usePinnedChannels() {
  function pin(channel: PinnedChannel): void {
    if (_pinnedChannels.value.some(c => c.shepardId === channel.shepardId)) return;
    _pinnedChannels.value = [..._pinnedChannels.value, channel];
    persist(_pinnedChannels.value);
  }

  function unpin(shepardId: string): void {
    _pinnedChannels.value = _pinnedChannels.value.filter(c => c.shepardId !== shepardId);
    persist(_pinnedChannels.value);
  }

  function isPinned(shepardId: string): boolean {
    return _pinnedChannels.value.some(c => c.shepardId === shepardId);
  }

  return {
    pinnedChannels: readonly(_pinnedChannels),
    pin,
    unpin,
    isPinned,
  };
}
