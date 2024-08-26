import {
  TimeseriesContainerApi,
  type CreateTimeseriesContainerRequest,
  type CreateTimeseriesRequest,
  type DeleteTimeseriesContainerRequest,
  type EditTimeseriesPermissionsRequest,
  type GetAllTimeseriesContainersRequest,
  type GetTimeseriesAvailableRequest,
  type GetTimeseriesContainerRequest,
  type GetTimeseriesPermissionsRequest,
  type GetTimeseriesRequest,
  type GetTimeseriesRolesRequest,
  type ImportTimeseriesRequest,
} from "@dlr-shepard/shepard-client";
import { getConfiguration } from "./serviceHelper";

export default class TimeseriesService {
  static createTimeseries(params: CreateTimeseriesRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.createTimeseries(params);
  }
  static getTimeseriesAvailable(params: GetTimeseriesAvailableRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.getTimeseriesAvailable(params);
  }
  static getTimeseries(params: GetTimeseriesRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.getTimeseries(params);
  }
  static getTimeseriesPermissions(params: GetTimeseriesPermissionsRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.getTimeseriesPermissions(params);
  }
  static editTimeseriesPermissions(params: EditTimeseriesPermissionsRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.editTimeseriesPermissions(params);
  }

  static createTimeseriesContainer(params: CreateTimeseriesContainerRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.createTimeseriesContainer(params);
  }
  static deleteTimeseriesContainer(params: DeleteTimeseriesContainerRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.deleteTimeseriesContainer(params);
  }
  static getTimeseriesContainer(params: GetTimeseriesContainerRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.getTimeseriesContainer(params);
  }
  static getAllTimeseriesContainers(params: GetAllTimeseriesContainersRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.getAllTimeseriesContainers(params);
  }

  static getTimeseriesRoles(params: GetTimeseriesRolesRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.getTimeseriesRoles(params);
  }

  static importTimeseries(params: ImportTimeseriesRequest) {
    const api = new TimeseriesContainerApi(getConfiguration());
    return api.importTimeseries(params);
  }
}
