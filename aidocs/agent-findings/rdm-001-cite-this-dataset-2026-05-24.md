---
stage: deployed
last-stage-change: 2026-05-24
---

# RDM-001 — "Cite this dataset" card on Collection landing (2026-05-24)

Closes the highest per-day-of-effort finding from the RDM Scrutinizer pass
(`aidocs/agent-findings/rdm-scrutinizer-2026-05-24.md §Top-5 #1`). Pure
frontend; no backend change. Built on the post-LIC1 `Collection` wire
shape that now exposes `license` + `accessRights` so the citation can
declare the licence in copy-paste form.

## What I built

Component tree:

```
frontend/utils/citation.ts                          (pure formatter — no I/O, no Vuetify)
  ├── formatCitation(input, format)                 — public entry; dispatches on format
  ├── CitationFormat = "plain"|"bibtex"|"ris"|"csl-json"
  ├── CITATION_FORMATS_ORDER                        — render order (plain, bibtex, ris, csl-json)
  └── CITATION_FORMAT_LABELS                        — human labels per format

frontend/components/context/collection/CiteThisCard.vue
  ├── <v-card> with heading "Cite this dataset" + Copy button
  ├── <v-tabs> for 4 formats (data-testid: cite-this-tab-<fmt>)
  ├── <pre> citation body (data-testid: cite-this-body)
  └── useClipboard() from @vueuse/core (existing dep — no new wrapper)

pages/collections/[collectionId]/index.vue
  └── <CiteThisCard :collection="collection" />     (slotted between Description and Semantic Annotations)

frontend/tests/unit/citation.test.ts                — 46 Vitest cases (all passing)
e2e/tests/rdm-001-cite-this-dataset.spec.ts         — 5 Playwright cases on the live deployment
```

Standards-track formats (citation pinned, not freelance):
- **APA 7th** for plain text — `apastyle.apa.org/style-grammar-guidelines/references/examples/data-set-references`
- **BibTeX `@dataset`** — biblatex de-facto convention (`bibtex.com/e/dataset-entry/`)
- **RIS `TY  - DATA`** — Research Information Systems spec
- **CSL JSON `type: dataset`** — `citationstyles.org`

## Citation format examples (LUMEN @ /collections/42)

Source data: `createdBy = "alice"`, `createdAt year = 2024`, `name = "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)"`, `license = "MIT"`, today = `2026-05-24`.

**Plain text:**
```
alice (2024). LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic) [Data set]. Shepard Research Data Platform. https://shepard.nuclide.systems/collections/42. Licensed under MIT. Accessed 2026-05-24.
```

**BibTeX:**
```bibtex
@dataset{shepard-42-2024,
  author       = {alice},
  title        = {{LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)}},
  year         = {2024},
  publisher    = {Shepard Research Data Platform},
  url          = {https://shepard.nuclide.systems/collections/42},
  urldate      = {2026-05-24},
  note         = {Licensed under MIT},
}
```

**RIS:**
```
TY  - DATA
AU  - alice
PY  - 2024
T1  - LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)
PB  - Shepard Research Data Platform
UR  - https://shepard.nuclide.systems/collections/42
Y2  - 2026-05-24
C1  - License: MIT
ER  -
```

**CSL JSON:**
```json
{
  "type": "dataset",
  "title": "LUMEN-Inspired Hotfire Test Campaign — Q3 2024 (synthetic)",
  "author": [
    { "family": "alice" }
  ],
  "issued": { "date-parts": [[2024]] },
  "publisher": "Shepard Research Data Platform",
  "URL": "https://shepard.nuclide.systems/collections/42",
  "accessed": { "date-parts": [[2026, 5, 24]] },
  "license": "MIT"
}
```

The license-absent path (any Collection with `license == null`) **omits the
licence line entirely** — no "Unlicensed" or "No license" leak, because
those wordings imply unauthorised redistribution when the truth is merely
"undeclared, consult the operator". This is asserted in the Vitest suite
(`omits the license line entirely when license is null`) and in the
Playwright e2e for the MFFD-Dropbox collection.

## Author-source decision (the constraint the task admitted as TBD)

The task spec invited surveying `Collection.contributors[].user.{displayName, orcid}` but openly noted "or similar — survey first". The survey result:

- The generated `Collection` model (`backend-client/src/models/Collection.ts`) exposes `createdBy: string` (a bare username) — there is **no `contributors[]` field, no `creators[]` array, no `author` attribute convention** seeded by `examples/lumen-showcase/seed.py`.
- The LUMEN seed *does* populate an `authors` key on per-publication-record DataObjects (`Dresia, K. et al.` etc.), but that's a per-DO attribute, not a Collection-level field.

Decision: today the card renders **one author = `createdBy`** (the username). The `formatCitation(authors: string[], ...)` signature accepts an array regardless, so when a future surface ships richer author sources — a `contributors[]` graph edge, an `authors` Collection-attribute convention, an ORCID lookup against the User registry — the formatter consumes it without changing shape. The 16-cell unit-test matrix (4 formats × license × single/multi-author) is honest because single vs multi is a real formatter branch (APA "&" rule, BibTeX "and" join, RIS multi-AU line) even though production passes length-1 arrays today.

## Test counts

- **Vitest** (`frontend/tests/unit/citation.test.ts`): **46/46 passing**
  - 16-case matrix (4 formats × license-present/absent × single/multi-author)
  - 6 plain-text edge cases (APA 1/2/3-author rule, empty → Anonymous, license-null branch)
  - 8 BibTeX cases (deterministic key, brace escaping, multi-author `and` join, missing-URL fallback)
  - 8 RIS cases (TY/ER terminator, multi-AU, CRLF, C1 license, Anonymous fallback)
  - 8 CSL JSON cases (valid JSON, type=dataset, family-only authors, date-parts, license omission)
  - 2 catalogue invariants (display order, label per format)

- **Playwright** (`e2e/tests/rdm-001-cite-this-dataset.spec.ts`): **5 cases**
  - Card heading visible on `/collections/42` with title + URL + year
  - BibTeX tab renders `@dataset{shepard-42-…`
  - RIS tab renders `TY  - DATA` … `ER  -`
  - CSL JSON tab renders valid JSON with `type=dataset`
  - Copy button triggers without a console error
  - Sixth (tolerant) case: card renders on `/collections/661923` MFFD-Dropbox with null-license-line-absent assertion

- **Whole-frontend Vitest**: 270 pre-existing tests still passing; the 5 failures in `useFetchRecentCollections.test.ts` are pre-existing on `main` (confirmed by `git stash` + re-run) and unrelated to this PR.

- **Whole-frontend `vue-tsc --noEmit`**: clean on the four files touched by this PR (one strictNullChecks issue caught + fixed before commit).

## Backlog rows flipped

- `aidocs/16-dispatcher-backlog.md`: RDM-001 row moved from `queued` → **`shipped`**, with the component tree + test counts inlined.
- `aidocs/34-upstream-upgrade-path.md`: new **RDM-001** row inserted directly above LIC1, describing the four formats, the wire-source mapping, and the zero-action-required upgrade path (admin restarts the frontend container; no backend change).
- `aidocs/44-fork-vs-upstream-feature-matrix.md`: new **RDM-001** row inserted directly below LIC1 in §7 (Semantic / FAIR features), marked `✓ ↑`.

## Files touched

```
M frontend/pages/collections/[collectionId]/index.vue
A frontend/components/context/collection/CiteThisCard.vue
A frontend/utils/citation.ts
A frontend/tests/unit/citation.test.ts
A e2e/tests/rdm-001-cite-this-dataset.spec.ts
M aidocs/16-dispatcher-backlog.md
M aidocs/34-upstream-upgrade-path.md
M aidocs/44-fork-vs-upstream-feature-matrix.md
A aidocs/agent-findings/rdm-001-cite-this-dataset-2026-05-24.md   (this file)
```

## Deferred / follow-up

- **Multi-author surface.** When a `contributors[]` graph edge or `creators` attribute convention ships, plug it into `authors.value` in `CiteThisCard.vue` — the `formatCitation` signature already accepts `string[]` so the change is one line.
- **ORCID rendering in author position.** Once RDM-002 lands and `createdBy` resolves to a `{username, displayName, orcid}` object, the plain-text format can emit `Krebs, F. (https://orcid.org/0000-...)` and the CSL JSON `author[].ORCID` field. The structural change is in the renderer; format files stay pinned.
- **DOI / PID slot.** When the publishing plugin (UH1 / FAIR3) mints a DataCite DOI, swap `url` for the DOI URL and add a `doi` field to BibTeX + CSL JSON. The card's existing `canonicalUrl` computed becomes a fallback.
- **Instance identity string** (`Shepard Research Data Platform` is hard-coded today). When INST1 surfaces a configurable `instance.identity.name`, read it in `CiteThisCard.vue` and pass through to `formatCitation.repository`. Pure data swap, no shape change.
- **Cite-this-DataObject card.** Same shape, on the DataObject landing — likely as RDM-001b. Same `formatCitation` helper, different fields source (`DataObject.name` for title, `DataObject.createdBy` for author, container links for related-identifiers).
