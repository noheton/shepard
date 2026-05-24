/**
 * useGlobalSearch — drives the global header-search dropdown (UI-002).
 *
 * Composes the three existing search composables — collections,
 * dataobjects, containers — into a single debounced surface. Each kind
 * runs in parallel and writes into its own result ref so the dropdown
 * can render incrementally as each batch lands.
 *
 * Why a composer (and not a fourth backend call): the existing search
 * composables already encode the wire shape, the id-vs-name dispatch,
 * and the de-duplication logic. Re-implementing any of that here would
 * be the kind of drift the "reuse before reimplement" rule warns about.
 *
 * Owner: UI-002 (`aidocs/agent-findings/ui-002-header-search-fix-2026-05-24.md`).
 */
import {
  useCollectionSearch,
  type MyCollectionSearchResult,
} from "./useCollectionSearch";
import {
  useDataObjectSearch,
  type DataObjectSearchResult,
} from "./useDataObjectSearch";
import {
  useContainerSearch,
  type MyContainerSearchResult,
} from "./useContainerSearch";

export interface GlobalSearchOptions {
  /** Debounce delay in ms (default 300). */
  debounceMs?: number;
  /** Max collections to show (default 5). */
  collectionLimit?: number;
  /** Max dataobjects to show (default 10). */
  dataObjectLimit?: number;
  /** Max containers to show (default 5). */
  containerLimit?: number;
}

export type GlobalSearchKind = "collection" | "dataobject" | "container";

export interface GlobalSearchState {
  /** Bound to the input field. */
  query: Ref<string>;
  /** True while ANY of the three searches is in flight. */
  isLoading: Ref<boolean>;
  /** True once the user has typed something and we've finished at least one query. */
  hasSearched: Ref<boolean>;
  /** Per-kind error message; null when healthy. */
  error: Ref<string | null>;
  /** Result arrays, trimmed to the configured per-kind limits. */
  collections: Ref<MyCollectionSearchResult[]>;
  dataObjects: Ref<DataObjectSearchResult[]>;
  containers: Ref<MyContainerSearchResult[]>;
  /** True when the query is non-empty AND all three result arrays are empty AND we've finished searching. */
  isEmpty: Ref<boolean>;
  /** Reset everything (clear text, clear results). */
  reset: () => void;
  /** Trigger an immediate search, bypassing the debounce (mostly for tests). */
  searchNow: () => void;
}

export function useGlobalSearch(
  opts: GlobalSearchOptions = {},
): GlobalSearchState {
  const debounceMs = opts.debounceMs ?? 300;
  const collectionLimit = opts.collectionLimit ?? 5;
  const dataObjectLimit = opts.dataObjectLimit ?? 10;
  const containerLimit = opts.containerLimit ?? 5;

  const query = ref<string>("");
  const queryRefForChildren = computed(() => query.value);
  const hasSearched = ref<boolean>(false);
  const error = ref<string | null>(null);

  // Children expect a Ref<string>; pass a writable ref kept in sync so
  // their `searchString.value` reads the current query.
  const sharedSearchRef = ref<string>("");

  const {
    collectionSearchResults,
    startSearch: startCollectionSearch,
    isLoading: collectionsLoading,
    resetResultList: resetCollections,
  } = useCollectionSearch(sharedSearchRef);

  const {
    dataObjectSearchResults,
    startSearch: startDataObjectSearch,
    isLoading: dataObjectsLoading,
    resetResultList: resetDataObjects,
  } = useDataObjectSearch(undefined, sharedSearchRef);

  const {
    containerSearchResults,
    startSearch: startContainerSearch,
    isLoading: containersLoading,
    resetResultList: resetContainers,
  } = useContainerSearch(sharedSearchRef);

  const collections = computed(() =>
    collectionSearchResults.value.slice(0, collectionLimit),
  );
  const dataObjects = computed(() =>
    dataObjectSearchResults.value.slice(0, dataObjectLimit),
  );
  const containers = computed(() =>
    containerSearchResults.value.slice(0, containerLimit),
  );

  const isLoading = computed(
    () =>
      collectionsLoading.value ||
      dataObjectsLoading.value ||
      containersLoading.value,
  );

  const isEmpty = computed(
    () =>
      hasSearched.value &&
      !isLoading.value &&
      query.value.trim() !== "" &&
      collections.value.length === 0 &&
      dataObjects.value.length === 0 &&
      containers.value.length === 0,
  );

  function reset() {
    query.value = "";
    sharedSearchRef.value = "";
    hasSearched.value = false;
    error.value = null;
    resetCollections();
    resetDataObjects();
    resetContainers();
  }

  function runSearch() {
    const trimmed = query.value.trim();
    sharedSearchRef.value = trimmed;
    if (trimmed === "") {
      resetCollections();
      resetDataObjects();
      resetContainers();
      hasSearched.value = false;
      error.value = null;
      return;
    }
    // Reset before each new query so stale results from a previous
    // input don't accumulate as the user types.
    resetCollections();
    resetDataObjects();
    resetContainers();
    error.value = null;
    // Fire all three in parallel. Each child returns a Promise (we patched
    // each `startSearch` to do so). We catch per-kind so one failing kind
    // doesn't poison the others, and surface a single user-facing error
    // when any kind failed.
    const handle = (label: string, p: Promise<void>): Promise<void> =>
      p.catch(e => {
        error.value = "Search temporarily unavailable";
        // Preserve full error for devtools / handleError pipeline.
        const h = (globalThis as { handleError?: (e: unknown, ctx: string) => void }).handleError;
        if (typeof h === "function") h(e, `globalSearch:${label}`);
      });

    const ops: Promise<void>[] = [
      handle("collections", startCollectionSearch()),
      handle("dataobjects", startDataObjectSearch()),
      handle("containers", startContainerSearch()),
    ];

    void Promise.allSettled(ops).then(() => {
      hasSearched.value = true;
    });
  }

  let debounceTimer: ReturnType<typeof setTimeout> | null = null;

  function scheduleSearch() {
    if (debounceTimer) clearTimeout(debounceTimer);
    debounceTimer = setTimeout(() => {
      debounceTimer = null;
      runSearch();
    }, debounceMs);
  }

  // Watch the query and (de)bounce.
  watch(queryRefForChildren, () => {
    const trimmed = query.value.trim();
    if (trimmed === "") {
      // Empty input ⇒ clear results immediately, no debounce, no fetch.
      if (debounceTimer) {
        clearTimeout(debounceTimer);
        debounceTimer = null;
      }
      sharedSearchRef.value = "";
      resetCollections();
      resetDataObjects();
      resetContainers();
      hasSearched.value = false;
      error.value = null;
      return;
    }
    scheduleSearch();
  });

  // Clean up the timer if the component using this composable unmounts.
  if (typeof onUnmounted === "function") {
    onUnmounted(() => {
      if (debounceTimer) clearTimeout(debounceTimer);
    });
  }

  return {
    query,
    isLoading,
    hasSearched,
    error,
    collections,
    dataObjects,
    containers,
    isEmpty,
    reset,
    searchNow: runSearch,
  };
}
