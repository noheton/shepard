import {
  instanceOfUser,
  instanceOfUserGroupV2,
  UserApi,
  UserGroupsApi,
  type Permissions,
  type User,
  type UserGroupV2,
} from "@dlr-shepard/backend-client";
import type { UpdatedPermissions } from "~/components/context/collection/edit-dialog/collectionEditTypes";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import { useV2ShepardApi } from "~/composables/common/api/useV2ShepardApi";
import type { MemberPermissions } from "./permissionTypes";
import { UserRole } from "./UserRole";

export async function mapPermissions(
  permissions: UpdatedPermissions,
  memberPermissionsList: Ref<MemberPermissions[] | undefined>,
) {
  const memberPermissions: MemberPermissions[] =
    memberPermissionsList.value || [];
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
    permissions?.readerGroupAppIds ?? undefined,
    UserRole.reader,
    memberPermissions,
  );
  await mapPermissionRoleUserGroups(
    permissions?.writerGroupAppIds ?? undefined,
    UserRole.writer,
    memberPermissions,
  );

  memberPermissionsList.value = memberPermissions;
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
  roleGroupAppIds: (string | null)[] | undefined,
  groupRole: UserRole,
  userPermissions: MemberPermissions[],
) {
  if (!roleGroupAppIds) return;
  await Promise.all(
    roleGroupAppIds.map(async appId => {
      if (!appId) return;
      const existing = userPermissions.find(
        userPermission =>
          instanceOfUserGroupV2(userPermission.member) &&
          userPermission.member.appId === appId,
      );
      if (existing) {
        existing.roleList.push(groupRole);
        return;
      }
      const userGroup: UserGroupV2 = await fetchUserGroupByAppId(appId);
      userPermissions.push({ member: userGroup, roleList: [groupRole] });
    }),
  );
}

async function fetchUserGroupByAppId(appId: string): Promise<UserGroupV2> {
  return useV2ShepardApi(UserGroupsApi).value.getUserGroupV2({ appId });
}

async function fetchUser(username: string) {
  const user = await useShepardApi(UserApi).value.getUser({
    username,
  });
  return user;
}

export const mapMemberPermissions = (
  memberPermissionsList: MemberPermissions[],
): Omit<Permissions, "entityId" | "owner" | "permissionType"> => {
  const users = memberPermissionsList.filter(memberPermissions =>
    instanceOfUser(memberPermissions.member),
  );

  const userGroups = memberPermissionsList.filter(memberPermissions =>
    instanceOfUserGroupV2(memberPermissions.member),
  );

  const mapUsernames = (role: UserRole): string[] => {
    return users
      .filter(memberPermissions => memberPermissions.roleList.includes(role))
      .map(memberPermissions => (memberPermissions.member as User).username);
  };

  const mapGroupAppIds = (role: UserRole): string[] => {
    return userGroups
      .filter(memberPermissions => memberPermissions.roleList.includes(role))
      .map(memberPermissions => (memberPermissions.member as UserGroupV2).appId)
      .filter((appId): appId is string => !!appId);
  };

  return {
    manager: mapUsernames(UserRole.manager),
    writer: mapUsernames(UserRole.writer),
    reader: mapUsernames(UserRole.reader),
    readerGroupAppIds: mapGroupAppIds(UserRole.reader),
    writerGroupAppIds: mapGroupAppIds(UserRole.writer),
  };
};
