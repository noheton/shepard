// LIC1: tests for the SPDX license vocabulary used by the LicenseInput
// autocomplete and the LicenseChip display. Pin the shape of the constants
// + the lookup helpers + the accessRights enum so a refactor that drops or
// rewrites an option breaks fast.
import { describe, it, expect } from "vitest";
import {
  SPDX_LICENSES,
  ACCESS_RIGHTS_OPTIONS,
  getAccessRightsOption,
  getSpdxLicense,
} from "../../utils/spdxLicenses";

describe("SPDX_LICENSES", () => {
  it("contains at least the funder-recommended core licenses", () => {
    const ids = SPDX_LICENSES.map(l => l.id);
    // The most common open licenses for research data + the two GitHub
    // defaults + the proprietary catch-all.
    for (const required of [
      "CC-BY-4.0",
      "CC0-1.0",
      "MIT",
      "Apache-2.0",
      "BSD-3-Clause",
      "GPL-3.0-only",
      "LGPL-3.0-only",
      "ODbL-1.0",
      "PROPRIETARY",
    ]) {
      expect(ids).toContain(required);
    }
  });

  it("has at least 25 licenses", () => {
    // Lower bound — adding licenses is safe, removing is not.
    expect(SPDX_LICENSES.length).toBeGreaterThanOrEqual(25);
  });

  it("has no duplicate identifiers", () => {
    const ids = SPDX_LICENSES.map(l => l.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("every entry has id + title + category", () => {
    for (const l of SPDX_LICENSES) {
      expect(l.id).toBeTruthy();
      expect(l.title).toBeTruthy();
      expect(l.category).toBeTruthy();
    }
  });

  it("only uses the documented categories", () => {
    const allowed = new Set([
      "creative-commons",
      "permissive",
      "copyleft",
      "data",
      "proprietary",
    ]);
    for (const l of SPDX_LICENSES) {
      expect(allowed.has(l.category)).toBe(true);
    }
  });
});

describe("getSpdxLicense", () => {
  it("returns the entry for a known SPDX id", () => {
    const l = getSpdxLicense("CC-BY-4.0");
    expect(l).toBeDefined();
    expect(l!.title).toContain("Creative Commons");
    expect(l!.category).toBe("creative-commons");
  });

  it("returns undefined for null / undefined / empty", () => {
    expect(getSpdxLicense(null)).toBeUndefined();
    expect(getSpdxLicense(undefined)).toBeUndefined();
    expect(getSpdxLicense("")).toBeUndefined();
  });

  it("returns undefined for an unknown id (so chip falls back to raw text)", () => {
    expect(getSpdxLicense("NotASpdxId-1.0")).toBeUndefined();
  });
});

describe("ACCESS_RIGHTS_OPTIONS", () => {
  it("has exactly the four documented enum values", () => {
    expect(ACCESS_RIGHTS_OPTIONS.map(o => o.value).sort()).toEqual(
      ["CLOSED", "EMBARGOED", "OPEN", "RESTRICTED"],
    );
  });

  it("OPEN is green, RESTRICTED is amber, CLOSED is red, EMBARGOED is blue", () => {
    const byValue = new Map(ACCESS_RIGHTS_OPTIONS.map(o => [o.value, o]));
    expect(byValue.get("OPEN")!.color).toBe("success");
    expect(byValue.get("RESTRICTED")!.color).toBe("warning");
    expect(byValue.get("CLOSED")!.color).toBe("error");
    expect(byValue.get("EMBARGOED")!.color).toBe("info");
  });

  it("every option has a label + description", () => {
    for (const o of ACCESS_RIGHTS_OPTIONS) {
      expect(o.label).toBeTruthy();
      expect(o.description).toBeTruthy();
    }
  });
});

describe("getAccessRightsOption", () => {
  it("returns the matching option for a known value", () => {
    const o = getAccessRightsOption("EMBARGOED");
    expect(o).toBeDefined();
    expect(o!.color).toBe("info");
    expect(o!.label).toBe("Embargoed");
  });

  it("returns undefined for null / undefined / empty / unknown", () => {
    expect(getAccessRightsOption(null)).toBeUndefined();
    expect(getAccessRightsOption(undefined)).toBeUndefined();
    expect(getAccessRightsOption("")).toBeUndefined();
    expect(getAccessRightsOption("NEW_STATE")).toBeUndefined();
  });
});
