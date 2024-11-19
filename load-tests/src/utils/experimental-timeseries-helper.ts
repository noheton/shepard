import http from "k6/http";
import { FormData } from "./form-data";
import { TimeseriesData } from "./timeseries-types";
import { buildParamsWithApiKey, buildUri } from "./uri";

export const timeseriesUrl = buildUri("/shepard/api/experimental-timeseriesContainers");
const params = buildParamsWithApiKey();

/*
  Endpoint-related Functions 
*/

export function createTimeseriesContainer(name: string) {
  const payload = JSON.stringify({ name: name });
  return http.post(timeseriesUrl, payload, params);
}

export function deleteTimeseriesContainer(containerId: number) {
  return http.del(timeseriesUrl + "/" + containerId, null, params);
}

export function addTimeseriesData(containerId: number, timeseries: TimeseriesData) {
  const timeseriesDataUrl = timeseriesUrl + `/${containerId}/payload`;
  return http.post(timeseriesDataUrl, JSON.stringify(timeseries), params);
}

export function importTimeseriesCSV(containerId: number, csvString: string, filename: string = "file.csv") {
  const fd = new FormData();
  fd.append("file", http.file(csvString, filename, "text/csv"));

  if (params.headers !== undefined) {
    params.headers["Content-Type"] = "multipart/form-data; boundary=" + fd.boundary;
  }

  const importURL = timeseriesUrl + `/${containerId}/import`;
  return http.post(importURL, fd.body(), params);
}
