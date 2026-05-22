# Persona review — Research Data Manager (FAIR Steward)

**Reviewer persona.** Lead Research Data Manager / FAIR Data Steward, evaluating Shepard's planned
SHACL trio (`aidocs/semantics/98-mffd-process-shapes.md`, `aidocs/platform/100-mffd-views-workspace.md`,
`aidocs/platform/101-view-shapes-and-spi.md`, plus `aidocs/semantics/contexts/mffd-context.jsonld`
and the SHACL TTL files) against funder mandates (DFG, EU Horizon Europe, Clean Aviation JU,
Helmholtz Association) and the planned Databus + MOSS publication path.

**Date.** 2026-05-22.

**Reviewer note — source-of-truth caveat.** The four artefacts named in the brief
(`98-mffd-process-shapes.md`, `100-mffd-views-workspace.md`, `101-view-shapes-and-spi.md`,
`contexts/mffd-context.jsonld`) are **not present on disk** as of this review (no entries
in `aidocs/semantics/`, no `contexts/` directory, no `shapes/` directory; no occurrences
in any worktree or git history). The closest existing anchors are:

- `aidocs/semantics/95-shacl-templates-and-individuals.md` — the parent design that the trio
  refines (high confidence).
- `aidocs/integrations/77-databus-moss-federation.md` — the publication-target design
  (shepard MOSS module shapes already drafted in §2).
- `aidocs/integrations/67-unhide-publish-plugin.md` — the existing schema.org+m4i+DCAT
  publishing surface.
- `aidocs/integrations/92-mffd-real-data-import-strategy.md` — the MFFD process inventory the
  trio's process shapes will target.

This review treats those four as the **specification surface the trio must satisfy**, calls
out where the trio MUST land specific guarantees, and flags areas where a FAIR-steward sign-off
should block merge until the artefacts exist.

---

## 1. Verdict (one paragraph)

The SHACL trio is the single most important slice on Shepard's FAIR roadmap, because it is the
artefact that finally turns ad-hoc annotation keys into a controlled vocabulary an external
auditor can trust. The architecture (shapes as templates + views + agent contracts, named
individuals, upper-ontology alignment to BFO/IAO/EMMO/PROV-O, metadata4ing + Dublin Core in
the MOSS module) is **structurally correct** and visibly ahead of the European RDM peer group
(Kadi4Mat ships SHACL export but not SHACL-driven UI; SciCat and openBIS don't ship SHACL at
all). However, three FAIR-fatal gaps will block DFG/EU/Clean Aviation acceptance if the trio
ships in its currently-scoped shape: (1) no `dcterms:license` on the `:DataObject`/`:Collection`
model (FAIR **R1.1** failure — every competitor has it; this fork doesn't), (2) no
`dcat:accessRights` + embargo enum (FAIR **A1.2** failure — open-vs-restricted-vs-closed
cannot be expressed), (3) no canonical citable DOI path — the architecture has KIP PIDs and a
planned InvenioRDM plugin but the trio doesn't bind them to the export shape. Fix those three
inside the trio (not as follow-ups), and Shepard becomes the only DLR-internal RDM platform
that can satisfy Clean Aviation JU's data-management-plan template **and** federate via MOSS
**and** drive its UI from the same SHACL artefact. Ship them later and the trio is
demoware that an EU reviewer will reject on first read.

---

## 2. FAIR scorecard (current state, before trio merges)

| Dim | Score | Justification |
|---|---|---|
| **F (Findable)** | **2 / 3** | UUID v7 `appId` is globally unique and resolves to a Shepard locator; KIP-PID path designed (`aidocs/66`) and Unhide harvest feed shipped (`aidocs/67` UH1a). Missing: DOI binding (FAIRDOM-SEEK / Kadi4Mat / Zenodo have it), and `appId` is not yet a dereferenceable IRI in the trio's `mffd-context.jsonld`. |
| **A (Accessible)** | **1 / 3** | OIDC + Keycloak shipped; audit log via PROV1a. Missing: `dcat:accessRights` enum (`OPEN`/`RESTRICTED`/`CLOSED`/`EMBARGOED`); missing `embargo_until` field; no documented `https://w3id.org/` style permanent IRI; access-conditions vocab not seeded in `:SemanticRepository`. Funder review will flag this immediately. |
| **I (Interoperable)** | **2 / 3** | metadata4ing + PROV-O + Dublin Core + schema.org all in the Unhide and Databus shapes; SHACL-validated MOSS module designed; upper-ontology alignment to BFO/IAO/EMMO is the trio's stated goal. Missing: CHAMEO and Material OWL seeded for MFFD CFRP context (`aidocs/semantics/96` calls them out but the V49 bootstrap migration doesn't seed them); QUDT units bound to channel-level annotations. |
| **R (Reusable)** | **1 / 3** | Provenance chain exists (Predecessor/Successor + PROV1a Activities); planned RO-Crate export referenced in InvenioRDM plugin (`aidocs/72`). Missing: **`dcterms:license` field on DataObject and Collection** — this is the single biggest FAIR-R gap and a hard fail for R1.1; no human-readable provenance statement generator; no `dcterms:rightsHolder`; no contribution role (`prov:hadRole`) vocabulary. |

**Aggregate: 6 / 12.** After the three structural fixes proposed in §7 this rises to ~10 / 12
within one sprint of additional work — the floor for an EU-fundable platform.

---

## 3. What works (3-5 bullets)

- **Three-layer architecture (`95` §2)** — ontology layer carries vocabulary + shape; Neo4j
  carries instances + perf; service layer connects. This is the right shape; it is the *only*
  open-source RDM platform I know that proposes SHACL-driven UI rendering. Kadi4Mat ships SHACL
  schema *export*; Shepard plans SHACL schema as the *source of truth*. That is a defensible
  EU-funding differentiator.

- **MOSS module already drafted (`77` §2.1–2.4)** — the `shepard` MOSS module has `module.yml`,
  `shapes.ttl`, `indexer.yml`, and `context.jsonld` ready to commit, using metadata4ing
  (`m4i:ProcessingStep`), PROV-O, and Dublin Core terms. Federation to DLR-wide Databus is
  one plugin away (`shepard-plugin-databus`), and the same shapes can extend to other
  institutes without central coordination.

- **PROV-O captured automatically (PROV1a)** — every admin / write action lands in `:Activity`
  with `prov:wasGeneratedBy`. This is the substrate every funder asks for ("show me the
  audit trail") and most RDM platforms hand-roll badly. Shepard's is automatic and W3C-standard.

- **`appId` (UUID v7) is time-ordered, globally unique, dereferenceable** (`aidocs/91`).
  This is the right identity primitive — better than Neo4j-internal ids (which leak), better
  than per-institute counters (which collide on federation). The trio's job is to expose it
  as `https://shepard.dlr.de/v2/.../{appId}` and bind that IRI in `mffd-context.jsonld`.

- **Plugin-first publication path** — Unhide, KIP, Invenio, Databus, MOSS each ship as a
  plugin against the same shape. Operators choose which publication targets to enable; FAIR
  posture is layered, not all-or-nothing. This is the structurally correct answer to "we publish
  to N catalogues" — the alternative (every catalogue in-tree) does not scale to 2030.

---

## 4. What's wrong / missing (file-cited)

1. **`dcterms:license` field is absent from `:DataObject` and `:Collection`.** Cited as the
   `#1 actionable gap` in `/root/.claude/projects/-opt-shepard/memory/project_competitive_position.md`.
   Every competitor (Kadi4Mat, openBIS, FAIRDOM-SEEK, NOMAD, SciCat) ships this; we don't.
   FAIR R1.1 says "(meta)data are released with a clear and accessible data usage license."
   Trio MUST add `sh:property [ sh:path dcterms:license ; sh:datatype xsd:anyURI ; sh:minCount 1 ]`
   to both the `Collection` and the `DataObject` shape, and the field MUST be present in
   `mffd-context.jsonld`. Backend column is one Flyway migration. **Without this the trio is
   demoware, not infrastructure.**

2. **`dcat:accessRights` + embargo are absent.** No `OPEN | RESTRICTED | CLOSED | EMBARGOED`
   enum on `:DataObject`/`:Collection`; no `dcterms:available` (embargo-until date). FAIR A1.2
   requires the access protocol to specify "where necessary, an authentication and authorization
   procedure" — the data model has no field to even encode "this is embargoed until 2027-03-01".
   MFFD is industry-IP gated; PLUTO is mission-data gated; both need this from day one. Trio
   MUST encode this in the SHACL shapes and the `:CollectionProperties` Neo4j entity.

3. **DOI binding is undefined.** `aidocs/66` (HMC KIP) mints handle-PIDs; `aidocs/72`
   (InvenioRDM plugin) proposes minting DOIs; `aidocs/77` (Databus) mints Databus URIs. The
   trio does **not** specify which IRI ends up in `mffd-context.jsonld` as the canonical
   citable identifier. An EU project DMP requires *one* citable PID per dataset; ambiguity
   here means each tool emits a different one. Trio MUST pick: KIP-handle is the default
   internal PID; DOI (via Invenio) is the canonical external citation; Databus URI is the
   federation handle. All three resolve to the same Shepard locator; only one is the citation.

4. **`mffd-context.jsonld` doesn't exist yet, but the schema.org / DataCite export shape is
   undefined.** The Unhide plugin (`67` §B.4) emits schema.org+m4i JSON-LD on the harvest
   feed. The InvenioRDM plugin emits DataCite XML. The Databus plugin emits a Databus
   `dataid.ttl`. The trio must publish a **single canonical mapping table** —
   `Collection.<field> → schema.org / DataCite / DCAT / m4i` — so that an operator can answer
   "what does this dataset look like to Unhide vs. to Databus vs. to DataCite?" with a single
   document, not three plugin source files. Without that table, drift between the three
   publication targets is inevitable.

5. **CHAMEO + Material OWL are referenced but not seeded.** `aidocs/semantics/96` calls out
   upper-ontology alignment to BFO/IAO/EMMO/CHAMEO/Material OWL; the V49 bootstrap migration
   (`backend/src/main/resources/neo4j/migrations/V49__Bootstrap_internal_semantic_repository.cypher`)
   does not seed CHAMEO or Material OWL terms. The MFFD process shapes will need
   `chameo:hasParameter`, `mat:Material`, `qudt:Unit` — if those terms aren't in the repo
   when the trio ships, the shapes won't validate. Trio MUST include a follow-up migration
   that seeds CHAMEO + Material OWL + QUDT (and a rollback file) in the same PR.

---

## 5. Arguments for different paths (forks to decide)

### Fork A — PID strategy (canonical citable identifier)

**Option A1 — UUIDv7 `appId` is the canonical PID, no DOI/Handle.**
*Pro:* zero external dependencies, immutable, time-ordered, already shipped.
*Con:* FAIR F1 says "globally unique and **persistent**" — UUID is unique, but "persistent"
in the FAIR sense is read as registered with a resolver authority (Handle/DOI). EU reviewers
will read "UUID is our PID" as non-compliant. *Also:* DataCite/Crossref discovery surfaces
will not index UUID-only datasets.

**Option A2 — Handle (KIP, ePIC) as canonical internal PID + DOI for published artefacts.**
*Pro:* matches existing DLR practice (KIP path designed in `aidocs/66`); decouples internal
identifier (always exists) from external citation (only when published). *Con:* two-tier PID
adds operator complexity; the Handle prefix policy is a DLR-internal coordination cost.

**Option A3 — DOI everywhere via InvenioRDM (`aidocs/72`).**
*Pro:* single citable identifier; DataCite ecosystem indexes for free; one document, one DOI.
*Con:* DOIs are costly (per-minted, ~3€), commitment-heavy (DOIs are forever — Shepard would
need a no-delete policy on minted entities), and inappropriate for draft/IN_REVIEW data.

**Lean: A2.** Handle for everything internal (free, no commitment, DLR-aligned via KIP);
DOI minted only on `PUBLISHED` status via Invenio plugin, only when the researcher
explicitly opts in. Best fit for "active RDM" (Shepard is not an archival repository) with
a clean handoff to archival systems. Trio binds Handle as the default in `mffd-context.jsonld`;
DOI is an additional `dcterms:identifier` when present.

### Fork B — Publication target (federation reach)

**Option B1 — Unhide-only (Helmholtz KG).**
*Pro:* shipped; one publication target; minimal operator friction.
*Con:* Helmholtz-internal discovery only; no external citation; doesn't satisfy Clean
Aviation JU "open by default" mandate; no DOI.

**Option B2 — Unhide + DLR Databus + MOSS.**
*Pro:* DLR-wide cross-institute discovery (`77` is built for this); SPARQL federation; no
external commitment beyond DLR boundary. *Con:* internal-only — externals can't find datasets;
no DOI; Databus is DBpedia-shaped, not domain-shaped (MOSS module fixes this but is operationally
two services).

**Option B3 — Unhide + Databus/MOSS + InvenioRDM + Zenodo push.**
*Pro:* covers internal discovery (Unhide/MOSS), DLR federation (Databus), citable publication
(Invenio + DOI), and global archival fallback (Zenodo) — full FAIR coverage.
*Con:* four plugin integrations; operator-side complexity; risk of inconsistent metadata
across the four targets unless the canonical mapping table (§4 item 4) lands first.

**Lean: B3, sequenced.** Ship Unhide + Databus/MOSS first (UH1a is shipped; DB1a is the
next plugin slot). Add Invenio (DOI) when the publication-status workflow exists. Zenodo
last as the archival fallback. The trio MUST land the canonical mapping table before B3 is
attempted, otherwise the four targets drift.

### Fork C — Provenance dump shape (what gets exported with the data?)

**Option C1 — RO-Crate only.**
*Pro:* W3C-aligned, repository-friendly (Zenodo, InvenioRDM both consume it),
human-readable JSON-LD manifest, designed for "package this dataset" scenarios.
*Con:* doesn't carry the full SHACL shape; lossy for the trio's named-individuals graph;
PROV-O nesting is awkward.

**Option C2 — PROV-O Turtle dump only.**
*Pro:* lossless for the provenance graph; SPARQL-queryable; native to MOSS.
*Con:* not a "package" format — repositories don't ingest it; researcher friction
("what do I do with this Turtle file?").

**Option C3 — Both, dual export.**
*Pro:* RO-Crate is the package (one file, drag into Zenodo); PROV-O is the metadata fidelity
(consumed by MOSS, SPARQL clients). *Con:* dual maintenance burden; risk of drift.

**Lean: C3.** RO-Crate IS the package format — operators expect it; consumers expect it.
PROV-O Turtle is the metadata fidelity layer for MOSS + SPARQL. They are different artefacts
serving different audiences; ship both. The canonical mapping table (§4 item 4) is what
keeps them consistent — single source-of-truth for "this field appears in RO-Crate as X and
in PROV-O Turtle as Y."

---

## 6. `[NEEDS-CLARIFICATION]` blocks

```
[NEEDS-CLARIFICATION] Should the SHACL trio land `dcterms:license` + `dcat:accessRights`
  in the same PR, or as immediate follow-ups?
  Context: §4 items 1–2. Without these the trio is FAIR-non-compliant for R1.1 and A1.2.
    Memory `project_competitive_position.md` lists license as the #1 actionable gap.
  Options:
    A) Land both fields in the trio PR (one Flyway migration + SHACL property + frontend form). — pro: FAIR-compliant on landing, no demoware moment. con: scope creep.
    B) Land the trio first; license/accessRights as immediate follow-up PRs. — pro: scope discipline. con: trio merges in a state we'd be embarrassed to show an EU reviewer.
    C) Land license now; accessRights with embargo workflow as a separate slice. — pro: license is one field, accessRights is a state machine. con: still partial.
  Lean: A — Funder review is a hard gate. License + accessRights enum (no embargo workflow yet)
    is one migration + ~30 lines of SHACL + ~50 lines of form code. Worth the scope creep.

[NEEDS-CLARIFICATION] Is the canonical citable PID Handle (KIP), DOI (Invenio), or
  Databus URI?
  Context: §5 Fork A. Three PID-minting plugins; one citation field in `mffd-context.jsonld`.
  Options:
    A) Handle (KIP) as default internal PID; DOI optional on PUBLISHED. — pro: free, no commitment,
       DLR-aligned. con: two-tier.
    B) DOI everywhere via Invenio. — pro: single PID. con: cost + commitment + inappropriate for drafts.
    C) Databus URI as canonical. — pro: federation-native. con: not a citation PID in DataCite/Crossref sense.
  Lean: A — matches DLR practice and lets the trio ship without external commitments.
    DOI is opt-in via Invenio at PUBLISHED handoff.

[NEEDS-CLARIFICATION] Should the canonical mapping table (Shepard field → schema.org / DataCite
  / DCAT / m4i) live inside the trio, or as a sibling doc (e.g., `aidocs/semantics/99-export-shape-mapping.md`)?
  Context: §4 item 4. Three plugins (Unhide, Invenio, Databus) currently each define their own
  field mapping. Drift is the risk.
  Options:
    A) Inside `101-view-shapes-and-spi.md` as a §X section. — pro: co-located with view definitions.
       con: trio already large.
    B) Sibling doc `99-export-shape-mapping.md`, cited by the trio and by `67/72/77`. — pro: stable
       reference for plugins. con: one more file.
    C) JSON file (`aidocs/semantics/contexts/export-shape-mapping.json`) machine-readable + sibling doc. — pro:
       plugins can load it at build/test time. con: most-effort.
  Lean: C — machine-readable canonical mapping is the structural fix that prevents drift.
    Plugins read it at test time and assert their emitted JSON-LD matches.

[NEEDS-CLARIFICATION] Does the trio seed CHAMEO + Material OWL + QUDT in a migration, or
  rely on operators preseeding them via ONT1c?
  Context: §4 item 5. `aidocs/65` (ONT1c) makes ontology preseed admin-configurable. The trio's
  MFFD process shapes assume CHAMEO + Material OWL terms exist; ONT1c is the seam where
  operators configure ontology source.
  Options:
    A) Migration `V##__Seed_chameo_matowl_qudt.cypher` ships with the trio. — pro: trio works
       out of the box. con: every operator gets the terms whether they want them or not.
    B) Trio references ONT1c-shaped preseed config; operator opts in via admin REST. — pro:
       admin-configurable per the CLAUDE.md "operator knobs" rule. con: trio fails out of the box
       on a fresh instance.
    C) Migration seeds a minimal subset (chameo:Parameter, qudt:Unit, mat:Material core); ONT1c
       configures the rest. — pro: trio works out of the box for MFFD demo; bigger ontologies opt-in. con: split.
  Lean: C — minimal seed for demo, ONT1c for full coverage. Matches the §3 admin-knobs pattern
    in CLAUDE.md and the §2.2 admin-configurable ontology preseed rule.

[NEEDS-CLARIFICATION] Should provenance export be RO-Crate, PROV-O Turtle, or both?
  Context: §5 Fork C. Two consumer audiences (repositories vs SPARQL clients).
  Options: A) RO-Crate only. B) PROV-O only. C) Both, dual export.
  Lean: C — different artefacts for different audiences. The canonical mapping table (above)
    keeps them consistent.

[NEEDS-CLARIFICATION] Does the trio enforce a single ORCID at Activity-creation, or accept
  the current free-text actor field?
  Context: PROV1a captures `Activity.actor` as User reference. Memory
  `project_competitive_position.md` lists "ORCID stamp at entity creation" as gap #2.
  Without ORCID on `prov:wasAssociatedWith`, the export shape can't satisfy DataCite
  `creator.nameIdentifier`.
  Options:
    A) Make ORCID mandatory on User profile, fail Activity creation without it. — pro: clean export.
       con: blocks anonymous-actor activities (migrations, auto-imports).
    B) ORCID optional on User; export emits it when present, falls back to displayName. — pro: pragmatic.
       con: drift.
    C) ORCID mandatory for User of role `researcher`; optional for `instance-admin` / `service-account`. — pro:
       role-appropriate. con: more rules.
  Lean: C — researcher role gets the ORCID requirement; admin/service accounts don't (they
    use Shepard's instance ORCID-equivalent ROR + displayName).
```

---

## 7. Top 3 changes to satisfy DFG / EU funder review

1. **Land `dcterms:license` + `dcat:accessRights` (enum) + `dcterms:available` (embargo date)
   in the trio's SHACL property shapes.** Single Flyway migration adds three columns to
   `:Collection` and `:DataObject`; SHACL property declarations enforce minCount/datatype at
   validation time; `mffd-context.jsonld` binds the three terms; frontend form (already
   shape-driven once trio ships) renders dropdowns automatically. This closes FAIR R1.1 + A1.2
   in one slice. **No funder review survives without these three fields.**

2. **Publish a canonical export-shape mapping table** (`aidocs/semantics/99-export-shape-mapping.md`
   + machine-readable JSON sibling). Single source of truth that says "for every Shepard
   field, here is its term in schema.org, DataCite, DCAT, m4i, and PROV-O." Unhide, Invenio,
   Databus, MOSS, RO-Crate exporters all consume this table; CI test asserts emitted JSON-LD
   matches it. Prevents drift between the four publication targets and gives reviewers a
   one-page artefact answering "how does Shepard map to FAIR vocabularies?"

3. **Seed CHAMEO + Material OWL + QUDT in the V49+1 bootstrap migration (minimal subset);
   register the full ontologies as ONT1c-installable.** The trio's MFFD process shapes need
   `chameo:hasParameter` and `qudt:Unit`; the rest of the upper ontology is a domain choice.
   Ships a working MFFD demo out of the box, satisfies the CLAUDE.md "operator knobs" rule
   for the larger ontology imports, and lets Clean Aviation JU reviewers see that the platform
   is upper-ontology-aligned without forcing every operator to download 200 MB of OWL on first boot.

---

**Reviewer recommendation.** Block trio merge on items §6-A, §6-C, and §6-F (clarifications
on license/accessRights, mapping table location, and ORCID policy). The other three
clarifications can be answered concurrently with implementation. With those three answered
and §7 items 1–3 in scope, the trio becomes the slice that takes Shepard from "interesting
RDM prototype" to "Clean Aviation JU + DFG-fundable infrastructure" — and the only DLR-internal
platform that drives its UI from the same SHACL artefact that exports its FAIR metadata.
