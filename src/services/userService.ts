import { UserApi, type GetUserRequest } from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class UserService {
  static getUser(params: GetUserRequest) {
    const api = new UserApi(getConfiguration());
    return api.getUser(params);
  }
  static getCurrentUser() {
    const api = new UserApi(getConfiguration());
    return api.getCurrentUser();
  }
}
