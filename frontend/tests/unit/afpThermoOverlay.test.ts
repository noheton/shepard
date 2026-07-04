/**
 * MFFD-RENDER-AFP-THERMO-OVERLAY slice 3 — pure-helper tests.
 *
 * Covers every exported helper in utils/afpThermoOverlay.ts:
 *   - tileMatchColor   (all 3 verdicts)
 *   - tileMatchLabel   (all 3 verdicts)
 *   - tileMatchIcon    (all 3 verdicts)
 *   - formatTempSetpoint (normal / zero / null / undefined)
 *   - formatSpeedSetpoint (normal / null)
 *   - formatTimeWindow (both present / start only / end only / both absent)
 *   - lastIriSegment (colon-sep / slash-sep / no separator / null / empty)
 *
 * 23 cases; no DOM, no fetch, no Vuetify.
 */
import { describe, it, expect } from "vitest";
import {
  tileMatchColor,
  tileMatchLabel,
  tileMatchIcon,
  formatTempSetpoint,
  formatSpeedSetpoint,
  formatTimeWindow,
  lastIriSegment,
} from "../../utils/afpThermoOverlay";

// ── tileMatchColor ────────────────────────────────────────────────────────

describe("tileMatchColor", () => {
  it("returns success for MATCHED", () => {
    expect(tileMatchColor("MATCHED")).toBe("success");
  });
  it("returns error for MISMATCHED", () => {
    expect(tileMatchColor("MISMATCHED")).toBe("error");
  });
  it("returns warning for UNVERIFIED", () => {
    expect(tileMatchColor("UNVERIFIED")).toBe("warning");
  });
});

// ── tileMatchLabel ────────────────────────────────────────────────────────

describe("tileMatchLabel", () => {
  it("returns readable label for MATCHED", () => {
    expect(tileMatchLabel("MATCHED")).toBe("Tile matched");
  });
  it("returns readable label for MISMATCHED", () => {
    expect(tileMatchLabel("MISMATCHED")).toBe("Tile mismatch");
  });
  it("returns readable label for UNVERIFIED", () => {
    expect(tileMatchLabel("UNVERIFIED")).toBe("Not verified");
  });
});

// ── tileMatchIcon ─────────────────────────────────────────────────────────

describe("tileMatchIcon", () => {
  it("returns check-circle icon for MATCHED", () => {
    expect(tileMatchIcon("MATCHED")).toBe("mdi-check-circle-outline");
  });
  it("returns alert-circle icon for MISMATCHED", () => {
    expect(tileMatchIcon("MISMATCHED")).toBe("mdi-alert-circle-outline");
  });
  it("returns help-circle icon for UNVERIFIED", () => {
    expect(tileMatchIcon("UNVERIFIED")).toBe("mdi-help-circle-outline");
  });
});

// ── formatTempSetpoint ────────────────────────────────────────────────────

describe("formatTempSetpoint", () => {
  it("formats a positive temperature with one decimal place", () => {
    expect(formatTempSetpoint(220)).toBe("220.0 °C");
  });
  it("formats a fractional temperature", () => {
    expect(formatTempSetpoint(37.5)).toBe("37.5 °C");
  });
  it("formats zero", () => {
    expect(formatTempSetpoint(0)).toBe("0.0 °C");
  });
  it("returns em-dash for null", () => {
    expect(formatTempSetpoint(null)).toBe("—");
  });
  it("returns em-dash for undefined", () => {
    expect(formatTempSetpoint(undefined)).toBe("—");
  });
});

// ── formatSpeedSetpoint ───────────────────────────────────────────────────

describe("formatSpeedSetpoint", () => {
  it("formats speed with one decimal and unit", () => {
    expect(formatSpeedSetpoint(50)).toBe("50.0 m/min");
  });
  it("returns em-dash for null", () => {
    expect(formatSpeedSetpoint(null)).toBe("—");
  });
});

// ── formatTimeWindow ──────────────────────────────────────────────────────

describe("formatTimeWindow", () => {
  it("formats both bounds as microseconds with arrow separator", () => {
    const result = formatTimeWindow(1_000_000, 2_000_000);
    expect(result).toContain("µs");
    expect(result).toContain("→");
  });
  it("shows em-dash for missing start, shows end", () => {
    const result = formatTimeWindow(null, 1_500_000);
    expect(result).toContain("—");
    expect(result).toContain("µs");
  });
  it("shows start, em-dash for missing end", () => {
    const result = formatTimeWindow(500_000, null);
    expect(result).toContain("µs");
    expect(result).toContain("—");
  });
  it("returns empty string when both absent", () => {
    expect(formatTimeWindow(null, null)).toBe("");
    expect(formatTimeWindow(undefined, undefined)).toBe("");
  });
});

// ── lastIriSegment ────────────────────────────────────────────────────────

describe("lastIriSegment", () => {
  it("returns last colon-delimited segment", () => {
    expect(lastIriSegment("urn:shepard:mffd:material-batch:B2024-03")).toBe("B2024-03");
  });
  it("returns last slash-delimited segment", () => {
    expect(lastIriSegment("https://example.org/vocab/material#lot42")).toBe("lot42");
  });
  it("returns the whole string when no separator found", () => {
    expect(lastIriSegment("BATCH-001")).toBe("BATCH-001");
  });
  it("returns em-dash for null", () => {
    expect(lastIriSegment(null)).toBe("—");
  });
  it("returns em-dash for undefined", () => {
    expect(lastIriSegment(undefined)).toBe("—");
  });
  it("returns em-dash for empty string", () => {
    expect(lastIriSegment("")).toBe("—");
  });
});
