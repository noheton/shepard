import { SearchApi, type TraversalRules } from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

export function useDataObjectAdvancedSearch(
  searchParam: Ref<{
    selectedQueryType: string;
    searchQuery?: string;
    collectionId?: number;
    dataObjectId?: number;
    traversalRules?: TraversalRules[];
  }>,
) {
  const results = ref<{ id: number; name: string; link: string }[]>([]);
  const loading = ref<boolean>(false);
  const router = useRouter();

  function genResult(
    objectId: number,
    objectName: string,
    collectionId?: number,
  ) {
    const routeData = router.resolve({
      name: "DataObject",
      params: {
        collectionId: String(collectionId),
        dataObjectId: String(objectId),
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
            queryType: "DataObject",
          },
          scopes: [
            {
              collectionId: searchParam.value.collectionId,
              dataObjectId: searchParam.value.dataObjectId,
              traversalRules: searchParam.value.traversalRules || [],
            },
          ],
        },
      })
      .then(response => {
        const tmp = [];
        if (response.results && response.resultSet) {
          for (let i = 0; i < response.results.length; i++) {
            const obj = response.results[i];
            const resSet = response.resultSet[i];
            if (obj && resSet)
              tmp.push(
                genResult(obj.id || 0, obj.name || "", resSet.collectionId),
              );
          }
        }
        results.value = tmp;
      })
      .catch(error => {
        handleError(error, "fetching data object search data");
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
    if (newParam.selectedQueryType == "DataObject") search();
    else reset();
  });

  return { results, loading };
}
