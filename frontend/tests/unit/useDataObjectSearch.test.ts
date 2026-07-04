/**
 * SEARCH-V2-3 — unit tests for `useDataObjectSearch`.
 *
 * Proves:
 *   - v2 path: when `collectionAppId` is supplied the composable calls
 *     `GET /v2/search?q=…&collectionAppId=…` and never touches the v1 client
 *   - v1 fallback: when only a numeric `collectionId` is supplied the
 *     composable falls back to `POST /shepard/api/search`
 *   - global v2: when neither scope is supplied the composable calls
 *     `GET /v2/search?q=…` (global search)
 *   - dedup: duplicate `appId` values are not appended to the results list
 *   - empty query: a blank/undefined `searchString` resets results without
 *     calling either API
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";

// ── mocks ──────────────────────────────────────────────────────────────────

const mockSearchV2 = vi.fn();
const mockSearchV1 = vi.fn();

vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: () => ref({ searchV2: mockSearchV2 }),
}));

vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: () => ref({ search: mockSearchV1 }),
}));

vi.mock("~/utils/appId", () => ({
  readDataObjectAppId: (obj: unknown) =>
    (obj as { appId?: string | null }).appId ?? null,
}));

// @dlr-shepard/backend-client used only for type tokens; the actual class is
// never called — the vi.mock above intercepts useV2ShepardApi / useShepardApi.
vi.mock("@dlr-shepard/backend-client", () => ({
  SearchApi: function SearchApi() {},
}));

// ── helpers ────────────────────────────────────────────────────────────────

const flush = () => new Promise<void>(r => setTimeout(r, 0));

const COLL_APP_ID = "019e6ffc-aaaa-7abc-9def-000000000001";

function makeV2SearchResult(items: Array<{ appId: string; name: string; kind?: string; parentCollectionAppId?: string }>) {
  return {
    items: items.map(i => ({
      appId: i.appId,
      name: i.name,
      kind: i.kind ?? "dataobject",
      parentCollectionAppId: i.parentCollectionAppId ?? null,
    })),
    total: items.length,
  };
}

function makeV1SearchResult(results: Array<{ id: number; name: string; appId?: string }>, collectionId = 42) {
  return {
    results: results.map(r => ({ id: r.id, name: r.name, appId: r.appId ?? null })),
    resultSet: results.map(() => ({ collectionId })),
  };
}

// ── setup ──────────────────────────────────────────────────────────────────

beforeEach(() => {
  vi.clearAllMocks();
  Object.assign(globalThis, { ref });
});

// ── tests ──────────────────────────────────────────────────────────────────

describe("useDataObjectSearch — SEARCH-V2-3", () => {
  it("calls GET /v2/search scoped to collectionAppId, not the v1 client", async () => {
    mockSearchV2.mockResolvedValue(
      makeV2SearchResult([{ appId: "do-app-1", name: "TR-004" }]),
    );

    const { useDataObjectSearch } = await import(
      "~/composables/context/useDataObjectSearch"
    );
    const query = ref("TR-004");
    const { dataObjectSearchResults, startSearch } = useDataObjectSearch(
      42,
      query,
      undefined,
      COLL_APP_ID,
    );

    await startSearch(42, COLL_APP_ID);
    await flush();

    expect(mockSearchV2).toHaveBeenCalledTimes(1);
    expect(mockSearchV2).toHaveBeenCalledWith({
      q: "TR-004",
      collectionAppId: COLL_APP_ID,
    });
    expect(mockSearchV1).not.toHaveBeenCalled();

    expect(dataObjectSearchResults.value).toHaveLength(1);
    expect(dataObjectSearchResults.value[0]?.dataObjectAppId).toBe("do-app-1");
    expect(dataObjectSearchResults.value[0]?.dataObjectName).toBe("TR-004");
    expect(dataObjectSearchResults.value[0]?.dataObjectId).toBe(0);
  });

  it("falls back to POST /shepard/api/search when only collectionId is provided", async () => {
    mockSearchV1.mockResolvedValue(
      makeV1SearchResult([{ id: 100, name: "TR-001" }]),
    );

    const { useDataObjectSearch } = await import(
      "~/composables/context/useDataObjectSearch"
    );
    const query = ref("TR");
    const { dataObjectSearchResults, startSearch } = useDataObjectSearch(
      42,
      query,
    );

    await startSearch(42);
    await flush();

    expect(mockSearchV1).toHaveBeenCalledTimes(1);
    expect(mockSearchV2).not.toHaveBeenCalled();

    expect(dataObjectSearchResults.value).toHaveLength(1);
    expect(dataObjectSearchResults.value[0]?.dataObjectId).toBe(100);
    expect(dataObjectSearchResults.value[0]?.dataObjectName).toBe("TR-001");
  });

  it("calls global GET /v2/search when no scope is provided", async () => {
    mockSearchV2.mockResolvedValue(
      makeV2SearchResult([{ appId: "do-app-global", name: "Anything" }]),
    );

    const { useDataObjectSearch } = await import(
      "~/composables/context/useDataObjectSearch"
    );
    const query = ref("anything");
    const { dataObjectSearchResults, startSearch } = useDataObjectSearch(
      undefined,
      query,
    );

    await startSearch();
    await flush();

    expect(mockSearchV2).toHaveBeenCalledTimes(1);
    expect(mockSearchV2).toHaveBeenCalledWith({ q: "anything" });
    expect(mockSearchV1).not.toHaveBeenCalled();

    expect(dataObjectSearchResults.value[0]?.dataObjectAppId).toBe("do-app-global");
  });

  it("deduplicates v2 results by appId", async () => {
    mockSearchV2.mockResolvedValue(
      makeV2SearchResult([
        { appId: "do-app-1", name: "TR-004" },
        { appId: "do-app-1", name: "TR-004 duplicate" },
        { appId: "do-app-2", name: "TR-005" },
      ]),
    );

    const { useDataObjectSearch } = await import(
      "~/composables/context/useDataObjectSearch"
    );
    const query = ref("TR");
    const { dataObjectSearchResults, startSearch } = useDataObjectSearch(
      42,
      query,
      undefined,
      COLL_APP_ID,
    );

    await startSearch(42, COLL_APP_ID);
    await flush();

    expect(dataObjectSearchResults.value).toHaveLength(2);
    const appIds = dataObjectSearchResults.value.map(r => r.dataObjectAppId);
    expect(appIds).toEqual(["do-app-1", "do-app-2"]);
  });

  it("filters out non-dataobject items from v2 results", async () => {
    mockSearchV2.mockResolvedValue({
      items: [
        { appId: "coll-app-1", name: "Some Collection", kind: "collection", parentCollectionAppId: null },
        { appId: "do-app-1", name: "TR-004", kind: "dataobject", parentCollectionAppId: COLL_APP_ID },
      ],
      total: 2,
    });

    const { useDataObjectSearch } = await import(
      "~/composables/context/useDataObjectSearch"
    );
    const query = ref("TR");
    const { dataObjectSearchResults, startSearch } = useDataObjectSearch(
      42,
      query,
      undefined,
      COLL_APP_ID,
    );

    await startSearch(42, COLL_APP_ID);
    await flush();

    expect(dataObjectSearchResults.value).toHaveLength(1);
    expect(dataObjectSearchResults.value[0]?.dataObjectAppId).toBe("do-app-1");
  });

  it("resets results and skips the API when searchString is empty", async () => {
    const { useDataObjectSearch } = await import(
      "~/composables/context/useDataObjectSearch"
    );
    const query = ref<string | undefined>(undefined);
    const { dataObjectSearchResults, startSearch } = useDataObjectSearch(
      42,
      query,
      undefined,
      COLL_APP_ID,
    );

    await startSearch(42, COLL_APP_ID);
    await flush();

    expect(mockSearchV2).not.toHaveBeenCalled();
    expect(mockSearchV1).not.toHaveBeenCalled();
    expect(dataObjectSearchResults.value).toHaveLength(0);
  });

  it("calls the onSearchDone callback after search completes", async () => {
    mockSearchV2.mockResolvedValue(makeV2SearchResult([]));

    const { useDataObjectSearch } = await import(
      "~/composables/context/useDataObjectSearch"
    );
    const query = ref("x");
    const onDone = vi.fn();
    const { startSearch } = useDataObjectSearch(42, query, onDone, COLL_APP_ID);

    await startSearch(42, COLL_APP_ID);
    await flush();

    expect(onDone).toHaveBeenCalledTimes(1);
  });
});
