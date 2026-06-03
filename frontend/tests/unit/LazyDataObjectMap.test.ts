import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { useFetchDataObjectMapByCollection } from "~/composables/context/useFetchDataObjectMap";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

// vi.mock is hoisted by Vitest above the imports at runtime, so this is
// semantically identical to placing it before the imports.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));

const mockGetAllDataObjects = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers();
  (useShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ getAllDataObjects: mockGetAllDataObjects }),
  );
  // Reset module-level cache between tests by re-requiring the module.
  // We achieve isolation by using a fresh collectionId per test.
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useFetchDataObjectMapByCollection — lazy fetch", () => {
  it("starts with an empty map and does NOT fetch on init", () => {
    mockGetAllDataObjects.mockResolvedValue([]);
    const { dataObjectsMap } = useFetchDataObjectMapByCollection(1001);

    expect(dataObjectsMap.value.size).toBe(0);
    // No fetch should have been triggered yet
    expect(mockGetAllDataObjects).not.toHaveBeenCalled();
  });

  it("fetchMap() populates the map after resolution", async () => {
    const data = [
      { id: 10, name: "Alpha" },
      { id: 20, name: "Beta" },
    ];
    mockGetAllDataObjects.mockResolvedValue(data);

    const { dataObjectsMap, fetchMap } = useFetchDataObjectMapByCollection(1002);
    expect(dataObjectsMap.value.size).toBe(0);

    await fetchMap();

    expect(dataObjectsMap.value.size).toBe(2);
    expect(dataObjectsMap.value.get(10)).toBe("Alpha");
    expect(dataObjectsMap.value.get(20)).toBe("Beta");
    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);
  });

  it("fetchMap() is idempotent — two concurrent calls produce only one API request", async () => {
    let resolveFirst!: (v: unknown[]) => void;
    const firstPromise = new Promise<unknown[]>(r => { resolveFirst = r; });
    mockGetAllDataObjects.mockReturnValueOnce(firstPromise);

    const { fetchMap } = useFetchDataObjectMapByCollection(1003);

    const p1 = fetchMap();
    const p2 = fetchMap(); // second call while first is in-flight

    resolveFirst([{ id: 5, name: "Gamma" }]);
    await Promise.all([p1, p2]);

    // Only one actual API call despite two fetchMap() invocations
    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);
  });

  it("TTL: second fetchMap() call within 5 minutes skips the API", async () => {
    mockGetAllDataObjects.mockResolvedValue([{ id: 1, name: "X" }]);

    const { fetchMap } = useFetchDataObjectMapByCollection(1004);
    await fetchMap();
    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);

    // Advance time by 4 minutes (below TTL)
    vi.advanceTimersByTime(4 * 60 * 1000);
    await fetchMap();

    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1); // still only 1
  });

  it("TTL: fetchMap() re-fetches after 5 minutes", async () => {
    mockGetAllDataObjects.mockResolvedValue([{ id: 1, name: "X" }]);

    const { fetchMap } = useFetchDataObjectMapByCollection(1005);
    await fetchMap();
    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);

    // Advance time past the 5-minute TTL
    vi.advanceTimersByTime(5 * 60 * 1000 + 1);

    mockGetAllDataObjects.mockResolvedValue([{ id: 1, name: "X-refreshed" }]);
    const { fetchMap: fetchMap2, dataObjectsMap } = useFetchDataObjectMapByCollection(1005);
    await fetchMap2();

    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(2);
    expect(dataObjectsMap.value.get(1)).toBe("X-refreshed");
  });

  it("same collectionId shares the cached data across two composable calls", async () => {
    // Post-b201fd2ea the composable returns a per-call *stable proxy* ref that
    // mirrors the shared module-level cache entry by value (not by ref
    // identity). So two calls for the same id produce distinct ref objects that
    // both resolve to the same cached contents after a fetch.
    mockGetAllDataObjects.mockResolvedValue([{ id: 42, name: "Shared" }]);
    const { dataObjectsMap: map1, fetchMap: fetchMap1 } =
      useFetchDataObjectMapByCollection(1006);
    const { dataObjectsMap: map2, fetchMap: fetchMap2 } =
      useFetchDataObjectMapByCollection(1006);

    await fetchMap1();
    // The second call hits the fresh TTL cache — no extra API request.
    await fetchMap2();

    expect(mockGetAllDataObjects).toHaveBeenCalledTimes(1);
    expect(map1.value.get(42)).toBe("Shared");
    expect(map2.value.get(42)).toBe("Shared");
  });

  // ── BUG-COLL-APPID-ROUTE-007-PAGE: ref/getter id resolution ──────────────

  it("accepts a ref id and resolves it at fetch time", async () => {
    mockGetAllDataObjects.mockResolvedValue([{ id: 7, name: "Delta" }]);
    const idRef = ref<number | undefined>(1007);
    const { dataObjectsMap, fetchMap } = useFetchDataObjectMapByCollection(idRef);

    await fetchMap();

    expect(mockGetAllDataObjects).toHaveBeenCalledWith({ collectionId: 1007 });
    expect(dataObjectsMap.value.get(7)).toBe("Delta");
  });

  it("accepts a getter id and resolves it at fetch time", async () => {
    mockGetAllDataObjects.mockResolvedValue([{ id: 8, name: "Epsilon" }]);
    let backing: number | undefined = undefined;
    const { fetchMap } = useFetchDataObjectMapByCollection(() => backing);

    // Id not yet resolved — no API call should fire.
    await fetchMap();
    expect(mockGetAllDataObjects).not.toHaveBeenCalled();

    // Id resolves (mirrors the appId-route page where the numeric id arrives
    // only after the v2 collection loads).
    backing = 1008;
    await fetchMap();
    expect(mockGetAllDataObjects).toHaveBeenCalledWith({ collectionId: 1008 });
  });

  it("does NOT fire a v1 call when the id is undefined (UUID-route guard)", async () => {
    mockGetAllDataObjects.mockResolvedValue([]);
    // The appId route param resolves to `undefined` numeric id until the v2
    // collection loads — fetchMap must be a no-op, never a UUID-into-v1 call.
    const { dataObjectsMap, fetchMap } =
      useFetchDataObjectMapByCollection(undefined);

    await fetchMap();

    expect(mockGetAllDataObjects).not.toHaveBeenCalled();
    expect(dataObjectsMap.value.size).toBe(0);
  });
});
