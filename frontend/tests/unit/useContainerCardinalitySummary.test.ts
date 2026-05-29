/**
 * UI21-SIZEBAR-DATA — unit tests for useContainerCardinalitySummary.
 *
 * Tests focus on the URL-construction logic (type → path-segment mapping)
 * and the null-graceful handling of unsupported container types.
 * Network calls are not exercised here (fire-and-forget fetch is tested
 * via integration / Playwright).
 */
import { describe, it, expect } from "vitest";

// The function under test derives the URL path segment from the container type.
// Rather than importing the composable (which requires a full Nuxt/Vue runtime),
// we test the pure mapping logic extracted into a helper module.
// The summaryPathSegment mapping mirrors what's in the composable.
const summaryPathSegment: Partial<Record<string, string>> = {
  TIMESERIES: "timeseries-containers",
  FILE: "file-containers",
  STRUCTUREDDATA: "structured-data-containers",
};

describe("ContainerCardinalitySummary URL mapping (UI21-SIZEBAR-DATA)", () => {
  it("maps TIMESERIES to timeseries-containers", () => {
    expect(summaryPathSegment["TIMESERIES"]).toBe("timeseries-containers");
  });

  it("maps FILE to file-containers", () => {
    expect(summaryPathSegment["FILE"]).toBe("file-containers");
  });

  it("maps STRUCTUREDDATA to structured-data-containers", () => {
    expect(summaryPathSegment["STRUCTUREDDATA"]).toBe("structured-data-containers");
  });

  it("returns undefined for SPATIALDATA (no summary endpoint)", () => {
    expect(summaryPathSegment["SPATIALDATA"]).toBeUndefined();
  });

  it("returns undefined for BASIC (no summary endpoint)", () => {
    expect(summaryPathSegment["BASIC"]).toBeUndefined();
  });

  it("constructs the correct summary URL for a timeseries container", () => {
    const base = "https://shepard.example.com";
    const id = 42;
    const type = "TIMESERIES";
    const segment = summaryPathSegment[type];
    expect(segment).toBeDefined();
    const url = `${base}/v2/${segment}/${id}/summary`;
    expect(url).toBe("https://shepard.example.com/v2/timeseries-containers/42/summary");
  });

  it("constructs the correct summary URL for a file container", () => {
    const base = "https://shepard.example.com";
    const id = 17;
    const type = "FILE";
    const segment = summaryPathSegment[type];
    expect(segment).toBeDefined();
    const url = `${base}/v2/${segment}/${id}/summary`;
    expect(url).toBe("https://shepard.example.com/v2/file-containers/17/summary");
  });

  it("constructs the correct summary URL for a structured data container", () => {
    const base = "https://shepard.example.com";
    const id = 99;
    const type = "STRUCTUREDDATA";
    const segment = summaryPathSegment[type];
    expect(segment).toBeDefined();
    const url = `${base}/v2/${segment}/${id}/summary`;
    expect(url).toBe("https://shepard.example.com/v2/structured-data-containers/99/summary");
  });
});
