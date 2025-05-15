import type {
  BasicContainer,
  BasicContainerAttributes,
  ResponseError,
} from "@dlr-shepard/backend-client";
import { ContainerType, SearchApi } from "@dlr-shepard/backend-client";
import type { ContainerFilterType } from "~/components/container/containerTypeFilter";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useContainerListQueryParams } from "./useContainerListQueryParams";

export function useSearchContainers(itemsPerPage: number) {
  const serverItems = ref<BasicContainer[]>([]);
  const pageCount = ref<number>(0);
  const loading = ref<boolean>(true);
  const searchResultHint = ref<string | undefined>(undefined);

  const { queryParams } = useContainerListQueryParams();

  function searchContainers(
    page: number,
    sortByAttributes: SortBy<BasicContainerAttributes> | undefined,
    searchQuery: string,
    selectedFilter: ContainerFilterType | null | undefined,
  ) {
    loading.value = true;

    const orderByParams = sortByAttributes
      ? {
          orderBy: sortByAttributes.key,
          orderDesc: sortByAttributes.order === "desc",
        }
      : null;
    useShepardApi(SearchApi)
      .value.searchContainers({
        containerSearchBody: {
          searchParams: {
            query: searchQuery,
            queryType: selectedFilter ?? ContainerType.Basic,
          },
        },
        page: page - 1,
        size: itemsPerPage,
        ...orderByParams,
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
        handleError(e as ResponseError, "searching containers");
      });
  }

  watch(
    queryParams,
    newParams => {
      searchContainers(
        newParams.page ?? 1,
        newParams.sortBy,
        buildQueryString(newParams.searchText ?? null),
        newParams.selectedFilter,
      );
    },
    { immediate: true },
  );

  return {
    serverItems,
    pageCount,
    loading,
    searchContainers,
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
