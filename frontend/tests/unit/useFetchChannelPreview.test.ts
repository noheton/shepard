/**
 * PERF7 — Unit tests for `useFetchChannelPreview` and `useChannelPreviewLazy`.
 *
 * Tests exercise:
 *   1. No fetch fires when the target element is never visible (lazy path).
 *   2. In-flight de-duplication: two concurrent calls with identical keys share
 *      one Promise and produce only one API call.
 *   3. Different downsample options on the same channel bypass the dedup map
 *      and fire separate requests.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// ── Mock @vueuse/core before importing the module under test ──────────────────
// The test environment is `node` (no IntersectionObserver); we stub
// `useIntersectionObserver` so we can control exactly when the intersection
// callback fires.
//
// NOTE: vi.mock factories are hoisted to the top of the file by Vitest, which
// means any `let` variables declared in the test file body are not yet
// initialised when the factory executes. We work around this by capturing state
// through a shared closure object that is allocated before the factory runs.

type IOCallback = (entries: Partial<IntersectionObserverEntry>[]) => void;

// Mutable state shared between factory and tests.
const ioState = {
  callback: null as IOCallback | null,
  stop: null as ((...args: unknown[]) => void) | null,
};

vi.mock("@vueuse/core", () => ({
  useIntersectionObserver: (
    _target: unknown,
    callback: IOCallback,
    _options?: unknown,
  ) => {
    ioState.callback = callback;
    const stop = vi.fn();
    ioState.stop = stop;
    return { stop };
  },
}));

// ── Mock useShepardApi ────────────────────────────────────────────────────────
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));

import { useFetchChannelPreview, useChannelPreviewLazy, _inFlightMap } from
  "~/composables/container/useFetchChannelPreview";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const mockGetTimeseries = vi.fn();

/** Helper: flush Promise queue so async fetch completes. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

/** A minimal channel fixture. */
const ch = {
  measurement: "vibration",
  device: "sensor-a",
  location: "turbopump",
  symbolicName: "vib_x",
  field: "rms",
  valueType: "Double" as const,
};

beforeEach(() => {
  vi.clearAllMocks();
  ioState.callback = null;
  ioState.stop = null;
  _inFlightMap.clear();

  (useShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ getTimeseries: mockGetTimeseries }),
  );

  mockGetTimeseries.mockResolvedValue({ points: [{ timestamp: 1000, value: 42 }] });
});

afterEach(() => {
  _inFlightMap.clear();
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 1 — lazy: no fetch when element is never visible
// ─────────────────────────────────────────────────────────────────────────────
describe("useChannelPreviewLazy", () => {
  it("does NOT fetch when the target element is never visible in the viewport", async () => {
    const target = ref<HTMLElement | null>(null);
    useChannelPreviewLazy(1, ch, target);

    // Never invoke the intersection callback → no fetch should fire.
    await flush();

    expect(mockGetTimeseries).not.toHaveBeenCalled();
  });

  it("fires exactly one fetch when the target first becomes visible", async () => {
    const target = ref<HTMLElement | null>(null);
    const { data } = useChannelPreviewLazy(1, ch, target);

    // Simulate intersection with isIntersecting = true.
    expect(ioState.callback).not.toBeNull();
    ioState.callback!([{ isIntersecting: true } as IntersectionObserverEntry]);
    await flush();

    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
    expect(data.value).toEqual([[1000, 42]]);
  });

  it("stops the observer after the first visible intersection (one-shot)", async () => {
    const target = ref<HTMLElement | null>(null);
    useChannelPreviewLazy(1, ch, target);

    ioState.callback!([{ isIntersecting: true } as IntersectionObserverEntry]);
    await flush();

    expect(ioState.stop).toHaveBeenCalledTimes(1);

    // A second intersection should NOT trigger another fetch.
    ioState.callback!([{ isIntersecting: true } as IntersectionObserverEntry]);
    await flush();

    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
  });

  it("refetch() fires immediately regardless of visibility state", async () => {
    const target = ref<HTMLElement | null>(null);
    const { refetch } = useChannelPreviewLazy(1, ch, target);

    // No intersection fired → but refetch bypasses the observer.
    await refetch();

    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 2 — in-flight de-duplication
// ─────────────────────────────────────────────────────────────────────────────
describe("useFetchChannelPreview — in-flight de-duplication", () => {
  it("collapses concurrent identical requests to a single HTTP call", async () => {
    // Two composable instances with the same containerId + channel + options.
    const a = useFetchChannelPreview(42, ch);
    const b = useFetchChannelPreview(42, ch);

    await flush();

    // Both should see the data but only one API call should have been made.
    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
    expect(a.data.value).toEqual([[1000, 42]]);
    expect(b.data.value).toEqual([[1000, 42]]);
  });

  it("does NOT dedup when downsample options differ (different cache key)", async () => {
    const a = useFetchChannelPreview(42, ch, { downsample: true });
    const b = useFetchChannelPreview(42, ch, { downsample: false });

    await flush();

    // Different keys → two separate HTTP calls.
    expect(mockGetTimeseries).toHaveBeenCalledTimes(2);

    // Downsampled call should include the downsample param.
    const firstCall = mockGetTimeseries.mock.calls[0]?.[0];
    const secondCall = mockGetTimeseries.mock.calls[1]?.[0];
    const calls = [firstCall, secondCall];
    expect(calls.some(c => c?.downsample === "lttb")).toBe(true);
    expect(calls.some(c => c?.downsample === undefined)).toBe(true);
  });

  it("removes in-flight entry after promise settles so next fetch is fresh", async () => {
    useFetchChannelPreview(42, ch);
    // Map should be populated synchronously.
    expect(_inFlightMap.size).toBe(1);

    await flush();

    // After settlement the entry is cleaned up.
    expect(_inFlightMap.size).toBe(0);
  });
});
