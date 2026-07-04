/**
 * Tests for `useGlobalSearch` — the composer behind the header-search
 * dropdown (UI-002).
 *
 * Collections + DataObjects are now fetched via GET /v2/search
 * (useV2ShepardApi + SearchV2Api). Containers remain on v1 (V1-EXCEPTION).
 * We mock both API helpers and assert composed behaviour: debounce, error
 * capture, empty-state, limits, and that an empty query never fires a request.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { useGlobalSearch } from "~/composables/context/useGlobalSearch";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

// vi.mock is hoisted by Vitest above the imports at runtime.
vi.mock("~/composables/common/api/useV2ShepardApi", () => ({
  useV2ShepardApi: vi.fn(),
}));
vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));

const mockGlobalSearch = vi.fn();
const mockSearchContainers = vi.fn();

beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  mockGlobalSearch.mockReset();
  mockSearchContainers.mockReset();
  // v2 API — used for collections + dataobjects
  (useV2ShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ searchV2: mockGlobalSearch }),
  );
  // v1 API — used for containers only (via useContainerSearch)
  (useShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ searchContainers: mockSearchContainers }),
  );
});

afterEach(() => {
  vi.useRealTimers();
});

/** Advance the debounce + flush microtasks for any pending Promises. */
async function tickAndFlush(ms: number) {
  await vi.advanceTimersByTimeAsync(ms);
  for (let i = 0; i < 5; i++) {
    await Promise.resolve();
  }
}

/** Build a v2 search result envelope. */
function makeV2Result(items: Array<{ appId: string; name: string; kind: "collection" | "dataobject"; parentCollectionAppId?: string | null }>) {
  return { items, total: items.length, page: 0, pageSize: 50, query: "" };
}

describe("useGlobalSearch", () => {
  it("does not fire any request when the query is empty", async () => {
    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "";
    await tickAndFlush(200);
    expect(mockGlobalSearch).not.toHaveBeenCalled();
    expect(mockSearchContainers).not.toHaveBeenCalled();
    expect(g.collections.value).toEqual([]);
    expect(g.dataObjects.value).toEqual([]);
    expect(g.containers.value).toEqual([]);
  });

  it("does not fire when the query is only whitespace", async () => {
    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "   ";
    await tickAndFlush(200);
    expect(mockGlobalSearch).not.toHaveBeenCalled();
    expect(mockSearchContainers).not.toHaveBeenCalled();
  });

  it("debounces — typing many chars within the window fires only once", async () => {
    mockGlobalSearch.mockResolvedValue(makeV2Result([]));
    mockSearchContainers.mockResolvedValue({ results: [] });

    const g = useGlobalSearch({ debounceMs: 300 });
    g.query.value = "T";
    await tickAndFlush(50);
    g.query.value = "TR";
    await tickAndFlush(50);
    g.query.value = "TR-";
    await tickAndFlush(50);
    g.query.value = "TR-0";
    await tickAndFlush(50);
    g.query.value = "TR-00";
    await tickAndFlush(50);
    // Still under 300ms total since the LAST keystroke ⇒ no fire yet.
    expect(mockGlobalSearch).not.toHaveBeenCalled();
    // Now wait past the debounce window.
    await tickAndFlush(350);
    // v2 search fires once (both collections + dataobjects come from one call).
    expect(mockGlobalSearch).toHaveBeenCalledTimes(1);
    // Container search fires once (v1, separate call).
    expect(mockSearchContainers).toHaveBeenCalledTimes(1);
  });

  it("populates collections + dataobjects + containers from a single query", async () => {
    mockGlobalSearch.mockResolvedValue(makeV2Result([
      { appId: "019eb019-d49b-7131-b2d2-3f3107d36a4f", name: "LUMEN campaign", kind: "collection" },
      {
        appId: "019eb019-d6c8-7858-b24d-b3b15de81d97",
        name: "TR-004",
        kind: "dataobject",
        parentCollectionAppId: "019eb019-d49b-7131-b2d2-3f3107d36a4f",
      },
    ]));
    mockSearchContainers.mockResolvedValue({
      results: [{ id: 7, name: "lumen-ts", type: "TIMESERIES" }],
    });

    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "lumen";
    await tickAndFlush(150);

    expect(g.collections.value).toEqual([
      {
        collectionId: 0,
        collectionName: "LUMEN campaign",
        collectionAppId: "019eb019-d49b-7131-b2d2-3f3107d36a4f",
      },
    ]);
    expect(g.dataObjects.value).toEqual([
      {
        dataObjectId: 0,
        dataObjectName: "TR-004",
        dataObjectAppId: "019eb019-d6c8-7858-b24d-b3b15de81d97",
        collectionId: undefined,
        parentCollectionAppId: "019eb019-d49b-7131-b2d2-3f3107d36a4f",
      },
    ]);
    expect(g.containers.value).toEqual([
      { containerId: 7, containerName: "lumen-ts", containerType: "TIMESERIES" },
    ]);
    expect(g.error.value).toBeNull();
    expect(g.isEmpty.value).toBe(false);
  });

  it("v2 search is called with the trimmed query string", async () => {
    mockGlobalSearch.mockResolvedValue(makeV2Result([]));
    mockSearchContainers.mockResolvedValue({ results: [] });

    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "anything";
    await tickAndFlush(150);

    expect(mockGlobalSearch).toHaveBeenCalledTimes(1);
    expect(mockGlobalSearch).toHaveBeenCalledWith({ q: "anything" });
  });

  it("isEmpty is true when query is non-empty and all kinds return zero results", async () => {
    mockGlobalSearch.mockResolvedValue(makeV2Result([]));
    mockSearchContainers.mockResolvedValue({ results: [] });

    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "homarr"; // matches nothing
    await tickAndFlush(200);

    expect(g.isEmpty.value).toBe(true);
    expect(g.error.value).toBeNull();
  });

  it("captures error and sets a user-facing message when v2 search errors", async () => {
    mockGlobalSearch.mockRejectedValue(new Error("boom"));
    mockSearchContainers.mockResolvedValue({ results: [] });

    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "anything";
    await tickAndFlush(200);

    expect(g.error.value).toBe("Search temporarily unavailable");
  });

  it("respects per-kind limits", async () => {
    const manyCollections = Array.from({ length: 20 }, (_, i) => ({
      appId: `019eb019-0000-7000-a000-${String(i).padStart(12, "0")}`,
      name: `coll-${i}`,
      kind: "collection" as const,
    }));
    const manyDos = Array.from({ length: 20 }, (_, i) => ({
      appId: `019eb019-1000-7000-a000-${String(i).padStart(12, "0")}`,
      name: `do-${i}`,
      kind: "dataobject" as const,
      parentCollectionAppId: "019eb019-d49b-7131-b2d2-3f3107d36a4f",
    }));
    mockGlobalSearch.mockResolvedValue(makeV2Result([...manyCollections, ...manyDos]));
    mockSearchContainers.mockResolvedValue({
      results: Array.from({ length: 20 }, (_, i) => ({
        id: i + 1,
        name: `ct-${i}`,
        type: "FILE",
      })),
    });

    const g = useGlobalSearch({
      debounceMs: 50,
      collectionLimit: 3,
      dataObjectLimit: 7,
      containerLimit: 2,
    });
    g.query.value = "many";
    await tickAndFlush(100);

    expect(g.collections.value.length).toBe(3);
    expect(g.dataObjects.value.length).toBe(7);
    expect(g.containers.value.length).toBe(2);
  });

  it("reset() clears query + results", async () => {
    mockGlobalSearch.mockResolvedValue(makeV2Result([
      { appId: "019eb019-d49b-7131-b2d2-3f3107d36a4f", name: "X", kind: "collection" },
    ]));
    mockSearchContainers.mockResolvedValue({ results: [] });

    const g = useGlobalSearch({ debounceMs: 50 });
    g.query.value = "X";
    await tickAndFlush(100);
    expect(g.collections.value.length).toBeGreaterThan(0);

    g.reset();
    expect(g.query.value).toBe("");
    expect(g.collections.value).toEqual([]);
    expect(g.dataObjects.value).toEqual([]);
    expect(g.containers.value).toEqual([]);
    expect(g.hasSearched.value).toBe(false);
  });

  it("clearing the query mid-search wipes results immediately", async () => {
    mockGlobalSearch.mockResolvedValue(makeV2Result([
      { appId: "019eb019-d49b-7131-b2d2-3f3107d36a4f", name: "X", kind: "collection" },
    ]));
    mockSearchContainers.mockResolvedValue({ results: [] });

    const g = useGlobalSearch({ debounceMs: 50 });
    g.query.value = "X";
    await tickAndFlush(100);
    expect(g.collections.value.length).toBeGreaterThan(0);

    g.query.value = "";
    // No debounce delay needed — empty query clears synchronously.
    await Promise.resolve();
    expect(g.collections.value).toEqual([]);
    expect(g.hasSearched.value).toBe(false);
  });
});
