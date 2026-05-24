import { describe, it, expect } from "vitest";
import { isValidOrcid, orcidVTextFieldRule } from "../../utils/orcidFormat";

describe("isValidOrcid", () => {
  // The canonical ORCID example from orcid.org documentation.
  it("accepts the canonical example 0000-0002-1825-0097", () => {
    expect(isValidOrcid("0000-0002-1825-0097")).toBe(true);
  });

  // An ORCID with the special `X` check character (value 10 in mod 11-2).
  it("accepts an ORCID with trailing X check character", () => {
    // 0000-0001-5109-3700 → check digit `0`; pick a real X-checksum example.
    // ORCID example used in the ORCID public docs.
    expect(isValidOrcid("0000-0002-1694-233X")).toBe(true);
  });

  it("rejects null", () => {
    expect(isValidOrcid(null)).toBe(false);
  });

  it("rejects undefined", () => {
    expect(isValidOrcid(undefined)).toBe(false);
  });

  it("rejects the empty string", () => {
    expect(isValidOrcid("")).toBe(false);
  });

  it("rejects a string of the wrong length", () => {
    expect(isValidOrcid("0000-0002-1825-009")).toBe(false);
    expect(isValidOrcid("0000-0002-1825-00977")).toBe(false);
  });

  it("rejects a string with missing hyphens", () => {
    expect(isValidOrcid("0000000218250097000")).toBe(false);
  });

  it("rejects a string with hyphens in the wrong positions", () => {
    expect(isValidOrcid("000-00002-1825-0097")).toBe(false);
  });

  it("rejects letters in the first 15 digit positions", () => {
    expect(isValidOrcid("000A-0002-1825-0097")).toBe(false);
  });

  it("rejects a mismatched checksum", () => {
    // Flip the last digit — checksum no longer matches.
    expect(isValidOrcid("0000-0002-1825-0098")).toBe(false);
  });

  it("rejects free-text input like 'abc'", () => {
    expect(isValidOrcid("abc")).toBe(false);
  });
});

describe("orcidVTextFieldRule", () => {
  it("treats empty input as valid (clears the field)", () => {
    expect(orcidVTextFieldRule("")).toBe(true);
    expect(orcidVTextFieldRule(null)).toBe(true);
    expect(orcidVTextFieldRule(undefined)).toBe(true);
  });

  it("returns true for a valid ORCID", () => {
    expect(orcidVTextFieldRule("0000-0002-1825-0097")).toBe(true);
  });

  it("returns an error message for an invalid ORCID", () => {
    const result = orcidVTextFieldRule("not-an-orcid-at-all");
    expect(typeof result).toBe("string");
    expect(result).toMatch(/ORCID/);
  });
});
