import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import type {
  BasicEntity,
  ContainerSearchParamsQueryTypeEnum,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { computed, ref, watch, type Ref } from "vue";

export function useInlineSearch(
  text: Ref<string>,
  queryType: ContainerSearchParamsQueryTypeEnum,
) {
  const resultSet = ref<BasicEntity[]>([]);
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
        resultSet.value = [];
        if (response.results) {
          totalResults.value = response.results.length;
          resultSet.value = response.results.slice(0, 10);
        } else {
          totalResults.value = 0;
        }
      })
      .catch(e => {
        handleError(e as ResponseError, "fetching search data");
      });
  }

  watch(text, () => {
    if (
      text.value.length != 0 &&
      (text.value.length >= 3 || !isNaN(Number(text.value)))
    ) {
      fetchResults();
    } else {
      resultSet.value = [];
    }
  });

  return { resultSet, totalResults, searchQuery };
}
