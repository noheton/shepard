import { handleError } from "@/utils/error-handling";

import {
  QueryType,
  SearchApi,
  type BasicEntity,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { computed, ref, watch, type Ref } from "vue";
import { isNumeric } from "./helpers";

export function useSearchCollections(text: Ref<string>) {
  const results = ref<BasicEntity[]>([]);
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

  function inlineSearch() {
    createApiInstance(SearchApi)
      .search({
        searchBody: {
          scopes: [
            {
              traversalRules: [],
            },
          ],
          searchParams: {
            query: searchQuery.value,
            queryType: QueryType.Collection,
          },
        },
      })
      .then(response => {
        results.value = [];
        if (response.results) {
          totalResults.value = response.resultSet?.length || 0;
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
      inlineSearch();
    } else {
      results.value = [];
      totalResults.value = 0;
    }
  });

  return { results, totalResults, searchQuery };
}
