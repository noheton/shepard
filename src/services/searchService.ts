import { SearchApi, type SearchRequest } from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class SearchService {
  static search(params: SearchRequest) {
    const api = new SearchApi(getConfiguration());
    return api.search(params);
  }
}
