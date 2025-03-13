import { SearchApi, type User } from "@dlr-shepard/backend-client";

export function useCollectionPermissionUserSearch(
  searchString: Ref<string | undefined>,
  onSearchDone: () => void,
) {
  const isLoading = ref<boolean>(false);
  const ownerSearchResults = ref<User[]>([]);

  async function searchUsersByQuery(query: string) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    const searchStringParam = createSearchQueryFromString(query);

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

  function createSearchQueryFromString(searchText: string): string {
    return JSON.stringify({
      OR: [
        {
          property: "username",
          value: searchText ?? "",
          operator: "contains",
        },
        {
          property: "firstName",
          value: searchText ?? "",
          operator: "contains",
        },
        {
          property: "lastName",
          value: searchText,
          operator: "contains",
        },
        {
          property: "email",
          value: searchText,
          operator: "contains",
        },
      ],
    });
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
