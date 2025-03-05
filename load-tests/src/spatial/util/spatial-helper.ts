import http from "k6/http";
import { buildParamsWithApiKey, buildUri } from "../../utils/uri";
// @ts-ignore Import module
import { URL } from "https://jslib.k6.io/url/1.0.0/index.js";
import { fail } from "k6";
import { generateMultipleSpatialDataPoints, generateSingleRandomSpatialDataPoint } from "./generate-spatial";
import {
  createBoundingBoxFilter,
  createBoundingSphereFilter,
  createKNearestNeighborFilter,
  Metadata,
} from "./spatial-types";

export const spatialDataContainerUri = buildUri("/shepard/api/spatialDataContainers");
const params = buildParamsWithApiKey();

export function addSpatialDataPoint(containerId: number, numOfMeasurements: number = 100) {
  const dataPoint = generateSingleRandomSpatialDataPoint(numOfMeasurements);
  const spatialDataPayloadURL = spatialDataContainerUri + `/${containerId}/payload`;
  const spatialURL = buildUrlWithQueryParams(spatialDataPayloadURL, null);
  return http.post(spatialURL.toString(), JSON.stringify([dataPoint]), params);
}

export function addManySpatialDataPoints(containerId: number, numberOfPoints: number, numOfMeasurements: number = 100) {
  const dataPoints = generateMultipleSpatialDataPoints(numberOfPoints, numOfMeasurements);
  const spatialDataPayloadURL = spatialDataContainerUri + `/${containerId}/payload`;
  const spatialURL = buildUrlWithQueryParams(spatialDataPayloadURL, null);
  return http.post(spatialURL.toString(), JSON.stringify(dataPoints), params);
}

export function deleteSpatialDataPointsByContainerId(containerId: number) {
  const spatialDataPayloadURL = spatialDataContainerUri + `/${containerId}`;
  const spatialDeleteURL = buildUrlWithQueryParams(spatialDataPayloadURL, null);
  return http.del(spatialDeleteURL.toString(), null, params);
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

  const queryParamMap = new Map<string, string>();
  queryParamMap.set("geometryFilter", JSON.stringify(boundingBoxFilter));
  queryParamMap.set("metadataFilter", JSON.stringify(metadata));

  const spatialDataPayloadURL = spatialDataContainerUri + `/${containerId}/payload`;
  const spatialURL = buildUrlWithQueryParams(spatialDataPayloadURL, queryParamMap);

  return http.get(spatialURL.toString(), params);
}

export function filterByBoundingSphere(
  containerId: number,
  radius: number,
  centerX: number,
  centerY: number,
  centerZ: number,
  metadata?: Metadata,
) {
  const boundingSphereFilter = createBoundingSphereFilter(radius, centerX, centerY, centerZ);

  const queryParamMap = new Map<string, string>();
  queryParamMap.set("geometryFilter", JSON.stringify(boundingSphereFilter));
  queryParamMap.set("metadataFilter", JSON.stringify(metadata));

  const spatialDataPayloadURL = spatialDataContainerUri + `/${containerId}/payload`;
  const spatialURL = buildUrlWithQueryParams(spatialDataPayloadURL, queryParamMap);
  return http.get(spatialURL.toString(), params);
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

  const queryParamMap = new Map<string, string>();
  queryParamMap.set("geometryFilter", JSON.stringify(knnFilter));
  queryParamMap.set("metadataFilter", JSON.stringify(metadata));

  const spatialDataPayloadURL = spatialDataContainerUri + `/${containerId}/payload`;
  const spatialURL = buildUrlWithQueryParams(spatialDataPayloadURL, queryParamMap);
  return http.get(spatialURL.toString(), params);
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
    if (value) {
      url.searchParams.set(key, value);
    }
  }
  return url;
}

export function createSpatialDataContainer(name: string) {
  const payload = JSON.stringify({ name: name });
  return http.post(spatialDataContainerUri, payload, params);
}

export function deleteSpatialDataContainer(containerId: number) {
  return http.del(spatialDataContainerUri + "/" + containerId, null, params);
}

export function uploadBulkSpatialData(
  containerId: number,
  numberOfDataPoints: number,
  batchSize: number,
  numberOfMeasurements: number,
) {
  console.info("Using container ID:", containerId);

  if (numberOfDataPoints <= batchSize) {
    addManySpatialDataPoints(containerId, numberOfDataPoints, numberOfMeasurements);
  } else {
    for (let i = 0; i < Math.floor(numberOfDataPoints / batchSize); i++) {
      console.info(`Uploading datapoint batch ${i + 1} from ${Math.floor(numberOfDataPoints / batchSize)}`);
      const result = addManySpatialDataPoints(containerId, batchSize, numberOfMeasurements);
      if (result.status !== 200) {
        console.error("Could not setup/create datapoints for filtering load test!");
        fail("could not upload data point");
      }
    }
  }
  console.info(`Created ${numberOfDataPoints} spatial datapoints`);
}
