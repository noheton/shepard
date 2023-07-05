import router from "@/router";
import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import type {
  ContainerSearchParamsQueryTypeEnum,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { ref, watch, type Ref } from "vue";

const routerMap: { [key: string]: string } = {
  FILE: "Files",
  STRUCTUREDDATA: "StructuredData",
  TIMESERIES: "Timeseries",
};

export function useContainerSearch(
  containerSearchParam: Ref<{
    searchQuery: string | undefined;
    selectedQueryType: string | undefined;
  }>,
) {
  const results = ref<{ id: number; name: string; link: string }[]>([]);
  const loading = ref<boolean>(false);

  function addResult(name: string, containerId: number, containerName: string) {
    const routeData = router.resolve({
      name: name,
      params: {
        fileId: String(containerId),
      },
    });
    results?.value.push({
      id: containerId,
      name: containerName,
      link: routeData.href,
    });
  }

  function search() {
    if (!containerSearchParam.value.searchQuery) return;
    loading.value = true;
    results.value = [];
    SearchService.searchContainers({
      containerSearchBody: {
        searchParams: {
          query: containerSearchParam.value.searchQuery,
          queryType: containerSearchParam.value
            .selectedQueryType as keyof typeof ContainerSearchParamsQueryTypeEnum as ContainerSearchParamsQueryTypeEnum,
        },
      },
    })
      .then(response => {
        response.results?.forEach(container => {
          if (
            container.id &&
            container.name &&
            containerSearchParam.value.selectedQueryType
          ) {
            addResult(
              routerMap[containerSearchParam.value.selectedQueryType],
              container.id,
              container.name,
            );
          }
        });
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching search data");
      });
    loading.value = false;
  }

  watch(containerSearchParam, () => {
    search();
  });

  return { results, loading };
}
