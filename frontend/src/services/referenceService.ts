import {
  ReferenceApi,
  type GetAllReferencesRequest,
} from "@dlr-shepard/backend-client";
import { getConfiguration } from "./serviceHelper";

export default class ReferenceService {
  static getAllReferences(params: GetAllReferencesRequest) {
    const api = new ReferenceApi(getConfiguration());
    return api.getAllReferences(params);
  }
}
