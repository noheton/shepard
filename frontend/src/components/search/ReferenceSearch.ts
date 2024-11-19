import router from "@/router";
import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import type {
  ResponseError,
  TraversalRules,
} from "@dlr-shepard/backend-client";
import { ref, watch, type Ref } from "vue";

export function useReferenceSearch(
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

  function genResult(
    objectId: number,
    objectName: string,
    collectionId?: number,
    dataObjectId?: number,
  ) {
    const routeData = router.resolve({
      name: "DataObject",
      params: {
        collectionId: String(collectionId),
        dataObjectId: String(dataObjectId),
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
    SearchService.search({
      searchBody: {
        scopes: [
          {
            collectionId: searchParam.value.collectionId,
            dataObjectId: searchParam.value.dataObjectId,
            traversalRules: searchParam.value.traversalRules || [],
          },
        ],
        searchParams: {
          query: searchParam.value.searchQuery,
          queryType: "Reference",
        },
      },
    })
      .then(response => {
        const tmp = [];
        if (response.results && response.resultSet) {
          for (let i = 0; i < response.results.length; i++) {
            const obj = response.results[i];
            const resSet = response.resultSet[i];
            tmp.push(
              genResult(
                obj.id || 0,
                obj.name || "",
                resSet.collectionId,
                resSet.dataObjectId,
              ),
            );
          }
        }
        results.value = tmp;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching reference search data");
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
    if (newParam.selectedQueryType == "Reference") search();
    else reset();
  });

  return { results, loading };
}
