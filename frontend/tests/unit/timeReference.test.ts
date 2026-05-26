/**
 * TM1a — unit tests for time-reference helper logic.
 *
 * Tests cover:
 *   - nsToIsoString: nanosecond UTC epoch → human-readable string
 *   - dateParsToNs: "YYYY-MM-DD" + "HH:mm:ss" → nanoseconds
 *   - round-trip: date → ns → date
 *   - validation: EXPERIMENT_RELATIVE requires offset
 */
import { describe, it, expect } from "vitest";

// ── helpers extracted from TimeReferencePanel.vue ────────────────────────────

function nsToIsoString(ns: number | null | undefined): string {
  if (ns == null) return "—";
  const ms = ns / 1_000_000;
  return new Date(ms).toISOString().replace("T", " ").slice(0, 23) + " UTC";
}

function nsToDateParts(ns: number | null | undefined): { date: string; time: string } {
  if (ns == null) return { date: "", time: "" };
  const ms = ns / 1_000_000;
  const d = new Date(ms);
  const date = d.toISOString().slice(0, 10);
  const time = d.toISOString().slice(11, 19);
  return { date, time };
}

function dateParsToNs(dateStr: string, timeStr: string): number | null {
  const iso = `${dateStr}T${timeStr}Z`;
  const ms = Date.parse(iso);
  if (isNaN(ms)) return null;
  return ms * 1_000_000;
}

// ── validation helpers ────────────────────────────────────────────────────────

function isOffsetRequired(mode: string): boolean {
  return mode === "EXPERIMENT_RELATIVE";
}

function isOffsetValid(mode: string, offsetNs: number | null): boolean {
  if (!isOffsetRequired(mode)) return true;
  return offsetNs !== null;
}

// ── tests ─────────────────────────────────────────────────────────────────────

describe("nsToIsoString", () => {
  it("returns — for null", () => {
    expect(nsToIsoString(null)).toBe("—");
  });

  it("returns — for undefined", () => {
    expect(nsToIsoString(undefined)).toBe("—");
  });

  it("formats known epoch correctly", () => {
    // 2023-11-14 22:13:20.000 UTC = 1700000000000 ms = 1700000000000000000 ns
    const ns = 1_700_000_000_000 * 1_000_000;
    const result = nsToIsoString(ns);
    expect(result).toContain("2023-11-14");
    expect(result).toContain("UTC");
  });

  it("includes milliseconds in output", () => {
    // 1700000000500 ms
    const ns = 1_700_000_000_500 * 1_000_000;
    const result = nsToIsoString(ns);
    expect(result).toContain(".500");
  });
});

describe("nsToDateParts", () => {
  it("returns empty strings for null", () => {
    const { date, time } = nsToDateParts(null);
    expect(date).toBe("");
    expect(time).toBe("");
  });

  it("returns YYYY-MM-DD for date part", () => {
    const ns = 1_700_000_000_000 * 1_000_000; // 2023-11-14
    const { date } = nsToDateParts(ns);
    expect(date).toMatch(/^\d{4}-\d{2}-\d{2}$/);
    expect(date).toBe("2023-11-14");
  });

  it("returns HH:mm:ss for time part", () => {
    const ns = 1_700_000_000_000 * 1_000_000;
    const { time } = nsToDateParts(ns);
    expect(time).toMatch(/^\d{2}:\d{2}:\d{2}$/);
  });
});

describe("dateParsToNs", () => {
  it("returns null for invalid date", () => {
    expect(dateParsToNs("not-a-date", "00:00:00")).toBeNull();
  });

  it("returns null for invalid time", () => {
    expect(dateParsToNs("2023-11-14", "not-a-time")).toBeNull();
  });

  it("returns positive integer for valid inputs", () => {
    const ns = dateParsToNs("2023-11-14", "22:13:20");
    expect(ns).not.toBeNull();
    expect(typeof ns).toBe("number");
    expect(ns!).toBeGreaterThan(0);
  });

  it("round-trips through nsToDateParts", () => {
    const original = 1_700_000_000_000 * 1_000_000;
    const { date, time } = nsToDateParts(original);
    const roundTripped = dateParsToNs(date, time);
    // Round-trip accuracy: within 1 second (ignores sub-second ns precision lost in HH:mm:ss)
    expect(Math.abs(roundTripped! - original)).toBeLessThan(1_000 * 1_000_000);
  });
});

describe("TM1a validation", () => {
  it("WALL_CLOCK mode does not require offset", () => {
    expect(isOffsetValid("WALL_CLOCK", null)).toBe(true);
    expect(isOffsetValid("WALL_CLOCK", 0)).toBe(true);
  });

  it("EXPERIMENT_RELATIVE mode requires offset", () => {
    expect(isOffsetValid("EXPERIMENT_RELATIVE", null)).toBe(false);
  });

  it("EXPERIMENT_RELATIVE mode is valid when offset is provided", () => {
    expect(isOffsetValid("EXPERIMENT_RELATIVE", 1_700_000_000_000_000_000)).toBe(true);
  });

  it("EXPERIMENT_RELATIVE mode is valid when offset is zero", () => {
    // t=0 is valid (Unix epoch anchor)
    expect(isOffsetValid("EXPERIMENT_RELATIVE", 0)).toBe(true);
  });
});
