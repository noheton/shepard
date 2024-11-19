import {
  DataObjectApi,
  type CreateDataObjectRequest,
  type DeleteDataObjectRequest,
  type GetAllDataObjectsRequest,
  type GetDataObjectRequest,
  type UpdateDataObjectRequest,
} from "@dlr-shepard/backend-client";
import { getConfiguration } from "./serviceHelper";

export default class DataObjectService {
  static getDataObject(params: GetDataObjectRequest) {
    const api = new DataObjectApi(getConfiguration());
    return api.getDataObject(params);
  }

  static getAllDataObjects(params: GetAllDataObjectsRequest) {
    const api = new DataObjectApi(getConfiguration());
    return api.getAllDataObjects(params);
  }

  static createDataObject(params: CreateDataObjectRequest) {
    const api = new DataObjectApi(getConfiguration());
    return api.createDataObject(params);
  }

  static updateDataObject(params: UpdateDataObjectRequest) {
    const api = new DataObjectApi(getConfiguration());
    return api.updateDataObject(params);
  }

  static deleteDataObject(params: DeleteDataObjectRequest) {
    const api = new DataObjectApi(getConfiguration());
    return api.deleteDataObject(params);
  }
}
