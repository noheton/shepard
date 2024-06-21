import router from "@/router";
import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import type { ResponseError } from "@dlr-shepard/shepard-client";
import { ref, watch, type Ref } from "vue";

export function useCollectionSearch(
  searchParam: Ref<{
    selectedQueryType: string;
    searchQuery?: string;
  }>,
) {
  const results = ref<{ id: number; name: string; link: string }[]>([]);
  const loading = ref<boolean>(false);

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
    SearchService.search({
      searchBody: {
        scopes: [{ traversalRules: [] }],
        searchParams: {
          query: searchParam.value.searchQuery,
          queryType: "Collection",
        },
      },
    })
      .then(response => {
        results.value =
          response.results?.map(col =>
            genResult(col.id || 0, col.name || ""),
          ) || [];
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching collection search data");
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
