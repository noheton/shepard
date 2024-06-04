import {
  CollectionApi,
  type CreateCollectionRequest,
  type DeleteCollectionRequest,
  type EditCollectionPermissionsRequest,
  type ExportCollectionRequest,
  type GetAllCollectionsRequest,
  type GetCollectionPermissionsRequest,
  type GetCollectionRequest,
  type GetCollectionRolesRequest,
  type UpdateCollectionRequest,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class CollectionService {
  static getCollection(params: GetCollectionRequest) {
    const api = new CollectionApi(getConfiguration());
    return api.getCollection(params);
  }

  static getAllCollections(params: GetAllCollectionsRequest) {
    const api = new CollectionApi(getConfiguration());
    return api.getAllCollections(params);
  }

  static createCollection(params: CreateCollectionRequest) {
    const api = new CollectionApi(getConfiguration());
    return api.createCollection(params);
  }

  static updateCollection(params: UpdateCollectionRequest) {
    const api = new CollectionApi(getConfiguration());
    return api.updateCollection(params);
  }

  static deleteCollection(params: DeleteCollectionRequest) {
    const api = new CollectionApi(getConfiguration());
    return api.deleteCollection(params);
  }

  static getCollectionPermissions(params: GetCollectionPermissionsRequest) {
    const api = new CollectionApi(getConfiguration());
    return api.getCollectionPermissions(params);
  }

  static editCollectionPermissions(params: EditCollectionPermissionsRequest) {
    const api = new CollectionApi(getConfiguration());
    return api.editCollectionPermissions(params);
  }

  static getCollectionRoles(params: GetCollectionRolesRequest) {
    const api = new CollectionApi(getConfiguration());
    return api.getCollectionRoles(params);
  }

  static exportCollection(params: ExportCollectionRequest) {
    const api = new CollectionApi(getConfiguration());
    return api.exportCollection(params);
  }
}
