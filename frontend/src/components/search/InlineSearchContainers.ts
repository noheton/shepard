import type {
  BasicContainer,
  ContainerQueryType,
  ResponseError,
} from "@/generated/openapi";
import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import { isNumeric } from "@/utils/helpers";
import { computed, ref, watch, type Ref } from "vue";

export function useSearchContainers(
  text: Ref<string>,
  queryType: ContainerQueryType,
) {
  const results = ref<BasicContainer[]>([]);
  const totalResults = ref(0);

  const searchQuery = computed(() => {
    return JSON.stringify({
      OR: [
        {
          property: "name",
          value: text.value,
          operator: "contains",
        },
        {
          property: "createdBy",
          value: text.value,
          operator: "contains",
        },
        {
          property: "description",
          value: text.value,
          operator: "contains",
        },
        {
          property: "id",
          value: Number(text.value),
          operator: "eq",
        },
      ],
    });
  });

  function fetchResults() {
    SearchService.searchContainers({
      containerSearchBody: {
        searchParams: {
          query: searchQuery.value,
          queryType: queryType,
        },
      },
    })
      .then(response => {
        results.value = [];
        if (response.results) {
          totalResults.value = response.results.length;
          results.value = response.results.slice(0, 10);
        } else {
          totalResults.value = 0;
        }
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching search data");
      });
  }

  watch(text, () => {
    if (isNumeric(text.value) || text.value.length >= 3) {
      fetchResults();
    } else {
      results.value = [];
    }
  });

  return { results, totalResults, searchQuery };
}
