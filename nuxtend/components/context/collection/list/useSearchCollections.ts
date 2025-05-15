import {
  SearchApi,
  type BasicCollectionAttributes,
  type Collection,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useCollectionListQueryParams } from "./useCollectionListQueryParams";

export function useSearchCollections(itemsPerPage: number) {
  const serverItems = ref<Collection[]>([]);
  const pageCount = ref<number>(0);
  const loading = ref<boolean>(true);
  const searchResultHint = ref<string | undefined>(undefined);

  const { queryParams } = useCollectionListQueryParams();

  const searchApi = useShepardApi(SearchApi);

  /**
   * @param page Page to retrieve. Is 1-based.
   */
  function searchCollections(
    query: string,
    page: number,
    sortByAttributes: SortBy<BasicCollectionAttributes> | undefined,
  ) {
    loading.value = true;

    const orderByParams = sortByAttributes
      ? {
          orderBy: sortByAttributes.key,
          orderDesc: sortByAttributes.order === "desc",
        }
      : null;
    searchApi.value
      .searchCollections({
        collectionSearchBody: { searchParams: { query } },
        page: page - 1,
        size: itemsPerPage,
        orderBy: orderByParams?.orderBy,
        orderDesc: orderByParams?.orderDesc,
      })
      .then(response => {
        serverItems.value = response.results;
        pageCount.value = Math.ceil(response.totalResults / itemsPerPage);
        searchResultHint.value = getSearchResultHint(
          queryParams.value.searchText,
          response.totalResults,
        );
        loading.value = false;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching collections");
      });
  }

  watch(
    queryParams,
    newParams => {
      searchCollections(
        buildQueryString(newParams.searchText ?? null),
        newParams.page ?? 1,
        newParams.sortBy,
      );
    },
    { immediate: true },
  );

  return {
    serverItems,
    pageCount,
    loading,
    searchCollections,
    searchResultHint,
  };
}

function getSearchResultHint(
  searchText: string | undefined,
  resultCount: number,
) {
  return searchText
    ? `Found ${resultCount} results for "${searchText}"`
    : undefined;
}
