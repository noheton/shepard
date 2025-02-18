import { check } from "k6";
import { Options } from "k6/options";
import { addSpatialDataPoint, DatabaseType, deleteSpatialDataPointsByContainerId } from "./util/spatial-helper";

export const options: Options = {
  teardownTimeout: "5m",
  scenarios: {
    default: {
      executor: "constant-vus",
      vus: 1,
      duration: "1m",
      // use function name here that should be executed
      exec: "measuring_throughput_for_single_spatial_data_point_pgvector",
    },
  },
};

const NUMBER_OF_MEASUREMENTS = 100;

export function setup(): { containerId: number } {
  const containerId = Math.floor(Math.random() * 1000);
  return { containerId };
}

export function teardown(data: { containerId: number }) {
  const response = deleteSpatialDataPointsByContainerId(data.containerId);
  check(response, { "deleted spatial data points": (r) => r.status === 200 });
}

/**
 * Measuring the maximum throughput if we insert single data points as fast as possible.
 */
export function measuring_throughput_for_single_spatial_data_point_postgis(data: { containerId: number }) {
  const response = addSpatialDataPoint(data.containerId, DatabaseType.POSTGIS, NUMBER_OF_MEASUREMENTS);
  check(response, { "added spatial datapoint (PostGIS)": (r) => r.status === 200 });
}

export function measuring_throughput_for_single_spatial_data_point_pgvector(data: { containerId: number }) {
  const response = addSpatialDataPoint(data.containerId, DatabaseType.PGVECTOR, NUMBER_OF_MEASUREMENTS);
  check(response, { "added single spatial datapoint (PGVector)": (r) => r.status === 200 });
}
