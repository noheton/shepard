import {
  type PermissionType,
  UserGroupApi,
} from "@dlr-shepard/backend-client";
import {
  useUserGroupsV2,
  type UserGroupV2,
} from "~/composables/context/useUserGroupsV2";
import { useShepardApi } from "../common/api/useShepardApi";

/**
 * V2-SWEEP-002-2 — creates a user group via the appId-keyed `/v2/user-groups`
 * surface, then applies the initial permission type.
 *
 * Create itself is v2 (`useUserGroupsV2().createUserGroup`). Uniqueness is
 * enforced server-side (409 on duplicate name), surfaced here as an error.
 *
 * V1-EXCEPTION: `editUserGroupPermissions` has no v2 counterpart in
 * V2-SWEEP-002 slice 1 (permissions/roles are deferred). It needs the numeric
 * Neo4j id, which the v2 IO does not expose. Per the frontend-v2-only rule we
 * resolve it from the freshly-created v2 group by matching on its (unique) name
 * via the v1 list — the route/links still carry the appId. Tracked by
 * V2-SWEEP-002-PERMISSIONS in aidocs/16.
 */
export async function createUserGroup(
  userGroupName: string,
  permissionType: PermissionType,
): Promise<UserGroupV2 | null> {
  const { createUserGroup: createV2 } = useUserGroupsV2();

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
    // V1-EXCEPTION: no v2 permission endpoint yet (V2-SWEEP-002-PERMISSIONS).
    // Resolve the numeric id from the v2 group's unique name; never expose it.
    const numericId = await resolveNumericIdByName(created.name);
    if (numericId != null) {
      await useShepardApi(UserGroupApi).value.editUserGroupPermissions({
        userGroupId: numericId,
        permissions: {
          permissionType,
          reader: [],
          writer: [],
          manager: [],
        },
      });
    }
    emitSuccess(`Successfully created user group "${created.name}"`);
    return created;
  } catch (error) {
    handleError(error, "setting user group permissions");
    // Group was created; return it so the UI can proceed.
    return created;
  }
}

/**
 * V1-EXCEPTION helper — maps a v2 user group's unique name to its numeric Neo4j
 * id for the permission ops that lack a v2 endpoint. Removed once
 * V2-SWEEP-002-PERMISSIONS ships appId-keyed permission endpoints.
 */
export async function resolveNumericIdByName(
  name: string,
): Promise<number | null> {
  const all = await useShepardApi(UserGroupApi).value.getAllUserGroups({
    orderDesc: false,
  });
  const match = all.find(g => g.name === name);
  return match?.id ?? null;
}
