import {
  ContainerType,
  SearchApi,
  type BasicContainer,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { buildQueryString } from "~/components/container/buildQueryString";
import {
  ContainerSortByOrderOptions,
  type ContainerSortByAttribute,
} from "~/components/container/containerSortByAttribute";
import type { ContainerFilterType } from "~/components/container/containerTypeFilter";

export function useSearchContainers(
  queryParams: globalThis.Ref<ContainerListQueryParams>,
  itemsPerPage: number,
) {
  const serverItems = ref<BasicContainer[]>([]);
  const pageCount = ref<number>(0);
  const loading = ref<boolean>(true);

  const searchQuery = computed(() =>
    buildQueryString(queryParams.value.searchText ?? null),
  );

  function searchContainers(
    page: number,
    sortByAttributes: ContainerSortByAttribute | undefined,
    searchQuery: string,
    selectedFilter: ContainerFilterType | null | undefined,
  ) {
    loading.value = true;

    const orderByParams = sortByAttributes
      ? {
          orderBy: sortByAttributes.key,
          orderDesc:
            sortByAttributes.order === ContainerSortByOrderOptions.DESC,
        }
      : null;
    createApiInstance(SearchApi)
      .searchContainers({
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
        if (
          response.results !== undefined &&
          response.totalResults !== undefined
        ) {
          serverItems.value = response.results;
          pageCount.value = Math.ceil(response.totalResults / itemsPerPage);
        }
        loading.value = false;
      })
      .catch(e => {
        handleError(e as ResponseError, "searching containers");
      });
  }

  watch(
    queryParams,
    () => {
      searchContainers(
        queryParams.value.page,
        queryParams.value.sortBy,
        searchQuery.value,
        queryParams.value.selectedFilter,
      );
    },
    { immediate: true },
  );

  return {
    serverItems,
    pageCount,
    loading,
    searchContainers,
  };
}
