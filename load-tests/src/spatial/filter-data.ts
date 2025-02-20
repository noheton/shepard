import { check } from "k6";
import { Options } from "k6/options";
import { filterByBoundingBox, filterByBoundingSphere, filterByKNearestNeighbor } from "./util/spatial-helper";
import { Metadata } from "./util/spatial-types";

export const options: Options = {
  setupTimeout: "30m",
  scenarios: {
    default: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 10,
      // use function name here that should be executed
      // Options:
      // measure_bounding_box_filter || measure_bounding_box_filter_with_metadata
      // measure_bounding_sphere_filter
      // measure_knn_filter
      exec: "measure_knn_filter",
    },
  },
};

const BOUNDING_BOX = {
  minX: 0.0,
  minY: 0.0,
  minZ: 0.0,
  maxX: 5.1,
  maxY: 5.1,
  maxZ: 5.1,
};

const BOUNDING_SPHERE = {
  r: 6,
  centerX: 5,
  centerY: 5,
  centerZ: 5,
};

const KNN = {
  k: 500,
  x: 3,
  y: 3,
  z: 3,
};

export function setup(): { containerId: number } {
  //Using this specific container ID, since we uploaded data with the 'upload-spatial-data' script with this container id
  //const containerId = 123456789;
  const containerId = 2;
  return { containerId };
}

/* Bounding Box */
export function measure_bounding_box_filter(data: { containerId: number }) {
  const response = filterByBoundingBox(
    data.containerId,
    BOUNDING_BOX.minX,
    BOUNDING_BOX.minY,
    BOUNDING_BOX.minZ,
    BOUNDING_BOX.maxX,
    BOUNDING_BOX.maxY,
    BOUNDING_BOX.maxZ,
    undefined,
  );

  const responseBody = JSON.parse(String(response.body));
  if (Array.isArray(responseBody)) {
    console.log("Response length:", responseBody.length);
  }

  check(response, { "filtered spatial datapoints by bounding box": (r) => r.status === 200 });
}

export function measure_bounding_box_filter_with_metadata(data: { containerId: number }) {
  const metadataFilter: Metadata = {
    isSuccess: true,
  };
  const response = filterByBoundingBox(
    data.containerId,
    BOUNDING_BOX.minX,
    BOUNDING_BOX.minY,
    BOUNDING_BOX.minZ,
    BOUNDING_BOX.maxX,
    BOUNDING_BOX.maxY,
    BOUNDING_BOX.maxZ,
    metadataFilter,
  );
  const responseBody = JSON.parse(String(response.body));
  if (Array.isArray(responseBody)) {
    console.log("Response length:", responseBody.length);
  }
  check(response, { "filtered spatial datapoints by bounding box and metadata": (r) => r.status === 200 });
}

/* Bounding Sphere */
export function measure_bounding_sphere_filter(data: { containerId: number }) {
  const response = filterByBoundingSphere(
    data.containerId,
    BOUNDING_SPHERE.r,
    BOUNDING_SPHERE.centerX,
    BOUNDING_SPHERE.centerY,
    BOUNDING_SPHERE.centerZ,
    undefined,
  );

  const responseBody = JSON.parse(String(response.body));
  if (Array.isArray(responseBody)) {
    console.log("Response length:", responseBody.length);
  }

  check(response, { "filtered spatial datapoints by bounding sphere": (r) => r.status === 200 });
}

/* k Nearest Neighbor */
export function measure_knn_filter(data: { containerId: number }) {
  const response = filterByKNearestNeighbor(data.containerId, KNN.k, KNN.x, KNN.y, KNN.z, undefined);
  const responseBody = JSON.parse(String(response.body));
  if (Array.isArray(responseBody)) {
    console.log("Response length:", responseBody.length);
  }
  check(response, { "filtered spatial datapoints by KNN": (r) => r.status === 200 });
}
