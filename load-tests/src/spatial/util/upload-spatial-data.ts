import { Options } from "k6/options";
import { addManySpatialDataPoints, DatabaseType } from "./spatial-helper";
import { fail } from "k6";

const NUMBER_OF_DATAPOINTS = 10000000;
const NUMBER_OF_MEASUREMENTS = 2;
const UPLOAD_BATCH_SIZE = 500;
const DB_TYPE = DatabaseType.BOTH;
const CONTAINER_ID = 12345678;

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

export function uploadData() {
  console.info("Using container ID:", CONTAINER_ID);

  if (NUMBER_OF_DATAPOINTS <= UPLOAD_BATCH_SIZE) {
    addManySpatialDataPoints(CONTAINER_ID, DB_TYPE, NUMBER_OF_DATAPOINTS, NUMBER_OF_MEASUREMENTS);
  } else {
    for (let i = 0; i < Math.floor(NUMBER_OF_DATAPOINTS / UPLOAD_BATCH_SIZE); i++) {
      console.info(`Uploading datapoint batch ${i + 1} from ${Math.floor(NUMBER_OF_DATAPOINTS / UPLOAD_BATCH_SIZE)}`);
      const result = addManySpatialDataPoints(CONTAINER_ID, DB_TYPE, UPLOAD_BATCH_SIZE, NUMBER_OF_MEASUREMENTS);
      if (result.status !== 200) {
        console.error("Could not setup/create datapoints for filtering load test!");
        fail("could not upload data point");
      }
    }
  }
  console.info(`Created ${NUMBER_OF_DATAPOINTS} spatial datapoints`);
}
