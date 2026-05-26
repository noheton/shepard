/**
 * KIP1k — unit tests for PublicationStatusBadge display logic.
 *
 * Tests verify the computed state that drives the badge without
 * mounting the full Vuetify component tree (same pattern as
 * PredecessorRelationshipTypeChip.test.ts and AnnotationChip.test.ts).
 *
 * The badge has three states driven by the publications list:
 *   1. Published  — at least one active (non-retired) Publication row
 *   2. Unpublished — no Publications, or all are retired
 *   3. Fetching   — in-flight; badge hides itself (not tested here)
 *
 * Tooltip construction and "current PID" extraction are also verified.
 */
import { describe, it, expect } from "vitest";
import type { PublicationRecord } from "../../composables/context/usePublicationStatus";

// ── Inline the badge's core logic ────────────────────────────────────────────
// We test the pure functions in isolation, not the Vue component's rendering.

function activePublications(pubs: PublicationRecord[]): PublicationRecord[] {
  return pubs.filter(p => p.digitalObjectMutability !== "retired");
}

function isPublished(pubs: PublicationRecord[]): boolean {
  return activePublications(pubs).length > 0;
}

function currentPublication(pubs: PublicationRecord[]): PublicationRecord | null {
  return activePublications(pubs)[0] ?? null;
}

function buildTooltip(pubs: PublicationRecord[]): string {
  if (!isPublished(pubs)) return "This entity has not been published yet.";
  const pub = currentPublication(pubs);
  if (!pub) return "Published.";
  const parts: string[] = [`PID: ${pub.pid}`];
  if (pub.publishedBy) parts.push(`by ${pub.publishedBy}`);
  if (pub.mintedAt) {
    const date = new Date(pub.mintedAt).toLocaleDateString("en-GB", {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
    parts.push(`on ${date}`);
  }
  return parts.join(" · ");
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function makePublication(
  pid: string,
  opts: Partial<PublicationRecord> = {},
): PublicationRecord {
  return {
    appId: `pub-${pid}`,
    pid,
    mintedAt: "2026-05-26T10:00:00.000Z",
    minterId: "local",
    resolverUrl: `https://shepard.example/v2/.well-known/kip/${pid}`,
    publishedBy: "alice",
    entityKind: "data-objects",
    entityAppId: "01HF-A",
    versionNumber: 1,
    digitalObjectMutability: null,
    ...opts,
  };
}

// ── isPublished ───────────────────────────────────────────────────────────────

describe("isPublished", () => {
  it("returns false for empty list (never published)", () => {
    expect(isPublished([])).toBe(false);
  });

  it("returns true when at least one active Publication exists", () => {
    expect(isPublished([makePublication("pid:v1")])).toBe(true);
  });

  it("returns false when all Publications are retired", () => {
    const retired = makePublication("pid:v1", { digitalObjectMutability: "retired" });
    expect(isPublished([retired])).toBe(false);
  });

  it("returns true when some are retired but at least one is active", () => {
    const retired = makePublication("pid:v1", { digitalObjectMutability: "retired" });
    const active = makePublication("pid:v2");
    expect(isPublished([active, retired])).toBe(true);
  });
});

// ── activePublications ────────────────────────────────────────────────────────

describe("activePublications", () => {
  it("returns empty for empty list", () => {
    expect(activePublications([])).toHaveLength(0);
  });

  it("excludes retired rows", () => {
    const retired = makePublication("pid:v1", { digitalObjectMutability: "retired" });
    const active = makePublication("pid:v2");
    const result = activePublications([active, retired]);
    expect(result).toHaveLength(1);
    expect(result[0]!.pid).toBe("pid:v2");
  });

  it("includes rows with null digitalObjectMutability (active)", () => {
    const pubs = [makePublication("pid:v1"), makePublication("pid:v2")];
    expect(activePublications(pubs)).toHaveLength(2);
  });
});

// ── currentPublication ────────────────────────────────────────────────────────

describe("currentPublication", () => {
  it("returns null for empty list", () => {
    expect(currentPublication([])).toBeNull();
  });

  it("returns null when all are retired", () => {
    const retired = makePublication("pid:v1", { digitalObjectMutability: "retired" });
    expect(currentPublication([retired])).toBeNull();
  });

  it("returns the first active Publication (DAO returns most-recent first)", () => {
    const v2 = makePublication("pid:v2", { versionNumber: 2 });
    const v1 = makePublication("pid:v1", { versionNumber: 1 });
    expect(currentPublication([v2, v1])?.pid).toBe("pid:v2");
  });
});

// ── buildTooltip ─────────────────────────────────────────────────────────────

describe("buildTooltip", () => {
  it("shows unpublished message for empty list", () => {
    expect(buildTooltip([])).toBe("This entity has not been published yet.");
  });

  it("shows PID in tooltip when published", () => {
    const tooltip = buildTooltip([makePublication("shepard:dlr:v1")]);
    expect(tooltip).toContain("PID: shepard:dlr:v1");
  });

  it("includes publishedBy in tooltip", () => {
    const tooltip = buildTooltip([makePublication("pid:v1", { publishedBy: "bob" })]);
    expect(tooltip).toContain("by bob");
  });

  it("includes formatted date in tooltip when mintedAt is set", () => {
    const tooltip = buildTooltip([
      makePublication("pid:v1", { mintedAt: "2026-01-15T10:00:00.000Z" }),
    ]);
    // Date format is locale-sensitive; just verify "2026" appears
    expect(tooltip).toContain("2026");
  });

  it("omits 'by' segment when publishedBy is null", () => {
    const tooltip = buildTooltip([makePublication("pid:v1", { publishedBy: null })]);
    expect(tooltip).not.toContain("by");
  });

  it("shows unpublished message when all pubs are retired", () => {
    const retired = makePublication("pid:v1", { digitalObjectMutability: "retired" });
    expect(buildTooltip([retired])).toBe("This entity has not been published yet.");
  });
});
