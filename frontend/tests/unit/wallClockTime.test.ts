/**
 * TM1b — unit tests for wallClockTime utilities.
 *
 * Covers the formatDualTime() conversion function used in the chart tooltip
 * when timeReference = EXPERIMENT_RELATIVE and wallClockOffset is set.
 */
import { describe, it, expect } from "vitest";
import {
  formatDualTime,
  formatRelativeTime,
  formatAbsoluteTime,
} from "../../utils/wallClockTime";

// wallClockOffset: 2024-06-02T14:30:00.000 UTC
// = 1717338600000 ms
const OFFSET_MS = 1_717_338_600_000;

describe("formatDualTime", () => {
  it("t=0 maps to the wall-clock anchor", () => {
    const { relative, absolute } = formatDualTime(0, OFFSET_MS);
    expect(relative).toBe("t+0.000s");
    expect(absolute).toBe("2024-06-02 14:30:00.000 UTC");
  });

  it("t=8234 ms → t+8.234s and correct UTC", () => {
    const tMs = 8234; // 8.234 seconds from t=0
    const { relative, absolute } = formatDualTime(tMs, OFFSET_MS);
    expect(relative).toBe("t+8.234s");
    // 2024-06-02T14:30:08.234 UTC
    expect(absolute).toBe("2024-06-02 14:30:08.234 UTC");
  });

  it("negative relative time uses minus sign", () => {
    const { relative } = formatDualTime(-1000, OFFSET_MS);
    expect(relative).toBe("t−1.000s");
  });

  it("large relative time formats correctly", () => {
    // 3600 seconds = 1 hour after t=0
    const tMs = 3_600_000;
    const { relative, absolute } = formatDualTime(tMs, OFFSET_MS);
    expect(relative).toBe("t+3600.000s");
    // 2024-06-02T15:30:00.000 UTC
    expect(absolute).toBe("2024-06-02 15:30:00.000 UTC");
  });

  it("sub-millisecond precision is truncated at 3 decimal places", () => {
    // 1.5005 ms
    const tMs = 1.5005;
    const { relative } = formatDualTime(tMs, OFFSET_MS);
    // toFixed(3) rounds to 3 decimal places
    expect(relative).toMatch(/^t\+0\.00[12]s$/);
  });
});

describe("formatRelativeTime", () => {
  it("zero produces t+0.000s", () => {
    expect(formatRelativeTime(0)).toBe("t+0.000s");
  });

  it("positive offset", () => {
    expect(formatRelativeTime(5000)).toBe("t+5.000s");
  });

  it("negative offset uses minus sign character", () => {
    expect(formatRelativeTime(-2500)).toBe("t−2.500s");
  });
});

describe("formatAbsoluteTime", () => {
  it("produces ISO-style UTC string", () => {
    const result = formatAbsoluteTime(0, OFFSET_MS);
    expect(result).toBe("2024-06-02 14:30:00.000 UTC");
  });

  it("offset at t=8.234s", () => {
    const result = formatAbsoluteTime(8234, OFFSET_MS);
    expect(result).toBe("2024-06-02 14:30:08.234 UTC");
  });
});
