---
title: "Is the AI-collaborative `noheton/shepard` fork production-ready?"
subtitle: "Multi-lens role synthesis — 10 personas, three deployment framings"
stage: feature-defined
last-stage-change: 2026-05-23
audience: [maintainer, thesis-committee, prospective-adopter, funding-reviewer]
---

# Is the AI-collaborative `noheton/shepard` fork production-ready?

*A multi-lens synthesis, written 2026-05-23, that mirrors back what the
ten role-personas already in `aidocs/agent-findings/` actually say about
this fork — and what the AI-collaborative method that produces it
changes about the question.*

## §1 — Question framing

"Production-ready" is not one question. For this fork it is at least
three:

- Is the wire surface stable enough that an operator who runs
  `docker compose up` against `main` does not lose data, lose access,
  or break their existing clients?
- Is the development process disciplined enough that bugs that ship
  are caught before they cost operator time at 2 AM?
- Is the AI-collaborative method that produces the code itself a
  *production* method, or is it a research method that happens to be
  emitting production-shaped artefacts?

The third question is the thesis-relevant one. The fork's own
methodology chapter [aidocs/strategy/89] takes the position that
"transparent AI-assisted production is not a weakness of the thesis but
a methodological contribution — provided the practice is reproducible,
the failure modes are named, and the author's posture is publicly
stated independently of the convenience of the moment." This report
applies that standard *to the fork itself*, not just to the thesis: do
the safeguards (provenance, persona audits, security gates, test
floors, doc-currency rules) honestly reproduce, name failure modes, and
state posture? Or do they perform the appearance of discipline while
production-grade failure modes leak past them?

The answer is "mostly yes, with three named failure-mode classes." The
sections that follow are the evidence.

## §2 — Per-persona verdicts

Ten role-personas, one paragraph each. Each paragraph names (a) the
most damning finding the persona has already filed against the fork,
(b) the most production-relevant strength, and (c) a verdict.

**1. Core Tech & UX Auditor** (`ux-auditor.md`). Damning: header
search hits collections only — "TR-004" returns nothing unless the
user already knows the collection (`HeaderBar.vue:39-71`).
`CollectionLineageGraph.vue:16` calls `useFetchAllDataObjects` with
no cap; observable lag begins around 200 nodes — MFFD production
scale will exceed that. Strength: personal-digest landing, channel
quality scoring, RFC 7396 merge-patch shape, inline timeseries
charting. Verdict: **READY-WITH-RESERVATIONS** — needs virtualization
pass before MFFD live ingest at full scale.

**2. Data & Process Ontologist** (`data-ontologist.md`,
`persona-review-ontologist.md`). Damning: attributes-vs-annotations
duality — `AbstractDataObject.attributes` is open-world `Map<String,String>`;
`BasicEntity.annotations` is IRI-pair only — used in parallel in the
LUMEN seed with no controlled-vocabulary enforcement. The shipped
SHACL catalogue (1596 lines, 8 files) contains three PROV-O type
errors, lost QUDT units, three incompatible namespace decisions.
Strength: BFO → IAO/IOF → shepard-upper → mffd upper-ontology alignment
is genuinely correct; "shapes as templates, views, and agent contracts"
in `aidocs/semantics/95` is ahead of any peer RDM platform. Verdict:
**READY-WITH-RESERVATIONS** — type errors are one SHACL-cleanup PR.

**3. API Scrutinizer** (`api-scrutinizer.md`,
`persona-review-api-scrutinizer.md`). Damning: `DataObjectIO.referenceIds`
contains IDs of `BasicReference` nodes (TimeseriesReference,
FileReference), not DataObjects — "Any caller treating them as
`dataObjectId` values gets 404s. This happened in production with
the MCP server." 60+ REST files leak raw Neo4j OGM long IDs into
`/v2/` responses. 5-tuple channel addressing partially dragged into
v2. Strength: consistent auth, RFC 7396 applied uniformly, readable
package layout by primitive. Verdict: **NOT-READY** for external API
consumers — legacy-ID leakage is contract-breaking.

**4. Industrial Manufacturing & Quality Engineer (IME/AQE)**
(`manufacturing-quality.md`, `persona-review-ime-aqe.md`). Damning:
`DataObject.status` is unvalidated free-text with IO-layer enum hint
`DRAFT | IN_REVIEW | READY | PUBLISHED | ARCHIVED` — no `FAILED`,
`NCR_OPEN`, `REJECTED`. No NCR subtype. The rework loop is
representable structurally via Predecessor/Successor but the
relationship carries no metadata distinguishing "rework transition"
from "normal successor." For DIN EN 9100: "you cannot tell, from
the data model alone, whether TR-006 was cleared." Strength:
`ProvenanceCaptureFilter` (PROV1a) immutable mutation log; SHACL
`NCRShape` exists in `mini-shapes.ttl` (the gates are designed).
Verdict: **NOT-READY** for DIN EN 9100 certified production; gates
designed but not enforced.

**5. Research Data Manager (RDM, FAIR)** (`research-data-manager.md`,
`persona-review-rdm.md`). Damning: `Collection.java` has no `license`,
`embargo`/`accessRights`, `funder`/`grantId`; no canonical citable
DOI path bound to the export shape — three FAIR-fatal gaps (R1.1,
A1.2, no DOI binding) blocking DFG/EU/Clean Aviation acceptance.
ORCID exists on `User.java` but is not denormalised onto Collection
or DataObject. Strength: Helmholtz Unhide plugin (UH1a) shipped —
only DLR-origin platform proactively publishing to HKG without
operator intervention; snapshot-pinned RO-Crate export ships ahead
of the RO-Crate 1.2 public rollout. Ahead of Kadi4Mat / SciCat /
Coscine on publication automation, behind on basic license fields.
Verdict: **READY-WITH-RESERVATIONS** for internal DLR; **NOT-READY**
for DFG-funded publication until the three-field gap closes.

**6. Strategy Aligner & Executive Advisor** (`strategy-advisor.md`,
`persona-strategy-aligner-gh-pm-2026-05-23.md`). Damning: vision is
"backend-rich and UI-thin in several important areas." "Snap
dashboards" called the killer feature ships zero code; HDF5 has
backend but no UI; SPARQL proxy is shipped backend with no frontend
query interface. Strength: 14 pre-seeded ontologies, DataCite minting,
RO-Crate, 11 plugins extracted, "first shepard install anywhere to
publish to the Helmholtz Knowledge Graph." Verdict:
**READY-WITH-RESERVATIONS** for a DLR-internal pilot positioned
honestly; **NOT-READY** for a funder-facing pitch that doesn't
discount the UI-thin areas.

**7. Industrial Ecosystem Advocate** (`ecosystem-advocate.md`).
Damning: "Adoption (public): Unknown — 2 showcase datasets in repo;
used within DLR ZLP." Kadi4Mat: 100+ public records, active
NFDI4ING community. Coscine: 1500+ users, 138 institutions.
Strength: niche wins are clear — timeseries as first-class citizen,
PROV-O + metadata4ing dual typing, native Helmholtz Unhide, plugin
SPI lets external institutes extend without forking. Verdict:
**READY-WITH-RESERVATIONS** for the niche; ecosystem maturity not
yet at peer level.

**8. Analytics & AI Opportunities Specialist** (`analytics-ai.md`,
`persona-review-ai-opportunities.md`). Damning: shipped AI is AI1b
rolling-median MAD anomaly detection, pure Java, no LLM —
"extremely narrow. A reviewer reading the AI section without
tracking 📐 symbols would massively overestimate." AI1d–AI1l all
queued. Strength: shapes-as-agent-contracts is "the cleanest
substrate for AI in scientific RDM that I've seen at this stage of
design"; F(AI)²R provenance vocabulary ties every AI interaction
to a typed PROV-O activity — substrate the EU AI Act Article 50
disclosure deadline (August 2026) will demand. Verdict:
**READY-WITH-RESERVATIONS** for AI provenance capture; **NOT-READY**
for AI features as user-facing value.

**9. Reluctant Senior Researcher** (`persona-review-reluctant-senior.md`).
Damning: "the form has no 'upload my existing folder' button. It
assumes I'm creating something new. I'm not. I have 40 TB." Shape
keys collide with literal column names (`run_number=22192` doesn't
match `^[0-9]{6}$`); tab grouping is shape-author's opinion; coining
new vocab requires power-user role the senior doesn't have on day
one. Strength: CLAUDE.md operator-discipline rules — every config
flip lives at runtime, not in an XML restart. Verdict: **NOT-READY**
for the 40-TB-on-NFS migration scenario; **READY-WITH-RESERVATIONS**
for new-data workflows.

**10. Digital Native Researcher** (`persona-review-digital-native.md`).
Damning: typed Kiota v2 SDK directory has `pyproject.toml` + README,
empty `shepard_v2/` package — `find` returns zero `.py` files. 5-line
test ("load AFP Layup Q1 Shell + predecessor chain + timeseries
channels into pandas in ≤5 lines") fails today — realistic minimum
is ~12 lines and still doesn't fetch channel rows. 5-tuple channel
addressing forces hand-threading. Strength: MCP surface shipped
(shepard-plugin-mcp, task #30), OIDC-authenticated, queryable from
Claude. Verdict: **READY-WITH-RESERVATIONS** for MCP workflows;
**NOT-READY** for typed-SDK workflows until Kiota generation lands.

## §3 — What the AI-collaborative method changes

The fork's own methodology chapter [aidocs/strategy/89] is honest about
six commitments: transparent disclosure, DLR-compliant cloud (SAIA/GWDG),
substrate-level reproducibility via F(AI)²R provenance, human-oversight
deferral patterns, dataset-forging with snapshot boundaries, and a
named *Decide → Research → Convert → Write/Iterate → Export* workflow.
The reflexivity audit there finds alignment between stated and observed
practice. This section asks what that method *changes* about
production-readiness — specifically, which production failure modes it
*creates* and which it *catches*.

**Failure modes the method creates.** The MFFD live-ingest arc that
ran 2026-05-23 makes the AI-generated-code failure-mode class concrete.
Three stacked bugs surfaced in one day:

- **BUG-E** (`MFFD-IMPORT-BUG-E`): the importer was sending array-rooted
  JSON payloads to `StructuredDataService.java:53`, which calls
  `Document.parse(payload.getPayload())`; MongoDB's BSON model rejects
  array-rooted documents with a structurally-specific exception. The
  v15.9–v15.11 importer hit this on every `StepMetaProcessExecution`
  SD upload (100% failure rate on that payload kind). Diagnosed via
  backend log grep → exact exception type → traced to the
  `Document.parse()` call. A human reviewer with MongoDB experience
  would have caught the array-root assumption at design time.

- **BUG-F** (`MFFD-IMPORT-BUG-F`): the importer was creating a fresh
  SD container per Execution instead of reusing one container per
  process-step. The dest landed with **4193** structuredDataContainers
  created in one day vs **2** expected. The fix is a per-step container
  cache; the bug is a model-vs-storage confusion that a human reviewer
  with prior Shepard data-model experience would have flagged on first
  read of the upload loop.

- **BUG-G** (`MFFD-IMPORT-BUG-G`, folded into v15.14): payload-link
  ordering required oid plumbing that the AI-generated code threaded
  out-of-order. The fix is straightforward once the call graph is
  understood, but the AI did not understand the call graph well enough
  on first authorship.

Three bugs in one day, all in the same script, all in a critical path,
all on the AI-generated side. The pattern is consistent with what the
analytics-ai persona finding calls "code that *looks* correct but
encodes an assumption the data doesn't honor" — characteristic of
AI-generated code in domains where the LLM has surface-level pattern
familiarity but lacks substrate-specific intuition (BSON's no-array-root
rule, model-vs-storage cardinality).

**Failure modes the method catches.** The bugs surfaced *because* the
fork ships disciplined safeguards that an undisciplined AI-collaborative
process would not have:

- **MFFD-IMPORT-DIAG (v15.11)** shipped 36 new pytest cases and a
  `DiagSink` emitting one JSON line per event with credential masking,
  PIPE_BUF atomic writes, and 9 auto-classified error hints. The
  diagnostic instrumentation is what made BUG-E diagnosable from logs
  alone — *the substrate-level reproducibility commitment from §89
  literally produced the bug-fix capability.*

- **MFFD-IMPORT-COMPLETENESS-CHECK (v15.13)** ships a pre-flight
  totals banner + contained completeness pass + uploaded
  `MIGRATION-COMPLETENESS-<session>` DataObject. The verdict struct
  recognises shapes like `BUG-E-SHAPE-SD-100PCT-LOSS` — *the bug class
  is encoded as a named verdict flag the next operator will see
  immediately.*

- **Persona-audit findings before merge.** Every major design slice has
  10 persona findings filed against it in `aidocs/agent-findings/`. The
  pattern is durable: when a 2026-05-23 audit runs against the SHACL
  trio and finds three docs missing from disk, the absence is **named
  in five separate persona findings** (api-scrutinizer, rdm, ime-aqe,
  ontologist, digital-native) — the missing-docs failure mode is
  caught by the audit process, not by waiting for an operator to
  discover it.

- **Continuous doc maintenance rules** in CLAUDE.md ("Always:" rules
  for `aidocs/42`, `aidocs/44`, `aidocs/34`, doc-stage tags,
  bibliography maintenance). The same-PR requirement is what keeps
  the surfaces from drifting; an undisciplined AI process would let
  any one of these decay within weeks.

- **Six security gates** wired into CI (SpotBugs+findsecbugs, CodeQL,
  OWASP Dependency-Check, Trivy, gitleaks, dependency-review). The
  60%-line / 60%-branch JaCoCo floor with `-Djacoco.haltOnFailure=true`
  in CI catches AI-generated code that ships uncovered new methods.

The honest summary: the method *creates* a code-correctness failure
mode that human review would catch, *and* compensates with a
process-level safety net (diagnostic instrumentation, completeness
checks, persona audits, security gates, doc-currency rules) dense
enough that the net catches what the individual code does not.
Whether the net is tight enough for production is the §6 question.

## §4 — Comparison to upstream DLR `gitlab.com/dlr-shepard/shepard` 5.2.0

The fork's catalogue of changes lives in `aidocs/34-upstream-upgrade-path.md`
(199 table rows as of 2026-05-23). The standing rule is byte-stability
of `/shepard/api/...` against upstream 5.2.0; all new endpoints land
under `/v2/`. The legacy v1 surface stays available conditional on the
default-active `shepard-plugin-v1-compat` and the
`:LegacyV1Config.enabled` singleton — operators flip the singleton
when their tooling has migrated. Upstream divergence:

- **Additive**: 14 pre-seeded ontologies (V49 + OntologySeedService),
  PROV-O + metadata4ing dual typing, DataCite minting (KIP1g) and KIP
  publication (KIP1h), Helmholtz Unhide plugin (UH1a), snapshot-pinned
  RO-Crate export, RFC 7396 merge-patch on mutation endpoints, Plugin
  SPI (PM1a) with 11 plugins extracted, instance-admin role chain (A0,
  A1, A3a–c), feature-toggle registry (A3b), `:SemanticConfig` admin
  endpoint (N1c2), TimescaleDB-backed timeseries with inline charting,
  MAD anomaly detection (AI1b), shepard-plugin-mcp (task #30),
  shepard-plugin-minter-local (KIP1h).

- **Conditional**: the `/shepard/api/...` byte-stability promise is
  *conditional* on plugin presence and config singleton state from the
  V1COMPAT.0 row onward. An operator who removes the plugin or flips
  the singleton enters HTTP 410 territory by design — but this is an
  operator gesture, not a fork-imposed sunset.

- **Migration**: `aidocs/34` claims "upgrading is essentially
  `pull-and-restart`. No data-mutating migration has shipped yet."
  The Cypher migration directory has ~65 migrations; all are
  documented as idempotent + fail-fast. Migration tests are tracked as
  deferred but referenced [CLAUDE.md "Always: maintain the upstream
  upgrade path" rule 4].

- **Renamed config**: `shepard.spatial-data.*` → `shepard.infrastructure.spatial.*`
  (A3c). The old keys still work but the alias is documented for
  removal in a future release.

**The honest divergence risk**: the `/v2/` shelf is growing rapidly
(60+ REST files), the persona-audited bugs found in §3 are on the v2
side (referenceIds bug, legacy-ID leakage), and the SHACL trio of
design docs is *not on disk* as of 2026-05-23 in five separate
persona audits. The wire surface is stable in principle but the
*development surface* (`/v2/`) ships bugs that an upstream-equivalent
release process with human code review would have caught.

## §5 — Risk catalogue

Ten production risks specific to this fork, ordered by severity:

1. **AI-generated bugs in critical paths.** Trigger: AI authors code
   in a substrate it has surface-level rather than deep familiarity
   with (MongoDB BSON, Neo4j OGM, MFFD cardinality). Blast: silent
   data corruption (BUG-F's 4193 orphans), 100% payload-kind failure
   (BUG-E). Mitigation: diagnostic v15.11 + completeness v15.13 +
   persona audits — partial, post-hoc. Next: human-code-review gate
   for critical-path PRs (Neo4j OGM, Mongo write side, security
   perimeter).

2. **Single-maintainer bus factor.** Trigger: Krebs unavailable.
   Blast: the "Always:" discipline depends on a maintainer who
   enforces it. Mitigation: CLAUDE.md is detailed enough that a
   second contributor *could* enforce it; practice is currently
   solo. Next: co-maintainer at DLR Augsburg + transfer session.

3. **Plugin SPI maturity vs production demand.** Trigger: operator
   needs a payload kind the SPI doesn't yet support (visualisation,
   importer, ai, table, ImageBundle, video, AAS). Blast: feature-gate
   failure pushing operator off-platform. Mitigation: 11 plugins
   extracted; SPI seam real. Next: ship one reference plugin with
   full three-audience docs as canonical template.

4. **Operator-discipline drift.** Trigger: AI sessions end without
   doc-currency rules honoured. Blast: live documents stop being
   live; nine-surface trace returns empty. Mitigation: rules explicit
   + visibly enforced; CI `--check` gate (DOC-STAGE2) not yet in CI.
   Next: ship the CI doc-stage check before v6.0.0 stable.

5. **Upstream divergence drift on `/v2/`.** Trigger: the bug class
   found in §3 (legacy-ID leakage, referenceIds confusion) repeats.
   Blast: operators upgrading from upstream 5.2.0 to `/v2/` write
   code that breaks on the next minor. Mitigation: findings filed,
   fix path named, nothing shipped. Next: one PR removing legacy
   long-ID fields from `/v2/` IO classes.

6. **License + access-control completeness.** Trigger: DFG- or
   EU-funded researcher publishes via Unhide. Blast: publication
   fails or ships without license (R1.1). Mitigation: KIP1e queued
   but not shipped. Next: ship Collection.license + accessRights +
   embargo in one PR (additive Cypher).

7. **Storage volume risk (SM1 not implemented).** Trigger: 6 months
   of running accumulates orphans (today already 4193 orphan SD
   containers from BUG-F). Blast: Mongo / Garage / Neo4j disk fills
   with no operator signal. Mitigation: SM1 designed; SD-WIPE-AND-REGET
   row planned for today's specific orphan set. Next: SM1 phase 1
   (orphan-count metric in OBS-MFFD1 + admin-visible orphan list).

8. **Identity + auth surfaces.** Trigger: OIDC change, Keycloak
   realm misconfig, JWT rotation, federation. Blast: lockout, stuck
   grants, audit gaps. Mitigation: A0/A1 shipped + ProvenanceCaptureFilter;
   MFFD-IMPORT-AUTH1 v15.9 auto-mint shows path is working but
   operator-fragile. Next: federation auth-design as blocker for F1.

9. **Federation maturity (designed not shipped).** Trigger: read/write
   across two Shepard instances (PROV-USER-MIRROR-ENDPOINT gap
   today). Blast: cross-instance prov chains cannot carry full
   attribution. Mitigation: designed in `aidocs/strategy/94`. Next:
   pin federation to v7.0; don't promise before then.

10. **Data-loss redrive recurrence.** Trigger: future payload kind
    ships an upload loop with the same model-vs-storage confusion as
    BUG-F. Blast: another N-thousand orphan accumulation. Mitigation:
    plugin-first heuristic + completeness-check verdict struct
    extensible. Next: generic `payload-kind-cardinality-invariant`
    SHACL shape that fails fast before orphans accumulate.

## §6 — Net verdict for production deployment

Three framings, three verdicts.

**A. DLR-internal pilot at ZLP Augsburg (the MFFD live-ingest use
case the fork was built for).** Verdict: **READY-WITH-RESERVATIONS,
green-lit.** The reservations are: (1) the §3 BUG-E/F/G pattern is
likely to recur on the next critical-path substrate, so the
operator should expect to be the human-review gate; (2) the
60%-coverage floor is honest but the new-code 70% target gates
need active enforcement; (3) Collection.license should ship before
any Unhide publish runs. The ZLP pilot is exactly the deployment
the fork was designed for — disciplined operator, internal data,
known scale, tolerant of "fix-forward" cadence — and the discipline
is real enough that the risks above are managed rather than
hidden.

**B. DLR-cluster federation (DaMaST, HMC Phase 2, multi-institute).**
Verdict: **NOT-READY**. Two structural reasons: (1) federation is
designed but not shipped (`aidocs/strategy/94`), and the
PROV-USER-MIRROR-ENDPOINT gap surfaced today shows what happens
when cross-instance prov-chains hit a missing endpoint — graceful
degradation warnings, not a working chain; (2) the v2 referenceIds
bug found by the API Scrutinizer would compound across instances —
a federated client wired against `referenceIds` would 404 on every
DataObject lookup. The fork needs a 12-month maturation slice
before federation becomes a credible production claim.

**C. External adoption by a third-party institute (a hypothetical
PI at another university).** Verdict: **NOT-READY**. Three blockers
in order: (1) no externally-adopted installation exists; the
ecosystem-advocate finding "Adoption (public): Unknown — 2 showcase
datasets in repo; used within DLR ZLP" is the operative fact; (2) the
typed Kiota v2 SDK is empty — the digital-native persona's 5-line
test fails today; (3) the single-maintainer bus factor compounds
the first two — a PI considering adoption needs to see at least
one other institution that has done it and at least one other
maintainer to call when something breaks. The honest pitch to a
third-party PI today is "this is a credible reference architecture
for aerospace research data, with a working pilot at DLR ZLP — fork
it and see, but expect to do the maintenance work yourself for the
first year." That is not "production-ready," it is "credible
research platform."

## §7 — Recommended pre-production actions

Ordered for impact-per-effort; each maps to an `aidocs/16` row
(existing where shown, proposed where new):

1. **Ship Collection.license + accessRights + embargo (KIP1e)** —
   S, blocks FAIR R1.1 and Unhide DataCite publishes.
2. **Clean legacy-ID leakage from `/v2/` responses** (`aidocs/16`
   proposed row `V2-CLEAN-LEGACY-IDS`) — M, blocks every typed-SDK
   adoption; the API Scrutinizer findings are the spec.
3. **Generate the Kiota v2 Python SDK** — S, blocks the
   digital-native 5-line test and any Python notebook workflow.
4. **Add a human-code-review gate for critical-path substrates**
   (CLAUDE.md proposed addition; not an aidocs/16 row but a process
   change) — XS effort, catches the BUG-E/F/G class.
5. **Ship the doc-stage CI check (DOC-STAGE2 in `aidocs/16`)** — S,
   prevents doc-currency drift.
6. **Add Collection.heroImage + UI for HDF5/SPARQL/labjournal-history
   (the strategy-advisor UI-thin areas)** — M each, raise the
   browser-visible feature parity with the vision doc claims.
7. **SM1 phase 1 — orphan-count metric in OBS-MFFD1 chart +
   admin-visible orphan list** — M, prevents the storage-volume
   risk recurrence.
8. **Identify a co-maintainer at DLR Augsburg + transfer-of-discipline
   session** — XS calendar effort, single largest bus-factor
   mitigation.
9. **Ship one reference plugin (importer or table) with full
   three-audience docs as the canonical template** — L, accelerates
   external plugin authoring.
10. **Federation design `aidocs/strategy/94` to in-flight (F1
    milestone pinned to v7.0, not v6.x)** — XS, removes the
    federation-overpromise risk.

## §8 — Reflexive note

This report is itself AI-generated. It is a synthesis of ten
persona findings each of which is also AI-generated, against a
codebase substantially authored by AI-assisted commits. The
methodological honesty required by `aidocs/strategy/89` applies:

- **AI-synthesized claims** in §2 are direct paraphrases of persona
  findings already in `aidocs/agent-findings/`. The synthesis
  itself is AI work; the underlying findings are AI work; neither
  has been independently human-verified for this report.
- **The §3 BUG-E/F/G evidence** is verifiable against `aidocs/16`
  rows (cited inline) and against today's commit log
  (`e59cd82f feat(mffd-import): v15.14 — BUG-F (container reuse) +
  BUG-G (link ordering + oid plumbing)`) — those facts are
  primary-source-verified.
- **The §5 risk catalogue and §6 verdicts** are AI judgements
  grounded in the persona findings, the methodology chapter, and
  the today's-bug evidence. They are the kind of judgement a thesis
  committee should specifically interrogate; a human review pass
  by a domain expert (not the maintainer) would strengthen them.
- **The §7 recommended actions** are AI synthesis of pre-existing
  `aidocs/16` rows + the persona findings; a maintainer review
  before commitment is warranted.

The reflexive question the methodology chapter requires of itself
applies here too: *if this report's verdict were uncomfortable for
the maintainer, would the AI-collaborative method honour the
discomfort or smooth it away?* The verdict above is **honestly
mixed** — the fork is production-ready for the pilot it was built
for, not production-ready for federation, not production-ready for
external adoption today. That mixed verdict is the test of whether
the method passes its own reflexivity standard. The report records
it without smoothing.

— end —
