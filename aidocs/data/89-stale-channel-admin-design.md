---
title: Stale timeseries channel admin tool — orphan detection + bulk cleanup
stage: feature-defined
last-stage-change: 2026-05-23
audience: contributors, ops, admins, reviewers
companion: aidocs/16-dispatcher-backlog.md (ADMIN-STALE-CH), aidocs/platform/87-timeseries-appid-migration.md
---

# 89 — Stale timeseries channel admin tool (ADMIN-STALE-CH)

**Status:** Design · feature-defined
**Audience:** Contributors, operators, instance admins, API reviewers
**Depends on:** P-series (SQL timeseries, shipped), TS-IDa (`shepard_id` column on `timeseries`, shipped), A0 (`instance-admin` role, shipped), PROV1a (`ProvenanceCaptureFilter`, shipped), L1 admin CLI baseline (shipped)
**Pairs with:** SM1 (#74 — storage-management umbrella), API3 (container safe-delete), AD_STORE1 (`AdminStorageOverviewRest`)
**Blast radius:** ⚠ HIGH — destructive admin operation on the timeseries substrate. False positives delete real research data.

---

## CHANGELOG / substrate-correction header

The backlog wording for ADMIN-STALE-CH (`aidocs/16` row, 2026-05-23) refers
to "orphaned 5-tuples in InfluxDB but no Neo4j `TimeseriesReference`
pointing at them." That language is a historical artefact. **The current
timeseries substrate is PostgreSQL/TimescaleDB**, not InfluxDB. The
InfluxDB deployment was replaced by the P-series SQL pipeline, and the
remaining InfluxDB → TimescaleDB migration plumbing
(`backend/src/main/java/de/dlr/shepard/data/timeseries/migration/`,
`TimeseriesDataPointRepository.java:131` "Batched COPY insert used by the
InfluxDB→TimescaleDB migration tool") is operator tooling, not the live
read/write path.

The same substrate-correction header appears on
`aidocs/platform/87-timeseries-appid-migration.md` and on
`aidocs/data/12-timescaledb-performance-analysis.md`. This doc adopts
the corrected view from the start: **detection is a SQL set-difference
between `timeseries` rows and Neo4j `:Timeseries` refs**, deletion is
`DELETE FROM timeseries WHERE id = ?` (which cascades to
`timeseries_data_points` per the `ON DELETE CASCADE` constraint in
`V1.0.0__setup_timeseries_tables.sql`).

---

## §1 Why this matters

**RDM lens (primary).** A research data manager budgeting institute
storage has no way today to answer the question "what fraction of the
TimescaleDB hypertable is data nobody references?". `AdminStorageOverviewRest`
(`GET /v2/admin/storage-overview`) reports a total hypertable size — but
not the slice that is reachable from a live `:DataObject` via a
`:TimeseriesReference`. Without that distinction, two failure modes
compound silently:

1. **Storage cost drift.** FinOps practice for observability storage
   targets ~10% of infrastructure spend; surveyed organisations routinely
   spend 15–25% with no retention discipline ([TechTarget FinOps
   guide](https://www.techtarget.com/searchstorage/feature/Stop-Overpaying-for-Storage-A-FinOps-Guide-for-CIOs);
   [Maxima Consulting on observability + FinOps](https://www.maximaconsulting.com/newsroom/observability-needs-its-own-finops-strategy)).
   A Shepard instance accreting orphan channels indefinitely lands at the
   bad end of this distribution.

2. **Provenance hygiene.** A channel whose `DataObject` was deleted but
   whose backing `timeseries` row was not cascade-cleaned is a hole in
   the provenance graph — the data exists, but nothing in the metadata
   layer knows about it. F(AI)²R and FAIR both require that "data
   reachable but not referenced" be either re-attached or deleted; a
   permanent third category is not acceptable.

**Manufacturing-Quality lens (secondary).** EN 9100 audit trails require
every measurement to be traceable to a controlled artefact (test run,
NCR, lot release). A channel with no `:TimeseriesReference` is, by
definition, a measurement with no controlled artefact attached. The tool
identifies those gaps so they can be either re-attached (the original
DataObject was deleted in error) or deleted (the channel was never
controlled material — test/scratch data).

**Reluctant Senior lens (secondary).** A 28-year veteran will not adopt a
system that quietly garbage-collects data. The tool is therefore
designed as **inspect → preview → confirm → execute**, never as an
auto-sweeper. The default mode is informational. Deletion requires an
admin to read a sample of the data, see the byte-savings estimate, and
confirm.

**API Scrutinizer lens (secondary).** Every mutation is `dry-run`-able,
returns RFC 7807 problem details on failure, and follows the established
`/v2/admin/*` shape (compare `AdminStorageOverviewRest`,
`AdminFeaturesRest`, `StorageAdminRest`).

### Concrete pain points the tool addresses

- **Mid-pipeline test channels never wired up.** During the v15.x MFFD
  importer development cycles (2026-05-22), repeated dry-runs and aborted
  imports created `timeseries` rows for the `mffd_import` measurement.
  Most were eventually attached to the `mffd-import-observability`
  DataObject (OBS-MFFD1), but a v15.4 namespace (`symbolicName="v15.4"`)
  was superseded by v15.5/15.6 and the older symbolic-name rows are
  candidates for review.
- **Cascade gaps from pre-A1e DataObject deletes.** Before fail-fast
  migration discipline, a DataObject delete could fail mid-transaction
  with the Neo4j side rolled back but the Timescale side already committed.
  Audit pass needed.
- **Tooling experimentation leftovers.** Any `tsdb-cli`, smoke test, or
  benchmark that wrote into a real container leaves rows behind.

### What's explicitly out of scope

- **File-storage orphans** (Garage / GridFS objects with no
  `:FileContainer.payload` ref). Covered by a separate ADMIN-STALE-FILE
  row to be filed against SM1; the cross-substrate question shape is
  identical but the substrate is different.
- **Neo4j orphan nodes** (e.g. `:Permissions` with no owner). Covered
  by the existing `V14__orphan_permissions_backfill.cypher` migration
  and `GET /v2/admin/permission-audit`.
- **Auto-deletion / scheduled GC.** Explicitly avoided in v1. The tool
  identifies and supports admin-driven deletion; it does NOT run as a
  cron job. SM1 may add policy-driven retention later (e.g. "channels
  with no refs for 365 days → auto-delete with operator notification"),
  but that's a follow-on with a different consent model.

---

## §2 Definition of "stale"

**Lens citation: RDM + Reluctant Senior.** The acceptance ladder below
exists because both lenses reject any single binary "stale / not stale"
flag. RDM wants policy-driven categorisation for retention budgeting;
Reluctant Senior wants a "this is genuinely safe to remove" signal that
can be eyeballed before clicking delete.

### The ladder

| Bucket | Definition | Suggested action |
|---|---|---|
| **CONFIRMED-STALE** | `timeseries` row exists ∧ no `:Timeseries` ref points at the 5-tuple in any container's referenced-by chain ∧ last data point > `staleAfterDays` (default 90) old ∧ `symbolicName` matches a configurable "scratch" pattern (default: `^(scratch|tmp|test|smoke|v\d+(\.\d+)*-rc.*|wip|debug)$`). | One-step admin delete OK; UI surfaces a green "safe-delete" badge. |
| **PROBABLY-STALE** | `timeseries` row exists ∧ no `:Timeseries` ref ∧ last data point > `staleAfterDays` old ∧ does NOT match the scratch pattern. | Two-step admin delete (preview sample first); UI surfaces an amber "review-before-delete" badge. |
| **CANDIDATE** | `timeseries` row exists ∧ no `:Timeseries` ref ∧ last data point ≤ `staleAfterDays` old. | NO bulk delete. UI surfaces a blue "in-flight ingest, do not touch" badge with a "snooze for 7 days" affordance so the admin can re-evaluate after the upstream pipeline either commits or aborts. |

The thresholds live in an admin-configurable `:StaleChannelConfig`
singleton (CLAUDE.md §"surface operator knobs in the admin config" — the
A3b / N1c2 / UH1a pattern). Defaults are wired through
`application.properties` (`shepard.stale-channels.stale-after-days=90`,
`shepard.stale-channels.scratch-pattern=...`) and seeded into the
singleton on first start. Runtime PATCH wins.

### Why the scratch-pattern matters

Reluctant Senior lens: 28-year veterans use prefixes like `tmp_`,
`test_`, `wip_`, `v15.4-rc1` to mark known-disposable work. Recognising
those prefixes shifts a known-disposable channel from the PROBABLY-STALE
"review carefully" bucket into the CONFIRMED-STALE "one-click delete"
bucket, halving the cognitive load for routine cleanup.

What would change my mind: if telemetry shows that >5% of CONFIRMED-STALE
matches against the default pattern would be regretted by the admin
who ran them, the pattern stays empty by default and admins opt in. The
provenance audit log (PROV1a) is the read-out for this.

---

## §3 Cross-substrate orphan detection

**Lens citation: API Scrutinizer + Reluctant Senior + RDM.** All three
need the same property: the detection must give the same answer if
queried twice in a row with no intervening writes. That rules out
sampling, approximate counts in the hot path, and any algorithm where
the substrate gap can race.

### The algorithm

1. **Open one read-only transaction with `SET TRANSACTION ISOLATION LEVEL
   REPEATABLE READ`.** All Postgres queries in steps 2-4 share this
   snapshot. (Race-window guarantee — see §7.)
2. **Read all `timeseries` rows in the target container scope.** One row
   per (container_id, measurement, field, device, location, symbolic_name)
   tuple.
3. **For each row, fetch last/first write timestamp and approximate point
   count.** Approximate row count is fine here — admin UI display only,
   not used for the orphan decision.
4. **Resolve Neo4j refs in the same logical snapshot.** Cypher query
   collects every `(:DataObject)-[*]->(:TimeseriesReference)-[:HAS_PAYLOAD]->(:Timeseries)`
   tuple, materialises the 5-tuple per channel, returns the set.
5. **Set-difference: `timeseries_rows − referenced_5_tuples` = orphan
   candidates.**
6. **Re-verify on delete** (separate transaction at delete time): for every
   `shepard_id` the caller asked to delete, re-resolve the
   `:TimeseriesReference` set and abort the delete for any channel whose
   reference status changed between detection and deletion. See §7 for
   the rationale.

### Postgres-side query (channel registry + write-recency)

```sql
-- ADMIN-STALE-CH-a — one orphan-candidate query per container scope.
-- Runs inside a REPEATABLE READ transaction. The Cypher half of the
-- set-difference runs against the same logical snapshot.
WITH bounds AS (
  SELECT
    t.id                  AS timeseries_id,
    t.shepard_id          AS shepard_id,
    t.container_id        AS container_id,
    t.measurement, t.field, t.device, t.location, t.symbolic_name,
    t.value_type
  FROM timeseries t
  WHERE (:containerId IS NULL OR t.container_id = :containerId)
),
recency AS (
  -- One scan per channel; pushdown to the (timeseries_id, time DESC)
  -- composite index (V1.8.0 — see aidocs/data/12).
  SELECT
    timeseries_id,
    MIN(time)           AS first_write_ns,
    MAX(time)           AS last_write_ns,
    COUNT(*)            AS approx_points  -- exact within the row scope
  FROM timeseries_data_points
  WHERE timeseries_id IN (SELECT timeseries_id FROM bounds)
  GROUP BY timeseries_id
)
SELECT b.*, r.first_write_ns, r.last_write_ns, r.approx_points
FROM bounds b
LEFT JOIN recency r USING (timeseries_id)
ORDER BY r.last_write_ns NULLS FIRST, b.timeseries_id;
```

For container-scoped queries the index path on
`(timeseries_data_points.timeseries_id, time DESC)` (restored in V1.8.0
per `aidocs/data/12 §1`) makes the recency CTE cheap. For
cluster-wide queries (`:containerId IS NULL`) the cost is O(channels);
typical Shepard instances are O(10k) channels, well within
single-transaction budget.

### Neo4j-side query (live references)

```cypher
// ADMIN-STALE-CH-a — every live 5-tuple in the metadata graph.
// :Timeseries label is the OGM label of ReferencedTimeseriesNodeEntity
// (see context/references/timeseriesreference/model/ReferencedTimeseriesNodeEntity.java).
MATCH (do:DataObject)-->(:TimeseriesReference)-[:HAS_PAYLOAD]->(ts:Timeseries)
WHERE do.deleted IS NULL OR do.deleted = false
WITH DISTINCT
  ts.measurement   AS measurement,
  ts.device        AS device,
  ts.location      AS location,
  ts.symbolicName  AS symbolic_name,
  ts.field         AS field
RETURN measurement, device, location, symbolic_name, field;
```

The OGM session reads from the same logical "now" as the Postgres
transaction. Neo4j doesn't share a transaction with Postgres
(impossible without 2PC), so the implementation uses the §7 in-transaction
re-verify pattern to close the cross-substrate race.

### Set-difference (application-side)

Java pseudocode:

```java
Set<TupleKey> liveRefs = neo4jOgm.query(LIVE_REFS_QUERY).stream()
    .map(TupleKey::of)
    .collect(toSet());

List<OrphanCandidate> orphans = postgresChannels.stream()
    .filter(ch -> !liveRefs.contains(ch.tupleKey()))
    .map(ch -> annotate(ch, classify(ch, liveRefs, config)))
    .toList();
```

`classify` applies the §2 ladder. Output rows carry `shepardId` (the
single-field identifier post-PR-1) as the canonical handle for downstream
delete calls — operators do not have to round-trip the 5-tuple.

---

## §4 REST surface

All endpoints land under `/v2/admin/timeseries/stale-channels`, gated on
`@RolesAllowed(Constants.INSTANCE_ADMIN_ROLE)`, captured by
`ProvenanceCaptureFilter` (PROV1a — every admin endpoint records to
`:Activity` automatically). All shapes follow the established
`/v2/admin/*` pattern (`AdminStorageOverviewRest`, `AdminFeaturesRest`,
`StorageAdminRest`).

### Endpoint inventory

| Verb | Path | Purpose | Body / params |
|---|---|---|---|
| `GET` | `/v2/admin/timeseries/stale-channels` | Paginated list of orphan candidates with classification + recency + size. | `?containerAppId=&bucket=CONFIRMED_STALE\|PROBABLY_STALE\|CANDIDATE&staleAfterDays=&pageSize=&offset=` |
| `GET` | `/v2/admin/timeseries/stale-channels/summary` | Per-container + cluster-wide totals (bytes, channel count, bucket breakdown). Drives the SM1 / AD_STORE1 storage overview view. | `?containerAppId=` |
| `POST` | `/v2/admin/timeseries/stale-channels/preview` | Sample N points per channel from a candidate set so the admin can eyeball before committing. | Body: `{ shepardIds: [...], samplePoints: 5 }` |
| `POST` | `/v2/admin/timeseries/stale-channels/delete?dryRun=true` | **Default mode** when the param is omitted. Returns the would-delete count + summed bytes + sample rows. No mutation. | Body: `{ shepardIds: [...], reason: "free-text justification (required)" }` |
| `POST` | `/v2/admin/timeseries/stale-channels/delete?dryRun=false` | Performs the delete, transactionally, with the §3-step-6 re-verify gate. Returns the receipt: deleted shepardIds, refused shepardIds (with reasons), bytes reclaimed estimate. | Body: as above. |
| `GET` | `/v2/admin/timeseries/stale-channels/config` | Returns the `:StaleChannelConfig` singleton (defaults: `staleAfterDays=90`, `scratchPattern=...`, `requireReasonChars=20`). | — |
| `PATCH` | `/v2/admin/timeseries/stale-channels/config` | RFC 7396 merge-patch. Captured by PROV1a (who changed what knob). | Body: partial `:StaleChannelConfig` |
| `GET` | `/v2/admin/timeseries/stale-channels/history` | Last N delete operations from `:Activity` filtered to this tool's activity-type. | `?days=30` |

### Response shape (paginated list)

```json
{
  "items": [
    {
      "shepardId": "0193b8c2-...-...",
      "containerAppId": "01900000-...",
      "containerName": "MFFD import observability",
      "channel": {
        "measurement": "mffd_import",
        "device": "v15.4",
        "location": "source",
        "symbolicName": "v15.4",
        "field": "files_total"
      },
      "valueType": "Integer",
      "bucket": "CONFIRMED_STALE",
      "bucketRationale": [
        "no_live_reference",
        "last_write_age_days=121",
        "matches_scratch_pattern=v15.4"
      ],
      "firstWriteIso": "2026-01-15T12:33:01Z",
      "lastWriteIso": "2026-01-22T08:11:43Z",
      "approxPoints": 412,
      "estimatedBytes": 28672,
      "audit": {
        "lastReferencedByOnPath": null,
        "lastDeletedReferencedDataObjectAppId": null
      }
    }
  ],
  "page": { "offset": 0, "pageSize": 50, "totalCount": 173 },
  "summary": {
    "confirmedStaleCount": 84,
    "probablyStaleCount": 67,
    "candidateCount": 22,
    "estimatedBytesReclaimable": 31_415_926
  }
}
```

### Wire-shape principles (API Scrutinizer lens)

1. **Single-field identity.** `shepardId` is the canonical address.
   The 5-tuple appears nested under `channel` for human readability
   only; no endpoint requires the 5-tuple as input. This is the
   payoff from platform/87 PR-1.
2. **Reason field is mandatory** on `POST /delete`. The `reason` value
   lands in `:Activity.message` via PROV1a so "why was this deleted"
   is audit-queryable. Minimum length (default 20 chars) is config-driven.
3. **`dryRun` defaults to true.** A bare `POST /delete` with no query
   param NEVER mutates. The caller must explicitly opt in via
   `?dryRun=false`. Mirrors `gitleaks --no-commit` and `terraform plan`.
4. **Soft refusal on race.** When the §3-step-6 re-verify gate finds a
   shepardId whose reference status changed since detection, the
   endpoint does NOT 500 — it returns 200 with `refused: [{shepardId,
   reason}]` so the UI can render "skipped" rows alongside "deleted"
   rows. The caller's transaction is atomic in the sense that "every
   shepardId either deletes cleanly or is reported back" — partial
   delete failure is the expected normal case.
5. **RFC 7807 problem details** on any 4xx/5xx, per the established
   v2 pattern.
6. **No `/shepard/api/v1` surface.** This is a fork addition, not in
   upstream v5.2.0; v1 wire is frozen.

### CLI parity (per L1)

```
shepard-admin stale-channels list [--container <appId>] [--bucket confirmed-stale]
shepard-admin stale-channels preview --shepard-id <id> [--shepard-id <id> ...]
shepard-admin stale-channels delete --dry-run --shepard-id <id> ... --reason "..."
shepard-admin stale-channels delete --confirm --shepard-id <id> ... --reason "..."
shepard-admin stale-channels config show
shepard-admin stale-channels config set stale-after-days 60
shepard-admin stale-channels history --days 7
```

Shared `--output={human,json}`, `--url`, `--api-key` flags per L1
baseline. The default for `delete` is `--dry-run`; `--confirm` is the
opt-in. There is no flag that lets the CLI run without an explicit
choice.

---

## §5 UI surface

**Lens citation: Reluctant Senior + RDM.** The page is reachable from
the existing `/admin/storage/` route (the AD_STORE1 overview); it is
not surfaced on a regular user's nav. Reuse-first per
`feedback_reuse_before_reimplement.md`.

### Page

`frontend/pages/admin/storage/stale-channels.vue` — main view, routed
behind the existing `instance-admin` guard.

### Components (Vuetify v3 — reused, not new)

| Surface | Vuetify component | Why |
|---|---|---|
| Filter strip | `v-toolbar` + `v-chip-group` (bucket filter) + `v-select` (container picker) + `v-text-field` (free search on measurement/symbolicName) + `v-btn` (refresh) | Mirrors existing admin list pages. |
| Table | `v-data-table-server` with `show-select` + `v-data-table-footer` (server-side pagination — the list is potentially O(10k)) | Pagination is from-the-start, not retrofit. |
| Bucket badges | `v-chip` with color `success`/`warning`/`info` for CONFIRMED/PROBABLY/CANDIDATE | Consistent with `v-chip` use across `frontend/components/context/`. |
| Per-row expand | `v-data-table` `expanded-row` slot rendering a `TimeseriesAllChannelsChart`-like sparkline of the recent writes + last 5 sample points | Reuse the existing chart component; do not re-implement. |
| Bulk-delete flow | `v-stepper` with three steps: (1) preview, (2) confirm with reason, (3) execute + receipt | Two-step destructive-action gate is standard Vuetify pattern. |
| Result toast | `v-snackbar` with deleted-count + reclaimed-bytes + "Undo within 30 days" link to the soft-delete history | The undo link only appears if §7 option (1) or (2) is taken. |
| Estimated savings sparkline | `v-progress-linear` cumulative + a `v-card` block showing "if you delete this selection, you reclaim ≈ X MiB" | The FinOps lens — surface the impact at the moment of decision. |
| Bucket explainer drawer | `v-navigation-drawer` with the §2 ladder text + a link to this design doc | "What do these badges mean" → one click away. |

### Acceptance criteria (UI)

- Header chip-group lets the admin toggle bucket visibility without a
  round-trip (`v-data-table-server` filter param).
- The default visible selection is **CONFIRMED-STALE only**. The other
  buckets are explicitly filtered IN, not out — the Reluctant Senior
  reads "I see only what's safe to delete" by default.
- The delete button is disabled until ≥1 row is selected AND a reason
  ≥ N chars is typed.
- The receipt page shows refused rows separately with the race reason,
  so the admin can re-detect and retry.
- Empty-state copy: "No stale channels detected. Storage is clean." —
  not "No data" (RDM lens — empty state is a positive signal here).

### MFFD demo screenshot (target)

The Storage admin page is the v1 launch demo. The screenshot shows the
MFFD import observability container with ~9 channels, of which 0 are
CONFIRMED-STALE today (they're all live-referenced). The empty-state
copy fires as the "system is clean" positive signal. A separate demo
container is seeded with a synthetic CONFIRMED-STALE row for screenshot
purposes.

---

## §6 MCP integration

**Lens citation: API Scrutinizer + RDM.** MCP tools follow the
`feedback_ai_human_collab_provenance.md` acceptance ladder — agents
propose, humans confirm.

### Tools

| Tool name | Mode | Description |
|---|---|---|
| `mcp__shepard__list_stale_channels` | read-only | Returns the same shape as `GET /v2/admin/timeseries/stale-channels`. Useful for "what's the storage hygiene state right now" questions. |
| `mcp__shepard__preview_stale_delete` | read-only | Same as `POST /preview` — agents can sample data before suggesting a delete batch. |
| `mcp__shepard__propose_stale_delete` | propose-only | Records a proposal in `:Activity` with `fair2r:wasAcceptedAs="proposed"`. Does NOT touch the substrate. A human admin must confirm via the UI or CLI. |
| (no auto-delete tool) | — | An MCP-side tool that actually deletes is intentionally omitted in v1. SM1's auto-policy work is the right place to revisit. |

### Acceptance ladder for agent-proposed deletes

1. Agent calls `propose_stale_delete` with a candidate set + rationale.
2. A `:Activity` node lands with type `stale_channel_delete_proposed`,
   `wasInformedBy` the originating prompt (PROMPT1 seed — promptlog
   integration is the eventual pairing).
3. The next admin visit to `/admin/storage/stale-channels` surfaces a
   "1 pending agent proposal" banner with the rationale + candidate
   set, pre-selected in the table.
4. Admin reviews, optionally edits the selection, and confirms. The
   confirmation transitions the activity to
   `fair2r:wasAcceptedAs="accepted"` (or `rejected`).

This gives full F(AI)²R audit trail: an LLM proposed action X, human Y
approved action Y (which may differ from X), the delta is preserved.
See `project_ai_human_collab_provenance.md`.

---

## §7 Safety + reversal story

**Lens citation: ALL FOUR (API Scrutinizer + Manufacturing-Quality +
RDM + Reluctant Senior).** Destructive operations consult all lenses.

### The race-window hard requirement (HARD-REQ-1)

Between detection (§3 steps 1-5) and deletion (§4 `POST /delete`),
some user or agent may create a new `:TimeseriesReference` pointing at
a channel that was orphan at detection time. Without a re-verify step,
that channel deletes cleanly and the new reference dangles, silently
destroying the data the new reference was about to expose.

**HARD-REQ-1: Re-verify referenced-by status inside the delete
transaction.** Implementation: open a fresh Postgres transaction at
`REPEATABLE READ`, re-resolve the Neo4j refs for the requested
shepardIds, intersect with the delete set, **remove any that are now
referenced**, then `DELETE FROM timeseries WHERE shepard_id IN (...)`
in the same transaction. The cascade FK takes care of the data points.

The `refused` field in the §4 response surfaces removed rows back to
the caller so the UI can render "skipped — became referenced between
detection and delete" without 500-ing.

This is the **one thing** that would prevent shipping (§13). Without
HARD-REQ-1, the tool is a silent data-loss vector. Every code review
must verify the re-verify path.

### Lens-by-lens argument

**API Scrutinizer.** Every delete operation has a `dryRun=true` default,
RFC 7807 error envelope, and explicit `reason` requirement. The CLI
default is `--dry-run`; `--confirm` is opt-in. The `/preview` endpoint
exists specifically so callers can introspect before mutating. No
endpoint emits an "everything was deleted" 200 — the receipt always
itemises `deletedShepardIds` and `refusedShepardIds`.

**Manufacturing-Quality.** Two protections:

1. **Audit-classified channels are immune.** A `:Timeseries` node, OR
   the `:TimeseriesContainer` it belongs to, OR any `:Annotation` on
   the referenced-by chain, may carry a `audit_classified` attribute.
   The delete endpoint refuses any shepardId whose channel resolves
   to an audit-classified ancestor. Refusal returns 200 + `refused:
   [{ shepardId, reason: "audit_classified" }]`. The PROV1a record
   logs the refusal too.
2. **Calibration linkage immunity.** If the `symbolicName` matches a
   calibration-cert linkage pattern (CHAMEO `chameo:hasCalibration`
   when ONT1d ships, or the temporary `calibration_cert_id` annotation
   key today), the channel is treated as immutable measurement
   provenance and refused.

The immune-list is configurable via `:StaleChannelConfig.immunePatterns`
and `:StaleChannelConfig.immuneAttributes`.

**RDM.** Any channel that was ever published / cited externally must
NEVER be deleted. The check: scan `:DataObject.publicationState =
PUBLISHED` ancestors; if any reachable `:DataObject` was ever published
(including via the `:DataObjectRevision` chain), refuse the delete.
The check honors the FAIR principle that a citable artefact is
permanent. Implementation hooks the existing
`PublicationStateService` (or, if absent, deferred to RDM1 with this
tool's release-gate). The Welzmüller et al. PLUTO RDM paper
([DLR eLib 215120](https://elib.dlr.de/215120/)) sets the citation
expectation that motivates this rule.

**Reluctant Senior.** "Preview first" gate (§4 `POST /preview` +
the `v-stepper` flow); "what was deleted last week" history (§4
`GET /history`); 30-day soft-delete window (option chosen below).

### Soft-delete: argued

Three options:

| Option | Mechanism | Read-path cost | Recovery cost | Verdict |
|---|---|---|---|---|
| **(1) Schema migration `deleted_at TIMESTAMPTZ NULL` on `timeseries`** | All reads add `WHERE deleted_at IS NULL`. A nightly job hard-deletes rows older than 30 days. | Touches every read path in `TimeseriesService` + `TimeseriesRepository`. Index needed on `(container_id, deleted_at)` to keep planner happy. | One UPDATE; data is intact. | **CHOSEN.** Read-path cost is paid once; recovery cost is zero. The veteran's "what got deleted" question is answered in one query. |
| **(2) Trash schema (`timeseries_trash` + `timeseries_data_points_trash`)** | Copy row + cascade points to trash, then delete from live. | Zero read-path cost. | Re-insertion is complex; identity recovery (`id`, `shepard_id`) needs careful handling. | Rejected. The transactional copy from a hypertable is non-trivial; for a feature whose v1 should ship in two sprints, this is the wrong shape. |
| **(3) No soft-delete; rely on PG WAL backups** | Direct hard-delete. Recovery requires PITR restore. | Zero. | Hours of downtime per recovery; backup quality determines whether recovery is possible at all. | Rejected. The RDM + Reluctant Senior lenses both veto "no soft-delete." A research institute cannot accept "restore from backup if you regret it." |

**Decision: Option (1).** Soft-delete via `deleted_at TIMESTAMPTZ` on
`timeseries`, hard-deletion gated on a 30-day retention window
enforced by a background Quarkus scheduled job. The UI surfaces the
30-day window in the receipt toast as "Undo within 30 days" linking
to a recover view.

**What would change my mind:** if the read-path cost (measured —
`p99` of `TimeseriesService.getChannel` and friends) regresses by
more than 5% after the migration, switch to option (2) and pay the
trash-table complexity tax. The decision is reversible; measurement
gates it.

### Audit-trail integration

Every state change (`/delete`, `/config` PATCH, recover-from-trash)
captures via PROV1a (`ProvenanceCaptureFilter`) automatically — the
filter is already wired for `@RolesAllowed("instance-admin")`
endpoints. The activity-type taxonomy adds:

- `stale_channel_dry_run` (caller queried what would be deleted)
- `stale_channel_delete` (caller deleted; payload includes the
  shepardId list and reason)
- `stale_channel_proposed` (agent proposed via MCP)
- `stale_channel_recover` (admin restored soft-deleted rows)
- `stale_channel_config_change` (admin tweaked thresholds)

The `GET /history` endpoint filters `:Activity` to these types.

---

## §8 Plugin-or-in-tree?

**Decision: in-tree.** Lens: API Scrutinizer + plugin-first heuristic
review (CLAUDE.md §"think plugin-first").

The plugin-first rule has an explicit exception list. This tool touches:

- `@RolesAllowed("instance-admin")` — the **Authentication / permissions
  surface** exception. Roles are not pluggable.
- Direct Postgres `EntityManager` against `timeseries` — the **Identity
  primitives** exception. The channel registry is a core identity
  surface every plugin compiles against.
- Neo4j OGM session against `:TimeseriesReference` — core graph
  primitive.
- `ProvenanceCaptureFilter` — the **PROV1a infrastructure** is core.

A plugin shape would have to either re-implement the substrate access
or re-export it through a SPI it doesn't yet have. Neither is worth
the friction. The right shape is in-tree under
`backend/src/main/java/de/dlr/shepard/v2/admin/storage/staleChannels/`,
following the same package layout as `StorageAdminRest` and the
AD_STORE1 doc.

**Counter-argument I rejected:** "SM1 is shepard-plugin-storage-management,
make this a sub-plugin." SM1 (#74) is not a plugin in the design —
it's an umbrella feature that adds an admin surface on top of the
existing storage primitives. Storage management is core (the same
exception list applies).

---

## §9 Relationship to SM1 (#74)

This tool is a **sub-feature** of SM1, not a separate feature.

| SM1 capability | This tool's contribution |
|---|---|
| `StorageProvider` SPI | N/A — this tool consumes Postgres + Neo4j directly. SM1's SPI is for file-storage adapters (Garage/MinIO/local), a different substrate. |
| Orphan default = infinite grace + nag | This tool implements `nag` via PROV1a recurring activity; the 30-day soft-delete window is the "grace" period for channels rather than files. |
| `onExceed` default = WARN | This tool's `GET /summary` is what feeds the WARN. When a container's reclaimable-bytes / total-bytes ratio crosses a threshold (default 20%), a `NotificationProducer` (NTF1 dependency) emits a nag to admins. |
| Neo4j retention policies | Future work — when SM1 ships retention policies, the auto-deletion mode of this tool consumes them. Out of scope for v1. |

The `GET /v2/admin/storage-overview` (AD_STORE1) endpoint gains two new
fields in the response: `staleChannelCount` and
`staleChannelReclaimableBytes`. The Storage Overview UI gets a
"Manage stale channels" link routing to the new page.

ADMIN-STALE-CH's row in `aidocs/16` lands as the first sub-row under
SM1 in the GitHub project board; SM1's note column references this
design doc as the first concrete shippable.

---

## §10 The MFFD import telemetry worked example

**Lens: RDM + Manufacturing-Quality.** Framed as a worked example, not
a live finding. Validation that the 9 channels are currently live-referenced
is deferred to the ADMIN-STALE-CH-a implementation kick-off (which will
`curl` the endpoint and confirm).

### The setup

During the 2026-05-22 MFFD import session, the importer wrote telemetry
to TimeseriesContainer 593750 under measurement `mffd_import`. Channel
identity (the 5-tuple) varied across v15.x iterations:

```
measurement="mffd_import" device="v15.4" location="source"
  symbolicName="v15.4" field="files_total"
measurement="mffd_import" device="v15.4" location="source"
  symbolicName="v15.4" field="bytes_processed"
... (7 more in the v15.4 namespace)
```

When v15.5 superseded v15.4, the importer was rerun with the new symbolic
name. Each version's channels are written by a fresh importer run.

### What the tool would say today

Three scenarios:

1. **v15.5/v15.6 are the active channels, all referenced by the
   `mffd-import-observability` DataObject.** v15.4 channels are
   `:Timeseries`-referenced from a prior DataObject revision; if that
   revision exists, the v15.4 channels are NOT orphans — they're
   linked through the revision chain.
   - Tool verdict: **NOT STALE** for any v15.x; the revision graph
     keeps them all referenced.

2. **v15.5/v15.6 are referenced from the live DataObject; v15.4 was
   never linked (early-iteration dry-run).** The v15.4 channels are
   orphans by §3 — no `:Timeseries` ref points at them.
   - Last write age: would need a `curl` to confirm, but ≥ a day or
     two (v15.4 was superseded 2026-05-22 by v15.5).
   - Bucket: **CANDIDATE** (age < 90 days) initially. Becomes
     **CONFIRMED-STALE** after 90 days IF the scratch-pattern fires
     on `symbolicName=v15.4` (it does per the default
     `^...v\d+(\.\d+)*-rc.*$` shape — but the pattern would need
     adjusting to catch `v15.4` without the `-rc` suffix; this is
     a §13 open question).

3. **All three versions are referenced.** No action.

The worked example demonstrates how the tool degrades gracefully — it
catches genuine orphans while never auto-removing in-flight ingest.

### How the admin would resolve

For (2), the right admin action is:

1. Open `/admin/storage/stale-channels`, filter to container 593750.
2. See 9 v15.4 channels in the CANDIDATE bucket (or CONFIRMED-STALE
   after 90 days).
3. Click `Preview` on the selection; eyeball that the 412 sample points
   per channel are early-iteration test data (e.g. `files_total=0,0,0,1,2`).
4. Type "v15.4 superseded by v15.5 on 2026-05-22 — early-iteration
   importer dry-run rows" in the reason field.
5. Confirm delete. Soft-delete lands the rows in `deleted_at`; the
   `:Activity` records the operation; the receipt confirms 9 channels
   reclaimed.
6. The 30-day window keeps the rows recoverable.

---

## §11 Risks + counter-evidence

### R1 — TimescaleDB chunk decompression cost on delete

Deleting a `timeseries` row cascades to `timeseries_data_points`. If the
points span compressed chunks, the cascade triggers chunk-level
decompression-before-delete. TimescaleDB's compressed chunks are
read-only; updates / deletes force a decompress-modify-recompress cycle
([Tiger Data docs on chunks_detailed_size](https://docs.tigerdata.com/api/latest/hypertable/chunks_detailed_size/);
[TimescaleDB GitHub #5724 on compressed-chunk operations](https://github.com/timescale/timescaledb/issues/5724)).

For a typical orphan with O(1k) points spread across O(1) chunk, the
cost is modest. For pathological cases (orphans spanning many compressed
chunks), the delete can block writes on the same hypertable. **Mitigation:**
the implementation orders deletes by `last_write_ns ASC` so old (likely-
compressed) data goes first, and chunks the delete into batches of N
shepardIds per transaction (default N=50). The CLI exposes the batch
size as a flag.

The §1 InfluxDB "tombstone" risk does NOT exist in Timescale — the
substrate model is different.

### R2 — Race between detection and delete

Addressed by HARD-REQ-1 in §7. Without that guard, a new
`:TimeseriesReference` created between snapshot and delete becomes a
silent dangling pointer.

### R3 — `approximate_row_count` lies on compressed hypertables

TimescaleDB's `approximate_row_count()` can be wildly off on compressed
hypertables ([GitHub #5724](https://github.com/timescale/timescaledb/issues/5724)).
The §3 detection query intentionally uses exact `COUNT(*)` scoped to
the bounded set of channel IDs — not approximate. The "estimated bytes"
field in the response is genuinely approximate (derived from row count
× per-row encoded size estimate from `chunk_compression_stats()`); the
UI labels it as such.

### R4 — Cross-substrate snapshot drift

Postgres REPEATABLE READ + Neo4j OGM session are not in the same
transaction (no 2PC). If a `:TimeseriesReference` is created at exactly
the moment the Cypher query runs, it may or may not appear in the
result set. HARD-REQ-1 (the in-transaction re-verify on delete)
closes this. The detection-time result is, by design, advisory.

### R5 — Migration ordering with TS-ID continuation

Platform/87 PR-2..PR-9 continues the shepardId rollout. While the wire
surface migration is in progress, callers may address channels either
by 5-tuple (legacy) or shepardId (new). The tool accepts shepardId
canonically; the response includes the 5-tuple for human readability.
No ordering risk — the substrate already has `shepard_id` on every row
post-PR-1 (Flyway V1.11.0).

### R6 — Auto-policy creep

The temptation: ship v2 with "auto-delete after 365 days, no admin
required." **Resisted.** Auto-deletion changes the consent model. Until
SM1 lands a fully-specified retention-policy SPI with operator-visible
defaults (and per-container overrides, per-classification overrides,
per-publication-state overrides), the manual gate is non-negotiable.
The "nag" pattern is the right intermediate signal.

### R7 — Cardinality blow-up on cluster-wide queries

A cluster with 100k+ channels takes the §3 set-difference into "is
this still a single-transaction operation?" territory. **Mitigation:**
the CLI / REST default for `containerAppId IS NULL` (cluster-wide) is
a streamed list, not a snapshot. The snapshot guarantee applies per
container. The summary endpoint uses cached counts refreshed every
N minutes (configurable).

Prometheus has the same problem at scale ([Cloudflare on Prometheus
high-cardinality](https://blog.cloudflare.com/how-cloudflare-runs-prometheus-at-scale/);
[Prometheus discussion #10598 on ephemeral-metrics cleanup](https://github.com/prometheus/prometheus/discussions/10598)).
The lesson learned: detection has to be incremental for cluster-scale
deployments. Single-instance Shepard at O(10k) channels does not hit
this; the design is forward-compatible with the streaming approach.

### R8 — FinOps invisibility before the tool ships

Today, no one can answer "what fraction of Timescale storage is
reachable?" The org pays for storage but cannot price the orphan slice.
Shipping the `GET /summary` endpoint alone (without the delete path)
already pays for the FinOps team's visibility. Reflects industry
pattern ([Maxima Consulting on observability FinOps strategy](https://www.maximaconsulting.com/newsroom/observability-needs-its-own-finops-strategy);
[TechTarget on storage cost over-spend](https://www.techtarget.com/searchstorage/feature/Stop-Overpaying-for-Storage-A-FinOps-Guide-for-CIOs)).

---

## §12 Implementation roadmap

Six tasks. All in-tree under
`backend/src/main/java/de/dlr/shepard/v2/admin/storage/staleChannels/`
(and the frontend mirror under `frontend/pages/admin/storage/`). Effort
estimates in person-days for one engineer.

| Sub-row | Slice | Effort | Depends on | Acceptance |
|---|---|---|---|---|
| **ADMIN-STALE-CH-a** | Detection query + DTOs. New service `StaleChannelDetectionService` with the §3 SQL + Cypher + set-difference. `GET /v2/admin/timeseries/stale-channels` and `GET /summary`. | 3 d | TS-IDa (shipped) | List endpoint returns deterministic results on a static substrate. Test fixture seeds 5 channels (3 referenced, 2 orphan) and asserts bucket classification. |
| **ADMIN-STALE-CH-b** | Dry-run REST + sample preview. `POST /preview` and `POST /delete?dryRun=true`. | 2 d | -a | Dry-run returns the same shape as a real delete (minus mutation). Property test: `dryRun=true` never writes. |
| **ADMIN-STALE-CH-c** | Real delete with HARD-REQ-1 re-verify + soft-delete schema migration (`deleted_at`). Flyway `V1.12.0__add_deleted_at_to_timeseries.sql`. `POST /delete?dryRun=false`. | 4 d | -b, RDM1 publication-state check | Race test: two concurrent transactions, one creating a `:TimeseriesReference`, one deleting the underlying channel. The reference-creating tx wins; the delete reports `refused`. |
| **ADMIN-STALE-CH-d** | UI page + components. `/admin/storage/stale-channels` with `v-data-table-server`, `v-stepper`, `v-snackbar`. | 4 d | -a (data shape) | Vitest + Playwright (UI13 + UI-PW1). Empty-state copy; bucket toggles; reason validation; receipt rendering. |
| **ADMIN-STALE-CH-e** | Audit-trail + nag integration. Activity-types added; `GET /history`. NotificationProducer for the 20%-threshold WARN (requires NTF1 partial). | 2 d | -c, NTF1 partial | Provenance audit pass: every state change appears in `:Activity` with a queryable activity-type. |
| **ADMIN-STALE-CH-f** | MCP tools (`list_stale_channels`, `preview_stale_delete`, `propose_stale_delete`) + admin-side accept/reject flow. | 3 d | -c, -d | Agent proposal lands in `:Activity` with `fair2r:wasAcceptedAs="proposed"`. Admin UI surfaces it as a pre-selected candidate set. |

Total: ~18 days, ~3.5 weeks for one engineer. Parallelisable: -a ↦ {-b, -d} can fan out; -c ↦ -e is sequential; -f is independent of -d.

Each sub-row gets its own commit + GitHub Issue per `feedback_github_pm_policies.md` (the 4-gate Issue filter; ADMIN-STALE-CH-a..f are in-tree solo-dev work, so per the policy may stay as aidocs/16 rows without separate Issues — single Issue umbrella optional).

---

## §13 Acceptance criteria + open questions

### Ship criteria

1. **HARD-REQ-1 verified** — race test passes consistently in CI; a code
   review checklist item explicitly verifies the in-transaction re-verify
   path before merge.
2. **Bucket classifier round-trips** — 100% accuracy on a curated fixture
   of 20 channels per bucket (60 total).
3. **Soft-delete recovery works** — admin can recover a deleted channel
   within 30 days; after 30 days, the background job hard-deletes and
   recovery returns 410 Gone.
4. **Read-path regression** — p99 of `TimeseriesService.getChannel`
   does NOT regress more than 5% after the `WHERE deleted_at IS NULL`
   migration. Measured by JMH or runtime instrumentation.
5. **Audit trail complete** — every endpoint's mutation appears in
   `:Activity`; `GET /history` returns all of them filtered to the
   tool's activity-types.
6. **Coverage** — sub-row -c (the delete path) has ≥80% branch
   coverage. Sub-row -d has Playwright coverage of the stepper flow.

### Open questions

1. **Scratch-pattern default.** The proposed default
   `^(scratch|tmp|test|smoke|v\d+(\.\d+)*-rc.*|wip|debug)$` would NOT
   match `v15.4` (no `-rc` suffix). Should the default be more
   permissive (catch `v15.4`) at the cost of false positives on real
   versioned channels? Recommend: leave default conservative; expose
   the pattern in the UI config page so operators can extend per
   institute. Awaiting decision.
2. **Publication-state immunity.** Is the right gate
   `:DataObject.publicationState=PUBLISHED`, or also `EMBARGOED` /
   `RESTRICTED`? Recommend: any state other than `DRAFT` is immune
   until RDM-PUB ships, then revisit. Awaiting decision.
3. **CSV export from the UI.** `feedback_capture_api_ui_annoyances.md`
   has a common request for "give me this table as CSV." Implementing
   in -d adds ~0.5 d; skip in v1 or include? Recommend: skip, file as
   a UI ergonomics row; the JSON output of the CLI covers the
   automation case.
4. **Background nag cadence.** The 20%-reclaimable threshold fires a
   notification — how often? Daily for the first hit, then weekly until
   addressed? Awaiting NTF1 nag-pattern doc.
5. **Per-container vs cluster-wide config.** The thresholds in
   `:StaleChannelConfig` are cluster-wide today. SM1 may need
   per-container overrides (e.g. a sensitive aerospace container with
   `staleAfterDays=730`, a sandbox container with `30`). v1 ships
   cluster-wide; per-container is filed as a follow-on.

### The one thing that would prevent shipping

**HARD-REQ-1 (the in-transaction re-verify pattern in §7) must be
implemented and tested before any delete endpoint goes to production.**
Without it, this tool is a silent data-loss vector. Every PR touching
the delete path must include or update the race test.

---

## §14 References

External citations:

- [Tiger Data docs — `chunks_detailed_size()`](https://docs.tigerdata.com/api/latest/hypertable/chunks_detailed_size/) — substrate accounting primitive used by the `/summary` endpoint.
- [TimescaleDB GitHub issue #5724 — `approximate_row_count()` on compressed hypertables](https://github.com/timescale/timescaledb/issues/5724) — why exact counts are used in detection.
- [TimescaleDB compression stats docs](https://docs.timescale.com/api/latest/compression/chunk_compression_stats/) — feeds the per-channel bytes-reclaimable estimate.
- [Prometheus discussion #10598 — head-series memory + ephemeral metrics](https://github.com/prometheus/prometheus/discussions/10598) — prior art for stale-series accumulation patterns.
- [Cloudflare — How Cloudflare runs Prometheus at scale](https://blog.cloudflare.com/how-cloudflare-runs-prometheus-at-scale/) — cluster-scale cardinality patterns informing §11 R7.
- [Maxima Consulting — Observability needs its own FinOps strategy](https://www.maximaconsulting.com/newsroom/observability-needs-its-own-finops-strategy) — FinOps framing in §1, §11 R8.
- [TechTarget — A FinOps guide for CIOs (storage)](https://www.techtarget.com/searchstorage/feature/Stop-Overpaying-for-Storage-A-FinOps-Guide-for-CIOs) — observability spend ratio benchmarks.
- Welzmüller, F. et al. (2024) — "Research Data Management for Space Missions: Practical Experiences and Lessons Learned." DLR eLib 215120 — RDM lens on publication-state immunity.

Internal cross-references:

- `aidocs/platform/87-timeseries-appid-migration.md` — provides `shepard_id` as the canonical identity.
- `aidocs/data/12-timescaledb-performance-analysis.md` — substrate performance baseline; V1.8.0 index restoration.
- `aidocs/16-dispatcher-backlog.md` — ADMIN-STALE-CH row + SM1 row.
- `backend/src/main/java/de/dlr/shepard/v2/admin/resources/AdminStorageOverviewRest.java` — pattern to extend.
- `backend/src/main/java/de/dlr/shepard/provenance/filters/ProvenanceCaptureFilter.java` — automatic audit capture.
- `backend/src/main/resources/db/migration/V1.0.0__setup_timeseries_tables.sql` — `ON DELETE CASCADE` underpinning the deletion model.
- `feedback_ai_human_collab_provenance.md` (memory) — MCP propose-only ladder.
- `feedback_ui_api_parity.md` (memory) — REST + UI + CLI parity discipline.
- `feedback_reuse_before_reimplement.md` (memory) — Vuetify reuse mandate for §5.
- `feedback_db_review_all_stores.md` (memory) — cross-substrate review framing for §3.
- `feedback_executing_actions_with_care` (CLAUDE.md) — destructive-action discipline applied throughout §7.
