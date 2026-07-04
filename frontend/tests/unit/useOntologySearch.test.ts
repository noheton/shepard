/**
 * L4 — Vitest coverage for the useOntologySearch composable.
 *
 * Exercises the search-as-you-type debounce, the min-length floor, the
 * race-guard (stale request never clobbers a fresher one), and the derived
 * tree/graph/total computeds. `useTermSearch` is mocked at the module boundary
 * so no network is touched.
 */
import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import {
  useOntologySearch,
  MIN_QUERY_LENGTH,
  SEARCH_DEBOUNCE_MS,
} from "~/composables/semantic/useOntologySearch";

// `vi.hoisted` lets the mock factory (which is hoisted above the imports)
// reference a shared spy without an ESLint import/first violation.
const { searchMock } = vi.hoisted(() => ({ searchMock: vi.fn() }));
vi.mock("~/composables/context/useTermSearch", () => ({
  useTermSearch: () => ({ search: searchMock }),
}));

// Flush pending microtasks (promise continuations) under fake timers — a
// setTimeout-based flush would never resolve while timers are faked.
const flush = async () => {
  await Promise.resolve();
  await Promise.resolve();
};

beforeEach(() => {
  vi.useFakeTimers();
  searchMock.mockReset();
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useOntologySearch", () => {
  it("does not search for queries shorter than the min length", async () => {
    const { query, results, searched } = useOntologySearch();
    query.value = "a"; // 1 char
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 10);
    expect(searchMock).not.toHaveBeenCalled();
    expect(results.value).toEqual([]);
    expect(searched.value).toBe(false);
    expect(MIN_QUERY_LENGTH).toBe(2);
  });

  it("debounces — a rapid sequence of keystrokes fires one request", async () => {
    searchMock.mockResolvedValue([
      { uri: "http://purl.org/dc/terms/creator", label: "Creator" },
    ]);
    const { query } = useOntologySearch();
    query.value = "cr";
    query.value = "cre";
    query.value = "crea";
    // Only after the debounce window elapses does the request fire — once.
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 10);
    await flush();
    expect(searchMock).toHaveBeenCalledTimes(1);
    expect(searchMock).toHaveBeenCalledWith("crea", 50);
  });

  it("populates results, tree, graph and total on success", async () => {
    searchMock.mockResolvedValue([
      { uri: "http://purl.org/dc/terms/creator", label: "Creator" },
      { uri: "http://purl.org/dc/terms/title", label: "Title" },
    ]);
    const { query, results, tree, graph, total, searched, loading } = useOntologySearch();
    query.value = "title";
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 10);
    await flush();

    expect(results.value).toHaveLength(2);
    expect(searched.value).toBe(true);
    expect(loading.value).toBe(false);
    expect(total.value).toBe(2);
    expect(tree.value).toHaveLength(1); // single namespace
    expect(graph.value.nodes).toHaveLength(3); // 1 ns + 2 terms
  });

  it("surfaces errors and clears results", async () => {
    searchMock.mockRejectedValue(new Error("boom"));
    const { query, error, results } = useOntologySearch();
    query.value = "anything";
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 10);
    await flush();
    expect(error.value).toBe("boom");
    expect(results.value).toEqual([]);
  });

  it("clears results immediately when the query shrinks below min length", async () => {
    searchMock.mockResolvedValue([
      { uri: "http://purl.org/dc/terms/creator", label: "Creator" },
    ]);
    const { query, results, searched } = useOntologySearch();
    query.value = "creator";
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 10);
    await flush();
    expect(results.value).toHaveLength(1);

    query.value = "c"; // back below floor
    await flush(); // let the watcher run (Vue flushes watchers on microtask)
    expect(results.value).toEqual([]);
    expect(searched.value).toBe(false);
  });

  it("race-guards: a stale slow response does not clobber the newer one", async () => {
    // First call resolves slowly, second call resolves fast with newer data.
    let resolveSlow!: (v: unknown) => void;
    const slow = new Promise((res) => {
      resolveSlow = res;
    });
    searchMock
      .mockReturnValueOnce(slow)
      .mockResolvedValueOnce([
        { uri: "http://purl.org/dc/terms/title", label: "Title" },
      ]);

    const { query, results } = useOntologySearch();

    query.value = "cr"; // schedules run #1
    await flush(); // let the watcher set its debounce timer
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 10); // fires run #1 (slow)
    query.value = "title"; // schedules run #2
    await flush();
    await vi.advanceTimersByTimeAsync(SEARCH_DEBOUNCE_MS + 10); // fires run #2 (fast)
    await flush();

    // run #2 has resolved with Title.
    expect(results.value.map((r) => r.label)).toEqual(["Title"]);

    // Now the stale run #1 finally resolves — it must be ignored.
    resolveSlow([{ uri: "http://purl.org/dc/terms/creator", label: "Creator" }]);
    await flush();
    expect(results.value.map((r) => r.label)).toEqual(["Title"]);
  });
});
