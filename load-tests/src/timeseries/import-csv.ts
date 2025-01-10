import { check } from "k6";
import { Options } from "k6/options";
import {
  createTestMeasurement,
  createTimeseriesContainer,
  deleteTimeseriesContainer,
  generateCsvString,
  getIdFromResponse,
  importTimeseriesCSV,
} from "../utils/timeseries-helper";

export const options: Options = {
  scenarios: {
    default: {
      executor: "constant-vus",
      vus: 1,
      duration: "1m",
      exec: "importCsv",
    },
  },
};

const NUMBER_OF_LINES = 1000;

export function setup() {
  const containerName = "load-test-" + Date.now();
  const response = createTimeseriesContainer(containerName);
  const containerId = getIdFromResponse(response.json());

  const csvString1 = generateCsvString(NUMBER_OF_LINES, createTestMeasurement("measurement1"), true);
  const csvString = csvString1 + generateCsvString(NUMBER_OF_LINES, createTestMeasurement("measurement2"), false);

  return { containerId, csvString };
}

export function teardown(data: { containerId: number }) {
  deleteTimeseriesContainer(data.containerId);
}

export function importCsv(data: { containerId: number; csvString: string }) {
  const response = importTimeseriesCSV(data.containerId, data.csvString);
  check(response, { "import timeseries CSV": (r) => r.status === 200 });
}
