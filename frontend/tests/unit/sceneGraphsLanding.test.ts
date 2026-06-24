/**
 * SCENEGRAPH-LIST-1 — unit tests for the `/scene-graphs` landing page
 * helpers extracted to `utils/sceneGraphsLanding.ts`.
 *
 * Mirrors the test shape of `sectionLanding.test.ts` + `toolsLanding.test.ts` —
 * pure-helper tests so we don't need to mount Vuetify's `v-data-table-server`
 * to verify the three-branch display, row truncation, and date formatting.
 */
import { describe, it, expect } from "vitest";
import {
  formatEpochMillis,
  resolveLandingBranch,
} from "../../utils/sceneGraphsLanding";
// II3 (ui-scrutinizer-2026-05-30): `truncateAppId` moved to the canonical
// `~/utils/appId.ts` so the chip + helpers share one shape.
import { truncateAppId } from "../../utils/appId";

describe("sceneGraphsLanding — resolveLandingBranch", () => {
  it("returns 'help' when the catalogue is empty (no scenes seeded yet)", () => {
    // Empty list, no loading, no error → show the help card (preserved from
    // the pre-SCENEGRAPH-LIST-1 fallback so a fresh instance is still
    // discoverable).
    expect(resolveLandingBranch(0, false, null)).toBe("help");
  });

  it("returns 'table' when rows are loaded (renders rows from a mocked list)", () => {
    expect(resolveLandingBranch(3, false, null)).toBe("table");
  });

  it("returns 'table' during an in-flight fetch even with zero rows", () => {
    // Don't flash the empty card while loading — keep showing the table card
    // so its skeleton/loading indicator is visible.
    expect(resolveLandingBranch(0, true, null)).toBe("table");
  });

  it("returns 'error' when the fetch failed and we have no rows to fall back on", () => {
    expect(resolveLandingBranch(0, false, "Network error")).toBe("error");
  });

  it("keeps showing 'table' when a previous page is still in memory after a fresh error", () => {
    // Stale-but-valid rows are better than wiping the table on a refresh
    // failure (the user can still navigate to a known scene).
    expect(resolveLandingBranch(5, false, "Refresh failed")).toBe("table");
  });
});

describe("sceneGraphsLanding — truncateAppId", () => {
  it("returns the full string when it's short enough already", () => {
    expect(truncateAppId("short")).toBe("short");
    expect(truncateAppId("0123456789abc")).toBe("0123456789abc"); // 13 chars
  });

  it("truncates a UUID v7 to <8-char-head>…<4-char-tail>", () => {
    expect(truncateAppId("0197b6a2-aaaa-7000-8000-000000000001")).toBe(
      "0197b6a2…0001",
    );
  });

  it("handles empty input gracefully", () => {
    expect(truncateAppId("")).toBe("");
  });
});

describe("sceneGraphsLanding — formatEpochMillis", () => {
  it("returns em-dash for null / undefined (pre-SCENEGRAPH-LIST-1 rows)", () => {
    expect(formatEpochMillis(null)).toBe("—");
    expect(formatEpochMillis(undefined)).toBe("—");
  });

  it("formats a valid epoch into a short DD-Mon-YYYY string", () => {
    // 2024-06-15T12:00:00Z = 1718452800000ms — locale-formatted, so we check
    // for the components rather than the exact string (avoids TZ flakiness).
    const out = formatEpochMillis(1718452800000);
    expect(out).toMatch(/2024/);
    expect(out).toMatch(/Jun/);
  });
});
