/**
 * RDM-001 — citation formatter tests.
 *
 * 16-case matrix: 4 formats × license-present/absent × single/multi-author.
 * Plus a handful of edge-case probes (empty authors, brace-in-title BibTeX
 * escaping, ISO date parsing for CSL JSON, deterministic BibTeX key).
 */
import { describe, it, expect } from "vitest";
import {
  formatCitation,
  CITATION_FORMATS_ORDER,
  CITATION_FORMAT_LABELS,
  type CitationInput,
  type CitationFormat,
} from "../../utils/citation";

const baseInput = (overrides: Partial<CitationInput> = {}): CitationInput => ({
  authors: ["Krebs, F."],
  year: 2024,
  title: "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)",
  repository: "Shepard Research Data Platform",
  url: "https://shepard.nuclide.systems/collections/42",
  license: "MIT",
  accessedDate: "2026-05-24",
  ...overrides,
});

const MULTI_AUTHORS = ["Krebs, F.", "Müller, M.", "Weber, S."];

describe("formatCitation — 16-case matrix", () => {
  // Build the cross-product matrix programmatically so a missing case
  // jumps out at review.
  const formats: CitationFormat[] = ["plain", "bibtex", "ris", "csl-json"];
  const licenseStates: { label: string; value: string | null }[] = [
    { label: "with-license", value: "MIT" },
    { label: "no-license", value: null },
  ];
  const authorStates: { label: string; value: string[] }[] = [
    { label: "single-author", value: ["Krebs, F."] },
    { label: "multi-author", value: MULTI_AUTHORS },
  ];

  for (const fmt of formats) {
    for (const lic of licenseStates) {
      for (const auth of authorStates) {
        it(`${fmt} / ${lic.label} / ${auth.label} renders without error`, () => {
          const out = formatCitation(
            baseInput({ license: lic.value, authors: auth.value }),
            fmt,
          );
          expect(typeof out).toBe("string");
          expect(out.length).toBeGreaterThan(10);
        });
      }
    }
  }
});

describe("plain text format", () => {
  it("renders APA-flavoured single-author citation with license", () => {
    const out = formatCitation(baseInput(), "plain");
    expect(out).toContain("Krebs, F. (2024).");
    expect(out).toContain("LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)");
    expect(out).toContain("[Data set]");
    expect(out).toContain("Shepard Research Data Platform");
    expect(out).toContain("https://shepard.nuclide.systems/collections/42");
    expect(out).toContain("Licensed under MIT.");
    expect(out).toContain("Accessed 2026-05-24.");
  });

  it("omits the license line entirely when license is null", () => {
    const out = formatCitation(baseInput({ license: null }), "plain");
    expect(out).not.toContain("Licensed under");
    // Sanity check: it didn't accidentally say "no license"
    expect(out.toLowerCase()).not.toContain("no license");
    expect(out.toLowerCase()).not.toContain("unlicensed");
  });

  it("formats two authors with ` & ` (APA two-author rule)", () => {
    const out = formatCitation(
      baseInput({ authors: ["Krebs, F.", "Müller, M."] }),
      "plain",
    );
    expect(out).toContain("Krebs, F. & Müller, M.");
  });

  it("formats three authors with comma list + Oxford `&`", () => {
    const out = formatCitation(baseInput({ authors: MULTI_AUTHORS }), "plain");
    expect(out).toContain("Krebs, F., Müller, M., & Weber, S.");
  });

  it("falls back to Anonymous when authors is empty", () => {
    const out = formatCitation(baseInput({ authors: [] }), "plain");
    expect(out).toContain("Anonymous (2024).");
  });
});

describe("BibTeX format", () => {
  it("starts with @dataset and a deterministic key", () => {
    const out = formatCitation(baseInput(), "bibtex");
    expect(out).toMatch(/^@dataset\{shepard-42-2024,/);
  });

  it("contains all required fields", () => {
    const out = formatCitation(baseInput(), "bibtex");
    expect(out).toMatch(/author\s*=\s*\{Krebs, F\.\}/);
    expect(out).toMatch(/year\s*=\s*\{2024\}/);
    expect(out).toMatch(/publisher\s*=\s*\{Shepard Research Data Platform\}/);
    expect(out).toMatch(/url\s*=\s*\{https:\/\/shepard\.nuclide\.systems\/collections\/42\}/);
    expect(out).toMatch(/urldate\s*=\s*\{2026-05-24\}/);
  });

  it("joins multiple authors with ` and ` per BibTeX convention", () => {
    const out = formatCitation(baseInput({ authors: MULTI_AUTHORS }), "bibtex");
    expect(out).toMatch(/author\s*=\s*\{Krebs, F\. and Müller, M\. and Weber, S\.\}/);
  });

  it("emits a note field with the license when present", () => {
    const out = formatCitation(baseInput(), "bibtex");
    expect(out).toMatch(/note\s*=\s*\{Licensed under MIT\}/);
  });

  it("omits the note field when license is null", () => {
    const out = formatCitation(baseInput({ license: null }), "bibtex");
    expect(out).not.toMatch(/note\s*=/);
  });

  it("escapes braces in the title field", () => {
    const out = formatCitation(
      baseInput({ title: "Dataset with {weird} braces" }),
      "bibtex",
    );
    expect(out).toContain("\\{weird\\}");
  });

  it("uses a stable BibTeX key for the same collection URL", () => {
    const a = formatCitation(baseInput(), "bibtex");
    const b = formatCitation(baseInput(), "bibtex");
    expect(a).toBe(b);
  });

  it("falls back to `dataset` key when the URL has no /collections/ segment", () => {
    const out = formatCitation(
      baseInput({ url: "https://example.org/random/path" }),
      "bibtex",
    );
    expect(out).toMatch(/^@dataset\{shepard-dataset-2024,/);
  });
});

describe("RIS format", () => {
  it("uses TY  - DATA and ER  -  terminator", () => {
    const out = formatCitation(baseInput(), "ris");
    expect(out).toMatch(/^TY {2}- DATA/);
    expect(out).toMatch(/ER {2}- $/m);
  });

  it("emits one AU line per author", () => {
    const out = formatCitation(baseInput({ authors: MULTI_AUTHORS }), "ris");
    expect(out).toMatch(/AU {2}- Krebs, F\./);
    expect(out).toMatch(/AU {2}- Müller, M\./);
    expect(out).toMatch(/AU {2}- Weber, S\./);
  });

  it("includes PY / T1 / PB / UR / Y2 fields", () => {
    const out = formatCitation(baseInput(), "ris");
    expect(out).toMatch(/PY {2}- 2024/);
    expect(out).toMatch(/T1 {2}- LUMEN-Inspired Hotfire Test Campaign/);
    expect(out).toMatch(/PB {2}- Shepard Research Data Platform/);
    expect(out).toMatch(/UR {2}- https:\/\/shepard\.nuclide\.systems\/collections\/42/);
    expect(out).toMatch(/Y2 {2}- 2026-05-24/);
  });

  it("emits a C1 license line when license is present", () => {
    const out = formatCitation(baseInput(), "ris");
    expect(out).toMatch(/C1 {2}- License: MIT/);
  });

  it("omits the C1 license line when license is null", () => {
    const out = formatCitation(baseInput({ license: null }), "ris");
    expect(out).not.toMatch(/C1 {2}- License/);
  });

  it("falls back to AU - Anonymous when authors is empty", () => {
    const out = formatCitation(baseInput({ authors: [] }), "ris");
    expect(out).toMatch(/AU {2}- Anonymous/);
  });

  it("uses CRLF line endings per RIS spec", () => {
    const out = formatCitation(baseInput(), "ris");
    expect(out).toContain("\r\n");
  });
});

describe("CSL JSON format", () => {
  it("parses as valid JSON", () => {
    const out = formatCitation(baseInput(), "csl-json");
    expect(() => JSON.parse(out)).not.toThrow();
  });

  it("declares type=dataset", () => {
    const out = formatCitation(baseInput(), "csl-json");
    const parsed = JSON.parse(out);
    expect(parsed.type).toBe("dataset");
  });

  it("encodes authors as {family} objects (no `given` heuristic)", () => {
    const out = formatCitation(baseInput({ authors: MULTI_AUTHORS }), "csl-json");
    const parsed = JSON.parse(out);
    expect(parsed.author).toHaveLength(3);
    expect(parsed.author[0]).toEqual({ family: "Krebs, F." });
    expect(parsed.author[1]).toEqual({ family: "Müller, M." });
    expect(parsed.author[2]).toEqual({ family: "Weber, S." });
  });

  it("encodes year + accessed-date as CSL date-parts", () => {
    const out = formatCitation(baseInput(), "csl-json");
    const parsed = JSON.parse(out);
    expect(parsed.issued).toEqual({ "date-parts": [[2024]] });
    expect(parsed.accessed).toEqual({ "date-parts": [[2026, 5, 24]] });
  });

  it("includes the license field when present", () => {
    const out = formatCitation(baseInput(), "csl-json");
    const parsed = JSON.parse(out);
    expect(parsed.license).toBe("MIT");
  });

  it("omits the license field entirely when null (not 'license: null')", () => {
    const out = formatCitation(baseInput({ license: null }), "csl-json");
    const parsed = JSON.parse(out);
    expect("license" in parsed).toBe(false);
  });

  it("includes URL + publisher + title", () => {
    const out = formatCitation(baseInput(), "csl-json");
    const parsed = JSON.parse(out);
    expect(parsed.URL).toBe("https://shepard.nuclide.systems/collections/42");
    expect(parsed.publisher).toBe("Shepard Research Data Platform");
    expect(parsed.title).toBe("LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)");
  });

  it("falls back to Anonymous family-name when authors is empty", () => {
    const out = formatCitation(baseInput({ authors: [] }), "csl-json");
    const parsed = JSON.parse(out);
    expect(parsed.author).toEqual([{ family: "Anonymous" }]);
  });
});

describe("formats catalogue", () => {
  it("lists all four formats in display order", () => {
    expect(CITATION_FORMATS_ORDER).toEqual(["plain", "bibtex", "ris", "csl-json"]);
  });

  it("has a human label per format", () => {
    for (const f of CITATION_FORMATS_ORDER) {
      expect(CITATION_FORMAT_LABELS[f]).toBeTruthy();
      expect(typeof CITATION_FORMAT_LABELS[f]).toBe("string");
    }
  });
});
