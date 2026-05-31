/**
 * BUG-COLL-APPID-ROUTE-001 (2026-05-31) — collectionRouteParams parser tests.
 *
 * Smoke-tests the regression that broke every `/collections/{appId}/...` URL
 * family: pre-fix the parser called `parseInt()` on the route id and
 * truncated UUID v7 ids to their leading numeric prefix
 * (`parseInt("019e6ffc-89a4-...")` → `19`). The fix returns the raw string
 * for both UUID v7 and legacy numeric shapes; malformed input parses to
 * `undefined`.
 */
import { describe, it, expect } from "vitest";
import {
  parseCollectionRouteParams,
  isCollectionRouteParams,
} from "../../utils/collectionRouteParams";

const UUID_V7 = "019e6ffc-89a4-76b5-8dbb-15888646a904";
const ANOTHER_UUID_V7 = "018f9c5a-7e26-7000-a000-000000000010";
const NUMERIC_LEGACY = "2107";
const NUMERIC_BIG = "9223372036854775807"; // Long.MAX_VALUE — survives as string
const MALFORMED = "not-a-real-id";

describe("parseCollectionRouteParams", () => {
  it("round-trips a UUID v7 collectionId without truncation", () => {
    const r = parseCollectionRouteParams({ collectionId: UUID_V7 });
    expect(r.collectionId).toBe(UUID_V7);
  });

  it("round-trips a legacy numeric collectionId as string", () => {
    const r = parseCollectionRouteParams({ collectionId: NUMERIC_LEGACY });
    expect(r.collectionId).toBe(NUMERIC_LEGACY);
  });

  it("preserves a Long.MAX_VALUE-sized numeric id without precision loss", () => {
    const r = parseCollectionRouteParams({ collectionId: NUMERIC_BIG });
    // `parseInt(NUMERIC_BIG)` would lose precision; string preserves it.
    expect(r.collectionId).toBe(NUMERIC_BIG);
  });

  it("rejects a malformed collectionId as undefined", () => {
    const r = parseCollectionRouteParams({ collectionId: MALFORMED });
    expect(r.collectionId).toBeUndefined();
  });

  it("rejects an empty collectionId", () => {
    const r = parseCollectionRouteParams({ collectionId: "" });
    expect(r.collectionId).toBeUndefined();
  });

  it("rejects an array collectionId (vue-router catch-all shape)", () => {
    const r = parseCollectionRouteParams({
      collectionId: ["019e6ffc"] as unknown as string,
    });
    expect(r.collectionId).toBeUndefined();
  });

  it("round-trips dataObjectId, fileReferenceId, timeseriesReferenceId, structuredDataReferenceId UUIDs", () => {
    const r = parseCollectionRouteParams({
      collectionId: UUID_V7,
      dataObjectId: ANOTHER_UUID_V7,
      fileReferenceId: ANOTHER_UUID_V7,
      timeseriesReferenceId: ANOTHER_UUID_V7,
      structuredDataReferenceId: ANOTHER_UUID_V7,
    });
    expect(r.collectionId).toBe(UUID_V7);
    expect(r.dataObjectId).toBe(ANOTHER_UUID_V7);
    expect(r.fileReferenceId).toBe(ANOTHER_UUID_V7);
    expect(r.timeseriesReferenceId).toBe(ANOTHER_UUID_V7);
    expect(r.structuredDataReferenceId).toBe(ANOTHER_UUID_V7);
  });

  it("round-trips numeric ids on every field shape", () => {
    const r = parseCollectionRouteParams({
      collectionId: "1",
      dataObjectId: "2",
      fileReferenceId: "3",
      timeseriesReferenceId: "4",
      structuredDataReferenceId: "5",
    });
    expect(r.collectionId).toBe("1");
    expect(r.dataObjectId).toBe("2");
    expect(r.fileReferenceId).toBe("3");
    expect(r.timeseriesReferenceId).toBe("4");
    expect(r.structuredDataReferenceId).toBe("5");
  });

  it("regression: does NOT truncate UUID v7 to leading-digit prefix", () => {
    // The pre-fix bug: parseInt("019e6ffc-...") returned 19.
    const r = parseCollectionRouteParams({ collectionId: UUID_V7 });
    expect(r.collectionId).not.toBe("19");
    expect(r.collectionId).not.toBe(19);
  });
});

describe("isCollectionRouteParams", () => {
  it("accepts a populated UUID v7 collectionId", () => {
    expect(isCollectionRouteParams({ collectionId: UUID_V7 })).toBe(true);
  });

  it("accepts a populated numeric collectionId", () => {
    expect(isCollectionRouteParams({ collectionId: "2107" })).toBe(true);
  });

  it("rejects an undefined collectionId", () => {
    expect(isCollectionRouteParams({ collectionId: undefined })).toBe(false);
  });

  it("rejects an empty-string collectionId (the unset sentinel)", () => {
    expect(isCollectionRouteParams({ collectionId: "" })).toBe(false);
  });
});
