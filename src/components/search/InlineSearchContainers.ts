import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import type {
  ContainerSearchParamsQueryTypeEnum,
  ContainerSearchResult,
  ResponseError,
} from "@dlr-shepard/shepard-client";
import { ref, watch, type Ref } from "vue";

export function useInlineSearch(
  text: Ref<string>,
  queryType: ContainerSearchParamsQueryTypeEnum,
) {
  const result = ref<ContainerSearchResult>();

  function fetchResults() {
    const searchQuery = {
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
    };
    SearchService.searchContainers({
      containerSearchBody: {
        searchParams: {
          query: JSON.stringify(searchQuery),
          queryType: queryType,
        },
      },
    })
      .then(response => {
        result.value = response;
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
      result.value = undefined;
    }
  });

  return result;
}
