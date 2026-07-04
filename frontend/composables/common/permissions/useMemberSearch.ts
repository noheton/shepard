/**
 * SEARCH-V2-4 — migrated from v1 SearchApi onto the v2 surface.
 *
 * User search: GET /v2/users?q= (UserSearchV2Rest, landed in SEARCH-V2-4-PRE).
 * Group search: GET /v2/user-groups?q= (UserGroupV2Rest, SEARCH-V2-4-PRE).
 *
 * Returns User[] and UserGroup[] respectively. Groups are mapped from
 * UserGroupV2 (backend-client) to the v1 UserGroup shape so the rest
 * of the permissions system (mapPermissions, MemberPermissionList, etc.)
 * can remain on the existing Member type without a wider refactor.
 * The numeric `id` is set to 0 (sentinel — v2 does not expose it);
 * all equality checks in the permission UI use `appId`.
 */
import {
  UserApi,
  UserGroupsApi,
  type ResponseError,
  type User,
  type UserGroup,
  type UserGroupV2,
  UserGroupV2FromJSON,
} from "@dlr-shepard/backend-client";
import { useV2ShepardApi } from "../api/useV2ShepardApi";

export type Member = User | UserGroup;

export enum SearchType {
  USER,
  GROUP,
  MEMBER,
}

/** Maps a backend-client UserGroupV2 to the legacy UserGroup shape. */
function v2GroupToUserGroup(v2: UserGroupV2): UserGroup {
  return {
    id: 0, // v2 does not expose numeric Neo4j id; use appId for equality
    name: v2.name,
    appId: v2.appId ?? null,
    usernames: v2.usernames ?? [],
    createdAt: v2.createdAt ? new Date(v2.createdAt) : new Date(0),
    createdBy: v2.createdBy ?? "",
    updatedAt: v2.updatedAt != null ? new Date(v2.updatedAt) : null,
    updatedBy: v2.updatedBy ?? null,
  };
}

export function useMemberSearch(
  searchString: Ref<string | undefined>,
  onSearchDone: () => void,
  searchType: SearchType = SearchType.MEMBER,
) {
  const isLoading = ref<boolean>(false);
  const searchResults = ref<Member[]>([]);

  const v2UserApi = useV2ShepardApi(UserApi);
  const v2UserGroupsApi = useV2ShepardApi(UserGroupsApi);

  async function searchUsersByQuery(query: string) {
    if (isLoading.value === true) return;

    isLoading.value = true;

    const shouldSearchUsers =
      searchType === SearchType.USER || searchType === SearchType.MEMBER;
    const shouldSearchGroups =
      searchType === SearchType.GROUP || searchType === SearchType.MEMBER;

    const [userResults, groupResults] = await Promise.all([
      shouldSearchUsers
        ? v2UserApi.value
            .searchUsersV2({ q: query })
            .catch(e => {
              handleError(e as ResponseError, "searching for users.");
              return [] as User[];
            })
        : Promise.resolve([] as User[]),
      shouldSearchGroups
        ? v2UserGroupsApi.value
            .listUserGroups({ q: query })
            .catch(e => {
              handleError(e as ResponseError, "searching for user groups.");
              return null;
            })
        : Promise.resolve(null),
    ]);

    searchResults.value = [];

    if (userResults.length > 0) {
      searchResults.value.push(...userResults);
    }

    if (groupResults?.items) {
      const groups = groupResults.items.map((item: unknown) =>
        v2GroupToUserGroup(UserGroupV2FromJSON(item)),
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
