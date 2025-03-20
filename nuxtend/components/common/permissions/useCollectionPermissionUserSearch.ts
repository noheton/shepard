import { SearchApi, type User } from "@dlr-shepard/backend-client";
import { createUserSearchQueryFromString } from "./createSearchQueryFromString";

export function useCollectionPermissionUserSearch(
  searchString: Ref<string | undefined>,
  onSearchDone: () => void,
) {
  const isLoading = ref<boolean>(false);
  const ownerSearchResults = ref<User[]>([]);

  async function searchUsersByQuery(query: string) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    const searchStringParam = createUserSearchQueryFromString(query);

    const searchResponse = await createApiInstance(SearchApi).searchUsers({
      userSearchBody: {
        searchParams: {
          query: searchStringParam,
        },
      },
    });

    if (searchResponse.results) {
      ownerSearchResults.value = searchResponse.results;
    }
    isLoading.value = false;
    onSearchDone();
  }

  function resetResultList() {
    ownerSearchResults.value = [];
  }

  const startSearch = () => {
    if (!searchString.value) {
      resetResultList();
      return;
    }
    searchUsersByQuery(searchString.value);
  };

  return {
    ownerSearchResults,
    isLoading,
    startSearch,
  };
}
