import {
  SearchApi,
  UserGroupApi,
  type User,
  type UserGroup,
} from "@dlr-shepard/backend-client";
import { createSearchQueryFromString } from "./createSearchQueryFromString";

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

    const searchStringParam = createSearchQueryFromString(query);

    const userSearchResponse = await createApiInstance(SearchApi).searchUsers({
      userSearchBody: {
        searchParams: {
          query: searchStringParam,
        },
      },
    });
    const userGroupResponse = isNaN(Number(query))
      ? undefined
      : await createApiInstance(UserGroupApi)
          .getUserGroup({
            userGroupId: Number(query),
          })
          .catch(() => undefined);

    if (userSearchResponse.results) {
      searchResults.value = userSearchResponse.results;
    }

    if (userGroupResponse) {
      searchResults.value.push(userGroupResponse);
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
