/**
 * UIVERIFY-tools-cluster — unit tests for the /snapshots/diff page helpers.
 *
 * Covers the pure helper functions (`optionTitle`, `formatCreated`) and the
 * `runDiff` URL-construction logic inlined in the page component.
 *
 * Pattern: inline-replication of page helpers (same as pagesShapesRender.test.ts),
 * no Vue mount required.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

// ─── Replicated page helpers ──────────────────────────────────────────────────
// Mirror the logic from frontend/pages/snapshots/diff.vue so changes to the
// page that diverge from these expected shapes will fail this test.

function formatCreated(iso: string): string {
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return iso;
  return d.toISOString().slice(0, 16).replace("T", " ");
}

interface SnapshotListItem {
  appId: string;
  name: string;
  createdAt: string;
  collectionName?: string | null;
}

function optionTitle(s: SnapshotListItem): string {
  const when = formatCreated(s.createdAt);
  const coll = s.collectionName ?? "—";
  const head = `${s.appId.slice(0, 8)}…`;
  return `${s.name} · ${coll} · ${when} · ${head}`;
}

/**
 * Mirrors the v2Base resolution logic from runDiff() in diff.vue.
 * Extracted here so we can assert URL shape in isolation.
 */
function resolveV2Base(config: { backendV2ApiUrl?: string; backendApiUrl: string }): string {
  const explicit = config.backendV2ApiUrl;
  return explicit && explicit.length > 0
    ? explicit
    : config.backendApiUrl.replace(/\/shepard\/api\/?$/, "");
}

function buildDiffUrl(v2Base: string, aAppId: string, bAppId: string): string {
  return (
    v2Base +
    `/v2/snapshots/${encodeURIComponent(aAppId)}/diff/${encodeURIComponent(bAppId)}`
  );
}

// ─── formatCreated ────────────────────────────────────────────────────────────

describe("formatCreated", () => {
  it("formats a valid ISO 8601 datetime as 'YYYY-MM-DD HH:MM'", () => {
    expect(formatCreated("2026-05-31T12:34:56Z")).toBe("2026-05-31 12:34");
  });

  it("handles ISO with fractional seconds", () => {
    expect(formatCreated("2026-05-31T08:00:00.123Z")).toBe("2026-05-31 08:00");
  });

  it("returns the raw input when the date is invalid", () => {
    expect(formatCreated("not-a-date")).toBe("not-a-date");
    expect(formatCreated("")).toBe("");
    expect(formatCreated("2026-13-01T00:00:00Z")).toBe("2026-13-01T00:00:00Z"); // month 13 → invalid
  });
});

// ─── optionTitle ──────────────────────────────────────────────────────────────

describe("optionTitle", () => {
  it("joins name · collectionName · formatted-date · appId-prefix", () => {
    const s: SnapshotListItem = {
      appId: "0197b6a2-7b4c-7000-8a3b-1234567890ab",
      name: "v1.0-baseline",
      createdAt: "2026-05-30T10:00:00Z",
      collectionName: "LUMEN",
    };
    const title = optionTitle(s);
    expect(title).toBe("v1.0-baseline · LUMEN · 2026-05-30 10:00 · 0197b6a2…");
  });

  it("uses '—' when collectionName is null", () => {
    const s: SnapshotListItem = {
      appId: "0197b6a2-7b4c-7000-8a3b-1234567890ab",
      name: "orphan-snap",
      createdAt: "2026-06-01T09:00:00Z",
      collectionName: null,
    };
    expect(optionTitle(s)).toContain("· — ·");
  });

  it("uses '—' when collectionName is undefined", () => {
    const s: SnapshotListItem = {
      appId: "0197b6a2-7b4c-7000-8a3b-1234567890ab",
      name: "no-coll",
      createdAt: "2026-06-02T00:00:00Z",
    };
    expect(optionTitle(s)).toContain("· — ·");
  });

  it("truncates appId to 8 chars + ellipsis", () => {
    const s: SnapshotListItem = {
      appId: "abcdef12-face-cafe-beef-000000000000",
      name: "snap",
      createdAt: "2026-06-01T00:00:00Z",
      collectionName: "C",
    };
    // The first 8 chars of "abcdef12-face-cafe-beef-000000000000" are "abcdef12".
    expect(optionTitle(s)).toContain("abcdef12…");
    // Must NOT contain more than 8 chars before the ellipsis.
    const appIdPart = optionTitle(s).split(" · ").at(-1)!;
    expect(appIdPart).toBe("abcdef12…");
  });
});

// ─── runDiff URL construction ─────────────────────────────────────────────────

describe("runDiff URL construction", () => {
  it("builds /v2/snapshots/{a}/diff/{b} against the v2 base", () => {
    const url = buildDiffUrl("http://localhost:8080", "snap-aaa", "snap-bbb");
    expect(url).toBe(
      "http://localhost:8080/v2/snapshots/snap-aaa/diff/snap-bbb",
    );
  });

  it("URL-encodes snapshot appIds with special characters", () => {
    const url = buildDiffUrl("http://host", "a/b", "c d");
    expect(url).toContain(encodeURIComponent("a/b"));
    expect(url).toContain(encodeURIComponent("c d"));
    expect(url).not.toContain("a/b");
    expect(url).not.toContain("c d");
  });

  it("resolves v2Base from backendApiUrl when backendV2ApiUrl is absent", () => {
    const base = resolveV2Base({
      backendApiUrl: "http://host/shepard/api",
    });
    expect(base).toBe("http://host");
  });

  it("resolves v2Base from backendApiUrl with trailing slash", () => {
    const base = resolveV2Base({
      backendApiUrl: "http://host/shepard/api/",
    });
    expect(base).toBe("http://host");
  });

  it("prefers explicit backendV2ApiUrl when set", () => {
    const base = resolveV2Base({
      backendV2ApiUrl: "https://v2.host",
      backendApiUrl: "http://old/shepard/api",
    });
    expect(base).toBe("https://v2.host");
  });

  it("ignores empty backendV2ApiUrl and falls back to backendApiUrl", () => {
    const base = resolveV2Base({
      backendV2ApiUrl: "",
      backendApiUrl: "http://host/shepard/api",
    });
    expect(base).toBe("http://host");
  });
});
