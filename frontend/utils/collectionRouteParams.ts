import type { RouteParamsGeneric } from "vue-router";

/**
 * BUG-COLL-APPID-ROUTE-001 fix (2026-05-31): all id fields are strings.
 *
 * Pre-fix these were typed `number` and parsed with `parseInt(...)`, which
 * truncated any UUID v7 appId to its leading numeric prefix
 * (`parseInt("019e6ffc-...")` → `19`). Every `/collections/{appId}/...` URL
 * was therefore broken end-to-end (frontend fetched `/collections/19` →
 * 404). Strings round-trip both UUID v7 (post-L2d) and the legacy
 * numeric-long form (upstream v1 compat) untouched.
 *
 * The generated `@dlr-shepard/backend-client` still types path params as
 * `number`; call-sites cast at the boundary
 * (`as unknown as number`) — runtime is `String(x) + encodeURIComponent`
 * so strings work on the wire.
 */
export interface CollectionRouteParams {
  collectionId: string;
  dataObjectId?: string;
  timeseriesReferenceId?: string;
  fileReferenceId?: string;
  structuredDataReferenceId?: string;
}

export const isCollectionRouteParams = (
  routeParams: Partial<CollectionRouteParams>,
): routeParams is CollectionRouteParams => {
  if (!routeParams.collectionId) return false;
  return true;
};

/**
 * UUID v7 shape: 8-4-4-4-12 hex, version nibble = 7.
 * Legacy upstream-compat: arbitrarily long all-digit string (Neo4j Long).
 * Anything else is malformed and parses to undefined.
 */
const UUID_V7_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const NUMERIC_RE = /^\d+$/;

function parseIdLike(raw: unknown): string | undefined {
  if (typeof raw !== "string" || raw.length === 0) return undefined;
  if (UUID_V7_RE.test(raw) || NUMERIC_RE.test(raw)) return raw;
  return undefined;
}

/**
 * A helper function to parse the router parameter to create an instance of `CollectionRouteParams`.
 * @param routeParams - RouteParamsGeneric
 * @returns A partial of `CollectionRouteParams`; `collectionId` is `undefined`
 * if the route didn't carry one or carried a malformed value, in which case
 * `isCollectionRouteParams` returns false and the caller should redirect.
 */
export function parseCollectionRouteParams(
  routeParams: RouteParamsGeneric,
): Partial<CollectionRouteParams> {
  return {
    collectionId: parseIdLike(routeParams.collectionId),
    dataObjectId: parseIdLike(routeParams.dataObjectId),
    timeseriesReferenceId: parseIdLike(routeParams.timeseriesReferenceId),
    fileReferenceId: parseIdLike(routeParams.fileReferenceId),
    structuredDataReferenceId: parseIdLike(routeParams.structuredDataReferenceId),
  };
}
