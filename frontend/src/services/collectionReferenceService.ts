import {
  CollectionReferenceApi,
  type CreateCollectionReferenceRequest,
  type DeleteCollectionReferenceRequest,
  type GetAllCollectionReferencesRequest,
  type GetCollectionReferencePayloadRequest,
  type GetCollectionReferenceRequest,
} from "@/generated/openapi";
import { getConfiguration } from "./serviceHelper";

export default class CollectionReferenceService {
  static getCollectionReference(params: GetCollectionReferenceRequest) {
    const api = new CollectionReferenceApi(getConfiguration());
    return api.getCollectionReference(params);
  }

  static getAllCollectionReferences(params: GetAllCollectionReferencesRequest) {
    const api = new CollectionReferenceApi(getConfiguration());
    return api.getAllCollectionReferences(params);
  }

  static createCollectionReference(params: CreateCollectionReferenceRequest) {
    const api = new CollectionReferenceApi(getConfiguration());
    return api.createCollectionReference(params);
  }

  static deleteCollectionReference(params: DeleteCollectionReferenceRequest) {
    const api = new CollectionReferenceApi(getConfiguration());
    return api.deleteCollectionReference(params);
  }

  static getCollectionReferencePayload(
    params: GetCollectionReferencePayloadRequest,
  ) {
    const api = new CollectionReferenceApi(getConfiguration());
    return api.getCollectionReferencePayload(params);
  }
}
