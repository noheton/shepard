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
    permissions?.readerGroupIds,
    permissions?.readerGroupAppIds ?? undefined,
    UserRole.reader,
    memberPermissions,
  );
  await mapPermissionRoleUserGroups(
    permissions?.writerGroupIds,
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
  roleUserGroupIds: number[] | undefined,
  roleGroupAppIds: (string | null)[] | undefined,
  groupRole: UserRole,
  userPermissions: MemberPermissions[],
) {
  if (!roleUserGroupIds) return;
  await Promise.all(
    roleUserGroupIds.map(async (roleUserGroupId, idx) => {
      const existing = userPermissions.find(
        userPermission =>
          instanceOfUserGroup(userPermission.member) &&
          userPermission.member.id === roleUserGroupId,
      );
      if (existing) {
        existing.roleList.push(groupRole);
        return;
      }
      const appId = roleGroupAppIds?.[idx] ?? null;
      const userGroup: UserGroup = appId
        ? await fetchUserGroupByAppId(appId, roleUserGroupId)
        : await fetchUserGroup(roleUserGroupId);
      userPermissions.push({ member: userGroup, roleList: [groupRole] });
    }),
  );
}

function v2ToUserGroup(v2: UserGroupV2, numericId: number): UserGroup {
  return {
    id: numericId,
    name: v2.name,
    appId: v2.appId,
    createdAt: v2.createdAt ? new Date(v2.createdAt) : new Date(0),
    createdBy: v2.createdBy ?? "",
    updatedAt: v2.updatedAt != null ? new Date(v2.updatedAt) : null,
    updatedBy: v2.updatedBy ?? null,
    usernames: v2.usernames ?? [],
  };
}

async function fetchUserGroupByAppId(
  appId: string,
  numericId: number,
): Promise<UserGroup> {
  const v2Group = await useUserGroupsV2().getUserGroup(appId);
  return v2ToUserGroup(v2Group, numericId);
}

async function fetchUser(username: string) {
  const user = await useShepardApi(UserApi).value.getUser({
    username,
  });
  return user;
}
async function fetchUserGroup(groupId: number) {
  // V1-EXCEPTION (legacy fallback only): used only when `readerGroupAppIds` /
  // `writerGroupAppIds` is absent or null for this index (groups without a UUID
  // backfill). Servers running V2-SWEEP-002-4 always populate the appId arrays,
  // so this path is exercised only against older server versions.
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
