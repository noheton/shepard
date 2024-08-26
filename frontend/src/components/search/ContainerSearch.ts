import router from "@/router";
import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import type {
  ContainerQueryType,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { ref, watch, type Ref } from "vue";

const routerMap: { [key: string]: string } = {
  FILE: "Files",
  STRUCTUREDDATA: "StructuredData",
  TIMESERIES: "Timeseries",
};

export function useContainerSearch(
  searchParam: Ref<{
    selectedQueryType: string;
    searchQuery?: string;
  }>,
) {
  const results = ref<{ id: number; name: string; link: string }[]>([]);
  const loading = ref<boolean>(false);

  function addResult(name: string, containerId: number, containerName: string) {
    const routeData = router.resolve({
      name: name,
      params: {
        fileId: String(containerId),
        structuredDataId: String(containerId),
        timeseriesId: String(containerId),
      },
    });
    results?.value.push({
      id: containerId,
      name: containerName,
      link: routeData.href,
    });
  }

  function search() {
    if (!searchParam.value.searchQuery) return;
    loading.value = true;
    results.value = [];
    SearchService.searchContainers({
      containerSearchBody: {
        searchParams: {
          query: searchParam.value.searchQuery,
          queryType: searchParam.value
            .selectedQueryType as keyof typeof ContainerQueryType as ContainerQueryType,
        },
      },
    })
      .then(response => {
        response.results?.forEach(container => {
          if (
            container.id &&
            container.name &&
            Object.keys(routerMap).includes(searchParam.value.selectedQueryType)
          ) {
            addResult(
              routerMap[searchParam.value.selectedQueryType],
              container.id,
              container.name,
            );
          }
        });
        results.value = [...results.value];
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching container search data");
      })
      .finally(() => {
        loading.value = false;
      });
  }

  function reset() {
    results.value = [];
    loading.value = false;
  }

  watch(searchParam, newParam => {
    if (Object.keys(routerMap).includes(newParam.selectedQueryType)) search();
    else reset();
  });

  return { results, loading };
}
