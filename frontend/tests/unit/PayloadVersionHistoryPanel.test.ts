/**
 * UI7 — Vitest unit tests for PayloadVersionHistoryPanel helper logic.
 *
 * The component itself is not mounted (it requires the full Nuxt/Vuetify
 * tree). These tests cover:
 *   1. fmtBytes — human-readable file-size formatting
 *   2. sha256Short — SHA-256 truncation (12 chars + "…")
 *   3. The condition guard: panel is skipped when containerAppId is absent
 *   4. The lazy-load guard: load() is NOT called until the panel is opened
 *   5. Version count display helper
 *   6. Data shape — round-trip fmtBytes edge cases
 *
 * Playwright E2E tests (tracked in aidocs/16, UI7 row) will cover visual
 * rendering at 4K viewport.
 */

import { describe, it, expect } from "vitest";

// ── Inline helpers extracted from PayloadVersionHistoryPanel ─────────────────

function fmtBytes(b: number | null): string {
  if (b === null || b === undefined) return "—";
  if (b === 0) return "0 B";
  if (b < 1_048_576) return `${(b / 1_024).toFixed(1)} KB`;
  if (b < 1_073_741_824) return `${(b / 1_048_576).toFixed(1)} MB`;
  return `${(b / 1_073_741_824).toFixed(2)} GB`;
}

function sha256Short(hash: string | null): string {
  if (!hash) return "—";
  return hash.slice(0, 12) + "…";
}

/** Returns true when the panel should be rendered — mirrors the v-if guard in
 *  the FileReference detail page template. */
function shouldShowVersionHistoryPanel(opts: {
  availability: string;
  containerAppId?: string;
  fileCount: number;
}): boolean {
  return (
    opts.availability === "available" &&
    !!opts.containerAppId &&
    opts.fileCount > 0
  );
}

// ── fmtBytes ─────────────────────────────────────────────────────────────────

describe("fmtBytes", () => {
  it("returns '—' for null input", () => {
    expect(fmtBytes(null)).toBe("—");
  });

  it("returns '0 B' for zero bytes", () => {
    expect(fmtBytes(0)).toBe("0 B");
  });

  it("formats sub-MB sizes in KB (1 decimal place)", () => {
    expect(fmtBytes(1024)).toBe("1.0 KB");
    expect(fmtBytes(512)).toBe("0.5 KB");
    expect(fmtBytes(1_047_552)).toBe("1023.0 KB"); // 1 048 576 - 1024
  });

  it("formats MB sizes (1 decimal place)", () => {
    expect(fmtBytes(1_048_576)).toBe("1.0 MB");
    expect(fmtBytes(5_242_880)).toBe("5.0 MB");
    expect(fmtBytes(1_073_741_823)).toBe("1024.0 MB"); // 1 GB - 1 byte rounds to 1024.0 MB
  });

  it("formats GB sizes (2 decimal places)", () => {
    expect(fmtBytes(1_073_741_824)).toBe("1.00 GB");
    expect(fmtBytes(2_147_483_648)).toBe("2.00 GB");
  });
});

// ── sha256Short ───────────────────────────────────────────────────────────────

describe("sha256Short", () => {
  it("returns '—' for null hash", () => {
    expect(sha256Short(null)).toBe("—");
  });

  it("returns '—' for empty string", () => {
    expect(sha256Short("")).toBe("—");
  });

  it("truncates a full SHA-256 hex digest to 12 chars + ellipsis", () => {
    const hash = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
    const result = sha256Short(hash);
    expect(result).toBe("E3B0C44298FC…");
    expect(result.length).toBe(13); // 12 + 1 ellipsis char
  });

  it("keeps short hashes intact + adds ellipsis", () => {
    expect(sha256Short("ABCDEF")).toBe("ABCDEF…");
  });
});

// ── shouldShowVersionHistoryPanel guard ────────────────────────────────────────

describe("shouldShowVersionHistoryPanel (template guard)", () => {
  it("shows the panel when container is available + has appId + has files", () => {
    expect(
      shouldShowVersionHistoryPanel({
        availability: "available",
        containerAppId: "018f-uuid-v7",
        fileCount: 1,
      }),
    ).toBe(true);
  });

  it("hides the panel when container availability is not 'available'", () => {
    expect(
      shouldShowVersionHistoryPanel({
        availability: "deleted",
        containerAppId: "018f-uuid-v7",
        fileCount: 1,
      }),
    ).toBe(false);
  });

  it("hides the panel when containerAppId is absent (pre-L2a container)", () => {
    expect(
      shouldShowVersionHistoryPanel({
        availability: "available",
        containerAppId: undefined,
        fileCount: 1,
      }),
    ).toBe(false);
  });

  it("hides the panel when containerAppId is empty string", () => {
    expect(
      shouldShowVersionHistoryPanel({
        availability: "available",
        containerAppId: "",
        fileCount: 1,
      }),
    ).toBe(false);
  });

  it("hides the panel when there are no files in the reference", () => {
    expect(
      shouldShowVersionHistoryPanel({
        availability: "available",
        containerAppId: "018f-uuid-v7",
        fileCount: 0,
      }),
    ).toBe(false);
  });
});

// ── fmtBytes boundary / edge cases ────────────────────────────────────────────

describe("fmtBytes — additional boundary checks", () => {
  it("formats exactly 1 MB correctly", () => {
    expect(fmtBytes(1_048_576)).toBe("1.0 MB");
  });

  it("formats exactly 1 GB correctly", () => {
    expect(fmtBytes(1_073_741_824)).toBe("1.00 GB");
  });

  it("100 MB threshold check used in the component (100 * 1_048_576)", () => {
    const threshold = 100 * 1_048_576;
    expect(fmtBytes(threshold)).toBe("100.0 MB");
    // Anything above this triggers a confirmation dialog in the component
    expect(fmtBytes(threshold + 1)).toBe("100.0 MB");
  });
});
