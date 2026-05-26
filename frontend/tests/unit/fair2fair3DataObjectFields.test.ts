/**
 * FAIR2 + FAIR3 — unit tests for the computed property helpers added to the
 * DataObject detail page (index.vue).
 *
 * Rather than mounting the full page component (which requires a Quarkus-backed
 * API and a complex Nuxt context), we test the pure computation logic in
 * isolation using the same defensive cast pattern the page uses.
 *
 * What we verify:
 * - createdByOrcid is correctly extracted from a wire-shape object (FAIR2).
 * - embargoEndDate is correctly extracted from a wire-shape object (FAIR3).
 * - Both fields return null when absent / null (wire-compat: absent when unset).
 * - The ORCID link target is correctly formed (https://orcid.org/<id>).
 */
import { describe, it, expect } from "vitest";

// ── Helpers mirroring the computed accessors in the page component ──────────
// (inline copies so the test doesn't need a full mount)

function getCreatedByOrcid(
  obj: Record<string, unknown> | null
): string | null {
  if (!obj) return null;
  const raw = (obj as { createdByOrcid?: string | null }).createdByOrcid;
  return raw ?? null;
}

function getEmbargoEndDate(
  obj: Record<string, unknown> | null
): string | null {
  if (!obj) return null;
  const raw = (obj as { embargoEndDate?: string | null }).embargoEndDate;
  return raw ?? null;
}

function orcidLinkTarget(orcid: string): string {
  return `https://orcid.org/${orcid}`;
}

// ── FAIR2 tests ──────────────────────────────────────────────────────────────

describe("FAIR2 — createdByOrcid on DataObject detail", () => {
  it("returns the ORCID when present on the wire shape", () => {
    const wireShape = { createdByOrcid: "0000-0001-2345-6789" };
    expect(getCreatedByOrcid(wireShape)).toBe("0000-0001-2345-6789");
  });

  it("returns null when createdByOrcid is absent (pre-FAIR2 entity)", () => {
    const wireShape: Record<string, unknown> = { name: "TR-004" };
    expect(getCreatedByOrcid(wireShape)).toBeNull();
  });

  it("returns null when createdByOrcid is explicitly null", () => {
    const wireShape = { createdByOrcid: null };
    expect(getCreatedByOrcid(wireShape)).toBeNull();
  });

  it("returns null when the object itself is null", () => {
    expect(getCreatedByOrcid(null)).toBeNull();
  });

  it("builds the correct orcid.org link target", () => {
    const orcid = "0000-0002-9079-593X";
    expect(orcidLinkTarget(orcid)).toBe(`https://orcid.org/${orcid}`);
  });
});

// ── FAIR3 tests ──────────────────────────────────────────────────────────────

describe("FAIR3 — embargoEndDate on DataObject detail", () => {
  it("returns the embargo end date when present", () => {
    const wireShape = { embargoEndDate: "2027-12-31" };
    expect(getEmbargoEndDate(wireShape)).toBe("2027-12-31");
  });

  it("returns null when embargoEndDate is absent (unset / pre-FAIR3)", () => {
    const wireShape: Record<string, unknown> = { name: "TR-004" };
    expect(getEmbargoEndDate(wireShape)).toBeNull();
  });

  it("returns null when embargoEndDate is explicitly null", () => {
    const wireShape = { embargoEndDate: null };
    expect(getEmbargoEndDate(wireShape)).toBeNull();
  });

  it("returns null when the object itself is null", () => {
    expect(getEmbargoEndDate(null)).toBeNull();
  });

  it("correctly surfaces a date set on a realistic wire shape", () => {
    const wireShape = {
      id: 42,
      name: "AFP-Layup-Step-1",
      description: "AFP layup with EMBARGOED data",
      accessRights: "EMBARGOED",
      embargoEndDate: "2028-06-30",
      license: "CC-BY-4.0",
    };
    expect(getEmbargoEndDate(wireShape)).toBe("2028-06-30");
  });
});
