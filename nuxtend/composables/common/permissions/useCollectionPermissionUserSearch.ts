import {
  type ResponseError,
  SearchApi,
  type User,
} from "@dlr-shepard/backend-client";

export function useCollectionPermissionUserSearch(
  searchString: Ref<string | undefined>,
  onSearchDone: () => void,
) {
  const isLoading = ref<boolean>(false);
  const ownerSearchResults = ref<User[]>([]);

  async function searchUsersByQuery(query: string) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    const searchStringParam = buildUserQueryString(query);

    const searchResponse = await createApiInstance(SearchApi)
      .searchUsers({
        userSearchBody: {
          searchParams: {
            query: searchStringParam,
          },
        },
      })
      .catch(e => {
        handleError(e as ResponseError, "searching for users.");
        return undefined;
      });

    if (searchResponse && searchResponse.results) {
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
