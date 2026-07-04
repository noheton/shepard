---
name: MFFD process-chain mapping
description: YAML-authored cross-process Predecessor edges (tapelaying → bridgewelding → NDT → cleats) materialised by a Shepard admin loader. Closes GAP-4.
type: design
stage: feature-defined
last-stage-change: 2026-06-02
---

# MFFD process-chain mapping (118)

Companion to `aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md §GAP-4`
and `aidocs/integrations/113-mffd-real-data-import-plan.md` (the import plan
this unblocks at wave **W4**).

## 1. Problem

The MFFD process chain is

```
tapelaying → bridgewelding → NDT thermography → cleats
```

The `mffd-export` cube3 tool preserves Predecessor edges *within* each
process (Track-N predecessor-of Track-N+1 inside `tapelaying`). It does
**not** preserve the cross-process edges — which tape tracks went into
ply N of AF_3, which AF_M became which NDT thermography session, which
weldment carries cleat L.

That mapping lives only in flo's domain knowledge. Without it, the
MFFD digital thread breaks at every process boundary in the UI.

GAP-4 in the 2026-06-02 gap-analysis flagged this as the highest-value
infrastructure unlock — once the loader exists, a single afternoon of
flo authoring the mapping turns the four disconnected sub-graphs into
a coherent chain.

## 2. Shape

A single YAML file maps **source DataObjects** to **target
DataObjects** via the `urn:shepard:mffd:*` semantic-annotation
predicates each side already carries (per V100 template seeds).

```yaml
# MFFD process-chain mapping — schema version 1
schemaVersion: 1
mappings:
  # ─ tapelaying → bridgewelding ───────────────────────────────────────────
  - source:
      process: afp-layup        # urn:shepard:mffd:process-type
      ply_number: 5             # urn:shepard:mffd:ply-number
      track_number: 244         # urn:shepard:mffd:track-number
    target:
      process: bridge-welding
      part_name: AF_3           # urn:shepard:mffd:part-name
    transitionKind: normal      # normal | rework | re-test | concession

  # ─ bridgewelding → NDT thermography ─────────────────────────────────────
  - source:
      process: bridge-welding
      part_name: AF_3
    target:
      process: ndt-thermography
      campaign_id: P_K          # urn:shepard:mffd:campaign-id
    transitionKind: normal

  # ─ NDT thermography → cleats (or rework) ────────────────────────────────
  - source:
      process: ndt-thermography
      campaign_id: P_K
    target:
      process: cleats
      cleat_id: L_12            # urn:shepard:mffd:cleat-id
    transitionKind: normal

  - source:
      process: ndt-thermography
      campaign_id: P_K_FAIL
    target:
      process: bridge-welding
      part_name: AF_3
    transitionKind: rework      # NDT FAIL → return to weld
```

### 2.1 Selector keys

The selector vocabulary is **the set of `urn:shepard:mffd:*`
predicates already seeded by V100 templates** (process-type,
step-number, ply-number, track-number, part-name, campaign-id,
cleat-id, …).  No new ontology terms are introduced; the loader
just runs an `AND`-over-predicates match against
`SemanticAnnotation.propertyIRI`.

Mapping the YAML key to the predicate URI is a convention:

| YAML key       | Predicate URI                             |
|----------------|-------------------------------------------|
| `process`      | `urn:shepard:mffd:process-type`           |
| `step_number`  | `urn:shepard:mffd:step-number`            |
| `ply_number`   | `urn:shepard:mffd:ply-number`             |
| `track_number` | `urn:shepard:mffd:track-number`           |
| `part_name`    | `urn:shepard:mffd:part-name`              |
| `campaign_id`  | `urn:shepard:mffd:campaign-id`            |
| `cleat_id`     | `urn:shepard:mffd:cleat-id`               |

(Any other `*_*` key is converted via `key → "urn:shepard:mffd:" +
key.replace("_", "-")`, so adding a new predicate is zero-code on the
loader side.)

### 2.2 `transitionKind`

The edge property is one of `normal | rework | re-test | concession`,
matching the QM1b (AAA2) vocabulary so this PR and AAA2 absorb each
other in either merge order. Default when omitted: `normal`.

## 3. Loader semantics

The loader is implemented in Java in `MffdProcessChainMappingService`
(under `de.dlr.shepard.v2.admin.mffd.services`). For each mapping
entry:

1. Match **source** DataObjects via:

   ```cypher
   MATCH (d:DataObject)
   WHERE all(key IN keys($source) WHERE EXISTS {
     MATCH (a:SemanticAnnotation {
       subjectAppId: d.appId,
       subjectKind: 'DataObject',
       propertyIRI: $source[key].iri
     }) WHERE a.valueName = $source[key].value
   })
   RETURN d
   ```

2. Match **target** DataObjects the same way.

3. For each (source × target) Cartesian, MERGE the edge:

   ```cypher
   MATCH (s:DataObject {appId: $sAppId}), (t:DataObject {appId: $tAppId})
   MERGE (s)-[r:has_successor]->(t)
   ON CREATE SET r.transitionKind = $kind, r.createdAtMillis = timestamp(),
                 r.createdBySource = 'mffd-process-chain-mapping'
   ON MATCH  SET r.transitionKind = $kind, r.updatedAtMillis = timestamp()
   ```

   The MERGE is idempotent — re-running the same YAML keeps the
   edge set stable; rerunning a *changed* YAML updates the
   `transitionKind` on existing edges and adds new ones.

4. Return per-entry `{matched, unmatched, edgesCreated}` counters
   plus an `unresolved[]` checklist of YAML line numbers whose
   selectors hit zero DataObjects (typo, not-yet-imported, or
   missing annotation).

The loader **never deletes edges**. Removing a mapping from the
YAML and re-running keeps the prior edge — operators who need to
drop an edge use the standard DataObject Predecessor edit surface
or run a one-off `MATCH ... DELETE` Cypher.

## 4. Surfaces

### 4.1 REST (admin)

```
POST /v2/admin/mffd/process-chain-mapping
  Content-Type: text/yaml | application/yaml | text/plain
  Authorization: instance-admin
  Body: the YAML payload
  Response 200: { schemaVersion, entries, matched, unmatched,
                  edgesCreated, unresolved: [{line, reason, ...}] }
  Response 400: malformed YAML or schemaVersion mismatch
  Response 403: caller lacks instance-admin
```

A `:Activity` is recorded with `targetKind = MffdProcessChainMapping`,
`edgesCreated`, `matched`, `unmatched` in the activity body so the
audit trail captures who applied which mapping when. PROV1a's
`ProvenanceCaptureFilter` is skipped via `PROP_SKIP_CAPTURE` to avoid
a duplicate generic Activity.

### 4.2 Frontend

A small placeholder admin page at `/admin/mffd-process-chain` shows
the schema, links to this design doc, and offers a YAML upload form
that POSTs to the endpoint and renders the counters / unresolved
checklist. Linked from the `/admin` hub tile grid.

### 4.3 Operator runbook (CLI / cypher-shell)

`docs/admin/runbooks/mffd-process-chain-mapping.md` describes the
two ways an operator can invoke the loader:

1. **Admin REST + curl** — same body as the page form, but
   `curl -X POST --data-binary @mapping.yaml`. The shepard-admin
   CLI (`L1`) will eventually carry a one-liner; until then curl is
   the documented path.
2. **Validation-only first pass** —
   `python3 scripts/validate-mffd-process-chain-mapping.py mapping.yaml`
   reports unresolved selectors against the live instance without
   mutating anything.

## 5. Validation script

`scripts/validate-mffd-process-chain-mapping.py` is a thin Python
companion that runs the same selector-match logic against the
live REST surface (`GET /v2/data-objects?annotation=…`) without
the loader's MERGE step. Operators use it during YAML authoring to
catch typos before applying.

Exit codes:

| Code | Meaning                                                    |
|------|------------------------------------------------------------|
| 0    | YAML parsed cleanly; all selectors resolved at least 1 DO. |
| 0    | YAML parsed cleanly; some selectors unresolved (warning).  |
| 2    | YAML malformed or schemaVersion not supported.             |
| 3    | REST surface unreachable / authentication failed.          |

Unresolved selectors are **not** a non-zero exit — flo will
frequently author the mapping before the import lands, and the
checklist itself is the deliverable. Only structural YAML errors
fail noisily.

## 6. What ships in this PR

* V105 Cypher migration adding the `has_successor.transitionKind`
  range index + rollback twin. The mapping data is **not** in the
  migration — the loader-via-REST path is the right pace layer.
* `MffdProcessChainMappingService` + `MffdProcessChainMappingRest`
  implementing the REST endpoint.
* `scripts/mffd-process-chain-mapping.example.yaml` with ~15 worked
  entries covering all four `transitionKind` values.
* `scripts/validate-mffd-process-chain-mapping.py` + pytest suite.
* `frontend/pages/admin/mffd-process-chain.vue` placeholder page +
  `/admin` tile.
* `docs/admin/runbooks/mffd-process-chain-mapping.md`.

## 7. Out of scope

* The actual MFFD mapping data — flo authors that on a YAML file
  outside the repo, runs the validator, then POSTs once via the
  admin page (or curl) at MFFD-W4 wave time.
* A UI for editing individual edges — the YAML is the authoring
  surface; for one-off edge tweaks the standard DataObject
  Predecessor edit UI applies.
* Visualisation of the resulting graph at MFFD scale — that's
  task #25 (graph rendering at scale).
* Reverse mapping (CSV import) — out-of-band, can be added later
  via a YAML-emitter alongside the validator.

## 8. Cross-references

* `aidocs/agent-findings/mffd-feature-gaps-2026-06-02.md §GAP-4` — origin
* `aidocs/integrations/113-mffd-real-data-import-plan.md` — consumer
* `aidocs/16` rows: `MFFD-AF-TRACK-MAPPING-1`, `MFFD-AF-TRACK-MAPPING-2-CYPHER`,
  `MFFD-MAPPING-REST-1`
* QM1b (AAA2 in flight) — adds the same `transitionKind` property in
  the rework/concession context; the two PRs are merge-order-safe.
* `backend/src/main/resources/neo4j/migrations/V100__Mffd_process_templates_seed.cypher` —
  upstream V100 seeds the `urn:shepard:mffd:*` predicates this mapping selects against.
