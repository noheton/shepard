/**
 * V2-SWEEP-002-3 — call sites annotated with V1-EXCEPTION comments tracking
 * the pending migration to v2 user-group endpoints.
 *
 * The full v2 migration (using `GET /v2/user-groups/{appId}/roles`,
 * `GET /v2/user-groups/{appId}/permissions`, and
 * `PATCH /v2/user-groups/{appId}/permissions`) is BLOCKED until:
 *
 *   1. The backend ships `UserGroupV2Rest` with the three v2 endpoints.
 *   2. The generated `@dlr-shepard/backend-client` is regenerated to include
 *      the v2 methods (`getUserGroupRolesV2`, `getUserGroupPermissionsV2`,
 *      `patchUserGroupPermissions`).
 *   3. The `UserGroup` v1 model gains an `appId` field (or a v2 list endpoint
 *      that returns `UserGroupV2` with `appId` is added).
 *
 * All three blockers are tracked as V2-SWEEP-002-4 in aidocs/16.
 *
 * Remaining V1-EXCEPTIONs after V2-SWEEP-002-4 lands:
 * - `useShepardApi(UserApi).value.getUser(...)` — no v2 user-by-username
 *   endpoint yet; tracked separately in aidocs/16.
 */

import {
  UserApi,
  UserGroupApi,
  type ResponseError,
  type Roles,
  type User,
  type UserGroup,
} from "@dlr-shepard/backend-client";
import { mapPermissions } from "~/components/common/permissions/mapPermissions";
import type { UpdatedPermissions } from "~/components/context/collection/edit-dialog/collectionEditTypes";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

export const UserGroupMemberRole = {
  manager: "Manager",
  writer: "Writer",
  reader: "Reader",
  owner: "Owner",
};

type UserGroupMemberRole =
  (typeof UserGroupMemberRole)[keyof typeof UserGroupMemberRole];

export interface UserGroupMemberPermissions {
  member: User;
  roleList: UserGroupMemberRole[];
}

export function useHandleUserGroupMembers(userGroup: UserGroup) {
  const currentUserGroup = ref<UserGroup>(userGroup);
  const loading = ref(false);
  const updatedPermissions = ref<UpdatedPermissions | undefined>(undefined);
  const roles = ref<Roles | undefined>(undefined);

  const isAllowedToEditPermissions: ComputedRef<boolean> = computed(() => {
    return !!roles.value?.owner || !!roles.value?.manager;
  });

  const isAllowedToEditOwnership: ComputedRef<boolean> = computed(() => {
    return !!roles.value?.owner;
  });

  const userGroupMemberPermissions = ref<
    UserGroupMemberPermissions[] | undefined
  >(undefined);

  async function getGroupMembers() {
    try {
      const users = await Promise.all(
        currentUserGroup.value.usernames.map(
          async username =>
            await useShepardApi(UserApi).value.getUser({ username }),
        ),
      );
      userGroupMemberPermissions.value = users.map(user => ({
        member: user,
        roleList: [],
      }));
    } catch (e) {
      handleError(e as ResponseError, "fetching userGroup roles.");
    }
  }

  async function getUserRoles() {
    try {
      // V1-EXCEPTION: migrate to GET /v2/user-groups/{appId}/roles once
      // V2-SWEEP-002-4 ships and the generated client is regenerated.
      roles.value = await useShepardApi(UserGroupApi).value.getUserGroupRoles({
        userGroupId: currentUserGroup.value.id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching userGroup roles.");
    }
  }

  async function getUserGroupPermissions() {
    try {
      // V1-EXCEPTION: migrate to GET /v2/user-groups/{appId}/permissions once
      // V2-SWEEP-002-4 ships and the generated client is regenerated.
      updatedPermissions.value = await useShepardApi(
        UserGroupApi,
      ).value.getUserGroupPermissions({
        userGroupId: currentUserGroup.value.id,
      });
      if (updatedPermissions.value.owner) {
        const owner = await useShepardApi(UserApi).value.getUser({
          username: updatedPermissions.value.owner,
        });

        await mapPermissions(
          updatedPermissions.value,
          userGroupMemberPermissions,
        );
        if (userGroupMemberPermissions.value) {
          const ownerMember = userGroupMemberPermissions.value.find(
            userPermission => userPermission.member.username === owner.username,
          );
          if (ownerMember) ownerMember.roleList.push(UserGroupMemberRole.owner);
        }
      }
    } catch (e) {
      handleError(e as ResponseError, "fetching userGroup permissions.");
    }
  }

  async function addMember(member: User) {
    if (
      currentUserGroup.value.usernames.find(
        username => username === member.username,
      )
    ) {
      emitSuccess(
        `User already a member of user group "${currentUserGroup.value.name}"`,
      );
      return;
    }
    const newMembers = currentUserGroup.value.usernames.concat([
      member.username,
    ]);
    if (updatedPermissions.value) {
      updatedPermissions.value = {
        ...updatedPermissions.value,
        reader: updatedPermissions.value.reader.concat([member.username]),
      };
      try {
        const updatedUserGroup = await useShepardApi(
          UserGroupApi,
        ).value.updateUserGroup({
          userGroupId: currentUserGroup.value.id,
          userGroup: { ...currentUserGroup.value, usernames: newMembers },
        });
        // V1-EXCEPTION: migrate to PATCH /v2/user-groups/{appId}/permissions
        // (RFC 7396 merge-patch) once V2-SWEEP-002-4 ships.
        await useShepardApi(UserGroupApi).value.editUserGroupPermissions({
          userGroupId: currentUserGroup.value.id,
          permissions: updatedPermissions.value,
        });
        emitSuccess(`Successfully added member to "${updatedUserGroup.name}"`);
        currentUserGroup.value = updatedUserGroup;
        await loadMembers();
      } catch (e) {
        handleError(e as ResponseError, "updating userGroup members.");
      }
    }
  }

  async function removeMember(member: User) {
    if (updatedPermissions.value) {
      const updatedUserGroup = {
        ...currentUserGroup.value,
        usernames: currentUserGroup.value.usernames.filter(
          username => username !== member.username,
        ),
      };
      updatedPermissions.value = {
        ...updatedPermissions.value,
        manager: updatedPermissions.value?.manager.filter(
          username => username !== member.username,
        ),
        reader: updatedPermissions.value?.reader.filter(
          username => username !== member.username,
        ),
        writer: updatedPermissions.value?.writer.filter(
          username => username !== member.username,
        ),
      };
      try {
        useShepardApi(UserGroupApi).value.updateUserGroup({
          userGroupId: currentUserGroup.value.id,
          userGroup: updatedUserGroup,
        });
        // V1-EXCEPTION: migrate to PATCH /v2/user-groups/{appId}/permissions
        // once V2-SWEEP-002-4 ships.
        useShepardApi(UserGroupApi).value.editUserGroupPermissions({
          userGroupId: currentUserGroup.value.id,
          permissions: updatedPermissions.value,
        });
        emitSuccess(
          `Successfully removed member from "${updatedUserGroup.name}"`,
        );
        currentUserGroup.value = updatedUserGroup;
        await loadMembers();
      } catch (e) {
        handleError(e as ResponseError, "updating userGroup members.");
      }
    }
  }

  async function addManager(member: User) {
    if (updatedPermissions.value) {
      updatedPermissions.value = {
        ...updatedPermissions.value,
        manager: updatedPermissions.value.manager.concat([member.username]),
      };

      try {
        // V1-EXCEPTION: migrate to PATCH /v2/user-groups/{appId}/permissions
        // once V2-SWEEP-002-4 ships.
        await useShepardApi(UserGroupApi).value.editUserGroupPermissions({
          userGroupId: currentUserGroup.value.id,
          permissions: updatedPermissions.value,
        });
        emitSuccess(
          `Successfully added manager to "${currentUserGroup.value.name}"`,
        );
        await loadMembers();
      } catch (e) {
        handleError(e as ResponseError, "updating userGroup permissions.");
      }
    }
  }

  async function removeManager(member: User) {
    if (updatedPermissions.value) {
      updatedPermissions.value = {
        ...updatedPermissions.value,
        manager: updatedPermissions.value?.manager.filter(
          username => username !== member.username,
        ),
      };

      try {
        // V1-EXCEPTION: migrate to PATCH /v2/user-groups/{appId}/permissions
        // once V2-SWEEP-002-4 ships.
        await useShepardApi(UserGroupApi).value.editUserGroupPermissions({
          userGroupId: currentUserGroup.value.id,
          permissions: updatedPermissions.value,
        });
        emitSuccess(
          `Successfully removed manager from "${currentUserGroup.value.name}"`,
        );
        await loadMembers();
      } catch (e) {
        handleError(e as ResponseError, "updating userGroup permissions.");
      }
    }
  }

  async function changeOwner(newOwner: User) {
    if (updatedPermissions.value && updatedPermissions.value.owner) {
      const oldOwner = updatedPermissions.value.owner;
      updatedPermissions.value = {
        ...updatedPermissions.value,
        manager: updatedPermissions.value.manager.concat([oldOwner]),
        reader: updatedPermissions.value.reader.concat([oldOwner]),
        owner: newOwner.username,
      };
      try {
        // V1-EXCEPTION: migrate to PATCH /v2/user-groups/{appId}/permissions
        // once V2-SWEEP-002-4 ships.
        await useShepardApi(UserGroupApi).value.editUserGroupPermissions({
          userGroupId: currentUserGroup.value.id,
          permissions: updatedPermissions.value,
        });
        emitSuccess(
          `Successfully changed owner of "${currentUserGroup.value.name}"`,
        );
        await loadMembers();
      } catch (e) {
        handleError(e as ResponseError, "updating userGroup permissions.");
      }
    }
  }

  async function deleteUserGroup() {
    try {
      // V1-EXCEPTION: no v2 DELETE user-group endpoint exists yet.
      // Tracked as V2-SWEEP-002-4.
      await useShepardApi(UserGroupApi).value.deleteUserGroup({
        userGroupId: currentUserGroup.value.id,
      });
      emitSuccess(
        `Successfully deleted user group "${currentUserGroup.value.name}"`,
      );
    } catch (e) {
      handleError(e as ResponseError, "Deleting user group.");
    }
  }

  async function loadMembers() {
    loading.value = true;
    await getGroupMembers();
    await getUserRoles();
    if (isAllowedToEditPermissions.value) await getUserGroupPermissions();
    loading.value = false;
  }

  loadMembers();

  return {
    userGroupMemberPermissions,
    loading,
    isAllowedToEditPermissions,
    isAllowedToEditOwnership,
    addMember,
    removeMember,
    addManager,
    removeManager,
    changeOwner,
    deleteUserGroup,
  };
}
