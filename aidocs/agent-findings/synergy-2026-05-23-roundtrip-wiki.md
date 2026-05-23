---
stage: fragment
last-stage-change: 2026-05-23
---

# S-03 — Round-trip wiki: Confluence import × Wiki-writer × Snapshot chain

## Synergy

A Confluence Space export becomes a Shepard snapshot baseline; the
`shepard-plugin-wiki-writer` plugin re-renders snapshot deltas as
Confluence pages. Each snapshot becomes a wiki revision. The wiki
stops being a duplicate of the data and becomes a **rendered view**
on the snapshot chain.

## Elements (named anchors)

- **Plugin (designed):** Confluence import —
  `aidocs/integrations/82-confluence-import.md` (HTML-export ZIP →
  `LabJournalEntry` + `FileReference` via IMP1 plan-seal).
- **Plugin (shipped):** `shepard-plugin-wiki-writer` —
  `aidocs/40 §4` (WW1; `project_wiki_writer_plugin`), generates
  Confluence-shaped Markdown.
- **Feature (deployed):** Snapshots — `aidocs/data/41-snapshots-design.md`
  (COW architecture, snapshot = "frozen, immutable, reproducible").
- **Plugin family:** Lab journal — `aidocs/data/37-lab-journal-and-jupyter-design.md`
  (J1 / J1d edit history).
- **Container:** ImportV2Rest + IMP1 plan-seal —
  `backend/src/main/java/de/dlr/shepard/v2/importer/...`.

## Why this is non-obvious

- The Confluence import design (aidocs/82) treats Confluence as a
  *source* — a one-way migration. The wiki-writer plugin (WW1)
  treats Confluence as a *target* — a one-way export. Neither
  design references the other.
- Snapshots are designed as a Shepard-internal versioning primitive;
  the snapshot design doc (aidocs/41) does not mention export-back
  to wikis.
- The bidirectional pair is the unlock: import + writer + snapshot
  IS a round-trip wiki bridge. The wiki becomes a *renderable view*
  of the snapshot chain, not a separate document.
- For institutes that already have years of Confluence content
  (`aidocs/82 §Motivation`), this turns Shepard from a migration
  destination into a complement — the existing wiki keeps working,
  but is now backed by typed provenance graph.
- The Lab Journal J1d feature (edit history) and the snapshot
  chain are already structurally identical (immutable revisions of
  a content node). Aligning the two means a single revision tree
  drives both the wiki and the lab-journal UI.

## Concrete output

### 1. Round-trip diagram

```
                    snapshot S_n          snapshot S_{n+1}
                       │                       │
   ┌───────────────────┴───────────┐    ┌──────┴──────────────────┐
   │  Confluence import (aidocs/82)│    │  delta: forging pass     │
   │  HTML-export → LabJournal +   │    │  (annotate / lift /      │
   │  attachments → snapshot       │    │  classify)               │
   └───────────────────────────────┘    └──────┬──────────────────┘
                                              ▼
                              ┌──────────────────────────────────┐
                              │ shepard-plugin-wiki-writer (WW1) │
                              │ render delta as Confluence page  │
                              │ POST via user-PAT to space       │
                              └──────────────────────────────────┘
```

### 2. New mappings (added to wiki-writer)

| Shepard concept | Confluence concept |
|---|---|
| `Collection` root | Confluence Space (`shp-<appId>`) |
| `Snapshot S_n` | Page version |
| `DataObject` | Page (under collection-space) |
| `LabJournalEntry` | Page body (Markdown → storage format) |
| `Annotation` | Page label / inline `<ac:placeholder>` |
| `Predecessor/Successor` | `<ac:link>` to neighbouring page |
| Delta `S_n → S_{n+1}` | Page edit + comment "snapshot at YYYY-MM-DD" |
| AI-touched (F(AI)²R) | `<ac:placeholder>` ribbon "🤖 AI-only, unverified" |

### 3. Snapshot diff service

```http
GET /v2/snapshots/{appId}/diff?against={otherAppId}&format=confluence-storage
```

Returns a Confluence storage-format XML diff describing what to push
to update a target page from snapshot S_n to S_{n+1}. The
wiki-writer is the consumer; same endpoint used by the J1d diff
viewer.

### 4. Two-way sync invariant

- Confluence is **renderable**, never **canonical**. The snapshot
  chain in Shepard is the source of truth. A wiki edit doesn't
  flow back automatically (manual-only via re-import).
- The plugin honours `If-Match` ETag on the Confluence side to
  detect manual edits and surface the conflict in the UI rather
  than overwrite.

## Real-world use case

**Persona:** the LUMEN test-campaign group lead at DLR Lampoldshausen.
They have ~200 Confluence pages describing the LUMEN-T test campaign
(2024–2026). They are moving to Shepard for the data side and want
the wiki to stay readable. Today: they would either freeze the wiki
(loses currency) or duplicate content (forks the record). After this
synergy: import the Confluence Space as the baseline snapshot S_0;
forge the dataset over the next campaign; each new snapshot
auto-pushes a delta page to the wiki space; the audit trail in
Confluence is identical to the snapshot chain.

For MFFD (`examples/mffd-showcase/pipeline.yaml`): a similar shape
covers the ZLP Augsburg manufacturing logbook. Each AFP layup
shift's data ingest produces a snapshot; the wiki-writer pushes a
shift report to the ZLP Confluence; the auditor reads the wiki, the
engineer queries the graph, both views agree.

## External evidence

- **Confluence Storage Format documentation (Atlassian)** —
  [confluence.atlassian.com/doc/confluence-storage-format-790796544.html](https://confluence.atlassian.com/doc/confluence-storage-format-790796544.html)
  Takeaway: Confluence storage format is XML-based and supports
  `<ac:link>`, `<ac:placeholder>`, `<ac:macro>` — the building blocks
  the wiki-writer needs for the predecessor-link + AI-ribbon shapes.
- **RDA *Persistent Identification of Instruments* white paper —
  Data Science Journal 2020 (`10.5334/dsj-2020-018`)** —
  [datascience.codata.org/articles/10.5334/dsj-2020-018](https://datascience.codata.org/articles/10.5334/dsj-2020-018)
  Takeaway: the FAIR principles applied to *infrastructure*
  metadata demonstrate the value of a single source of truth being
  rendered into multiple human-facing surfaces. Same shape.
- **Atlassian REST API v2 — Content / Spaces** —
  [developer.atlassian.com/cloud/confluence/rest/v2/api-group-page](https://developer.atlassian.com/cloud/confluence/rest/v2/api-group-page)
  Takeaway: page-version management is first-class in the REST API;
  a snapshot delta cleanly maps to a `PUT /pages/{id}` with the new
  body + version.next.

## Effort estimate

**M (medium).** Components:

- Confluence import (aidocs/82) ships first — independently
  planned, ~2 weeks.
- Snapshots (aidocs/41) ship first — already shipped (per
  front-matter `stage: deployed`).
- Wiki-writer baseline (WW1) — already shipped.
- The synergy itself: snapshot-diff endpoint + Confluence-storage
  renderer (~1 week) + sync conflict UI (~3-5 days) + space-creation
  bootstrap (~2 days).

Net incremental: ~2-3 weeks after the three components ship.

## Risk / counter-evidence

- The Atlassian Cloud REST API rate-limits aggressively (~10 req/s
  per token). A campaign with 1000 pages will need batched push +
  retry. Mitigation: rate-limit-aware queue inside the wiki-writer
  (NTF1 nag pattern for failures).
- Confluence Data Center vs Cloud have divergent API shapes; the
  aidocs/82 design targets Data Center. The wiki-writer config has
  to switch transports per target. Mitigation: write-side already
  abstracts via `WikiTarget` SPI (per `project_wiki_writer_plugin`).
- Round-trip with conflict resolution is a classic CRDT
  problem; the *renderable, never canonical* rule sidesteps it but
  costs some user surprise. Mitigation: in-app `/help` doc explains
  the one-way data-of-record discipline. The vision doc already
  carries the forging metaphor that aligns with this.
- The Confluence import handles HTML export; XML export
  (per aidocs/82) is the "preserves hierarchy + metadata" path but
  is harder to parse. If users export XML, the import path may need
  a separate parser. Mitigation: stay on the HTML path for v1 and
  match it on the writer side.
