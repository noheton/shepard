import {
  UserApi,
  UserGroupApi,
  type ResponseError,
  type Roles,
  type User,
} from "@dlr-shepard/backend-client";
import { mapPermissions } from "~/components/common/permissions/mapPermissions";
import type { UpdatedPermissions } from "~/components/context/collection/edit-dialog/collectionEditTypes";
import { resolveNumericIdByName } from "~/composables/context/useCreateUserGroup";
import {
  useUserGroupsV2,
  type UserGroupV2,
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
 * V2-SWEEP-002-2 — member + lifecycle management for a user group.
 *
 * CRUD + membership (add/remove member, delete) go through the appId-keyed
 * `/v2/user-groups` surface (`useUserGroupsV2`). The group is addressed by
 * `appId` throughout.
 *
 * V1-EXCEPTION: roles + permissions (`getUserGroupRoles`,
 * `getUserGroupPermissions`, `editUserGroupPermissions`) have no v2 counterpart
 * in V2-SWEEP-002 slice 1. They need the numeric Neo4j id, which the v2 IO does
 * not expose; we resolve it from the group's unique name (never the route param
 * directly) via `resolveNumericIdByName`. Tracked by V2-SWEEP-002-PERMISSIONS
 * in aidocs/16; this whole permission block collapses to v2 calls once it ships.
 */
export function useHandleUserGroupMembers(userGroup: UserGroupV2) {
  const currentUserGroup = ref<UserGroupV2>(userGroup);
  const loading = ref(false);
  const updatedPermissions = ref<UpdatedPermissions | undefined>(undefined);
  const roles = ref<Roles | undefined>(undefined);

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

  /**
   * V1-EXCEPTION: resolves the numeric id for the permission/role ops that lack
   * a v2 endpoint. Caches per loaded group.
   */
  const numericId = ref<number | null>(null);
  async function ensureNumericId(): Promise<number | null> {
    if (numericId.value == null) {
      numericId.value = await resolveNumericIdByName(
        currentUserGroup.value.name,
      );
    }
    return numericId.value;
  }

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
      const id = await ensureNumericId();
      if (id == null) return;
      // V1-EXCEPTION: no v2 roles endpoint (V2-SWEEP-002-PERMISSIONS).
      roles.value = await useShepardApi(UserGroupApi).value.getUserGroupRoles({
        userGroupId: id,
      });
    } catch (e) {
      handleError(e as ResponseError, "fetching userGroup roles.");
    }
  }

  async function getUserGroupPermissions() {
    try {
      const id = await ensureNumericId();
      if (id == null) return;
      // V1-EXCEPTION: no v2 permissions endpoint (V2-SWEEP-002-PERMISSIONS).
      updatedPermissions.value = await useShepardApi(
        UserGroupApi,
      ).value.getUserGroupPermissions({
        userGroupId: id,
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
    try {
      // v2: membership is a PATCH on the usernames array.
      const updatedUserGroup = await v2.addMember(
        currentUserGroup.value,
        member.username,
      );
      currentUserGroup.value = updatedUserGroup;
      if (updatedPermissions.value) {
        const id = await ensureNumericId();
        if (id != null) {
          updatedPermissions.value = {
            ...updatedPermissions.value,
            reader: updatedPermissions.value.reader.concat([member.username]),
          };
          // V1-EXCEPTION: no v2 permissions endpoint (V2-SWEEP-002-PERMISSIONS).
          await useShepardApi(UserGroupApi).value.editUserGroupPermissions({
            userGroupId: id,
            permissions: updatedPermissions.value,
          });
        }
      }
      emitSuccess(`Successfully added member to "${updatedUserGroup.name}"`);
      await loadMembers();
    } catch (e) {
      handleError(e as ResponseError, "updating userGroup members.");
    }
  }

  async function removeMember(member: User) {
    try {
      // v2: membership is a PATCH on the usernames array.
      const updatedUserGroup = await v2.removeMember(
        currentUserGroup.value,
        member.username,
      );
      currentUserGroup.value = updatedUserGroup;
      if (updatedPermissions.value) {
        const id = await ensureNumericId();
        if (id != null) {
          updatedPermissions.value = {
            ...updatedPermissions.value,
            manager: updatedPermissions.value.manager.filter(
              username => username !== member.username,
            ),
            reader: updatedPermissions.value.reader.filter(
              username => username !== member.username,
            ),
            writer: updatedPermissions.value.writer.filter(
              username => username !== member.username,
            ),
          };
          // V1-EXCEPTION: no v2 permissions endpoint (V2-SWEEP-002-PERMISSIONS).
          await useShepardApi(UserGroupApi).value.editUserGroupPermissions({
            userGroupId: id,
            permissions: updatedPermissions.value,
          });
        }
      }
      emitSuccess(`Successfully removed member from "${updatedUserGroup.name}"`);
      await loadMembers();
    } catch (e) {
      handleError(e as ResponseError, "updating userGroup members.");
    }
  }

  async function addManager(member: User) {
    if (updatedPermissions.value) {
      updatedPermissions.value = {
        ...updatedPermissions.value,
        manager: updatedPermissions.value.manager.concat([member.username]),
      };

      try {
        const id = await ensureNumericId();
        if (id == null) return;
        // V1-EXCEPTION: no v2 permissions endpoint (V2-SWEEP-002-PERMISSIONS).
        await useShepardApi(UserGroupApi).value.editUserGroupPermissions({
          userGroupId: id,
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
        const id = await ensureNumericId();
        if (id == null) return;
        // V1-EXCEPTION: no v2 permissions endpoint (V2-SWEEP-002-PERMISSIONS).
        await useShepardApi(UserGroupApi).value.editUserGroupPermissions({
          userGroupId: id,
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
        const id = await ensureNumericId();
        if (id == null) return;
        // V1-EXCEPTION: no v2 permissions endpoint (V2-SWEEP-002-PERMISSIONS).
        await useShepardApi(UserGroupApi).value.editUserGroupPermissions({
          userGroupId: id,
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
      // v2: delete by appId.
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
