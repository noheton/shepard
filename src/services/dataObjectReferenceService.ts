import {
  CreateDataObjectReferenceRequest,
  DataObjectReferenceApi,
  DeleteDataObjectReferenceRequest,
  GetAllDataObjectReferencesRequest,
  GetDataObjectReferencePayloadRequest,
  GetDataObjectReferenceRequest,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class DataObjectReferenceService {
  static getDataObjectReference(params: GetDataObjectReferenceRequest) {
    const api = new DataObjectReferenceApi(getConfiguration());
    return api.getDataObjectReference(params);
  }

  static getAllDataObjectReferences(params: GetAllDataObjectReferencesRequest) {
    const api = new DataObjectReferenceApi(getConfiguration());
    return api.getAllDataObjectReferences(params);
  }

  static createDataObjectReference(params: CreateDataObjectReferenceRequest) {
    const api = new DataObjectReferenceApi(getConfiguration());
    return api.createDataObjectReference(params);
  }

  static deleteDataObjectReference(params: DeleteDataObjectReferenceRequest) {
    const api = new DataObjectReferenceApi(getConfiguration());
    return api.deleteDataObjectReference(params);
  }

  static getDataObjectReferencePayload(
    params: GetDataObjectReferencePayloadRequest,
  ) {
    const api = new DataObjectReferenceApi(getConfiguration());
    return api.getDataObjectReferencePayload(params);
  }
}
