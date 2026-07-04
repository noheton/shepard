import type { PermissionType } from "@dlr-shepard/backend-client";
import {
  useUserGroupsV2,
  type UserGroupV2,
} from "~/composables/context/useUserGroupsV2";

/**
 * V2-SWEEP-002-3 — creates a user group via the appId-keyed `/v2/user-groups`
 * surface, then applies the initial permission type via
 * `PATCH /v2/user-groups/{appId}/permissions`.
 *
 * Both operations are v2 (shipped in V2-SWEEP-002 + V2-SWEEP-002-PERMISSIONS,
 * 2026-06-10). The `resolveNumericIdByName` bridge is no longer needed.
 */
export async function createUserGroup(
  userGroupName: string,
  permissionType: PermissionType,
): Promise<UserGroupV2 | null> {
  const { createUserGroup: createV2, patchUserGroupPermissions } =
    useUserGroupsV2();

  let created: UserGroupV2;
  try {
    created = await createV2({ name: userGroupName, usernames: [] });
  } catch {
    handleError(
      `User group name "${userGroupName}" could not be created (it may already exist)`,
      "creating user group",
    );
    return null;
  }

  try {
    await patchUserGroupPermissions(created.appId, {
      permissionType,
      reader: [],
      writer: [],
      manager: [],
    });
    emitSuccess(`Successfully created user group "${created.name}"`);
    return created;
  } catch (error) {
    handleError(error, "setting user group permissions");
    return created;
  }
}
