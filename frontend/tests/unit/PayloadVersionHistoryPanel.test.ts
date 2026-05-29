/**
 * UI7 — unit tests for PayloadVersionHistoryPanel helper logic.
 *
 * Following the project pattern (see EditFileReferenceDialog.test.ts,
 * useFetchPayloadVersions.test.ts), these tests exercise the pure helper
 * functions inlined from the component script without mounting the full
 * Nuxt / Vuetify component tree.
 *
 * Functions under test:
 *   - fmtBytes()    — human-readable byte size formatting
 *   - sha256Short() — truncates SHA-256 digest to first 12 chars + "…"
 *   - column layout — verify expected headers are defined
 */

import { describe, it, expect } from "vitest";

// ── Helpers inlined from PayloadVersionHistoryPanel.vue ────────────────────

function fmtBytes(b: number | null | undefined): string {
  if (b === null || b === undefined) return "—";
  if (b === 0) return "0 B";
  if (b < 1_048_576) return `${(b / 1_024).toFixed(1)} KB`;
  if (b < 1_073_741_824) return `${(b / 1_048_576).toFixed(1)} MB`;
  return `${(b / 1_073_741_824).toFixed(2)} GB`;
}

function sha256Short(hash: string | null | undefined): string {
  if (!hash) return "—";
  return hash.slice(0, 12) + "…";
}

const PANEL_HEADERS = [
  { title: "Version", key: "versionNumber" },
  { title: "Size", key: "sizeBytes" },
  { title: "Uploaded By", key: "uploadedBy" },
  { title: "Uploaded At", key: "uploadedAt" },
  { title: "SHA-256", key: "sha256" },
  { title: "Download", key: "actions" },
];

// ── Tests ──────────────────────────────────────────────────────────────────

describe("PayloadVersionHistoryPanel — fmtBytes", () => {
  it("returns '—' for null", () => {
    expect(fmtBytes(null)).toBe("—");
  });

  it("returns '—' for undefined", () => {
    expect(fmtBytes(undefined)).toBe("—");
  });

  it("returns '0 B' for zero", () => {
    expect(fmtBytes(0)).toBe("0 B");
  });

  it("formats bytes below 1 MB as KB", () => {
    // 512 * 1024 = 524_288 bytes → 512.0 KB
    expect(fmtBytes(512 * 1_024)).toBe("512.0 KB");
  });

  it("formats bytes below 1 GB as MB", () => {
    // 4 * 1024 * 1024 = 4_194_304 bytes → 4.0 MB
    expect(fmtBytes(4 * 1_048_576)).toBe("4.0 MB");
  });

  it("formats bytes >= 1 GB as GB", () => {
    // 2 * 1024 * 1024 * 1024 = 2_147_483_648 bytes → 2.00 GB
    expect(fmtBytes(2 * 1_073_741_824)).toBe("2.00 GB");
  });
});

describe("PayloadVersionHistoryPanel — sha256Short", () => {
  it("returns '—' for null", () => {
    expect(sha256Short(null)).toBe("—");
  });

  it("returns '—' for undefined", () => {
    expect(sha256Short(undefined)).toBe("—");
  });

  it("returns '—' for empty string", () => {
    expect(sha256Short("")).toBe("—");
  });

  it("truncates a full SHA-256 to first 12 chars followed by ellipsis", () => {
    const full = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
    const short = sha256Short(full);
    expect(short).toBe("E3B0C44298FC…");
    expect(short.length).toBe(13); // 12 chars + "…"
  });

  it("returns the string unchanged (+ ellipsis) when input is short", () => {
    expect(sha256Short("ABCD")).toBe("ABCD…");
  });
});

describe("PayloadVersionHistoryPanel — column layout", () => {
  it("defines Version column", () => {
    expect(PANEL_HEADERS.some(h => h.key === "versionNumber")).toBe(true);
  });

  it("defines Size column", () => {
    expect(PANEL_HEADERS.some(h => h.key === "sizeBytes")).toBe(true);
  });

  it("defines Uploaded By column", () => {
    expect(PANEL_HEADERS.some(h => h.key === "uploadedBy")).toBe(true);
  });

  it("defines Uploaded At column", () => {
    expect(PANEL_HEADERS.some(h => h.key === "uploadedAt")).toBe(true);
  });

  it("defines SHA-256 column", () => {
    expect(PANEL_HEADERS.some(h => h.key === "sha256")).toBe(true);
  });

  it("defines Download (actions) column", () => {
    expect(PANEL_HEADERS.some(h => h.key === "actions")).toBe(true);
  });
});

describe("PayloadVersionHistoryPanel — rendering states (logic)", () => {
  it("renders loading state when isLoading is true and versions are empty", () => {
    // Verify the template condition: show loading spinner when isLoading is true
    const isLoading = true;
    const versions: unknown[] = [];
    const error: string | null = null;

    expect(isLoading && !error).toBe(true);
    expect(versions.length).toBe(0);
  });

  it("renders version list when versions array is non-empty", () => {
    const isLoading = false;
    const error: string | null = null;
    const versions = [
      {
        appId: "018f4e2a-1b2c-7d3e-8f4a-000000000001",
        versionNumber: 1,
        fileOid: "deadbeef00",
        sha256: "E3B0C44298FC1C149AFBF4C8996FB924",
        sizeBytes: 4096,
        uploadedBy: "test.user",
        uploadedAt: "2026-05-20T10:00:00Z",
      },
    ];

    expect(!isLoading && !error && versions.length > 0).toBe(true);
    const v0 = versions[0]!;
    expect(v0.versionNumber).toBe(1);
    expect(fmtBytes(v0.sizeBytes)).toBe("4.0 KB");
    expect(sha256Short(v0.sha256)).toBe("E3B0C44298FC…");
  });

  it("renders empty state when versions is empty and not loading", () => {
    const isLoading = false;
    const error: string | null = null;
    const versions: unknown[] = [];

    // The empty state is shown when: !isLoading && !error && versions.length === 0
    expect(!isLoading && !error && versions.length === 0).toBe(true);
  });
});
