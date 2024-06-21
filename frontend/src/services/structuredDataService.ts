import {
  StructureddataApi,
  type CreateStructuredDataContainerRequest,
  type CreateStructuredDataRequest,
  type DeleteStructuredDataContainerRequest,
  type DeleteStructuredDataRequest,
  type EditStructuredDataPermissionsRequest,
  type GetAllStructuredDataContainersRequest,
  type GetAllStructuredDatasRequest,
  type GetStructuredDataContainerRequest,
  type GetStructuredDataPermissionsRequest,
  type GetStructuredDataRequest,
  type GetStructuredDataRolesRequest,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class StructuredDataService {
  static createStructuredData(params: CreateStructuredDataRequest) {
    const api = new StructureddataApi(getConfiguration());
    return api.createStructuredData(params);
  }

  static deleteStructuredData(params: DeleteStructuredDataRequest) {
    const api = new StructureddataApi(getConfiguration());
    return api.deleteStructuredData(params);
  }

  static getStructuredData(params: GetStructuredDataRequest) {
    const api = new StructureddataApi(getConfiguration());
    return api.getStructuredData(params);
  }

  static getAllStructuredDatas(params: GetAllStructuredDatasRequest) {
    const api = new StructureddataApi(getConfiguration());
    return api.getAllStructuredDatas(params);
  }

  static getStructuredDataPermissions(
    params: GetStructuredDataPermissionsRequest,
  ) {
    const api = new StructureddataApi(getConfiguration());
    return api.getStructuredDataPermissions(params);
  }

  static editStructuredDataPermissions(
    params: EditStructuredDataPermissionsRequest,
  ) {
    const api = new StructureddataApi(getConfiguration());
    return api.editStructuredDataPermissions(params);
  }

  static createStructuredDataContainer(
    params: CreateStructuredDataContainerRequest,
  ) {
    const api = new StructureddataApi(getConfiguration());
    return api.createStructuredDataContainer(params);
  }

  static deleteStructuredDataContainer(
    params: DeleteStructuredDataContainerRequest,
  ) {
    const api = new StructureddataApi(getConfiguration());
    return api.deleteStructuredDataContainer(params);
  }

  static getStructuredDataContainer(params: GetStructuredDataContainerRequest) {
    const api = new StructureddataApi(getConfiguration());
    return api.getStructuredDataContainer(params);
  }

  static getAllStructuredDataContainers(
    params: GetAllStructuredDataContainersRequest,
  ) {
    const api = new StructureddataApi(getConfiguration());
    return api.getAllStructuredDataContainers(params);
  }

  static getStructuredDataRoles(params: GetStructuredDataRolesRequest) {
    const api = new StructureddataApi(getConfiguration());
    return api.getStructuredDataRoles(params);
  }
}
