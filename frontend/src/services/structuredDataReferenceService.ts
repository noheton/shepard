import {
  StructuredDataReferenceApi,
  type CreateStructuredDataReferenceRequest,
  type DeleteStructuredDataReferenceRequest,
  type GetAllStructuredDataReferencesRequest,
  type GetStructuredDataPayloadRequest,
  type GetStructuredDataReferenceRequest,
} from "@/generated/openapi";
import { getConfiguration } from "./serviceHelper";

export default class StructuredDataReferenceService {
  static createStructuredDataReference(
    params: CreateStructuredDataReferenceRequest,
  ) {
    const api = new StructuredDataReferenceApi(getConfiguration());
    return api.createStructuredDataReference(params);
  }
  static deleteStructuredDataReference(
    params: DeleteStructuredDataReferenceRequest,
  ) {
    const api = new StructuredDataReferenceApi(getConfiguration());
    return api.deleteStructuredDataReference(params);
  }
  static getStructuredDataReference(params: GetStructuredDataReferenceRequest) {
    const api = new StructuredDataReferenceApi(getConfiguration());
    return api.getStructuredDataReference(params);
  }
  static getAllStructuredDataReferences(
    params: GetAllStructuredDataReferencesRequest,
  ) {
    const api = new StructuredDataReferenceApi(getConfiguration());
    return api.getAllStructuredDataReferences(params);
  }

  static getStructuredDataPayload(params: GetStructuredDataPayloadRequest) {
    const api = new StructuredDataReferenceApi(getConfiguration());
    return api.getStructuredDataPayload(params);
  }
}
