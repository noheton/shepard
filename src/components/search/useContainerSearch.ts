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
              let routeData = undefined;
              (routeData = router.resolve({
                name: "Files",
                params: {
                  fileId: String(container.id),
                },
              })),
                results?.value.push({
                  id: container.id,
                  name: container.name,
                  link: routeData.href,
                });
            }
          });
        }

        if (response.structuredDataContainers) {
          let routeData = undefined;
          response.structuredDataContainers.forEach(container => {
            if (container.id && container.name) {
              (routeData = router.resolve({
                name: "StructuredData",
                params: {
                  fileId: String(container.id),
                },
              })),
                results?.value.push({
                  id: container.id,
                  name: container.name,
                  link: routeData.href,
                });
            }
          });
        }

        if (response.timeseriesContainers) {
          let routeData = undefined;
          response.timeseriesContainers.forEach(container => {
            if (container.id && container.name) {
              (routeData = router.resolve({
                name: "Timeseries",
                params: {
                  fileId: String(container.id),
                },
              })),
                results?.value.push({
                  id: container.id,
                  name: container.name,
                  link: routeData.href,
                });
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
