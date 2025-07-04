import { SearchApi } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

export function useCollectionAdvancedSearch(
  searchParam: Ref<{
    selectedQueryType: string;
    searchQuery?: string;
  }>,
) {
  const results = ref<{ id: number; name: string; link: string }[]>([]);
  const loading = ref<boolean>(false);
  const router = useRouter();

  function genResult(objectId: number, objectName: string) {
    const routeData = router.resolve({
      name: "Collection",
      params: {
        collectionId: String(objectId),
      },
    });
    return {
      id: objectId,
      name: objectName,
      link: routeData.href,
    };
  }

  function search() {
    if (!searchParam.value.searchQuery) return;
    loading.value = true;
    results.value = [];
    useShepardApi(SearchApi)
      .value.search({
        searchBody: {
          searchParams: {
            query: searchParam.value.searchQuery,
            queryType: "Collection",
          },
          scopes: [{ traversalRules: [] }],
        },
      })
      .then(response => {
        results.value =
          response.results?.map(col =>
            genResult(col.id || 0, col.name || ""),
          ) || [];
      })
      .catch(error => {
        handleError(error, "fetching collection search data");
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
    if (newParam.selectedQueryType == "Collection") search();
    else reset();
  });

  return { results, loading };
}
