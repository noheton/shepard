/**
 * SEARCH-V2-2 — unit tests for the migrated `useCollectionSearch` composable.
 *
 * Verifies:
 * - calls GET /v2/search (`searchV2`), not the v1 POST search
 * - filters results to kind === "collection" only
 * - deduplicates by collectionAppId
 * - sets collectionId to 0 (sentinel — numeric id not exposed by v2 search)
 * - empty query resolves immediately without firing a request
 * - `onSearchDone` callback is invoked after each completed search
 */
import { describe, it, expect, vi, beforeEach } from "vitest";
import { ref } from "vue";
import { useCollectionSearch } from "~/composables/context/useCollectionSearch";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));

const mockSearchV2 = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ searchV2: mockSearchV2 }),
  );
});

function makeV2Result(
  items: Array<{
    appId: string;
    name: string;
    kind: "collection" | "dataobject";
    parentCollectionAppId?: string | null;
  }>,
) {
  return { items, total: items.length, page: 0, pageSize: 50, query: "" };
}

describe("useCollectionSearch (SEARCH-V2-2)", () => {
  it("calls searchV2, not any v1 search method", async () => {
    mockSearchV2.mockResolvedValue(makeV2Result([]));
    const q = ref("LUMEN");
    const { startSearch } = useCollectionSearch(q);
    await startSearch();
    expect(mockSearchV2).toHaveBeenCalledTimes(1);
    expect(mockSearchV2).toHaveBeenCalledWith({ q: "LUMEN" });
  });

  it("resolves immediately without firing a request when query is empty", async () => {
    const q = ref("");
    const { startSearch } = useCollectionSearch(q);
    await startSearch();
    expect(mockSearchV2).not.toHaveBeenCalled();
  });

  it("resolves without firing a request when query is only whitespace", async () => {
    const q = ref("   ");
    const { startSearch } = useCollectionSearch(q);
    await startSearch();
    expect(mockSearchV2).not.toHaveBeenCalled();
  });

  it("filters results — only kind=collection items are kept", async () => {
    mockSearchV2.mockResolvedValue(makeV2Result([
      { appId: "019eb019-d49b-7131-b2d2-aaaaaaaaaaaa", name: "LUMEN campaign", kind: "collection" },
      { appId: "019eb019-d6c8-7858-b24d-bbbbbbbbbbbb", name: "TR-004", kind: "dataobject" },
    ]));
    const q = ref("LUMEN");
    const { collectionSearchResults, startSearch } = useCollectionSearch(q);
    await startSearch();
    expect(collectionSearchResults.value).toHaveLength(1);
    expect(collectionSearchResults.value[0]!.collectionName).toBe("LUMEN campaign");
    expect(collectionSearchResults.value[0]!.collectionAppId).toBe("019eb019-d49b-7131-b2d2-aaaaaaaaaaaa");
  });

  it("sets collectionId to 0 (sentinel — v2 search exposes no numeric id)", async () => {
    mockSearchV2.mockResolvedValue(makeV2Result([
      { appId: "019eb019-d49b-7131-b2d2-aaaaaaaaaaaa", name: "LUMEN campaign", kind: "collection" },
    ]));
    const q = ref("LUMEN");
    const { collectionSearchResults, startSearch } = useCollectionSearch(q);
    await startSearch();
    expect(collectionSearchResults.value[0]!.collectionId).toBe(0);
  });

  it("deduplicates results by collectionAppId across multiple calls", async () => {
    const item = { appId: "019eb019-d49b-7131-b2d2-aaaaaaaaaaaa", name: "LUMEN campaign", kind: "collection" as const };
    mockSearchV2.mockResolvedValue(makeV2Result([item]));
    const q = ref("LUMEN");
    const { collectionSearchResults, startSearch, isLoading } = useCollectionSearch(q);
    await startSearch();
    // Simulate a second call returning the same appId.
    isLoading.value = false; // reset guard so second call proceeds
    await startSearch();
    expect(collectionSearchResults.value).toHaveLength(1);
  });

  it("invokes onSearchDone callback after each successful search", async () => {
    mockSearchV2.mockResolvedValue(makeV2Result([]));
    const onDone = vi.fn();
    const q = ref("test");
    const { startSearch } = useCollectionSearch(q, onDone);
    await startSearch();
    expect(onDone).toHaveBeenCalledTimes(1);
  });

  it("resetResultList clears accumulated results", async () => {
    mockSearchV2.mockResolvedValue(makeV2Result([
      { appId: "019eb019-d49b-7131-b2d2-aaaaaaaaaaaa", name: "LUMEN campaign", kind: "collection" },
    ]));
    const q = ref("LUMEN");
    const { collectionSearchResults, startSearch, resetResultList } = useCollectionSearch(q);
    await startSearch();
    expect(collectionSearchResults.value).toHaveLength(1);
    resetResultList();
    expect(collectionSearchResults.value).toHaveLength(0);
  });

  it("trimmed query is sent to searchV2", async () => {
    mockSearchV2.mockResolvedValue(makeV2Result([]));
    const q = ref("  MFFD  ");
    const { startSearch } = useCollectionSearch(q);
    await startSearch();
    expect(mockSearchV2).toHaveBeenCalledWith({ q: "MFFD" });
  });
});
