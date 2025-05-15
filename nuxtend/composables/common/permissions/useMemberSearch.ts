import {
  SearchApi,
  type ResponseError,
  type User,
  type UserGroup,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../api/useShepardApi";

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

    const userSearchStringParam = buildUserQueryString(query);
    const userGroupSearchStringParam = buildUserGroupQueryString(query);

    const searchApi = useShepardApi(SearchApi);

    const userSearchResponse = await useShepardApi(SearchApi)
      .value.searchUsers({
        userSearchBody: {
          searchParams: {
            query: userSearchStringParam,
          },
        },
      })
      .catch(e => {
        handleError(e as ResponseError, "searching for users.");
        return undefined;
      });
    const userGroupSearchResponse = await searchApi.value
      .searchUserGroups({
        userSearchBody: {
          searchParams: {
            query: userGroupSearchStringParam,
          },
        },
      })
      .catch(e => {
        handleError(e as ResponseError, "searching for user groups.");
        return undefined;
      });
    if (userSearchResponse && userSearchResponse.results) {
      searchResults.value = userSearchResponse.results;
    }

    if (userGroupSearchResponse && userGroupSearchResponse.results) {
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
