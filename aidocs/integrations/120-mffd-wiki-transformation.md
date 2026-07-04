---
name: MFFD wiki transformation — Confluence to LabJournalEntries + glossary + Programme attachments
description: Three-track transformation of the 112-page Confluence MFFD space — journal-shaped pages split into LabJournalEntries on the relevant process-step DOs, plan documents folded into the Programme description + attached as FileReferences, reference pages mined for the urn:shepard:mffd:* controlled vocabulary.
type: design
stage: feature-defined
last-stage-change: 2026-06-02
---

# 120 — MFFD wiki transformation

**Status:** decision-quality 2026-06-02. Implementation deferred to a script,
specified here.
**Companion:** `aidocs/integrations/119-mffd-collection-layout.md` (Collection
targets), `aidocs/integrations/113-mffd-real-data-import-plan.md` (W2.5).
**Predecessor:** the user directive 2026-05-28 — *"don't seed wiki content
with MFFD; integrate on structure"* — and 2026-05-23 — *"create project
terminology from wiki data"*. This doc closes both.
**Source data:** `/mnt/pve/unas/dump/dataset/wiki/MFFD/*.html` (extracted from
`Confluence-space-export-163115-2.html.zip` on 2026-06-02).

## 0. TL;DR

| Track | Pages | Output | Target |
|---|---|---|---|
| **journal** | 6 (dated diaries) | ~218 `LabJournalEntries` (one per dated block) | per-track DOs in `mffd-afp-tapelaying`, `mffd-cell`, `mffd-programme` |
| **plans** | 5 (project planning) | 5 FileReferences + folded into Programme description | `mffd-programme.programme-overview` |
| **glossary** | ~99 (reference pages) | controlled-vocab terms under `urn:shepard:mffd:*` | `mffd-programme.vocabulary-manifest` |

Three tracks ⇒ one script (`wiki-to-journal.py`) with three modes (`--journal`,
`--plans`, `--glossary`), idempotent by composite key, dry-run by default.

## 1. Page-shape audit (112 pages classified)

Run by `examples/mffd-showcase/raw-data/mffd-data/analyze-confluence-export.py`
+ a small regex over each page's body text:

| Class | Count | Heuristic | Track |
|---|---|---|---|
| **journal candidates** | 6 | `>=5` dated entries (`DD.MM.YYYY`) | track 1 |
| **plan documents** | 5 | title contains `Plan|Roadmap|Painlist|Project Plan|Architecture` | track 2 |
| **reference pages** | ~99 | everything else (FAQs, configs, naming conventions, vocab tables) | track 3 |

The full classification lives in `manifest.json` once `wiki-to-journal.py` runs;
the script writes it alongside the input directory.

### 1.1 The six journal candidates

| Page (filename) | Dates | Bytes | Voice | Target DO(s) |
|---|---|---|---|---|
| `Legeplan_*.html` | 102 | 8 277 | daily ply-layup plan with execution notes | `mffd-afp-tapelaying` per-track DOs (cross-ref by date + track number) |
| `Laser-Integration-FPZ_*.html` | 36 | 6 818 | laser integration trials | `mffd-cell.cell-overview` DO |
| `Materialinfo-und-Entnahme_Batch_226368_*.html` | 35 | 2 462 | material withdrawal log per shift | `mffd-afp-tapelaying.material-batch-226368` DO (carries `urn:shepard:material:batch = 226368`) |
| `Legetagebuch_485279251.html` | 25 | 37 575 | lay diary, shift protocols — *canonical journal voice* | `mffd-afp-tapelaying` (per-day entries) |
| `Schichtplan_*.html` | 14 | 3 853 | shift schedule | `mffd-programme.programme-overview` |
| `Versuchlog_Platten_F-AFP_*.html` | 6 | 7 623 | test-plate experiments (pre-production) | `mffd-programme.programme-overview` |

**Net journal entries:** ~218 (the dated blocks across all six pages).

### 1.2 The five plan documents

| Page | Folder destination |
|---|---|
| `Project-Plan_*.html` | Programme description (top section) + FileReference |
| `Plan-for-the-test-shell-manufacture_*.html` | FileReference only (reference doc) |
| `Painlist-Prozessablauf_F-AFP_*.html` | FileReference + vocab mining |
| `Painlist-TPS_*.html` | FileReference + vocab mining |
| `TLZ-Roadmap-ST_*.html` | FileReference only |

Per the operator decision 2026-06-02 (option "both") — fold the readable
narrative of each plan into the Programme's `programme-overview` DO text,
**and** attach the original HTML as a FileReference. The original remains the
auditable source; the folded text gives the discoverability.

### 1.3 The ~99 reference pages

Skim with a regex pass for *capitalised acronyms* (`[A-Z]{2,}` like AFP, TPS,
FSD, NDT, LBR, MFZ, MFFD, …), proper nouns (places, equipment names), and
*Glossary-table* HTML patterns (`<table>` with two-column term/definition
shape). Output:

```json
{
  "AFP":  { "term": "Automated Fibre Placement",          "source_pages": [...] },
  "TPS":  { "term": "Tape Placement System",              "source_pages": [...] },
  "FSD":  { "term": "Fibre Steering Device",              "source_pages": [...] },
  "MFFD": { "term": "Multifunctional Fuselage Demonstrator", "source_pages": [...] },
  "MFZ":  { "term": "Multifunctional Test Cell (MFZ Zelle)",  "source_pages": [...] },
  ...
}
```

These land on `mffd-programme.vocabulary-manifest` as a JSON-LD document under
`urn:shepard:mffd:term:<acronym>`, plus matching SemanticAnnotations on every
DO that mentions the acronym in its name or description.

## 2. Transformation contract

### 2.1 Per-entry shape (journal track)

```yaml
target_dataobject_appid: <mapped by page-routing.yaml>
timestamp:    <DD.MM.YYYY → ISO 8601 @ 09:00 Europe/Berlin (no time of day in source)>
author:       <:MirroredUser.shepardId>  # see §3 below
text:         |
  <markdown of the dated block; original Confluence URL preserved as suffix:
   "_Source: https://confluence.dlr.de/spaces/MFFD/pages/<page-id>_">
annotations:
  urn:shepard:wiki:source-page-id:     <confluence-page-id>
  urn:shepard:wiki:source-page-title:  <title>
  urn:shepard:wiki:source-block-index: <N>
  urn:shepard:wiki:source-url:         https://confluence.dlr.de/...
provenance:
  - type: :WikiImportActivity
    wasAttributedTo: <:MirroredUser>
    wasDerivedFrom:  <confluence-page-id>
    wasGeneratedBy:  <agent: wiki-to-journal.py vN>
```

**Idempotency key:** `(source-page-id, source-block-index)`. Re-runs update in
place; never duplicate.

### 2.2 Per-FileReference shape (plan track)

```yaml
target_dataobject_appid: <mffd-programme.programme-overview.appId>
name:        "<page title>.html"
content:     <raw HTML, original UTF-8>
annotations:
  urn:shepard:wiki:source-page-id:    <confluence-page-id>
  urn:shepard:wiki:plan-document:     true
  urn:shepard:wiki:plan-category:     project-plan | painlist | roadmap | manufacture-plan
provenance:
  - same shape as journal, type :WikiImportActivity
```

### 2.3 Per-vocab-term shape (glossary track)

```yaml
target_dataobject_appid: <mffd-programme.vocabulary-manifest.appId>
predicate: urn:shepard:mffd:term:<acronym>
value: <expanded term>
provenance:
  - source_pages: [<confluence-page-id>, ...]
  - extraction_confidence: high | medium | low
```

Plus, on every DO whose name or description contains the acronym, write a
`SemanticAnnotation` `urn:shepard:mffd:mentions = <acronym>` so the inverse
query "which DOs mention AFP" returns clean results.

## 3. Author preservation — `:MirroredUser`

Operator decision 2026-06-02: PROV-O-correct over text-only fallback.

Per `aidocs/16` row `PROV-USER-MIRROR-ENDPOINT` (shipped 2026-05-23), Shepard
already has a `:MirroredUser` Neo4j entity + `POST /v2/admin/users/mirror`
endpoint. The Confluence export stores `<meta name="confluence-author" content="kreb_fl">`
in each page's HTML head; the script:

1. Extracts the author meta from every page (and each comment block in
   Legetagebuch-style pages, where multiple authors contribute per day).
2. Calls `POST /v2/admin/users/mirror` with `{externalSystem: "confluence-dlr", externalId: "kreb_fl", displayName: "Florian Krebs", orcid?: ...}` to upsert the `:MirroredUser` row.
3. Records the returned `:MirroredUser.shepardId` and uses it as the
   `wasAttributedTo` for the resulting `:WikiImportActivity`.

When a page has no `confluence-author` meta (rare), the script falls back to a
generic `:MirroredUser{externalId="wiki-import-anonymous"}` and emits a WARN
log entry — the entry still imports.

## 4. Page-routing table

`examples/mffd-showcase/wiki-journal/page-routing.yaml` — six rows, hand-curated:

```yaml
# Each key is a Confluence page id; each row routes to a target DO.
# `target` is the slug; the script resolves it to appId at run time via the SDK.
# `role` is recorded as the entry's `urn:shepard:wiki:journal-role` for filtering.
# `annotations` lets a row inject step-specific tags on every emitted entry.

217288071_Legeplan:
  target: mffd-afp-tapelaying
  role:   step-log
  per_entry_target_resolver: by-date-and-track  # see §4.1

448751575_Legeplan:                              # duplicate Confluence revision
  target: mffd-afp-tapelaying
  role:   step-log
  per_entry_target_resolver: by-date-and-track

485279251_Legetagebuch:
  target: mffd-afp-tapelaying
  role:   shift-protocol
  per_entry_target_resolver: by-date

<id>_Laser-Integration-FPZ:
  target: mffd-cell
  role:   integration-trial
  per_entry_target_resolver: cell-overview-do   # fixed target, all entries

<id>_Materialinfo-226368:
  target: mffd-afp-tapelaying
  role:   material-withdrawal
  per_entry_target_resolver: material-batch-do
  annotations:
    urn:shepard:material:batch: '226368'

<id>_Schichtplan:
  target: mffd-programme
  role:   shift-schedule
  per_entry_target_resolver: programme-overview-do

<id>_Versuchlog_Platten_F-AFP:
  target: mffd-programme
  role:   test-plate-experiment
  per_entry_target_resolver: programme-overview-do
```

### 4.1 Per-entry target resolvers (the load-bearing part)

| resolver | how to resolve a dated block to a target DO |
|---|---|
| `by-date` | parse the date, look up the AFP-tapelaying track DO created on the same day; if none, fall back to a daily `daily-log-<date>` DO under `mffd-afp-tapelaying` |
| `by-date-and-track` | parse `Track <N>` mentions within the block; if present, attach to that track DO; else use `by-date` |
| `cell-overview-do`, `programme-overview-do`, `material-batch-do` | fixed target — every entry from that page lands on the same DO |

The resolver runs against the **W2-populated** state, so the wiki track must
run **post-W2** (the `mffd-afp-tapelaying` Collection has to exist with all
track DOs landed). See `aidocs/integrations/113 §"Implementation sequence"`.

## 5. Script shape

```
examples/mffd-showcase/scripts/
  wiki-to-journal.py            # the main script
  wiki-to-plans.py              # thin wrapper around the same parser
  wiki-to-glossary.py           # thin wrapper
  ../wiki-journal/
    page-routing.yaml
    glossary-stopwords.txt      # acronyms to ignore (HTTP, URL, …)
    sample-output.md            # human-reviewable preview before --commit
```

CLI:

```bash
# Default: dry-run, prints sample-output.md
python3 wiki-to-journal.py \
  --source /mnt/pve/unas/dump/dataset/wiki/MFFD \
  --routing examples/mffd-showcase/wiki-journal/page-routing.yaml \
  --shepard https://shepard.nuclide.systems \
  --apikey "$SHEPARD_API_KEY"

# After human review of sample-output.md:
python3 wiki-to-journal.py --commit ...
```

The script **never** writes without `--commit`. Even with `--commit`, it
idempotency-checks every entry against the composite key before POSTing.

## 6. Sequencing within the import plan

Per `aidocs/integrations/119-mffd-collection-layout.md §8`:

```
W2          (~24 h)  tapelaying ingest
W2.5        (~30 min) ← THIS DOC: wiki-to-journal.py --dry-run, review, --commit
W3 W5 W6 W8 (parallel)
```

- The wiki track is **post-W2** because the resolvers depend on `mffd-afp-tapelaying`
  being populated.
- The glossary track can run **anytime after the Programme Collection exists**
  (step 1 of §8 in 119, ~30 min pre-W2). It does not depend on per-track DOs.
- The plans track sits in step 1 too — those 5 HTML files attach to the
  Programme's `programme-overview` DO at Collection seed time.

## 7. Open items

| # | Item | Plan |
|---|---|---|
| 1 | Multi-author pages (Legetagebuch has different authors per shift) | extract per-block author from inline `<by>...</by>` patterns; fall back to page author |
| 2 | German text — language tag on annotations? | yes, `urn:shepard:lang = de` on entries imported from German pages |
| 3 | Confluence links between pages (217 inter-page links per `analyze-confluence-export.py`) | rewrite the in-page Markdown to use `shepard://entity/<appId>` once the target DO is known; preserve the original Confluence URL in the entry text suffix |
| 4 | Confluence attachments (980 per analysis) | follow-up: ingest as FileReferences attached to the resulting entries; out of scope for this v1 |

## 8. Acceptance criteria

1. **Idempotency**: running `--commit` twice creates no duplicates; the second
   run's diff is empty.
2. **Author preservation**: every emitted entry's PROV-O has
   `wasAttributedTo → :MirroredUser` (not the generic `wiki-import-anonymous`)
   for at least 95% of entries.
3. **Coverage**: of the 6 journal candidates, all 6 emit ≥ 80% of their dated
   blocks as entries (the 20% slack is for malformed dates).
4. **Vocab quality**: the glossary track produces at least the canonical
   campaign terms — AFP, TPS, FSD, NDT, LBR, MFFD, MFZ, AF — plus their
   expansions.
5. **Round-trippable**: every entry's `urn:shepard:wiki:source-url` points back
   to a working Confluence page (when DLR Confluence is reachable).

## 9. Related repo artefacts

- `examples/mffd-showcase/raw-data/mffd-data/mffd-confluence-space-export/` —
  the local already-extracted wiki copy (now also at `/mnt/pve/unas/dump/dataset/wiki/`).
- `examples/mffd-showcase/raw-data/mffd-data/analyze-confluence-export.py` —
  initial classifier.
- `examples/mffd-showcase/raw-data/mffd-data/confluence-analysis.md` — first-pass
  audit report.
- `aidocs/16` rows added by this doc:
  - `MFFD-WIKI-TO-JOURNAL` — implement track 1 (the script)
  - `MFFD-WIKI-TO-GLOSSARY` — implement track 3
  - `MFFD-WIKI-TO-PLANS` — implement track 2 (smallest, may fold into seed-mffd-collections.py)
