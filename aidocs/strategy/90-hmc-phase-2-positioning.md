---
stage: feature-defined
last-stage-change: 2026-05-23
---

# 90 — HMC Phase 2 positioning: Shepard's pre-committed work-packages at DLR ZLP

**Audience.** DLR HMC programme management; ZLP leadership reviewing
Phase 2 commitments; Shepard contributors needing to know which
roadmap items are externally promised; thesis readers (§4 of the
outline in `project_thesis_idea.md`) tracing how Shepard's
direction is shaped by Helmholtz funding obligations.

**Status.** Reconstructed from Flo Krebs's HMC Phase 2 work-package
proposal slide deck for DLR ZLP Augsburg (uploaded to AI working
memory 2026-05-23 as `4edb9770-HMC_2_Workpackages.pptx`,
preliminary version with placeholder presenter/date footer). The
three WPs below are presented in the deck as Shepard's Phase 2
contribution; this doc distils them and ties each to the existing
aidocs roadmap.

---

## 1. What HMC is and why Phase 2 matters

The **Helmholtz Metadata Collaboration (HMC)** is the cross-centre
metadata-and-FAIR-data programme of the Helmholtz Association.
DLR is a Helmholtz centre; HMC is the institutional accountability
layer that translates the FAIR principles (Wilkinson 2016, see
`docs/_data/references.bib` `wilkinsonFair2016` and
`hmcFairRequirements`) into Helmholtz-wide expectations for
centre-level RDM infrastructure.

HMC operates in funding phases. **Phase 2** is the current cycle in
which DLR ZLP commits a set of work-packages that Shepard then has
to deliver. The deck records three such WPs.

### 1.1 Why this is more strategic than internal — the BMFTR PoF V framing

The HMC Phase 2 WPs do not sit inside DLR alone. They are nested
inside a chain of accountability that reaches the German federal
ministry. The chain became explicit in a 2025-09-24 letter from
the Bundesministerium für Forschung, Technologie und Raumfahrt
(BMFTR) to the President of the Helmholtz Association,
Prof. Dr. Otmar D. Wiestler [@bmftrPofV2025]. The letter
transmits the **übergreifende forschungspolitische Ziele für die
fünfte Periode der programmorientierten Förderung (PoF V)** —
the cross-cutting research-policy goals for the *fifth period of
programme-oriented funding*, covering **2028–2034**.

The letter is short (two pages, signed by Dr. Jochen Zachgo,
Leiter Abteilung 4 "Hochschul- und Wissenschaftssystem;
Bildungsfinanzierung"). It is structural rather than thematic:
the ministry instructs the Helmholtz presidium that the
overarching policy goals for PoF V shall comprise

1. *(a) einen rein deklaratorischen Verweis auf die Geltung der
   forschungspolitischen Ziele des PFI* — a purely declaratory
   reference to the PFI (Pakt für Forschung und Innovation)
   research-policy goals;
2. *(b) die Entwicklung einer gemeinsamen Nachhaltigkeitsstrategie*
   — the development of a joint sustainability strategy;
3. **(c) die Entwicklung eines gemeinsamen und übergreifenden
   Architekturkonzepts für die Datenmanagement-Infrastruktur**
   — the development of a *joint and overarching architecture
   concept for the data-management infrastructure*.

Point (c) is Shepard's territory at the Helmholtz strategic
level. The ministry is telling the Helmholtz centres collectively
that, for PoF V (the next funding cycle after the current one),
they shall arrive with a *common architectural concept* for the
data-management infrastructure — not a thematic content
programme. HMC is the obvious vehicle through which that concept
will be drafted; the WPs in §2 below are the centre-level
contributions HMC will aggregate.

The political consequence for Shepard's roadmap is concrete: any
architectural choice that closes off cross-centre federation
(e.g. a proprietary protocol substrate, a tightly coupled Neo4j
schema that other centres cannot adopt without inheriting
Shepard's full deployment shape) becomes a **political
liability** as well as a technical one. The work-packages in §2
are therefore not "nice-to-have HMC contributions"; they are the
DLR ZLP slice of the joint architectural concept the ministry has
asked the association to draft. WP-3 (cross-DLR interoperability,
§5) is the part of the deck that most directly addresses the
ministry's structural ask.

The letter does not mention Shepard. The connection from
ministerial PoF V goals to Shepard's `aidocs/16` rows is mediated
by HMC and by the DLR centre-level commitments documented here.
Recording that mediation is the contribution this section makes.

The pattern matters because:

- WPs in this deck are **promises to HMC programme management**, not
  internal wishlist items. They drive priority irrespective of
  internal `aidocs/16` ranking.
- HMC alignment is one of the four funding-body alignments (with
  NFDI4Ing — see `aidocs/strategy/88-nfdi4ing-alignment.md`; DFG —
  `aidocs/strategy/75-dfg-eresearch-funding.md`; Clean Aviation JU
  — `aidocs/strategy/74-dlr-bt-stakeholder.md`) that justify
  continued Shepard development funding inside DLR.
- The WPs span three layers (export/import, semantic features,
  cross-DLR interoperability) — each maps to a distinct
  Shepard architectural seam.

---

## 2. The three committed work-packages

The HMC Phase 2 deck lists three Shepard-bearing work-packages
for DLR ZLP:

1. **Solutions for data export and import** (§3 below)
2. **Shepard semantic features extensions development** (§4 below)
3. **Establish interoperability between data management solutions in DLR** (§5 below)

---

## 3. WP-1: Solutions for data export and import

### What the deck promises

Three sub-streams:

| Sub-stream | Slide claim | Mapped Shepard item |
|------------|-------------|---------------------|
| FAIR data packaging formats | "Investigation into formats like **RO-Crate** to standardize research data packaging and sharing" | `aidocs/integrations/66-hmc-kip-integration.md`; FS1g (RO-Crate export, shipped per `aidocs/strategy/82` §H1); `roCrate11` bib entry |
| Shepard import/export functionalities | "Development of import and export functionalities within the Shepard system" | v2 importer (live, see `aidocs/integrations/...`); v2 export endpoints (planned, see `aidocs/16` rows for EXPORT-*); MFFD real-data import (2026-05-22 arc) is the operational evidence |
| **Persistent Identifier (PID) integration** | "Integrate PID generators for uniquely identifying digital objects" | KIP DOI minting via `aidocs/integrations/66-hmc-kip-integration.md`; PIDINST federation via inst.dlr (`aidocs/strategy/73` table); `appId` (UUID v7) as the local PID substrate, awaiting external resolver wiring |

### Honest status

RO-Crate export shipped (FS1g). PID minting **designed but not
yet operational against an external resolver** — the KIP doc lays
out the path; ePIC / DataCite credentials are an operator
provisioning step, not a code task.

The import side runs daily in production (MFFD ingest at cube3 +
nuclide; see `RESUME.md` for the live arc). The export side has
fewer real consumers — the next operational test would be a full
MFFD AFP campaign round-tripped through RO-Crate to InvenioRDM
(per `aidocs/strategy/82` §H1).

### What this WP doesn't yet have

- **Round-trip evidence.** RO-Crate out, RO-Crate in, identical
  semantic content — that's the integrity test.
- **Operator-facing PID flow.** "How do I mint a DOI for this
  Collection?" is a one-click question that needs a one-click
  answer.

### The Pub-Service shape (Krebs whiteboard sketch, 2026-05-23)

The concrete shape of the publication pipeline this WP needs to
deliver is recorded in a whiteboard sketch by the WP-author
[@krebsFederationSketches2026, sketch B]. The sketch traces a
single data flow from a Shepard `Sammlung V1` (collection) through
an attached `meta.json` (carrying a `DL-Lin…` licence annotation)
into a *Pub Service* component, which then branches to three
downstream targets: **Databus** (the dbpedia/databus + MOSS
federation substrate, cf.\ §5), **S3** (object storage —
Garage per ADR-0024), and **InvenioRDM** (the publication
repository). An `md5` checksum is noted on the collection node,
and an `FDM Institution` header in the right margin records the
institutional owner of the publication act (in DLR ZLP's
deployment: the centre itself).

Three commitments are encoded in this sketch that this WP makes
operational:

1. **Publication is a separate service, not a Shepard endpoint.**
   The Pub Service is named as a distinct component, which lets
   the publication logic — licence checks, PID minting, format
   conversion, target routing — accumulate without bloating
   Shepard core. This aligns with the plugin-first rule
   (`aidocs/platform/47`): the publication seam is exactly the
   shape `shepard-plugin-publisher` would take.
2. **Multi-target by design.** The sketch shows three downstream
   branches, not one. The WP's deliverable is not "Shepard to
   InvenioRDM" or "Shepard to Databus" specifically; it is the
   *router* shape that lets an operator pick the target by
   licence, audience, or institutional policy. This anticipates
   `aidocs/integrations/67-unhide-publish-plugin.md` (the
   Helmholtz Unhide harvest seam already shipped) being one such
   target alongside Databus and InvenioRDM.
3. **Integrity travels with the publication.** The `md5` notation
   on the collection node makes the integrity hash a first-class
   part of the meta-record, not a post-hoc audit artefact. A
   reviewer downstream can verify that the bits she retrieved
   from S3 match the bits Shepard published.

This sketch is a **thesis architecture-chapter figure candidate**
for §3 / §6 of the outline in `project_thesis_idea.md`. The
physical original needs to be re-uploaded as a file artefact for
PDF embedding; the description here is sufficient for the WP-1
scoping conversation.

---

## 4. WP-2: Shepard semantic features extensions development

### What the deck promises

> "Usability and ease of use of ontology-based annotation in Shepard
> require improvement. Investigate methods for stronger integration
> of ontologies within Shepard. Develop a comprehensive integration
> concept to address usability issues."

### Mapped Shepard items

This WP is the load-bearing semantic-substrate commitment. It maps
directly to the in-flight semantics aidocs:

- `aidocs/semantics/94-metadata4ing-integration-design.md` — m4i
  deepening (the NFDI4Ing-canonical engineering ontology Shepard
  must speak natively)
- `aidocs/semantics/95-shacl-templates-and-individuals.md` —
  SHACL templates as the user-facing annotation surface (the
  "usability" pillar named in the slide)
- `aidocs/semantics/96-upper-ontology-alignment.md` — alignment
  to IOF / IAO / BFO so the annotations interoperate
- `aidocs/semantics/98-shapes-views-and-process-model.md` —
  shapes-as-views (the M1 wave that resolves the "ontology
  annotation feels like vocabulary lookup, not data work" problem)
- `aidocs/semantics/65-admin-configurable-ontology-preseed.md` —
  the admin surface (N1c2) that lets operators turn vocabularies
  on/off without redeploying

### The honest read

The slide says "usability of ontology-based annotation requires
improvement" — that's an explicit acknowledgement that the current
state is **not yet usable enough**. The M1 wave (TS-IDc + POST
/v2/shapes/render + Trace3D — see `aidocs/strategy/86` decision
2026-05-23 in `aidocs/agent-findings/...`) is the answer; it's not
shipped yet. The WP commitment is to **ship the M1 wave + collect
real-user feedback during MFFD operational use**.

### Cross-funding leverage

This WP overlaps directly with NFDI4Ing's m4i obligations (see
`aidocs/strategy/88-nfdi4ing-alignment.md` §3). HMC funds the
"make it usable" half; NFDI4Ing funds the "speak m4i natively"
half. The work is the same code path with two funding rationales.

---

## 5. WP-3: Establish interoperability between data management solutions in DLR

### What the deck promises

This is the cross-DLR federation WP — the most ambitious of the
three. The slide names four DLR data-management systems explicitly:

| System | DLR organisational unit | Existing Shepard touchpoint |
|--------|-------------------------|------------------------------|
| **Shepard** | BT (Bauweisen und Strukturtechnologie, our home) + RY (Raumfahrtsysteme) | This codebase; two production instances per `project_dlr_institutional_strategy.md` |
| **twinStash** | FX / SP | Mentioned in `aidocs/strategy/73-dlr-stakeholder.md`; landscape analysis target |
| **Defacto** | SG | New; no existing aidocs touchpoint — needs a survey row |
| **Inst.DLR** | IW (Institut für Werkstoffforschung) | PIDINST integration target per `aidocs/strategy/73`; instrument metadata registry |

The deck names four activities:

1. **Landscape analysis** — map existing solutions, assess current state
2. **Interoperability investigation** — connections to DLR systems + external data spaces (Aeronautics Dataspace, NFDI Data Space)
3. **Data integration** — methods to unify diverse sources
4. **Prototype development** — federation prototype demonstrating interconnected DM systems

### The Databus + MOSS foundation

The deck's final slide (slide 8, "Federating data in DLR") makes a
specific architectural commitment:

> "Databus and MOSS as foundation. Integration with Shepard (and
> other DLR data tools — e.g. TwinStash). Goal: an interactive
> modular publication service that consolidates existing data and
> metadata (automated as much as possible from existing systems)
> and registers them in the databus."

Concrete references:

- **Databus** = dbpedia/databus
  (<https://github.com/dbpedia/databus>) — "a digital factory
  platform for managing files online with stable IDs, high-quality
  metadata, powerful API and tools for building on data". Already
  in the artefacts pile via the **NFDI4Energy databus and MOSS
  guide PDF** (uploaded 2026-05-23 as
  `6e8723b0-NFDI4Energy_databus_and_MOSS_guide.pdf`); that document
  is the implementation manual Shepard's integration would follow.
- **MOSS** — Metadata Online Submission System
  (companion to Databus, also documented in the same NFDI4Energy
  guide).

This is the DataHub target per `project_dlr_institutional_strategy.md`
(D4 axis): Databus + MOSS are the **federation substrate** that
Shepard publishes into, not a competing system.

### Honest status

- **Landscape analysis**: partial. Shepard documented; twinStash
  named but not surveyed; Defacto entirely unknown; Inst.DLR
  designed-against in `aidocs/strategy/73` but not surveyed.
- **Interoperability investigation**: starts with the Unhide feed
  (`/v2/unhide/feed.jsonld`, shipped) as the existing federation
  seam. Databus integration is design-pending.
- **Data integration**: m4i is the lingua franca; the federation
  protocol is RO-Crate + Unhide feed today, Databus tomorrow.
- **Prototype development**: nothing yet. The MFFD multi-instance
  arc (cube3 + nuclide + future cube) is the closest operational
  evidence; full federation prototype is post-Phase-2-midpoint
  work.

---

## 6. What this means for Shepard's roadmap

Three things follow from these being **HMC-committed** WPs rather
than internal preferences:

1. **The semantic-features WP (§4) is the highest-priority
   in-flight work.** It's the one with the loudest funding-body
   commitment AND the explicit "usability is insufficient"
   acknowledgement. The M1 wave shipping schedule is HMC-visible.

2. **WP-1's PID flow needs an operator-facing seam before Phase
   2 end-of-cycle.** Mint-a-DOI as a one-click operation is the
   acceptance test for "Shepard ships its data with HMC-compliant
   identifiers."

3. **WP-3 is the seed of the DataHub story.** The federation
   prototype Flo commits to in Phase 2 is the first concrete
   step toward the institutional strategy's D4 DataHub axis. The
   2026-05-23 Databus + MOSS PDF is therefore the canonical
   integration reference; Shepard's federation work-stream rests
   on that document's protocol claims.

---

## 7. Sources

- `4edb9770-HMC_2_Workpackages.pptx` — HMC Phase 2 work-packages
  deck by Florian Krebs, DLR ZLP Augsburg. Uploaded to AI working
  memory 2026-05-23; preliminary version (footer placeholders
  unfilled). Bib entry: `hmcPhase2WpKrebs2025` (see `docs/_data/references.bib`).

- `6e8723b0-NFDI4Energy_databus_and_MOSS_guide.pdf` — NFDI4Energy
  Databus + MOSS integration guide. Uploaded to AI working memory
  2026-05-23. Cited in §5 as the federation-substrate manual.

- `hmcFairRequirements` (existing bib) — Helmholtz Metadata
  Collaboration's operational expression of FAIR; the
  accountability framing this WP set lives under.

- `aidocs/integrations/66-hmc-kip-integration.md` — KIP DOI
  minting design (the §3 PID mechanism).

- `aidocs/strategy/82-zlp-augsburg-stakeholder.md` — ZLP-level
  stakeholder map (where these WPs sit at the institute level).

- `aidocs/strategy/73-dlr-stakeholder.md` + `74-dlr-bt-stakeholder.md`
  — broader DLR stakeholder landscape (the federation targets in §5).

- `aidocs/strategy/88-nfdi4ing-alignment.md` (sibling) — NFDI4Ing
  positioning; cross-funding overlap with WP-2 explained there.

---

## 8. Honest companion

This document records what was **promised in a Phase 2 proposal
deck**. Promises are not deliveries. Reviewers reading this for an
HMC mid-cycle check should compare:

- WP-1 against `aidocs/16` EXPORT-* + IMPORT-* row status,
  RO-Crate round-trip tests, and a working PID-minting demo
- WP-2 against M1-wave ship status (TS-IDc, POST /v2/shapes/render,
  Trace3D) and real-user feedback from the MFFD AFP campaign
- WP-3 against a documented Databus + MOSS integration in the
  Shepard codebase (currently: none) and a survey row each for
  twinStash + Defacto + Inst.DLR (currently: partial coverage)

If those checks come back empty, this doc has aged out of
honesty and needs a §6.1 "what slipped" appendix.
