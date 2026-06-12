// BTKVS-B2 — pure helpers of the form-descriptor composable:
// path building + the doc 125 §5.2 violations[]→field dictionary mapping.
import { describe, expect, it } from "vitest";
import {
  templateExcelExportPath,
  templateFormPath,
  violationsByPath,
} from "~/composables/useTemplateForm";

describe("templateFormPath", () => {
  it("builds the /v2/ descriptor path from an appId", () => {
    expect(templateFormPath("019e7243-f995-7914-be80-53e367aa5172")).toBe(
      "/v2/templates/019e7243-f995-7914-be80-53e367aa5172/form",
    );
  });

  it("URL-encodes hostile appIds", () => {
    expect(templateFormPath("a/b")).toBe("/v2/templates/a%2Fb/form");
  });
});

describe("templateExcelExportPath", () => {
  it("builds the /v2/ export path with the dataObjectAppId query (BTKVS-C1-EXCEL-EXPORT)", () => {
    expect(templateExcelExportPath("tmpl-1", "019e7243-f995-7914-be80-1")).toBe(
      "/v2/templates/tmpl-1/export?dataObjectAppId=019e7243-f995-7914-be80-1",
    );
  });

  it("URL-encodes both appIds", () => {
    expect(templateExcelExportPath("a/b", "c&d")).toBe(
      "/v2/templates/a%2Fb/export?dataObjectAppId=c%26d",
    );
  });
});

describe("violationsByPath", () => {
  it("maps a pattern-violating Docket-ID to its field path", () => {
    const problem = {
      violations: [
        {
          path: "urn:shepard:attribute:docket_id",
          value: "123",
          constraint: "http://www.w3.org/ns/shacl#PatternConstraintComponent",
          message: 'Value does not match pattern "^[A-Z][0-9]{3}$"',
        },
      ],
    };
    const mapped = violationsByPath(problem);
    expect(mapped["urn:shepard:attribute:docket_id"]).toContain("pattern");
  });

  it("falls back to the constraint IRI when no message is supplied", () => {
    const mapped = violationsByPath({
      violations: [{ path: "urn:x", constraint: "urn:c" }],
    });
    expect(mapped["urn:x"]).toBe("urn:c");
  });

  it("tolerates null/empty problems", () => {
    expect(violationsByPath(null)).toEqual({});
    expect(violationsByPath({})).toEqual({});
  });
});
