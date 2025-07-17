import type { RouteParamsGeneric } from "vue-router";

export interface CollectionRouteParams {
  collectionId: number;
  dataObjectId?: number;
  timeseriesReferenceId?: number;
  fileReferenceId?: number;
  structuredDataReferenceId?: number;
}

export const isCollectionRouteParams = (
  routeParams: Partial<CollectionRouteParams>,
): routeParams is CollectionRouteParams => {
  if (!routeParams.collectionId) return false;
  return true;
};

/**
 * A helper function to parse the router parameter to create an instance of `CollectionRouteParams`.
 * @param routeParams - RouteParamsGeneric
 * @returns Returns `undefined` if the collectionId was not present in the route params, else returns an instance of `CollectionRouteParams`
 */

export function parseCollectionRouteParams(
  routeParams: RouteParamsGeneric,
): Partial<CollectionRouteParams> {
  return {
    collectionId: parseCollectionId(routeParams),
    dataObjectId: parseDataObjectId(routeParams),
    timeseriesReferenceId: parseTimeseriesReferenceId(routeParams),
    fileReferenceId: parseFileReferenceId(routeParams),
    structuredDataReferenceId: parseStructuredDataReferenceId(routeParams),
  };
}

function parseCollectionId(
  routeParams: RouteParamsGeneric,
): number | undefined {
  if (
    routeParams.collectionId &&
    typeof routeParams.collectionId === "string" &&
    !Number.isNaN(routeParams.collectionId)
  ) {
    return parseInt(routeParams.collectionId);
  }
  return undefined;
}

function parseDataObjectId(
  routeParams: RouteParamsGeneric,
): number | undefined {
  if (
    routeParams.dataObjectId &&
    typeof routeParams.dataObjectId === "string"
  ) {
    return parseInt(routeParams.dataObjectId);
  }
  return undefined;
}

function parseTimeseriesReferenceId(
  routeParams: RouteParamsGeneric,
): number | undefined {
  if (
    routeParams.timeseriesReferenceId &&
    typeof routeParams.timeseriesReferenceId === "string"
  ) {
    return parseInt(routeParams.timeseriesReferenceId);
  }
  return undefined;
}

function parseFileReferenceId(
  routeParams: RouteParamsGeneric,
): number | undefined {
  if (
    routeParams.fileReferenceId &&
    typeof routeParams.fileReferenceId === "string"
  ) {
    return parseInt(routeParams.fileReferenceId);
  }
  return undefined;
}

function parseStructuredDataReferenceId(
  routeParams: RouteParamsGeneric,
): number | undefined {
  if (
    routeParams.structuredDataReferenceId &&
    typeof routeParams.structuredDataReferenceId === "string"
  ) {
    return parseInt(routeParams.structuredDataReferenceId);
  }
  return undefined;
}
