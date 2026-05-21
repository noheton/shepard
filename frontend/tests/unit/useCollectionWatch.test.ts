import { describe, it, expect, vi, beforeEach } from "vitest";

// Mock BEFORE importing the module under test so the mock is hoisted.
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

import { useCollectionWatch } from "~/composables/context/useCollectionWatch";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

// Helpers that the mock CollectionWatchesApi can be configured to resolve.
const mockGetMyWatch = vi.fn();
const mockWatch = vi.fn();
const mockUnwatch = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({
      getMyWatch: mockGetMyWatch,
      watch: mockWatch,
      unwatch: mockUnwatch,
    }),
  );
});

/** Flush micro-task queue (watch({ immediate }) fires after the current tick). */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useCollectionWatch — initial fetch", () => {
  it("isWatching starts false before the probe resolves", () => {
    mockGetMyWatch.mockResolvedValue({});
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    // Before async resolves — should be false
    expect(isWatching.value).toBe(false);
  });

  it("sets isWatching=true after getMyWatch resolves (200)", async () => {
    mockGetMyWatch.mockResolvedValue({});
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(true);
  });

  it("sets isWatching=false when getMyWatch throws (404 = not watching)", async () => {
    mockGetMyWatch.mockRejectedValue(Object.assign(new Error("Not Found"), { status: 404 }));
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(false);
  });

  it("treats any error as not-watching (e.g. 500)", async () => {
    mockGetMyWatch.mockRejectedValue(new Error("Internal Server Error"));
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(false);
  });

  it("skips the probe and stays false when appId is null", async () => {
    const { isWatching } = useCollectionWatch(ref(null));
    await flush();
    expect(isWatching.value).toBe(false);
    expect(mockGetMyWatch).not.toHaveBeenCalled();
  });
});

describe("useCollectionWatch — toggle()", () => {
  it("calls watch() when not currently watching", async () => {
    // Start as not watching (404)
    mockGetMyWatch.mockRejectedValue(new Error("Not Found"));
    mockWatch.mockResolvedValue({});

    const { isWatching, toggle } = useCollectionWatch(ref("col-abc"));
    await flush(); // settle initial state

    expect(isWatching.value).toBe(false);
    await toggle();

    expect(mockWatch).toHaveBeenCalledWith({ collectionAppId: "col-abc" });
    expect(isWatching.value).toBe(true);
  });

  it("calls unwatch() when currently watching", async () => {
    mockGetMyWatch.mockResolvedValue({});
    mockUnwatch.mockResolvedValue(undefined);

    const { isWatching, toggle } = useCollectionWatch(ref("col-abc"));
    await flush();

    expect(isWatching.value).toBe(true);
    await toggle();

    expect(mockUnwatch).toHaveBeenCalledWith({ collectionAppId: "col-abc" });
    expect(isWatching.value).toBe(false);
  });

  it("optimistically updates isWatching before the API call resolves", async () => {
    mockGetMyWatch.mockRejectedValue(new Error("Not Found"));
    let resolveWatch!: () => void;
    mockWatch.mockReturnValue(new Promise<void>(r => { resolveWatch = r; }));

    const { isWatching, toggle } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(false);

    // Do NOT await — check state while the call is in-flight
    const togglePromise = toggle();
    expect(isWatching.value).toBe(true); // optimistic
    resolveWatch();
    await togglePromise;
    expect(isWatching.value).toBe(true);
  });

  it("reverts optimistic update when watch() fails", async () => {
    mockGetMyWatch.mockRejectedValue(new Error("Not Found"));
    mockWatch.mockRejectedValue(new Error("Server error"));

    const { isWatching, toggle } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(false);

    await toggle();

    expect(isWatching.value).toBe(false); // reverted
  });

  it("reverts optimistic update when unwatch() fails", async () => {
    mockGetMyWatch.mockResolvedValue({});
    mockUnwatch.mockRejectedValue(new Error("Server error"));

    const { isWatching, toggle } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(true);

    await toggle();

    expect(isWatching.value).toBe(true); // reverted
  });

  it("is a no-op when appId is null", async () => {
    const { isWatching, toggle } = useCollectionWatch(ref(null));
    await flush();
    await toggle();

    expect(mockWatch).not.toHaveBeenCalled();
    expect(mockUnwatch).not.toHaveBeenCalled();
    expect(isWatching.value).toBe(false);
  });
});

describe("useCollectionWatch — appId change", () => {
  it("re-fetches when appId ref changes", async () => {
    // Initially not watching
    mockGetMyWatch.mockRejectedValue(new Error("Not Found"));
    const appId = ref<string | null>("col-1");

    const { isWatching } = useCollectionWatch(appId);
    await flush();
    expect(isWatching.value).toBe(false);

    // Switch to a different collection that the user watches
    mockGetMyWatch.mockResolvedValue({});
    appId.value = "col-2";
    await flush();

    expect(isWatching.value).toBe(true);
    expect(mockGetMyWatch).toHaveBeenCalledTimes(2);
  });

  it("resets to false when appId changes to null", async () => {
    mockGetMyWatch.mockResolvedValue({});
    const appId = ref<string | null>("col-1");

    const { isWatching } = useCollectionWatch(appId);
    await flush();
    expect(isWatching.value).toBe(true);

    appId.value = null;
    await flush();

    expect(isWatching.value).toBe(false);
  });
});

describe("useCollectionWatch — plain string appId", () => {
  it("accepts a plain string (non-ref) appId", async () => {
    mockGetMyWatch.mockResolvedValue({});
    const { isWatching } = useCollectionWatch("col-static");
    await flush();
    expect(isWatching.value).toBe(true);
    expect(mockGetMyWatch).toHaveBeenCalledWith({ collectionAppId: "col-static" });
  });
});
