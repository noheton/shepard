/**
 * UX-ANNO1 — unit tests for the normalizePropertyHint utility.
 *
 * The function converts machine-readable channel identifiers (snake_case,
 * kebab-case, camelCase) into human-readable search terms for the annotation
 * property search pre-fill.
 */
import { describe, it, expect } from "vitest";
import { normalizePropertyHint } from "~/utils/annotationPropertyHint";

describe("normalizePropertyHint", () => {
  it("converts snake_case to space-separated words", () => {
    expect(normalizePropertyHint("compaction_force")).toBe("compaction force");
  });

  it("converts kebab-case to space-separated words", () => {
    expect(normalizePropertyHint("TCP-temperature")).toBe("TCP temperature");
  });

  it("converts camelCase to space-separated words", () => {
    expect(normalizePropertyHint("vibrationRmsX")).toBe("vibration Rms X");
  });

  it("handles mixed snake_case and camelCase", () => {
    expect(normalizePropertyHint("turboPump_vibration")).toBe("turbo Pump vibration");
  });

  it("trims leading and trailing whitespace", () => {
    expect(normalizePropertyHint("_channel_name_")).toBe("channel name");
  });

  it("collapses multiple consecutive separators", () => {
    expect(normalizePropertyHint("a__b--c")).toBe("a b c");
  });

  it("passes through a plain word unchanged", () => {
    expect(normalizePropertyHint("pressure")).toBe("pressure");
  });

  it("passes through an already-spaced label unchanged", () => {
    expect(normalizePropertyHint("mass flow rate")).toBe("mass flow rate");
  });
});
