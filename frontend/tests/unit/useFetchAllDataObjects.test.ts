/**
 * PERF6 — useFetchAllDataObjects singleton cache
 *
 * Verifies:
 *   1. Same collectionId → same ref identity, API called once
 *   2. Different collectionIds → separate refs, API called twice
 *   3. Concurrent callers with same collectionId → only one in-flight fetch
 *   4. Cache invalidation → refetch on next call
 *   5. Error on first fetch does not leave stale truthy data
 *   6. After TTL expiry the composable refetches
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// Mock BEFORE importing the module under test so the mock is hoisted.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

import {
  useFetchAllDataObjects,
  invalidateDataObjectsCache,
  _resetDataObjectsCacheForTests,
} from "~/composables/context/useFetchAllDataObjects";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

const mockGetAllDataObjects = vi.fn();
const mockListDataObjects = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  _resetDataObjectsCacheForTests();

  (useShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ getAllDataObjects: mockGetAllDataObjects }),
  );
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ listDataObjects: mockListDataObjects }),
  );
});

afterEach(() => {
  vi.useRealTimers();
});

/** Flush microtask + timer queues so auto-triggered fetches settle. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

function makeDo(id: number) {
  return { id, name: `Dataset #${id}`, status: "DRAFT" };
}

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — same collectionId returns shared ref", () => {
  it("two callers with same collectionId receive the same dataObjects ref identity", async () => {
    mockGetAllDataObjects.mockResolvedValue([makeDo(1), makeDo(2)]);

    const a = useFetchAllDataObjects(42);
    const b = useFetchAllDataObjects(42);

    await flush();

    // Ref identity must be the same object — not just deeply equal.
    expect(a.dataObjects).toBe(b.dataObjects);
  });

  it("two callers with same collectionId fire only one API request", async () => {
    mockGetAllDataObjects.mockResolvedValue([makeDo(1)]);

    useFetchAllDataObjects(42);
    useFetchAllDataObjects(42);

    await flush();

    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — different collectionIds are independent", () => {
  it("two callers with different collectionIds receive different dataObjects refs", async () => {
    mockGetAllDataObjects.mockResolvedValue([]);

    const a = useFetchAllDataObjects(1);
    const b = useFetchAllDataObjects(2);

    await flush();

    expect(a.dataObjects).not.toBe(b.dataObjects);
  });

  it("two callers with different collectionIds fire two API requests", async () => {
    mockGetAllDataObjects.mockResolvedValue([]);

    useFetchAllDataObjects(1);
    useFetchAllDataObjects(2);

    await flush();

    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(2);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — concurrent callers attach to in-flight fetch", () => {
  it("a second caller arriving before the first fetch resolves does not fire a second request", async () => {
    let resolveFirst!: (v: unknown[]) => void;
    mockGetAllDataObjects.mockReturnValueOnce(
      new Promise(r => { resolveFirst = r; }),
    );

    const a = useFetchAllDataObjects(10);
    // Second caller arrives while first is still in flight.
    const b = useFetchAllDataObjects(10);

    // Resolve the first fetch.
    resolveFirst([makeDo(5)]);
    await flush();

    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);
    // Both see the populated data.
    expect(a.dataObjects.value).toHaveLength(1);
    expect(b.dataObjects.value).toHaveLength(1);
    // And share the same ref.
    expect(a.dataObjects).toBe(b.dataObjects);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — invalidateDataObjectsCache forces refetch", () => {
  it("invalidating a specific collectionId causes the next call to refetch", async () => {
    mockGetAllDataObjects.mockResolvedValue([makeDo(1)]);

    useFetchAllDataObjects(99);
    await flush();
    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);

    // Invalidate and call again.
    invalidateDataObjectsCache(99);
    useFetchAllDataObjects(99);
    await flush();

    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(2);
  });

  it("invalidating without argument clears all entries", async () => {
    mockGetAllDataObjects.mockResolvedValue([]);

    useFetchAllDataObjects(1);
    useFetchAllDataObjects(2);
    await flush();
    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(2);

    mockGetAllDataObjects.mockClear();
    invalidateDataObjectsCache(); // no argument = full clear
    useFetchAllDataObjects(1);
    useFetchAllDataObjects(2);
    await flush();

    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(2);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — error on first load yields empty array (no crash)", () => {
  it("sets dataObjects to [] on first-load error", async () => {
    mockGetAllDataObjects.mockRejectedValue(new Error("Network failure"));

    const { dataObjects, loading } = useFetchAllDataObjects(77);
    await flush();

    expect(dataObjects.value).toEqual([]);
    expect(loading.value).toBe(false);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — TTL expiry triggers a fresh fetch", () => {
  it("data is NOT re-fetched within the TTL window", async () => {
    vi.useFakeTimers();
    mockGetAllDataObjects.mockResolvedValue([makeDo(1)]);

    useFetchAllDataObjects(55);
    // Flush with fake timers: run microtasks then let async settle.
    await vi.runAllTimersAsync();
    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);

    // Well within TTL — second caller should NOT refetch.
    useFetchAllDataObjects(55);
    await vi.runAllTimersAsync();
    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);
  });

  it("data IS re-fetched after the TTL has expired", async () => {
    vi.useFakeTimers();
    mockGetAllDataObjects.mockResolvedValue([makeDo(1)]);

    useFetchAllDataObjects(55);
    await vi.runAllTimersAsync();
    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);

    // Advance past 5-minute TTL.
    vi.advanceTimersByTime(5 * 60 * 1000 + 1);

    mockGetAllDataObjects.mockResolvedValue([makeDo(2)]);
    useFetchAllDataObjects(55);
    await vi.runAllTimersAsync();

    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(2);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — v2 path used when collectionAppId is provided", () => {
  it("calls listDataObjects (v2) when appId ref resolves to a non-null string", async () => {
    mockListDataObjects.mockResolvedValue([makeDo(3)]);
    const appIdRef = ref<string | null>("collection-uuid-abc");

    const { dataObjects } = useFetchAllDataObjects(30, appIdRef);
    await flush();

    expect(mockListDataObjects).toHaveBeenCalledTimes(1);
    expect(mockListDataObjects).toHaveBeenCalledWith(
      expect.objectContaining({ collectionAppId: "collection-uuid-abc" }),
    );
    expect(dataObjects.value).toHaveLength(1);
  });
});
