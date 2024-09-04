import {
  TimeseriesReferenceApi,
  type CreateTimeseriesReferenceRequest,
  type DeleteTimeseriesReferenceRequest,
  type ExportTimeseriesPayloadRequest,
  type GetAllTimeseriesReferencesRequest,
  type GetTimeseriesPayloadRequest,
  type GetTimeseriesReferenceRequest,
} from "@/generated/openapi";
import { getConfiguration } from "./serviceHelper";

export default class TimeseriesReferenceService {
  static getTimeseriesReference(params: GetTimeseriesReferenceRequest) {
    const api = new TimeseriesReferenceApi(getConfiguration());
    return api.getTimeseriesReference(params);
  }

  static getAllTimeseriesReferences(params: GetAllTimeseriesReferencesRequest) {
    const api = new TimeseriesReferenceApi(getConfiguration());
    return api.getAllTimeseriesReferences(params);
  }

  static createTimeseriesReference(params: CreateTimeseriesReferenceRequest) {
    const api = new TimeseriesReferenceApi(getConfiguration());
    return api.createTimeseriesReference(params);
  }

  static deleteTimeseriesReference(params: DeleteTimeseriesReferenceRequest) {
    const api = new TimeseriesReferenceApi(getConfiguration());
    return api.deleteTimeseriesReference(params);
  }

  static exportTimeseriesPayload(params: ExportTimeseriesPayloadRequest) {
    const api = new TimeseriesReferenceApi(getConfiguration());
    return api.exportTimeseriesPayload(params);
  }

  static getTimeseriesPayload(params: GetTimeseriesPayloadRequest) {
    const api = new TimeseriesReferenceApi(getConfiguration());
    return api.getTimeseriesPayload(params);
  }
}
