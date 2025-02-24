import { check } from "k6";
import { Options } from "k6/options";
import { getIdFromResponse } from "../utils/container-helper";
import { addSpatialDataPoint, createSpatialDataContainer, deleteSpatialDataContainer } from "./util/spatial-helper";

export const options: Options = {
  teardownTimeout: "5m",
  scenarios: {
    default: {
      executor: "constant-vus",
      vus: 1,
      duration: "1m",
      // use function name here that should be executed
      exec: "measuring_throughput_for_single_spatial_data_point",
    },
  },
};

const NUMBER_OF_MEASUREMENTS = 100;

export function setup(): { containerId: number } {
  const containerName = "load-test-" + Date.now();
  const response = createSpatialDataContainer(containerName);
  const containerId = getIdFromResponse(response.json());

  return { containerId };
}

export function teardown(data: { containerId: number }) {
  const response = deleteSpatialDataContainer(data.containerId);
  check(response, { "deleted spatial data container": (r) => r.status === 200 });
}

/**
 * Measuring the maximum throughput if we insert single data points as fast as possible.
 */
export function measuring_throughput_for_single_spatial_data_point(data: { containerId: number }) {
  const response = addSpatialDataPoint(data.containerId, NUMBER_OF_MEASUREMENTS);
  check(response, { "added spatial datapoint (PostGIS)": (r) => r.status === 200 });
}
