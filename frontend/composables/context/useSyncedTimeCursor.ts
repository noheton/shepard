/**
 * MFFD-MULTIPLAYER-1 — Synchronised time cursor for the multi-payload player.
 *
 * <p>Exposes a single reactive {@code currentTime} (DataObject-relative
 * milliseconds; t=0 is the start of the DO's playable window) that every
 * tile in {@code MultiPlayerPane.vue} reads from and writes to. This is the
 * "shared cursor" the AAA3 cross-track view introduced via the
 * {@code crossTime} ref, graduated to {@code provide}/{@code inject} so
 * tiles deep in a grid layout can wire up without prop drilling.
 *
 * <p><b>Time anchor.</b> All times in this composable are expressed in
 * <i>DataObject-relative milliseconds</i> — t=0 is the floor of the
 * playable window (the latest "available-from" across all subscribed
 * tiles). Each tile normalises its own source-time against this anchor.
 * The cross-track view uses "seconds from earliest"; this composable
 * uses milliseconds throughout to match {@code HTMLMediaElement.currentTime}
 * (which is in seconds but to ms precision — we multiply by 1000).
 *
 * <p><b>Playable range.</b> Computed as the <i>intersection</i> of every
 * subscribed tile's available range. A tile that lacks a time axis (e.g. a
 * pointcloud at a fixed timestamp) does not constrain the range; it can
 * read {@code currentTime} to render a static "highlight at t" marker.
 *
 * <p><b>Playback.</b> {@code play(rate)} drives a {@code requestAnimationFrame}
 * loop that advances {@code currentTime} at the given rate. {@code pause()}
 * stops it. {@code seek(t)} jumps to an absolute t (clamped to the range).
 * Reaching the right edge auto-pauses.
 *
 * <p>The composable is reusable across other multi-payload contexts (e.g. a
 * future Collection-level multi-DO sync); the {@code MultiPlayerPane}
 * mounts one instance per DO via {@link provideSyncedTimeCursor}.
 */
import { computed, inject, onScopeDispose, provide, ref } from "vue";
import type { ComputedRef, InjectionKey, Ref } from "vue";

/** A tile-supplied playable range in DO-relative ms. */
export interface TileRange {
  /** Tile id (stable identifier so the same tile can re-register). */
  id: string;
  /** Earliest playable t, DO-relative ms. */
  start: number;
  /** Latest playable t, DO-relative ms. */
  end: number;
}

export interface SyncedTimeCursor {
  /** Current cursor, DO-relative ms. Read+write — writes propagate to all subscribers. */
  currentTime: Ref<number>;
  /** Whether playback is currently advancing the cursor. */
  isPlaying: Ref<boolean>;
  /** Playback rate (1.0 = real-time). */
  rate: Ref<number>;
  /** The intersected playable range, or null when no constraining tile is registered. */
  range: ComputedRef<{ start: number; end: number } | null>;
  /** Number of tiles that have registered a range (drives the "hide when <2" gate). */
  constrainingTileCount: ComputedRef<number>;
  /** Move the cursor to {@code t} (clamped to {@code range}). */
  seek: (t: number) => void;
  /** Start playback. */
  play: () => void;
  /** Pause playback. */
  pause: () => void;
  /** Toggle play/pause. */
  togglePlay: () => void;
  /** Set the playback rate (must be positive). */
  setRate: (r: number) => void;
  /**
   * Register a tile's playable range. Returns an unregister function the
   * tile should call on unmount.
   */
  registerRange: (range: TileRange) => () => void;
}

/** Symbol-typed injection key so multiple cursors can coexist without collision. */
export const SyncedTimeCursorKey: InjectionKey<SyncedTimeCursor> = Symbol(
  "SyncedTimeCursor",
);

/**
 * Compute the intersection of a list of ranges. Returns null when the list
 * is empty or the intersection is degenerate (start > end).
 */
export function intersectRanges(
  ranges: ReadonlyArray<TileRange>,
): { start: number; end: number } | null {
  if (ranges.length === 0) return null;
  let start = -Infinity;
  let end = Infinity;
  for (const r of ranges) {
    if (r.start > start) start = r.start;
    if (r.end < end) end = r.end;
  }
  if (start === -Infinity || end === Infinity) return null;
  if (start > end) return null;
  return { start, end };
}

/** Clamp {@code t} to {@code [lo, hi]}. */
export function clamp(t: number, lo: number, hi: number): number {
  if (t < lo) return lo;
  if (t > hi) return hi;
  return t;
}

/**
 * Build a {@link SyncedTimeCursor} instance and provide it on the current
 * component's scope. Callers (tiles) read it via {@link useSyncedTimeCursor}.
 *
 * <p>The animation loop uses {@code requestAnimationFrame} when available
 * (browsers) and falls back to {@code setTimeout(0)} for jsdom tests.
 */
export function provideSyncedTimeCursor(): SyncedTimeCursor {
  const currentTime = ref(0);
  const isPlaying = ref(false);
  const rate = ref(1);
  const ranges = ref<TileRange[]>([]);

  const range = computed(() => intersectRanges(ranges.value));
  const constrainingTileCount = computed(() => ranges.value.length);

  function registerRange(r: TileRange): () => void {
    // Replace any prior registration for the same id (re-mount safe).
    ranges.value = [...ranges.value.filter(x => x.id !== r.id), r];
    return () => {
      ranges.value = ranges.value.filter(x => x.id !== r.id);
    };
  }

  function seek(t: number): void {
    const rng = range.value;
    if (rng) {
      currentTime.value = clamp(t, rng.start, rng.end);
    } else {
      currentTime.value = t;
    }
  }

  let lastTickMs: number | null = null;
  let rafHandle: number | null = null;

  function tick(now: number): void {
    if (!isPlaying.value) {
      lastTickMs = null;
      return;
    }
    if (lastTickMs == null) {
      lastTickMs = now;
    } else {
      const dt = now - lastTickMs;
      lastTickMs = now;
      const next = currentTime.value + dt * rate.value;
      const rng = range.value;
      if (rng && next >= rng.end) {
        currentTime.value = rng.end;
        pause();
        return;
      }
      currentTime.value = rng ? clamp(next, rng.start, rng.end) : next;
    }
    scheduleNext();
  }

  function scheduleNext(): void {
    if (typeof requestAnimationFrame === "function") {
      rafHandle = requestAnimationFrame(tick);
    } else {
      rafHandle = setTimeout(() => tick(Date.now()), 16) as unknown as number;
    }
  }

  function cancelScheduled(): void {
    if (rafHandle == null) return;
    if (typeof cancelAnimationFrame === "function") {
      cancelAnimationFrame(rafHandle);
    } else {
      clearTimeout(rafHandle as unknown as ReturnType<typeof setTimeout>);
    }
    rafHandle = null;
  }

  function play(): void {
    if (isPlaying.value) return;
    const rng = range.value;
    // If at right edge, rewind to start before playing.
    if (rng && currentTime.value >= rng.end) {
      currentTime.value = rng.start;
    }
    isPlaying.value = true;
    lastTickMs = null;
    scheduleNext();
  }

  function pause(): void {
    isPlaying.value = false;
    lastTickMs = null;
    cancelScheduled();
  }

  function togglePlay(): void {
    if (isPlaying.value) pause();
    else play();
  }

  function setRate(r: number): void {
    if (!Number.isFinite(r) || r <= 0) return;
    rate.value = r;
  }

  onScopeDispose(() => {
    cancelScheduled();
  });

  const api: SyncedTimeCursor = {
    currentTime,
    isPlaying,
    rate,
    range,
    constrainingTileCount,
    seek,
    play,
    pause,
    togglePlay,
    setRate,
    registerRange,
  };
  provide(SyncedTimeCursorKey, api);
  return api;
}

/**
 * Resolve the synced time cursor from the nearest ancestor that called
 * {@link provideSyncedTimeCursor}. Throws when no provider exists — tiles
 * are expected to render only inside a {@code MultiPlayerPane}.
 */
export function useSyncedTimeCursor(): SyncedTimeCursor {
  const api = inject(SyncedTimeCursorKey, null);
  if (!api) {
    throw new Error(
      "useSyncedTimeCursor: no provider in scope. Tile must mount inside MultiPlayerPane.",
    );
  }
  return api;
}
