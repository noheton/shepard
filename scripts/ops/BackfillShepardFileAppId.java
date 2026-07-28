// =============================================================================
// OPERATOR OPS PROGRAM — NOT a startup migration. Run MANUALLY, offline.
// CHILD-APPID-BACKFILL  (aidocs/16)  —  2026-07-28
// =============================================================================
//
// WHAT: Mint a UUID v7 `appId` for every v4-legacy `:ShepardFile` node that has
//       NULL appId. Additive + nullable-compatible (CLAUDE.md schema rule): the
//       write-path (FileStorageService.storeFile) already mints appId for NEW
//       ShepardFile/Timeseries nodes since commit e7d2c7219; this backfills the
//       historical population only.
//
// GROUND TRUTH (measured 2026-07-28, substrate-direct):
//       :ShepardFile with NULL appId .................. 567,658
//       :ShepardFile with appId set ...................  155,762
//       Unique constraint `appId_unique_ShepardFile` already exists (additive).
//
// WHY JAVA, NOT PURE CYPHER (this is the crux):
//   The appId contract is RFC 9562 UUID **v7**: 48-bit big-endian Unix-ms
//   timestamp in the high bits + version nibble 0b0111 + variant bits + random.
//   That time-ordered shape is what AppIdGenerator.next() guarantees
//   (com.github.f4b6a3.uuid UuidCreator.getTimeOrderedEpoch()) and what cursor
//   pagination (aidocs/25 L2, aidocs/12 §11.A.2) and lexicographic sort depend on.
//   Neo4j `randomUUID()` and APOC `apoc.create.uuid()` produce UUID **v4**
//   (pure random) — verified 2026-07-28 that APOC 5.26 ships ONLY the v4 form
//   (apoc.create.uuid / .uuids / .uuidBase64). A v4 here would (a) violate the
//   "every persisted entity carries ONE stable shepardId [v7]" invariant, mixing
//   two UUID versions in one column, and (b) break the sortable/time-ordered
//   property every OTHER appId in the graph has. => appId MUST be minted by the
//   backend's AppIdGenerator (or the identical f4b6a3 library), i.e. in the JVM.
//
// WHY A JVM DRIVER LOOP, NOT `CALL {} IN TRANSACTIONS`:
//   The mint cannot happen inside Cypher, so the batching lives in this driver:
//   read a page of null-appId node ids -> mint v7 per id in the JVM -> UNWIND a
//   $pairs list into one SET statement per batch. The SET itself is O(1)/row
//   (index-free property write); the driving scan is a one-time :ShepardFile
//   label scan (PROFILE 2026-07-28: ~2 DbHits/row, linear). As appIds are set,
//   the `WHERE f.appId IS NULL` predicate matches fewer each pass => naturally
//   CONVERGENT and idempotent: re-run until it reports 0.
//
// HARD PREREQUISITES:
//   1. Ingest paused (avoids racing the write-path mint on the same nodes —
//      harmless if it races, since both guard on appId IS NULL, but cleaner).
//   2. Runs from a host with the backend classpath (the f4b6a3 uuid-creator jar
//      + neo4j-java-driver are already backend deps) OR ship this as a one-shot
//      `shepard-admin files backfill-appid` CLI command (RECOMMENDED long-term —
//      reuses AppIdGenerator directly, no separate classpath wiring; file a
//      REF-/CHILD-APPID-CLI backlog row).
//
// SCHEMA-ADDITIVE + NULLABLE + NEW-NAMESPACE compliant: adds a property, never
//   mutates existing ones; the unique constraint already tolerates the nulls
//   (Neo4j unique constraints ignore NULLs). No rollback file needed — additive.
//   To undo (only if reverting the whole feature):
//     CALL { MATCH (f:ShepardFile) WHERE f.appId IS NOT NULL AND <minted-marker>
//            REMOVE f.appId } IN TRANSACTIONS OF 5000 ROWS
//   (there is no minted-marker today; if a clean rollback is ever required, add a
//   transient `appIdBackfilledAt` stamp in the SET below and gate the REMOVE on it.)
//
// COMPILE + RUN (example; adjust classpath to the built backend jar):
//   BOLT=bolt://localhost:7687   # inside the compose net use infrastructure-neo4j-1
//   NEOPW=$(docker inspect infrastructure-neo4j-1 | grep -oE 'NEO4J_AUTH=[^"]+' | head -1 | cut -d/ -f2)
//   javac -cp "backend/target/quarkus-app/lib/main/*" scripts/ops/BackfillShepardFileAppId.java -d /tmp/ops
//   java  -cp "/tmp/ops:backend/target/quarkus-app/lib/main/*" \
//         de.dlr.shepard.ops.BackfillShepardFileAppId "$BOLT" neo4j "$NEOPW"
//
// EXPECTED: ~567,658 nodes / batches of 5,000 = ~114 batches. Prints running
//   total; final line "DONE, 0 remaining".
// =============================================================================
package de.dlr.shepard.ops;

import com.github.f4b6a3.uuid.UuidCreator;   // SAME lib AppIdGenerator uses
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

public final class BackfillShepardFileAppId {

  private static final int BATCH = 5_000;

  public static void main(String[] args) {
    if (args.length < 3) {
      System.err.println("usage: BackfillShepardFileAppId <boltUri> <user> <password>");
      System.exit(2);
    }
    long total = 0;
    try (Driver driver = GraphDatabase.driver(args[0], AuthTokens.basic(args[1], args[2]));
        Session session = driver.session()) {
      while (true) {
        // 1. Read one page of node ids that still lack an appId (label scan; the
        //    unique index cannot seek NULLs, so this is a bounded LIMIT scan).
        List<Long> ids =
            session.executeRead(tx ->
                tx.run("MATCH (f:ShepardFile) WHERE f.appId IS NULL RETURN id(f) AS id LIMIT $n",
                        Map.of("n", BATCH))
                  .list(r -> r.get("id").asLong()));
        if (ids.isEmpty()) break;

        // 2. Mint a spec-correct UUID v7 per id, in the JVM (== AppIdGenerator.next()).
        List<Map<String, Object>> pairs = new ArrayList<>(ids.size());
        for (Long id : ids) {
          pairs.add(Map.of("id", id, "appId", UuidCreator.getTimeOrderedEpoch().toString()));
        }

        // 3. One SET statement per batch = one transaction (commit releases, bounds heap).
        //    Guard on appId IS NULL keeps it idempotent even if a page overlaps a re-run.
        int written =
            session.executeWrite(tx ->
                tx.run("UNWIND $pairs AS p MATCH (f:ShepardFile) WHERE id(f)=p.id AND f.appId IS NULL "
                        + "SET f.appId = p.appId RETURN count(f) AS c",
                        Map.of("pairs", pairs))
                  .single().get("c").asInt());

        total += written;
        System.out.printf("batch=%d written=%d total=%d%n", ids.size(), written, total);
      }

      long remaining =
          session.executeRead(tx ->
              tx.run("MATCH (f:ShepardFile) WHERE f.appId IS NULL RETURN count(f) AS c")
                .single().get("c").asLong());
      System.out.printf("DONE, %d remaining (backfilled %d)%n", remaining, total);
      if (remaining != 0) System.exit(1);   // fail-fast surfacing
    }
  }

  private BackfillShepardFileAppId() {}
}
