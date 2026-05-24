/**
 * Tests for `useGlobalSearch` — the composer behind the header-search
 * dropdown (UI-002).
 *
 * The composer wires three existing search composables (collections /
 * dataobjects / containers). We mock `useShepardApi` at one level
 * and assert composed behaviour: debounce, error capture, empty-state,
 * limits, and that an empty query never fires a request.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";

vi.mock("~/composables/common/api/useShepardApi", () => ({
  useShepardApi: vi.fn(),
}));

import { useGlobalSearch } from "~/composables/context/useGlobalSearch";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const mockSearch = vi.fn();
const mockSearchContainers = vi.fn();

beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  mockSearch.mockReset();
  mockSearchContainers.mockReset();
  (useShepardApi as ReturnType<typeof vi.fn>).mockReturnValue(
    ref({ search: mockSearch, searchContainers: mockSearchContainers }),
  );
});

afterEach(() => {
  vi.useRealTimers();
});

/** Advance the debounce + flush microtasks for any pending Promises. */
async function tickAndFlush(ms: number) {
  // Advance, then yield repeatedly so chains of awaited promises can settle.
  await vi.advanceTimersByTimeAsync(ms);
  // Plus a few microtask flushes for good measure (Promise.allSettled chains
  // sit one tick deeper than the resolved promises themselves).
  for (let i = 0; i < 5; i++) {
    await Promise.resolve();
  }
}

describe("useGlobalSearch", () => {
  it("does not fire any request when the query is empty", async () => {
    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "";
    await tickAndFlush(200);
    expect(mockSearch).not.toHaveBeenCalled();
    expect(mockSearchContainers).not.toHaveBeenCalled();
    expect(g.collections.value).toEqual([]);
    expect(g.dataObjects.value).toEqual([]);
    expect(g.containers.value).toEqual([]);
  });

  it("does not fire when the query is only whitespace", async () => {
    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "   ";
    await tickAndFlush(200);
    expect(mockSearch).not.toHaveBeenCalled();
    expect(mockSearchContainers).not.toHaveBeenCalled();
  });

  it("debounces — typing many chars within the window fires only once per kind", async () => {
    mockSearch.mockResolvedValue({ results: [], resultSet: [] });
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
    expect(mockSearch).not.toHaveBeenCalled();
    // Now wait past the debounce window.
    await tickAndFlush(350);
    // search() is called twice (once for Collection, once for DataObject);
    // searchContainers() is called once.
    expect(mockSearch).toHaveBeenCalledTimes(2);
    expect(mockSearchContainers).toHaveBeenCalledTimes(1);
  });

  it("populates collections + dataobjects + containers from a single query", async () => {
    mockSearch.mockImplementation((req: { searchBody: { searchParams: { queryType: string } } }) => {
      const t = req.searchBody.searchParams.queryType;
      if (t === "Collection") {
        return Promise.resolve({
          results: [{ id: 42, name: "LUMEN campaign" }],
          resultSet: [{ collectionId: 42 }],
        });
      }
      // DataObject
      return Promise.resolve({
        results: [{ id: 999, name: "TR-004" }],
        resultSet: [{ collectionId: 42, dataObjectId: 999 }],
      });
    });
    mockSearchContainers.mockResolvedValue({
      results: [{ id: 7, name: "lumen-ts", type: "TIMESERIES" }],
    });

    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "lumen";
    await tickAndFlush(150);

    expect(g.collections.value).toEqual([{ collectionId: 42, collectionName: "LUMEN campaign" }]);
    expect(g.dataObjects.value).toEqual([
      { dataObjectId: 999, dataObjectName: "TR-004", collectionId: 42 },
    ]);
    expect(g.containers.value).toEqual([
      { containerId: 7, containerName: "lumen-ts", containerType: "TIMESERIES" },
    ]);
    expect(g.error.value).toBeNull();
    expect(g.isEmpty.value).toBe(false);
  });

  it("DataObject search runs WITHOUT collectionId in scope (global mode)", async () => {
    mockSearch.mockResolvedValue({ results: [], resultSet: [] });
    mockSearchContainers.mockResolvedValue({ results: [] });

    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "anything";
    await tickAndFlush(150);

    const doCalls = mockSearch.mock.calls.filter(
      (call: unknown[]) => {
        const req = call[0] as { searchBody: { searchParams: { queryType: string } } };
        return req.searchBody.searchParams.queryType === "DataObject";
      },
    );
    expect(doCalls.length).toBe(1);
    const firstCall = doCalls[0];
    expect(firstCall).toBeDefined();
    const reqArg = firstCall![0] as { searchBody: { scopes: Array<{ collectionId?: number }> } };
    const scope = reqArg.searchBody.scopes[0];
    expect(scope).toBeDefined();
    expect(scope!.collectionId).toBeUndefined();
  });

  it("isEmpty is true when query is non-empty and all kinds return zero results", async () => {
    mockSearch.mockResolvedValue({ results: [], resultSet: [] });
    mockSearchContainers.mockResolvedValue({ results: [] });

    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "homarr"; // matches nothing
    await tickAndFlush(200);

    expect(g.isEmpty.value).toBe(true);
    expect(g.error.value).toBeNull();
  });

  it("captures error and sets a user-facing message when a kind errors", async () => {
    mockSearch.mockRejectedValue(new Error("boom"));
    mockSearchContainers.mockResolvedValue({ results: [] });

    const g = useGlobalSearch({ debounceMs: 100 });
    g.query.value = "anything";
    await tickAndFlush(200);

    expect(g.error.value).toBe("Search temporarily unavailable");
  });

  it("respects per-kind limits", async () => {
    const many = Array.from({ length: 20 }, (_, i) => ({ id: i + 1, name: `r${i}` }));
    const triples = many.map(r => ({ collectionId: r.id }));
    mockSearch.mockImplementation((req: { searchBody: { searchParams: { queryType: string } } }) => {
      const t = req.searchBody.searchParams.queryType;
      if (t === "Collection") {
        return Promise.resolve({ results: many, resultSet: triples });
      }
      return Promise.resolve({
        results: many.map(r => ({ id: r.id + 1000, name: r.name })),
        resultSet: many.map(r => ({ collectionId: 1, dataObjectId: r.id + 1000 })),
      });
    });
    mockSearchContainers.mockResolvedValue({
      results: many.map(r => ({ id: r.id, name: r.name, type: "FILE" })),
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
    mockSearch.mockResolvedValue({
      results: [{ id: 1, name: "X" }],
      resultSet: [{ collectionId: 1 }],
    });
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
    mockSearch.mockResolvedValue({
      results: [{ id: 1, name: "X" }],
      resultSet: [{ collectionId: 1 }],
    });
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
