---
title: MFFD process-chain mapping
description: Apply a YAML mapping of cross-process Predecessor edges (tapelaying → bridgewelding → NDT thermography → cleats) onto a live Shepard instance.
audience: instance-admin
---

# MFFD process-chain mapping

This runbook covers the **operator-facing path** for applying a MFFD
process-chain mapping YAML file (per
[`aidocs/integrations/118-mffd-process-chain-mapping.md`](../../../aidocs/integrations/118-mffd-process-chain-mapping.md)).

The mapping creates `has_successor` edges between matching
DataObjects on the Shepard graph, materialising the cross-process
links (which tape tracks went into which AF_M, which AF_M passed
which NDT campaign, etc.) that `mffd-export` does not preserve.

## 1. Authoring the YAML

The mapping author works against the schema documented in
[aidocs/integrations/118 §2](../../../aidocs/integrations/118-mffd-process-chain-mapping.md#2-shape).
A worked example with all four `transitionKind` values lives at
[`scripts/mffd-process-chain-mapping.example.yaml`](../../../scripts/mffd-process-chain-mapping.example.yaml)
in the repository.

## 2. Validation (no mutation)

Before applying, run the offline validator to catch typos:

```bash
python3 scripts/validate-mffd-process-chain-mapping.py \
    --url https://shepard.example.org/v2 \
    --api-key <admin-key> \
    path/to/mapping.yaml
```

The validator hits the live REST surface (read-only) and reports
unresolved selectors as a checklist — it never mutates anything.
Unresolved selectors are **not** an exit-non-zero condition: the
checklist itself is the deliverable. Only structural YAML errors
fail the exit code.

To run in offline mode (structural validation only — no REST calls):

```bash
python3 scripts/validate-mffd-process-chain-mapping.py \
    --offline path/to/mapping.yaml
```

## 3. Applying the mapping

### 3.1 Via the admin UI

1. Sign in to Shepard as an instance admin.
2. Open `/admin` → tile "MFFD process-chain mapping".
3. Upload the YAML file (or paste it into the textarea).
4. Click **Apply mapping**.
5. The page renders counters (entries / matched / edges-created) and
   any unresolved checklist rows.

### 3.2 Via curl

```bash
curl -X POST \
    -H "Content-Type: application/yaml" \
    -H "X-API-Key: <admin-key>" \
    --data-binary @path/to/mapping.yaml \
    https://shepard.example.org/v2/admin/mffd/process-chain-mapping
```

The response is a JSON document:

```json
{
  "schemaVersion": 1,
  "entries": 15,
  "matched": 38,
  "unmatched": 1,
  "edgesCreated": 38,
  "unresolved": [
    { "line": 142, "side": "target", "reason": "No DataObjects match all selector predicates." }
  ],
  "warnings": []
}
```

## 4. Idempotency + re-applies

- Re-running the **same YAML** does not duplicate edges. The MERGE
  converges on the existing edge set.
- Re-running a YAML where an entry's `transitionKind` changed
  updates the property on the existing edge (no extra edge created).
- Removing an entry from the YAML and re-applying does **not**
  delete the previously created edge. The loader never deletes
  edges. To drop edges, either use the standard DataObject
  Predecessor edit UI, or run a one-off Cypher delete in the Neo4j
  shell.

## 5. Audit trail

Every successful apply records a single `:Activity` row:

- `actionKind = EXECUTE`
- `targetKind = MffdProcessChainMapping`
- `summary = "Applied MFFD process-chain mapping (schemaVersion=…, entries=…, matched=…, unmatched=…, edgesCreated=…)"`
- `agentUsername` = the calling admin's username
- HMAC-chained against the previous Activity (PR-3 tamper-evidence)

The Activity row is queryable via `GET /v2/provenance/activities?targetKind=MffdProcessChainMapping`
or visible on the `/admin/provenance` dashboard.

## 6. Troubleshooting

| Symptom                                                  | Cause / fix                                                                                                                                                       |
|----------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `400 Bad Request` with "Unsupported schemaVersion=N"     | Update the YAML to `schemaVersion: 1`.                                                                                                                            |
| `400 Bad Request` with "YAML parse error: …"             | Fix the YAML syntax. The error message carries the parser's line context.                                                                                         |
| Many entries unresolved on both source and target        | The DataObjects haven't been imported yet, or carry different `urn:shepard:mffd:*` annotation values than the YAML expects. Run the validator against live data.  |
| `edgesCreated < matched`                                 | Some (source × target) pairs already had a `has_successor` edge with the same `transitionKind` — re-applying is a no-op for those pairs (expected on a re-run).   |
| `403 Forbidden`                                          | Caller lacks the `instance-admin` role. Grant via `/admin#instance-admins`.                                                                                       |

## 7. Rolling back

To remove all edges this loader created in a given apply session:

```cypher
MATCH ()-[r:has_successor {createdBySource: 'mffd-process-chain-mapping'}]-()
WHERE r.createdAtMillis >= $sinceMillis AND r.createdAtMillis <= $untilMillis
DELETE r;
```

Run this from `cypher-shell` after taking a Neo4j snapshot per
[runbook 04 (restore Neo4j)](04-restore-neo4j.md).
