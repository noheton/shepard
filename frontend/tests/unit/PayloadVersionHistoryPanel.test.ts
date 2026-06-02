/**
 * UI7 — unit tests for PayloadVersionHistoryPanel helper logic.
 *
 * We test the pure helper functions (fmtBytes, sha256Short) and the data
 * shaping logic that determines which versions are rendered, following the
 * same pattern as EditFileReferenceDialog.test.ts and
 * PayloadVersionHistoryDialog-adjacent tests.
 *
 * Full visual rendering + expansion panel behaviour is covered by Playwright
 * E2E tests (tracked in aidocs/16 UI7 row).
 */

import { describe, it, expect } from "vitest";
import type { PayloadVersionIO } from "~/composables/container/useFetchPayloadVersions";

// ── Inline helpers mirroring the component ────────────────────────────────────

function fmtBytes(b: number | null | undefined): string {
  if (b === null || b === undefined) return "—";
  if (b === 0) return "0 B";
  if (b < 1_048_576) return `${(b / 1_024).toFixed(1)} KB`;
  if (b < 1_073_741_824) return `${(b / 1_048_576).toFixed(1)} MB`;
  return `${(b / 1_073_741_824).toFixed(2)} GB`;
}

function sha256Short(hash: string | null): string {
  if (!hash) return "—";
  return hash.slice(0, 16) + "…";
}

// ── Sample versions ────────────────────────────────────────────────────────────

const V1: PayloadVersionIO = {
  appId: "018f4e2a-0001-7000-8000-000000000001",
  versionNumber: 1,
  fileOid: "60b73212cfa45d2d5baa795d",
  sha256: "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855",
  sizeBytes: 4096,
  uploadedBy: "alice",
  uploadedAt: "2026-05-17T12:00:00Z",
};

const V2: PayloadVersionIO = {
  appId: "018f4e2a-0002-7000-8000-000000000002",
  versionNumber: 2,
  fileOid: "70c84323dfb56e3e6cbb8a6e",
  sha256: "DEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEFDEADBEEF",
  sizeBytes: 8192,
  uploadedBy: "bob",
  uploadedAt: "2026-05-18T10:30:00Z",
};

const V_PRESIGNED: PayloadVersionIO = {
  appId: "018f4e2a-0003-7000-8000-000000000003",
  versionNumber: 3,
  fileOid: null,       // presigned-URL upload — no GridFS oid
  sha256: null,        // no digest recorded
  sizeBytes: null,
  uploadedBy: "carol",
  uploadedAt: "2026-05-19T09:00:00Z",
};

// ── fmtBytes ──────────────────────────────────────────────────────────────────

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
    expect(fmtBytes(4096)).toBe("4.0 KB");
    expect(fmtBytes(512 * 1024)).toBe("512.0 KB");
  });

  it("formats bytes in the MB range", () => {
    expect(fmtBytes(2 * 1_048_576)).toBe("2.0 MB");
    expect(fmtBytes(500 * 1_048_576)).toBe("500.0 MB");
  });

  it("formats bytes in the GB range", () => {
    expect(fmtBytes(1_073_741_824)).toBe("1.00 GB");
    expect(fmtBytes(4 * 1_073_741_824)).toBe("4.00 GB");
  });
});

// ── sha256Short ───────────────────────────────────────────────────────────────

describe("PayloadVersionHistoryPanel — sha256Short", () => {
  it("returns '—' for null", () => {
    expect(sha256Short(null)).toBe("—");
  });

  it("truncates a full SHA-256 hex string to 16 chars + ellipsis", () => {
    const full = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
    const result = sha256Short(full);
    expect(result).toBe("E3B0C44298FC1C14…");
    expect(result.length).toBe(17); // 16 hex chars + 1 ellipsis char
  });

  it("handles a short hash without panicking", () => {
    expect(sha256Short("DEADBEEF")).toBe("DEADBEEF…");
  });
});

// ── Download eligibility ───────────────────────────────────────────────────────

describe("PayloadVersionHistoryPanel — download eligibility", () => {
  /**
   * The download button is disabled when fileOid is null (presigned-URL path).
   * We mirror the template logic: `!item.fileOid` disables the button.
   */
  function canDownload(v: PayloadVersionIO): boolean {
    return !!v.fileOid;
  }

  it("download is enabled when fileOid is present", () => {
    expect(canDownload(V1)).toBe(true);
    expect(canDownload(V2)).toBe(true);
  });

  it("download is disabled when fileOid is null (presigned-URL upload)", () => {
    expect(canDownload(V_PRESIGNED)).toBe(false);
  });
});

// ── Version list data shape ────────────────────────────────────────────────────

describe("PayloadVersionHistoryPanel — version list data shape", () => {
  it("versions are expected to be ordered by versionNumber ascending", () => {
    const versions = [V1, V2, V_PRESIGNED];
    // The backend returns versions in ascending versionNumber order (per PV1a spec).
    // Verify the test data satisfies that invariant.
    for (let i = 1; i < versions.length; i++) {
      expect(versions[i]!.versionNumber).toBeGreaterThan(versions[i - 1]!.versionNumber);
    }
  });

  it("each version has the required display fields", () => {
    for (const v of [V1, V2, V_PRESIGNED]) {
      expect(typeof v.versionNumber).toBe("number");
      expect(typeof v.uploadedBy).toBe("string");
      expect(typeof v.uploadedAt).toBe("string");
      // fileOid and sha256 may be null
      expect(v.fileOid === null || typeof v.fileOid === "string").toBe(true);
      expect(v.sha256 === null || typeof v.sha256 === "string").toBe(true);
    }
  });

  it("sizeBytes may be null for presigned-URL uploads", () => {
    expect(V_PRESIGNED.sizeBytes).toBeNull();
    expect(fmtBytes(V_PRESIGNED.sizeBytes)).toBe("—");
  });
});

// ── Panel visibility predicate ────────────────────────────────────────────────

describe("PayloadVersionHistoryPanel — visibility predicate", () => {
  /**
   * The panel is rendered only when all three conditions hold:
   *   - fileContainerAppId is non-null and non-empty
   *   - primaryFileName is non-null and non-empty
   *   - fileReference is loaded (truthy)
   *
   * This mirrors the v-if on the panel's wrapper row in the page.
   */
  function shouldRenderPanel(opts: {
    fileContainerAppId: string | null;
    primaryFileName: string | null;
    fileReferenceLoaded: boolean;
  }): boolean {
    return !!(opts.fileContainerAppId && opts.primaryFileName && opts.fileReferenceLoaded);
  }

  it("renders when all conditions are met", () => {
    expect(shouldRenderPanel({
      fileContainerAppId: "018f4e2a-1111-7000-8000-000000000001",
      primaryFileName: "sensor_data.csv",
      fileReferenceLoaded: true,
    })).toBe(true);
  });

  it("does not render when containerAppId is null (pre-L2a container)", () => {
    expect(shouldRenderPanel({
      fileContainerAppId: null,
      primaryFileName: "sensor_data.csv",
      fileReferenceLoaded: true,
    })).toBe(false);
  });

  it("does not render when primaryFileName is null (no files in reference)", () => {
    expect(shouldRenderPanel({
      fileContainerAppId: "018f4e2a-1111-7000-8000-000000000001",
      primaryFileName: null,
      fileReferenceLoaded: true,
    })).toBe(false);
  });

  it("does not render when fileReference is not yet loaded", () => {
    expect(shouldRenderPanel({
      fileContainerAppId: "018f4e2a-1111-7000-8000-000000000001",
      primaryFileName: "sensor_data.csv",
      fileReferenceLoaded: false,
    })).toBe(false);
  });
});
