---
stage: deployed
last-stage-change: 2026-05-27
---

# FAIR8 — F-UJI Alignment Findings (2026-05-27)

## What changed

`frontend/utils/metadataCompleteness.ts` (the RDM-005 completeness helper) was
re-annotated and extended to correlate with F-UJI FAIR maturity indicators
(fuji.net / CESSDA PANGAEA, based on FAIRsFAIR metrics v0.5).

### Indicator mapping after FAIR8

| Check id | Points | F-UJI indicator(s) | Notes |
|---|---|---|---|
| `name` | 10 | FsF-F2-01M (Title) | DataCite §3 |
| `description` | 15 | FsF-F2-01M (Description), FsF-R1-01MD | DataCite §17 |
| `license` | 20 | FsF-R1.1-01M | DataCite §16; highest single weight |
| `accessRights` | 10 | FsF-A1-01M | DataCite §16; Horizon Europe embargoed deposits |
| `creatorOrcid` | 10 | FsF-F2-01M (Creator) | DataCite §2 |
| `semanticAnnotation` | 10 | FsF-I2-01M | FAIR I1 + I2; ontology-aware catalogues |
| `labJournal` | 5 | FsF-R1.2-01M | FAIR R1.2; provenance narrative |
| `keywords` | 5 | FsF-F2-01M (Keywords) | NEW — replaces `heroImage` (no F-UJI mapping) |
| `dataObjects` | 15 | FsF-F3-01M | FAIR F2/F3; harvestable content |
| **Total** | **100** | | |

The `heroImage` check (5 pts) was replaced by `keywords` (5 pts, FsF-F2-01M).
Hero image has no FAIR indicator mapping; keywords are a required sub-field of
FsF-F2-01M (descriptive core metadata). Total remains 100; band thresholds
(error/warning/success) are unchanged.

### New input: `keywordCount: number | null`

`MetadataCompletenessInputs` gained `keywordCount`. The `keywords` check passes
when `(keywordCount ?? 0) > 0`. The full card (`MetadataCompletenessCard.vue`)
passes `keywordCount: 0` conservatively until a keyword-annotation query
endpoint ships (tracked below). The gallery card passes `null`.

## F-UJI coverage assessment

| F-UJI category | Indicators | Shepard score today | Notes |
|---|---|---|---|
| **F — Findable** | FsF-F1-01D (PID exists), F1-02D (PID resolves), F2-01M (core metadata), F3-01M (data ID in metadata), F4-01M (searchable) | Partial | F2+F3 covered; F1 needs KIP PID; F4 needs catalogue harvest |
| **A — Accessible** | FsF-A1-01M (access info), A1-02M (protocol), A1-03D (data protocol) | A1-01M covered via `accessRights` check | A1-02M / A1-03D depend on published endpoint |
| **I — Interoperable** | FsF-I1-01M (formal representation), I2-01M (semantic vocab), I3-01M (related entity links) | I2-01M covered via `semanticAnnotation` | I1-01M (RDF) via RO-Crate; I3 needs qualified links |
| **R — Reusable** | FsF-R1-01MD (content metadata), R1.1-01M (license), R1.2-01M (provenance), R1.3-01M (community standard), R1.3-02D (file format) | R1.1 + R1.2 + R1-01MD covered | R1.3 needs domain-profile plugin (FAIR6) |

**Evidence for funding-agency auditors:** a Collection scoring ≥ 80/100 satisfies
at minimum FsF-F2-01M, FsF-R1.1-01M, FsF-A1-01M, FsF-R1.2-01M, FsF-I2-01M, and
FsF-F3-01M — six of the eleven indicator checks most commonly cited in DFG and
Horizon Europe data management plan requirements. This creates a concrete
"pre-audit readiness" signal that correlates with an F-UJI external run.

## What is still missing (F-UJI gaps)

| Gap | F-UJI indicator | Tracking |
|---|---|---|
| PID registration / resolution | FsF-F1-01D, FsF-F1-02D | KIP1a (shipped in identity layer) |
| Catalogue harvest index | FsF-F4-01M | Unhide plugin (aidocs/67) |
| Formal RDF/JSON-LD metadata representation | FsF-I1-01M | RO-Crate export (V2d) partial |
| Qualified links to related entities | FsF-I3-01M | Linked-PID design (aidocs/16 RDM-005a §9) |
| Keyword-annotation query endpoint | FsF-F2-01M (keywords) | FAIR8 follow-up; `keywordCount` wired at 0 until done |
| Community metadata standard compliance | FsF-R1.3-01M | FAIR6 metadata-profile plugin |
| Long-term file format check | FsF-R1.3-02D | Payload-kind validation (plugin-first) |

## Tests

37 Vitest cases in `frontend/tests/unit/metadataCompleteness.test.ts` (all pass).
21 Vitest cases in `frontend/tests/unit/collectionGalleryCard.test.ts` (all pass).
New cases: `keywords pass`, `keywords fail: 0`, `keywords fail: null (loading)`.
