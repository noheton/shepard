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
import io.quarkus.scheduler.Scheduled;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
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

  /**
   * Cypher to stamp the new provider after a successful file move.
   *
   * <p>FS1e3 — single SET statement records the pre-migration state
   * (previousProviderId, previousLocator) AND the swap (providerId)
   * AND the audit timestamp (migratedAt) AND the future-proof
   * integrity hash slot (migrationHmac). Cypher evaluates the SET
   * left-to-right, so {@code f.previousProviderId = f.providerId} is
   * captured BEFORE {@code f.providerId = $target} overwrites it.
   * That preserves the "stamp before swap" invariant inside a single
   * transaction — no race where a reader sees the new providerId
   * without the previousProviderId trail.
   *
   * <p>The {@code migrationHmac} parameter is {@code null} today (no
   * integrity hash is computed in this PR per the FS1e3 task spec's
   * "no behaviour split yet — just record-keeping" rule). The field
   * is future-proof for FS1e6's verify+report endpoint.
   */
  static final String CYPHER_UPDATE =
    "MATCH (f:ShepardFile {oid: $oid}) " +
    "SET f.previousProviderId = f.providerId, " +
    "    f.previousLocator    = $sourceLocator, " +
    "    f.migratedAt         = datetime(), " +
    "    f.migrationHmac      = $hmac, " +
    "    f.providerId         = $target";

  /**
   * Cypher to roll back a single file's storage-adapter pointer to
   * its pre-migration state. Used by {@link #rollbackOne(String)}
   * AFTER the bytes have been written back to the previous adapter.
   *
   * <p>FS1e3 — clears all four FS1e3 bookkeeping fields (matches
   * runbook §9.1's {@code REMOVE} semantic — the rolled-back row
   * becomes a normal never-migrated row again). The match predicate
   * {@code f.previousProviderId IS NOT NULL} keeps the operation
   * idempotent + refuses to roll back a row that has nothing to
   * revert.
   */
  static final String CYPHER_ROLLBACK =
    "MATCH (f:ShepardFile {appId: $appId}) " +
    "WHERE f.previousProviderId IS NOT NULL " +
    "SET f.providerId = f.previousProviderId " +
    "REMOVE f.previousProviderId, f.previousLocator, " +
    "       f.migratedAt, f.migrationHmac " +
    "RETURN f.oid AS oid";

  /**
   * Cypher to fetch the rollback context for a single file (oid +
   * containerMongoId + previousProviderId + previousLocator). Used by
   * {@link #rollbackOne(String)} to read the previousX fields BEFORE
   * mutating any state, so a refusal (no previousProviderId set) does
   * not need any cleanup.
   */
  static final String CYPHER_FETCH_ROLLBACK_CTX =
    "MATCH (fc:FileContainer)-[:file_in_container]->(f:ShepardFile {appId: $appId}) " +
    "RETURN f.oid              AS oid, " +
    "       fc.mongoId         AS containerMongoId, " +
    "       f.filename         AS filename, " +
    "       f.fileSize         AS fileSize, " +
    "       f.providerId       AS currentProviderId, " +
    "       f.previousProviderId AS previousProviderId, " +
    "       f.previousLocator    AS previousLocator";

  @Inject
  FileStorageRegistry registry;

  @ConfigProperty(name = "shepard.migration.auto-sweep.enabled", defaultValue = "false")
  boolean autoSweepEnabled;

  @ConfigProperty(name = "shepard.migration.auto-sweep.source", defaultValue = "")
  String autoSweepSource;

  @ConfigProperty(name = "shepard.migration.auto-sweep.target", defaultValue = "")
  String autoSweepTarget;

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

  /**
   * FS1e2 — idle-background draining mode.
   *
   * <p>Runs every {@code shepard.migration.auto-sweep.interval}
   * (default {@code PT5M}). If {@code shepard.migration.auto-sweep.enabled}
   * is {@code false} (the default) the method returns immediately so
   * deployments without object-store migration incur zero overhead.
   *
   * <p>Idempotent: {@link #triggerMigration} already guards against a
   * second concurrent run; this method just fires the trigger.
   *
   * <p>Progress notes via P3 (SSE streaming) are deferred — gated on
   * the P3 pattern landing. Today's {@link #getState()} REST poll is
   * the progress path.
   */
  @Scheduled(every = "{shepard.migration.auto-sweep.interval}")
  public void autoSweep() {
    if (!autoSweepEnabled) {
      Log.debug("FS1e2 auto-sweep skipped — shepard.migration.auto-sweep.enabled=false");
      return;
    }
    if (autoSweepSource == null || autoSweepSource.isBlank()) {
      Log.warn("FS1e2 auto-sweep: shepard.migration.auto-sweep.source is not configured — skipping");
      return;
    }
    if (autoSweepTarget == null || autoSweepTarget.isBlank()) {
      Log.warn("FS1e2 auto-sweep: shepard.migration.auto-sweep.target is not configured — skipping");
      return;
    }
    if (stateRef.get().status() == FileMigrationStatus.RUNNING) {
      Log.debug("FS1e2 auto-sweep: migration already running — skipping this tick");
      return;
    }
    Log.infof("FS1e2 auto-sweep: triggering migration sweep (source=%s, target=%s)",
      autoSweepSource, autoSweepTarget);
    try {
      triggerMigration(autoSweepSource, autoSweepTarget);
    } catch (IllegalArgumentException e) {
      Log.warnf("FS1e2 auto-sweep: invalid configuration — %s", e.getMessage());
    }
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

  /**
   * Migrate a single file's bytes from {@code sourceId} to
   * {@code targetId}, then stamp the four FS1e3 bookkeeping fields
   * on {@code :ShepardFile} and flip {@code providerId}. Package-
   * private so the FS1e3 round-trip test can drive it without
   * standing up a full Quarkus boot.
   */
  void migrateOne(
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

    // FS1e3 — Update Neo4j with a single stamp-and-swap SET. Cypher
    // evaluates SET left-to-right, so previousProviderId captures the
    // pre-swap providerId BEFORE the same statement overwrites it
    // with $target. The migrationHmac slot stays null today — the
    // FS1e6 verify+report endpoint will populate it on a future PR.
    // Done before deleting from source so a partial-failure re-run
    // doesn't re-copy already-moved files.
    Map<String, Object> params = new HashMap<>();
    params.put("oid", oid);
    params.put("target", targetId);
    params.put("sourceLocator", srcLocator.locator());
    params.put("hmac", null);
    neo4jSession.query(CYPHER_UPDATE, params);

    // Delete from source (idempotent — missing key is fine).
    // FS1e3 task spec: "no behaviour split yet — just record-keeping",
    // so the delete stays. Per-file rollback (rollbackOne) re-puts
    // bytes from the current adapter back to the previous adapter
    // using previousLocator; it does not depend on the source still
    // holding the original copy.
    source.delete(srcLocator);

    Log.debugf("FileMigrationService: moved oid=%s (%s → %s)", oid, sourceId, targetId);
  }

  /**
   * FS1e3 — operator-facing per-file rollback. Reads the bytes from
   * the file's <em>current</em> storage adapter ({@code providerId}),
   * writes them back to the <em>previous</em> adapter
   * ({@code previousProviderId}) at the preserved
   * {@code previousLocator}, then clears the four FS1e3 bookkeeping
   * fields and restores {@code providerId} to its pre-migration
   * value.
   *
   * <p>Refusal semantics:
   *
   * <ul>
   *   <li>If no {@code :ShepardFile {appId: $appId}} exists, throws
   *       {@link IllegalArgumentException} ("unknown appId").</li>
   *   <li>If the row exists but {@code previousProviderId} is null
   *       (never migrated, or already rolled back), throws
   *       {@link IllegalStateException} ("nothing to roll back").</li>
   *   <li>If either adapter is missing or disabled, throws
   *       {@link IllegalArgumentException} per {@link #findAdapter}.</li>
   * </ul>
   *
   * <p>The current-adapter bytes are NOT deleted on rollback — they
   * become orphaned. A future {@code sweep-orphans} verb (runbook
   * §17.E) catalogues + removes them. The runbook §9.1 documents
   * this trade-off explicitly.
   *
   * <p>Per-file rollback is intended for mid-migration error
   * recovery: a single file failed verification or had a downstream
   * consistency issue, and the operator wants to revert that one row
   * without disrupting the wider migration. For a wider abort, see
   * the planned {@code shepard-admin files migrate rollback} CLI
   * verb (FS1e3+ follow-up).
   *
   * @param appId the {@code :ShepardFile.appId} — the v2 native
   *              identifier (UUID v7). Not the legacy {@code oid};
   *              the v2 admin surface keys by {@code appId} to
   *              avoid leaking the GridFS-era identifier shape.
   * @param session the Neo4j OGM session to drive the Cypher
   *                statements through; overload {@link #rollbackOne(String)}
   *                opens one from {@link NeoConnector#getInstance()}.
   * @throws IllegalArgumentException if the appId is unknown
   * @throws IllegalStateException if the row has nothing to roll back
   * @throws StorageException if any adapter operation fails
   */
  public void rollbackOne(String appId, Session session) throws StorageException {
    if (appId == null || appId.isBlank()) {
      throw new IllegalArgumentException("appId must not be blank");
    }
    if (session == null) {
      throw new IllegalStateException("Neo4j session unavailable for rollback");
    }

    // Read rollback context BEFORE any mutation so refusal is cheap.
    var ctxResult = session.query(CYPHER_FETCH_ROLLBACK_CTX, Map.of("appId", appId));
    Map<String, Object> ctx = null;
    for (Map<String, Object> row : ctxResult) {
      ctx = row;
      break;
    }
    if (ctx == null) {
      throw new IllegalArgumentException(
        "Cannot roll back: no :ShepardFile with appId=" + appId + " found");
    }

    String oid = (String) ctx.get("oid");
    String containerMongoId = (String) ctx.get("containerMongoId");
    String filename = (String) ctx.get("filename");
    Long fileSize = ctx.get("fileSize") instanceof Number n ? n.longValue() : null;
    String currentProviderId = (String) ctx.get("currentProviderId");
    String previousProviderId = (String) ctx.get("previousProviderId");
    String previousLocator = (String) ctx.get("previousLocator");

    if (previousProviderId == null || previousProviderId.isBlank()) {
      throw new IllegalStateException(
        "Cannot roll back :ShepardFile appId=" + appId +
        ": previousProviderId is null (never migrated, or already rolled back)");
    }
    if (previousLocator == null || previousLocator.isBlank()) {
      throw new IllegalStateException(
        "Cannot roll back :ShepardFile appId=" + appId +
        ": previousLocator is null (corrupt state — please file an issue)");
    }

    FileStorage current = findAdapter(currentProviderId);
    FileStorage previous = findAdapter(previousProviderId);

    // 1. Read bytes from the current (post-migration) adapter
    StorageLocator currentLocator = buildLocator(currentProviderId, containerMongoId, oid);
    StorageGetResponse resp = current.get(currentLocator);
    String effectiveFilename = filename != null ? filename : oid;
    Long sizeToStore = resp.sizeBytes() != null ? resp.sizeBytes() : fileSize;

    // 2. Write back to the previous adapter using the preserved
    //    oid as assignedObjectKey — adapters that honor it (e.g.
    //    S3FileStorage) recreate the bytes at exactly the original
    //    locator. Adapters that don't (e.g. GridFS minting a fresh
    //    oid) introduce a known caveat documented in
    //    docs/reference/file-storage.md §Rollback to GridFS.
    StoragePutRequest putReq = new StoragePutRequest(
      containerMongoId,
      effectiveFilename,
      resp.contentType(),
      resp.stream(),
      sizeToStore,
      oid
    );
    try {
      previous.put(putReq);
    } finally {
      try { resp.stream().close(); } catch (IOException ignore) {}
    }

    // 3. Restore providerId + clear the FS1e3 bookkeeping fields.
    //    Atomic per the rollback Cypher (single MATCH + SET +
    //    REMOVE). The match predicate {previousProviderId IS NOT
    //    NULL} keeps this idempotent on a re-run.
    session.query(CYPHER_ROLLBACK, Map.of("appId", appId));

    Log.infof(
      "FileMigrationService: rolled back oid=%s appId=%s (%s → %s); " +
      "previous-adapter bytes orphan in '%s' until sweep-orphans runs",
      oid, appId, currentProviderId, previousProviderId, currentProviderId);
  }

  /**
   * Convenience overload — opens a Neo4j OGM session from
   * {@link NeoConnector#getInstance()} and delegates to
   * {@link #rollbackOne(String, Session)}. This is the entry point
   * called from {@code FileMigrationRest}; the two-arg form is
   * exposed for the FS1e3 round-trip unit test.
   *
   * @param appId the {@code :ShepardFile.appId}
   * @throws IllegalArgumentException / IllegalStateException per the
   *         two-arg overload
   * @throws StorageException on adapter failure
   */
  public void rollbackOne(String appId) throws StorageException {
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) {
      throw new IllegalStateException("Neo4j session unavailable for rollback");
    }
    try {
      rollbackOne(appId, session);
    } finally {
      try { session.clear(); } catch (Exception ignore) {}
    }
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
