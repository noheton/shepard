import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import type {
  ResponseBody,
  ResponseError,
  SearchParamsQueryTypeEnum,
  SearchScopeTraversalRulesEnum,
} from "@dlr-shepard/shepard-client";
import { ref, watch, type Ref } from "vue";

export function useUnifiedSearch(
  unifiedSearchParam: Ref<{
    searchQuery: string | undefined;
    selectedQueryType: string | undefined;
    collectionId: number | undefined;
    dataObjectId: number | undefined;
    traversalRules: SearchScopeTraversalRulesEnum[];
  }>,
) {
  const results = ref<ResponseBody>();
  const loading = ref<boolean>(false);

  function search() {
    if (!unifiedSearchParam.value.searchQuery) return;
    loading.value = true;
    SearchService.search({
      searchBody: {
        scopes: [
          {
            collectionId: unifiedSearchParam.value.collectionId,
            dataObjectId: unifiedSearchParam.value.dataObjectId,
            traversalRules: unifiedSearchParam.value.traversalRules,
          },
        ],
        searchParams: {
          query: unifiedSearchParam.value.searchQuery,
          queryType: unifiedSearchParam.value
            .selectedQueryType as keyof typeof SearchParamsQueryTypeEnum as SearchParamsQueryTypeEnum,
        },
      },
    })
      .then(response => {
        results.value = response;
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching search data");
      });
    loading.value = false;
  }
  watch(unifiedSearchParam, () => {
    search();
  });

  return { results, loading };
}
