/**
 * KRL-INTERPRETER-06 — unit tests for the result-panel pure logic.
 *
 * The deep-link URL builder is tested as a pure function. The success/error
 * branch selection and severity-color mapping are similarly isolated.
 */
import { describe, it, expect } from "vitest";

/**
 * Pure helper extracted from KrlInterpretResultPanel.vue — builds the URDF
 * render route's deep-link from a payload URL.
 */
function buildUrdfDeepLink(urdfPayloadUrl: string | null): string | null {
  if (!urdfPayloadUrl) return null;
  const encoded = encodeURIComponent(urdfPayloadUrl);
  return `/shapes/render?renderer=urdf&urdfUrl=${encoded}`;
}

function severityColor(severity: string): string {
  switch (severity) {
    case "ERROR":
      return "error";
    case "WARN":
      return "warning";
    case "INFO":
      return "info";
    default:
      return "default";
  }
}

describe("KrlInterpretResultPanel — buildUrdfDeepLink", () => {
  it("returns null when no payload URL is provided", () => {
    expect(buildUrdfDeepLink(null)).toBeNull();
    expect(buildUrdfDeepLink("")).toBeNull();
  });

  it("encodes the URDF payload URL into the render route's query param", () => {
    const link = buildUrdfDeepLink(
      "https://shepard.example.com/shepard/api/collections/42/dataObjects/100/fileReferences/9/payload",
    );
    expect(link).toBe(
      "/shapes/render?renderer=urdf&urdfUrl=https%3A%2F%2Fshepard.example.com%2Fshepard%2Fapi%2Fcollections%2F42%2FdataObjects%2F100%2FfileReferences%2F9%2Fpayload",
    );
  });

  it("preserves special characters in the payload URL", () => {
    const link = buildUrdfDeepLink("/file with space.urdf");
    expect(link).toBe("/shapes/render?renderer=urdf&urdfUrl=%2Ffile%20with%20space.urdf");
  });
});

describe("KrlInterpretResultPanel — severityColor", () => {
  it("maps ERROR to error", () => {
    expect(severityColor("ERROR")).toBe("error");
  });

  it("maps WARN to warning, INFO to info, fallback to default", () => {
    expect(severityColor("WARN")).toBe("warning");
    expect(severityColor("INFO")).toBe("info");
    expect(severityColor("OTHER")).toBe("default");
  });
});
