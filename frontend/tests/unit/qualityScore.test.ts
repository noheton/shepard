// AI1c — unit tests for the quality-score band helper.
// Pin the colour/label contract so a refactor that moves thresholds breaks fast.
import { describe, it, expect } from "vitest";
import { qualityScoreColor, qualityScoreLabel } from "../../utils/qualityScore";

describe("qualityScoreColor", () => {
  it("returns success for scores >= 0.8", () => {
    expect(qualityScoreColor(1.0)).toBe("success");
    expect(qualityScoreColor(0.8)).toBe("success");
    expect(qualityScoreColor(0.95)).toBe("success");
  });

  it("returns warning for scores in [0.5, 0.8)", () => {
    expect(qualityScoreColor(0.5)).toBe("warning");
    expect(qualityScoreColor(0.7)).toBe("warning");
    expect(qualityScoreColor(0.799)).toBe("warning");
  });

  it("returns error for scores below 0.5", () => {
    expect(qualityScoreColor(0.0)).toBe("error");
    expect(qualityScoreColor(0.49)).toBe("error");
    expect(qualityScoreColor(0.1)).toBe("error");
  });

  it("returns null for null or undefined (not yet scored)", () => {
    expect(qualityScoreColor(null)).toBeNull();
    expect(qualityScoreColor(undefined)).toBeNull();
  });
});

describe("qualityScoreLabel", () => {
  it("formats score to two decimal places", () => {
    expect(qualityScoreLabel(1.0)).toBe("1.00");
    expect(qualityScoreLabel(0.875)).toBe("0.88");
    expect(qualityScoreLabel(0.5)).toBe("0.50");
    expect(qualityScoreLabel(0.0)).toBe("0.00");
  });

  it("returns null for null or undefined (not yet scored)", () => {
    expect(qualityScoreLabel(null)).toBeNull();
    expect(qualityScoreLabel(undefined)).toBeNull();
  });
});
