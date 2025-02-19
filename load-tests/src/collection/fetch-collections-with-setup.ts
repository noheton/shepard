import { check } from "k6";
import { Options } from "k6/options";
import { getIdFromResponse } from "../utils/timeseries-helper";
import {
  createCollection,
  createCollectionBatch,
  createDataObject,
  deleteCollection,
  deleteCollectionsByName,
  getCollections,
  searchCollectionsDedicated,
} from "../utils/collection-helper";

export const options: Options = {
  scenarios: {
    default: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 10,
      // use function name here that should be executed
      // exec: "measuring_get_collections",
      exec: "measuring_search_collections",
    },
  },
  setupTimeout: "20m",
  teardownTimeout: "20m",
};

const collectionBaseName = "CollectionLoadTest";
const numberOfCollections = 1;
const numberOfDataObjectsPerCollection = 2000;

export function setup() {
  createCollectionBatch(collectionBaseName, numberOfCollections, numberOfDataObjectsPerCollection);
}

export function teardown() {
  deleteCollectionsByName(collectionBaseName);
}

export function measuring_get_collections() {
  const response = getCollections(collectionBaseName);
  // TODO: Check correct number of collections
  check(response, { "get collections": (r) => r.status === 200 });
}

export function measuring_search_collections() {
  const response = searchCollectionsDedicated(collectionBaseName);
  // TODO: Check correct number of collections
  check(response, { "get collections": (r) => r.status === 200 });
}
