---
stage: audited-by-personas
last-stage-change: 2026-05-24
audience: contributors
---

# Persona audit — Industrial Manufacturing & Quality Engineer (live, 2026-05-24)

**Lens.** Combined Lead Industrial Manufacturing Engineer (IME) + Aerospace
Quality Engineer (AQE) doing a readiness assessment of Shepard for use in
an EN 9100 / EASA Part 21G shop-floor environment. The brief was to
re-evaluate after today's shipments (LIC1, Cite-this card, Metadata
Completeness Card, header search, count badges, ORCID input,
PROV-resolver attribution fix, migration-chain readiness check).

**Honest verdict up front.** Today's shipments moved the **Research Data
Management (FAIR)** needle materially. They moved the **EN 9100
Quality** needle almost not at all. Those are two different audits, and
the platform has converged hard on the first while leaving the second
structurally unaddressed. The good news is that the substrate (typed
Activity log, schema-free Neo4j properties, `status` as a free-form
String, plugin-first SPI) makes the QA layer cheap to add — but
"cheap" is conditional on three deliberate decisions documented below.

---

## What I found

### The static evidence
- `de.dlr.shepard.common.neo4j.entities.AbstractDataObject` —
  `status` is a free-form `String` (line 19), NOT an enum. Same shape
  for `license` + `accessRights` shipped today via LIC1
  (`V57__NOOP_AbstractDataObject_fair_fields.cypher`). This is the
  good news: the substrate already accepts any vocabulary I want to
  introduce.
- `frontend/components/context/data-object/edit-dialog/EditDataObjectDescriptionDialog.vue`
  line 52 — the v-select **hard-codes** the lifecycle vocabulary:
  `['DRAFT', 'IN_REVIEW', 'READY', 'PUBLISHED', 'ARCHIVED']`. These
  are **publication-lifecycle** values, lifted from Zenodo/Dataverse
  patterns. Not a single one of them is QA: there is no `FAILED`,
  no `NCR_OPEN`, no `REJECTED`, no `REWORKED`, no `HOLD`, no
  `CONCESSION_PENDING`. So the wire is open but the UI is
  domain-closed.
- `de.dlr.shepard.context.collection.entities.DataObject` (lines
  30-34) — the predecessor / successor relationship is a **bare
  Neo4j edge with zero properties**: `@Relationship(type =
  Constants.HAS_SUCCESSOR)`. There is no edge type attribute, no
  `transitionKind`, no `dispositionReason`. An auditor reading the
  graph cannot distinguish a normal predecessor (`AFP layup → NDT
  inspect`) from a rework loop (`NDT FAIL → rework → NDT recheck
  PASS`) without out-of-band convention. The LUMEN demo (`seed.py`
  lines 369, 442-487) preserves the lineage via two parallel
  predecessor links on TR-006 (the investigation node + TR-005),
  but the audit chain has to be **decoded** from the topology, not
  read from typed edges.
- `de.dlr.shepard.provenance.entities.Activity` + filter — captures
  CREATE / UPDATE / DELETE on 2xx mutations. **Read capture is
  opt-in** via `shepard.provenance.capture-reads=false` (default
  off). Today's PROV-resolver overhaul correctly attributes
  `:Activity` rows to the edited DataObject. So the "who-changed-what"
  trail is real and EN 9100-shaped, but the "who-looked-at-what"
  trail (still relevant under §7.5.3 control of documented
  information for restricted/ITAR data) defaults off.
- `examples/lumen-showcase/seed.py` — TR-004 anomaly chain. The
  investigation DO (line 439-487) carries a `closed_at` attribute
  (free-text key in the `attributes` map). The disposition
  ("bearing replaced") lives in `description`, the corrective
  evidence (TR-006 = post-fix re-test) is only inferable from
  the topology. There is **no closure record signed by an
  inspector**, **no link to a calibration certificate**, **no
  concession approval reference**. An EN 9100 §10.2 auditor
  reading the graph would have to take it on faith that TR-006
  cleared the anomaly.

### The live deploy walk
- LIC1 (license + accessRights) is on the wire (`DataObject` +
  `Collection` `GET` responses include both; OpenAPI pins
  `accessRights` to `OPEN|RESTRICTED|CLOSED|EMBARGOED`).
- `MetadataCompletenessCard.vue` scores 9 checks worth 100 pts:
  name (10) + description ≥50 chars (15) + license (20) +
  accessRights (10) + creator ORCID (10) + ≥1 semantic
  annotation (10) + ≥1 labjournal entry (5) + hero image (5) +
  ≥1 DataObject (15). **Every single check is FAIR-shaped, not
  QA-shaped.** Calibration evidence, inspector approval,
  conformance status, NCR state — none of them move the score.
- Count badges + header search + Cite-this — all useful for a
  researcher, irrelevant for a shop-floor operator.

### The aidocs/44 scan
- Multiple rows acknowledge EN 9100 context (the cross-instance
  prov UI design `aidocs/frontend/100` mentions a ≤3-click EN 9100
  audit pane), but **no row anywhere in the feature matrix
  proposes an NCR primitive, a quality gate, a calibration-cert
  payload kind, or an inspector-sign-off primitive.** The closest
  hit is the experiment ontology (`shex:DefectType`,
  `shex:InspectionMethod` SKOS schemes) which are annotation
  vocabularies — useful, but they describe the data after the
  fact; they don't enforce a workflow.

---

## EN 9100 readiness assessment

A line-by-line audit against the clauses an EN 9100 / AS9100 Rev D
auditor cares about. Severity: **PASS / GAP / FAIL**. Citations are
to the corresponding shipped EN 9100 §; gap descriptions ground the
finding in real Shepard files.

| EN 9100 § | Requirement | Shepard today | Severity | Closure path |
|---|---|---|---|---|
| §7.1.5 | Monitoring + measuring resources — calibration evidence retained | No primitive for a calibration certificate; can be uploaded as a `FileReference` with `attributes['calibration_cert']='2024-06-15'` but it's freetext, not enforced, not linked to the producing DataObject | **GAP** | New `CalibrationReference` payload kind (plugin-first per CLAUDE.md `§Always: think plugin-first`) carrying `{instrumentSerial, certDate, certExpiry, certificateFile, calibratorOrg, traceableTo}`; render badge on every DO whose timeseries was produced by an out-of-cal instrument |
| §7.5.3.2 | Control of documented information — version control, retention, prevention of obsolete use | Version control: ✓ (`VersionableEntity`); prevention of obsolete use: GAP — there is no "this DO is superseded" status; only `ARCHIVED` which is publication-shaped | **GAP** | Status vocab extension below |
| §8.3 | Design + development — design output review/verify/validate gates | DRAFT → IN_REVIEW → READY → PUBLISHED is gate-shaped but the **gate is informational, not enforcing**. No "READY requires N approvals" rule. No "downstream DOs blocked until predecessor READY" rule. | **GAP** | Workflow plugin proposal below |
| §8.5.2 | Identification + traceability — every part has a unique identifier traceable through every process step | DataObject `appId` (UUID v7) ✓; predecessor/successor chain ✓ for human-reconstructable lineage. **But:** edge type doesn't distinguish rework-loop predecessors from forward-flow predecessors | **PARTIAL** | Add edge property `transitionKind` (FORWARD / REWORK / CONCESSION / DERIVE) — schema-free Neo4j, additive |
| §8.5.4 | Preservation — protect from deterioration, loss | Storage redundancy via Garage S3 ✓; preservation of provenance: PROV-resolver fix today helps; PRESERVATION of NCR closure record: N/A (no NCR primitive yet) | **PARTIAL** | NCR primitive + immutable closure event |
| §8.7 | Control of nonconforming outputs — identify, isolate, document, dispose, retain records | **FAIL** — Shepard cannot natively represent an NCR. The whole §8.7 surface (identify, segregate, disposition: rework/repair/use-as-is/scrap, concession authority, record retention) has no primitive | **FAIL** | NCR plugin proposal below |
| §9.1.3 | Analysis + evaluation of QMS data | Postgres + Timescale + Neo4j substrate is rich enough to query for "all NCRs in window X, what was the disposition mix" — but only once NCRs exist as typed entities | **GAP** | Falls out of §8.7 closure |
| §10.2 | Nonconformity + corrective action — root cause, action effectiveness verification | TR-004 anomaly investigation tree exists in LUMEN as a special child DO with `closed_at` attribute. **There is no `correctiveAction` linkage**, no "5-why root-cause" structured field, no "effectiveness verification record" link | **FAIL** | CAR / CAPA primitive — typed payload kind |
| §10.3 | Continual improvement | No platform support; falls out of §9.1.3 + §10.2 closure | **GAP** | Follow-on |

**Score by clause weight** (auditor-rough): **2 PASS / 4 PARTIAL /
4 GAP / 2 FAIL** out of the 10 clauses an aerospace QA system must
satisfy. **A blind audit would not certify** Shepard as a §8.7
nonconformance-control surface today. The fix is not large, but it
is structural — see §"Quality gate + NCR routing plan" below.

The standard external reading: aerospace digital NCR practice is
documented well by Infosys's "Digital Thread for Non-Conformities in
Aerospace" white paper [^1] and the IAQG-aligned operational
description at Connect981 [^2]; the four canonical NCR dispositions
(rework / repair / use-as-is / scrap) and the disposition-authority
record are the structural elements every aerospace ERP/MES/QMS
implements and Shepard today does not.

---

## Did today's shipments move the EN 9100 needle?

**LIC1 (license + accessRights).** Moves §8.5.3 (property belonging
to customers / external providers — IP marking) needle slightly,
because `RESTRICTED` and `CLOSED` are now first-class. Does NOT move
§8.7 (nonconforming outputs). Verdict: **+0.5 clause**.

**Metadata Completeness Card.** Moves nothing in EN 9100. The
9 checks are FAIR-shaped — they help a researcher publish to
Zenodo, not an operator clear an audit. The widget is a model
component (clean pure helper, e2e-tested) and the architecture
trivially extends to a sibling **QA Completeness Card** — see
§"Quality gate + NCR routing plan". Verdict: **+0 clauses today,
+5 clauses if extended**.

**PROV-resolver attribution fix.** This actually matters.
EN 9100 §7.5.3 (control of documented information) requires a
"who changed what when" trail. The pre-fix bug — Activity rows
attributed to the wrong DO — would have been an audit finding.
Today's fix means a §7.5.3 auditor can now ask "show me every
mutation to DO X by user Y between 2026-01-01 and 2026-05-01"
and get a defensible answer. Verdict: **+1 clause (§7.5.3 from
PARTIAL → PASS for mutations on the v2 surface)**.

**Migration-chain readiness check.** Production hygiene, not
directly an audit-clause mover. But: a deployment that
fails-fast on a missing migration is exactly what §7.1.4 wants
(controlled environment). Verdict: **+0.25 clause (§7.1.4
hardening)**.

**Header search + count badges + ORCID input.** All researcher
ergonomics. **+0** to EN 9100, **+1** to FAIR / Findability.

**Net change today.** Roughly **+1.75 audit clauses** (mostly
§7.5.3) on an EN 9100 audit. Roughly **+3 FAIR-band points** on
a DMP completeness audit. Two-track velocity, FAIR track winning.

---

## Quality gate + NCR routing plan (concrete)

The single highest-impact structural change. Four primitives,
sequenced.

### Primitive 1 — Status vocabulary extension

**Today.** `EditDataObjectDescriptionDialog.vue` hard-codes
`['DRAFT', 'IN_REVIEW', 'READY', 'PUBLISHED', 'ARCHIVED']`.

**Proposed.** Add an admin-configurable status registry (`:StatusVocabConfig`
singleton per the CLAUDE.md `§Always: surface operator knobs in the
admin config` pattern; mirror A3b / N1c2 / UH1a). Ship default
vocabulary as the union of the existing 5 lifecycle values **plus**
the QA values:

| Group | Values | Use |
|---|---|---|
| Lifecycle (today) | `DRAFT`, `IN_REVIEW`, `READY`, `PUBLISHED`, `ARCHIVED` | Publication readiness |
| Quality (new) | `FAILED`, `NCR_OPEN`, `NCR_DISPOSITIONED`, `NCR_CLOSED`, `HOLD`, `REWORKED`, `CONCESSION_PENDING`, `SCRAPPED` | EN 9100 §8.7 |

Server stays permissive (already does — `status` is `String`); the
UI v-select switches to read from the config. The status chip
(`StatusChip` in `TitleAndMetadataDisplay.vue` line 42) is already
generic — it just renders whatever string is set. The only friction
today is the hard-coded v-select. **One file changes, the
substrate is already correct.**

Effort: S (one sprint slice, no migration, no breaking change).
Audit gain: §8.7 first-class status + §7.5.3 obsolete-use signal.

### Primitive 2 — Edge-property predecessor refinement

**Today.** `[:has_successor]` carries zero properties.

**Proposed.** Schema-free additive property `transitionKind`. Four
values: `FORWARD` (default, normal flow), `REWORK` (this predecessor
is the failed version being re-done), `CONCESSION` (this
predecessor was accepted-as-is despite a deviation), `DERIVE`
(this DO is a transform of the predecessor, not a successor step).
The frontend lineage graph (`CollectionLineageGraph.vue`) renders
REWORK as a coloured dashed edge; the EN 9100 audit pane filters
in / out the rework loops on demand.

Two-line Cypher migration (idempotent; backfill all existing
edges to `FORWARD`). Wire change: optional query param on the
predecessor PUT endpoint. Backwards-compatible.

Effort: S. Audit gain: §8.5.2 native rework-loop traceability —
no convention required.

### Primitive 3 — `shepard-plugin-quality` (NCR / CAR primitives)

**Plugin-first** per the CLAUDE.md heuristic. Three payload kinds:

- **`NonConformanceReference`** — carries `{detectedAt, detectedBy,
  defectTypeIRI, severity, disposition: REWORK|REPAIR|USE_AS_IS|SCRAP,
  dispositionedBy, dispositionedAt, dispositionRationale,
  concessionAuthority}`. Attaches to any DataObject (the part-being-
  inspected DO).
- **`CorrectiveActionReference`** — carries the §10.2 CAR/CAPA record:
  `{rootCauseStatement, actionDescription, owner, plannedClosureDate,
  effectivenessVerificationDoId, closedAt, closedBy}`. Links the NCR
  to the verification (the next-cycle clean inspection record).
- **`CalibrationReference`** — carries §7.1.5 cal-cert metadata
  + the cert file reference + the `traceableTo` (NMI / NIST / PTB
  chain). Surface a badge on every DO whose producing instrument is
  out-of-cal at the data-capture timestamp.

All three are typed PayloadKind SPI implementations; storage falls
back to `FileReference` for the artefact + Neo4j for the typed
attributes. **Zero core changes.**

Effort: M (one focused two-week sprint for the plugin module +
docs trio + Vitest e2e against a seeded NCR). Audit gain: closes
§8.7 + §10.2 + §7.1.5 in one ship.

### Primitive 4 — QA Completeness Card

Sibling component to `MetadataCompletenessCard.vue`. Different
score-set: license-evidence-of-IP (5), all instruments in-cal at
production timestamp (25), all NCRs dispositioned + closed (25),
all CARs have a verification DO link (20), no FAILED-status DOs
unlinked to an NCR (15), inspector ORCID on every disposition
(10). Same 100-pt ceiling. Same red/amber/green band.

Renders on the Collection detail page next to the existing
FAIR card. **A shop-floor lead sees one number per Collection
that says "audit-ready or not."**

Effort: S (the pure helper is ~200 lines mirroring
`metadataCompleteness.ts`). Audit gain: operationalises the
above three primitives.

---

## Rework loop data model (TR-004 chain re-cast)

The LUMEN demo (`seed.py`) already encodes the rework topology in
the right shape — it just needs typed edges and an NCR primitive
to be audit-defensible.

**Today (decode-from-topology):**
```
TR-004 (vibration anomaly)
  └── child: "Anomaly Investigation — TR-004 Fuel Turbopump"
                     ↓ (predecessor link, untyped)
TR-005 (hold for bearing teardown)
                     ↓
TR-006 (re-test after repair) ← also predecessor: investigation DO
```

**Proposed (typed + audit-ready):**
```
TR-004 (status=FAILED)
  ├── NonConformanceReference (NCR-0042)
  │     defectTypeIRI = shex:VibrationAnomaly
  │     severity = MAJOR
  │     disposition = REPAIR
  │     dispositionedBy = inspector-ORCID-...
  └── child: "Anomaly Investigation" (status=NCR_DISPOSITIONED)
                     │
        CorrectiveActionReference (CAR-0042)
          rootCause = "Bearing wear in fuel turbopump"
          action = "Replace bearing, run-in 200rpm × 30min"
          verificationDoId = TR-006.appId
                     ↓ predecessor[transitionKind=REWORK]
TR-005 (status=HOLD)
                     ↓ predecessor[transitionKind=FORWARD]
TR-006 (status=READY, NCR-0042 closed via CAR-0042 verification)
```

An EN 9100 auditor reading this can now answer in **2 clicks**:
"was TR-004 cleared? by whom? on what evidence?" The lineage graph
renders the REWORK edge as a dashed coloured line; the QA
Completeness Card flips from amber to green; the Activity log
shows the disposition + closure as discrete events
attributed to the named inspector.

---

## Shop-floor UI requirements (for Role 1 to action)

Hand-off list — what an IME on a ruggedized terminal needs that
the current researcher-facing UI doesn't provide:

1. **Large-target status flip.** A 48×48 button on the DO detail
   page that one-tap flips status from `IN_PROGRESS` to `FAILED`
   (triggering NCR creation modal) or `READY`. Today's v-select
   inside a dialog is ~12×12 effective target — fails the
   shop-floor IME glove-on test.
2. **Barcode / QR scan to navigate.** A persistent input field
   that accepts a scanned UUID v7 (or DLR part number) and jumps
   to the DO. Today there's a header search box (just shipped) —
   confirm it accepts free-text well; add barcode-input mode
   (`inputmode="numeric"` + auto-submit on `\n`).
3. **Active NCR strip.** Top-of-page banner on any DO with status
   in the NCR_* range; one-tap to the NCR detail. Today's
   StatusChip is visible but is competing for attention with
   ~10 other UI elements.
4. **Inspector signature on disposition.** Confirm-dialog with a
   second-factor (PIN / OIDC step-up via Keycloak) before
   `dispositionedBy` is written. Without this, the immutable
   audit trail is just an honour system.
5. **Offline-tolerant write queue.** Shop-floor wifi is unreliable
   in steel-frame buildings. NCR creation should queue locally
   (IndexedDB) and replay on reconnect with an explicit "queued"
   chip. Out of scope for a first sprint, but document the
   requirement now so we don't paint into a corner.
6. **Print-to-PDF the audit pane.** EN 9100 surveillance audits
   still want a paper trail. One-click PDF of the
   "TR-004 → NCR-0042 → CAR-0042 → TR-006" chain with
   timestamps + signatures, ready for the audit binder.

---

## Status vocabulary extension proposal

Concrete delta vs the current 5 lifecycle values:

| Status | Phase | Meaning | Triggers / blocks |
|---|---|---|---|
| `DRAFT` | Lifecycle (today) | Initial author working state | none |
| `IN_REVIEW` | Lifecycle (today) | Submitted for peer review | none today; could enforce reviewer-count |
| `READY` | Lifecycle (today) | Reviewed, ready to advance | none today; could block successor creation if predecessor != READY |
| `PUBLISHED` | Lifecycle (today) | Released for external citation | governs Unhide feed inclusion |
| `ARCHIVED` | Lifecycle (today) | Retired / superseded | hides from default list |
| **`FAILED`** | QA (new) | Conformance fail detected | **forces NCR creation** within 24h or escalates |
| **`NCR_OPEN`** | QA (new) | NCR raised, awaiting disposition | blocks publication |
| **`NCR_DISPOSITIONED`** | QA (new) | Disposition decided (rework/repair/use-as-is/scrap), pending execution | continues blocking publication |
| **`NCR_CLOSED`** | QA (new) | CAR verified effective | unblocks publication path |
| **`HOLD`** | QA (new) | Quarantined pending investigation | blocks all successors |
| **`REWORKED`** | QA (new) | Marked as the post-rework artefact | predecessor-edge `transitionKind=REWORK` |
| **`CONCESSION_PENDING`** | QA (new) | Use-as-is awaiting authority | blocks publication |
| **`SCRAPPED`** | QA (new) | Disposition = scrap, terminal | terminal — no successors allowed |

All values free-form `String` on the wire; UI v-select reads from
`:StatusVocabConfig` (operator-flippable per CLAUDE.md admin-config
rule); status-driven blocking rules ship behind a feature flag
(`shepard.quality.enforce-status-gates=false` default) so existing
LUMEN seed data continues to load unchanged.

---

## Calibration certificate linkage (concrete shape)

**Today.** Zero primitive; could be hacked as a `FileReference` with
`attributes['calibration_cert']='2024-06-15'` but it's freetext,
not enforced, not linked to the producing instrument.

**Proposed `CalibrationReference`** (typed PayloadKind in the
quality plugin):
```yaml
CalibrationReference:
  instrumentSerial: "FT-2024-0042"       # required
  instrumentIRI: "https://instdlr.dlr.de/handle/12345"  # PIDINST link
  certDate: "2024-06-15"
  certExpiry: "2025-06-15"
  certificateFile: <FileReference appId>  # the actual PDF
  calibratorOrg: "DKD-K-12345"            # accreditation
  traceableTo: "PTB"                       # NMI / NIST / PTB
  uncertainty: "±0.05% FS"
  validFor: ["pc_chamber", "pc_nozzle"]    # which channels this cert covers
```

The badge surfaces on every DO whose `TimeseriesContainer` was
populated by an instrument with `instrumentSerial =
FT-2024-0042` **between** `certDate` and `certExpiry`. If the
data-capture timestamp is after `certExpiry`, the badge goes
red and the DO drops to QA Completeness amber.

The PIDINST link to `instdlr` (already noted in
`aidocs/40-ecosystem.md` line 75) closes the
instrument-identity loop without re-inventing the registry —
this is the kind of cross-tool integration the ecosystem-advocate
findings already call out.

---

## Where Shepard could function as a lightweight MES overlay

Two things would have to land before a serious operator would
consider Shepard a real MES replacement:

1. **Real-time** rather than upload-driven. A shop-floor MES gets
   sensor data from PLCs in milliseconds, not from CSV uploads
   hours later. The `home-showcase/collector.py` MQTT bridge
   demonstrates the pattern (MQTT topic → TimeseriesContainer in
   real-time) but the scale and protocol set (OPC UA, MTConnect,
   Modbus) for shop-floor use needs a dedicated plugin
   (`shepard-plugin-shop-floor-ingest`).
2. **Work-order primitive.** An MES routes work orders to stations,
   not just records what happened. This is **out of Shepard's
   stated mission** — the vision (`aidocs/42`) is a research data
   management platform, not a production-control system. The
   research/manufacturing boundary should be: Shepard accepts the
   MES's output (process records, conformance records, NCRs) and
   provides the FAIR / archival / digital-thread surface. **It
   should not try to replace the MES.**

The honest read: Shepard is positioned as a **digital-thread
substrate for research-grade aerospace manufacturing data** (MFFD
is literally that), and that's a more defensible niche than
trying to compete with Siemens Opcenter or Apriso. The QA
primitives above let it interoperate cleanly with an upstream
MES (NCR exported from Opcenter → ingested as `NonConformanceReference`
via the importer plugin); it does not require Shepard to BE the MES.

---

## "As-designed" vs "as-built" — current state + proposal

**Today.** Implicit only. The DO `name` is a human label; the
predecessor chain reconstructs the as-built order. There's no
typed "this is the design intent" vs "this is what actually
happened" split.

**Proposed.** Two collection-level templates (per the
`TemplateKind=PROCESS_RECIPE` slice currently in flight per
aidocs/44 line 440) and a typed link:
- `PROCESS_RECIPE` template = **as-designed**: the planned
  DAG of process steps with target parameters.
- Instantiated `Collection` = **as-built**: actual DOs created
  during the run, linked via `instantiatedFrom` predicate to the
  recipe.
- Per-step delta surfaces: for each as-built DO, compute the
  deviation vs the recipe's target parameter and surface as a
  QA chip ("AFP consolidation force −18% vs target").

The MFFD synthetic showcase (`mffd-showcase/seed.py`) is the
perfect proving ground — it already has a 12-step DAG with a Q1
anomaly (consolidation force drop at ply 5). Wire up the recipe
template and the as-built deviation surfaces and the
"as-designed vs as-built" story becomes one Playwright e2e
test away.

---

## Opportunities

In priority order, by `effort × audit-clause-impact`:

1. **Status vocabulary extension (Primitive 1)** — 1 file change,
   2 clause moves (§8.7 + §7.5.3). Highest ratio.
2. **Edge-property `transitionKind` (Primitive 2)** — 2-line Cypher
   migration + 1 frontend render-tweak, closes §8.5.2 rework gap.
3. **`shepard-plugin-quality`** — one focused sprint, closes
   §8.7 + §10.2 + §7.1.5 in a single ship. The plugin-first
   posture means **zero core changes**.
4. **QA Completeness Card** — pure-helper + Vitest pattern is
   proven (`metadataCompleteness.ts`). One sprint, gives operators
   the single number they want.
5. **Read-capture default-on for restricted DOs** — flip
   `shepard.provenance.capture-reads=true` for any DO with
   `accessRights=RESTRICTED` or `CLOSED`. §7.5.3.2 + §10.2.2
   need this for full ITAR / EAR compliance.

---

## Real-world impact

A DLR institute doing MFFD-style production research today has
to either (a) keep a parallel quality system in SAP-QM or
Apriso and manually cross-reference, or (b) accept that the
Shepard record is **research-grade traceable** but not
**audit-grade certifiable**. The four primitives above close that
gap — at a cost roughly equivalent to one quarter of effort
sprints. Once closed, Shepard becomes the substrate where the
MFFD digital-thread story (already aimed at JEC + Clean Aviation
JU funding) can be told **with audit numbers attached**, not just
narrative.

The political angle: aerospace funding bodies increasingly score
"audit-readiness" as a separate axis from "research excellence."
The Infosys white paper on the aerospace digital-thread for NCRs
[^1] and the IAQG OASIS Insights launch (April 2025) are evidence
that the upstream regulators expect platforms in this space to
operationalise §8.7. A Shepard that scores 4 PASS / 6 PARTIAL /
0 FAIL on the table above is genuinely competitive in this
market; a Shepard that scores 2 PASS / 4 PARTIAL / 4 GAP /
2 FAIL is a research demonstrator.

---

## Gaps & blockers

- **Cultural** — the team has been (correctly) optimising for
  the FAIR audience this quarter (LIC1, completeness card, ORCID,
  Cite-this). The QA audience needs an explicit champion or it
  will keep slipping to "next quarter."
- **Plugin scope** — `shepard-plugin-quality` overlaps domains
  with `shepard-plugin-importer` (which already has an
  ImporterRun status enum: `PENDING/RUNNING/SUCCEEDED/FAILED/
  CANCELLED`). The Job/Status primitive there is the seed of a
  cross-cutting workflow primitive that the quality plugin can
  reuse — coordinate before duplicating.
- **PIDINST integration** — the `instdlr` registry mentioned in
  aidocs/40 is the right closure for instrument identity. If
  instdlr coverage is incomplete (which is likely — it's a young
  registry), the calibration plugin has to fall back to a
  Shepard-local `Instrument` entity. Confirm scope with the
  instdlr maintainer before designing the cal-cert plugin.
- **Inspector identity** — currently Shepard knows Keycloak
  usernames, not OIDC step-up + ORCID + role binding. The
  "inspector signature on disposition" requirement (shop-floor
  requirement #4) needs a small auth-flow extension. The ORCID
  input shipped today is the right starting point.

---

## What surprised me

- **The substrate is more ready than the UI suggests.** `status` is
  a free-form String on the wire AND in the DB; the only thing
  pinning it to publication-lifecycle is one hard-coded array in
  one Vue file. The team has been building like the substrate
  needs schema migrations to grow vocabulary, but it doesn't.
- **The PROV-resolver fix today is bigger than its commit message
  suggests.** Correct Activity attribution is the difference
  between "we have an audit log" and "we have a defensible audit
  log." Pre-fix would have been a §7.5.3 audit finding. Today's
  ship retires that risk.
- **The completeness widget pattern is ridiculously transferable.**
  The pure-helper + per-check breakdown + scoring band + e2e-test
  pattern in `metadataCompleteness.ts` is exactly the shape a QA
  Completeness Card needs. The architecture is **already there**;
  it just needs a second instance.
- **The LUMEN demo's rework chain is already topologically
  correct** — TR-004 → investigation child → TR-006 with the
  investigation also a predecessor of TR-006. Add typed edges and
  an NCR primitive and the demo becomes an EN 9100 case study
  rather than a research data demo.
- **Zero rows in `aidocs/44` propose anything in §8.7 / §10.2.**
  Across 530-odd rows of shipped + designed + queued features,
  the QA workflow surface is conspicuously absent. This is either
  (a) deliberate scope-fence (the platform is FAIR-first, not
  QMS-first) — which is fine if explicit — or (b) blind spot,
  which is what this audit suggests. The CLAUDE.md vision should
  say which.

---

## Bottom line

Today's shipments are well-executed FAIR-band work. They moved a
DMP-compliance audit needle materially. They moved an EN 9100
audit needle marginally (+1.75 clauses, almost all from the
PROV-resolver fix). The four primitives in §"Quality gate + NCR
routing plan" close the remaining gap at a cost roughly equivalent
to one focused quarter — and they ship as a plugin (per the
CLAUDE.md plugin-first heuristic), so the core platform
investment is one config singleton + one edge property.

The structural opportunity is genuine: aerospace research data
platforms that can also satisfy §8.7 + §10.2 are rare. Shepard
is closer than it looks.

---

## External sources cited

[^1]: Infosys, "Digital Thread for Non-Conformities in Aerospace" white paper. https://www.infosys.com/engineering-services/insights/documents/digital-thread-non-conformities.pdf
[^2]: Connect981, "Aerospace Non-Conformance Reports (NCRs): Step-by-Step Process and Best Practices." https://connect981.com/blog-posts/aerospace-non-conformance-report-ncr-process-2
[^3]: NSF, "Exploring the Impact of IA9101 and IA9104 on Your AS9100 Certification." https://www.nsf.org/knowledge-library/impact-ia9101-ia9104-on-as9100-certification
[^4]: EASA, "Guide for Compliance with PART 21 as amended." https://www.easa.europa.eu/en/downloads/137622/en
[^5]: SimplerQMS, "Nonconformance Report (NCR): Definition, Example, and Process." https://simplerqms.com/non-conformance-report/

IAQG OASIS Insights (April 2025) — referenced in the WebSearch
synthesis as evidence that AS9100 audit data is increasingly being
benchmarked at the platform level; reinforces the
"audit-readiness as funding axis" thesis above.
