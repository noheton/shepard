import {
  UserGroupApi,
  type CreateUserGroupRequest,
  type DeleteUserGroupRequest,
  type EditUserGroupPermissionsRequest,
  type GetAllUserGroupsRequest,
  type GetUserGroupPermissionsRequest,
  type GetUserGroupRequest,
  type GetUserGroupRolesRequest,
  type UpdateUserGroupRequest,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class UserGroupService {
  static getUserGroup(params: GetUserGroupRequest) {
    const api = new UserGroupApi(getConfiguration());
    return api.getUserGroup(params);
  }

  static getAllUserGroups(params: GetAllUserGroupsRequest) {
    const api = new UserGroupApi(getConfiguration());
    return api.getAllUserGroups(params);
  }

  static createUserGroup(params: CreateUserGroupRequest) {
    const api = new UserGroupApi(getConfiguration());
    return api.createUserGroup(params);
  }

  static updateUserGroup(params: UpdateUserGroupRequest) {
    const api = new UserGroupApi(getConfiguration());
    return api.updateUserGroup(params);
  }

  static deleteUserGroup(params: DeleteUserGroupRequest) {
    const api = new UserGroupApi(getConfiguration());
    return api.deleteUserGroup(params);
  }

  static getUserGroupPermissions(params: GetUserGroupPermissionsRequest) {
    const api = new UserGroupApi(getConfiguration());
    return api.getUserGroupPermissions(params);
  }

  static editUserGroupPermissions(params: EditUserGroupPermissionsRequest) {
    const api = new UserGroupApi(getConfiguration());
    return api.editUserGroupPermissions(params);
  }
  static getUserGroupRoles(params: GetUserGroupRolesRequest) {
    const api = new UserGroupApi(getConfiguration());
    return api.getUserGroupRoles(params);
  }
}
