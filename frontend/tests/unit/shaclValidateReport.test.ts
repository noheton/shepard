import { describe, it, expect } from "vitest";
import { parseShaclReport, severityColor, severityIcon } from "~/utils/shaclValidateReport";

describe("parseShaclReport", () => {
  it("parses a conforming report", () => {
    const r = parseShaclReport({ conforms: true, parseError: null, engineError: null, findings: [] });
    expect(r.conforms).toBe(true);
    expect(r.findings).toHaveLength(0);
    expect(r.parseError).toBeNull();
  });

  it("parses a report with findings", () => {
    const r = parseShaclReport({
      conforms: false,
      parseError: null,
      engineError: null,
      findings: [
        {
          focusNode: "http://example.org/alice",
          resultPath: "http://example.org/age",
          value: "-1",
          severity: "Violation",
          message: "age must be >= 0",
          constraint: "http://www.w3.org/ns/shacl#MinInclusiveConstraintComponent",
        },
      ],
    });
    expect(r.conforms).toBe(false);
    expect(r.findings).toHaveLength(1);
    expect(r.findings[0]!.focusNode).toBe("http://example.org/alice");
    expect(r.findings[0]!.severity).toBe("Violation");
    expect(r.findings[0]!.message).toBe("age must be >= 0");
  });

  it("handles null/missing fields in findings gracefully", () => {
    const r = parseShaclReport({
      conforms: false,
      parseError: null,
      engineError: null,
      findings: [{ severity: "Warning", message: "missing required property" }],
    });
    expect(r.findings[0]!.focusNode).toBeNull();
    expect(r.findings[0]!.resultPath).toBeNull();
    expect(r.findings[0]!.value).toBeNull();
    expect(r.findings[0]!.constraint).toBeNull();
  });

  it("surfaces parseError", () => {
    const r = parseShaclReport({
      conforms: false,
      parseError: "Not a valid Turtle document",
      engineError: null,
      findings: [],
    });
    expect(r.parseError).toBe("Not a valid Turtle document");
    expect(r.conforms).toBe(false);
  });

  it("surfaces engineError", () => {
    const r = parseShaclReport({
      conforms: false,
      parseError: null,
      engineError: "Jena threw NullPointerException",
      findings: [],
    });
    expect(r.engineError).toBe("Jena threw NullPointerException");
  });

  it("returns safe defaults for unexpected response shape", () => {
    const r = parseShaclReport(null);
    expect(r.conforms).toBe(false);
    expect(r.parseError).toBe("Unexpected response shape");
    expect(r.findings).toHaveLength(0);
  });

  it("returns safe defaults for non-object input", () => {
    const r = parseShaclReport("not-an-object");
    expect(r.conforms).toBe(false);
    expect(r.findings).toHaveLength(0);
  });
});

describe("severityColor", () => {
  it("returns success color for Info", () => {
    expect(severityColor("Info")).toBe("info");
  });
  it("returns warning color for Warning", () => {
    expect(severityColor("Warning")).toBe("warning");
  });
  it("returns error color for Violation", () => {
    expect(severityColor("Violation")).toBe("error");
  });
  it("returns error color for unknown severities", () => {
    expect(severityColor("Unknown")).toBe("error");
  });
});

describe("severityIcon", () => {
  it("returns info icon for Info", () => {
    expect(severityIcon("Info")).toBe("mdi-information");
  });
  it("returns alert icon for Warning", () => {
    expect(severityIcon("Warning")).toBe("mdi-alert");
  });
  it("returns close-circle icon for Violation", () => {
    expect(severityIcon("Violation")).toBe("mdi-close-circle");
  });
});
