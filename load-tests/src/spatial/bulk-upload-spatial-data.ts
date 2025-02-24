import { check } from "k6";
import { Options } from "k6/options";
import { getIdFromResponse } from "../utils/container-helper";
import { createSpatialDataContainer, deleteSpatialDataContainer, uploadBulkSpatialData } from "./util/spatial-helper";

const NUMBER_OF_DATAPOINTS = 10000;
const NUMBER_OF_MEASUREMENTS = 2;
const UPLOAD_BATCH_SIZE = 500;

export const options: Options = {
  scenarios: {
    default: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 1,
      maxDuration: "490m", // 8h
      exec: "uploadData",
    },
  },
};

export function setup(): { containerId: number } {
  const containerName = "bulk-load-test-" + Date.now();
  const response = createSpatialDataContainer(containerName);
  const containerId = getIdFromResponse(response.json());

  return { containerId };
}

export function teardown(data: { containerId: number }) {
  const response = deleteSpatialDataContainer(data.containerId);
  check(response, { "deleted spatial data container": (r) => r.status === 200 });
}

export function uploadData(data: { containerId: number }) {
  uploadBulkSpatialData(data.containerId, NUMBER_OF_DATAPOINTS, UPLOAD_BATCH_SIZE, NUMBER_OF_MEASUREMENTS);
}
