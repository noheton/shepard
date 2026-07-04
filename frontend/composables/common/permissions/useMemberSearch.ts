import {
  UserApi,
  UserGroupsApi,
  UserGroupV2FromJSON,
  type ResponseError,
  type User,
  type UserGroupV2,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../api/useV2ShepardApi";

/**
 * SEARCH-V2-4: Member is now User | UserGroupV2 — only v2 appId-keyed types.
 * Previously User | UserGroup (v1 numeric-id type).
 */
export type Member = User | UserGroupV2;

export enum SearchType {
  USER,
  GROUP,
  MEMBER,
}

export function useMemberSearch(
  searchString: Ref<string | undefined>,
  onSearchDone: () => void,
  searchType: SearchType = SearchType.MEMBER,
) {
  const isLoading = ref<boolean>(false);
  const searchResults = ref<Member[]>([]);

  async function searchUsersByQuery(query: string) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    const userApi = useV2ShepardApi(UserApi);
    const groupApi = useV2ShepardApi(UserGroupsApi);

    const userSearchResponse =
      searchType === SearchType.USER || searchType === SearchType.MEMBER
        ? await userApi.value
            .searchUsersV2({ q: query })
            .catch(e => {
              handleError(e as ResponseError, "searching for users.");
              return undefined;
            })
        : undefined;

    const userGroupSearchResponse =
      searchType === SearchType.GROUP || searchType === SearchType.MEMBER
        ? await groupApi.value
            .listUserGroups({ q: query, pageSize: 50 })
            .catch(e => {
              handleError(e as ResponseError, "searching for user groups.");
              return undefined;
            })
        : undefined;

    searchResults.value = [];

    if (userSearchResponse) {
      searchResults.value = userSearchResponse;
    }

    if (userGroupSearchResponse?.items) {
      const groups: UserGroupV2[] = (userGroupSearchResponse.items as unknown[]).map(
        item => UserGroupV2FromJSON(item),
      );
      searchResults.value.push(...groups);
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
