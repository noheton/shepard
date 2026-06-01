import { describe, it, expect } from "vitest";
import {
  formatEpochMillis,
  resolveLandingBranch,
} from "~/utils/sceneGraphsLanding";
import { isPlausibleAppId } from "~/utils/toolsLanding";

describe("formatEpochMillis", () => {
  it("returns em-dash for null", () => {
    expect(formatEpochMillis(null)).toBe("—");
  });

  it("returns em-dash for undefined", () => {
    expect(formatEpochMillis(undefined)).toBe("—");
  });

  it("formats a known epoch millis value", () => {
    const result = formatEpochMillis(0);
    expect(typeof result).toBe("string");
    expect(result.length).toBeGreaterThan(0);
    expect(result).not.toBe("—");
  });

  it("formats a recent timestamp without throwing", () => {
    const ts = new Date("2026-05-30T12:00:00Z").getTime();
    const result = formatEpochMillis(ts);
    expect(result).toMatch(/2026/);
  });
});

describe("resolveLandingBranch", () => {
  it("returns 'table' when there are rows", () => {
    expect(resolveLandingBranch(5, false, null)).toBe("table");
  });

  it("returns 'table' while loading even with zero rows", () => {
    expect(resolveLandingBranch(0, true, null)).toBe("table");
  });

  it("returns 'error' when error present and not loading and no rows", () => {
    expect(resolveLandingBranch(0, false, "Something went wrong")).toBe("error");
  });

  it("returns 'help' when empty and no error and not loading", () => {
    expect(resolveLandingBranch(0, false, null)).toBe("help");
  });

  it("returns 'table' when rows > 0 even with an error message", () => {
    expect(resolveLandingBranch(3, false, "partial error")).toBe("table");
  });
});

describe("isPlausibleAppId", () => {
  it("accepts a valid UUID v7", () => {
    expect(
      isPlausibleAppId("0197b6a2-1234-7abc-89de-abcdef012345"),
    ).toBe(true);
  });

  it("rejects null", () => {
    expect(isPlausibleAppId(null)).toBe(false);
  });

  it("rejects undefined", () => {
    expect(isPlausibleAppId(undefined)).toBe(false);
  });

  it("rejects empty string", () => {
    expect(isPlausibleAppId("")).toBe(false);
  });

  it("rejects a plain string", () => {
    expect(isPlausibleAppId("not-a-uuid")).toBe(false);
  });

  it("rejects a UUID missing a segment", () => {
    expect(isPlausibleAppId("0197b6a2-1234-7abc-89de")).toBe(false);
  });

  it("accepts uppercase UUID (lowercased internally)", () => {
    expect(
      isPlausibleAppId("0197B6A2-1234-7ABC-89DE-ABCDEF012345"),
    ).toBe(true);
  });
});
