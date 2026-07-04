import {
  UserApi,
  type ResponseError,
  type User,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../api/useV2ShepardApi";

/**
 * SEARCH-V2-4: migrated from v1 SearchApi POST /search/users to
 * GET /v2/users?q= via useV2ShepardApi.
 */
export function usePermissionUserSearch(
  searchString: Ref<string | undefined>,
  onSearchDone: () => void,
) {
  const isLoading = ref<boolean>(false);
  const ownerSearchResults = ref<User[]>([]);

  async function searchUsersByQuery(query: string) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    const users = await useV2ShepardApi(UserApi)
      .value.searchUsersV2({ q: query })
      .catch(e => {
        handleError(e as ResponseError, "searching for users.");
        return undefined;
      });

    if (users) {
      ownerSearchResults.value = users;
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
