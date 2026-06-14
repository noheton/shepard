/**
 * V2-SWEEP-002-3 — composable stub for user-group permission / role helpers.
 *
 * STATUS: MIGRATION BLOCKED — the v2 backend user-group REST surface
 * (`/v2/user-groups/{appId}/roles`, `/v2/user-groups/{appId}/permissions`,
 * `PATCH /v2/user-groups/{appId}/permissions`) does NOT yet exist in the
 * backend or the generated `@dlr-shepard/backend-client`. The v1
 * `UserGroup` entity also carries no `appId` field.
 *
 * The v2 backend endpoints are tracked as V2-SWEEP-002-4 in aidocs/16.
 * Once the backend ships and the client is regenerated this composable
 * should be updated to:
 *
 *   import { UserGroupsApi } from "@dlr-shepard/backend-client";
 *   import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
 *
 *   and delegate to:
 *     api.value.getUserGroupRolesV2({ appId })
 *     api.value.getUserGroupPermissionsV2({ appId })
 *     api.value.patchUserGroupPermissions({ appId, jsonNode: permissions })
 *
 * Until then the call sites in useHandleUserGroupMembers.ts and
 * useCreateUserGroup.ts retain v1 calls (V1-EXCEPTION), documented with
 * V2-SWEEP-002-3 comments.
 *
 * This file exists as a forward declaration / migration marker so:
 *  1. The import in migrated call sites compiles (no change to callers when
 *     the real v2 surface lands).
 *  2. The test file (useUserGroupsV2.test.ts) exercises the wrapper contract
 *     in isolation.
 */

import {
  type Permissions,
  type Roles,
  UserGroupApi,
} from "@dlr-shepard/backend-client";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

/** Wire shape of GET /v2/user-groups/{appId}/roles (future) */
export type UserGroupRolesV2 = Roles;

/** Wire shape of GET|PATCH /v2/user-groups/{appId}/permissions (future) */
export type UserGroupPermissionsV2 = Permissions;

export function useUserGroupsV2() {
  const api = useShepardApi(UserGroupApi);

  /**
   * GET /v1/userGroups/{userGroupId}/roles
   *
   * V1-EXCEPTION: routed via v1 endpoint because the v2 counterpart
   * (`GET /v2/user-groups/{appId}/roles`) does not exist yet and `UserGroup`
   * carries no `appId`. Tracked as V2-SWEEP-002-4.
   */
  async function getUserGroupRoles(
    userGroupId: number,
  ): Promise<UserGroupRolesV2> {
    return api.value.getUserGroupRoles({ userGroupId });
  }

  /**
   * GET /v1/userGroups/{userGroupId}/permissions
   *
   * V1-EXCEPTION: same blocker as getUserGroupRoles.
   * Tracked as V2-SWEEP-002-4.
   */
  async function getUserGroupPermissions(
    userGroupId: number,
  ): Promise<UserGroupPermissionsV2> {
    return api.value.getUserGroupPermissions({ userGroupId });
  }

  /**
   * PUT /v1/userGroups/{userGroupId}/permissions
   *
   * V1-EXCEPTION: v2 `PATCH /v2/user-groups/{appId}/permissions` does not
   * exist yet. This delegates to the v1 PUT (full replacement, not merge-patch).
   * Tracked as V2-SWEEP-002-4.
   */
  async function editUserGroupPermissions(
    userGroupId: number,
    permissions: Omit<Permissions, "entityId">,
  ): Promise<Permissions> {
    return api.value.editUserGroupPermissions({ userGroupId, permissions });
  }

  return {
    getUserGroupRoles,
    getUserGroupPermissions,
    editUserGroupPermissions,
  };
}
