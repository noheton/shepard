import {
  CreateUriReferenceRequest,
  DeleteUriReferenceRequest,
  GetAllUriReferencesRequest,
  GetUriReferenceRequest,
  UriReferenceApi,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class UriReferenceService {
  static getUriReference(params: GetUriReferenceRequest) {
    const api = new UriReferenceApi(getConfiguration());
    return api.getUriReference(params);
  }

  static getAllUriReferences(params: GetAllUriReferencesRequest) {
    const api = new UriReferenceApi(getConfiguration());
    return api.getAllUriReferences(params);
  }

  static createUriReference(params: CreateUriReferenceRequest) {
    const api = new UriReferenceApi(getConfiguration());
    return api.createUriReference(params);
  }

  static deleteUriReference(params: DeleteUriReferenceRequest) {
    const api = new UriReferenceApi(getConfiguration());
    return api.deleteUriReference(params);
  }
}
