import { SearchApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export interface DataObjectSearchResult {
  dataObjectName: string;
  dataObjectId: number;
  /**
   * Owning collection — populated from the parallel `resultSet` (ResultTriple)
   * array on the server response. Required for building a navigation route
   * from the global header-search dropdown. Optional because legacy callers
   * may not need it; it is set whenever the backend returns it.
   */
  collectionId?: number;
}

/**
 * DataObject search composable.
 *
 * @param collectionId  When provided, narrows the search to that one
 *                      collection (the original behaviour, used by the
 *                      in-collection DataObject autocompletes). When
 *                      `undefined`, the search runs across every collection
 *                      the current user can read — this is the
 *                      "global header-search" mode (UI-002 fix). The
 *                      backend dispatches on null collectionId per
 *                      `DataObjectSearchService.java:38`.
 */
export function useDataObjectSearch(
  collectionId: number | undefined,
  searchString: Ref<string | undefined>,
  onSearchDone?: () => void,
) {
  const isLoading = ref<boolean>(false);
  const dataObjectSearchResults = ref<DataObjectSearchResult[]>([]);

  const searchDone = (callbackFn?: () => void) => {
    isLoading.value = false;
    if (callbackFn) {
      callbackFn();
    }
  };

  async function searchDataObjectsByQuery(
    collectionIdParam: number | undefined,
    query: string,
  ) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    let searchStringParam = "";
    if (isIntegerString(query)) {
      const searchId = parseInt(query);
      searchStringParam = createSearchQueryFromId(searchId);
    } else {
      searchStringParam = createSearchQueryFromString(query);
    }

    // collectionId omitted ⇒ global search across all visible collections.
    const scope =
      collectionIdParam !== undefined
        ? { collectionId: collectionIdParam, traversalRules: [] }
        : { traversalRules: [] };

    const searchResponse = await useShepardApi(SearchApi).value.search({
      searchBody: {
        searchParams: { query: searchStringParam, queryType: "DataObject" },
        scopes: [scope],
      },
    });

    if (searchResponse.results) {
      // The server returns parallel `results` (BasicEntity[]) +
      // `resultSet` (ResultTriple[]) arrays — same length, index-aligned.
      // We zip them so each row carries its owning collectionId.
      const triples = searchResponse.resultSet ?? [];
      searchResponse.results.forEach((result, idx) => {
        if (
          !dataObjectSearchResults.value.some(
            existingResult => existingResult.dataObjectId === result.id,
          )
        ) {
          dataObjectSearchResults.value.push({
            dataObjectId: result.id,
            dataObjectName: result.name,
            collectionId: triples[idx]?.collectionId,
          });
        }
      });
    }
    searchDone(onSearchDone);
  }

  function createSearchQueryFromString(query: string): string {
    const searchStringParam = {
      property: "name",
      operator: "contains",
      value: query,
    };
    return JSON.stringify(searchStringParam);
  }

  function createSearchQueryFromId(searchId: number): string {
    const searchStringParam = {
      property: "id",
      operator: "eq",
      value: searchId,
    };
    return JSON.stringify(searchStringParam);
  }

  function isIntegerString(value: string): boolean {
    const integerRegex = /^[+-]?\d+$/;
    return integerRegex.test(value);
  }

  function resetResultList() {
    dataObjectSearchResults.value = [];
  }

  /**
   * Trigger a search. Returns the underlying Promise so callers that
   * compose multiple searches (e.g. `useGlobalSearch`) can observe
   * completion / rejection. Legacy callers that don't await still work.
   */
  const startSearch = (collectionIdParam?: number): Promise<void> => {
    if (!searchString.value) {
      resetResultList();
      return Promise.resolve();
    }

    // Use the explicit override if given, otherwise the closure value.
    // Either side may be undefined ⇒ global search.
    const currCollectionId = collectionIdParam ?? collectionId;

    return searchDataObjectsByQuery(currCollectionId, searchString.value);
  };

  return {
    dataObjectSearchResults,
    startSearch,
    isLoading,
    resetResultList,
  };
}
