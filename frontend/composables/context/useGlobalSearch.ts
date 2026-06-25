/**
 * useGlobalSearch — drives the global header-search dropdown (UI-002).
 *
 * Collections + DataObjects: GET /v2/search via useV2ShepardApi (appId-keyed,
 * no numeric Neo4j ids). Containers: POST /shepard/api/search via
 * useContainerSearch (V1-EXCEPTION — no v2 container search endpoint yet;
 * tracked in aidocs/16 MISSING-V2-APPID-IN-SEARCH).
 *
 * Owner: UI-002 / MISSING-V2-APPID-IN-SEARCH
 */
import { SearchV2Api } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import {
  useContainerSearch,
  type MyContainerSearchResult,
} from "./useContainerSearch";
import type { MyCollectionSearchResult } from "./useCollectionSearch";
import type { DataObjectSearchResult } from "./useDataObjectSearch";

export type { MyCollectionSearchResult } from "./useCollectionSearch";
export type { DataObjectSearchResult } from "./useDataObjectSearch";

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
  /** True while ANY of the searches is in flight. */
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

  const v2SearchApi = useV2ShepardApi(SearchV2Api);

  const query = ref<string>("");
  const queryRefForWatch = computed(() => query.value);
  const hasSearched = ref<boolean>(false);
  const error = ref<string | null>(null);
  const v2IsLoading = ref<boolean>(false);

  // Raw v2 result arrays (before limit slicing)
  const rawCollections = ref<MyCollectionSearchResult[]>([]);
  const rawDataObjects = ref<DataObjectSearchResult[]>([]);

  // Container search stays on v1 — no v2 container search endpoint yet.
  const sharedSearchRef = ref<string>("");
  const {
    containerSearchResults,
    startSearch: startContainerSearch,
    isLoading: containersLoading,
    resetResultList: resetContainers,
  } = useContainerSearch(sharedSearchRef);

  const collections = computed(() =>
    rawCollections.value.slice(0, collectionLimit),
  );
  const dataObjects = computed(() =>
    rawDataObjects.value.slice(0, dataObjectLimit),
  );
  const containers = computed(() =>
    containerSearchResults.value.slice(0, containerLimit),
  );

  const isLoading = computed(
    () => v2IsLoading.value || containersLoading.value,
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

  function resetV2Results() {
    rawCollections.value = [];
    rawDataObjects.value = [];
  }

  function reset() {
    query.value = "";
    sharedSearchRef.value = "";
    hasSearched.value = false;
    error.value = null;
    resetV2Results();
    resetContainers();
  }

  async function fetchV2(trimmed: string): Promise<void> {
    v2IsLoading.value = true;
    try {
      const result = await v2SearchApi.value.globalSearch({ q: trimmed });
      rawCollections.value = result.items
        .filter(item => item.kind === "collection")
        .map(item => ({
          collectionId: 0, // numeric id not exposed by v2; not used for navigation
          collectionName: item.name,
          collectionAppId: item.appId,
        }));
      rawDataObjects.value = result.items
        .filter(item => item.kind === "dataobject")
        .map(item => ({
          dataObjectId: 0, // numeric id not exposed by v2; not used for navigation
          dataObjectName: item.name,
          dataObjectAppId: item.appId,
          collectionId: undefined,
          parentCollectionAppId: item.parentCollectionAppId ?? null,
        }));
    } finally {
      v2IsLoading.value = false;
    }
  }

  function runSearch() {
    const trimmed = query.value.trim();
    sharedSearchRef.value = trimmed;
    if (trimmed === "") {
      resetV2Results();
      resetContainers();
      hasSearched.value = false;
      error.value = null;
      return;
    }
    resetV2Results();
    resetContainers();
    error.value = null;

    const handle = (label: string, p: Promise<void>): Promise<void> =>
      p.catch(e => {
        error.value = "Search temporarily unavailable";
        const h = (globalThis as { handleError?: (e: unknown, ctx: string) => void }).handleError;
        if (typeof h === "function") h(e, `globalSearch:${label}`);
      });

    const ops: Promise<void>[] = [
      handle("v2", fetchV2(trimmed)),
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

  watch(queryRefForWatch, () => {
    const trimmed = query.value.trim();
    if (trimmed === "") {
      if (debounceTimer) {
        clearTimeout(debounceTimer);
        debounceTimer = null;
      }
      sharedSearchRef.value = "";
      resetV2Results();
      resetContainers();
      hasSearched.value = false;
      error.value = null;
      return;
    }
    scheduleSearch();
  });

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
