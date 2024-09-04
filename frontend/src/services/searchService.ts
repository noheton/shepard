import {
  SearchApi,
  type SearchContainersRequest,
  type SearchRequest,
  type SearchUsersRequest,
} from "@/generated/openapi";
import { getConfiguration } from "./serviceHelper";

export default class SearchService {
  static search(params: SearchRequest) {
    const api = new SearchApi(getConfiguration());
    return api.search(params);
  }
  static searchContainers(params: SearchContainersRequest) {
    const api = new SearchApi(getConfiguration());
    return api.searchContainers(params);
  }
  static searchUsers(params: SearchUsersRequest) {
    const api = new SearchApi(getConfiguration());
    return api.searchUsers(params);
  }
}
