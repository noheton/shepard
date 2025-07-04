import { type ContainerType, SearchApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

const routerMap: { [key: string]: string } = {
  FILE: "Files",
  STRUCTUREDDATA: "StructuredData",
  TIMESERIES: "Timeseries",
};

export function useContainerAdvancedSearch(
  searchParam: Ref<{
    selectedQueryType: string;
    searchQuery?: string;
  }>,
) {
  const results = ref<{ id: number; name: string; link: string }[]>([]);
  const loading = ref<boolean>(false);
  const router = useRouter();

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
    useShepardApi(SearchApi)
      .value.searchContainers({
        containerSearchBody: {
          searchParams: {
            query: searchParam.value.searchQuery,
            queryType: searchParam.value
              .selectedQueryType as keyof typeof ContainerType as ContainerType,
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
            const mapResult = routerMap[searchParam.value.selectedQueryType];
            if (!mapResult)
              throw new Error(
                "useContainerAdvancedSearch - routerMap gone wrong",
              );
            addResult(mapResult, container.id, container.name);
          }
        });
        results.value = [...results.value];
      })
      .catch(error => {
        handleError(error, "fetching container search data");
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
