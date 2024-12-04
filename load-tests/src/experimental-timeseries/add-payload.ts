import { check } from "k6";
import { Options } from "k6/options";
import {
  addExperimentalTimeseriesData,
  createTimeseriesContainer,
  deleteTimeseriesContainer,
} from "../utils/experimental-timeseries-helper";
import { generateTimeseries, getIdFromResponse } from "../utils/timeseries-helper";

export const options: Options = {
  scenarios: {
    default: {
      executor: "constant-vus",
      vus: 1,
      duration: "1m",
      // use function name here that should be executed
      // exec: "measuring_throughput_for_single_data_point",
      exec: "measuring_throughput_for_multiple_data_points",
    },
  },
};

export function setup(): { containerId: number } {
  const containerName = "load-test-" + Date.now();
  const response = createTimeseriesContainer(containerName);
  const containerId = getIdFromResponse(response.json());
  return { containerId };
}

export function teardown(data: { containerId: number }) {
  deleteTimeseriesContainer(data.containerId);
}

/**
 * Measuring the maximum throughput if we insert single data points as fast as possible.
 */
export function measuring_throughput_for_single_data_point(data: { containerId: number }) {
  const timeseries = generateTimeseries(1);
  const response = addExperimentalTimeseriesData(data.containerId, timeseries);
  check(response, { "add timeseries": (r) => r.status === 201 });
}

/**
 * Measuring the maximum throughput if we insert multiple data points as fast as possible.
 * The batch size can be adjustest for testing purpose.
 */

export function measuring_throughput_for_multiple_data_points(data: { containerId: number }) {
  const numberOfDataPointsPerBatch = 1000;
  const timeseries = generateTimeseries(numberOfDataPointsPerBatch);
  const response = addExperimentalTimeseriesData(data.containerId, timeseries);
  check(response, { "add timeseries": (r) => r.status === 201 });
}
