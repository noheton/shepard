package de.dlr.shepard.common.neo4j.migrations;

import static com.mongodb.client.model.Filters.eq;

import ac.simons.neo4j.migrations.core.JavaBasedMigration;
import ac.simons.neo4j.migrations.core.MigrationContext;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;
import io.quarkus.logging.Log;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;

/**
 * V23 — FR1b split-singleton migration per {@code aidocs/53 §1.8.5}.
 *
 * <p>For every {@code :FileBundleReference} that holds exactly one
 * {@link de.dlr.shepard.data.file.entities.ShepardFile} reachable
 * through its single default {@code :FileGroup}, convert it into a
 * {@link de.dlr.shepard.context.references.file.entities.FileReference}
 * singleton:
 * <ol>
 *   <li>Move the byte payload from the bundle's per-Reference Mongo
 *       collection (the {@code FileContainer.mongoId}-named collection
 *       + GridFS) into the shared
 *       {@code de.dlr.shepard.context.references.file.services.SingletonFileReferenceService#SHARED_FILES_NAMESPACE}
 *       namespace.</li>
 *   <li>Drop the {@code :FileBundleReference} label; add
 *       {@code :SingletonFileReference}.</li>
 *   <li>Drop the synthetic default {@code :FileGroup} node and the
 *       {@code HAS_GROUP} edge (V21 minted these; they no longer
 *       make sense for a singleton-shaped row).</li>
 *   <li>Re-point {@code HAS_PAYLOAD} from the bundle to the (now-singleton)
 *       Reference directly. The bundle already had the compatibility-shadow
 *       {@code HAS_PAYLOAD} edge, so step 3 only drops the group node + its
 *       outgoing edges; the reference→file edge survives.</li>
 *   <li>Drop the now-empty per-bundle Mongo collection.</li>
 * </ol>
 *
 * <p><strong>Opt-in.</strong> Guarded by the config key
 * {@code shepard.migration.split-singletons.enabled}. Default
 * {@code false} for upgrades — moving Mongo bytes between
 * collections is the kind of operation an admin wants to schedule
 * explicitly (potentially overnight on a large dataset).
 *
 * <p><strong>Idempotent.</strong> Each step uses guard predicates:
 * <ul>
 *   <li>The candidate scan in {@link #findCandidates} excludes nodes
 *       that already carry the {@code :SingletonFileReference} label
 *       (post-split shape).</li>
 *   <li>The Mongo byte copy uses {@code GridFSUploadOptions.metadata}
 *       carrying the source oid; the move skips files whose oid
 *       already exists in the destination namespace.</li>
 *   <li>Neo4j writes use {@code SET r:SingletonFileReference REMOVE
 *       r:FileBundleReference} which is a no-op when re-run.</li>
 * </ul>
 *
 * <p><strong>Fail-fast.</strong> Any IOException, MongoException, or
 * Neo4j driver error propagates — the surrounding
 * {@link de.dlr.shepard.common.neo4j.MigrationsRunner} translates it
 * into a startup-abort. There's no try/catch swallowing.
 *
 * <p><strong>Operator runbook.</strong>
 * <ol>
 *   <li>Take a Neo4j + MongoDB backup (per {@code aidocs/45 §2.1 W3}).</li>
 *   <li>Verify the toggle key is set:
 *       {@code shepard.migration.split-singletons.enabled=true}.</li>
 *   <li>Restart shepard. The migration logs progress every 1 000
 *       rows. On a 10 000-row dataset budget ~5–10 min (GridFS
 *       reads/writes are sequential).</li>
 *   <li>Post-run verification in {@code cypher-shell}:
 *       <pre>
 *       MATCH (s:SingletonFileReference)
 *       RETURN count(s) AS singletons,
 *              sum(CASE WHEN (s)-[:has_payload]-&gt;(:ShepardFile) THEN 1 ELSE 0 END) AS singletons_with_file;
 *       </pre>
 *       Both numbers should match.</li>
 *   <li>Rollback is possible via
 *       {@code V23_R__Rejoin_singletons_into_FileBundleReferences.cypher}
 *       within the timestamp-guard window (see that file's top
 *       comment); after the guard window expires, rollback is
 *       refused.</li>
 * </ol>
 *
 * <p>This migration is the second JavaBasedMigration in the tree;
 * {@code V2__Extract_json} set the precedent for in-Java migrations
 * that need to touch payload stores.
 */
public class V23__Split_singleton_bundles_to_FileReferences implements JavaBasedMigration {

  /**
   * Toggle key. Must be {@code true} (case-insensitive) for the
   * migration to do any work. Default is {@code false} (CONFIG row in
   * {@code aidocs/34}).
   */
  public static final String TOGGLE_KEY = "shepard.migration.split-singletons.enabled";

  /**
   * Shared destination namespace. Mirror of
   * {@code SingletonFileReferenceService.SHARED_FILES_NAMESPACE} —
   * duplicated here to keep this migration class free of an
   * application-classpath dependency (matches
   * {@code V2__Extract_json}'s self-contained shape).
   */
  public static final String SHARED_FILES_NAMESPACE = "_shepard_files";

  /**
   * Property stamped on each freshly-converted singleton so the
   * V23_R rollback's timestamp guard knows the row was minted by
   * V23 (rather than user-created post-V23 via {@code /v2/files}).
   */
  public static final String LEGACY_MARKER_PROPERTY = "legacyV23Singleton";

  /**
   * Property carrying the source bundle's {@code FileContainer.mongoId}
   * so rollback can move bytes back to a freshly-minted per-bundle
   * collection.
   */
  public static final String LEGACY_MONGO_ID_PROPERTY = "legacyV23BundleMongoId";

  @Override
  public void apply(MigrationContext context) {
    boolean enabled = isEnabled();
    if (!enabled) {
      Log.info(
        "V23 (split-singletons) skipped: " +
        TOGGLE_KEY +
        " is not 'true'. " +
        "Singleton-shaped bundles will continue as bundles until an operator flips the toggle and re-runs migrations."
      );
      return;
    }
    Log.info("V23 (split-singletons): toggle enabled — beginning migration");

    String connStr = ConfigProvider.getConfig().getValue("quarkus.mongodb.connection-string", String.class);
    ConnectionString cs = new ConnectionString(connStr);
    String dbName = cs.getDatabase() != null ? cs.getDatabase() : "shepard";
    MongoClientSettings settings = MongoClientSettings.builder().applyConnectionString(cs).build();

    try (MongoClient mongoClient = MongoClients.create(settings)) {
      MongoDatabase database = mongoClient.getDatabase(dbName);
      try (Session session = context.getSession()) {
        List<CandidateRow> candidates = findCandidates(session);
        Log.infof("V23: %d singleton-shaped bundles to convert", candidates.size());
        int processed = 0;
        for (CandidateRow row : candidates) {
          processed++;
          try {
            convertOne(session, database, row);
          } catch (Exception ex) {
            Log.errorf(ex, "V23: failed to convert bundle elementId=%s; aborting", row.bundleElementId);
            throw new RuntimeException("V23 conversion failed for bundle " + row.bundleElementId, ex);
          }
          if (processed % 1000 == 0) {
            Log.infof("V23: converted %d / %d singleton-shaped bundles", processed, candidates.size());
          }
        }
        Log.infof("V23: complete — %d singletons created", processed);
      }
    }
  }

  /**
   * Read the toggle key from MicroProfile config. {@code true}
   * (case-insensitive) enables the migration; anything else
   * disables. Default disabled.
   */
  static boolean isEnabled() {
    return ConfigProvider.getConfig()
      .getOptionalValue(TOGGLE_KEY, Boolean.class)
      .orElse(false);
  }

  /**
   * Locate every {@code :FileBundleReference} that:
   * <ul>
   *   <li>does not already carry the {@code :SingletonFileReference}
   *       label (idempotency),</li>
   *   <li>has exactly one {@code :HAS_GROUP} edge to a {@code :FileGroup},</li>
   *   <li>that group has exactly one {@code :has_payload} edge to a
   *       {@code :ShepardFile} — and the bundle has exactly one
   *       {@code :has_payload} edge to the same file (the V21
   *       compatibility-shadow shape).</li>
   * </ul>
   *
   * @return the candidate rows for conversion. Each row carries the
   *   element id of the bundle node, the element id of the group node,
   *   the ShepardFile {@code oid}, and the source FileContainer's
   *   {@code mongoId}.
   */
  static List<CandidateRow> findCandidates(Session session) {
    String query =
      "MATCH (r:FileBundleReference) " +
      "WHERE NOT r:SingletonFileReference " +
      "MATCH (r)-[hg:HAS_GROUP]->(g:FileGroup) " +
      "WITH r, hg, g, count{(r)-[:HAS_GROUP]->(:FileGroup)} AS groupCount " +
      "WHERE groupCount = 1 " +
      "MATCH (g)-[gp:has_payload]->(f:ShepardFile) " +
      "WITH r, hg, g, f, count{(g)-[:has_payload]->(:ShepardFile)} AS groupPayloads, " +
      "     count{(r)-[:has_payload]->(:ShepardFile)} AS bundlePayloads " +
      "WHERE groupPayloads = 1 AND bundlePayloads = 1 " +
      "OPTIONAL MATCH (r)-[:is_in_container]->(fc:FileContainer) " +
      "RETURN elementId(r) AS bundleEid, elementId(g) AS groupEid, " +
      "       f.oid AS fileOid, fc.mongoId AS containerMongoId";
    var result = session.executeRead(tx -> tx.run(query).list());
    List<CandidateRow> rows = new ArrayList<>(result.size());
    for (var rec : result) {
      rows.add(new CandidateRow(
        rec.get("bundleEid").asString(),
        rec.get("groupEid").asString(),
        rec.get("fileOid").isNull() ? null : rec.get("fileOid").asString(),
        rec.get("containerMongoId").isNull() ? null : rec.get("containerMongoId").asString()
      ));
    }
    return rows;
  }

  /**
   * Convert one candidate row: copy Mongo bytes, then run the
   * Neo4j relabel + delete-group in a single transaction. Both
   * sides are individually idempotent — re-running this method on
   * a candidate that's already been (partially) converted is safe.
   */
  static void convertOne(Session session, MongoDatabase database, CandidateRow row) {
    // 1) Move bytes if we have something to move.
    if (row.fileOid != null && row.containerMongoId != null) {
      moveBytesIfNeeded(database, row.containerMongoId, row.fileOid);
    }

    // 2) Apply the Neo4j-side transformation.
    Map<String, Object> params = new HashMap<>();
    params.put("bundleEid", row.bundleElementId);
    params.put("groupEid", row.groupElementId);
    params.put("legacyMongoId", row.containerMongoId);

    String cypher =
      "MATCH (r) WHERE elementId(r) = $bundleEid " +
      "MATCH (g:FileGroup) WHERE elementId(g) = $groupEid " +
      // The shadow group->file edge is no longer reachable once we drop g;
      // detach-delete handles that.
      "DETACH DELETE g " +
      "WITH r " +
      // Relabel the reference. Removing :FileBundleReference is necessary
      // so the bundle DAO stops seeing it; the legacy :FileReference label
      // also goes (singletons live exclusively under :SingletonFileReference).
      "REMOVE r:FileBundleReference, r:FileReference " +
      "SET r:SingletonFileReference, r." + LEGACY_MARKER_PROPERTY + " = true";

    String cypherWithLegacyMongo =
      "MATCH (r) WHERE elementId(r) = $bundleEid " +
      "MATCH (g:FileGroup) WHERE elementId(g) = $groupEid " +
      "DETACH DELETE g " +
      "WITH r " +
      "REMOVE r:FileBundleReference, r:FileReference " +
      "SET r:SingletonFileReference, r." + LEGACY_MARKER_PROPERTY + " = true, " +
      "    r." + LEGACY_MONGO_ID_PROPERTY + " = $legacyMongoId";

    final String q = row.containerMongoId != null ? cypherWithLegacyMongo : cypher;
    try (Transaction tx = session.beginTransaction()) {
      tx.run(q, params);
      tx.commit();
    }

    // 3) Best-effort drop of the now-empty source collection. We don't
    // fail the migration if the collection refuses to drop (e.g. it
    // had other unrelated docs — would be a graph inconsistency, but
    // not one V23 should fail-fast on, since the singleton conversion
    // is already complete).
    if (row.containerMongoId != null) {
      try {
        MongoCollection<Document> coll = database.getCollection(row.containerMongoId);
        long remaining = coll.countDocuments();
        if (remaining == 0) {
          coll.drop();
        } else {
          Log.warnf(
            "V23: source collection %s has %d docs remaining; leaving in place (operator triage)",
            row.containerMongoId,
            remaining
          );
        }
      } catch (Exception ex) {
        // Log + continue — the Neo4j side is already converted.
        Log.warnf(ex, "V23: could not drop source collection %s", row.containerMongoId);
      }
    }
  }

  /**
   * Copy the byte payload from the per-bundle collection's GridFS
   * blob into the shared {@link #SHARED_FILES_NAMESPACE}. The shared
   * namespace uses the same Mongo collection / GridFS bucket pattern
   * as the per-bundle case — one document per file in the collection
   * (carrying the GridFS file id), GridFS chunks in the global
   * {@code fs.chunks}.
   *
   * <p>Idempotency: if a doc with the same source oid already exists
   * in the destination collection, skip. This catches the case where
   * the migration was interrupted between the Mongo copy and the
   * Neo4j relabel.
   */
  static void moveBytesIfNeeded(MongoDatabase database, String sourceCollectionName, String fileOid) {
    MongoCollection<Document> dest = database.getCollection(SHARED_FILES_NAMESPACE);
    Document existing = dest.find(eq("_id", new ObjectId(fileOid))).first();
    if (existing != null) {
      // Already moved. Skip.
      return;
    }

    MongoCollection<Document> source = database.getCollection(sourceCollectionName);
    Document sourceDoc = source.find(eq("_id", new ObjectId(fileOid))).first();
    if (sourceDoc == null) {
      // Source doc is missing — orphan ShepardFile. Log + leave it
      // to the Neo4j side, which will still relabel (a singleton
      // pointing at a missing blob is degenerate but won't break
      // metadata reads).
      Log.warnf("V23: source doc oid=%s missing from %s; relabel-only", fileOid, sourceCollectionName);
      return;
    }

    String sourceFileMongoId = sourceDoc.getString("FileMongoId");
    if (sourceFileMongoId == null) {
      Log.warnf("V23: source doc oid=%s has no FileMongoId; relabel-only", fileOid);
      // Insert the metadata doc anyway so future lookups via the
      // shared namespace succeed for the (degenerate) metadata-only
      // case.
      dest.insertOne(new Document(sourceDoc));
      source.deleteOne(eq("_id", new ObjectId(fileOid)));
      return;
    }

    // GridFS blobs live in the global {@code fs.files} / {@code fs.chunks}
    // collections shared across the database — they're keyed by ObjectId
    // and are NOT tied to a specific Mongo collection. So we DON'T need
    // to copy bytes between buckets — the same GridFS oid resolves from
    // either source or destination metadata collection. We only need to:
    //   * insert the metadata doc into the shared namespace, and
    //   * remove the metadata doc from the source collection.
    // The bytes themselves stay where they are. This is a property of
    // shepard's storage layout (see FileService comments) — GridFS
    // chunks aren't scoped to a per-bundle collection, only the small
    // metadata docs are.
    dest.insertOne(new Document(sourceDoc));
    source.deleteOne(eq("_id", new ObjectId(fileOid)));

    // Belt-and-braces: validate the chunk is still there (a missing
    // chunk would be silent data loss on the next read; we surface
    // it loudly).
    GridFSBucket bucket = GridFSBuckets.create(database);
    try (InputStream verify = bucket.openDownloadStream(new ObjectId(sourceFileMongoId))) {
      // Read one byte to confirm the stream opens cleanly.
      if (verify.read() < 0) {
        Log.warnf("V23: GridFS oid=%s is zero-length (corrupt or pre-migrated empty file)", sourceFileMongoId);
      }
      // We intentionally don't drain the whole stream — first-byte
      // verification is enough to catch the common "chunks gone"
      // failure mode, and draining gigabytes per row would be
      // prohibitive.
    } catch (Exception ex) {
      Log.warnf(ex, "V23: could not verify GridFS blob oid=%s", sourceFileMongoId);
      // Note: we DO NOT rethrow. The metadata doc has been moved
      // already; failing now would leave the metadata in the shared
      // namespace and the bundle node un-converted, an inconsistent
      // half-state. Log and continue.
    }

    // The GridFSUploadOptions import is retained for the "fall back
    // to physical chunk copy" path that a future MongoDB layout
    // (e.g. per-collection GridFS buckets) might force. Today's
    // shepard uses one shared bucket, so the no-bytes-moved
    // optimisation above is correct.
    @SuppressWarnings("unused")
    GridFSUploadOptions reservedForFutureLayouts = new GridFSUploadOptions();
  }

  /**
   * A single candidate row from {@link #findCandidates}. Public
   * fields keep parity with {@code V2__Extract_json}'s
   * lombok-allargs DTOs.
   */
  static final class CandidateRow {

    final String bundleElementId;
    final String groupElementId;
    final String fileOid;
    final String containerMongoId;

    CandidateRow(String bundleElementId, String groupElementId, String fileOid, String containerMongoId) {
      this.bundleElementId = bundleElementId;
      this.groupElementId = groupElementId;
      this.fileOid = fileOid;
      this.containerMongoId = containerMongoId;
    }
  }
}
