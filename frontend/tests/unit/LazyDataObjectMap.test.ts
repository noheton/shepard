import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

// Must mock BEFORE importing the module under test so the mock is hoisted.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));

import { useFetchDataObjectMapByCollection } from "~/composables/context/useFetchDataObjectMap";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

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

/** Flush microtasks so promises resolve. */
const flush = () => new Promise<void>(r => setTimeout(r, 0));

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

  it("same collectionId returns same reactive Ref from two composable calls", () => {
    mockGetAllDataObjects.mockResolvedValue([]);
    const { dataObjectsMap: map1 } = useFetchDataObjectMapByCollection(1006);
    const { dataObjectsMap: map2 } = useFetchDataObjectMapByCollection(1006);
    // Both references must point to the same underlying Ref
    expect(map1).toBe(map2);
  });
});
