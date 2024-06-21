import SearchService from "@/services/searchService";
import { handleError } from "@/utils/error-handling";
import type { ResponseError, User } from "@dlr-shepard/shepard-client";
import { computed, ref, watch, type Ref } from "vue";

export function useSearchUsers(text: Ref<string>) {
  const results = ref<User[]>([]);
  const totalResults = ref(0);

  const searchQuery = computed(() => {
    return JSON.stringify({
      OR: [
        {
          property: "username",
          value: text.value,
          operator: "contains",
        },
        {
          property: "firstName",
          value: text.value,
          operator: "contains",
        },
        {
          property: "lastName",
          value: text.value,
          operator: "contains",
        },
        {
          property: "email",
          value: text.value,
          operator: "contains",
        },
      ],
    });
  });

  function fetchResults() {
    SearchService.searchUsers({
      userSearchBody: {
        searchParams: {
          query: searchQuery.value,
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
    if (text.value.length >= 3) {
      fetchResults();
    } else {
      results.value = [];
    }
  });

  return { results, totalResults, searchQuery };
}
