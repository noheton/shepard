import http from "k6/http";
import { buildParamsWithApiKey, buildUri } from "../../utils/uri";
// @ts-ignore Import module
import { URL } from "https://jslib.k6.io/url/1.0.0/index.js";
import { generateMultipleSpatialDataPoints, generateSingleRandomSpatialDataPoint } from "./generate-spatial";
import {
  createBoundingBoxFilter,
  createBoundingSphereFilter,
  createKNearestNeighborFilter,
  Metadata,
  SpatialFilter,
} from "./spatial-types";

export const spatialUri = buildUri("/shepard/api/spatialDataContainer");
const params = buildParamsWithApiKey();

export function addSpatialDataPoint(containerId: number, numOfMeasurements: number = 100) {
  const dataPoint = generateSingleRandomSpatialDataPoint(numOfMeasurements);
  const spatialDataPayloadURL = spatialUri + `/${containerId}/payload`;
  const spatialURL = buildUrlWithQueryParams(spatialDataPayloadURL, null);
  return http.patch(spatialURL.toString(), JSON.stringify([dataPoint]), params);
}

export function addManySpatialDataPoints(containerId: number, numberOfPoints: number, numOfMeasurements: number = 100) {
  const dataPoints = generateMultipleSpatialDataPoints(numberOfPoints, numOfMeasurements);
  const spatialDataPayloadURL = spatialUri + `/${containerId}/payload`;
  const spatialURL = buildUrlWithQueryParams(spatialDataPayloadURL, null);
  return http.patch(spatialURL.toString(), JSON.stringify(dataPoints), params);
}

export function deleteSpatialDataPointsByContainerId(containerId: number) {
  const spatialDataPayloadURL = spatialUri + `/${containerId}`;
  const spatialDeletelURL = buildUrlWithQueryParams(spatialDataPayloadURL, null);
  return http.del(spatialDeletelURL.toString(), null, params);
}

export function filterByBoundingBox(
  containerId: number,
  minX: number,
  minY: number,
  minZ: number,
  maxX: number,
  maxY: number,
  maxZ: number,
  metadata?: Metadata,
) {
  const boundingBoxFilter = createBoundingBoxFilter(minX, minY, minZ, maxX, maxY, maxZ);

  const filter: SpatialFilter = {
    geometryFilter: boundingBoxFilter,
    metadata: metadata,
  };

  const spatialDataPayloadURL = spatialUri + `/${containerId}/payload`;
  const spatialURL = buildUrlWithQueryParams(spatialDataPayloadURL, null);
  return http.post(spatialURL.toString(), JSON.stringify(filter), params);
}

export function filterByBoundingSphere(
  containerId: number,
  r: number,
  centerX: number,
  centerY: number,
  centerZ: number,
  metadata?: Metadata,
) {
  const boundingSphereFilter = createBoundingSphereFilter(r, centerX, centerY, centerZ);

  const filter: SpatialFilter = {
    geometryFilter: boundingSphereFilter,
    metadata: metadata,
  };

  console.info(filter);

  const spatialDataPayloadURL = spatialUri + `/${containerId}/payload`;
  const spatialURL = buildUrlWithQueryParams(spatialDataPayloadURL, null);
  return http.post(spatialURL.toString(), JSON.stringify(filter), params);
}

export function filterByKNearestNeighbor(
  containerId: number,
  k: number,
  x: number,
  y: number,
  z: number,
  metadata?: Metadata,
) {
  const knnFilter = createKNearestNeighborFilter(k, x, y, z);

  const filter: SpatialFilter = {
    geometryFilter: knnFilter,
    metadata: metadata,
  };

  const spatialDataPayloadURL = spatialUri + `/${containerId}/payload`;
  const spatialURL = buildUrlWithQueryParams(spatialDataPayloadURL, null);
  return http.post(spatialURL.toString(), JSON.stringify(filter), params);
}

/**
 * This function returns a proper URL object for a passed URL-string and adds a Map of query parameters to the URL object.
 * @returns URL object
 */
function buildUrlWithQueryParams(apiUri: string, queryParams: Map<string, string> | undefined | null): URL {
  const url = new URL(apiUri);
  if (!queryParams) {
    return url;
  }
  for (let [key, value] of queryParams) {
    url.searchParams.set(key, value);
  }
  return url;
}
