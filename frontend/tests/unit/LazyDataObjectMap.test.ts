/**
 * V2-SWEEP Wave 3 — `useFetchDataObjectMapByCollection` is v2-only.
 *
 * The id→name map loads from the v2 appId-keyed list
 * `GET /v2/collections/{collectionAppId}/data-objects` (DataObjectV2Api via
 * useV2ShepardApi, paged exhaustive). The collection identifier is the
 * route param string (appId or legacy numeric); the v1
 * `getAllDataObjects({collectionId: Long})` call is gone.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { useFetchDataObjectMapByCollection } from "~/composables/context/useFetchDataObjectMap";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

// vi.mock is hoisted by Vitest above the imports at runtime, so this is
// semantically identical to placing it before the imports.
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));
// A regression re-introducing the v1 helper must be observable.
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(() => {
    throw new Error("v1 helper must not be used");
  }),
}));

const mockListDataObjects = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  vi.useFakeTimers();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ listDataObjects: mockListDataObjects }),
  );
  // Reset module-level cache between tests by re-requiring the module.
  // We achieve isolation by using a fresh collectionId per test.
});

afterEach(() => {
  vi.useRealTimers();
});

const APP_ID = "019e6ffc-aaaa-7bcd-9eef-0000000010";

describe("useFetchDataObjectMapByCollection — lazy fetch (v2)", () => {
  it("starts with an empty map and does NOT fetch on init", () => {
    mockListDataObjects.mockResolvedValue([]);
    const { dataObjectsMap } = useFetchDataObjectMapByCollection(`${APP_ID}01`);

    expect(dataObjectsMap.value.size).toBe(0);
    // No fetch should have been triggered yet
    expect(mockListDataObjects).not.toHaveBeenCalled();
  });

  it("fetchMap() populates the map from the v2 list", async () => {
    const data = [
      { id: 10, appId: `${APP_ID}aa`, name: "Alpha" },
      { id: 20, appId: `${APP_ID}bb`, name: "Beta" },
    ];
    mockListDataObjects.mockResolvedValue(data);

    const { dataObjectsMap, fetchMap } = useFetchDataObjectMapByCollection(
      `${APP_ID}02`,
    );
    expect(dataObjectsMap.value.size).toBe(0);

    await fetchMap();

    expect(mockListDataObjects).toHaveBeenCalledWith(
      expect.objectContaining({ collectionAppId: `${APP_ID}02`, page: 0 }),
    );
    expect(dataObjectsMap.value.size).toBe(2);
    expect(dataObjectsMap.value.get(10)).toBe("Alpha");
    expect(dataObjectsMap.value.get(20)).toBe("Beta");
    expect(mockListDataObjects).toHaveBeenCalledTimes(1);
  });

  it("fetchMap() is idempotent — two concurrent calls produce only one API request", async () => {
    let resolveFirst!: (v: unknown[]) => void;
    const firstPromise = new Promise<unknown[]>(r => { resolveFirst = r; });
    mockListDataObjects.mockReturnValueOnce(firstPromise);

    const { fetchMap } = useFetchDataObjectMapByCollection(`${APP_ID}03`);

    const p1 = fetchMap();
    const p2 = fetchMap(); // second call while first is in-flight

    resolveFirst([{ id: 5, name: "Gamma" }]);
    await Promise.all([p1, p2]);

    // Only one actual API call despite two fetchMap() invocations
    expect(mockListDataObjects).toHaveBeenCalledTimes(1);
  });

  it("TTL: second fetchMap() call within 5 minutes skips the API", async () => {
    mockListDataObjects.mockResolvedValue([{ id: 1, name: "X" }]);

    const { fetchMap } = useFetchDataObjectMapByCollection(`${APP_ID}04`);
    await fetchMap();
    expect(mockListDataObjects).toHaveBeenCalledTimes(1);

    // Advance time by 4 minutes (below TTL)
    vi.advanceTimersByTime(4 * 60 * 1000);
    await fetchMap();

    expect(mockListDataObjects).toHaveBeenCalledTimes(1); // still only 1
  });

  it("TTL: fetchMap() re-fetches after 5 minutes", async () => {
    mockListDataObjects.mockResolvedValue([{ id: 1, name: "X" }]);

    const { fetchMap } = useFetchDataObjectMapByCollection(`${APP_ID}05`);
    await fetchMap();
    expect(mockListDataObjects).toHaveBeenCalledTimes(1);

    // Advance time past the 5-minute TTL
    vi.advanceTimersByTime(5 * 60 * 1000 + 1);

    mockListDataObjects.mockResolvedValue([{ id: 1, name: "X-refreshed" }]);
    const { fetchMap: fetchMap2, dataObjectsMap } =
      useFetchDataObjectMapByCollection(`${APP_ID}05`);
    await fetchMap2();

    expect(mockListDataObjects).toHaveBeenCalledTimes(2);
    expect(dataObjectsMap.value.get(1)).toBe("X-refreshed");
  });

  it("same collection identifier shares the cached data across two composable calls", async () => {
    mockListDataObjects.mockResolvedValue([{ id: 42, name: "Shared" }]);
    const { dataObjectsMap: map1, fetchMap: fetchMap1 } =
      useFetchDataObjectMapByCollection(`${APP_ID}06`);
    const { dataObjectsMap: map2, fetchMap: fetchMap2 } =
      useFetchDataObjectMapByCollection(`${APP_ID}06`);

    await fetchMap1();
    // The second call hits the fresh TTL cache — no extra API request.
    await fetchMap2();

    expect(mockListDataObjects).toHaveBeenCalledTimes(1);
    expect(map1.value.get(42)).toBe("Shared");
    expect(map2.value.get(42)).toBe("Shared");
  });

  it("exhausts pages until a short page arrives", async () => {
    const fullPage = Array.from({ length: 200 }, (_, i) => ({
      id: 1000 + i,
      name: `DO-${i}`,
    }));
    mockListDataObjects
      .mockResolvedValueOnce(fullPage)
      .mockResolvedValueOnce([{ id: 9999, name: "last" }]);

    const { dataObjectsMap, fetchMap } = useFetchDataObjectMapByCollection(
      `${APP_ID}07`,
    );
    await fetchMap();

    expect(mockListDataObjects).toHaveBeenCalledTimes(2);
    expect(mockListDataObjects).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1 }),
    );
    expect(dataObjectsMap.value.size).toBe(201);
  });

  // ── ref/getter id resolution (route param arrives async) ─────────────────

  it("accepts a ref id and resolves it at fetch time", async () => {
    mockListDataObjects.mockResolvedValue([{ id: 7, name: "Delta" }]);
    const idRef = ref<string | undefined>(`${APP_ID}08`);
    const { dataObjectsMap, fetchMap } = useFetchDataObjectMapByCollection(idRef);

    await fetchMap();

    expect(mockListDataObjects).toHaveBeenCalledWith(
      expect.objectContaining({ collectionAppId: `${APP_ID}08` }),
    );
    expect(dataObjectsMap.value.get(7)).toBe("Delta");
  });

  it("accepts a getter id and resolves it at fetch time", async () => {
    mockListDataObjects.mockResolvedValue([{ id: 8, name: "Epsilon" }]);
    let backing: string | undefined = undefined;
    const { fetchMap } = useFetchDataObjectMapByCollection(() => backing);

    // Id not yet resolved — no API call should fire.
    await fetchMap();
    expect(mockListDataObjects).not.toHaveBeenCalled();

    backing = `${APP_ID}09`;
    await fetchMap();
    expect(mockListDataObjects).toHaveBeenCalledWith(
      expect.objectContaining({ collectionAppId: `${APP_ID}09` }),
    );
  });

  it("is a no-op while the identifier is undefined", async () => {
    mockListDataObjects.mockResolvedValue([]);
    const { dataObjectsMap, fetchMap } =
      useFetchDataObjectMapByCollection(undefined);

    await fetchMap();

    expect(mockListDataObjects).not.toHaveBeenCalled();
    expect(dataObjectsMap.value.size).toBe(0);
  });
});
