import { SearchApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export interface DataObjectSearchResult {
  dataObjectName: string;
  dataObjectId: number;
}

export function useDataObjectSearch(
  collectionId: number,
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
    collectionIdParam: number,
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

    const searchResponse = await useShepardApi(SearchApi).value.search({
      searchBody: {
        searchParams: { query: searchStringParam, queryType: "DataObject" },
        scopes: [{ collectionId: collectionIdParam, traversalRules: [] }],
      },
    });

    if (searchResponse.results) {
      searchResponse.results.forEach(result => {
        if (
          !dataObjectSearchResults.value.some(
            existingResult => existingResult.dataObjectId === result.id,
          )
        ) {
          dataObjectSearchResults.value.push({
            dataObjectId: result.id,
            dataObjectName: result.name,
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

  const startSearch = (collectionIdParam?: number) => {
    if (!searchString.value) {
      resetResultList();
      return;
    }

    // use either start search param, or the initial collectionId, since collection Id might have maybe changed outside
    const currCollectionId = collectionIdParam ?? collectionId;

    searchDataObjectsByQuery(currCollectionId, searchString.value);
  };

  return {
    dataObjectSearchResults,
    startSearch,
    isLoading,
    resetResultList,
  };
}
