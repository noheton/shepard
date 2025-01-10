import { JSONValue } from "k6";
import http from "k6/http";
import { FormData } from "../utils/form-data";
import { buildParamsWithApiKey } from "../utils/uri";
import { TimeseriesData, TimeseriesDataPoint, TimeseriesObject } from "./timeseries-types";
import { buildUri } from "./uri";

export const timeseriesUrl = buildUri("/shepard/api/timeseriesContainers");
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

/*
  Helper Functions
*/

export function getIdFromResponse(response: JSONValue): number {
  if (response != null && typeof response == "object" && "id" in response) {
    return response.id as number;
  }
  console.error(JSON.stringify(response));
  throw new Error("Response body does not contain property 'id'.");
}

export function generateTimeseries(numberOfDataPoints: number): TimeseriesData {
  return {
    timeseries: {
      measurement: "testmeasurement",
      device: "device",
      location: "location",
      symbolicName: "symbolicName",
      field: "field",
    },
    points: generateDataPoints(numberOfDataPoints),
  };
}

export function generateDataPoints(numberOfDataPoints: number): TimeseriesDataPoint[] {
  const dataPoints = new Array<TimeseriesDataPoint>(numberOfDataPoints);
  for (let i = 0; i < numberOfDataPoints; i++) {
    const nanoseconds = new Date().getTime() * 1000 * 1000; // timestamp in nanoseconds
    dataPoints[i] = { timestamp: nanoseconds + i, value: i };
  }
  return dataPoints;
}

export function generateCsvString(
  numberOfRows: number,
  measurement: TimeseriesObject,
  generateHeader: boolean,
): string {
  const headers = "DEVICE,FIELD,LOCATION,MEASUREMENT,SYMBOLICNAME,TIMESTAMP,VALUE";

  let timeStamp = 1708067683056880000;
  let value = 22.0;

  let csvString = "";
  if (generateHeader) csvString = headers + "\n";

  for (var i = 0; i < numberOfRows; i++) {
    timeStamp += 1;
    value += 1;
    csvString += `${measurement.device},${measurement.field},${measurement.location},${measurement.measurement},${measurement.symbolicName},${timeStamp},${value}\n`;
  }
  return csvString;
}

export function createTestMeasurement(measurement: string): TimeseriesObject {
  return {
    measurement: measurement,
    device: "device",
    location: "location",
    symbolicName: "symbolicName",
    field: "field",
  } as TimeseriesObject;
}
