# Single-file FileBundleReference → SingletonFileReference migration

**Runbook for SINGLETON-FILE-MIGRATION.** Convert `:FileBundleReference`
rows that hold exactly one file into native FR1b
`:SingletonFileReference` rows, preserving every appId so incoming links
keep resolving.

## When to run this

- A `scripts/audit-single-file-bundles.py` report shows a non-zero
  "Singleton-shaped bundles (debt)" count.
- An operator-visible bug surfaces because a downstream resolver
  (URDF, KRL interpret, scene-graph) misses a file. (The canonical
  instance was 2026-05-30: the kr210-r2700-urdf showcase. See the
  rule in CLAUDE.md "Always: singleton FileReference for one-file
  uploads; FileBundleReference only when bundling >1".)

## Why this is safe

The conversion is **in-place**: `V23__Split_singleton_bundles_to_FileReferences.java`
relabels the existing graph node (`FileBundleReference → SingletonFileReference`)
without minting a new appId. Every incoming reference — REST callers,
semantic annotations, MCP tool arguments — continues to resolve. The
Mongo metadata doc is moved from the per-bundle collection into the
shared `_shepard_files` namespace; the GridFS bytes stay put (the same
ObjectId resolves from either side per the shared-bucket layout).

The migration is also reversible within a guard window via
`V23_R__Rejoin_singletons_into_FileBundleReferences.cypher`.

## Step-by-step

### 1. Snapshot Neo4j + MongoDB

Per `docs/admin/runbooks/04-restore-neo4j.md` (Neo4j) and the standard
Mongo dump pattern:

```bash
# Neo4j
docker exec infrastructure-neo4j-1 \
  neo4j-admin database dump neo4j --to-path=/data/snapshots/

# MongoDB (the GridFS bucket lives here too)
docker exec infrastructure-mongodb-1 \
  mongodump --archive=/data/snapshots/mongo-pre-v23.archive --db=shepard
```

### 2. Run the audit

```bash
uv run scripts/audit-single-file-bundles.py
```

The report writes to `aidocs/agent-findings/singleton-file-audit-<date>.md`.
Verify the candidate count + per-collection breakdown matches your
expectations before flipping the toggle.

### 3. Enable the V23 toggle

In `application.properties` (deploy-time) or via the equivalent env var:

```properties
shepard.migration.split-singletons.enabled=true
```

This is opt-in by default because moving Mongo metadata at scale can
take several minutes on a large dataset (V23 logs progress every
1000 rows).

### 4. Restart the backend

```bash
docker compose restart backend
# or, for a clean image rebuild:
make redeploy-backend
```

V23 runs during the startup migration sweep. Watch the logs:

```bash
docker logs -f infrastructure-backend-1 2>&1 | grep -E "V23"
```

You should see lines like:

```
INFO  [de.dlr.she.com.neo.mig.V23] (...) V23 (split-singletons): toggle enabled — beginning migration
INFO  [de.dlr.she.com.neo.mig.V23] (...) V23: 142 singleton-shaped bundles to convert
INFO  [de.dlr.she.com.neo.mig.V23] (...) V23: converted 142 / 142 singleton-shaped bundles
INFO  [de.dlr.she.com.neo.mig.V23] (...) V23: complete — 142 singletons created
```

### 5. Verify

```cypher
MATCH (s:SingletonFileReference)
WHERE s.legacyV23Singleton = true
RETURN count(s) AS converted;

MATCH (s:SingletonFileReference)
RETURN count(s) AS singletons,
       sum(CASE WHEN (s)-[:has_payload]->(:ShepardFile) THEN 1 ELSE 0 END)
         AS singletons_with_file;
```

Both numbers in the second query should match.

### 6. Emit PROV-O Activities

```bash
# Dry-run first (default mode of the backfill script).
uv run scripts/backfill-single-file-bundles-to-singletons.py

# Then commit:
uv run scripts/backfill-single-file-bundles-to-singletons.py --commit
```

One `:Activity {kind: 'SINGLETON_FILE_MIGRATION'}` lands per converted
singleton. The activity feed UI surfaces them under "system" actor by
default; set `ACTOR_USER_ID` if you want a custom attribution. The
audit feed query is the standing "graph not log" rule —
`MATCH (a:Activity {kind: 'SINGLETON_FILE_MIGRATION'}) RETURN a`.

### 7. Re-audit

```bash
uv run scripts/audit-single-file-bundles.py
```

The "Singleton-shaped bundles (debt)" count should drop to 0.

## Rollback

Within the V23_R timestamp-guard window (see the rollback file's top
comment for the window definition):

```bash
docker exec infrastructure-neo4j-1 \
  cypher-shell -u neo4j -p $NEO4J_PW \
  -f /var/lib/neo4j/migrations/V23_R__Rejoin_singletons_into_FileBundleReferences.cypher
```

After the guard window expires, rollback is refused by design — the
relabeled rows have a presumption of being intentional from the
operator's perspective and the rejoin would silently undo unrelated
operator decisions.

## Known limitations

- V23 leaves a `legacyV23Mongo Id` property on each converted row so
  the rollback knows which per-bundle Mongo collection to recreate. The
  property is informational only; it can be removed after the
  guard-window has expired:

  ```cypher
  MATCH (s:SingletonFileReference {legacyV23Singleton: true})
  REMOVE s.legacyV23BundleMongoId
  RETURN count(s);
  ```

- The per-bundle Mongo source collections are best-effort dropped at the
  end of V23. If a collection had stray docs (a graph inconsistency)
  the drop is skipped and a `V23: source collection X has Y docs
  remaining; leaving in place (operator triage)` warning is emitted.

## Cross-references

- `aidocs/16-dispatcher-backlog.md` §SINGLETON-FILE-MIGRATION
- `backend/src/main/java/de/dlr/shepard/common/neo4j/migrations/V23__Split_singleton_bundles_to_FileReferences.java`
- `backend/src/main/resources/neo4j/migrations/V23_R__Rejoin_singletons_into_FileBundleReferences.cypher`
- CLAUDE.md "Always: singleton FileReference for one-file uploads;
  FileBundleReference only when bundling >1"
- `docs/reference/file-reference.md` (user-facing FR1b reference page)
