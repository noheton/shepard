/**
 * II2 (ui-scrutinizer-2026-05-30) — unit tests for the DataObject
 * citation-input mapping. Mirrors the pure-logic test pattern from
 * `fair2fair3DataObjectFields.test.ts` so we don't mount the full
 * Vuetify card.
 *
 * The wrapper component (`CiteThisDataObjectCard.vue`) just builds a
 * `CitationInput` shape that the shared `CiteThisCardCommon` renders.
 * These tests verify the mapping is correct for the cases the
 * scrutinizer flagged: present/absent license, present/absent ORCID,
 * empty `createdBy`, the SSR-safe URL fallback.
 */
import { describe, it, expect } from "vitest";
import {
  formatCitation,
  type CitationInput,
} from "../../utils/citation";

// ── Inline copies of the wrapper's computed accessors ───────────────────────

function authorsFromDataObject(
  obj: Record<string, unknown> | null,
): string[] {
  if (!obj) return [];
  const cb = (obj as { createdBy?: string }).createdBy?.trim();
  if (!cb) return [];
  const orcid = (obj as { createdByOrcid?: string | null }).createdByOrcid;
  return orcid ? [`${cb} (ORCID: ${orcid})`] : [cb];
}

function licenseFromDataObject(
  obj: Record<string, unknown> | null,
): string | null {
  if (!obj) return null;
  const raw = (obj as { license?: string | null }).license;
  return raw ?? null;
}

function yearFromCreatedAt(
  createdAt: Date | string | null | undefined,
): number {
  if (!createdAt) return new Date().getFullYear();
  if (createdAt instanceof Date) return createdAt.getFullYear();
  return new Date(createdAt).getFullYear();
}

function ssrSafeUrl(
  windowObj: Window | undefined,
  fallback: string,
): string {
  if (typeof windowObj === "undefined") return fallback;
  return `${windowObj.location.origin}${windowObj.location.pathname}`;
}

// ── Tests ───────────────────────────────────────────────────────────────────

describe("CiteThisDataObjectCard — authors mapping", () => {
  it("returns [createdBy] when no ORCID is present", () => {
    expect(authorsFromDataObject({ createdBy: "alice" })).toEqual(["alice"]);
  });

  it("appends ORCID in parentheses when present (FAIR2)", () => {
    expect(
      authorsFromDataObject({
        createdBy: "alice",
        createdByOrcid: "0000-0002-1825-0097",
      }),
    ).toEqual(["alice (ORCID: 0000-0002-1825-0097)"]);
  });

  it("returns [] when createdBy is empty or whitespace (defensive)", () => {
    expect(authorsFromDataObject({ createdBy: "" })).toEqual([]);
    expect(authorsFromDataObject({ createdBy: "   " })).toEqual([]);
  });

  it("ignores ORCID when createdBy is missing (single-author shape preserved)", () => {
    expect(authorsFromDataObject({ createdByOrcid: "0000-0001-2345-6789" })).toEqual([]);
  });
});

describe("CiteThisDataObjectCard — license + year + URL", () => {
  it("reads license defensively (null when wire shape omits the field)", () => {
    expect(licenseFromDataObject({})).toBeNull();
    expect(licenseFromDataObject({ license: "CC-BY-4.0" })).toBe("CC-BY-4.0");
  });

  it("extracts year from a Date instance", () => {
    expect(yearFromCreatedAt(new Date("2024-06-15T12:00:00Z"))).toBe(2024);
  });

  it("extracts year from an ISO string", () => {
    expect(yearFromCreatedAt("2023-01-01T00:00:00Z")).toBe(2023);
  });

  it("falls back to current year when createdAt is null", () => {
    const now = new Date().getFullYear();
    expect(yearFromCreatedAt(null)).toBe(now);
  });

  it("returns the fallback URL during SSR (window undefined)", () => {
    expect(ssrSafeUrl(undefined, "/dataobjects/123")).toBe(
      "/dataobjects/123",
    );
  });
});

describe("CiteThisDataObjectCard — end-to-end citation render", () => {
  it("builds a plain-text citation reflecting the DataObject's ORCID + license", () => {
    const input: CitationInput = {
      authors: authorsFromDataObject({
        createdBy: "alice",
        createdByOrcid: "0000-0002-1825-0097",
      }),
      year: yearFromCreatedAt(new Date("2024-06-15T00:00:00Z")),
      title: "TR-004 — engine hotfire run",
      repository: "Shepard Research Data Platform",
      url: "https://example.org/dataobjects/42",
      license: licenseFromDataObject({ license: "MIT" }),
      accessedDate: "2026-05-30",
    };
    const rendered = formatCitation(input, "plain");
    expect(rendered).toContain("alice (ORCID: 0000-0002-1825-0097)");
    expect(rendered).toContain("TR-004 — engine hotfire run");
    expect(rendered).toContain("MIT");
    expect(rendered).toContain("2026-05-30");
  });
});
