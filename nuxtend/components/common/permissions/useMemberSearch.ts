import {
  SearchApi,
  type User,
  type UserGroup,
} from "@dlr-shepard/backend-client";
import {
  createUserGroupSearchQueryFromString,
  createUserSearchQueryFromString,
} from "./createSearchQueryFromString";

export type Member = User | UserGroup;

export function useMemberSearch(
  searchString: Ref<string | undefined>,
  onSearchDone: () => void,
) {
  const isLoading = ref<boolean>(false);
  const searchResults = ref<Member[]>([]);

  async function searchUsersByQuery(query: string) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    const userSearchStringParam = createUserSearchQueryFromString(query);
    const userGroupSearchStringParam =
      createUserGroupSearchQueryFromString(query);

    const userSearchResponse = await createApiInstance(SearchApi).searchUsers({
      userSearchBody: {
        searchParams: {
          query: userSearchStringParam,
        },
      },
    });
    const userGroupSearchResponse = await createApiInstance(
      SearchApi,
    ).searchUserGroups({
      userSearchBody: {
        searchParams: {
          query: userGroupSearchStringParam,
        },
      },
    });
    if (userSearchResponse.results) {
      searchResults.value = userSearchResponse.results;
    }

    if (userGroupSearchResponse.results) {
      searchResults.value.push(...userGroupSearchResponse.results);
    }

    isLoading.value = false;
    onSearchDone();
  }

  function resetResultList() {
    searchResults.value = [];
  }

  const startSearch = () => {
    if (!searchString.value) {
      resetResultList();
      return;
    }
    searchUsersByQuery(searchString.value);
  };

  return {
    searchResults,
    isLoading,
    startSearch,
  };
}
