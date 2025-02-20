import { check } from "k6";
import { Options } from "k6/options";
import {
  createCollection,
  createDataObject,
  deleteCollectionsIfTheyAlreadyExist,
  getCollections,
  isArrayWithOneCollectionOfThisName,
  searchCollectionsDedicated,
} from "../utils/collection-helper";
import { getIdFromResponse } from "../utils/timeseries-helper";

export const options: Options = {
  scenarios: {
    default: {
      executor: "shared-iterations",
      vus: 1,
      iterations: 10,
      // use function name here that should be executed
      exec: "measuring_get_collections",
      // exec: "measuring_search_collections",
    },
  },
  setupTimeout: "20m",
  teardownTimeout: "20m",
};

/**
 * Unfortunately, the get collections endpoint does only provide an exact match for the name parameter.
 * For that reason we can only test retrieval of a single collection in a controlled setting.
 */

const testCollectionName = "CollectionLoadTest";
const numberOfDataObjectsPerCollection = 2000;

export function setup() {
  deleteCollectionsIfTheyAlreadyExist(testCollectionName);
  const createCollectionResponse = createCollection(testCollectionName);
  const collectionId = getIdFromResponse(createCollectionResponse.json());

  for (var i = 0; i < numberOfDataObjectsPerCollection; i++) {
    createDataObject(collectionId, `${i}`);
  }
}

export function teardown() {
  deleteCollectionsIfTheyAlreadyExist(testCollectionName);
}

export function measuring_get_collections() {
  const response = getCollections(testCollectionName);
  check(response, { "get collections": (r) => r.status === 200 });
  check(response, {
    "got one collection via get": (r) => {
      const responseData = r.json();
      return isArrayWithOneCollectionOfThisName(responseData, testCollectionName);
    },
  });
}

export function measuring_search_collections() {
  const response = searchCollectionsDedicated(testCollectionName);
  check(response, { "get collections": (r) => r.status === 200 });
  check(response, {
    "got one collection via get": (r) => {
      const responseData = r.json();
      if (!(!!responseData && typeof responseData === "object" && "results" in responseData && responseData.results)) {
        return false;
      }
      return isArrayWithOneCollectionOfThisName(responseData.results, testCollectionName);
    },
  });
}
