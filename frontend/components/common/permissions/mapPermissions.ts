import {
  instanceOfUser,
  instanceOfUserGroup,
  UserApi,
  type Permissions,
  type User,
  type UserGroup,
} from "@dlr-shepard/backend-client";
import type { UpdatedPermissions } from "~/components/context/collection/edit-dialog/collectionEditTypes";
import { useShepardApi } from "~/composables/common/api/useShepardApi";
import {
  useUserGroupsV2,
  type UserGroupV2,
} from "~/composables/context/useUserGroupsV2";
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
          instanceOfUserGroup(userPermission.member) &&
          userPermission.member.appId === appId,
      );
      if (existing) {
        existing.roleList.push(groupRole);
        return;
      }
      const userGroup: UserGroup = await fetchUserGroupByAppId(appId);
      userPermissions.push({ member: userGroup, roleList: [groupRole] });
    }),
  );
}

function v2ToUserGroup(v2: UserGroupV2): UserGroup {
  return {
    id: 0,
    name: v2.name,
    appId: v2.appId,
    createdAt: v2.createdAt ? new Date(v2.createdAt) : new Date(0),
    createdBy: v2.createdBy ?? "",
    updatedAt: v2.updatedAt != null ? new Date(v2.updatedAt) : null,
    updatedBy: v2.updatedBy ?? null,
    usernames: v2.usernames ?? [],
  };
}

async function fetchUserGroupByAppId(appId: string): Promise<UserGroup> {
  const v2Group = await useUserGroupsV2().getUserGroup(appId);
  return v2ToUserGroup(v2Group);
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
    instanceOfUserGroup(memberPermissions.member),
  );

  const mapUsernames = (role: UserRole): string[] => {
    return users
      .filter(memberPermissions => memberPermissions.roleList.includes(role))
      .map(memberPermissions => (memberPermissions.member as User).username);
  };

  const mapGroupAppIds = (role: UserRole): string[] => {
    return userGroups
      .filter(memberPermissions => memberPermissions.roleList.includes(role))
      .map(memberPermissions => (memberPermissions.member as UserGroup).appId)
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
