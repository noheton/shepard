---
layout: default
title: "Runbook — Orphan retention policy (SM1a)"
description: "Operator runbook for configuring orphan-data retention windows, recovering containers from delete-on-next-sweep state, and querying the retention audit trail."
stage: feature-defined
last-stage-change: 2026-05-27
audience: instance-admin
---

# Orphan retention policy (SM1a)

> **Note:** SM1a storage management is partially shipped. Sections marked
> **[FUTURE]** apply once SM1a is fully deployed (per the SM1a-RUNBOOK backlog
> entry in `aidocs/16-dispatcher-backlog.md`). Today, Shepard's integrity rule
> is: **referenced data is never deleted; orphan accumulation is by design until
> SM1a ships.** No retention sweep fires on a pre-SM1a instance.

**When to use this runbook:**

- You want to set (or change) how long non-referenced payload data is kept before
  the system schedules deletion — or disable automatic deletion entirely.
- **[FUTURE]** A container has been flagged for deletion on the next sweep and you
  want to recover it before the sweep runs.
- **[FUTURE]** You want to audit what was deleted by the retention sweep and when.
- **[FUTURE]** You want to opt the entire instance out of automatic orphan cleanup.

This runbook covers timeseries channels (TimescaleDB), file payloads (Garage/S3),
and structured-data payloads (MongoDB/PostgreSQL) — all three substrate orphan types
share the same SM1a policy surface.

The INTEGRITY rule from `feedback_referenced_data_infinite_retention.md` is honored
throughout: data that has any live reference **is never touched** by the retention
policy. Only data satisfying `refs = 0 AND payload_size > 0 AND not_in_active_job`
enters the retention clock.

---

## 0. Pre-flight

Verify you have the `instance-admin` role and a current API key:

```bash
# [operator-machine]
curl -fsS -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  "${SHEPARD_URL}/v2/admin/features" | jq '.[] | select(.id == "SM1a")'
```

Expected (pre-SM1a): empty result or the feature row with `"enabled": false`.

**[FUTURE]** Once SM1a ships, the feature row will read:

```json
{
  "id": "SM1a",
  "enabled": true,
  "description": "Orphan retention policy with configurable per-container windows"
}
```

If the feature row is absent or `enabled: false`, the retention sweep is not running.
All **[FUTURE]** sections below are no-ops until SM1a is enabled.

---

## 1. Set the instance default retention window

The instance default controls how long orphan data lives on any container that has
not been given a per-container override (see §2). The knob is exposed at:

```
PATCH /v2/admin/storage/retention/config
```

**[FUTURE]** Runtime configuration (SM1a shipped):

```bash
# [operator-machine]
# Read current default
curl -fsS -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  "${SHEPARD_URL}/v2/admin/storage/retention/config" | jq .

# Set instance default to 365 days (1 year — the SM1a default)
curl -fsS -X PATCH \
  -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/merge-patch+json" \
  "${SHEPARD_URL}/v2/admin/storage/retention/config" \
  -d '{"instanceDefaultDays": 365}'

# Set instance default to 90 days
curl -fsS -X PATCH \
  -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/merge-patch+json" \
  "${SHEPARD_URL}/v2/admin/storage/retention/config" \
  -d '{"instanceDefaultDays": 90}'
```

**[FUTURE]** CLI parity (L1 baseline):

```bash
# [operator-machine]
shepard-admin storage retention config show
shepard-admin storage retention config set instance-default-days 365
```

The `PATCH` is captured by `ProvenanceCaptureFilter` (PROV1a) — the change appears in
the audit trail with the admin's username, timestamp, and the before/after values.

**Today (pre-SM1a):** No `PATCH /v2/admin/storage/retention/config` endpoint exists.
To document your intended default for when SM1a ships, add a comment to
`infrastructure/.env`:

```bash
# [operator-machine]
echo "# SM1a orphan retention: set to 365 days on SM1a deployment" \
  >> /opt/shepard/infrastructure/.env.local
```

---

## 2. Per-container retention override

**[FUTURE]** Each container (timeseries, file, structured-data) has an
`orphan_retention_days` field:

| Value | Meaning |
|-------|---------|
| `NULL` | Use the instance default (§1) |
| `-1` | Infinite retention — never auto-delete orphans on this container |
| `0` | Delete on next sweep — minimal grace |
| `N > 0` | Keep orphans for N days |

Set a per-container override:

```bash
# [operator-machine]
# Make a specific container retain orphans for 2 years (e.g. a sensitive archive)
CONTAINER_APP_ID="01HX..."
curl -fsS -X PATCH \
  -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/merge-patch+json" \
  "${SHEPARD_URL}/v2/timeseries-containers/${CONTAINER_APP_ID}/retention" \
  -d '{"orphanRetentionDays": 730}'

# Set a sandbox container to delete orphans on next sweep
curl -fsS -X PATCH \
  -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/merge-patch+json" \
  "${SHEPARD_URL}/v2/timeseries-containers/${CONTAINER_APP_ID}/retention" \
  -d '{"orphanRetentionDays": 0}'

# Restore to use the instance default (clear the override)
curl -fsS -X PATCH \
  -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/merge-patch+json" \
  "${SHEPARD_URL}/v2/timeseries-containers/${CONTAINER_APP_ID}/retention" \
  -d '{"orphanRetentionDays": null}'
```

The same `PATCH` endpoint exists for `/v2/file-containers/{id}/retention` and
`/v2/structured-data-containers/{id}/retention`.

---

## 3. Nag cadence (notifications before deletion)

**[FUTURE]** When SM1a + NTF1 (notifications) are both deployed, the system sends
nag warnings to `instance-admin` users before orphan deletion fires:

| Days before deletion | Notification |
|---|---|
| 30 | "Container X has N orphan payloads scheduled for deletion in 30 days" |
| 7 | Same message; higher urgency |
| 1 | "Deletion fires tomorrow — recover now or it will be permanent" |
| (on deletion) | "N items deleted from container X; Y bytes reclaimed" |

Notifications are delivered via the configured NTF1 channels (in-app + email opt-in).
The notification subject identifies the container by name and `appId`.

**Today (pre-SM1a):** No nag notifications exist. Orphan data accumulates silently.
To proactively audit orphan accumulation today, use the storage overview endpoint:

```bash
# [operator-machine]
curl -fsS -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  "${SHEPARD_URL}/v2/admin/storage-overview" | jq '{
    timescale: {
      hypertableSizeBytes: .timescale.hypertableSizeBytes,
      channelCount: .timescale.channelCount
    },
    mongo: {
      storageSizeBytes: .mongo.storageSizeBytes,
      collections: .mongo.collections
    }
  }'
```

This tells you overall substrate sizes but not the orphan slice. The stale-channels
admin tool (`aidocs/data/89-stale-channel-admin-design.md`, ADMIN-STALE-CH backlog
row) provides the finer-grained orphan breakdown when shipped.

---

## 4. Recover a container from "delete-on-next-sweep" state

**[FUTURE]** If you or a researcher set `orphanRetentionDays: 0` (or if the
retention clock has reached zero), orphan data on that container is scheduled for
the next sweep run. The sweep runs nightly. You have until the sweep fires to cancel
the deletion.

### Check whether a container is in sweep-pending state

```bash
# [operator-machine]
CONTAINER_APP_ID="01HX..."
curl -fsS -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  "${SHEPARD_URL}/v2/timeseries-containers/${CONTAINER_APP_ID}/retention" \
  | jq '{orphanRetentionDays, sweepScheduledAt, orphanCount}'
```

If `orphanRetentionDays = 0` and `sweepScheduledAt` is set to a near-future
timestamp, the container is in sweep-pending state.

### Cancel the sweep (extend retention)

```bash
# [operator-machine]
# Give yourself 30 more days to decide
curl -fsS -X PATCH \
  -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/merge-patch+json" \
  "${SHEPARD_URL}/v2/timeseries-containers/${CONTAINER_APP_ID}/retention" \
  -d '{"orphanRetentionDays": 30}'
```

Setting any non-zero value clears the sweep-pending flag and resets the clock.

### Verify the sweep was cancelled

```bash
# [operator-machine]
curl -fsS -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  "${SHEPARD_URL}/v2/timeseries-containers/${CONTAINER_APP_ID}/retention" \
  | jq '{orphanRetentionDays, sweepScheduledAt}'
# Expected: sweepScheduledAt: null
```

### Neo4j audit check (post-sweep, if you're investigating after the fact)

**[FUTURE]** After a sweep fires, verify on Neo4j which containers were affected:

```bash
# [nuclide]
docker exec infrastructure-neo4j-1 cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (a:Activity)
   WHERE a.actionKind = 'DELETE'
     AND a.targetKind IN ['Timeseries', 'FilePayload', 'StructuredDataPayload']
     AND a.summary CONTAINS 'RetentionSweep'
   RETURN a.appId, a.agentUsername, a.targetAppId, a.targetKind, a.summary,
          datetime({epochMillis: a.startedAtMillis}) AS startedAt
   ORDER BY a.startedAtMillis DESC
   LIMIT 20;"
```

This query searches for `EXECUTE` / `DELETE` activities with `summary CONTAINS
'RetentionSweep'`. On a fully-shipped SM1a instance that uses the designed
`sourceKind` property, the canonical query from the spec is:

```cypher
// [nuclide] — SM1a full deployment
MATCH (a:Activity {sourceKind: 'RetentionSweep'})
RETURN a.appId, a.agentUsername, a.targetAppId, a.targetKind,
       datetime({epochMillis: a.startedAtMillis}) AS startedAt,
       a.summary
ORDER BY a.startedAtMillis DESC
LIMIT 50;
```

> **Note:** The `sourceKind` property lands with SM1a. On a pre-SM1a instance this
> query returns zero rows — use the `summary CONTAINS 'RetentionSweep'` fallback
> above for forensics on the pre-SM1a code path.

**What the results mean:**

| Field | Meaning |
|-------|---------|
| `appId` | This retention-sweep activity's own UUID — cite this when filing a support issue |
| `agentUsername` | The system account that ran the sweep (`_shepherd_system` or `instance-admin`) |
| `targetAppId` | The `appId` of the deleted payload or channel |
| `targetKind` | `Timeseries` / `FilePayload` / `StructuredDataPayload` |
| `startedAt` | When the sweep ran |
| `summary` | Human-readable summary including container name, retention window, bytes reclaimed |

---

## 5. Full audit-trail query (last 30 days of sweep activity)

**[FUTURE]** Pull a complete log of all SM1a sweep events for the last 30 days:

```bash
# [nuclide]
CUTOFF=$(python3 -c "import time; print(int((time.time() - 30*86400) * 1000))")
docker exec infrastructure-neo4j-1 cypher-shell -u neo4j -p "${NEO4J_PASSWORD}" \
  "MATCH (a:Activity)
   WHERE a.summary CONTAINS 'RetentionSweep'
     AND a.startedAtMillis >= ${CUTOFF}
   OPTIONAL MATCH (a)-[:WAS_ASSOCIATED_WITH]->(u)
   RETURN a.appId, a.targetKind, a.targetAppId, a.summary,
          datetime({epochMillis: a.startedAtMillis}) AS startedAt,
          u.username AS triggeredBy
   ORDER BY a.startedAtMillis DESC;"
```

To count bytes reclaimed per container, parse the `summary` field — SM1a populates it
in the form:

```
RetentionSweep: container <appId> (<name>) — deleted N channels / M files, reclaimed X bytes
```

**[FUTURE]** REST endpoint equivalent:

```bash
# [operator-machine]
curl -fsS -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  "${SHEPARD_URL}/v2/admin/storage/retention/history?days=30" | jq .
```

---

## 6. Opt out entirely (infinite retention)

To disable automatic orphan cleanup for the entire instance, set the instance
default to `-1` (infinite).

**[FUTURE]**

```bash
# [operator-machine]
curl -fsS -X PATCH \
  -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/merge-patch+json" \
  "${SHEPARD_URL}/v2/admin/storage/retention/config" \
  -d '{"instanceDefaultDays": -1}'
```

**[FUTURE]** CLI:

```bash
# [operator-machine]
shepard-admin storage retention config set instance-default-days infinite
```

With `instanceDefaultDays: -1`, the sweep scheduler still runs nightly but skips
every container whose effective retention value resolves to `-1`. The sweep log shows:

```
RetentionSweep: skipped container <appId> (<name>) — infinite retention configured
```

**Deploy-time seed (any state):** If you want the instance to start with infinite
retention before SM1a's runtime PATCH endpoint is available, set in
`infrastructure/.env` (or your IaC):

```bash
# infrastructure/.env
SHEPARD_STORAGE_ORPHAN_DEFAULT_RETENTION_DAYS=-1
```

This value seeds the `:StorageRetentionConfig` singleton on first start. A later
runtime `PATCH` overrides it. Both are honored per the CLAUDE.md "runtime value
wins; deploy-time key is the install default" precedence rule.

---

## 7. Rollback

### Undo a retention config change (runtime PATCH)

**[FUTURE]** The `ProvenanceCaptureFilter` records every `PATCH` to `:Activity` with
before/after values. To reverse a change, PATCH the old value back:

```bash
# [operator-machine]
# Example: revert from 90 days back to 365
curl -fsS -X PATCH \
  -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  -H "Content-Type: application/merge-patch+json" \
  "${SHEPARD_URL}/v2/admin/storage/retention/config" \
  -d '{"instanceDefaultDays": 365}'
```

The revert itself lands in `:Activity` as a new `UPDATE` event, so the full history
of "who changed what and when" is preserved.

### Recover soft-deleted orphan data within the grace window

**[FUTURE]** SM1a uses soft-delete (`deleted_at TIMESTAMPTZ` on the affected tables)
with a 30-day hard-delete window. During that window, data can be recovered:

```bash
# [operator-machine]
ORPHAN_SHEPARD_ID="01HX..."
curl -fsS -X POST \
  -H "Authorization: Bearer ${INSTANCE_ADMIN_API_KEY}" \
  "${SHEPARD_URL}/v2/admin/storage/retention/recover" \
  -d "{\"shepardIds\": [\"${ORPHAN_SHEPARD_ID}\"], \"reason\": \"Incorrect deletion — re-attaching to DataObject TR-004\"}"
```

After 30 days, the background hard-delete job runs and recovery returns `410 Gone`.
At that point, the only recovery path is a PITR backup restore (see
`docs/admin/runbooks/05-restore-timescaledb.md`).

---

## Versions / references

- **This runbook** — `docs/admin/runbooks/orphan-retention-policy.md` (written 2026-05-27)
- **SM1a backlog row** — `aidocs/16-dispatcher-backlog.md §SM1a` (queued; design captured)
- **SM1a-RUNBOOK backlog row** — `aidocs/16-dispatcher-backlog.md §SM1a-RUNBOOK`
- **Stale-channels admin design** — `aidocs/data/89-stale-channel-admin-design.md`
  (the first concrete SM1 shippable, ADMIN-STALE-CH row)
- **API3 container safe-delete design** — `aidocs/platform/API3-container-safe-delete-design.md`
  (SM1 `onContainerDeleted` hook shape)
- **Storage overview endpoint** — `backend/src/main/java/de/dlr/shepard/v2/admin/resources/AdminStorageOverviewRest.java`
  (today's pre-SM1a capacity read)
- **Provenance retention job** — `backend/src/main/java/de/dlr/shepard/provenance/services/ProvenanceRetentionJob.java`
  (the currently-shipped nightly job — covers `:Activity` rows, not payload data)
- **INTEGRITY rule** — `feedback_referenced_data_infinite_retention.md`
  (referenced data is never deleted)
- **Sibling runbook** — `docs/admin/runbooks/restore-tsdb-container-neo4j-shadow.md`
  (recovery for the case where TimescaleDB has rows but Neo4j lost the shadow node)

## When to graduate this runbook

Mark `stage: deployed` and remove all `[FUTURE]` tags when:

1. `POST /v2/admin/storage/retention/config` is live and gated on `instance-admin`.
2. The SM1a sweep job runs nightly and records to `:Activity`.
3. The soft-delete + 30-day hard-delete window is shipped and tested.
4. The stale-channels admin tool (ADMIN-STALE-CH) is deployed.

Until then, §0 (check feature toggle) and §3 (storage overview as a proxy) are the
only sections applicable to a running instance.
