---
stage: audited-by-personas
last-stage-change: 2026-05-23
---

# PROV-O fragment design for v15 MFFD batch import

**Audience:** v15 author (Claude Opus 4.7 acting on behalf of fkrebs@nucli.de).
**Goal:** every batch (every 100 DOs or 5 min) emits one Turtle fragment to
`POST /v2/semantic/{repoAppId}/import` (n10s-backed INTERNAL repo per
`aidocs/semantics/48`), satisfying the no-parentless-claim invariant
(`aidocs/semantics/95 §14e`, `backend/src/main/resources/shapes/fair2r-shapes.ttl`).
**Source of truth:** `https://github.com/noheton/f-ai-r/blob/main/doc/provenance.ttl`
(read 2026-05-22). PROV-O 2013-04-30 recommendation. UUID v7 already used by
shepard (`aidocs/platform/25`). HTTPS URI scheme per `aidocs/platform/91`.

---

## Agency model

F(AI)²R's reference TTL (`agent:orchestrator prov:actedOnBehalfOf agent:human-author`)
settles the direction: **the AI agent acts on behalf of the human.** Human is
responsible party, AI is delegated tool — matches OECD AI-in-Science and EU AI
Act Art-50. The script is a `fair2r:Source` (also typed `prov:Plan` since it
plans the AI's actions) that the AI agent `prov:used`.

## Worked turtle fragment for one batch

```turtle
@prefix prov:    <http://www.w3.org/ns/prov#> .
@prefix dcterms: <http://purl.org/dc/terms/> .
@prefix foaf:    <http://xmlns.com/foaf/0.1/> .
@prefix xsd:     <http://www.w3.org/2001/XMLSchema#> .
@prefix rdfs:    <http://www.w3.org/2000/01/rdf-schema#> .
@prefix fair2r:  <https://noheton.org/f-ai-r/ns#> .
@prefix verif:   <https://noheton.org/f-ai-r/ns/verif#> .
@prefix shepard: <http://semantics.dlr.de/shepard-upper#> .
@prefix agent:   <https://noheton.org/f-ai-r/agent/> .
@prefix mffd:    <http://semantics.dlr.de/mffd-process#> .

# Per-instance namespaces — minted by the destination Shepard
@prefix act:  <https://shepard.nuclide.systems/id/activity/> .
@prefix src:  <https://shepard.nuclide.systems/id/source/> .
@prefix do:   <https://shepard.nuclide.systems/id/dataobject/> .
@prefix coll: <https://shepard.nuclide.systems/id/collection/> .
@prefix usr:  <https://shepard.nuclide.systems/id/user/> .

# ─── The script artefact (one per v15 SHA) ─────────────────────────────
src:01923f7e-import-mffd-v15
    a fair2r:Source , prov:Plan ;
    rdfs:label "import-mffd.py v15"@en ;
    dcterms:identifier "import-mffd.py@v15" ;
    fair2r:sourceSha256 "2c67de6ba38fbbc9a4842d7a5ac5b4dc..."^^xsd:hexBinary ;  # 64-hex, v15 hash
    dcterms:source <https://github.com/dlr-shepard/shepard-fork/blob/main/examples/mffd-showcase/scripts/import-mffd.py> ;
    dcterms:date "2026-05-22"^^xsd:date .

# ─── Agents — declared once per instance, referenced thereafter ────────
# (Idempotent: INSERT … WHERE NOT EXISTS via n10s import semantics.)
agent:claude-opus-4-7 a fair2r:AIAgent ;
    rdfs:label "Claude Opus 4.7" ;
    fair2r:modelFamily "Anthropic Claude" ;
    fair2r:modelVersion "claude-opus-4-7[1m]" ;
    prov:actedOnBehalfOf usr:fkrebs-at-nucli-de .

usr:fkrebs-at-nucli-de a fair2r:HumanResearcher ;
    foaf:name "Florian Krebs" ;
    foaf:mbox <mailto:fkrebs@nucli.de> .

# ─── The batch Activity ────────────────────────────────────────────────
act:0192a14b-3c00-7a44-9f12-c0ffeebabe11
    a fair2r:AuthoringPass ;
    rdfs:label "MFFD v15 import batch #47"@en ;
    prov:startedAtTime "2026-05-22T14:31:08.214Z"^^xsd:dateTime ;
    prov:endedAtTime   "2026-05-22T14:33:42.901Z"^^xsd:dateTime ;
    dcterms:date       "2026-05-22"^^xsd:date ;

    # Two agents — both prov:Association-qualified
    prov:wasAssociatedWith agent:claude-opus-4-7 ;
    prov:wasAssociatedWith usr:fkrebs-at-nucli-de ;
    prov:qualifiedAssociation [
        a prov:Association ;
        prov:agent   agent:claude-opus-4-7 ;
        prov:hadRole shepard:role-executor ;
        prov:hadPlan src:01923f7e-import-mffd-v15
    ] ;
    prov:qualifiedAssociation [
        a prov:Association ;
        prov:agent   usr:fkrebs-at-nucli-de ;
        prov:hadRole shepard:role-operator
    ] ;

    # The script the AI used to execute
    prov:used src:01923f7e-import-mffd-v15 ;

    # Scope — destination collection (queryable post-hoc)
    shepard:targetCollection coll:515365-mffd-bridge-welding ;

    # Counts (additive scalars on the Activity for fast aggregation)
    shepard:filesUploaded         93 ;
    shepard:timeseriesImported    14 ;
    shepard:structuredPayloads     7 ;
    shepard:batchSequence         47 ;
    shepard:throughputBytesPerSec "4.21E7"^^xsd:double ;
    shepard:retryCount            0 ;
    shepard:sourceInstance        "nuclide-edge-dropbox" ;

    # Generated entities — minimum-viable enumeration
    prov:generated do:0192a14b-3c01-7a44-9f12-c0ffee000001 ,
                   do:0192a14b-3c01-7a44-9f12-c0ffee000002 ,
                   do:0192a14b-3c01-7a44-9f12-c0ffee000003 .

# ─── One generated entity, declared with the invariant satisfied ───────
do:0192a14b-3c01-7a44-9f12-c0ffee000001
    a shepard:DataObject , fair2r:Claim ;
    rdfs:label "Frame 12 — Bridge-Weld AF-4"@en ;
    prov:wasGeneratedBy  act:0192a14b-3c00-7a44-9f12-c0ffeebabe11 ;
    prov:wasAttributedTo agent:claude-opus-4-7 , usr:fkrebs-at-nucli-de ;
    fair2r:verificationState verif:unverified ;
    fair2r:claimText "Imported from nuclide-edge dropbox; raw process trace, unreviewed." ;
    dcterms:created "2026-05-22T14:31:08Z"^^xsd:dateTime .
```

---

## IRI minting strategy

| Concept | IRI shape | Source of UUID / token |
|---|---|---|
| **Activity** | `https://<instance>/id/activity/<uuid-v7>` | Mint UUID v7 client-side in v15 (same algorithm Shepard uses). Embed in the POST body — the destination accepts pre-minted IRIs because n10s import is idempotent on `r:Resource.uri`. |
| **Script Source** | `https://<instance>/id/source/<uuid-v7-derived-from-sha>` | UUID v7 seeded from the script SHA-256 (first 16 bytes of SHA → set version+variant bits). Stable across instances for the same script artefact. |
| **AI agent** | `https://noheton.org/f-ai-r/agent/claude-opus-4-7` | **Vendor namespace, fixed.** Do NOT mint per-instance — agent identity is global. Vendored at TPL9a load time (`fair2r-shapes.ttl:14`). |
| **Human researcher** | `https://<instance>/id/user/<slug-of-mailto-localpart>` (slug = `fkrebs-at-nucli-de`) | Slug rule: `<local>-at-<domain-dot-to-dash>`. If OIDC `sub` exists, prefer `https://<issuer>/user/<sub>`. ORCID overrides everything: `https://orcid.org/<orcid>` if present. |
| **Generated DataObject** | `https://<instance>/id/dataobject/<appId>` | The DataObject's existing UUID v7 (`aidocs/platform/91`). Already minted by Shepard at create time — v15 just references it. |
| **Generated FileContainer / Timeseries / StructuredData** | `https://<instance>/id/{file,timeseries,structured}/<appId>` | Same — Shepard mints; v15 references. |
| **Collection scope** | `https://<instance>/id/collection/<appId>` | Pre-existing; from the destination collection ID (e.g. 515365). |
| **Verification rung** | `https://noheton.org/f-ai-r/ns/verif#unverified` | Fixed vendor IRIs — never minted. |
| **Roles (`shepard:role-*`)** | `http://semantics.dlr.de/shepard-upper#role-executor` etc. | Declared once in `shepard-core-shapes.ttl`; referenced by IRI thereafter. |

**Critical decision: pre-mint or post-mint.** v15 SHOULD pre-mint Activity IRIs
client-side (UUID v7 supports this — RFC 9562). This lets v15 reference the
Activity in the same fragment as the entities it generates without a round-trip.

---

## F(AI)²R predicates used per batch

SHACL-required on `fair2r:AuthoringPass` (per `fair2r-shapes.ttl`):
`prov:startedAtTime` (1), `prov:wasAssociatedWith fair2r:AIAgent` (≥1),
`prov:used fair2r:Prompt` (≥1 — v15's script doubles as the Plan/Source),
`prov:generated` (≥1).

SHACL-required on `fair2r:AIAgent`: `rdfs:label` (1), `fair2r:modelVersion` (1).

SHACL-required on `fair2r:Claim`: `prov:wasGeneratedBy → Activity` (exactly 1
— **THE invariant**), `fair2r:verificationState` (1), `fair2r:claimText` (1).

Operationally added (not SHACL-required): `prov:endedAtTime`, `dcterms:date`,
`prov:qualifiedAssociation` (carries role+plan, PROV-O Level 2),
`prov:wasAttributedTo` (≥1 — both AI + human on every generated entity),
`prov:actedOnBehalfOf` (AI → human, per F(AI)²R reference TTL),
`fair2r:modelFamily` (optional but useful for cross-vendor SPARQL filters).

**When to use `fair2r:Claim` vs plain `prov:Entity`:** v15 is mostly a file
mover. Type DataObjects as `fair2r:Claim` ONLY when the AI interpreted something
(inferred frame number, classified a defect). Pure "file at path X" imports
stay as `prov:Entity` to avoid `fair2r:claimText` friction. Mixed batches are
fine — the `prov:generated` list can contain both kinds.

---

## New shepard: predicates proposed

F(AI)²R is vocabulary-thin by design. v15 needs operational metadata it doesn't
provide. Mint in `shepard:` upper-ontology namespace; declare via a Cypher
migration that also extends `shepard-core-shapes.ttl`:

| Predicate | Domain → Range | rdfs:label | Purpose |
|---|---|---|---|
| `shepard:targetCollection` | Activity → Collection | "target collection" | Scope filter for SPARQL ("all imports into Coll-515365"). |
| `shepard:filesUploaded` | Activity → xsd:nonNegativeInteger | "files uploaded" | Additive count of FileContainer payloads. |
| `shepard:timeseriesImported` | Activity → xsd:nonNegativeInteger | "timeseries points imported" | Throughput aggregate. |
| `shepard:structuredPayloads` | Activity → xsd:nonNegativeInteger | "structured-data payloads imported" | |
| `shepard:batchSequence` | Activity → xsd:nonNegativeInteger | "batch sequence number" | Monotonic per-(script, source); gap = missed batch. |
| `shepard:throughputBytesPerSec` | Activity → xsd:double | "throughput (B/s)" | Observability. |
| `shepard:retryCount` | Activity → xsd:nonNegativeInteger | "retry count" | HTTP retries; non-zero = backpressure signal. |
| `shepard:sourceInstance` | Activity → xsd:string | "source instance identifier" | **Multi-OID collision indicator** — partition key for cross-instance SPARQL UNIONs. |
| `shepard:role-executor` | individual `a prov:Role` | "executor" | For `prov:hadRole` on AI agent's Association. |
| `shepard:role-operator` | individual `a prov:Role` | "operator (responsible party)" | For human's Association. |

---

## Verification ladder integration

| Phase | State | Trigger | Activity that promotes it |
|---|---|---|---|
| Batch import (default) | `verif:unverified` | v15 emits Claim with `wasGeneratedBy` → AuthoringPass | (the AuthoringPass itself) |
| Schema/SHACL conformance passes | stays `verif:unverified` | SHACL validation green | n/a (conformance is structural, not epistemic) |
| Downstream `shepard-plugin-ai` confirms | `verif:ai-confirmed` | Auto-annotation cross-checks | a new `fair2r:AuditPass` Activity referencing the Claim via `fair2r:auditedEntity` + `fair2r:auditOutcome "confirmed"` |
| Human reviewer accepts in UI | `verif:human-confirmed` | One-click promotion in TPL9f ladder widget | a fresh `prov:Activity` with `fair2r:repairs` edge — the Claim's content stands; only its verification state advances |
| Anchored to immutable ledger (TPL10) | `verif:ledger-anchored` | Notary-anchor txn | a `fair2r:AuditPass` Activity citing the anchor TXID |

v15 must NEVER write anything above `verif:unverified`. The whole point of
"AI-imported = unverified by default" is the gate is human attention.

---

## Gotchas the script must handle

1. **Activity IRI uniqueness across retries.** Pre-mint UUID v7 once; persist
   to `./mffd-import.journal.ndjson`; reuse on retry. n10s dedupes by `r:Resource.uri`.
   Bump `shepard:retryCount` instead of re-minting.

2. **Emit the PROV fragment LAST.** If a DataObject POST fails mid-batch but the
   Activity emit succeeds, `prov:generated` points to a 404. Enumerate only
   already-created entities. Shepard's PROV1a filter captures the bare per-POST
   Activity anyway — the v15 fragment is the narrative summary, not the only record.

3. **n10s parse failures are silent.** Import returns `triplesLoaded: N` without
   reporting rejects. Check `triplesLoaded == triplesEmitted` and log a warning.
   SHACL validation (per `aidocs/95`) gives the real diagnostic.

4. **n10s `Resource` label leakage — highest-risk integration concern.** Per
   `aidocs/48 §3.3`, ontology resources carry `:Resource`; domain entities don't.
   When v15 emits a `do:<appId>` IRI that already corresponds to a `:DataObject`
   node, n10s may create a duplicate `:Resource` shadow rather than merging onto
   the LPG entity. Verify `POST /v2/semantic/{repoAppId}/import` merges by `appId`.
   If unsure, emit only the Activity + qualifiedAssociation + *external* IRIs
   (script, agent) — let PROV1a handle `wasGeneratedBy` edges on DataObjects
   natively in the LPG graph.

5. **Multi-instance IRI collisions.** Two instances importing the same source
   produce Activity IRIs under each instance's `base-url`. Use
   `shepard:sourceInstance` as the cross-instance partition key.

6. **Clock skew.** `prov:startedAtTime` is v15's wall clock; PROV1a uses server
   clock. NTP-bounded skew (<100ms typical). SPARQL joins between the two
   Activity streams must tolerate a few seconds.

7. **Empty batches.** Zero `prov:generated` violates SHACL `minCount 1`. If the
   5-min timer fires with no new entities, skip emitting.

8. **Agent declaration noise.** Emit `agent:claude-opus-4-7 a fair2r:AIAgent`
   once at v15 startup (bootstrap fragment); per-batch fragments reference by
   IRI only.

9. **Schema-growth discipline.** New `shepard:*` predicates (`filesUploaded` etc.)
   land via a Cypher migration that also extends `shepard-core-shapes.ttl`.
   Without it, a SHACL-closure-enabled gateway rejects v15's fragments.

10. **Large batches.** 100-DO batch ≈ 30 KB Turtle (fine). If batch size grows
    >200 entities, split into multiple Activity fragments joined by
    `dcterms:isPartOf` to a parent "run" Activity.
