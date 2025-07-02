import { SearchApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export interface MyCollectionSearchResult {
  collectionName: string;
  collectionId: number;
}

export function useCollectionSearch(
  searchString: Ref<string>,
  onSearchDone?: () => void,
) {
  const isLoading = ref<boolean>(false);
  const collectionSearchResults = ref<MyCollectionSearchResult[]>([]);

  const searchDone = (callbackFn?: () => void) => {
    isLoading.value = false;
    if (callbackFn) {
      callbackFn();
    }
  };

  async function searchCollectionsByQuery(query: string) {
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
        searchParams: { query: searchStringParam, queryType: "Collection" },
        scopes: [{ traversalRules: [] }],
      },
    });

    if (searchResponse.results) {
      searchResponse.results.forEach(result => {
        if (
          !collectionSearchResults.value.some(
            existingResult => existingResult.collectionId === result.id,
          )
        ) {
          collectionSearchResults.value.push({
            collectionId: result.id,
            collectionName: result.name,
          });
        }
      });
    }
    searchDone(onSearchDone);
  }

  function createSearchQueryFromId(searchId: number): string {
    const searchStringParam = {
      property: "id",
      operator: "eq",
      value: searchId,
    };
    return JSON.stringify(searchStringParam);
  }

  function createSearchQueryFromString(query: string): string {
    const searchStringParam = {
      property: "name",
      operator: "contains",
      value: query,
    };
    return JSON.stringify(searchStringParam);
  }

  function isIntegerString(value: string): boolean {
    const integerRegex = /^[+-]?\d+$/;
    return integerRegex.test(value);
  }

  function resetResultList() {
    collectionSearchResults.value = [];
  }

  const startSearch = () => {
    const trimmedSearchString = searchString.value.trim();
    if (trimmedSearchString === "") {
      resetResultList();
      return;
    }

    searchCollectionsByQuery(trimmedSearchString);
  };

  return {
    collectionSearchResults,
    startSearch,
    isLoading,
    resetResultList,
  };
}
