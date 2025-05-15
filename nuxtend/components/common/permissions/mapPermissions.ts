import {
  instanceOfUser,
  instanceOfUserGroup,
  UserApi,
  UserGroupApi,
  type Permissions,
  type User,
  type UserGroup,
} from "@dlr-shepard/backend-client";
import type { UpdatedPermissions } from "~/components/context/collection/edit-dialog/collectionEditTypes";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import type { MemberPermissions } from "./EditPermissionsDialog.vue";
import { UserRole } from "./UserRole";

export async function mapPermissions(
  permissions: UpdatedPermissions,
): Promise<MemberPermissions[]> {
  const memberPermissions: MemberPermissions[] = [];
  await mapPermissionRoleUsers(
    permissions?.manager,
    UserRole.manager,
    memberPermissions,
  );
  await mapPermissionRoleUsers(
    permissions?.reader,
    UserRole.reader,
    memberPermissions,
  );
  await mapPermissionRoleUsers(
    permissions?.writer,
    UserRole.writer,
    memberPermissions,
  );
  await mapPermissionRoleUserGroups(
    permissions?.readerGroupIds,
    UserRole.reader,
    memberPermissions,
  );
  await mapPermissionRoleUserGroups(
    permissions?.writerGroupIds,
    UserRole.writer,
    memberPermissions,
  );

  return memberPermissions;
}

async function mapPermissionRoleUsers(
  roleUsernames: string[] | undefined,
  userRole: UserRole,
  userPermissions: MemberPermissions[],
) {
  if (!roleUsernames) return;
  await Promise.all(
    roleUsernames.map(async roleUsername => {
      const existing = userPermissions.find(
        userPermission =>
          instanceOfUser(userPermission.member) &&
          userPermission.member.username === roleUsername,
      );
      if (existing) existing.roleList.push(userRole);
      else {
        const user: User = await fetchUser(roleUsername);
        userPermissions.push({
          member: user,
          roleList: [userRole],
        });
      }
    }),
  );
}

async function mapPermissionRoleUserGroups(
  roleUserGroupIds: number[] | undefined,
  groupRole: UserRole,
  userPermissions: MemberPermissions[],
) {
  if (!roleUserGroupIds) return;
  await Promise.all(
    roleUserGroupIds.map(async roleUserGroupId => {
      const existing = userPermissions.find(
        userPermission =>
          instanceOfUserGroup(userPermission.member) &&
          userPermission.member.id === roleUserGroupId,
      );
      if (existing) existing.roleList.push(groupRole);
      else {
        const userGroup: UserGroup = await fetchUserGroup(roleUserGroupId);
        userPermissions.push({
          member: userGroup,
          roleList: [groupRole],
        });
      }
    }),
  );
}

async function fetchUser(username: string) {
  const user = await useShepardApi(UserApi).value.getUser({
    username,
  });
  return user;
}
async function fetchUserGroup(groupId: number) {
  const userGroup = await useShepardApi(UserGroupApi).value.getUserGroup({
    userGroupId: groupId,
  });
  return userGroup;
}

export const mapMemberPermissions = (
  memberPermissionsList: MemberPermissions[],
): Omit<Permissions, "entityId" | "owner" | "permissionType"> => {
  const users = memberPermissionsList.filter(memberPermissions =>
    instanceOfUser(memberPermissions.member),
  );

  const userGroups = memberPermissionsList.filter(memberPermissions =>
    instanceOfUserGroup(memberPermissions.member),
  );

  const mapUsernames = (role: UserRole): string[] => {
    return users
      .filter(memberPermissions => memberPermissions.roleList.includes(role))
      .map(memberPermissions => (memberPermissions.member as User).username);
  };

  const mapGroupIds = (role: UserRole): number[] => {
    return userGroups
      .filter(memberPermissions => memberPermissions.roleList.includes(role))
      .map(memberPermissions => (memberPermissions.member as UserGroup).id);
  };

  return {
    manager: mapUsernames(UserRole.manager),
    writer: mapUsernames(UserRole.writer),
    reader: mapUsernames(UserRole.reader),
    readerGroupIds: mapGroupIds(UserRole.reader),
    writerGroupIds: mapGroupIds(UserRole.writer),
  };
};
