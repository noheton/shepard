package de.dlr.shepard.v2.importer.services;

import de.dlr.shepard.data.timeseries.daos.TimeseriesContainerDAO;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.v2.importer.daos.ImportPlanDAO;
import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * IMP-STATS-01 — background stats collector for the Shepard import substrate.
 *
 * <p>Every 5 minutes this bean samples two system-wide metrics and writes them
 * into the TimescaleDB {@code timeseries_data_points} hypertable so that
 * import progress is visible in the standard TS channel chart, not just in
 * server logs.
 *
 * <p><b>Metrics collected:</b>
 * <ul>
 *   <li>{@code import.data_objects_created} — global count of non-deleted
 *       DataObjects across all Collections (proxies overall ingest progress).</li>
 *   <li>{@code import.bytes_stored} — sum of {@code ShepardFile.fileSize} across
 *       all files that have a non-null size (files uploaded before FB1a have
 *       {@code null}; they are excluded from the sum).</li>
 * </ul>
 *
 * <p><b>Metrics intentionally deferred:</b>
 * <ul>
 *   <li>{@code import.errors_count} — TODO: wire to ImportLockService retry
 *       counter once that surface is available in-process.</li>
 *   <li>{@code import.queue_depth} — TODO: wire once the in-process import
 *       job queue has a queryable size (current v15 importer is an external
 *       Python process with no in-JVM hook).</li>
 * </ul>
 *
 * <p><b>Bootstrap:</b> On {@link StartupEvent} the collector finds-or-creates a
 * {@code TimeseriesContainer} node with appId {@link #CONTAINER_APP_ID} and caches
 * its Neo4j bigint ID for use in the {@code timeseries.container_id} column.
 * Because the startup hook needs a request context to use the DAO, it calls the
 * same method annotated with {@code @ActivateRequestContext}.
 *
 * <p><b>Channel identity:</b>
 * <table>
 *   <tr><th>5-tuple field</th><th>value</th></tr>
 *   <tr><td>measurement</td><td>{@code "import"}</td></tr>
 *   <tr><td>device</td><td>{@code "shepard-backend"}</td></tr>
 *   <tr><td>location</td><td>{@code "global"}</td></tr>
 *   <tr><td>field</td><td>{@code "value"}</td></tr>
 *   <tr><td>symbolicName</td><td>metric name (see {@link #CHANNEL_DATA_OBJECTS},
 *       {@link #CHANNEL_BYTES_STORED})</td></tr>
 * </table>
 *
 * <p>These coordinates are intentionally distinct from the Python
 * {@code mffd-import-stats-collector.py} script which uses
 * {@code device="mffd-dropbox"} and {@code measurement="import_progress"} —
 * the two collectors write to the same Neo4j container but to different Postgres
 * channel rows.
 */
@ApplicationScoped
public class ImportStatsCollector {

  /** appId of the singleton {@code TimeseriesContainer} node for import stats. */
  public static final String CONTAINER_APP_ID = "shepard-import-stats";

  /** Human-readable name stored on the Neo4j node. */
  static final String CONTAINER_NAME = "Shepard Import Stats";

  /** Channel 5-tuple constants. */
  static final String MEASUREMENT = "import";
  static final String DEVICE      = "shepard-backend";
  static final String LOCATION    = "global";
  static final String FIELD       = "value";

  /** symbolicName values — one per metric. */
  static final String CHANNEL_DATA_OBJECTS = "data_objects_created";
  static final String CHANNEL_BYTES_STORED = "bytes_stored";

  @Inject
  TimeseriesContainerDAO containerDAO;

  @Inject
  ImportPlanDAO importPlanDAO;

  @Inject
  TimeseriesRepository timeseriesRepository;

  @Inject
  AgroalDataSource defaultDataSource;

  /**
   * Neo4j bigint ID of the {@link #CONTAINER_APP_ID} container node.
   * Cached on startup; {@code -1} until bootstrapped.
   *
   * <p>Package-visible so unit tests can seed the ID without going through
   * the full bootstrap flow.
   */
  final AtomicLong containerNeo4jId = new AtomicLong(-1L);

  // ─── Startup bootstrap ────────────────────────────────────────────────────

  /**
   * Runs at application startup: finds or creates the stats container node and
   * caches its Neo4j ID. The {@code @ActivateRequestContext} is required because
   * {@link TimeseriesContainerDAO} is {@code @RequestScoped}.
   */
  @ActivateRequestContext
  void onStart(@Observes StartupEvent event) {
    try {
      long neo4jId = findOrCreateContainer();
      containerNeo4jId.set(neo4jId);
      Log.infof("IMP-STATS-01: bootstrap complete — container appId=%s neo4jId=%d",
          CONTAINER_APP_ID, neo4jId);
    } catch (Exception e) {
      Log.warnf(e, "IMP-STATS-01: bootstrap failed — stats collection disabled until next restart");
    }
  }

  // ─── Scheduled collection ─────────────────────────────────────────────────

  /**
   * Collects metrics every 5 minutes and writes them into
   * {@code timeseries_data_points}.
   *
   * <p>{@code @ActivateRequestContext} is required because the DAOs and
   * {@link TimeseriesRepository} are {@code @RequestScoped}.
   */
  @Scheduled(every = "5m", identity = "import-stats-collector")
  @ActivateRequestContext
  public void collect() {
    long neo4jId = containerNeo4jId.get();
    if (neo4jId < 0) {
      Log.debug("IMP-STATS-01: container not yet bootstrapped — skipping tick");
      return;
    }

    long nowNs = System.currentTimeMillis() * 1_000_000L;

    try {
      long doCount  = countGlobalDataObjects();
      long bytes    = sumFileSizeBytes();

      int doChannelId    = ensureChannel(neo4jId, CHANNEL_DATA_OBJECTS, DataPointValueType.Integer);
      int bytesChannelId = ensureChannel(neo4jId, CHANNEL_BYTES_STORED, DataPointValueType.Integer);

      writeDataPoint(doChannelId,    nowNs, doCount);
      writeDataPoint(bytesChannelId, nowNs, bytes);

      Log.debugf("IMP-STATS-01: tick — dataObjects=%d bytes=%d", doCount, bytes);
    } catch (Exception e) {
      Log.warnf(e, "IMP-STATS-01: tick failed");
    }
  }

  // ─── Metric queries ───────────────────────────────────────────────────────

  /**
   * Count of all non-deleted DataObjects across every Collection.
   *
   * <p>Delegates to {@link ImportPlanDAO#countAllDataObjects()} — a single
   * Cypher COUNT, no full graph load.
   */
  long countGlobalDataObjects() {
    return importPlanDAO.countAllDataObjects();
  }

  /**
   * Sum of {@code ShepardFile.fileSize} for all files where the field is
   * non-null (files uploaded before FB1a have {@code null} — they are
   * intentionally excluded so the sum reflects only measured bytes).
   *
   * <p>Delegates to {@link ImportPlanDAO#sumFileSizeBytes()}.
   */
  long sumFileSizeBytes() {
    return importPlanDAO.sumFileSizeBytes();
  }

  // ─── Channel / data-point helpers ─────────────────────────────────────────

  /**
   * Find or create a channel row for the given {@code symbolicName}.
   *
   * @return the Postgres {@code timeseries.id} for that channel
   */
  private int ensureChannel(long neo4jId, String symbolicName, DataPointValueType valueType) {
    TimeseriesEntity entity = new TimeseriesEntity(
      neo4jId,
      MEASUREMENT,
      FIELD,
      DEVICE,
      LOCATION,
      symbolicName,
      valueType
    );

    // upsert() handles the race-free atomic two-table insert.
    timeseriesRepository.upsert(neo4jId, entity);

    // After upsert, load the canonical row to get its id.
    // Timeseries constructor order: measurement, device, location, symbolicName, field
    Optional<TimeseriesEntity> found = timeseriesRepository.findTimeseries(
      neo4jId,
      new Timeseries(MEASUREMENT, DEVICE, LOCATION, symbolicName, FIELD)
    );
    if (found.isEmpty()) {
      throw new IllegalStateException(
        "IMP-STATS-01: channel row missing after upsert — symbolicName=" + symbolicName
      );
    }
    return found.get().getId();
  }

  /**
   * Upsert a single integer data point into {@code timeseries_data_points}.
   *
   * <p>Uses {@code ON CONFLICT (timeseries_id, time) DO UPDATE} so that
   * back-to-back collections within the same millisecond round don't fail.
   */
  private void writeDataPoint(int timeseriesId, long timeNs, long value)
      throws SQLException {
    String sql =
      "INSERT INTO timeseries_data_points (timeseries_id, time, int_value) " +
      "VALUES (?, ?, ?) " +
      "ON CONFLICT (timeseries_id, time) DO UPDATE SET int_value = EXCLUDED.int_value";

    try (Connection conn = defaultDataSource.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, timeseriesId);
      ps.setLong(2, timeNs);
      ps.setLong(3, value);
      ps.executeUpdate();
    }
  }

  // ─── Bootstrap helper ─────────────────────────────────────────────────────

  /**
   * Find-or-create the stats {@link TimeseriesContainer} Neo4j node.
   *
   * <p>Package-visible so unit tests can call it directly without the
   * {@code @ActivateRequestContext} startup-event wrapper.
   *
   * @return the Neo4j bigint ID of the (possibly freshly-created) node
   */
  long findOrCreateContainerForTest() {
    return findOrCreateContainer();
  }

  private long findOrCreateContainer() {
    Optional<TimeseriesContainer> existing = containerDAO.findByAppId(CONTAINER_APP_ID);
    if (existing.isPresent()) {
      return existing.get().getId();
    }

    // Create a minimal container — no Permissions, no Collection parent.
    // The container exists purely for timeseries channel routing; it is not
    // browsable via the UI (no Collection → has_reference path).
    TimeseriesContainer container = new TimeseriesContainer();
    container.setName(CONTAINER_NAME);
    container.setAppId(CONTAINER_APP_ID);
    container.setCreatedAt(new Date());

    TimeseriesContainer saved = containerDAO.createOrUpdate(container);
    Log.infof("IMP-STATS-01: created stats container — appId=%s neo4jId=%d",
        CONTAINER_APP_ID, saved.getId());
    return saved.getId();
  }
}
