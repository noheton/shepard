// @ts-ignore
import { URL } from "https://jslib.k6.io/url/1.0.0/index.js";
import http from "k6/http";
import { buildParamsWithApiKey } from "../utils/uri";
import { buildUri } from "./uri";

export const collectionUrl = buildUri("/shepard/api/collections");
export const universalSearchUrl = buildUri("/shepard/api/search");
export const collectionSearchUrl = buildUri("/shepard/api/search/collections");
const params = buildParamsWithApiKey();

export function getCollection(collectionId: number) {
  return http.get(collectionUrl + "/" + collectionId, params);
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

export function createCollection(name: string) {
  const payload = JSON.stringify({ name });
  return http.post(collectionUrl, payload, params);
}

export function createDataObject(collectionId: number, name: string) {
  const payload = JSON.stringify({ name });
  const url = collectionUrl + "/" + collectionId + "/dataObjects/";
  return http.post(url, payload, params);
}

export function deleteCollection(collectionId: number) {
  return http.del(collectionUrl + "/" + collectionId, null, params);
}

export function deleteCollectionsIfTheyAlreadyExist(collectionName: string) {
  const response = getCollections(collectionName);

  const returnedCollections = response.json();

  if (!Array.isArray(returnedCollections) || returnedCollections.length === 0) return;
  returnedCollections.forEach((c) => {
    if (!!c && typeof c === "object" && "id" in c && typeof c.id === "number") {
      deleteCollection(c.id);
    }
  });
}

export function isArrayWithOneCollectionOfThisName(
  collections: unknown,
  expectedName: String,
): collections is Array<{ name: String }> {
  if (!Array.isArray(collections) || collections.length !== 1) return false;
  const returnedCollection = collections.at(0);
  return (
    !!returnedCollection &&
    typeof returnedCollection === "object" &&
    "name" in returnedCollection &&
    typeof returnedCollection.name === "string" &&
    !!returnedCollection.name &&
    returnedCollection.name === expectedName
  );
}
