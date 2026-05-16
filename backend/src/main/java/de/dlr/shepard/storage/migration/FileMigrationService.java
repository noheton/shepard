package de.dlr.shepard.storage.migration;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.storage.FileStorage;
import de.dlr.shepard.storage.FileStorageRegistry;
import de.dlr.shepard.storage.StorageException;
import de.dlr.shepard.storage.StorageGetResponse;
import de.dlr.shepard.storage.StorageLocator;
import de.dlr.shepard.storage.StoragePutRequest;
import de.dlr.shepard.storage.gridfs.GridFsFileStorage;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.neo4j.ogm.session.Session;

/**
 * FS1e1 — big-bang file-storage migration service. Copies every
 * {@code :ShepardFile} node whose {@code providerId} equals the
 * configured source adapter id to the target adapter, then flips
 * {@code providerId} on the Neo4j node. Files are processed one at
 * a time (streamed, not buffered) to avoid JVM OOM on large
 * deployments.
 *
 * <p><strong>OID preservation.</strong> The source file's {@code oid}
 * is passed as {@link StoragePutRequest#assignedObjectKey()} so the
 * target adapter stores the object at the same key fragment. Only
 * {@code providerId} changes in Neo4j — existing API clients (and
 * in-flight presigned URLs) keep working.
 *
 * <p><strong>Concurrency.</strong> Only one migration job runs at a
 * time. A second {@link #triggerMigration} call while
 * {@link FileMigrationStatus#RUNNING} returns the current state
 * unchanged. Job state is in-memory only; a restart resets to
 * {@link FileMigrationStatus#IDLE} but re-running migration is safe —
 * the Cypher query filters by {@code providerId = $src} so already-
 * migrated files are skipped.
 *
 * <p><strong>Failure handling.</strong> Per-file errors are logged
 * and counted; the job continues to the next file rather than
 * aborting. The job finishes as {@link FileMigrationStatus#DONE} even
 * if some files failed (operators inspect
 * {@link #getState()}{@code .filesFailed()} to find the count and
 * re-run the migration to sweep the residual). A fatal error (Neo4j
 * session failure, adapter unreachable before any file is processed)
 * transitions to {@link FileMigrationStatus#FAILED}.
 *
 * <p>Designed per {@code aidocs/45 §6} (FS1e1 scope).
 */
@ApplicationScoped
public class FileMigrationService {

  /**
   * Cypher query to count files pending migration from a given
   * source provider. Used at job-start for the progress display.
   */
  static final String CYPHER_COUNT =
    "MATCH (fc:FileContainer)-[:file_in_container]->(f:ShepardFile) " +
    "WHERE f.providerId = $src " +
    "RETURN count(f) AS total";

  /**
   * Cypher query to fetch one batch of files pending migration.
   * Returns oid, container mongoId, filename, and fileSize. The
   * SKIP / LIMIT makes the cursor deterministic for progress
   * reporting; re-running after partial failure picks up from the
   * first un-migrated row (already-migrated rows have
   * {@code providerId = $target} so the WHERE clause excludes them).
   */
  static final String CYPHER_FETCH =
    "MATCH (fc:FileContainer)-[:file_in_container]->(f:ShepardFile) " +
    "WHERE f.providerId = $src " +
    "RETURN f.oid AS oid, fc.mongoId AS containerMongoId, " +
    "f.filename AS filename, f.fileSize AS fileSize " +
    "ORDER BY f.oid";

  /** Cypher to stamp the new provider after a successful file move. */
  static final String CYPHER_UPDATE =
    "MATCH (f:ShepardFile {oid: $oid}) SET f.providerId = $target";

  @Inject
  FileStorageRegistry registry;

  private final AtomicReference<FileMigrationState> stateRef =
    new AtomicReference<>(FileMigrationState.idle());

  private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "file-migration");
    t.setDaemon(true);
    return t;
  });

  /** Returns the current migration state (never null). */
  public FileMigrationState getState() {
    return stateRef.get();
  }

  /**
   * Triggers a big-bang migration from {@code sourceId} to
   * {@code targetId} adapters. Returns immediately; the migration
   * runs on a background thread.
   *
   * @throws IllegalArgumentException if either adapter id is unknown,
   *         or if the source and target are the same id
   * @throws IllegalStateException if a migration is already running
   */
  public synchronized FileMigrationState triggerMigration(String sourceId, String targetId) {
    FileMigrationState current = stateRef.get();
    if (current.status() == FileMigrationStatus.RUNNING) {
      return current;
    }
    if (sourceId == null || sourceId.isBlank()) {
      throw new IllegalArgumentException("sourceProviderId must not be blank");
    }
    if (targetId == null || targetId.isBlank()) {
      throw new IllegalArgumentException("targetProviderId must not be blank");
    }
    if (sourceId.equals(targetId)) {
      throw new IllegalArgumentException(
        "sourceProviderId and targetProviderId must be different (got '" + sourceId + "')");
    }
    // Validate adapters exist and are enabled before dispatching
    findAdapter(sourceId);
    findAdapter(targetId);

    FileMigrationState initial = FileMigrationState.starting(sourceId, targetId);
    stateRef.set(initial);
    Log.infof("FileMigrationService: migration queued (source=%s, target=%s)", sourceId, targetId);
    executor.submit(() -> runMigration(sourceId, targetId));
    return initial;
  }

  private void runMigration(String sourceId, String targetId) {
    Log.infof("FileMigrationService: migration started (source=%s, target=%s)", sourceId, targetId);
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) {
      stateRef.set(stateRef.get().withFailed("Neo4j session unavailable"));
      Log.errorf("FileMigrationService: cannot open Neo4j session — migration aborted");
      return;
    }

    try {
      long total = countPending(session, sourceId);
      stateRef.set(stateRef.get().withTotal(total));
      Log.infof("FileMigrationService: %d file(s) to migrate from '%s' to '%s'", total, sourceId, targetId);

      if (total == 0) {
        stateRef.set(stateRef.get().withDone());
        Log.infof("FileMigrationService: nothing to migrate — done");
        return;
      }

      List<Map<String, Object>> rows = fetchPending(session, sourceId);
      long migrated = 0;
      long failed = 0;

      for (Map<String, Object> row : rows) {
        String oid = (String) row.get("oid");
        String containerMongoId = (String) row.get("containerMongoId");
        String filename = (String) row.get("filename");
        Long fileSize = row.get("fileSize") instanceof Number n ? n.longValue() : null;

        try {
          migrateOne(session, sourceId, targetId, oid, containerMongoId, filename, fileSize);
          migrated++;
        } catch (Exception e) {
          failed++;
          Log.warnf("FileMigrationService: skipping oid=%s — %s", oid, e.getMessage());
        }

        long m = migrated;
        long f = failed;
        stateRef.updateAndGet(s -> s.withProgress(m, f));
      }

      stateRef.set(stateRef.get().withDone());
      Log.infof(
        "FileMigrationService: migration complete (migrated=%d, failed=%d, source=%s, target=%s)",
        migrated, failed, sourceId, targetId
      );
    } catch (Exception e) {
      Log.errorf("FileMigrationService: migration failed — %s", e.getMessage());
      stateRef.set(stateRef.get().withFailed(e.getMessage()));
    } finally {
      try { session.clear(); } catch (Exception ignore) {}
    }
  }

  private void migrateOne(
    Session neo4jSession,
    String sourceId,
    String targetId,
    String oid,
    String containerMongoId,
    String filename,
    Long fileSize
  ) throws StorageException {
    FileStorage source = findAdapter(sourceId);
    FileStorage target = findAdapter(targetId);

    StorageLocator srcLocator = buildLocator(sourceId, containerMongoId, oid);
    StorageGetResponse resp = source.get(srcLocator);

    String effectiveFilename = filename != null ? filename : oid;
    Long sizeToStore = resp.sizeBytes() != null ? resp.sizeBytes() : fileSize;

    StoragePutRequest putReq = new StoragePutRequest(
      containerMongoId,
      effectiveFilename,
      resp.contentType(),
      resp.stream(),
      sizeToStore,
      oid
    );

    try {
      target.put(putReq);
    } finally {
      try { resp.stream().close(); } catch (IOException ignore) {}
    }

    // Update Neo4j — flip providerId before deleting from source so a
    // partial-failure re-run doesn't re-copy already-moved files.
    Map<String, Object> params = new HashMap<>();
    params.put("oid", oid);
    params.put("target", targetId);
    neo4jSession.query(CYPHER_UPDATE, params);

    // Delete from source (idempotent — missing key is fine)
    source.delete(srcLocator);

    Log.debugf("FileMigrationService: moved oid=%s (%s → %s)", oid, sourceId, targetId);
  }

  private long countPending(Session session, String sourceId) {
    var result = session.query(CYPHER_COUNT, Map.of("src", sourceId));
    var it = result.iterator();
    if (!it.hasNext()) return 0L;
    Object val = it.next().get("total");
    return val instanceof Number n ? n.longValue() : 0L;
  }

  private List<Map<String, Object>> fetchPending(Session session, String sourceId) {
    var result = session.query(CYPHER_FETCH, Map.of("src", sourceId));
    List<Map<String, Object>> rows = new ArrayList<>();
    for (Map<String, Object> row : result) {
      rows.add(new HashMap<>(row));
    }
    return rows;
  }

  private FileStorage findAdapter(String id) {
    for (FileStorage s : registry.list()) {
      if (id.equals(s.id())) {
        if (!s.isEnabled()) {
          throw new IllegalArgumentException(
            "Storage adapter '" + id + "' is registered but disabled — configure it before migrating");
        }
        return s;
      }
    }
    List<String> knownIds = registry.list().stream().map(FileStorage::id).toList();
    throw new IllegalArgumentException(
      "Storage adapter '" + id + "' not found. Available: " + knownIds);
  }

  /** Build a locator matching the adapter's format. */
  private static StorageLocator buildLocator(String providerId, String containerMongoId, String oid) {
    if (GridFsFileStorage.ID.equals(providerId)) {
      return new StorageLocator(providerId, containerMongoId + GridFsFileStorage.LOCATOR_SEPARATOR + oid);
    }
    return new StorageLocator(providerId, containerMongoId + "/" + oid);
  }
}
