/**
 * Helpers for the structured SHACL validation report panel (UI-GAP-6).
 * Mirrors ShapeValidationReportIO from the backend.
 */

export interface ShaclFinding {
  focusNode: string | null;
  resultPath: string | null;
  value: string | null;
  severity: string;
  message: string;
  constraint: string | null;
}

export interface ShaclReport {
  conforms: boolean;
  parseError: string | null;
  engineError: string | null;
  findings: ShaclFinding[];
}

/** Parse the raw POST /v2/shapes/validate JSON response into a typed report. */
export function parseShaclReport(raw: unknown): ShaclReport {
  if (typeof raw !== "object" || raw === null) {
    return { conforms: false, parseError: "Unexpected response shape", engineError: null, findings: [] };
  }
  const r = raw as Record<string, unknown>;
  const findings: ShaclFinding[] = Array.isArray(r.findings)
    ? r.findings.map((f) => {
        const fi = f as Record<string, unknown>;
        return {
          focusNode: typeof fi.focusNode === "string" ? fi.focusNode : null,
          resultPath: typeof fi.resultPath === "string" ? fi.resultPath : null,
          value: typeof fi.value === "string" ? fi.value : null,
          severity: typeof fi.severity === "string" ? fi.severity : "Violation",
          message: typeof fi.message === "string" ? fi.message : "",
          constraint: typeof fi.constraint === "string" ? fi.constraint : null,
        };
      })
    : [];
  return {
    conforms: r.conforms === true,
    parseError: typeof r.parseError === "string" ? r.parseError : null,
    engineError: typeof r.engineError === "string" ? r.engineError : null,
    findings,
  };
}

/** Vuetify colour token for a SHACL severity string. */
export function severityColor(severity: string): string {
  switch (severity) {
    case "Info": return "info";
    case "Warning": return "warning";
    default: return "error"; // Violation + unknown
  }
}

/** MDI icon for a SHACL severity string. */
export function severityIcon(severity: string): string {
  switch (severity) {
    case "Info": return "mdi-information";
    case "Warning": return "mdi-alert";
    default: return "mdi-close-circle"; // Violation + unknown
  }
}
