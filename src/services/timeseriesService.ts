import {
  CreateTimeseriesContainerRequest,
  CreateTimeseriesRequest,
  DeleteTimeseriesContainerRequest,
  EditTimeseriesPermissionsRequest,
  GetAllTimeseriesContainersRequest,
  GetTimeseriesAvailableRequest,
  GetTimeseriesContainerRequest,
  GetTimeseriesPermissionsRequest,
  GetTimeseriesRolesRequest,
  TimeseriesApi,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class TimeseriesService {
  static createTimeseries(params: CreateTimeseriesRequest) {
    const api = new TimeseriesApi(getConfiguration());
    return api.createTimeseries(params);
  }
  static getTimeseriesAvailable(params: GetTimeseriesAvailableRequest) {
    const api = new TimeseriesApi(getConfiguration());
    return api.getTimeseriesAvailable(params);
  }
  static getTimeseriesPermissions(params: GetTimeseriesPermissionsRequest) {
    const api = new TimeseriesApi(getConfiguration());
    return api.getTimeseriesPermissions(params);
  }
  static editTimeseriesPermissions(params: EditTimeseriesPermissionsRequest) {
    const api = new TimeseriesApi(getConfiguration());
    return api.editTimeseriesPermissions(params);
  }

  static createTimeseriesContainer(params: CreateTimeseriesContainerRequest) {
    const api = new TimeseriesApi(getConfiguration());
    return api.createTimeseriesContainer(params);
  }
  static deleteTimeseriesContainer(params: DeleteTimeseriesContainerRequest) {
    const api = new TimeseriesApi(getConfiguration());
    return api.deleteTimeseriesContainer(params);
  }
  static getTimeseriesContainer(params: GetTimeseriesContainerRequest) {
    const api = new TimeseriesApi(getConfiguration());
    return api.getTimeseriesContainer(params);
  }
  static getAllTimeseriesContainers(params: GetAllTimeseriesContainersRequest) {
    const api = new TimeseriesApi(getConfiguration());
    return api.getAllTimeseriesContainers(params);
  }

  static getTimeseriesRoles(params: GetTimeseriesRolesRequest) {
    const api = new TimeseriesApi(getConfiguration());
    return api.getTimeseriesRoles(params);
  }
}
