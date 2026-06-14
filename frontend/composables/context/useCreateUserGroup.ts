/**
 * V2-SWEEP-002-3 — annotated with V1-EXCEPTION comments tracking the pending
 * migration to v2 user-group endpoints.
 *
 * All v2 migration is BLOCKED pending V2-SWEEP-002-4:
 * - No v2 search surface for user groups (`SearchApi.searchUserGroups` stays v1).
 * - No v2 create endpoint with a compatible `usernames[]` write path.
 * - No `appId` on the v1 `UserGroup` return type.
 *
 * Tracked in aidocs/16 as V2-SWEEP-002-4.
 */

import {
  type PermissionType,
  SearchApi,
  UserGroupApi,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "../common/api/useShepardApi";

const userGroupSearchStringParam = (name: string) =>
  JSON.stringify({
    property: "name",
    value: name,
    operator: "eq",
  });

export async function createUserGroup(
  userGroupName: string,
  permissionType: PermissionType,
) {
  try {
    // V1-EXCEPTION: no v2 search surface for user groups yet.
    // Tracked in aidocs/16 as V2-SWEEP-002-4 (MISSING-V2-APPID-IN-SEARCH).
    const searchResults = await useShepardApi(SearchApi).value.searchUserGroups(
      {
        userSearchBody: {
          searchParams: {
            query: userGroupSearchStringParam(userGroupName),
          },
        },
      },
    );

    if (searchResults.results && searchResults.results.length > 0) {
      handleError(
        `User group name "${userGroupName}" already exists`,
        "creating user group",
      );
      return null;
    }

    const userGroupApi = useShepardApi(UserGroupApi);

    // V1-EXCEPTION: createUserGroup stays on v1. The v1 `UserGroup` return type
    // has no `appId` field so we cannot call v2 permissions endpoints.
    // Tracked as V2-SWEEP-002-4.
    const createdUserGroup = await userGroupApi.value.createUserGroup({
      userGroup: {
        name: userGroupName,
        usernames: [],
      },
    });

    // V1-EXCEPTION: editUserGroupPermissions stays on v1 (PUT, full replace).
    // Migrate to PATCH /v2/user-groups/{appId}/permissions (RFC 7396) once
    // V2-SWEEP-002-4 ships and the generated client is regenerated.
    await userGroupApi.value.editUserGroupPermissions({
      userGroupId: createdUserGroup.id,
      permissions: {
        permissionType: permissionType,
        reader: [],
        writer: [],
        manager: [],
      },
    });

    emitSuccess(`Successfully created user group "${createdUserGroup.name}"`);
    return createdUserGroup;
  } catch (error) {
    handleError(error, "creating user group");
    return null;
  }
}
