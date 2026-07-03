/**
 * PERF6 + V2-SWEEP Wave 4 + LINEAGE-V2 — useFetchAllDataObjects singleton cache (v2-only)
 *
 * Verifies:
 *   1. Same collectionAppId → same ref identity, API called once
 *   2. Different collectionAppIds → separate refs, API called twice
 *   3. Concurrent callers with same collectionAppId → only one in-flight fetch
 *   4. Cache invalidation → refetch on next call
 *   5. Error on first fetch does not leave stale truthy data
 *   6. After TTL expiry the composable refetches
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  useFetchAllDataObjects,
  invalidateDataObjectsCache,
  _resetDataObjectsCacheForTests,
} from "~/composables/context/useFetchAllDataObjects";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

// vi.mock is hoisted by Vitest above the imports at runtime.
// V2-SWEEP Wave 4: the v1 helper must never be touched — a regression
// re-introducing it is observable as a thrown error.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(() => {
    throw new Error("v1 helper must not be used");
  }),
}));
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

const mockListDataObjects = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  _resetDataObjectsCacheForTests();

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

describe("PERF6 — same collectionAppId returns shared ref", () => {
  it("two callers with same collectionAppId receive the same dataObjects ref identity", async () => {
    mockListDataObjects.mockResolvedValue([makeDo(1), makeDo(2)]);

    const a = useFetchAllDataObjects("coll-uuid-42");
    const b = useFetchAllDataObjects("coll-uuid-42");

    await flush();

    // Ref identity must be the same object — not just deeply equal.
    expect(a.dataObjects).toBe(b.dataObjects);
  });

  it("two callers with same collectionAppId fire only one API request", async () => {
    mockListDataObjects.mockResolvedValue([makeDo(1)]);

    useFetchAllDataObjects("coll-uuid-42");
    useFetchAllDataObjects("coll-uuid-42");

    await flush();

    expect(mockListDataObjects).toHaveBeenCalledTimes(1);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — different collectionAppIds are independent", () => {
  it("two callers with different collectionAppIds receive different dataObjects refs", async () => {
    mockListDataObjects.mockResolvedValue([]);

    const a = useFetchAllDataObjects("coll-uuid-1");
    const b = useFetchAllDataObjects("coll-uuid-2");

    await flush();

    expect(a.dataObjects).not.toBe(b.dataObjects);
  });

  it("two callers with different collectionAppIds fire two API requests", async () => {
    mockListDataObjects.mockResolvedValue([]);

    useFetchAllDataObjects("coll-uuid-1");
    useFetchAllDataObjects("coll-uuid-2");

    await flush();

    expect(mockListDataObjects).toHaveBeenCalledTimes(2);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — concurrent callers attach to in-flight fetch", () => {
  it("a second caller arriving before the first fetch resolves does not fire a second request", async () => {
    let resolveFirst!: (v: unknown[]) => void;
    mockListDataObjects.mockReturnValueOnce(
      new Promise(r => { resolveFirst = r; }),
    );

    const a = useFetchAllDataObjects("coll-uuid-10");
    // Second caller arrives while first is still in flight.
    const b = useFetchAllDataObjects("coll-uuid-10");

    // Resolve the first fetch.
    resolveFirst([makeDo(5)]);
    await flush();

    expect(mockListDataObjects).toHaveBeenCalledTimes(1);
    // Both see the populated data.
    expect(a.dataObjects.value).toHaveLength(1);
    expect(b.dataObjects.value).toHaveLength(1);
    // And share the same ref.
    expect(a.dataObjects).toBe(b.dataObjects);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — invalidateDataObjectsCache forces refetch", () => {
  it("invalidating a specific collectionAppId causes the next call to refetch", async () => {
    mockListDataObjects.mockResolvedValue([makeDo(1)]);

    useFetchAllDataObjects("coll-uuid-99");
    await flush();
    expect(mockListDataObjects).toHaveBeenCalledTimes(1);

    // Invalidate and call again.
    invalidateDataObjectsCache("coll-uuid-99");
    useFetchAllDataObjects("coll-uuid-99");
    await flush();

    expect(mockListDataObjects).toHaveBeenCalledTimes(2);
  });

  it("invalidating without argument clears all entries", async () => {
    mockListDataObjects.mockResolvedValue([]);

    useFetchAllDataObjects("coll-uuid-1");
    useFetchAllDataObjects("coll-uuid-2");
    await flush();
    expect(mockListDataObjects).toHaveBeenCalledTimes(2);

    mockListDataObjects.mockClear();
    invalidateDataObjectsCache(); // no argument = full clear
    useFetchAllDataObjects("coll-uuid-1");
    useFetchAllDataObjects("coll-uuid-2");
    await flush();

    expect(mockListDataObjects).toHaveBeenCalledTimes(2);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — error on first load yields empty array (no crash)", () => {
  it("sets dataObjects to [] on first-load error", async () => {
    mockListDataObjects.mockRejectedValue(new Error("Network failure"));

    const { dataObjects, loading } = useFetchAllDataObjects("coll-uuid-77");
    await flush();

    expect(dataObjects.value).toEqual([]);
    expect(loading.value).toBe(false);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — TTL expiry triggers a fresh fetch", () => {
  it("data is NOT re-fetched within the TTL window", async () => {
    vi.useFakeTimers();
    mockListDataObjects.mockResolvedValue([makeDo(1)]);

    useFetchAllDataObjects("coll-uuid-55");
    // Flush with fake timers: run microtasks then let async settle.
    await vi.runAllTimersAsync();
    expect(mockListDataObjects).toHaveBeenCalledTimes(1);

    // Well within TTL — second caller should NOT refetch.
    useFetchAllDataObjects("coll-uuid-55");
    await vi.runAllTimersAsync();
    expect(mockListDataObjects).toHaveBeenCalledTimes(1);
  });

  it("data IS re-fetched after the TTL has expired", async () => {
    vi.useFakeTimers();
    mockListDataObjects.mockResolvedValue([makeDo(1)]);

    useFetchAllDataObjects("coll-uuid-55");
    await vi.runAllTimersAsync();
    expect(mockListDataObjects).toHaveBeenCalledTimes(1);

    // Advance past 5-minute TTL.
    vi.advanceTimersByTime(5 * 60 * 1000 + 1);

    mockListDataObjects.mockResolvedValue([makeDo(2)]);
    useFetchAllDataObjects("coll-uuid-55");
    await vi.runAllTimersAsync();

    expect(mockListDataObjects).toHaveBeenCalledTimes(2);
  });
});

// ──────────────────────────────────────────────────────────────────────────────

describe("PERF6 — v2 path always used (LINEAGE-V2: string key)", () => {
  it("calls listDataObjects (v2) with the supplied collectionAppId string", async () => {
    mockListDataObjects.mockResolvedValue([makeDo(3)]);

    const { dataObjects } = useFetchAllDataObjects("collection-uuid-abc");
    await flush();

    expect(mockListDataObjects).toHaveBeenCalledTimes(1);
    expect(mockListDataObjects).toHaveBeenCalledWith(
      expect.objectContaining({ collectionAppId: "collection-uuid-abc" }),
    );
    expect(dataObjects.value).toHaveLength(1);
  });
});
