// @ts-ignore
import { URL } from "https://jslib.k6.io/url/1.0.0/index.js";
import http from "k6/http";
import { buildParamsWithApiKey } from "../utils/uri";
import { buildUri } from "./uri";

export const collectionUrl = buildUri("/shepard/api/collections");
export const universalSearchUrl = buildUri("/shepard/api/search");
export const collectionSearchUrl = buildUri("/shepard/api/search/collections");
const params = buildParamsWithApiKey();

export function createCollection(name: string) {
  const payload = JSON.stringify({ name });
  return http.post(collectionUrl, payload, params);
}

export function createCollectionBatch(
  baseName: string,
  numberOfCollections: number,
  numberOfDataObjectsPerCollection: number,
) {
  const url =
    collectionUrl +
    "/generate?baseName=" +
    baseName +
    "&numberOfCollections=" +
    numberOfCollections +
    "&numberOfDataObjectsPerCollection=" +
    numberOfDataObjectsPerCollection;
  return http.post(url, undefined, params);
}

export function createDataObject(collectionId: number, name: string) {
  const payload = JSON.stringify({ name });
  const url = collectionUrl + "/" + collectionId + "/dataObjects/";
  return http.post(url, payload, params);
}

export function deleteCollectionsByName(name: string) {
  const response: any = searchCollectionsDedicated(name).json();

  console.log("Deleting " + response.totalResults + " previously created collections with name containing " + name);
  response.results.forEach((r: any) => deleteCollection(r.id));
}

export function deleteCollection(collectionId: number) {
  return http.del(collectionUrl + "/" + collectionId, null, params);
}

export function getCollections(name?: string) {
  const url = new URL(collectionUrl);
  if (name) {
    url.searchParams.append("name", name);
  }
  return http.get(url.toString(), params);
}

export function searchCollectionsDedicated(name?: string) {
  const payload = JSON.stringify({
    searchParams: { query: `{\"property\":\"name\",\"value\":\"${name}\",\"operator\":\"contains\"}` },
  });
  return http.post(collectionSearchUrl, payload, params);
}
