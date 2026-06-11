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
import { useFetchChannelPreview, useChannelPreviewLazy, _inFlightMap } from
  "~/composables/container/useFetchChannelPreview";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

// ── Mock @vueuse/core before importing the module under test ──────────────────
// The test environment is `node` (no IntersectionObserver); we stub
// `useIntersectionObserver` so we can control exactly when the intersection
// callback fires.
//
// NOTE: vi.mock factories are hoisted to the top of the file by Vitest, which
// means any `const`/`let` variables declared in the test file body are not yet
// initialised when the factory executes. We use `vi.hoisted` to lift the shared
// state object alongside the mock factory so the closure resolves cleanly.

type IOCallback = (entries: Partial<IntersectionObserverEntry>[]) => void;

// Mutable state shared between factory and tests, hoisted alongside vi.mock.
const ioState = vi.hoisted(() => ({
  callback: null as IOCallback | null,
  stop: null as ((...args: unknown[]) => void) | null,
}));

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
    useChannelPreviewLazy("1", ch, target);

    // Never invoke the intersection callback → no fetch should fire.
    await flush();

    expect(mockGetTimeseries).not.toHaveBeenCalled();
  });

  it("fires exactly one fetch when the target first becomes visible", async () => {
    const target = ref<HTMLElement | null>(null);
    const { data } = useChannelPreviewLazy("1", ch, target);

    // Simulate intersection with isIntersecting = true.
    expect(ioState.callback).not.toBeNull();
    ioState.callback!([{ isIntersecting: true } as IntersectionObserverEntry]);
    await flush();

    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
    expect(data.value).toEqual([[1000, 42]]);
  });

  it("stops the observer after the first visible intersection (one-shot)", async () => {
    const target = ref<HTMLElement | null>(null);
    useChannelPreviewLazy("1", ch, target);

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
    const { refetch } = useChannelPreviewLazy("1", ch, target);

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
    const a = useFetchChannelPreview("42", ch);
    const b = useFetchChannelPreview("42", ch);

    await flush();

    // Both should see the data but only one API call should have been made.
    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
    expect(a.data.value).toEqual([[1000, 42]]);
    expect(b.data.value).toEqual([[1000, 42]]);
  });

  it("does NOT dedup when downsample options differ (different cache key)", async () => {
    useFetchChannelPreview("42", ch, { downsample: true });
    useFetchChannelPreview("42", ch, { downsample: false });

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
    useFetchChannelPreview("42", ch);
    // Map should be populated synchronously.
    expect(_inFlightMap.size).toBe(1);

    await flush();

    // After settlement the entry is cleaned up.
    expect(_inFlightMap.size).toBe(0);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 3 — TS-LTTB-VIS-TOGGLE-01: Raw/LTTB toggle semantics
//
// These tests verify the contract that the chart viewer Raw/LTTB toggle
// drives in the composable layer:
//   - LTTB mode: ?downsample=lttb included in the request
//   - Raw mode:  no downsample param at all
//   - refetch(false) (Raw toggle): no downsample param
//   - refetch(true)  (LTTB toggle): ?downsample=lttb included
// ─────────────────────────────────────────────────────────────────────────────
describe("TS-LTTB-VIS-TOGGLE-01 — Raw / LTTB toggle: correct downsample param", () => {
  it("LTTB mode (downsample=true) sends ?downsample=lttb to the API", async () => {
    useFetchChannelPreview("42", ch, { downsample: true });
    await flush();

    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
    const call = mockGetTimeseries.mock.calls[0]?.[0];
    expect(call?.downsample).toBe("lttb");
  });

  it("Raw mode (downsample=false) does NOT send a downsample param to the API", async () => {
    useFetchChannelPreview("42", ch, { downsample: false });
    await flush();

    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
    const call = mockGetTimeseries.mock.calls[0]?.[0];
    expect(call?.downsample).toBeUndefined();
  });

  it("refetch(false) — Raw toggle — omits downsample param", async () => {
    const { refetch } = useFetchChannelPreview("42", ch, { downsample: true });
    await flush();
    vi.clearAllMocks();
    mockGetTimeseries.mockResolvedValue({ points: [] });

    // Simulates the user clicking "Raw" in TimeseriesAllChannelsChart
    await refetch(false);

    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
    const call = mockGetTimeseries.mock.calls[0]?.[0];
    expect(call?.downsample).toBeUndefined();
  });

  it("refetch(true) — LTTB toggle — includes downsample=lttb", async () => {
    const { refetch } = useFetchChannelPreview("42", ch, { downsample: false });
    await flush();
    vi.clearAllMocks();
    mockGetTimeseries.mockResolvedValue({ points: [] });

    // Simulates the user clicking "LTTB" in TimeseriesAllChannelsChart
    await refetch(true);

    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
    const call = mockGetTimeseries.mock.calls[0]?.[0];
    expect(call?.downsample).toBe("lttb");
  });

  it("downsampled ref tracks the active mode: true after LTTB fetch, false after Raw fetch", async () => {
    const { downsampled, refetch } = useFetchChannelPreview("42", ch, { downsample: true });
    await flush();
    expect(downsampled.value).toBe(true);

    mockGetTimeseries.mockResolvedValue({ points: [{ timestamp: 2000, value: 99 }] });
    await refetch(false);
    expect(downsampled.value).toBe(false);

    await refetch(true);
    expect(downsampled.value).toBe(true);
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Test 4 — TS-IDc: channelShepardId path
//
// When the caller supplies a {@code channelShepardId}, the composable must
// hit the v2 path-param endpoint
//   /v2/timeseries-containers/{containerId}/channels/{shepardId}/data
// instead of the legacy 5-tuple {@code getTimeseries} client call.
// ─────────────────────────────────────────────────────────────────────────────
describe("TS-IDc — channelShepardId path", () => {
  const SHEPARD_ID = "a2c0f1dd-4dce-4400-92e4-445cd18826e6";

  beforeEach(() => {
    // Replace global fetch so we can inspect the URL the new path uses.
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue({
        ok: true,
        json: async () => ({ points: [{ timestamp: 1500, value: 7 }] }),
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("hits the v2 shepardId endpoint when channelShepardId is supplied", async () => {
    const { data } = useFetchChannelPreview("1772", ch, {
      channelShepardId: SHEPARD_ID,
      downsample: true,
      maxPoints: 60,
    });
    await flush();

    // Legacy 5-tuple client method MUST NOT be called.
    expect(mockGetTimeseries).not.toHaveBeenCalled();

    // The raw fetch was hit with the canonical v2 path.
    expect(fetch).toHaveBeenCalledTimes(1);
    const url = (fetch as ReturnType<typeof vi.fn>).mock.calls[0]?.[0] as string;
    expect(url).toContain(`/v2/timeseries-containers/1772/channels/${SHEPARD_ID}/data`);
    expect(url).toContain("downsample=lttb");
    expect(url).toContain("max_points=60");

    expect(data.value).toEqual([[1500, 7]]);
  });

  it("falls through to the legacy 5-tuple path when channelShepardId is absent", async () => {
    useFetchChannelPreview("42", ch);
    await flush();

    expect(fetch).not.toHaveBeenCalled();
    expect(mockGetTimeseries).toHaveBeenCalledTimes(1);
  });

  it("dedup key collapses two callers passing the same shepardId to one fetch", async () => {
    useFetchChannelPreview("1772", ch, { channelShepardId: SHEPARD_ID });
    useFetchChannelPreview("1772", ch, { channelShepardId: SHEPARD_ID });
    await flush();

    // One HTTP call, two consumers — the in-flight map merges identical
    // shepardId-keyed requests.
    expect(fetch).toHaveBeenCalledTimes(1);
  });
});
