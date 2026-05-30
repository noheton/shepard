import type { RouteParamsGeneric } from "vue-router";

export interface CollectionRouteParams {
  collectionId: number;
  /**
   * Set when the route param looks like a UUID v7 appId (UX-WALK-2026-05-29-03).
   * When present, `collectionId` will be `NaN` until resolved via the v2 API.
   * Once resolved, `collectionId` is updated to the numeric OGM id and this field
   * is retained for v2 calls that take appId directly.
   */
  collectionAppId?: string;
  dataObjectId?: number;
  timeseriesReferenceId?: number;
  fileReferenceId?: number;
  structuredDataReferenceId?: number;
}

/**
 * Returns true when `value` looks like a UUID v7 (or any UUID) appId.
 * A UUID is 32 hex digits separated by hyphens in the 8-4-4-4-12 pattern.
 * Exported so callers like `useFetchCollection` can re-use the check.
 */
export function isPlausibleAppId(value: string): boolean {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    value,
  );
}

export const isCollectionRouteParams = (
  routeParams: Partial<CollectionRouteParams>,
): routeParams is CollectionRouteParams => {
  // Valid when we have either a numeric id or a plausible appId UUID.
  if (routeParams.collectionAppId) return true;
  if (routeParams.collectionId && !Number.isNaN(routeParams.collectionId))
    return true;
  return false;
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
    collectionAppId: parseCollectionAppId(routeParams),
    dataObjectId: parseDataObjectId(routeParams),
    timeseriesReferenceId: parseTimeseriesReferenceId(routeParams),
    fileReferenceId: parseFileReferenceId(routeParams),
    structuredDataReferenceId: parseStructuredDataReferenceId(routeParams),
  };
}

/**
 * Parses the collectionId route param as a number.
 * Returns `NaN` (not `undefined`) when the param looks like a UUID appId —
 * the caller should check `collectionAppId` in that case.
 */
function parseCollectionId(
  routeParams: RouteParamsGeneric,
): number | undefined {
  const raw = routeParams.collectionId;
  if (!raw || typeof raw !== "string") return undefined;
  // UUID-shaped param — numeric id not yet known; return NaN as a sentinel so
  // isCollectionRouteParams can pass and the composable triggers v2 resolution.
  if (isPlausibleAppId(raw)) return NaN;
  const parsed = parseInt(raw, 10);
  return Number.isNaN(parsed) ? undefined : parsed;
}

/**
 * Captures the raw collectionId param when it looks like a UUID v7 appId.
 * Returns `undefined` for pure numeric ids (no appId in the URL).
 */
function parseCollectionAppId(
  routeParams: RouteParamsGeneric,
): string | undefined {
  const raw = routeParams.collectionId;
  if (!raw || typeof raw !== "string") return undefined;
  return isPlausibleAppId(raw) ? raw : undefined;
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
