import {
  UserApi,
  type User,
  type ResponseError,
} from "@dlr-shepard/backend-client";
import { mapPermissions } from "~/components/common/permissions/mapPermissions";
import type { UpdatedPermissions } from "~/components/context/collection/edit-dialog/collectionEditTypes";
import {
  useUserGroupsV2,
  type UserGroupV2,
  type UserGroupRolesV2,
  type UserGroupPermissionsV2,
} from "~/composables/context/useUserGroupsV2";
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

/**
 * V2-SWEEP-002-3 — member + lifecycle management for a user group.
 *
 * All operations go through the appId-keyed `/v2/user-groups` surface
 * (`useUserGroupsV2`). Roles and permissions are now served by
 * `GET /v2/user-groups/{appId}/roles` and
 * `GET|PATCH /v2/user-groups/{appId}/permissions`
 * (shipped in V2-SWEEP-002-PERMISSIONS, 2026-06-10).
 *
 * Remaining V1-EXCEPTION: `mapPermissions.ts` resolves numeric group IDs
 * from `readerGroupIds`/`writerGroupIds` in the permissions payload via the
 * v1 `getUserGroup({userGroupId: number})` endpoint. That stays until the
 * permissions payload is redesigned to carry group appIds (V2-SWEEP-002-4).
 */
export function useHandleUserGroupMembers(userGroup: UserGroupV2) {
  const currentUserGroup = ref<UserGroupV2>(userGroup);
  const loading = ref(false);
  const updatedPermissions = ref<UserGroupPermissionsV2 | undefined>(undefined);
  const roles = ref<UserGroupRolesV2 | undefined>(undefined);

  const v2 = useUserGroupsV2();

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
      handleError(e as ResponseError, "fetching userGroup members.");
    }
  }

  async function getUserRoles() {
    try {
      roles.value = await v2.getUserGroupRoles(currentUserGroup.value.appId);
    } catch (e) {
      handleError(e as ResponseError, "fetching userGroup roles.");
    }
  }

  async function getUserGroupPermissions() {
    try {
      updatedPermissions.value = await v2.getUserGroupPermissions(
        currentUserGroup.value.appId,
      );
      if (updatedPermissions.value.owner) {
        await useShepardApi(UserApi).value.getUser({
          username: updatedPermissions.value.owner,
        });
        await mapPermissions(
          updatedPermissions.value as UpdatedPermissions,
          userGroupMemberPermissions,
        );
        if (userGroupMemberPermissions.value) {
          const ownerMember = userGroupMemberPermissions.value.find(
            userPermission =>
              userPermission.member.username === updatedPermissions.value!.owner,
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
    try {
      const updatedUserGroup = await v2.addMember(
        currentUserGroup.value,
        member.username,
      );
      currentUserGroup.value = updatedUserGroup;
      if (updatedPermissions.value) {
        updatedPermissions.value = {
          ...updatedPermissions.value,
          reader: (updatedPermissions.value.reader ?? []).concat([
            member.username,
          ]),
        };
        await v2.patchUserGroupPermissions(
          currentUserGroup.value.appId,
          updatedPermissions.value,
        );
      }
      emitSuccess(`Successfully added member to "${updatedUserGroup.name}"`);
      await loadMembers();
    } catch (e) {
      handleError(e as ResponseError, "updating userGroup members.");
    }
  }

  async function removeMember(member: User) {
    try {
      const updatedUserGroup = await v2.removeMember(
        currentUserGroup.value,
        member.username,
      );
      currentUserGroup.value = updatedUserGroup;
      if (updatedPermissions.value) {
        updatedPermissions.value = {
          ...updatedPermissions.value,
          manager: (updatedPermissions.value.manager ?? []).filter(
            username => username !== member.username,
          ),
          reader: (updatedPermissions.value.reader ?? []).filter(
            username => username !== member.username,
          ),
          writer: (updatedPermissions.value.writer ?? []).filter(
            username => username !== member.username,
          ),
        };
        await v2.patchUserGroupPermissions(
          currentUserGroup.value.appId,
          updatedPermissions.value,
        );
      }
      emitSuccess(
        `Successfully removed member from "${updatedUserGroup.name}"`,
      );
      await loadMembers();
    } catch (e) {
      handleError(e as ResponseError, "updating userGroup members.");
    }
  }

  async function addManager(member: User) {
    if (updatedPermissions.value) {
      updatedPermissions.value = {
        ...updatedPermissions.value,
        manager: (updatedPermissions.value.manager ?? []).concat([
          member.username,
        ]),
      };
      try {
        await v2.patchUserGroupPermissions(
          currentUserGroup.value.appId,
          updatedPermissions.value,
        );
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
        manager: (updatedPermissions.value.manager ?? []).filter(
          username => username !== member.username,
        ),
      };
      try {
        await v2.patchUserGroupPermissions(
          currentUserGroup.value.appId,
          updatedPermissions.value,
        );
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
        manager: (updatedPermissions.value.manager ?? []).concat([oldOwner]),
        reader: (updatedPermissions.value.reader ?? []).concat([oldOwner]),
        owner: newOwner.username,
      };
      try {
        await v2.patchUserGroupPermissions(
          currentUserGroup.value.appId,
          updatedPermissions.value,
        );
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
      await v2.deleteUserGroup(currentUserGroup.value.appId);
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
