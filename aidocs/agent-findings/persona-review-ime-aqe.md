# Persona review — Industrial Manufacturing & Quality Engineer (IME/AQE) on the SHACL trio

**Reviewer.** Industrial Manufacturing & Quality Engineer persona (CLAUDE.md
Role 4). 28 years on aerospace shop floors; AS9100/EN 9100 lead-auditor
mindset; EASA Part 21 (G) production-organisation muscle memory.

**Date.** 2026-05-22.

**Scope.** Aerospace manufacturing readiness assessment of the SHACL trio:
`aidocs/semantics/98-mffd-process-shapes.md`,
`aidocs/platform/100-mffd-views-workspace.md`,
`aidocs/platform/101-view-shapes-and-spi.md`,
plus the shape files under `aidocs/semantics/shapes/`.

**Caveat up front — the trio is not on disk.** None of the four cited
paths exist in `main`, none of the ten locked worktrees carry them, no
git reflog or stash reveals them, and a repo-wide grep finds **only two
backreferences** to the names (`aidocs/agent-findings/shacl-changeover-
non-ts.md` and `aidocs/agent-findings/industrial-robotics-ontology-
audit.md`). The trio is the **lost-in-worktree-cleanup design layer**.
The clarify-first memory (`feedback_agent_clarify_first.md`) explicitly
names these three docs as the gate condition for downstream
implementation work — that gate is currently open, and that is the
single largest finding of this review.

What I **did** review (the trio's *implemented shadow*, which is what
the auditor will actually trace at certification time):

- `backend/src/main/resources/shapes/shepard-core-shapes.ttl` (223 lines)
- `backend/src/main/resources/shapes/mffd-shapes.ttl` (370 lines —
  `MFFDCampaignShape`, `ProcessStepShape`, `BridgeWeldingShape`,
  `CalibrationCertificateShape`, `NDTGateShape`)
- `backend/src/main/resources/shapes/mini-shapes.ttl` (199 lines —
  `NCRShape`, `SignOffShape`, `VerificationActivityShape`)
- `backend/src/main/resources/shapes/ledger-anchor-shapes.ttl`
- `examples/mffd-showcase/seed.py` (12-step DAG, Q1 anomaly chain)
- `aidocs/agent-findings/persona-review-ontologist.md` (sibling persona)
- `aidocs/agent-findings/shacl-changeover-non-ts.md` (SHACL-1 PR-1..7)
- `aidocs/integrations/92-mffd-real-data-import-strategy.md`
- Memory: `project_mffd_domain_context.md`, `project_rebar_integration.md`,
  `feedback_agent_clarify_first.md`,
  `feedback_shacl_single_source_of_truth.md`

The shipped SHACL catalogue is approximately what the "98" doc would
have specified plus what "100/101" would have demanded form-renderer-
side. That is enough surface to render an aerospace-quality verdict —
but the absence of the prose source-of-truth means several decisions
this persona has strong opinions on **are not yet documented**, which
is itself a Part 21 (G) non-conformance against §21.A.139(b)(1)
(documented procedures).

---

## 1. Verdict (one paragraph)

The catalogue is **structurally on the right path** — `NCRShape` has
the EN 9100 §8.7 quadrant (finding / severity / root-cause /
corrective-action / status), `CalibrationCertificateShape` carries
`validUntil` (EN 9100 §7.1.5), `NDTGateShape` mandates an inspector
and a result enum, the DataObject status vocabulary now includes
`NCR_OPEN` and `REJECTED`, and `SignOffShape` exists as a typed
Activity with Agent + timestamp + approval-document IRI. The shipped
`ledger-anchor-shapes.ttl` even closes the **tamper-evidence** gap that
a EN 9100 §7.5.3.1 / Part 21 (G) §21.A.139(b)(2) auditor would normally
have to take on faith. **However**, the catalogue **does not enforce
the invariants that turn the pieces into a defensible audit trail**:
`NDT FAIL ⇒ raisedNCR exists` is a comment string not a SHACL rule,
the rework loop has **no typed link** between the failed and the
re-test DataObject (it leans on bare `prov:wasInformedBy`),
`hasCalibrationCert.validUntil >= inspectedAt` is not validated,
inspector ≠ operator is not enforced (independence violation),
sign-off is not anchored to the artefact it signs, and the trio's
prose layer that would name these invariants explicitly **does not
exist in the repository**. **Verdict: GAPS. Not yet EN 9100 / Part 21
(G) credible. With the seven fixes in §7, six weeks to credible; with
the trio's prose docs restored, three weeks.**

---

## 2. DIN EN 9100 readiness table

| EN 9100 § | Requirement | What's in the trio (shipped) | Gap |
|---|---|---|---|
| §8.5.2 — Identification & traceability | Every artefact bears a unique identifier; full forward + backward trace from end-product to material batch and equipment used. | `mffd:campaignNumber` pattern `MFFD-YYYY-NNN`, `mffd:certIdentifier` pattern `CAL-YYYY-NNNN`, `shepard:ncrIdentifier` pattern `NCR-XXXXX`, `appId` UUIDv7 substrate. | **Material batch is invisible.** No `mffd:materialBatch` slot on `ProcessStepShape`; CF/LMPAEK tape lot, resin lot, fastener lot are unrecorded. **Robot/equipment instance is implicit.** `mffd:operator` is mandatory; `mffd:equipmentUsed` is not — calibration cert references equipment, but the process step does not directly assert which equipment ran, so the join goes via the cert which is the wrong direction. Auditor cannot answer "which AFP head laid ply 7?" in one hop. |
| §7.1.5 — Monitoring & measurement resources (calibration) | Equipment used for acceptance is calibrated; calibration is traceable; out-of-cal equipment voids the records it produced. | `CalibrationCertificateShape`: identifier pattern, `calibratedEquipment` IRI, `calibratedOn`, `validUntil`, `calibrationLab`, `certificateDocument` PDF. `ProcessStepShape.hasCalibrationCert minCount 1`. | **No date-validity SHACL rule** — `validUntil >= ProcessStep.endedAtTime` is not enforced. A process step CAN reference an expired cert and the validator passes. **No traceability chain to the calibration standard** — no `mffd:traceableTo` (NIST/PTB primary). **No out-of-cal cascade** — declaring a cert revoked does not flip the children to `NCR_OPEN`; there is no inverse query in SHACL. |
| §8.7 — Control of non-conforming output (NCR routing) | Non-conformance is documented, segregated, dispositioned (rework / repair / scrap / use-as-is / concession), and the disposition is approved by authorised personnel. | `NCRShape` has finding, severity (`MINOR`/`MAJOR`/`CRITICAL`), root-cause, corrective-action, status (`OPEN`/`IN_PROGRESS`/`CLOSED`/`REJECTED`), `raisedAgainst` DataObject. `NDTGateShape.raisedNCR` predicate. | **Disposition vocabulary missing.** `ncrStatus` has 4 states; EN 9100 §8.7.1 lists at least 5 dispositions (rework, repair, scrap, regrade, use-as-is/concession). Lumping all of those into "IN_PROGRESS" loses the distinction an auditor cares about — was this defect repaired (still in spec) or used-as-is under concession (out of spec)? **No approval authority binding.** `NCRShape` has no required `prov:wasAssociatedWith` Quality-Agent — anyone can mark an NCR CLOSED. **No segregation flag.** No `mffd:partSegregated` boolean — physical control of the non-conforming part is unrecorded. **`raisedNCR` is optional** even when `ndtResult = "FAIL"` — the comment promises the invariant, the SHACL doesn't enforce it. |
| §7.5.3 — Inspector sign-off / independence | NDT results are recorded by a qualified inspector who is independent of the operator producing the work. | `NDTGateShape.inspector minCount 1` (`shepard:User`), `inspectedAt` mandatory `xsd:dateTime`, `ndtMethod` from controlled enum. | **Independence not enforced.** No SHACL rule `inspector != hasProcessStep/operator`. An operator can self-inspect their own work and the validator passes. This is the textbook §7.5.3 finding. **No qualification check.** Inspector is `shepard:User`, not a typed `shepard:QualifiedInspector` with `mffd:ndtLevel` (NAS 410 Level I/II/III). A trainee can sign off a Level III determination. |
| §10.2 — Corrective action effectiveness | After corrective action, a verification record demonstrates the corrective action was effective; recurrence is checked. | `NCRShape.correctiveAction` (free string), `ncrStatus = CLOSED`. `SignOffShape` exists as a separate node. | **No closure-evidence link.** `NCRShape` has no `mffd:effectivenessEvidence` pointing to the re-test NDTGate that demonstrates the fix worked. The seed (`examples/mffd-showcase/seed.py`) wires this through bare `prov:wasInformedBy`, which is too weak — an auditor would have to walk the prov chain and infer intent. **No sign-off requirement on closure** — moving NCR to CLOSED requires no SignOff Activity. **No recurrence check** — there is no `mffd:similarToNCR` link or query asserting "we checked for this defect elsewhere". |
| §7.5.3.1 — Record retention & integrity | Records of compliance retained for the lifetime of the product (often 30+ years for airworthy structure); tamper-evident; legible; retrievable. | `LedgerAnchorShape` (Bloxberg / OpenTimestamps / Bitcoin) provides on-chain hash anchoring. SHACL-1 PR-3 ships HMAC chain. | **Retention policy not asserted.** No `mffd:retainUntil` or `mffd:retentionClass` slot on Campaign, NCR, NDTGate, or Cert. **Anchoring is opt-in not mandatory** — no SHACL rule "every NCR closure SignOff `sh:node` references a `LedgerAnchor`". The mechanism is sound; the policy that mandates its use on safety-critical records is missing. |

**Summary scoring (lead-auditor's snap judgement):** §8.5.2 partial,
§7.1.5 partial-with-blocking-gap (date validity), §8.7 partial-with-
critical-gap (disposition vocabulary + approval authority), §7.5.3
**critical gap** (independence), §10.2 partial-with-major-gap (closure
evidence), §7.5.3.1 partial-with-policy-gap. **Overall: GAPS — not
yet certifiable**, but the structural primitives are present, which
means the path is "tighten the rules", not "add the concept".

---

## 3. What works (the genuine wins)

- **`NCRShape` is the right *shape*.** Finding / severity / root-cause /
  corrective-action / status / `raisedAgainst` is the EN 9100 §8.7
  quadrant in five fields. Nothing missing structurally — just rules
  not asserted (§7 below).
- **Calibration cert as a referenced sibling, not embedded.** The
  decision to model `mffd:CalibrationCertificate` as a peer DataObject
  with its own shape, reusable across all seven ProcessSteps via
  `sh:class` autocomplete, is exactly how EN 9100 §7.1.5 expects
  calibration to be traceable — one cert covers the equipment for the
  shift, not one cert per process step. This is a quietly excellent
  decision.
- **NDT method enum is realistic.** `ndtMethod sh:in (ultrasound,
  thermography, x-ray-CT, visual, tap-test, shearography)` matches
  what an aerospace IME actually orders for CFRP — not a textbook list,
  the real shop-floor toolkit. Whoever wrote this has talked to NDT.
- **`shepard:status` adds `NCR_OPEN` and `REJECTED`.** The upstream
  5-state lifecycle (`DRAFT`/`IN_REVIEW`/`READY`/`PUBLISHED`/`ARCHIVED`)
  was missing the failure terminal; the fork closes it. The ontologist
  persona caught this; it's worth re-affirming as IME — yes, this is
  the right two terms to add.
- **`SignOffShape` is a typed Activity, not a flag.** `prov:Activity`
  subclass with mandatory `prov:wasAssociatedWith` Agent, mandatory
  `prov:endedAtTime`, mandatory `approvalDocument` IRI is the right
  shape — sign-off as a *thing with provenance*, not a boolean on the
  DataObject. This is the structural fix for the "who clicked the
  button when" question.
- **Bridge welding numeric bounds.** The 16-channel weld-controller
  parameter ranges in `BridgeWeldingShape` (CM_I 4.5–5.5 A, W1_t
  60–70 s, etc.) mirror the `mffd:Constraint` declarations in
  `mffd-process.ttl §4` — and the SHACL messages name the **failure
  mode** if violated ("may cause ColdLap, Underweld or BurnThrough").
  That is the kind of message a shift supervisor can act on. Best
  feature in the catalogue.

---

## 4. What's wrong (the auditor-blockers)

- **The TR-anomaly→repair→re-test chain is not reconstructable in
  under 5 minutes — and possibly not at all.** Per the MFFD seed
  (`examples/mffd-showcase/seed.py` Q1 anomaly chain), the auditor's
  question is "AFP layup at ply 5 showed consolidation-force drop +
  TCP temp spike, NDT failed, rework happened, NDT recheck passed —
  show me the chain". With current shapes:
  1. Find the AFP DataObject. ✓ (one query)
  2. Find the NDT gate that failed for it. ✓ (`hasNDTGate` predicate).
  3. Find the NCR raised. **Partial** — `NDTGateShape.raisedNCR` is
     **optional** even when `ndtResult = FAIL`, so it may not be
     populated; the SHACL doesn't enforce it.
  4. Find the rework activity. **Missing** — no typed rework link.
     The seed wires it through `prov:wasInformedBy`, which is the
     same predicate used for normal predecessor-successor; the
     auditor can't distinguish a rework loop from a normal step
     forward without parsing the NCR text.
  5. Find the re-test gate. **Inferable but not direct** — there is
     no `mffd:reTestOf` predicate. The auditor walks the prov chain
     forwards and looks for "another NDTGate whose `inspectedAt` >
     NCR.openedAt and whose `raisedAgainst` ≅ original DataObject".
     This is **not a five-minute query**.
  **Net:** an auditor with EN 9100 lead-assessor experience would
  bring this up as a §8.7 finding — non-conforming product
  disposition not traceable without operator interpretation.
- **No SHACL enforcement of NDT-FAIL→NCR.** The `NDTGateShape` for
  `raisedNCR` has a `sh:message` saying "If ndtResult = FAIL,
  raisedNCR should reference the NCR…" — but no `sh:if` /
  `sh:condition` / SPARQL-based constraint binds the two. A FAIL
  gate with no NCR validates. This is the single most important
  conditional invariant in the catalogue and it is a comment string.
- **Inspector independence is not enforced.** `NDTGateShape.inspector`
  and `ProcessStepShape.operator` are both `shepard:User` — but no
  rule says they must be different individuals. The §7.5.3
  independence-of-inspection principle is unprotected.
- **Cert validity-at-time-of-use is not enforced.**
  `CalibrationCertificate.validUntil` exists; no rule asserts
  `validUntil >= ProcessStep.endedAtTime`. A cert that expired three
  months before the run can be referenced and validates clean.
- **`prov:wasInformedBy` is overloaded.** Per the seed it carries
  both (a) normal predecessor lineage AFP→Frame Welding, and (b)
  rework lineage AFP→Rework→NDTRecheck. A query "show me all
  re-test events" cannot run without semantic disambiguation — and
  per the ontologist persona's review the right predicate is
  `prov:wasRevisionOf` (or a domain-specific `mffd:revises`). Today
  it's missing entirely.

---

## 5. Arguments for different paths (two+ forks the trio leaves
unresolved)

### Fork A — NCR as DataObject vs. NCR as Annotation

**A1) NCR-as-DataObject (currently implemented in mini-shapes).**
- *Pro:* First-class entity with its own appId, lifecycle, REST
  surface, sign-off chain, optional attached files (8D report PDF,
  photographs, dispositioning approvals). EN 9100 §8.7 expects an
  NCR record to be a controlled document; modelling it as DataObject
  buys all the existing access control, audit, prov chain. Linkable
  from multiple ProcessSteps (one defect spanning two welds).
- *Con:* Heavier — one click to "create NCR" then fill in a separate
  form. Two top-level entities in the UI for what the operator
  thinks of as one event.
- *Persona lean:* **STRONG A1**. This is the right shape. The
  weight cost is real but the benefit is enormous: NCRs survive
  the DataObject that raised them being archived, can be
  cross-referenced across campaigns ("we saw this defect mode in
  MFFD-2025-003 too"), and survive being moved to a separate
  "Quality" workspace.

**A2) NCR-as-Annotation (the seductive alternative).**
- *Pro:* Lighter UI. One operator gesture: "annotate this DataObject
  with `quality:nonConformance`". No second entity.
- *Con:* Annotations don't have their own lifecycle, can't be
  closed/reopened, can't carry their own sign-off, can't be raised
  against multiple parents, can't be archived independently. **Fails
  EN 9100 §8.7 controlled-document requirement.** This is a "looks
  agile, fails audit" choice.
- *Persona lean:* Reject A2. The lightness is a trap.

### Fork B — Rework lineage: bare `prov:wasDerivedFrom` vs. typed `mffd:revises`

**B1) Bare `prov:wasDerivedFrom`.**
- *Pro:* W3C-PROV standard predicate. Tooling exists everywhere
  (Marquez, OpenLineage). Zero domain-specific commitment.
- *Con:* Doesn't distinguish "rework" (same physical part, repaired)
  from "regenerated" (new part, replaces the old). EN 9100 §8.7
  requires this distinction — rework keeps the part-number trace
  alive; replacement does not. PROV-O lacks the verb.

**B2) Typed `mffd:revises` (or `shepard:reworkOf` to make it
domain-neutral).**
- *Pro:* Names the auditor's question directly. SPARQL/Cypher query
  "show all reworks in MFFD-2025-003" is one hop. Distinguishes
  rework from replacement (use `shepard:replaces` for the latter).
- *Con:* New predicate; needs a SHACL rule that `revises` cardinality
  matches NCR open/closed status; needs UI surfacing.
- *Persona lean:* **B2 with a fallback subProperty axiom**
  (`shepard:reworkOf rdfs:subPropertyOf prov:wasRevisionOf`) so
  PROV-O tooling still sees the chain. This is the structural fix
  for the unreconstructable rework loop in §4. Without it, the
  trio fails the §8.7 audit.

### Fork C — Sign-off: user-attribute string vs. typed Activity vs. cryptographic signature

**C1) String attribute `signedBy: "alice@dlr.de"` on DataObject.**
- *Pro:* Zero infrastructure.
- *Con:* No timestamp guarantee, no chain-of-custody, no
  Part 21 (G) §21.A.139(b)(2) compliance, repudiable. Useless.

**C2) Typed Activity `shepard:SignOff` (currently implemented).**
- *Pro:* Has Agent, timestamp, document IRI, role. Sign-off is a
  *thing with provenance*. Already in `mini-shapes.ttl`.
- *Con:* Trust boundary is at the database. A privileged DB admin
  could write a SignOff that no human performed. Not tamper-evident.

**C3) Cryptographic signature.**
- *C3a — Detached PKCS#7 / CAdES signature on a canonicalised JSON-LD
  of the artefact.* True non-repudiation; works for Part 21 (G).
  Needs a PKI; DFN-PKI is available to DLR via X.509-Smartcard.
- *C3b — Ledger anchor (Bloxberg / OpenTimestamps) on the sign-off
  document hash.* `ledger-anchor-shapes.ttl` already ships this
  primitive. Doesn't sign *who*, but proves *when*.
- *C3c — Both: C3a signs the agent identity, C3b anchors the time.*
  Belt and braces; this is what an airworthiness records audit at
  Part 21 (G) renewal would expect for the high-stakes step.
- *Persona lean:* **C2 baseline + C3b mandatory for NCR closure
  SignOffs + C3a optional for high-criticality (`severity = CRITICAL`)
  closures**. The ledger-anchor primitive is already shipped; using
  it on NCR closure is one SHACL rule away. PKI/X.509 can wait until
  the first real audit cycle motivates the investment.

### Fork D — Disposition vocabulary granularity

**D1) Keep current 4-state (`OPEN`/`IN_PROGRESS`/`CLOSED`/`REJECTED`).**
- *Pro:* Simple.
- *Con:* Loses §8.7.1 disposition distinctions. Fails audit.

**D2) Expand to EN 9100 §8.7.1 vocabulary (`OPEN`, `UNDER_REVIEW`,
`DISPOSITIONED_REWORK`, `DISPOSITIONED_REPAIR`, `DISPOSITIONED_SCRAP`,
`DISPOSITIONED_REGRADE`, `DISPOSITIONED_USE_AS_IS`, `CLOSED`,
`REJECTED`).**
- *Pro:* Audit-defensible.
- *Con:* Verbose. Operators see a long dropdown.
- *Persona lean:* **D2**. The verbosity is the *audit value*.
  Pair the dropdown with a "common case" quick-button: "Mark rework"
  / "Mark scrap" / "Concession use-as-is (requires approver
  signature)".

### Fork E — Calibration validity: SHACL rule vs. service-layer check vs. query

**E1) SHACL rule** `validUntil >= xsd:date(now())` at validation
time, with a SPARQL-based constraint comparing cert.validUntil to
ProcessStep.endedAtTime via the join through `hasCalibrationCert`.
- *Pro:* Enforced at submission; cannot be bypassed.
- *Con:* SPARQL-based constraints need Jena SHACL engine support
  (which we have per SHACL-1 PR-1); cross-entity rules are heavier
  than property-shape rules.

**E2) Service-layer check** in `DataObjectService` before persist.
- *Pro:* Easier to debug, can issue a friendly error.
- *Con:* Bypassable via raw graph mutation; not declarative;
  doubles the contract surface.

**E3) Query/report** — "show me all process steps using expired
certs" runs at audit time, not at submission time.
- *Pro:* Operational reporting.
- *Con:* Catches violations after the fact, not before they enter
  the record.
- *Persona lean:* **E1 + E3.** Fail-fast at submission AND a
  standing audit dashboard. E2 is a redundancy that doubles the
  contract surface and should not be added.

---

## 6. `[NEEDS-CLARIFICATION]` blocks

```
[NEEDS-CLARIFICATION] Where are the trio source documents
(98-mffd-process-shapes.md, 100-mffd-views-workspace.md,
101-view-shapes-and-spi.md, semantics/shapes/, semantics/contexts/)?
  Context: feedback_agent_clarify_first.md explicitly gates downstream
  implementation work on these three docs being merged + iterated +
  signed off. None of them exist on disk. The repo refers to them
  only in two agent-findings files. The implemented shape catalogue
  exists in backend/src/main/resources/shapes/ but no prose layer
  states the design intent, the validation invariants, or the SPI
  contract.
  Options:
    A) Regenerate the trio docs from the shipped shapes (forensic
       reconstruction). Pro: matches what's in code. Con: bakes
       implementation as design; loses the original design intent.
    B) Treat the implemented shapes as the source of truth and
       formally retire the trio doc names; capture invariants in
       README.md files next to the .ttl files. Pro: single source
       of truth. Con: prose layer for invariants is harder to read
       in TTL comments than in markdown.
    C) Pause downstream work, recover the original trio docs from
       the worktree backups / Claude session history before they
       age out, then iterate. Pro: highest fidelity. Con: time-bounded
       — worktree backups will roll over.
  Lean: C if recoverable within 48 hours; else A on the
  understanding that the regenerated docs explicitly state "this
  is a forensic reconstruction post-cleanup".
```

```
[NEEDS-CLARIFICATION] What is the NCR-DataObject relationship
to the DataObject it pertains to — separate node, subtype, or
attached annotation?
  Context: mini-shapes.ttl shepard:NCR is a iao:DocumentPart with
  shepard:raisedAgainst → shepard:DataObject. The seed in
  examples/mffd-showcase/seed.py creates a separate DataObject
  for the NCR-related rework. The trio doc would name the
  canonical pattern; without it, plugin authors and the wiki-writer
  agent will diverge.
  Options:
    A) NCR is a peer DataObject with shepard:isNCRRecord = true
       attribute. Pro: leverages all existing DO infrastructure.
       Con: pollutes DO list views with quality records.
    B) NCR is its own first-class node-type (current mini-shape
       direction) with its own REST endpoints. Pro: clean
       separation, quality workspace can hide DOs. Con: needs
       a Java entity + service + repo + Rest class.
    C) NCR is an Annotation on the failed DataObject with a
       structured schema. Pro: lightest. Con: fails EN 9100
       §8.7 controlled-document requirement (rejected in §5
       Fork A).
  Lean: B. The shipped NCRShape direction is correct; needs a
  Java entity to back it.
```

```
[NEEDS-CLARIFICATION] Rework lineage predicate — what name and what
ontological hook?
  Context: §5 Fork B. The seed uses prov:wasInformedBy for both
  normal predecessor lineage and rework lineage, which is
  ambiguous. The ontologist persona flagged prov:wasRevisionOf
  as type-correct but unenforced.
  Options:
    A) shepard:reworkOf rdfs:subPropertyOf prov:wasRevisionOf.
       Pro: domain-neutral, PROV-O-compatible. Con: needs SHACL
       rule cardinality.
    B) mffd:revises (MFFD-namespaced). Pro: explicit domain. Con:
       every other domain plugin needs its own predicate.
    C) Reuse prov:wasRevisionOf directly. Pro: zero new vocab.
       Con: PROV-O semantics don't distinguish rework-of-same-
       physical-thing from new-version-of-document — they are
       both "revisions" in W3C-PROV.
  Lean: A. The shepard: namespace owns the cross-domain quality
  vocabulary; mffd: stays domain-pure.
```

```
[NEEDS-CLARIFICATION] Inspector qualification — separate
shepard:QualifiedInspector class with mffd:ndtLevel, or annotation
on shepard:User?
  Context: NDTGateShape.inspector is sh:class shepard:User. NAS 410
  Level I/II/III determines what determinations the inspector is
  qualified to sign. Today: anyone can sign.
  Options:
    A) New shepard:QualifiedInspector subclass of shepard:User with
       shepard:certifications [{method, level, validUntil}]. Pro:
       enforceable via SHACL. Con: adds a User subtype.
    B) Annotation on User with controlled vocabulary
       "ndt-ultrasonic-level-iii" etc. Pro: reuses existing
       annotation infrastructure. Con: not SHACL-validatable on
       the NDTGate without a SPARQL constraint.
    C) External: rely on Keycloak roles "ndt-level-i", "ndt-
       level-iii" and enforce at JWT/auth layer. Pro: leverages
       existing auth. Con: not visible in the graph for audit
       queries.
  Lean: A + C in parallel. The subclass + cert list gives audit
  reconstructability; Keycloak role gives operational gating.
  B is too weak alone.
```

```
[NEEDS-CLARIFICATION] Disposition vocabulary depth — keep current
4-state or expand to EN 9100 §8.7.1 9-state?
  Context: §5 Fork D. Current ncrStatus enum is OPEN / IN_PROGRESS /
  CLOSED / REJECTED. EN 9100 §8.7.1 names rework / repair / scrap /
  regrade / use-as-is/concession as distinct dispositions.
  Options:
    A) Keep 4-state, add a separate disposition predicate with the
       9-state vocab. Pro: minimal churn, semantics separated.
       Con: two fields for what operators think of as one decision.
    B) Replace 4-state ncrStatus with 9-state vocabulary inline.
       Pro: one field. Con: dropdown gets long; some states are
       sub-states (IN_PROGRESS pre-disposition vs. post-).
    C) Two-axis: status ∈ {OPEN, DISPOSITIONED, CLOSED, REJECTED} +
       disposition ∈ {rework, repair, scrap, regrade, use-as-is,
       n/a}, where disposition is mandatory iff status =
       DISPOSITIONED.
  Lean: C. Captures the lifecycle distinction (open → dispositioned
  → closed) and the choice (which disposition) separately.
  Reviewer-friendly.
```

```
[NEEDS-CLARIFICATION] Cryptographic non-repudiation — ledger anchor
on every NCR closure SignOff, or only on CRITICAL?
  Context: §5 Fork C. ledger-anchor-shapes.ttl is shipped and
  Bloxberg is free for DLR membership. Anchoring every NCR
  closure SignOff costs cents per artefact; anchoring only
  CRITICAL costs almost nothing.
  Options:
    A) Anchor every NCR closure. Pro: uniform, no decision logic.
       Con: trivial cost but operator surprise ("why is this
       slow?").
    B) Anchor when severity = CRITICAL. Pro: focused on highest-
       stakes records. Con: medium-severity NCRs lose tamper
       evidence.
    C) Anchor when severity ∈ {MAJOR, CRITICAL}. Pro: middle path.
       Con: arbitrary cutoff.
    D) Anchor on user request only ("anchor this record"). Pro:
       opt-in, no surprise. Con: nobody opts in unless they
       remember to.
  Lean: A. The cost is trivial, the uniform policy is
  auditor-friendly ("we anchor every quality closure"), and it
  matches a typical PSAC/CMM record-retention posture.
```

---

## 7. Top 3 changes for EASA Part 21 (G) credibility

Ranked by audit-blocker severity × structural reach.

**(1) Enforce the conditional invariants in SHACL — name and ship
the rules `NDT-FAIL ⇒ NCR`, `CertValid-at-RunTime`,
`InspectorIndependence`, `NCRClosure-requires-SignOff`.**
- *Effort:* ~1 week. Jena SHACL engine supports SPARQL-based
  constraints (SHACL-1 PR-1 already ships JenaShaclValidator);
  these are 4–6 SPARQL CONSTRUCT/ASK fragments.
- *Why first:* These four rules turn the catalogue from "describes
  the right shapes" to "won't accept a record that contradicts EN
  9100 §8.7 / §7.1.5 / §7.5.3". Without them the catalogue is a
  data-entry form, not a control system.
- *Operator-visible:* Form validation errors at submission with
  the message strings already in the shapes (those messages are
  already good; the rules just need to *fire*).

**(2) Add the `shepard:reworkOf` predicate with SHACL cardinality
binding it to NCR status — and surface it in the lineage graph.**
- *Effort:* ~2 weeks (predicate + SHACL rule + UI lineage-edge
  styling + Cypher backend support).
- *Why second:* Solves the §4 unreconstructable-rework-loop
  problem. An auditor can ask "show me the rework chain for
  MFFD-2025-003" and get a clean answer in two clicks (the
  graph view filters edges by predicate type per the planned
  views workspace).
- *Operator-visible:* When you click "rework needed" on an NCR,
  the new DataObject is created with `shepard:reworkOf` =
  the failed DataObject (not bare `prov:wasInformedBy`); the
  lineage graph renders this as a distinct edge style (orange
  vs. grey, "rework" label).

**(3) Mandate `LedgerAnchor` on every NCR closure SignOff +
formalise the calibration-cert validity-at-time-of-use rule.**
- *Effort:* ~1 week. `LedgerAnchorShape` is shipped; the rule is
  one SHACL constraint asserting "every SignOff whose
  `prov:used = ?ncr` and where `?ncr shepard:ncrStatus = 'CLOSED'`
  has at least one `LedgerAnchor anchors=?signoff`". The cert
  validity-at-time rule is also one SPARQL-based constraint.
- *Why third:* Closes the §7.5.3.1 retention-integrity gap and
  the §7.1.5 cert-validity gap simultaneously. Bloxberg
  membership is free for DLR; OpenTimestamps fallback is
  zero-cost. The §7.5.3.1 record-integrity story becomes
  defensible against a "what if the admin tampered with the
  database" question — which is the question Part 21 (G)
  renewal auditors actually ask.
- *Operator-visible:* Closing an NCR triggers an async "anchoring
  record…" notification (NTF1-shaped); the record's status pill
  shows a "anchored" badge with the Bloxberg txid; the SignOff's
  detail view links to the ledger explorer.

---

## 8. Honest gaps in this review

- I did not see the actual trio prose — my review is of the
  *implemented shadow*. Some of the [NEEDS-CLARIFICATION] blocks
  may be answered by the missing prose; recovering it before any
  implementation decision is built on this review is the right
  sequence.
- I did not validate that the shipped `BridgeWeldingShape` numeric
  bounds actually match the **MFFD physical control plan** held
  by ZLP Augsburg — the bounds are cited from the showcase
  ontology, which is itself a synthetic dataset per
  `examples/mffd-showcase/seed.py`. For the real MFFD data (when
  it arrives per `project_mffd_seed_demo.md`) those bounds must
  be sourced from the ZLP control-plan document, not the seed.
- EASA Part 21 (G) §21.A.139(b)(2) record-integrity expectations
  for digital records are still evolving — the Bloxberg-anchoring
  posture is defensible today; if EASA's Acceptable Means of
  Compliance (AMC) for digital production records publishes (~2027
  per the EASA-AI-WG roadmap), this review should be revisited.
- I rendered an EN-9100/Part-21G lead-auditor verdict; I did not
  cross-check against FAA AC-21-50 / AC-145-9 — for a US
  certification target the disposition vocabulary and the
  inspector-independence rules differ slightly.
