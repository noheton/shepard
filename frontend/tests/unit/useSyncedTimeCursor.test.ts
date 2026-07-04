/**
 * MFFD-MULTIPLAYER-1 — tests for the synced time cursor composable.
 *
 * The composable owns the only cross-tile reactive state in the multi-player
 * pane: cursor position, play/pause, rate, and the per-tile playable-range
 * registration. These tests exercise that logic without any DOM — the
 * provide/inject wiring and tile components are validated separately in the
 * MultiPlayerPane component test.
 */
import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { effectScope, nextTick } from "vue";
import {
  clamp,
  intersectRanges,
  provideSyncedTimeCursor,
  type SyncedTimeCursor,
} from "../../composables/context/useSyncedTimeCursor";

/**
 * Run {@code provideSyncedTimeCursor} inside an effect scope so {@code provide}
 * has a current instance to attach to. Returns the cursor and a teardown.
 */
function withCursor<T>(fn: (api: SyncedTimeCursor) => T): T {
  const scope = effectScope();
  try {
    return scope.run(() => fn(provideSyncedTimeCursor()))!;
  } finally {
    scope.stop();
  }
}

describe("intersectRanges", () => {
  it("returns null for an empty list", () => {
    expect(intersectRanges([])).toBeNull();
  });

  it("returns the single range when only one tile registered", () => {
    expect(
      intersectRanges([{ id: "ts", start: 100, end: 500 }]),
    ).toEqual({ start: 100, end: 500 });
  });

  it("returns the intersection of two overlapping ranges", () => {
    expect(
      intersectRanges([
        { id: "ts", start: 100, end: 500 },
        { id: "video", start: 200, end: 400 },
      ]),
    ).toEqual({ start: 200, end: 400 });
  });

  it("returns null when ranges do not overlap", () => {
    expect(
      intersectRanges([
        { id: "ts", start: 0, end: 100 },
        { id: "video", start: 200, end: 300 },
      ]),
    ).toBeNull();
  });

  it("intersects three ranges correctly", () => {
    expect(
      intersectRanges([
        { id: "ts", start: 0, end: 1000 },
        { id: "video", start: 100, end: 800 },
        { id: "thermo", start: 200, end: 600 },
      ]),
    ).toEqual({ start: 200, end: 600 });
  });
});

describe("clamp", () => {
  it("returns the value when it's in range", () => {
    expect(clamp(50, 0, 100)).toBe(50);
  });
  it("clamps to the lower bound", () => {
    expect(clamp(-10, 0, 100)).toBe(0);
  });
  it("clamps to the upper bound", () => {
    expect(clamp(200, 0, 100)).toBe(100);
  });
});

describe("provideSyncedTimeCursor — initial state", () => {
  it("starts at t=0, paused, rate=1, with null range", () => {
    withCursor(api => {
      expect(api.currentTime.value).toBe(0);
      expect(api.isPlaying.value).toBe(false);
      expect(api.rate.value).toBe(1);
      expect(api.range.value).toBeNull();
      expect(api.constrainingTileCount.value).toBe(0);
    });
  });
});

describe("registerRange", () => {
  it("computes intersection across two tiles and tracks the constraining count", () => {
    withCursor(api => {
      const unregTs = api.registerRange({ id: "ts", start: 0, end: 1000 });
      const unregVid = api.registerRange({ id: "video", start: 200, end: 800 });
      expect(api.range.value).toEqual({ start: 200, end: 800 });
      expect(api.constrainingTileCount.value).toBe(2);
      unregTs();
      expect(api.range.value).toEqual({ start: 200, end: 800 });
      expect(api.constrainingTileCount.value).toBe(1);
      unregVid();
      expect(api.range.value).toBeNull();
      expect(api.constrainingTileCount.value).toBe(0);
    });
  });

  it("replaces a prior registration with the same id (re-mount safe)", () => {
    withCursor(api => {
      api.registerRange({ id: "ts", start: 0, end: 500 });
      expect(api.range.value).toEqual({ start: 0, end: 500 });
      api.registerRange({ id: "ts", start: 100, end: 900 });
      expect(api.range.value).toEqual({ start: 100, end: 900 });
      expect(api.constrainingTileCount.value).toBe(1);
    });
  });
});

describe("seek", () => {
  it("clamps to the playable range when a range is set", () => {
    withCursor(api => {
      api.registerRange({ id: "ts", start: 100, end: 500 });
      api.seek(50);
      expect(api.currentTime.value).toBe(100);
      api.seek(750);
      expect(api.currentTime.value).toBe(500);
      api.seek(300);
      expect(api.currentTime.value).toBe(300);
    });
  });

  it("accepts any value when no range is set", () => {
    withCursor(api => {
      api.seek(1234);
      expect(api.currentTime.value).toBe(1234);
    });
  });
});

describe("play / pause / togglePlay / setRate", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    // Force the setTimeout fallback path — provideSyncedTimeCursor checks
    // typeof requestAnimationFrame === "function". jsdom may have one;
    // delete it so fake timers can drive ticks.
    // @ts-expect-error window may be undefined in node; this is best-effort.
    delete globalThis.requestAnimationFrame;
    // @ts-expect-error see above.
    delete globalThis.cancelAnimationFrame;
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("play() flips isPlaying true; pause() flips it false", () => {
    withCursor(api => {
      api.registerRange({ id: "ts", start: 0, end: 1000 });
      api.play();
      expect(api.isPlaying.value).toBe(true);
      api.pause();
      expect(api.isPlaying.value).toBe(false);
    });
  });

  it("togglePlay() flips state both directions", () => {
    withCursor(api => {
      api.registerRange({ id: "ts", start: 0, end: 1000 });
      expect(api.isPlaying.value).toBe(false);
      api.togglePlay();
      expect(api.isPlaying.value).toBe(true);
      api.togglePlay();
      expect(api.isPlaying.value).toBe(false);
    });
  });

  it("advances currentTime under fake timers at the configured rate", () => {
    withCursor(api => {
      api.registerRange({ id: "ts", start: 0, end: 100_000 });
      api.setRate(1);
      api.play();
      // First tick captures the start time; second tick advances by ~16ms*rate.
      // We let several timer cycles fire and assert that the cursor has moved.
      vi.advanceTimersByTime(500);
      expect(api.currentTime.value).toBeGreaterThan(0);
      api.pause();
    });
  });

  it("setRate(2) advances roughly twice as fast as setRate(1)", () => {
    let dt1 = 0;
    let dt2 = 0;
    withCursor(api => {
      api.registerRange({ id: "ts", start: 0, end: 1_000_000 });
      api.setRate(1);
      api.play();
      vi.advanceTimersByTime(500);
      dt1 = api.currentTime.value;
      api.pause();
    });
    withCursor(api => {
      api.registerRange({ id: "ts", start: 0, end: 1_000_000 });
      api.setRate(2);
      api.play();
      vi.advanceTimersByTime(500);
      dt2 = api.currentTime.value;
      api.pause();
    });
    expect(dt2).toBeGreaterThan(dt1 * 1.4);
  });

  it("setRate rejects non-positive or non-finite values", () => {
    withCursor(api => {
      api.setRate(2);
      api.setRate(0);
      expect(api.rate.value).toBe(2);
      api.setRate(-1);
      expect(api.rate.value).toBe(2);
      api.setRate(Number.NaN);
      expect(api.rate.value).toBe(2);
      api.setRate(Number.POSITIVE_INFINITY);
      expect(api.rate.value).toBe(2);
    });
  });

  it("auto-pauses at the right edge of the range", () => {
    withCursor(api => {
      api.registerRange({ id: "ts", start: 0, end: 200 });
      api.setRate(100); // 100ms-of-cursor per real ms — finishes fast.
      api.play();
      vi.advanceTimersByTime(500);
      expect(api.isPlaying.value).toBe(false);
      expect(api.currentTime.value).toBe(200);
    });
  });

  it("calling play() from the right edge rewinds to start", () => {
    withCursor(api => {
      api.registerRange({ id: "ts", start: 50, end: 200 });
      api.seek(200);
      expect(api.currentTime.value).toBe(200);
      api.play();
      // First tick sets lastTickMs but does not advance the cursor; the
      // value should now be at start.
      expect(api.currentTime.value).toBe(50);
      api.pause();
    });
  });
});

describe("useSyncedTimeCursor without a provider", () => {
  it("throws a descriptive error", async () => {
    const { useSyncedTimeCursor } = await import(
      "../../composables/context/useSyncedTimeCursor"
    );
    expect(() => useSyncedTimeCursor()).toThrow(/no provider in scope/);
  });
});

describe("currentTime reactive propagation", () => {
  it("seek updates the same Ref that subscribers read", async () => {
    await withCursor(async api => {
      api.registerRange({ id: "ts", start: 0, end: 1000 });
      let observed = -1;
      const stop = vi.fn();
      // Simulate a tile that watches currentTime.
      const watcher = (
        await import("vue")
      ).watch(api.currentTime, t => {
        observed = t;
      });
      api.seek(400);
      await nextTick();
      expect(observed).toBe(400);
      watcher();
      stop();
    });
  });
});
