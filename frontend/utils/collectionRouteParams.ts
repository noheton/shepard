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
  videoStreamReferenceId?: string;
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
 * BUG-COLL-APPID-ROUTE-007-PAGE — resolve the v1 NUMERIC id for an
 * appId-routed detail page.
 *
 * The `[collectionId]` / `[dataObjectId]` route params are now the v2 appId
 * (a UUID). v1 `/shepard/api/...` endpoints (getAllDataObjects, collection
 * roles, semantic-annotation CRUD, lineage, createDataObject, …) still take
 * the NUMERIC id, which only the loaded v2 entity payload carries. This helper
 * encodes the resolution order used by the page's `collectionNumericId`
 * computed so it is unit-testable in isolation:
 *
 *   1. the loaded entity's numeric `id` wins (the canonical source);
 *   2. otherwise fall back to the route param IF it is itself a positive
 *      integer — covers legacy `/collections/123` deep links that pre-date
 *      the appId routing;
 *   3. otherwise `undefined` — a UUID route param with no loaded entity must
 *      NEVER coerce into a numeric-id endpoint (that was the original bug:
 *      `Number("019e…")` is NaN, and casting the UUID string straight into a
 *      v1 call 404s).
 *
 * @param loadedId - the loaded entity's numeric `id` (or null/undefined while
 *   the fetch is in flight).
 * @param routeParam - the raw route param (UUID, numeric string, or undefined).
 */
export function resolveNumericId(
  loadedId: number | null | undefined,
  routeParam: unknown,
): number | undefined {
  if (loadedId != null) return loadedId;
  const n = Number(routeParam);
  return Number.isInteger(n) && n > 0 ? n : undefined;
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
    videoStreamReferenceId: parseIdLike(routeParams.videoStreamReferenceId),
  };
}
