/**
 * SEARCH-V2-4 — migrated from v1 SearchApi onto GET /v2/users?q=.
 *
 * Replaces the v1 POST /shepard/api/search/users (useShepardApi(SearchApi))
 * with the v2 UserSearchV2Rest endpoint landed in SEARCH-V2-4-PRE.
 */
import { UserApi, type ResponseError, type User } from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../api/useV2ShepardApi";

export function usePermissionUserSearch(
  searchString: Ref<string | undefined>,
  onSearchDone: () => void,
) {
  const isLoading = ref<boolean>(false);
  const ownerSearchResults = ref<User[]>([]);

  const v2UserApi = useV2ShepardApi(UserApi);

  async function searchUsersByQuery(query: string) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    const users = await v2UserApi.value
      .searchUsersV2({ q: query })
      .catch(e => {
        handleError(e as ResponseError, "searching for users.");
        return [] as User[];
      });

    ownerSearchResults.value = users;
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
