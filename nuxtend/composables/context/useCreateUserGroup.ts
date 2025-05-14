import {
  type PermissionType,
  SearchApi,
  UserGroupApi,
} from "@dlr-shepard/backend-client";

const userGroupSearchStringParam = (name: string) =>
  JSON.stringify({
    property: "name",
    value: name,
    operator: "eq",
  });

export async function createUserGroup(
  userGroupName: string,
  permissionType: PermissionType,
) {
  try {
    const searchResults = await createApiInstance(SearchApi).searchUserGroups({
      userSearchBody: {
        searchParams: {
          query: userGroupSearchStringParam(userGroupName),
        },
      },
    });

    if (searchResults.results && searchResults.results.length > 0) {
      handleError(
        `User group name "${userGroupName}" already exists`,
        "creating user group",
      );
      return null;
    }

    const createdUserGroup = await createApiInstance(
      UserGroupApi,
    ).createUserGroup({
      userGroup: {
        name: userGroupName,
        usernames: [],
      },
    });
    await createApiInstance(UserGroupApi).editUserGroupPermissions({
      userGroupId: createdUserGroup.id,
      permissions: {
        permissionType: permissionType,
        reader: [],
        writer: [],
        manager: [],
      },
    });

    emitSuccess(`Successfully created user group "${createdUserGroup.name}"`);
    return createdUserGroup;
  } catch (error) {
    handleError(error, "creating user group");
    return null;
  }
}
