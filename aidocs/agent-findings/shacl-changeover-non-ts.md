# SHACL changeover (non-TS scope) — implementation log

**Status.** Foundation slice landed on a worktree branch. PR-1 + PR-3
+ PR-4 implemented + tested + tracker-updated. PR-2 (n10s ↔ Jena
substrate), PR-5 (predicate lift), PR-6–8 (NCR / AFP / welding shape
integration with read projection to legacy attribute shape), PR-9
(`shepard-plugin-mffd-domain` move) explicitly DEFERRED — see "What
was deferred and why" below.

**Brief.** 2026-05-22 RESUME task. Six prior `[NEEDS-CLARIFICATION]`
blocks were answered by the user; this run proceeded straight to
implementation without further clarification.

## What landed

1. **PR-1 — Jena SHACL validator (substrate-less).**
   - `jena-shacl:5.4.0` dependency added to `backend/pom.xml`
     (Apache-2.0; `slf4j-jdk14` excluded to keep the single-logger
     contract).
   - `de.dlr.shepard.v2.shapes.validator.JenaShaclValidator`
     (`@ApplicationScoped`) — in-process validator taking two
     Turtle strings (data + shape), returning a `Report` with
     `conforms` / `parseError` / `engineError` / `findings`.
   - Parse / engine failures never throw outward; they degrade to a
     structured report the REST layer surfaces unchanged.

2. **PR-3 — HMAC audit chain.**
   - `de.dlr.shepard.provenance.entities.InstanceConfig` — Neo4j
     singleton carrying `instanceSecret` + `secretVersion`. Follows
     the A3b / N1c2 / UH1a `:*Config` pattern.
   - `InstanceConfigDAO` (`@ApplicationScoped`) + `InstanceConfigService`
     (StartupEvent observer seeds the singleton from
     `SHEPARD_INSTANCE_SECRET` env / `shepard.audit.instance-secret`
     config / `SecureRandom` fallback in that order; falls back with
     a WARN so an operator can wire in a stable value before the
     next restart).
   - `HmacChainService` — best-effort `HMAC-SHA256(secret_v_n,
     prevHmac ‖ activityCanonical)` chain. Stamped onto every
     `Activity` write via `ProvenanceService.record()`. Verifier
     walks the chain detecting `secretVersion` transitions.
   - `Activity` entity gained `auditHmac` / `auditPrevHmac` /
     `secretVersion` fields (all nullable; pre-chain rows simply
     lack them).
   - **Decision baked.** `mffd:auditHmac` / `mffd:auditPrevHmac` on
     the NCR SHACL shape (`mffd-ncr.shacl.ttl` lines 237-253) is a
     denormalised forward-pointer to the `:Activity` that last
     mutated the NCR — the `:Activity` chain is the source of
     truth. NCR write paths land in PR-6+ and copy the Activity's
     `auditHmac` into the corresponding shape predicate.

4. **PR-4 — `POST /v2/shapes/validate`.**
   - `ShapesValidateRest` (`@Path("/v2/shapes")`, `@RolesAllowed
     ("authenticated")`).
   - Request: `{dataTurtle, shapeTurtle}` (`ShapeValidationRequestIO`).
   - Response: `ShapeValidationReportIO` — pure wire-shape adapter
     for `JenaShaclValidator.Report`.
   - **Bad Turtle is a 200 with `parseError` set, NOT a 400.**
     This is the documented contract — lets the MCP tool branch on
     "malformed payload" separately from "malformed request".

5. **PR-5 (skeleton only).**
   - `V59__InstanceConfig_constraint.cypher` — appId uniqueness on
     `:InstanceConfig`, idempotent (`IF NOT EXISTS`).
   - `V60__NOOP_SHACL_predicates_placeholder.cypher` — reserved slot
     for the predicate-lift PR. NO-OP today. Header documents what
     PR-5 will lift (NCR, calibration cert, NDT gate, AFP layup
     minus TS refs, welding minus TS refs, AI annotations, structured/
     file refs semantic side) and what stays out of scope (anything
     referencing TS channels by 5-tuple — the sibling worktree's
     PR-9 owns those).

## What was deferred and why

- **PR-2 (n10s → Jena Model extraction).** Validate-only contract is
  shippable without it. The MCP-tool / form-builder / plugin-author
  use cases all supply both inputs. Reading RDF subgraphs out of
  n10s as a Jena Model is real work (cypher → RDF conversion +
  caching) and belongs in its own PR with its own integration test.
- **PR-5 (predicate lift).** v5 byte-fidelity fixtures do not exist
  in `backend/src/test` today; lifting predicates without recorded
  responses would mean writing a read projection with no oracle.
  V60 reserves the slot; the lift PR is gated on a fixture-capture
  PR.
- **PR-6–8 (NCR / AFP / welding shape integration).** Depends on
  PR-2 (so the read projection can pull RDF out of n10s) and PR-5
  (so it has a place to land lifted predicates). Will reference TS
  channels via the opaque `mffd:hasTraceChannelPlaceholder`
  predicate (decision baked in the brief, §1).
- **PR-9 (`shepard-plugin-mffd-domain` module).** The shape files
  in `aidocs/semantics/shapes/` are still evolving (last v2 pass
  was 2026-05-22, the brief's own date). Moving them into a plugin
  module is churn while the v2 changes settle. User explicitly
  pre-authorised this PR but the cost/value math says wait one
  cycle.

## v5 byte-fidelity proof per touched endpoint

**(e) of the final report.** No `/shepard/api/...` endpoints were
touched in this slice. Every new surface is `/v2/...`. v5 fixtures
do not exist in this repo today (`find backend/src/test -path
'*fixture*'` returns only the LUMEN test seed) — so the byte-fidelity
gate is vacuously satisfied for this PR and becomes a real obligation
on the PR-6–8 cluster that introduces a read projection.

## New `[NEEDS-CLARIFICATION]` count

**Zero.** The validate-only contract decision (decision #3 in the
brief) was specific enough that no further ambiguity arose during
implementation. The HMAC-location-on-NCR-shape vs `:Activity`
question was resolved by reading: `:Activity` chain = source of
truth; shape predicate = denormalised pointer (flagged in the PR-3
section above so a reviewer can object).

## Operator runbook touchpoints

- New env var: `SHEPARD_INSTANCE_SECRET` (recommended). When absent,
  shepard logs a WARN and falls back to `SecureRandom`. The
  fallback secret persists in `:InstanceConfig` and survives
  restarts; setting `SHEPARD_INSTANCE_SECRET` later does NOT
  override the persisted value (the singleton-wins-once-seeded
  rule).
- New verify-after-restart Cypher:
  ```cypher
  MATCH (n:InstanceConfig) RETURN n.appId, n.secretVersion;
  // expect: exactly one row, secretVersion=1 on a fresh install.
  ```
- Rotation API: not exposed yet — `InstanceConfigService.rotate()`
  is wired but lacks REST + CLI parity. A follow-up PR adds
  `POST /v2/admin/instance-config/rotate` + `shepard-admin
  instance-config rotate` per the A3b/N1c2/UH1a precedent.

## Cross-references

- `aidocs/34-upstream-upgrade-path.md` — SHACL-1 row.
- `aidocs/44-fork-vs-upstream-feature-matrix.md` — §7a SHACL-1
  validator row + §8 SHACL-1b audit chain row.
- `aidocs/semantics/98-mffd-process-shapes.md §Changelog v2-3` —
  the shape-level surface of the HMAC primitive (NCR shape
  predicates).
- `aidocs/semantics/95-shacl-templates-and-individuals.md §11` —
  the n10s + Jena coexistence architectural decision.
- Brief: 2026-05-22 RESUME instructions (no aidocs path; carried in
  the agent's session memory).
