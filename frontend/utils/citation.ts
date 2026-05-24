/**
 * Citation formatter — renders a Collection as a copy-paste-ready citation
 * in four standards-track formats.
 *
 * Closes RDM-001 (FAIR R1 — rich provenance metadata for re-use). Before
 * this util, a researcher who wanted to cite a Shepard dataset had to
 * hand-assemble the citation from scattered UI fields. The "Cite this
 * dataset" card on the Collection landing puts Shepard on par with the
 * citation affordance Zenodo / Coscine / Dataverse ship by default.
 *
 * Format references (pinned, not freelance):
 *   - APA 7th edition for the plain-text shape:
 *     https://apastyle.apa.org/style-grammar-guidelines/references/examples/data-set-references
 *   - BibTeX `@dataset` per the de-facto biblatex convention:
 *     https://www.bibtex.com/e/dataset-entry/
 *   - RIS `TY  - DATA` per the Research Information Systems spec:
 *     https://en.wikipedia.org/wiki/RIS_(file_format)
 *   - CSL JSON per citationstyles.org:
 *     https://docs.citationstyles.org/en/stable/specification.html
 *
 * The shape is **deliberately pure** — no I/O, no clipboard, no Vuetify.
 * That keeps it Vitest-friendly and reusable from any caller (the card,
 * the RO-Crate exporter, a future Helmholtz Unhide harvest payload).
 *
 * Author source: Shepard's `Collection` wire shape (post-LIC1) carries
 * `createdBy: string` — a bare username, not a `{displayName, orcid}`
 * object. The seed-script convention does NOT populate a Collection-level
 * `authors` attribute (the LUMEN publication-record DataObjects do, but
 * that's a per-DO field, not the Collection's). So in practice the
 * authors[] argument is most often a single-element array containing the
 * `createdBy` username. The formatter still accepts arbitrary length so
 * future surfaces (e.g. a multi-creator attribute, a contributors graph
 * edge) can pass richer data without a rewrite.
 */

export type CitationFormat = "plain" | "bibtex" | "ris" | "csl-json";

export interface CitationInput {
  /**
   * Author list, ordered. Free-text strings — may be `"alice"` (raw
   * username), `"Krebs, F."` (LastName, Initial.), or a full display name.
   * Empty array is allowed; the formatter falls back to `"Anonymous"`.
   */
  authors: string[];
  /** Four-digit publication year (typically the Collection's `createdAt` year). */
  year: number;
  /** Collection name — used as the dataset title. */
  title: string;
  /** Repository / publisher name (e.g. `"Shepard Research Data Platform"`). */
  repository: string;
  /** Canonical URL of the Collection landing page. */
  url: string;
  /**
   * SPDX license identifier (e.g. `"MIT"`, `"CC-BY-4.0"`) or `null` /
   * `undefined` when the Collection has no declared license. The formatter
   * **omits the license line entirely** when null — "no license" is worse
   * than nothing because it implies the dataset is unlicensed when in fact
   * it's merely undeclared (consult the operator).
   */
  license?: string | null;
  /**
   * Access date for the plain-text citation, in ISO YYYY-MM-DD form. APA
   * 7th requires retrieval date for online datasets. Pass `today` from the
   * caller so the unit tests can stay deterministic.
   */
  accessedDate: string;
}

/**
 * BibTeX entry keys must be ASCII-only and contain no whitespace. Build a
 * deterministic key from the URL's trailing collection id (so the same
 * Collection always produces the same key) with a year suffix for sort
 * stability — `shepard-42-2024`.
 */
function bibtexKey(url: string, year: number): string {
  const m = url.match(/\/collections\/([^/?#]+)/);
  const id = m && m[1] ? m[1].replace(/[^A-Za-z0-9_-]/g, "") : "dataset";
  return `shepard-${id}-${year}`;
}

/**
 * Format a list of authors APA-style: `"Krebs, F."` for one, `"Krebs, F.,
 * & Müller, M."` for two, `"Krebs, F., Müller, M., & Weber, S."` for
 * three+. Empty list returns `"Anonymous"`. The formatter does NOT split
 * `"Krebs, F."` into family/given — that would lose information when the
 * source is a bare username like `"alice"`.
 */
function formatAuthorsApa(authors: string[]): string {
  const cleaned = authors.map(a => a.trim()).filter(a => a.length > 0);
  if (cleaned.length === 0) return "Anonymous";
  if (cleaned.length === 1) return cleaned[0]!;
  if (cleaned.length === 2) return `${cleaned[0]} & ${cleaned[1]}`;
  // 3+ → APA comma list with Oxford-style `&` before the last
  const head = cleaned.slice(0, -1).join(", ");
  const tail = cleaned[cleaned.length - 1]!;
  return `${head}, & ${tail}`;
}

/**
 * Render the plain-text APA-flavoured citation, e.g.
 *   `Krebs, F. (2024). LUMEN — Q3 2024 [Data set]. Shepard Research Data
 *    Platform. https://shepard.nuclide.systems/collections/42. Licensed
 *    under MIT. Accessed 2026-05-24.`
 */
function formatPlain(c: CitationInput): string {
  const authors = formatAuthorsApa(c.authors);
  const head = `${authors} (${c.year}). ${c.title} [Data set]. ${c.repository}. ${c.url}.`;
  const licensePart = c.license ? ` Licensed under ${c.license}.` : "";
  const accessPart = ` Accessed ${c.accessedDate}.`;
  return head + licensePart + accessPart;
}

/**
 * Render `@dataset{...}` per the biblatex `@dataset` entry type.
 * `language = {en}` is omitted — Shepard collections may be multilingual
 * and we don't carry a language field yet. License lands in the `note`
 * field (biblatex `@dataset` has no canonical license slot).
 */
function formatBibtex(c: CitationInput): string {
  const key = bibtexKey(c.url, c.year);
  const authorField = c.authors.length > 0
    ? c.authors.map(a => a.trim()).filter(Boolean).join(" and ")
    : "Anonymous";
  const lines = [
    `@dataset{${key},`,
    `  author       = {${escapeBibtex(authorField)}},`,
    `  title        = {{${escapeBibtex(c.title)}}},`,
    `  year         = {${c.year}},`,
    `  publisher    = {${escapeBibtex(c.repository)}},`,
    `  url          = {${c.url}},`,
    `  urldate      = {${c.accessedDate}},`,
  ];
  if (c.license) {
    lines.push(`  note         = {Licensed under ${escapeBibtex(c.license)}},`);
  }
  lines.push(`}`);
  return lines.join("\n");
}

/**
 * Escape `{`, `}`, `\`, `%`, `&`, `$`, `#`, `_` in BibTeX field values.
 * Only the most common offenders — full TeX escaping would over-engineer
 * the path. Curly braces in field values are the most common breakage.
 */
function escapeBibtex(s: string): string {
  return s
    .replace(/\\/g, "\\textbackslash{}")
    .replace(/([&%$#_])/g, "\\$1")
    .replace(/[{}]/g, m => `\\${m}`);
}

/**
 * Render RIS per the Research Information Systems spec. `TY  - DATA` is
 * the dataset type tag. Note the spec requires two spaces between the
 * two-char tag and the hyphen. `ER  -` terminates the record.
 */
function formatRis(c: CitationInput): string {
  const lines: string[] = [];
  lines.push("TY  - DATA");
  for (const a of c.authors.map(s => s.trim()).filter(Boolean)) {
    lines.push(`AU  - ${a}`);
  }
  if (c.authors.filter(a => a.trim()).length === 0) {
    lines.push("AU  - Anonymous");
  }
  lines.push(`PY  - ${c.year}`);
  lines.push(`T1  - ${c.title}`);
  lines.push(`PB  - ${c.repository}`);
  lines.push(`UR  - ${c.url}`);
  lines.push(`Y2  - ${c.accessedDate}`);
  if (c.license) {
    // RI is the standard tag for "Reviewed Item" — not perfect, but RIS
    // has no canonical license tag. C1 (custom field 1) is the more
    // common informal slot for license in repository exports.
    lines.push(`C1  - License: ${c.license}`);
  }
  lines.push("ER  - ");
  return lines.join("\r\n");
}

/**
 * Render CSL JSON per citationstyles.org. `"type": "dataset"` is the CSL
 * type for a research dataset. We put the whole author string in
 * `family` and leave `given` absent — splitting `"alice"` or `"Krebs,
 * F."` into family/given would be lossy heuristics; downstream CSL
 * processors handle a family-only author gracefully (no "F. Alice", no
 * "Alice, F.").
 */
function formatCslJson(c: CitationInput): string {
  const obj: Record<string, unknown> = {
    type: "dataset",
    title: c.title,
    author: (c.authors.length > 0 ? c.authors : ["Anonymous"])
      .map(a => ({ family: a.trim() }))
      .filter(o => o.family.length > 0),
    issued: { "date-parts": [[c.year]] },
    publisher: c.repository,
    URL: c.url,
    accessed: { "date-parts": [parseIsoDate(c.accessedDate)] },
  };
  if (c.license) {
    obj.license = c.license;
  }
  return JSON.stringify(obj, null, 2);
}

/**
 * Parse `YYYY-MM-DD` into a `[year, month, day]` numeric tuple, the
 * structure CSL's `date-parts` expects. Falls back to `[year]` if the
 * input doesn't parse cleanly.
 */
function parseIsoDate(iso: string): number[] {
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (!m) {
    const yearOnly = iso.match(/^(\d{4})/);
    return yearOnly ? [Number(yearOnly[1])] : [];
  }
  return [Number(m[1]), Number(m[2]), Number(m[3])];
}

/**
 * Public entry point — dispatch on format. Pure function: same inputs
 * always produce the same output, no globals, no side effects.
 */
export function formatCitation(input: CitationInput, format: CitationFormat): string {
  switch (format) {
    case "plain":
      return formatPlain(input);
    case "bibtex":
      return formatBibtex(input);
    case "ris":
      return formatRis(input);
    case "csl-json":
      return formatCslJson(input);
    default: {
      // exhaustiveness check — TypeScript will error if a case is missed.
      const _exhaustive: never = format;
      return _exhaustive;
    }
  }
}

/**
 * Human-readable label for each format tab.
 */
export const CITATION_FORMAT_LABELS: Record<CitationFormat, string> = {
  plain: "Plain text",
  bibtex: "BibTeX",
  ris: "RIS",
  "csl-json": "CSL JSON",
};

/**
 * The four formats in display order. Plain text first because it's the
 * most common request from a paper supplement; BibTeX next because it's
 * the LaTeX user's reflex; RIS for EndNote/Zotero/Mendeley import; CSL
 * JSON for the API-savvy.
 */
export const CITATION_FORMATS_ORDER: CitationFormat[] = [
  "plain",
  "bibtex",
  "ris",
  "csl-json",
];
