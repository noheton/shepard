/**
 * BUG-COLL-APPID-ROUTE-007-PAGE — resolveNumericId() tests.
 *
 * The `[collectionId]` / `[dataObjectId]` route params are now the v2 appId
 * (a UUID). v1 `/shepard/api/...` endpoints still take the NUMERIC id, which
 * only the loaded v2 entity payload carries. `resolveNumericId` encodes the
 * resolution order used by the detail pages' `collectionNumericId` /
 * `dataObjectNumericId` computeds:
 *   1. loaded entity's numeric id wins,
 *   2. else numeric-route-param fallback (legacy /collections/123 deep links),
 *   3. else undefined — a UUID route param must NEVER coerce to a numeric id.
 */
import { describe, it, expect } from "vitest";
import { resolveNumericId } from "../../utils/collectionRouteParams";

const UUID_V7 = "019e6ffc-89a4-76b5-8dbb-15888646a904";

describe("resolveNumericId", () => {
  it("returns the loaded entity id when present (id wins over route param)", () => {
    expect(resolveNumericId(2107, UUID_V7)).toBe(2107);
  });

  it("prefers the loaded id even when the route param is a numeric string", () => {
    // A legacy numeric deep link still defers to the canonical loaded id.
    expect(resolveNumericId(2107, "999")).toBe(2107);
  });

  it("falls back to a numeric route param when no entity is loaded yet", () => {
    expect(resolveNumericId(undefined, "123")).toBe(123);
    expect(resolveNumericId(null, "123")).toBe(123);
  });

  it("returns undefined for a UUID route param with no loaded entity", () => {
    // This is the original bug: Number(UUID) is NaN, and the UUID must never
    // be cast into a numeric-id v1 endpoint.
    expect(resolveNumericId(undefined, UUID_V7)).toBeUndefined();
    expect(resolveNumericId(null, UUID_V7)).toBeUndefined();
  });

  it("returns undefined when neither a loaded id nor a numeric param exists", () => {
    expect(resolveNumericId(undefined, undefined)).toBeUndefined();
    expect(resolveNumericId(null, "")).toBeUndefined();
    expect(resolveNumericId(undefined, "not-a-number")).toBeUndefined();
  });

  it("rejects a non-positive numeric route param", () => {
    expect(resolveNumericId(undefined, "0")).toBeUndefined();
    expect(resolveNumericId(undefined, "-5")).toBeUndefined();
  });

  it("rejects a non-integer numeric route param", () => {
    expect(resolveNumericId(undefined, "12.5")).toBeUndefined();
  });

  it("accepts a zero loaded id as undefined only if it is genuinely absent — id 0 is preserved", () => {
    // A loaded id of 0 is technically non-null; resolveNumericId returns it as
    // given (the loaded-id branch does not range-check — that is the entity's
    // canonical id). Neo4j ids start at 1 in practice, but we document the
    // contract: a non-null loaded id always wins verbatim.
    expect(resolveNumericId(0, UUID_V7)).toBe(0);
  });
});
