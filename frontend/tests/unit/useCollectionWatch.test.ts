import { describe, it, expect, vi, beforeEach } from "vitest";
import { CollectionWatchesApi, MeApi } from "@dlr-shepard/backend-client";

// Mock BEFORE importing the module under test so the mock is hoisted.
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

import { useCollectionWatch, _resetUsernameCacheForTest } from "~/composables/context/useCollectionWatch";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

// CW1 wire mocks. The composable now (UI-005) calls listWatchers + getMe
// instead of getMyWatch so the browser never emits a 404 on landing.
const mockListWatchers = vi.fn();
const mockWatch = vi.fn();
const mockUnwatch = vi.fn();
const mockGetMe = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  _resetUsernameCacheForTest();
  // useV2ShepardApi(CollectionWatchesApi) vs useV2ShepardApi(MeApi)
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockImplementation((apiClass: unknown) => {
    if (apiClass === MeApi) {
      return ref({ getMe: mockGetMe });
    }
    if (apiClass === CollectionWatchesApi) {
      return ref({
        listWatchers: mockListWatchers,
        watch: mockWatch,
        unwatch: mockUnwatch,
      });
    }
    return ref({});
  });
  // Default username for tests — overridden where it matters.
  mockGetMe.mockResolvedValue({ username: "alice" });
});

/** Flush micro-task queue (watch({ immediate }) fires after the current tick). */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

describe("useCollectionWatch — initial fetch", () => {
  it("isWatching starts false before the probe resolves", () => {
    mockListWatchers.mockResolvedValue([{ username: "alice" }]);
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    // Before async resolves — should be false
    expect(isWatching.value).toBe(false);
  });

  it("sets isWatching=true when the caller's username is in the watcher list", async () => {
    mockListWatchers.mockResolvedValue([
      { username: "bob" },
      { username: "alice" },
    ]);
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(true);
    expect(mockListWatchers).toHaveBeenCalledWith({ collectionAppId: "col-abc" });
  });

  it("sets isWatching=false when the caller is not in the watcher list", async () => {
    mockListWatchers.mockResolvedValue([{ username: "bob" }]);
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(false);
  });

  it("sets isWatching=false when the watcher list is empty", async () => {
    mockListWatchers.mockResolvedValue([]);
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(false);
  });

  it("never calls the /me 404-emitting endpoint", async () => {
    // UI-005 invariant: the composable must not call getMyWatch any more,
    // because that endpoint returns 404 by design and pollutes the console.
    mockListWatchers.mockResolvedValue([{ username: "alice" }]);
    const api = useV2ShepardApi(CollectionWatchesApi).value as Record<string, unknown>;
    expect(api.getMyWatch).toBeUndefined();
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(true);
  });

  it("treats list-endpoint errors as not-watching (e.g. 403 lost Read)", async () => {
    mockListWatchers.mockRejectedValue(new Error("Forbidden"));
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(false);
  });

  it("treats getMe failure as not-watching", async () => {
    mockListWatchers.mockResolvedValue([{ username: "alice" }]);
    mockGetMe.mockRejectedValue(new Error("Unauthorized"));
    const { isWatching } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(false);
  });

  it("skips the probe and stays false when appId is null", async () => {
    const { isWatching } = useCollectionWatch(ref(null));
    await flush();
    expect(isWatching.value).toBe(false);
    expect(mockListWatchers).not.toHaveBeenCalled();
  });

  it("caches the username across consecutive refreshes (one getMe call)", async () => {
    mockListWatchers.mockResolvedValue([{ username: "alice" }]);
    const { isWatching } = useCollectionWatch(ref("col-1"));
    await flush();
    expect(isWatching.value).toBe(true);

    // Mount a second composable for a different collection — getMe must not be
    // called a second time within the same session.
    const { isWatching: w2 } = useCollectionWatch(ref("col-2"));
    await flush();
    expect(w2.value).toBe(true);
    expect(mockGetMe).toHaveBeenCalledTimes(1);
    expect(mockListWatchers).toHaveBeenCalledTimes(2);
  });
});

describe("useCollectionWatch — toggle()", () => {
  it("calls watch() when not currently watching", async () => {
    // Start as not watching (empty list)
    mockListWatchers.mockResolvedValue([]);
    mockWatch.mockResolvedValue({});

    const { isWatching, toggle } = useCollectionWatch(ref("col-abc"));
    await flush(); // settle initial state

    expect(isWatching.value).toBe(false);
    await toggle();

    expect(mockWatch).toHaveBeenCalledWith({ collectionAppId: "col-abc" });
    expect(isWatching.value).toBe(true);
  });

  it("calls unwatch() when currently watching", async () => {
    mockListWatchers.mockResolvedValue([{ username: "alice" }]);
    mockUnwatch.mockResolvedValue(undefined);

    const { isWatching, toggle } = useCollectionWatch(ref("col-abc"));
    await flush();

    expect(isWatching.value).toBe(true);
    await toggle();

    expect(mockUnwatch).toHaveBeenCalledWith({ collectionAppId: "col-abc" });
    expect(isWatching.value).toBe(false);
  });

  it("optimistically updates isWatching before the API call resolves", async () => {
    mockListWatchers.mockResolvedValue([]);
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
    mockListWatchers.mockResolvedValue([]);
    mockWatch.mockRejectedValue(new Error("Server error"));

    const { isWatching, toggle } = useCollectionWatch(ref("col-abc"));
    await flush();
    expect(isWatching.value).toBe(false);

    await toggle();

    expect(isWatching.value).toBe(false); // reverted
  });

  it("reverts optimistic update when unwatch() fails", async () => {
    mockListWatchers.mockResolvedValue([{ username: "alice" }]);
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
    mockListWatchers.mockResolvedValue([]);
    const appId = ref<string | null>("col-1");

    const { isWatching } = useCollectionWatch(appId);
    await flush();
    expect(isWatching.value).toBe(false);

    // Switch to a different collection that the user watches
    mockListWatchers.mockResolvedValue([{ username: "alice" }]);
    appId.value = "col-2";
    await flush();

    expect(isWatching.value).toBe(true);
    expect(mockListWatchers).toHaveBeenCalledTimes(2);
  });

  it("resets to false when appId changes to null", async () => {
    mockListWatchers.mockResolvedValue([{ username: "alice" }]);
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
    mockListWatchers.mockResolvedValue([{ username: "alice" }]);
    const { isWatching } = useCollectionWatch("col-static");
    await flush();
    expect(isWatching.value).toBe(true);
    expect(mockListWatchers).toHaveBeenCalledWith({ collectionAppId: "col-static" });
  });
});
