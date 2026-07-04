/**
 * UU2 — UI-STALE-URL-HINT — tests for the numeric-vs-UUID id shape helpers.
 *
 * The hint card on `EntityNotFound.vue` is gated by `isNumericLegacyId`;
 * the canonical operator repro is `/collections/1787/dataobjects/1792`
 * (pre-Neo4j-wipe long ids) — that must trip the hint, while a current
 * appId like `019e6ffc-89a4-76b5-8dbb-15888646a904` must NOT.
 */
import { describe, it, expect } from "vitest";
import { isNumericLegacyId, isPlausibleAppId } from "~/utils/idShape";

describe("isNumericLegacyId", () => {
  it("returns true for the canonical operator-repro numeric ids", () => {
    expect(isNumericLegacyId("1787")).toBe(true);
    expect(isNumericLegacyId("1792")).toBe(true);
  });

  it("returns true for any all-digit string (legacy Neo4j long)", () => {
    expect(isNumericLegacyId("0")).toBe(true);
    expect(isNumericLegacyId("99999999")).toBe(true);
  });

  it("returns false for UUID v7 appIds", () => {
    expect(isNumericLegacyId("019e6ffc-89a4-76b5-8dbb-15888646a904")).toBe(
      false,
    );
  });

  it("returns false for empty / null / undefined / mixed shapes", () => {
    expect(isNumericLegacyId("")).toBe(false);
    expect(isNumericLegacyId(null)).toBe(false);
    expect(isNumericLegacyId(undefined)).toBe(false);
    expect(isNumericLegacyId("1787abc")).toBe(false);
    expect(isNumericLegacyId("abc")).toBe(false);
  });
});

describe("isPlausibleAppId", () => {
  it("returns true for a v7-shaped appId", () => {
    expect(isPlausibleAppId("019e6ffc-89a4-76b5-8dbb-15888646a904")).toBe(true);
  });

  it("returns false for numeric legacy ids", () => {
    expect(isPlausibleAppId("1787")).toBe(false);
  });

  it("returns false for empty / wrong shapes", () => {
    expect(isPlausibleAppId("")).toBe(false);
    expect(isPlausibleAppId(null)).toBe(false);
    expect(isPlausibleAppId("not-a-uuid")).toBe(false);
  });
});
