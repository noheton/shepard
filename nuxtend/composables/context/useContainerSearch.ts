import { type ContainerType, SearchApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

export interface MyContainerSearchResult {
  containerName: string;
  containerId: number;
  containerType: ContainerType;
}

export function useContainerSearch(
  searchString: Ref<string | undefined>,
  onSearchDone?: () => void,
) {
  const isLoading = ref<boolean>(false);
  const containerSearchResults = ref<MyContainerSearchResult[]>([]);
  const searchDone = (callbackFn?: () => void) => {
    isLoading.value = false;
    if (callbackFn) {
      callbackFn();
    }
  };

  async function searchContainersByQuery(query: string) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    let searchStringParam = "";
    if (isIntegerString(query)) {
      const searchId = parseInt(query);
      searchStringParam = createSearchQueryFromId(searchId);
    } else {
      searchStringParam = createSearchQueryFromString(query);
    }

    const searchResponse = await useShepardApi(
      SearchApi,
    ).value.searchContainers({
      containerSearchBody: {
        searchParams: { query: searchStringParam, queryType: "BASIC" },
      },
    });

    if (searchResponse.results) {
      searchResponse.results.forEach(result => {
        if (
          !containerSearchResults.value.some(
            existingResult => existingResult.containerId === result.id,
          )
        ) {
          containerSearchResults.value.push({
            containerId: result.id,
            containerName: result.name,
            containerType: result.type,
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
    containerSearchResults.value = [];
  }

  const startSearch = () => {
    if (!searchString.value) {
      resetResultList();
      return;
    }
    searchContainersByQuery(searchString.value);
  };

  return {
    containerSearchResults,
    startSearch,
    isLoading,
    resetResultList,
  };
}
