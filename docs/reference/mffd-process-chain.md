---
audience: user
---

# MFFD process-chain mapping reference

The MFFD process-chain mapping feature lets an instance admin apply a YAML
file that wires cross-process `has_successor` edges onto the Neo4j graph,
stitching the MFFD upper-shell digital thread across process boundaries that
the cube3 export tool does not preserve.

## 1. Why this exists

The MFFD upper-shell manufacturing chain at ZLP Augsburg consists of four
distinct processes — AFP tape layup, stringer / frame welding, NDT
thermography, and LBR cleat installation. Each process is imported into its
own Shepard Collection. The `mffd-export` tool preserves Predecessor edges
*within* each Collection (Track N → Track N+1 inside the AFP run) but does
**not** carry the cross-Collection links: which AFP track went into which
weld assembly, which weld passed which NDT campaign, which NDT-cleared
assembly received which cleats.

The mapping YAML is authored by the domain expert (who holds that knowledge)
and applied once via this admin endpoint. Every re-apply is idempotent.

## 2. YAML schema

```yaml
schemaVersion: 1         # currently the only supported version

mappings:
  - source:              # AND-matched against SemanticAnnotation predicates
      process: afp-layup
      ply_number: 5
      track_number: 244
    target:
      process: bridge-welding
      part_name: AF_3
    transitionKind: normal     # normal | rework | re-test | concession (default: normal)

  - source:
      process: ndt-thermography
      campaign_id: P_K_FAIL
    target:
      process: bridge-welding
      part_name: AF_3
    transitionKind: rework     # NDT FAIL → return to bridge welding
```

### 2.1 Selector key → predicate mapping

Each selector key in the YAML is resolved to a `urn:shepard:mffd:*`
semantic annotation predicate. The mapping is:

| YAML key       | Predicate URI                                |
|----------------|----------------------------------------------|
| `process`      | `urn:shepard:mffd:process-type`              |
| `step_number`  | `urn:shepard:mffd:step-number`               |
| `ply_number`   | `urn:shepard:mffd:ply-number`                |
| `track_number` | `urn:shepard:mffd:track-number`              |
| `part_name`    | `urn:shepard:mffd:part-name`                 |
| `campaign_id`  | `urn:shepard:mffd:campaign-id`               |
| `cleat_id`     | `urn:shepard:mffd:cleat-id`                  |
| *(any other)*  | `urn:shepard:mffd:` + key with `_` → `-`    |

The fallback auto-hyphenation means adding a new MFFD predicate requires
no loader code change.

All predicates in a selector are **AND-ed**: only DataObjects that carry
all listed predicates with matching values are selected.

### 2.2 `transitionKind` values

| Value        | Use                                                      |
|--------------|----------------------------------------------------------|
| `normal`     | Standard process-to-process transition (default)         |
| `rework`     | Quality reject → returned to earlier step                |
| `re-test`    | Post-rework NDT or functional re-validation              |
| `concession` | Used-as-is with a documented concession / waiver         |

`transitionKind` is stored as a property on the `has_successor` Neo4j
relationship and filters the `urn:shepard:mffd:*` lineage query surface.

## 3. REST endpoint

```
POST /v2/admin/mffd/process-chain-mapping
```

- **Auth**: `instance-admin` role required (`X-API-Key` or Keycloak bearer).
- **Content-Type**: `application/yaml`, `text/yaml`, or `text/plain`.
- **Response**: `application/json` with the result envelope (see §4).
- **Idempotent**: re-posting the same YAML converges; mutating
  `transitionKind` in an existing entry updates the relationship property.

### 3.1 Worked example — curl

```bash
curl -X POST \
  -H "Content-Type: application/yaml" \
  -H "X-API-Key: ${ADMIN_KEY}" \
  --data-binary @path/to/mapping.yaml \
  https://shepard.example.org/v2/admin/mffd/process-chain-mapping
```

### 3.2 Response envelope

```json
{
  "schemaVersion": 1,
  "entries": 15,
  "matched": 38,
  "unmatched": 1,
  "edgesCreated": 38,
  "unresolved": [
    {
      "line": 142,
      "side": "target",
      "reason": "No DataObjects match all selector predicates."
    }
  ],
  "warnings": []
}
```

| Field          | Description                                                              |
|----------------|--------------------------------------------------------------------------|
| `entries`      | Number of mapping entries in the YAML                                    |
| `matched`      | Number of entries where both source and target resolved to ≥ 1 DataObject|
| `unmatched`    | Entries where at least one side had zero matches                         |
| `edgesCreated` | `has_successor` edges MERGEd (0 for already-existing edges)              |
| `unresolved`   | One row per unresolved selector: `line` (YAML line), `side`, `reason`   |
| `warnings`     | Non-blocking issues (e.g. a Cartesian product unusually large)           |

### 3.3 Error responses

| Status | Condition                                                  |
|--------|------------------------------------------------------------|
| `400`  | Malformed YAML or unsupported `schemaVersion`; RFC 7807 body |
| `403`  | Caller lacks `instance-admin` role                         |

## 4. Offline validator

Before applying, run the validator to catch typos without mutating data:

```bash
# Live validation — reads SemanticAnnotations via the REST surface
python3 scripts/validate-mffd-process-chain-mapping.py \
    --url https://shepard.example.org/v2 \
    --api-key "${ADMIN_KEY}" \
    path/to/mapping.yaml

# Offline structural-only validation (no REST calls)
python3 scripts/validate-mffd-process-chain-mapping.py \
    --offline path/to/mapping.yaml
```

Unresolved selectors are **not** a non-zero exit — the checklist is the
deliverable. Only structural YAML errors fail the exit code.

An example YAML with all four `transitionKind` values lives at
`scripts/mffd-process-chain-mapping.example.yaml`.

## 5. Admin UI

1. Sign in as an instance admin.
2. Navigate to `/admin` → tile **MFFD process-chain mapping**.
3. Upload a YAML file or paste directly into the textarea.
4. Click **Apply mapping**.
5. The page displays the counters and, if any, the unresolved checklist.

The **Download example YAML** button in the top-right corner fetches
`scripts/mffd-process-chain-mapping.example.yaml` from the static assets.

## 6. Loader semantics and idempotency

For each mapping entry the loader:

1. Queries Neo4j for all DataObjects matching the `source` selector
   (AND over all `urn:shepard:mffd:*` annotations).
2. Queries Neo4j for all DataObjects matching the `target` selector.
3. For each `(source, target)` pair in the Cartesian product, executes:

   ```cypher
   MERGE (s)-[r:has_successor {transitionKind: $kind, createdBySource: 'mffd-process-chain-mapping'}]->(t)
   ON CREATE SET r.createdAtMillis = $now
   ON MATCH SET  r.transitionKind  = $kind
   ```

The MERGE is safe to re-run: existing edges are updated in-place, not
duplicated. The loader **never deletes** edges. To remove a previously
created edge, use the DataObject detail UI's Predecessor edit, or run a
targeted Cypher delete from `cypher-shell`:

```cypher
MATCH ()-[r:has_successor {createdBySource: 'mffd-process-chain-mapping'}]-()
WHERE r.createdAtMillis >= $sinceMillis AND r.createdAtMillis <= $untilMillis
DELETE r;
```

## 7. Audit trail

Every successful apply records a single `:Activity` node:

- `actionKind = EXECUTE`
- `targetKind = MffdProcessChainMapping`
- `summary` = `"Applied MFFD process-chain mapping (schemaVersion=…, entries=…, matched=…, unmatched=…, edgesCreated=…)"`
- `agentUsername` = calling admin

The Activity is queryable via:

```
GET /v2/provenance/activities?targetKind=MffdProcessChainMapping
```

or visible on the `/admin/provenance` activity dashboard.

## 8. Troubleshooting

| Symptom | Cause / fix |
|---------|-------------|
| `400` "Unsupported schemaVersion=N" | Set `schemaVersion: 1` |
| `400` "YAML parse error: …" | Fix the YAML; the error carries the parser's line context |
| Many entries unresolved on both sides | DataObjects not yet imported, or carry different predicate values than the YAML expects. Run the offline validator against live data first |
| `edgesCreated` < `matched` | Those pairs already had a matching edge — re-run is a no-op (expected) |
| `403 Forbidden` | Caller lacks `instance-admin`. Grant via `/admin` → Instance admins |

## 9. Related docs

- Design doc: `aidocs/integrations/118-mffd-process-chain-mapping.md`
- Import plan: `aidocs/integrations/113-mffd-real-data-import-plan.md`
- Collection layout: `aidocs/integrations/119-mffd-collection-layout.md`
- Operator runbook: `docs/admin/runbooks/mffd-process-chain-mapping.md`
