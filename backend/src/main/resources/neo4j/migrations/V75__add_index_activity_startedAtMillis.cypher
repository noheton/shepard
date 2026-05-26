// DB-OPT2 — Activity.startedAtMillis range index
//
// Hot path: provenance / activity-log queries filtered by time range
// (e.g. GET /v2/activity?after=<ms>).  With 296,808 Activity nodes and no
// index, every such query issues a full NodeByLabelScan costing ~593,617
// DB hits and ~56 ms on MFFD data.
//
// This RANGE index drives NodeByIndexSeek, collapsing the scan to O(log n)
// + result-set size.  Safe to re-run (IF NOT EXISTS).
//
// Operator runbook: no data change; index build is online in Neo4j 5.x.
// Roll back: DROP INDEX Activity_startedAtMillis_idx IF EXISTS.

CREATE INDEX Activity_startedAtMillis_idx IF NOT EXISTS
FOR (n:Activity) ON (n.startedAtMillis);
