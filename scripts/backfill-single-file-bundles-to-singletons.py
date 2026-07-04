#!/usr/bin/env -S uv run --script
# /// script
# requires-python = ">=3.11"
# dependencies = ["neo4j>=5.20"]
# ///
"""
backfill-single-file-bundles-to-singletons.py — SINGLETON-FILE-03

Reconciliation note (load-bearing — read before running):

  The data transformation is already implemented in the in-tree migration
  V23__Split_singleton_bundles_to_FileReferences.java. V23 is the canonical
  shape because it preserves the bundle's appId on the resulting
  :SingletonFileReference (the "appId in, content out" contract — any
  incoming reference to the old appId keeps resolving). V23 is opt-in
  via `shepard.migration.split-singletons.enabled` (default false).

  This script is the operator-driven WRAPPER around V23 that:

    1. Confirms whether V23 has already run on this instance.
    2. Prints the operator runbook steps to enable V23 if not.
    3. After V23 has run, emits one PROV-O `:Activity` per converted
       row (kind = SINGLETON_FILE_MIGRATION) so the audit feed has a
       first-class entry per transform — the standing rule
       "the audit trail is a graph, not a log" applies even to admin
       migrations.

  The script does NOT call POST /v2/files to mint new singletons. That
  would mint NEW appIds + break every incoming reference to the old
  bundle's appId. Operators who want REST-driven transformation should
  use that endpoint only for the seed-migration path (-02), not the
  backfill path.

Usage:

  # 1) Audit only — prints the candidate count + V23 status.
  uv run scripts/backfill-single-file-bundles-to-singletons.py

  # 2) Dry-run: list every row that WOULD get a PROV-O activity but
  #    don't write any. Default.
  uv run scripts/backfill-single-file-bundles-to-singletons.py --dry-run

  # 3) Commit the PROV-O writes after V23 has run.
  uv run scripts/backfill-single-file-bundles-to-singletons.py --commit

Environment:

  NEO4J_URI         default bolt://localhost:7687
  NEO4J_USER        default neo4j
  NEO4J_PASSWORD    default reads NEO4J_PASSWORD / NEO4J_PW, falls back
                    to the dispatch-box dev value `neo4j_dev_secret`
  ACTOR_USER_ID     identity string stamped on each :Activity.
                    Default: "system:singleton-file-migration".

Idempotency:

  PROV-O writes are guarded by a per-row uniqueness check: the script
  reads `b.appId` on the now-relabeled :SingletonFileReference and uses
  it as the `:Activity { entityAppId, kind: 'SINGLETON_FILE_MIGRATION' }`
  composite key. Re-running with `--commit` is a no-op for rows that
  already carry an Activity.

Fail-fast behaviour:

  Any driver error or Cypher exception aborts the run. The script never
  swallows errors silently (per CLAUDE.md "completeness non-negotiable").
  Partial progress is fine: re-run picks up where it stopped.

See:
  - aidocs/16-dispatcher-backlog.md  §SINGLETON-FILE-MIGRATION
  - docs/admin/runbooks/single-file-singletons.md  (operator steps)
  - backend/.../V23__Split_singleton_bundles_to_FileReferences.java
"""

from __future__ import annotations

import argparse
import os
import sys
import time
from datetime import datetime, timezone

try:
    from neo4j import GraphDatabase
except ImportError:
    print("neo4j driver not installed.", file=sys.stderr)
    sys.exit(2)


# ── Cypher snippets ─────────────────────────────────────────────────────────

CHECK_V23_QUERY = """
MATCH (s:SingletonFileReference)
WHERE s.legacyV23Singleton = true
RETURN count(s) AS v23_converted
"""

# Bundles that look singleton-shaped AND have not yet been relabeled.
# These are the "V23 needs to be enabled + restart needed" cohort.
PENDING_QUERY = """
MATCH (b:FileBundleReference)
WHERE NOT b:SingletonFileReference
  AND (
    size(coalesce(b.fileOids, [])) = 1
    OR EXISTS {
      MATCH (b)-[:HAS_GROUP]->(g:FileGroup)
      WHERE count{(b)-[:HAS_GROUP]->(:FileGroup)} = 1
      AND   count{(g)-[:has_payload]->(:ShepardFile)} = 1
      RETURN g
    }
  )
RETURN elementId(b) AS bundleEid,
       b.appId      AS bundleAppId,
       b.name       AS bundleName
"""

# Already-converted singletons that DON'T yet have a PROV-O Activity.
NEEDS_PROV_QUERY = """
MATCH (s:SingletonFileReference)
WHERE s.legacyV23Singleton = true
  AND NOT EXISTS {
    MATCH (a:Activity {kind: 'SINGLETON_FILE_MIGRATION', entityAppId: s.appId})
    RETURN a
  }
OPTIONAL MATCH (d:DataObject)-[:has_reference]->(s)
OPTIONAL MATCH (c:Collection)-[:has_dataobject]->(d)
RETURN elementId(s) AS singletonEid,
       s.appId AS singletonAppId,
       s.name AS singletonName,
       coalesce(c.appId, '<no-coll>') AS collectionAppId,
       coalesce(c.name,  '<orphan>')  AS collectionName,
       coalesce(d.appId, '<no-do>')   AS dataObjectAppId
"""

# Write one PROV-O Activity per row. The activity is a first-class graph
# node + carries `entityAppId` so it's queryable from the singleton side
# via the standing audit-trail-is-a-graph rule.
WRITE_PROV_QUERY = """
MATCH (s:SingletonFileReference {appId: $singletonAppId})
MERGE (a:Activity {
    kind: 'SINGLETON_FILE_MIGRATION',
    entityAppId: $singletonAppId
  })
  ON CREATE SET
    a.appId            = $activityAppId,
    a.timestamp        = $ts,
    a.actorUserId      = $actor,
    a.sourceMode       = 'system',
    a.description      = $description,
    a.previousLabel    = 'FileBundleReference',
    a.newLabel         = 'SingletonFileReference',
    a.bundleName       = $bundleName,
    a.collectionAppId  = $collectionAppId,
    a.dataObjectAppId  = $dataObjectAppId,
    a.transformation   = 'single-file-bundle->singleton',
    a.migrationSource  = 'V23__Split_singleton_bundles_to_FileReferences'
MERGE (a)-[:WAS_GENERATED_BY]->(s)
RETURN a.appId AS activityAppId,
       a.timestamp AS timestamp
"""


def _run(driver, q, **params):
    with driver.session() as s:
        return list(s.run(q, **params))


def _print_runbook(pending_count: int) -> None:
    """Emit the operator runbook text for enabling V23."""
    print("─" * 70)
    print("V23 (split-singleton-bundles) has NOT been run on this instance.")
    print(f"{pending_count} :FileBundleReference rows are eligible.")
    print("─" * 70)
    print()
    print("Operator runbook (see docs/admin/runbooks/single-file-singletons.md):")
    print()
    print("  1. Snapshot Neo4j + MongoDB.")
    print("       docker exec infrastructure-neo4j-1 neo4j-admin database dump …")
    print("       mongodump … (per docs/admin/runbooks/04-restore-neo4j.md)")
    print()
    print("  2. Enable the V23 toggle in application.properties:")
    print("       shepard.migration.split-singletons.enabled=true")
    print("     (or set the equivalent env var SHEPARD_MIGRATION_SPLIT_SINGLETONS_ENABLED=true).")
    print()
    print("  3. Restart the backend. V23 runs on startup; progress logs every 1000 rows.")
    print()
    print("  4. Re-run this script with --commit to emit PROV-O Activities:")
    print("       uv run scripts/backfill-single-file-bundles-to-singletons.py --commit")
    print()
    print("Rollback (within V23_R's timestamp-guard window):")
    print("    cypher-shell -f V23_R__Rejoin_singletons_into_FileBundleReferences.cypher")
    print()
    print("─" * 70)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--neo4j-uri",
        default=os.environ.get("NEO4J_URI", "bolt://localhost:7687"),
    )
    parser.add_argument(
        "--neo4j-user",
        default=os.environ.get("NEO4J_USER", "neo4j"),
    )
    parser.add_argument(
        "--neo4j-password",
        default=os.environ.get("NEO4J_PASSWORD", os.environ.get("NEO4J_PW", "neo4j_dev_secret")),
    )
    parser.add_argument(
        "--actor",
        default=os.environ.get("ACTOR_USER_ID", "system:singleton-file-migration"),
        help="Identity stamped on each :Activity. Default: system:singleton-file-migration",
    )
    grp = parser.add_mutually_exclusive_group()
    grp.add_argument(
        "--dry-run",
        action="store_true",
        default=True,
        help="Default: don't write PROV-O. List what would happen.",
    )
    grp.add_argument(
        "--commit",
        action="store_true",
        help="Actually write PROV-O `:Activity` rows. After V23 has run.",
    )
    args = parser.parse_args()
    commit = args.commit  # mutually exclusive with dry-run via argparse
    if commit:
        args.dry_run = False

    driver = GraphDatabase.driver(
        args.neo4j_uri, auth=(args.neo4j_user, args.neo4j_password)
    )
    try:
        driver.verify_connectivity()
    except Exception as ex:
        print(f"ERROR: cannot reach Neo4j at {args.neo4j_uri}: {ex}", file=sys.stderr)
        return 3

    # ── 1) Phase: did V23 already run?
    v23 = _run(driver, CHECK_V23_QUERY)
    v23_converted = int(v23[0]["v23_converted"]) if v23 else 0

    # ── 2) Phase: how many candidates remain pre-V23?
    pending = _run(driver, PENDING_QUERY)
    pending_count = len(pending)

    print(f"V23 already-converted singletons: {v23_converted}")
    print(f"Single-file FileBundleReference rows still pending: {pending_count}")
    print()

    if v23_converted == 0 and pending_count == 0:
        print(
            "No work to do. No legacy V23 singletons and no pending "
            "single-file bundles. This instance is either fresh or fully "
            "migrated. Land SINGLETON-FILE-04 + -05 to prevent re-accretion."
        )
        driver.close()
        return 0

    if pending_count > 0:
        _print_runbook(pending_count)
        # Still proceed to PROV emit if there are also legacy singletons —
        # operators may have a mixed instance where V23 ran for some rows
        # and other rows accreted since.

    needs_prov = _run(driver, NEEDS_PROV_QUERY)
    print(f"Singletons still missing a PROV-O migration Activity: {len(needs_prov)}")

    if not needs_prov:
        print("Nothing to PROV-stamp. Done.")
        driver.close()
        return 0

    if args.dry_run:
        print()
        print("--- DRY RUN — no writes ---")
        for row in needs_prov:
            print(
                f"  WOULD STAMP :Activity {{kind: 'SINGLETON_FILE_MIGRATION', "
                f"entityAppId: {row['singletonAppId']!r}}} "
                f"for '{row['singletonName']}' (collection: {row['collectionName']})"
            )
        print()
        print("Re-run with --commit to write the PROV-O Activities.")
        driver.close()
        return 0

    # ── 3) Phase: commit PROV-O writes
    print()
    print(f"--- COMMITTING — actor={args.actor} ---")
    now_ms = int(time.time() * 1000)
    description = (
        "Migrated single-file :FileBundleReference to "
        ":SingletonFileReference via V23 (shepard.migration."
        "split-singletons.enabled=true). appId preserved end-to-end. "
        "PROV-O Activity emitted by scripts/"
        "backfill-single-file-bundles-to-singletons.py (SINGLETON-FILE-03)."
    )
    stamped = 0
    for row in needs_prov:
        # Cheap synthetic UUID v7-shaped activity id. The :Activity DAO
        # will accept any unique appId; this script's id format is
        # `migrate-<unix-ms>-<row-index>` so an operator can grep them
        # in the activity feed.
        activity_app_id = f"migrate-{now_ms}-{stamped:04d}"
        result = _run(
            driver, WRITE_PROV_QUERY,
            singletonAppId=row["singletonAppId"],
            activityAppId=activity_app_id,
            ts=datetime.now(timezone.utc).isoformat(),
            actor=args.actor,
            description=description,
            bundleName=row["singletonName"],
            collectionAppId=row["collectionAppId"],
            dataObjectAppId=row["dataObjectAppId"],
        )
        if result:
            stamped += 1
            print(
                f"  OK '{row['singletonName']}' → activity {result[0]['activityAppId']}"
            )
        else:
            print(f"  WARN no result for '{row['singletonName']}' — skipped")

    print()
    print(f"Stamped {stamped} :Activity row(s).")
    driver.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
