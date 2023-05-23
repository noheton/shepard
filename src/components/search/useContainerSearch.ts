import router from "@/router";
import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import type {
  ContainerSearchParamsQueryTypeEnum,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { ref, watch, type Ref } from "vue";

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
        if (response.fileContainers) {
          response.fileContainers.forEach(container => {
            if (container.id && container.name) {
              addResult("Files", container.id, container.name);
            }
          });
        }

        if (response.structuredDataContainers) {
          response.structuredDataContainers.forEach(container => {
            if (container.id && container.name) {
              addResult("StructuredData", container.id, container.name);
            }
          });
        }

        if (response.timeseriesContainers) {
          response.timeseriesContainers.forEach(container => {
            if (container.id && container.name) {
              addResult("Timeseries", container.id, container.name);
            }
          });
        }
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
