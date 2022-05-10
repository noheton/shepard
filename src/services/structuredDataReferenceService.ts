import {
  CreateStructuredDataReferenceRequest,
  DeleteStructuredDataReferenceRequest,
  GetAllStructuredDataReferencesRequest,
  GetStructuredDataPayloadRequest,
  GetStructuredDataReferenceRequest,
  StructureddataReferenceApi,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class StructuredDataReferenceService {
  static createStructuredDataReference(
    params: CreateStructuredDataReferenceRequest,
  ) {
    const api = new StructureddataReferenceApi(getConfiguration());
    return api.createStructuredDataReference(params);
  }
  static deleteStructuredDataReference(
    params: DeleteStructuredDataReferenceRequest,
  ) {
    const api = new StructureddataReferenceApi(getConfiguration());
    return api.deleteStructuredDataReference(params);
  }
  static getStructuredDataReference(params: GetStructuredDataReferenceRequest) {
    const api = new StructureddataReferenceApi(getConfiguration());
    return api.getStructuredDataReference(params);
  }
  static getAllStructuredDataReferences(
    params: GetAllStructuredDataReferencesRequest,
  ) {
    const api = new StructureddataReferenceApi(getConfiguration());
    return api.getAllStructuredDataReferences(params);
  }

  static getStructuredDataPayload(params: GetStructuredDataPayloadRequest) {
    const api = new StructureddataReferenceApi(getConfiguration());
    return api.getStructuredDataPayload(params);
  }
}
