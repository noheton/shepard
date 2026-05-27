/**
 * UI-011b — unit tests for the `useDoCountChip` utility.
 *
 * Verifies that every bucket boundary maps to the expected Vuetify
 * colour token.
 *
 * Bucket spec:
 *   0           → "default"  (grey  — empty)
 *   1–10        → "success"  (green — small)
 *   11–100      → "info"     (blue  — medium)
 *   101–1000    → "warning"  (orange — large)
 *   > 1000      → "error"    (red   — very large)
 */
import { describe, it, expect } from "vitest";
import { useDoCountChip } from "~/utils/doCountChip";

describe("useDoCountChip — colour bucketing", () => {
  // ── exact-zero ────────────────────────────────────────────────────────────
  it("returns 'default' (grey) for 0 DOs", () => {
    expect(useDoCountChip(0).color).toBe("default");
  });

  // ── small bucket (1–10) ───────────────────────────────────────────────────
  it("returns 'success' (green) for 1 DO", () => {
    expect(useDoCountChip(1).color).toBe("success");
  });

  it("returns 'success' (green) for 10 DOs (upper edge of small)", () => {
    expect(useDoCountChip(10).color).toBe("success");
  });

  // ── medium bucket (11–100) ────────────────────────────────────────────────
  it("returns 'info' (blue) for 11 DOs (lower edge of medium)", () => {
    expect(useDoCountChip(11).color).toBe("info");
  });

  it("returns 'info' (blue) for 50 DOs", () => {
    expect(useDoCountChip(50).color).toBe("info");
  });

  it("returns 'info' (blue) for 100 DOs (upper edge of medium)", () => {
    expect(useDoCountChip(100).color).toBe("info");
  });

  // ── large bucket (101–1000) ───────────────────────────────────────────────
  it("returns 'warning' (orange) for 101 DOs (lower edge of large)", () => {
    expect(useDoCountChip(101).color).toBe("warning");
  });

  it("returns 'warning' (orange) for 500 DOs", () => {
    expect(useDoCountChip(500).color).toBe("warning");
  });

  it("returns 'warning' (orange) for 1000 DOs (upper edge of large)", () => {
    expect(useDoCountChip(1000).color).toBe("warning");
  });

  // ── very large bucket (> 1000) ────────────────────────────────────────────
  it("returns 'error' (red) for 1001 DOs (lower edge of very large)", () => {
    expect(useDoCountChip(1001).color).toBe("error");
  });

  it("returns 'error' (red) for 8514 DOs (MFFD-Dropbox scale)", () => {
    expect(useDoCountChip(8514).color).toBe("error");
  });

  // ── label is always the stringified number ────────────────────────────────
  it("label matches the raw count as a string", () => {
    for (const n of [0, 1, 10, 11, 100, 101, 1000, 1001, 8514]) {
      expect(useDoCountChip(n).label).toBe(String(n));
    }
  });
});
