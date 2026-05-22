# v15 review — IME + AQE lens

*Reviewer: Industrial Manufacturing Engineer + Aerospace Quality Engineer
(Claude agent, Role 4 per CLAUDE.md), 2026-05-22.
Scope: `aidocs/integrations/93-mffd-import-v15-requirements.md`. Read-only.
Anchor regulations: DIN EN 9100:2018; EASA AI Concept Paper Issue 02
(March 2024); EU AI Act 2024/1689 Art. 50; EU Machinery Regulation
2023/1230 Art. 17 + Annex IV.*

## Audit-readiness scorecard

| Regulation | Requirement | What v15 produces | Gap | Severity |
|---|---|---|---|---|
| **EN 9100 §8.4.2** (control of externally provided processes) | Records of supplier-provided data, traceable to source | `prov:used src:import-mffd-v15` + `shepard:sourceInstance <cube3 URL>` per batch | No DO-level back-link to cube3 source URL/ID — only batch-level. Auditor walking from a single Track to its source needs an attribute on the DO. | **MAJOR** |
| **EN 9100 §8.5.2** (identification + traceability) | Unique identification of every output of the process | appId per DO + HMAC-chained Activity per write (commit 0f535314) | DAG integrity check (§13) is a *post-import* operator check, not a SHACL invariant that fails the import. An auditor wants "the system refused malformed lineage", not "I ran a Cypher walk after". | MAJOR |
| **EN 9100 §8.7** (control of non-conforming outputs) | Documented rework / concession decisions | Untyped `predecessorIds[]` edges only | NCR / rework chains in real MFFD data (NDT FAIL → Rework → NDT PASS) become indistinguishable from normal predecessor edges. No `prov:wasRevisionOf` / `fair2r:repairs` typing. | **CRITICAL** |
| **EN 9100 §7.1.5** (monitoring/measuring resource control — calibration) | Calibration cert linkage to measurement record | Whatever the source structured-data payload carries, passed through verbatim | Cert IDs are buried inside `StructuredDataPayload` JSON strings; no SHACL shape promotes them to typed `:CalibrationRef` edges. Audit query for "show me uncalibrated runs" is impossible. | MAJOR |
| **EASA CP2 DA-04** (DQR — traceability + integrity) | Origin + lineage + integrity per artefact | appId-stable; HMAC chain on every Activity | ✓ Strong fit. v15 inherits PROV1a+HMAC for free. | OK |
| **EASA CP2 EXP-09 MOC-2/4** (chronological I/O reconstruction) | Per-event Activity, retrievable by time | PROV1a captures one Activity per REST write | ✓ Per-DO writes generate Activities by default. Batch-level `fair2r:AuthoringPass` (§10) is *additional*, not a replacement. | OK |
| **EU AI Act Art. 50(2)** (machine-readable AI-output marking, effective 2026-08-02) | Every AI-generated artefact carries a machine-readable AI flag | `X-AI-Agent` header *sent* by v15 + per-batch `fair2r:AuthoringPass` triples | **`X-AI-Agent` is NOT consumed anywhere in backend** (grep: zero hits in `backend/src/main/java/`). The header is documentation, not capture. `fair2r:modeOfProduction` is unimplemented. Per-DO `_provenanceMode: "ai"` flag (per memory `project_ai_human_collab_provenance.md`) does not appear on any IO response. | **CRITICAL** |
| **EU 2023/1230 Art. 17** (technical-file retention ≥ 10 years) | Tamper-evident retention | HMAC chain (verifiable internally); Garage S3 storage (durable) | HMAC verifies internal consistency but the **key is held by Shepard** — an auditor in 2036 must trust DLR's secret. No external anchor. TPL17 (Bloxberg) is **designed, not shipped**. v15 has zero ledger hooks. | **CRITICAL** |
| **EU 2023/1230 Annex IV** (signed-off documents in technical file) | Inspector / operator sign-off with attribution | Source structured-data preserved; no typed `prov:wasAssociatedWith inspector:<id>` edge produced | If cube3 source contains a `signed_by` field, v15 lifts it as a freetext attribute, not as a typed agent edge. | MAJOR |

## AI-mode marking — what's missing

Per `project_ai_human_collab_provenance.md` every imported DO is **🤖 AI-only at
creation** (the entire MFFD ingest is `auto-applied` by claude-opus-4-7 on behalf
of fkrebs@nucli.de; no human touched any DO). The three-mode model demands:

1. **Wire-level flag on every IO response.** `_provenanceMode: "ai"` on every
   `DataObjectIO`. **Not implemented** — grep for `_provenanceMode`,
   `modeOfProduction`, or `fair2r:` in `backend/src/main/java/` returns nothing.
   An EASA inspector running "show me everything an AI created that no human has
   reviewed" gets no answer.
2. **`fair2r:wasAcceptedAs auto-applied`** on every DO's creating Activity.
   The v15 turtle in `data-ontologist-prov-o-v15.md` includes `fair2r:verificationState
   verif:unverified` on the `fair2r:Claim` — that's the *content claim* unverified
   state, not the acceptance-ladder rung. Two different predicates; v15 only ships
   one.
3. **UI badge.** Not blocking v15 (it's a frontend gap) but the DO detail page
   will show no chip differentiating these 8,383 AI-imported DOs from human
   uploads. The persona-review of MFFD provenance can't work without it.

**Concrete fix (smallest addition that closes the audit gap):**

- Extend `ProvenanceCaptureFilter` to read `X-AI-Agent` header (already sent by
  v15) and `X-AI-Acceptance` header (new — v15 should send `auto-applied`).
  Stamp the Activity with `fair2r:modeOfProduction "AI"` +
  `fair2r:wasAcceptedAs verif:auto-applied`.
- Derive `_provenanceMode` on `DataObjectIO` from the creating Activity.
  NON_NULL serialisation keeps it additive on the v1-compat surface.
- One Cypher migration adds the two properties to the `:Activity` schema —
  same migration that task #159 already opens for the v15 predicates. Bundle
  them.

**Effort:** ~1 day backend + half-day frontend; ships before v15 runs ⇒ every
imported DO is correctly marked from t=0. Run v15 *after* this lands, not before.

## Ledger-anchoring readiness

**Not ready.** v15 has zero hooks into TPL17 (Part 16 of doc 95). The pieces:

- `shepard-plugin-ledger-anchor` — not built (design only, doc 95 §14f).
- `shepard:anchorRequired true` SHACL predicate — not registered in any
  migration (task #159 doesn't include it).
- v15's per-batch `fair2r:AuthoringPass` Activity is the natural anchor target
  (one Bloxberg PoE call per 100-DO batch = ~84 anchor txs for the full
  8,383-DO import — easily affordable on the free academic ledger).

**Consequence for the MFFD ingest:** the imported dataset's *internal* integrity
is HMAC-protected (commit 0f535314, good); its *external* integrity rests on
"trust DLR's key store from 2026 through 2036". For EU 2023/1230 Art. 17's
10-year window this is defensible-but-weak — a sophisticated dispute (e.g. a
post-incident reconstruction in 2032) could argue the chain was retroactively
re-signed. Anchoring closes the dispute.

**Recommendation:** treat v15 as the **first ledger-anchor consumer**. Even a
minimum-viable Bloxberg adapter — a 50-line Python `bloxberg_anchor(sha256)` call
inside the v15 batch loop, with the txid stamped on the `fair2r:AuthoringPass`
Activity via the existing `semanticAnnotations` endpoint — would land the
audit-grade property without waiting for the full plugin. Promote to a real
plugin in a follow-on iteration.

**Estimated effort:** half-day to add to v15 directly; one sprint for the proper
plugin. The half-day path is the right call for the MFFD ingest.

## Process-step DAG integrity — Bug I fix verification

§13 acceptance criterion *"DAG topology preserved (every predecessorIds[] link
verified — full chain reachable via predecessor walk from any leaf back to its
PlyGroup root or Frame root)"* is the right shape, but:

- **The check is a post-import operator action**, not a SHACL-enforced invariant
  on every DO POST. A 401-during-batch scenario could leave dangling
  predecessors that pass `POST /dataObjects` but break the walk. An auditor wants
  the import to **refuse** malformed lineage at write time.
- **Two-pass ordering not specified.** A DO whose predecessor doesn't exist yet
  on dest must POST after its predecessor. v15's producer-iterates-source-DOs
  pattern (§5) doesn't say whether the iteration is topologically sorted. If
  iteration is by appId order from cube3, dangling references will appear
  mid-import and must be patched in pass 2.

**Concrete fix:** the v15 spec should mandate either (a) topological
pre-sort of source DOs before enqueueing, or (b) explicit two-pass mode
(pass 1: create all DOs without predecessors; pass 2: PATCH predecessors).
Without this, the §13 walk verification can fail on a 100%-successful import.

## Predecessor typing — the rework-loop blocker

v15 ships untyped `predecessorIds[]`. Real MFFD data contains rework events
(per project memory `project_mffd_domain_context.md` — NDT FAIL → Rework → NDT
PASS). After import these become indistinguishable from normal
predecessor links. An auditor cannot answer "show me every rework event in the
campaign" via a query — they must read the DO descriptions.

**Right shape (per PROV-O + memory `project_ai_human_collab_provenance.md`):**

| Source-side semantics | Edge type |
|---|---|
| Normal step succession (Frame-1 follows PlyGroup-1) | `prov:wasInformedBy` (default) |
| Rework / revision after failure | `prov:wasRevisionOf` + `fair2r:repairs <NCR-claim>` |
| Investigation of an anomaly | `prov:wasInformedBy` + `shepard:investigationOf <DO>` |

**v15 reality:** cube3 does not expose edge typing in its v1 surface
(`predecessorIds[]` is the only channel). The fix lives in two places:

1. **Heuristic upgrade during import.** v15 could detect rework patterns from
   source attributes (`status: "REWORK"`, `iteration > 1`, name suffix `_v2`,
   …) and tag the resulting Activity with `fair2r:repairs`. The MFFD seed
   convention is the reference.
2. **Post-import forging pass.** A second pass (data-forging stage 2: "type
   the predecessor edges") that an AI runs against the imported skeleton,
   reading the source narrative and proposing typed edges with
   `fair2r:wasAcceptedAs unchecked` until a human reviews.

Either is acceptable; the spec must pick one and write it down. Without typed
edges, the "the imported MFFD data is audit-grade" claim fails the first
rework-event question.

## Recommendations ordered by audit-severity

1. **CRITICAL — wire `X-AI-Agent` header capture + `_provenanceMode` IO field
   before v15 runs.** Without this, 8,383 DOs land flagged only by header
   convention that the backend ignores. EU AI Act Art. 50 effective 2026-08-02
   (75 days). One-day backend + half-day frontend, in the same Cypher migration
   that task #159 opens. **Block v15 on this.**
2. **CRITICAL — minimum-viable Bloxberg anchor inside v15.** Half-day add: per
   batch `fair2r:AuthoringPass`, compute SHA-256 of the batch turtle, call
   `bloxberg.org/createBloxbergCertificate`, store `(ledgerName, txId,
   anchoredAt)` triple on the Activity via `semanticAnnotations`. Closes the
   EU 2023/1230 Art. 17 10-year tamper-evidence gap for the foundational
   import. Promote to `shepard-plugin-ledger-anchor` after the fact.
3. **MAJOR — predecessor edge typing strategy.** Pick (heuristic during import)
   OR (forging pass 2). Write it into §10 of doc 93 before v15 runs. Untyped
   edges are an EN 9100 §8.7 NCR-traceability failure.
4. **MAJOR — topological pre-sort + write the §13 walk as an acceptance test
   not a post-hoc check.** A SHACL invariant on every DO POST (predecessor
   exists on dest, or is also being created in this batch) closes the
   "dangling predecessor on partial import" failure mode.
5. **MAJOR — promote source URL to a typed `shepard:sourceURI` attribute on
   every DO**, not just on the batch Activity. EN 9100 §8.4.2 auditor walks
   from a single Track back to cube3 in one query, not a join. Trivial
   addition to the POST body builder.

---

**Bottom line:** v15 is structurally sound (HMAC chain inherited, PROV1a
captures every write, batch-level f(ai)²r triples are correct shape). It
would **fail an EU AI Act Art. 50 audit today** because the AI-mode marking
the spec promises in `X-AI-Agent` is not consumed by the backend, and it
would **fail an EU 2023/1230 Art. 17 dispute** because the integrity
guarantee is internal HMAC only. Both gaps are < 1 day of work and should
block the import. The other three (predecessor typing, topological sort,
typed source URL) are the difference between "imported data" and
"audit-grade imported data" — fix before the post-import forging passes
start, because mutating an audit-weak baseline conflates the provenance.
