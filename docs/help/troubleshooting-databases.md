---
layout: default
title: Troubleshooting databases
description: Step-by-step fixes for the most common database problems on a running shepard instance.
audience: admin
permalink: /help/troubleshooting-databases/
stage: deployed
last-stage-change: 2026-05-26
---

# Troubleshooting databases

This page covers the most common database problems an operator sees on a running
shepard instance. Each scenario gives you the symptom, a diagnostic command,
the fix, and a verify step — all copy-paste ready.

For a general orientation to which substrate holds what data, see the
[Database overview]({{ '/reference/database-overview/' | relative_url }}).
For performance tuning after a large ingest, see the
[post-ingest tuning runbook]({{ '/ops/db-optimisation-runbook/' | relative_url }}).

---

## Scenario 1 — PgBouncer health-check is spamming the log

**Symptom.** `docker compose logs pgbouncer` shows thousands of lines per day that look like:

```
pgbouncer: LOG C-0x... closing because: query error (age=0)
ERROR: no such database: pgbouncer
```

This is cosmetic — the application still connects normally — but it fills the log
and buries real errors.

**Cause.** The Docker Compose `healthcheck` for the PgBouncer service probes with
`pg_isready -d pgbouncer`, but `pgbouncer` is a reserved admin pseudo-database,
not a real database PgBouncer will route. Each probe emits an error.

**Fix.** Update the `healthcheck` in your `docker-compose.override.yml` to probe
the real application database instead:

```yaml
services:
  pgbouncer:
    healthcheck:
      test: ["CMD", "pg_isready", "-q", "-h", "localhost", "-p", "5432",
             "-d", "postgres", "-U", "shepard"]
      interval: 30s
      timeout: 5s
      retries: 3
```

Then restart the service — no data is affected:

```bash
docker compose restart pgbouncer
```

**Verify.** Wait 60 seconds:

```bash
docker compose logs --since 60s pgbouncer | grep -c "no such database"
# Expected: 0
```

---

## Scenario 2 — TimescaleDB continuous aggregate job keeps failing

**Symptom.** Timeseries charts are slow for queries spanning more than one hour,
and the CAgg compression job log shows repeated failures.

**Diagnostic.**

```sql
-- Run from: docker compose exec timescaledb psql -U shepard -d shepard
SELECT job_id, proc_name, last_run_status, last_run_started_at
FROM timescaledb_information.jobs
WHERE proc_name IN ('policy_refresh_continuous_aggregate',
                    'policy_compress_chunks');
```

If `last_run_status = 'Failed'`, check the error:

```sql
SELECT * FROM timescaledb_information.job_errors
ORDER BY finish_time DESC LIMIT 10;
```

A message containing `integer_now function not set for 'timeseries_hourly'`
means migration V1.17.0 has not applied.

**Fix.** Let the application start — `MigrationsRunner` applies V1.17.0 at
startup. Once applied, the `integer_now` function is registered on the CAgg's
internal hypertable and the job can run. If you need data available immediately
without waiting for the scheduled job:

```sql
-- Materialize the full CAgg history (idempotent — safe to re-run)
CALL refresh_continuous_aggregate('timeseries_hourly', NULL, NULL);

-- Manually trigger the compression job (job_id from the query above)
SELECT run_job(<job_id>);
```

**Verify.**

```sql
SELECT count(*) FROM timeseries_hourly;
-- Expected: non-zero (e.g. ~32 000 rows for a full MFFD ingest)

SELECT last_run_status FROM timescaledb_information.jobs
WHERE proc_name = 'policy_refresh_continuous_aggregate';
-- Expected: 'Success'
```

---

## Scenario 3 — Garage S3 write failures (capacity cap hit)

**Symptom.** File uploads fail silently or with HTTP 500 after an otherwise
healthy period. New objects stop appearing in `docker compose exec garage /garage bucket list`.

**Diagnostic.** Check Garage capacity allocation vs. actual disk usage:

```bash
# Garage layout — shows configured capacity per node
docker compose exec garage /garage layout show

# Host free space
df -h /var/lib/docker
```

If the Garage layout shows a small capacity (e.g. `1 GB`) while the host has
much more free, the layout cap is the bottleneck. Garage silently refuses writes
when it believes the node is at capacity.

**Fix.** Update the Garage layout to reflect the actual available space.
The `capacity` field is in GB:

```bash
# Get the current node ID
docker compose exec garage /garage node id

# Update capacity (e.g. to 200 GB)
docker compose exec garage /garage layout assign --node <node-id> --capacity 200

# Apply the layout change
docker compose exec garage /garage layout apply --version <next-version>
```

After applying, verify the layout is staged correctly before committing:

```bash
docker compose exec garage /garage layout show
```

**Verify.** Upload a test file via the API and confirm it succeeds. Check
`docker compose logs garage` for any rejection messages.

---

## Scenario 4 — File downloads return HTTP 500 (Garage + UUID-shaped OIDs)

**Symptom.** Downloading a file stored in Garage via the REST API returns HTTP 500.
The backend log shows:

```
java.lang.IllegalArgumentException: invalid hexadecimal representation of an ObjectId: [<uuid>]
```

**Cause.** When the storage backend is Garage (S3), file objects are keyed with
UUID-shaped OIDs. The code path at `FileService.java` attempts to construct a MongoDB
`ObjectId` from the OID string. MongoDB `ObjectId` is a 24-character hex string;
UUID strings are a different format and the construction fails.

This is a known open bug (BUG-FILEPAYLOAD-UUID-OID) and affects the file download
path when `STORAGE_BACKEND=s3`.

**Workaround until fixed.** You can download the raw object directly from Garage
using the S3 API. Retrieve the object key from the database:

```bash
# Find the file OID from MongoDB fs.files
docker compose exec mongodb mongosh -u shepard -p <password> shepard \
  --eval 'db.getCollection("fs.files").findOne({filename: "<filename>"})'
```

Then fetch from Garage directly using the AWS CLI or curl with S3 credentials
from your `.env`.

**Status.** The fix requires changing `FileService.java` to detect UUID-shaped OIDs
and skip the `ObjectId` constructor. Track the issue in the codebase under the
`BUG-FILEPAYLOAD-UUID-OID` tag.

---

## Scenario 5 — Timeseries container data invisible to the REST API

**Symptom.** TimescaleDB has data points in `timeseries_data_points`, but the
API returns empty results or 404 for those timeseries channels. The data is
present when queried directly from `psql`.

**Diagnostic.** Every timeseries container needs a matching Neo4j node.
Check whether the Neo4j shadow exists:

```bash
# 1. Get the container_id from TimescaleDB
docker compose exec timescaledb psql -U shepard -d shepard -c \
  "SELECT DISTINCT container_id FROM timeseries_data_points LIMIT 20;"

# 2. Check whether a matching Neo4j TimeseriesContainer exists
docker compose exec neo4j cypher-shell -u neo4j -p <password> \
  "MATCH (t:TimeseriesContainer) WHERE t.id = <container_id> RETURN t"
```

If step 2 returns no rows, the TimescaleDB data has no Neo4j shadow — the REST
API has no way to find it.

**Cause.** This typically happens when a timeseries import writes data directly
to TimescaleDB (e.g. a bulk import script) without also creating the
`TimeseriesContainer` node in Neo4j, or when a Neo4j restore loses the node
while TimescaleDB data remains intact.

**Fix.** Recreate the Neo4j `:TimeseriesContainer` node and link it to its
parent `:DataObject`. Use the restore runbook
([`docs/admin/runbooks/restore-tsdb-container-neo4j-shadow`]({{ '/admin/runbooks/' | relative_url }}))
which covers the Cypher for re-linking an orphaned container.

**Verify.** After the fix, the API endpoint for that DataObject's timeseries
containers should return the channel list, and chart queries should return data.

---

## Scenario 6 — Neo4j migration aborts startup

**Symptom.** The backend fails to start. Logs contain:

```
MigrationsException: Pending migrations could not be applied...
```

or

```
Migration V<N> has failed with message: ...
```

**Diagnostic.**

```bash
# Which migrations have applied?
docker compose exec neo4j cypher-shell -u neo4j -p <password> \
  "MATCH (n:__Neo4jMigration) RETURN n.version, n.description, n.state ORDER BY n.version"
```

Look for a row with `state = 'FAILED'`.

**Common causes and fixes:**

| Cause | Fix |
|---|---|
| Migration file content changed after it was applied | Do not edit applied migration files. Restore the original or create a new corrective migration. |
| Constraint violation (e.g. duplicate nodes) | Resolve the data conflict manually in Cypher, then delete the `__Neo4jMigration` node for the failed version and restart. |
| Neo4j not yet accepting connections when migration runner starts | Restart the application — `MigrationsRunner` retries. If this recurs, check Neo4j container health. |
| Migrations applied out of order | Ensure V1 through V<N-1> are all present and `state = 'APPLIED'` before V<N> runs. |

**Verify.**

```bash
docker compose exec neo4j cypher-shell -u neo4j -p <password> \
  "MATCH (n:__Neo4jMigration) WHERE n.state <> 'APPLIED' RETURN n.version, n.state"
# Expected: no rows
```

---

## Scenario 7 — Activity node count growing without bound

**Symptom.** `MATCH (a:Activity) RETURN count(a)` returns a very large number
(hundreds of thousands or more). Provenance queries are slow even with the
`Activity_startedAtMillis_idx` index. Backup size is growing faster than
the dataset itself.

**Context.** Under active ingest (e.g. the MFFD pipeline), Activity nodes
accumulate at roughly 43 k nodes per ingest day. There is currently no automatic
retention or archival policy. This is a known gap (tracked in the backlog as
the Activity archival migration candidate).

**Diagnostic.**

```bash
docker compose exec neo4j cypher-shell -u neo4j -p <password> \
  "MATCH (a:Activity) RETURN count(a) AS total,
   min(a.startedAtMillis) AS oldest_ms,
   max(a.startedAtMillis) AS newest_ms"
```

Convert the epoch-ms values to check the span of the history.

**Mitigations until a retention migration ships:**

1. **Ensure the V75 index exists** — without it, every provenance query does a full scan.
   Check with `SHOW INDEXES YIELD name, state WHERE name = 'Activity_startedAtMillis_idx'`.
   If absent, apply V75 (let the application start to trigger `MigrationsRunner`).

2. **Archive old Activity nodes manually** if disk pressure is acute:
   ```cypher
   -- Preview how many nodes are older than 90 days
   MATCH (a:Activity)
   WHERE a.startedAtMillis < (timestamp() - 90 * 86400000)
   RETURN count(a)
   ```
   Do not delete without first exporting the provenance chain if you need audit trails.

3. **Monitor neo4j store size:**
   ```bash
   docker compose exec neo4j du -sh /data/databases/neo4j/
   ```

**Status.** A dedicated archival migration (write-to-archive → mark-as-archived →
deferred cleanup) is queued. See `aidocs/16-dispatcher-backlog.md`.

---

## Quick-reference: useful one-liners

```bash
# Neo4j — node count by label
docker compose exec neo4j cypher-shell -u neo4j -p <password> \
  "CALL apoc.meta.stats() YIELD labels RETURN labels"

# TimescaleDB — chunk count and compressed fraction
docker compose exec timescaledb psql -U shepard -d shepard -c \
  "SELECT count(*), is_compressed FROM timescaledb_information.chunks GROUP BY is_compressed;"

# MongoDB — collection sizes
docker compose exec mongodb mongosh -u shepard -p <password> shepard \
  --eval 'db.runCommand({dbStats: 1, scale: 1024*1024})'

# Garage — storage summary
docker compose exec garage /garage stats

# PostGIS — check extension is loaded
docker compose exec postgis psql -U shepard -d shepard -c "SELECT PostGIS_version();"
```
