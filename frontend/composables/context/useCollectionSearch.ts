import { SearchApi } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";

export interface MyCollectionSearchResult {
  collectionName: string;
  /**
   * @deprecated Numeric Neo4j id — not returned by v2 search; always 0 here.
   * Use `collectionAppId` for navigation and any appId-keyed v2 operation.
   * Remaining v1 callers that genuinely need the numeric id must resolve it
   * at call time from the loaded Collection entity (see CLAUDE.md §"named
   * v1 fallback set").
   */
  collectionId: number;
  /** UUID v7 — the stable cross-substrate identifier; use this for navigation. */
  collectionAppId: string | null;
}

export function useCollectionSearch(
  searchString: Ref<string>,
  onSearchDone?: () => void,
) {
  const isLoading = ref<boolean>(false);
  const collectionSearchResults = ref<MyCollectionSearchResult[]>([]);

  const v2SearchApi = useV2ShepardApi(SearchApi);

  const searchDone = (callbackFn?: () => void) => {
    isLoading.value = false;
    if (callbackFn) {
      callbackFn();
    }
  };

  async function searchCollectionsByQuery(query: string) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    const result = await v2SearchApi.value.searchV2({ q: query });

    result.items
      .filter(item => item.kind === "collection")
      .forEach(item => {
        if (
          !collectionSearchResults.value.some(
            existing => existing.collectionAppId === item.appId,
          )
        ) {
          collectionSearchResults.value.push({
            collectionId: 0, // not exposed by v2 search; use collectionAppId
            collectionName: item.name,
            collectionAppId: item.appId,
          });
        }
      });

    searchDone(onSearchDone);
  }

  function resetResultList() {
    collectionSearchResults.value = [];
  }

  /**
   * Trigger a search. Returns the underlying Promise so callers that
   * compose multiple searches (e.g. `useGlobalSearch`) can observe
   * completion / rejection. Legacy callers that don't await still work.
   */
  const startSearch = (): Promise<void> => {
    const trimmedSearchString = searchString.value.trim();
    if (trimmedSearchString === "") {
      resetResultList();
      return Promise.resolve();
    }

    return searchCollectionsByQuery(trimmedSearchString);
  };

  return {
    collectionSearchResults,
    startSearch,
    isLoading,
    resetResultList,
  };
}
