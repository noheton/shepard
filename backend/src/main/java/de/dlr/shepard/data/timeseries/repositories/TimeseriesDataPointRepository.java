package de.dlr.shepard.data.timeseries.repositories;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPointsQueryParams;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import de.dlr.shepard.data.timeseries.model.enums.FillOption;
import io.agroal.api.AgroalDataSource;
import io.micrometer.core.annotation.Timed;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hibernate.exception.DataException;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

@RequestScoped
public class TimeseriesDataPointRepository {

  /**
   * Maximum rows per INSERT batch.
   *
   * <p>Each batch generates a SQL query with one shared named parameter
   * ({@code :timeseriesid}) plus two per-row named parameters
   * ({@code :time{i}} and {@code :value{i}}).  Hibernate maps named parameters to
   * positional {@code ?} markers when it sends the query to Postgres over the wire.
   * The Postgres {@code Bind} message encodes the parameter count as {@code int16},
   * capping it at 32 767.
   *
   * <p>At the current value the actual parameter count is
   * {@code 1 + 2 × 20 000 = 40 001} — above the int16 limit.  In practice Quarkus /
   * Hibernate may translate the shared {@code :timeseriesid} binding in a way that
   * does not expand it per-row, making the effective count closer to
   * {@code 1 + 1 × 20 000 = 20 001}, which is safely under the limit.
   *
   * <p>The static initializer below uses the conservative estimate of one net new
   * placeholder per row to guard against a size increase that would <em>definitely</em>
   * exceed the limit regardless of the Hibernate translation strategy.  See
   * TS-AUDIT-2026-05-24-011 in {@code aidocs/16-dispatcher-backlog.md}.
   */
  static final int INSERT_BATCH_SIZE = 20000;

  /**
   * Conservative lower bound on distinct Postgres placeholders added per row by the
   * batch INSERT query.  The real count is two (one {@code :time{i}} + one
   * {@code :value{i}}), but the shared {@code :timeseriesid} parameter means the
   * effective incremental cost per row is closer to one once Hibernate's named-param
   * dedup is applied.  Using one here keeps the guard below the current safe operating
   * point while still catching any future batch-size increase that would clearly breach
   * the 32 767 limit.
   */
  static final int PARAMS_PER_ROW_LOWER_BOUND = 1;

  /** Fixed parameters in every INSERT batch query (the shared {@code :timeseriesid}). */
  static final int FIXED_PARAMS_PER_BATCH = 2;

  /** Postgres {@code Bind} message int16 limit on positional parameter count. */
  static final int PG_BIND_PARAM_LIMIT = 32_767;

  static {
    int estimated = INSERT_BATCH_SIZE * PARAMS_PER_ROW_LOWER_BOUND + FIXED_PARAMS_PER_BATCH;
    if (estimated >= PG_BIND_PARAM_LIMIT) {
      throw new IllegalStateException(
        "INSERT_BATCH_SIZE (" + INSERT_BATCH_SIZE + ") × PARAMS_PER_ROW_LOWER_BOUND (" +
        PARAMS_PER_ROW_LOWER_BOUND + ") + FIXED_PARAMS_PER_BATCH (" + FIXED_PARAMS_PER_BATCH +
        ") = " + estimated + " >= Postgres Bind parameter limit (" + PG_BIND_PARAM_LIMIT + "). " +
        "Reduce INSERT_BATCH_SIZE or switch to COPY-based ingest (no such limit). " +
        "See TS-AUDIT-2026-05-24-011."
      );
    }
  }

  @PersistenceContext
  EntityManager entityManager;

  @Inject
  AgroalDataSource defaultDataSource;

  /**
   * Insert a list of timeseries data points into the database.
   *
   * @param entities         list of timeseries data points
   * @param timeseriesEntity
   * @throws InvalidBodyException can be thrown when 'entities' contains the same
   *                              timestamp more than once (read more in
   *                              architectural documentation: 'Building Block
   *                              View' -> 'Timeseries: Multiple Values for One
   *                              Timestamp')
   */
  @Timed(value = "shepard.timeseries-data-point.batch-insert")
  public void insertManyDataPoints(List<TimeseriesDataPoint> entities, TimeseriesEntity timeseriesEntity) {
    for (int i = 0; i < entities.size(); i += INSERT_BATCH_SIZE) {
      int currentLimit = Math.min(i + INSERT_BATCH_SIZE, entities.size());
      Query query = buildInsertQueryObject(entities, i, currentLimit, timeseriesEntity);

      try {
        query.executeUpdate();
      } catch (DataException ex) {
        if (ex.getCause().toString().contains("ON CONFLICT DO UPDATE command cannot affect row a second time")) {
          throw new InvalidBodyException(
            "You provided the same timestamp value multiple times. Please make sure that there are only unique timestamps in a timeseries payload request!"
          );
        }
        throw ex;
      }
    }
  }

  /**
   * Insert a list of timeseries data points into the database using the COPY command.
   * This is used by the influxdb migration but can also be used for csv import or
   * similar scenarios.
   * @param entities
   * @param timeseriesEntity
   */
  @Timed(value = "shepard.timeseries-data-point.copy-insert")
  public void insertManyDataPointsWithCopyCommand(
    List<TimeseriesDataPoint> entities,
    TimeseriesEntity timeseriesEntity
  ) throws SQLException {
    try (Connection conn = defaultDataSource.getConnection()) {
      PGConnection pgConn = (PGConnection) conn.unwrap(PGConnection.class);
      CopyManager copyManager = pgConn.getCopyAPI();

      var columnName = getColumnName(timeseriesEntity.getValueType());
      var sb = new StringBuilder();

      timeseriesEntity.getId();

      // Strings must be quoted in double quotes in case they contain a comma which is also the delimiter
      if (timeseriesEntity.getValueType() == DataPointValueType.String) {
        for (int i = 0; i < entities.size(); i++) {
          TimeseriesDataPoint entity = entities.get(i);
          sb
            .append(timeseriesEntity.getId())
            .append(",")
            .append(entity.getTimestamp())
            .append(",\"")
            .append(entity.getValue())
            .append("\"\n");
        }
      } else {
        for (int i = 0; i < entities.size(); i++) {
          TimeseriesDataPoint entity = entities.get(i);
          sb
            .append(timeseriesEntity.getId())
            .append(",")
            .append(entity.getTimestamp())
            .append(",")
            .append(entity.getValue())
            .append("\n");
        }
      }

      InputStream input = new ByteArrayInputStream(sb.toString().getBytes());
      String sql =
        "COPY timeseries_data_points (timeseries_id, time, %s) FROM STDIN WITH (FORMAT csv);".formatted(columnName);

      copyManager.copyIn(sql, input);
    } catch (IOException ex) {
      Log.errorf("IOException during copy insert: %s", ex.getMessage());
      throw new RuntimeException("IO Error while inserting data points", ex);
    }
  }

  /**
   * Batched COPY insert used by the InfluxDB->TimescaleDB migration tool.
   * Each batch is committed separately so progress callbacks observe persistent state.
   * The reporter is invoked after every successful batch with (batchIndex, rowsInBatch).
   * If a batch fails the errorReporter is invoked and the exception is rethrown so the
   * caller can mark the migration FAILED; on retry, callers should pass startBatchIndex
   * equal to the last successfully reported batch index + 1.
   */
  @Timed(value = "shepard.timeseries-data-point.copy-insert-batched")
  public void insertManyDataPointsWithCopyCommandBatched(
    List<TimeseriesDataPoint> entities,
    TimeseriesEntity timeseriesEntity,
    int startBatchIndex,
    java.util.function.BiConsumer<Integer, Integer> batchReporter,
    java.util.function.BiConsumer<Integer, Throwable> errorReporter
  ) throws SQLException {
    int totalBatches = (entities.size() + INSERT_BATCH_SIZE - 1) / INSERT_BATCH_SIZE;
    for (int batchIndex = startBatchIndex; batchIndex < totalBatches; batchIndex++) {
      int from = batchIndex * INSERT_BATCH_SIZE;
      int to = Math.min(from + INSERT_BATCH_SIZE, entities.size());
      List<TimeseriesDataPoint> batch = entities.subList(from, to);
      try {
        insertManyDataPointsWithCopyCommand(batch, timeseriesEntity);
      } catch (SQLException | RuntimeException ex) {
        if (errorReporter != null) errorReporter.accept(batchIndex, ex);
        throw ex;
      }
      if (batchReporter != null) batchReporter.accept(batchIndex, batch.size());
    }
  }

  /**
   * Returns the earliest and latest data-point timestamps (nanoseconds since
   * epoch) for each of the requested TimeseriesContainer Neo4j IDs.
   *
   * <p>A single SQL pass — one row per container ID that has any data points.
   * Containers with no rows are absent from the result map (the caller maps
   * that to a {@code null} time-bounds pair).
   *
   * @param containerIds Neo4j long IDs of {@code TimeseriesContainer} nodes
   * @return map of container_id → {@code long[]{minTimeNs, maxTimeNs}}
   */
  @SuppressWarnings("unchecked")
  public Map<Long, long[]> findTimeBoundsByContainerIds(List<Long> containerIds) {
    if (containerIds == null || containerIds.isEmpty()) return Collections.emptyMap();

    // Container IDs are Neo4j long IDs (database-generated, never user-supplied),
    // so inlining them as literals is safe and avoids JDBC array-parameter friction.
    String inClause = containerIds.stream()
      .map(Object::toString)
      .collect(Collectors.joining(","));
    String sql =
      "SELECT t.container_id, MIN(tdp.time) AS min_time, MAX(tdp.time) AS max_time " +
      "FROM timeseries t " +
      "JOIN timeseries_data_points tdp ON tdp.timeseries_id = t.id " +
      "WHERE t.container_id IN (" + inClause + ") " +
      "GROUP BY t.container_id";

    List<Object[]> rows = entityManager.createNativeQuery(sql).getResultList();
    Map<Long, long[]> out = new HashMap<>(rows.size() * 2);
    for (Object[] row : rows) {
      long containerId = ((Number) row[0]).longValue();
      long minTime = ((Number) row[1]).longValue();
      long maxTime = ((Number) row[2]).longValue();
      out.put(containerId, new long[] { minTime, maxTime });
    }
    return out;
  }

  @Timed(value = "shepard.timeseries-data-point.compression")
  public void compressAllChunks() {
    var sqlString = "SELECT compress_chunk(c) FROM show_chunks('timeseries_data_points') c;";
    Query query = entityManager.createNativeQuery(sqlString);
    query.getResultList();
  }

  /**
   * Compresses any {@code timeseries_data_points} chunk that is older than 8 days
   * and not yet compressed.
   *
   * <p>Called after each import run (importer post-phase) and by the fortnightly
   * {@link de.dlr.shepard.data.timeseries.services.TimeseriesBackfillCompressionJob}
   * maintenance job, so backfill chunks ingested outside the daily compression
   * policy window don't sit uncompressed for up to 23 hours.
   *
   * <p>The {@code timeseries_data_points} hypertable uses a BIGINT nanoseconds-since-epoch
   * time column, so the cutoff is computed in nanoseconds rather than as a Postgres
   * {@code INTERVAL} (which only works with {@code TIMESTAMPTZ} hypertables).
   *
   * <p>8 days in nanoseconds = 8 × 24 × 3600 × 1 000 000 000 = 691 200 000 000 000 ns.
   * Any chunk whose {@code range_end_integer} is below {@code now_ns - 8_days_ns} is
   * a backfill candidate.  Chunks that have already been compressed are skipped.
   *
   * <p>Each compressed chunk is logged at INFO level (chunk_schema.chunk_name) so
   * operators can observe progress in the application log.
   *
   * <p>Idempotent: calling the method multiple times on the same set of chunks is safe;
   * attempting to compress an already-compressed chunk is a no-op in TimescaleDB.
   *
   * <p>TS-AUDIT-2026-05-24-008.
   */
  @Timed(value = "shepard.timeseries-data-point.compression-backfill")
  public void compressBackfilledChunks() {
    // 8 days expressed in nanoseconds (same unit as the hypertable's integer time column).
    final long eightDaysNs = 8L * 24L * 3600L * 1_000_000_000L;
    // unix_now_immutable() is the registered integer-now function (returns ns since epoch).
    @SuppressWarnings("unchecked")
    List<Object[]> stale = entityManager.createNativeQuery(
      "SELECT chunk_schema, chunk_name " +
      "FROM timescaledb_information.chunks " +
      "WHERE hypertable_name = 'timeseries_data_points' " +
      "  AND is_compressed = false " +
      "  AND range_end_integer < (unix_now_immutable() - :cutoffNs)"
    )
      .setParameter("cutoffNs", eightDaysNs)
      .getResultList();

    if (stale.isEmpty()) {
      Log.debug("TS-AUDIT-008: no uncompressed backfill chunks older than 8 days — nothing to do");
      return;
    }

    Log.infof("TS-AUDIT-008: found %d uncompressed backfill chunk(s) older than 8 days — compressing", stale.size());
    int compressed = 0;
    int failed = 0;
    for (Object[] row : stale) {
      String schema = (String) row[0];
      String chunkName = (String) row[1];
      String qualified = "\"" + schema + "\".\"" + chunkName + "\"";
      try {
        entityManager.createNativeQuery("SELECT compress_chunk('" + qualified + "'::regclass)")
          .getResultList();
        Log.infof("TS-AUDIT-008: compressed chunk %s", qualified);
        compressed++;
      } catch (RuntimeException ex) {
        // Chunk may have been compressed concurrently or no longer exist — log and continue.
        Log.warnf(ex, "TS-AUDIT-008: failed to compress chunk %s — skipping", qualified);
        failed++;
      }
    }
    Log.infof("TS-AUDIT-008: backfill compression complete — compressed=%d failed=%d", compressed, failed);
  }

  @Timed(value = "shepard.timeseries-data-point.query")
  public List<TimeseriesDataPoint> queryDataPoints(
    int timeseriesId,
    DataPointValueType valueType,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    assertCorrectValueTypesForAggregation(queryParams.getFunction(), valueType);
    assertCorrectValueTypesForFillOption(queryParams.getFillOption(), valueType);
    assertTimeIntervalForFillOption(queryParams.getTimeSliceNanoseconds(), queryParams.getFillOption());
    assertAggregationSetForFillOrGrouping(
      queryParams.getFunction(),
      queryParams.getTimeSliceNanoseconds(),
      queryParams.getFillOption()
    );

    var query = buildSelectQueryObject(timeseriesId, valueType, queryParams);

    @SuppressWarnings("unchecked")
    List<TimeseriesDataPoint> dataPoints = query.getResultList();
    return dataPoints;
  }

  @Timed(value = "shepard.timeseries-data-point-aggregate.query")
  public List<TimeseriesDataPoint> queryAggregationFunction(
    int timeseriesId,
    DataPointValueType valueType,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    assertCorrectValueTypesForAggregation(queryParams.getFunction(), valueType);

    if (
      queryParams.getFunction().isPresent() &&
      queryParams.getFunction().get() == AggregateFunction.INTEGRAL
    ) {
      return queryIntegralWhole(timeseriesId, valueType, queryParams.getStartTime(), queryParams.getEndTime());
    }

    var query = buildSelectAggregationFunctionQueryObject(timeseriesId, valueType, queryParams);

    @SuppressWarnings("unchecked")
    List<TimeseriesDataPoint> dataPoints = query.getResultList();
    return dataPoints;
  }

  private Query buildInsertQueryObject(
    List<TimeseriesDataPoint> entities,
    int startInclusive,
    int endExclusive,
    TimeseriesEntity timeseriesEntity
  ) {
    StringBuilder queryString = new StringBuilder();
    queryString.append(
      "INSERT INTO timeseries_data_points (timeseries_id, time, " +
      getColumnName(timeseriesEntity.getValueType()) +
      ") values "
    );
    queryString.append(
      IntStream.range(startInclusive, endExclusive)
        .mapToObj(index -> "(:timeseriesid" + ",:time" + index + ",:value" + index + ")")
        .collect(Collectors.joining(","))
    );
    queryString.append(
      " ON CONFLICT (timeseries_id, time) DO UPDATE SET time = EXCLUDED.time, timeseries_id = EXCLUDED.timeseries_id, " +
      getColumnName(timeseriesEntity.getValueType()) +
      " = " +
      "EXCLUDED." +
      getColumnName(timeseriesEntity.getValueType()) +
      ";"
    );

    Query query = entityManager.createNativeQuery(queryString.toString());

    query.setParameter("timeseriesid", timeseriesEntity.getId());

    IntStream.range(startInclusive, endExclusive).forEach(index -> {
      query.setParameter("time" + index, entities.get(index).getTimestamp());
      query.setParameter("value" + index, entities.get(index).getValue());
    });

    return query;
  }

  private Query buildSelectQueryObject(
    int timeseriesId,
    DataPointValueType valueType,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    String columnName = getColumnName(valueType);

    FillOption fillOption = queryParams.getFillOption().orElse(FillOption.NONE);
    var timeSliceNanoseconds = queryParams.getTimeSliceNanoseconds().orElse(null);

    String queryString = "";
    if (queryParams.getFunction().isPresent()) {
      AggregateFunction function = queryParams.getFunction().get();
      if (timeSliceNanoseconds == null) {
        timeSliceNanoseconds = queryParams.getEndTime() - queryParams.getStartTime();
      }

      queryString = "SELECT ";

      queryString += switch (fillOption) {
        case NONE -> "time_bucket(:timeInNanoseconds, time) as timestamp, ";
        case NULL, LINEAR, PREVIOUS -> "time_bucket_gapfill(:timeInNanoseconds, time) as timestamp, ";
      };

      String aggregationString = "";
      switch (function) {
        case MAX, MIN, COUNT, SUM, STDDEV -> aggregationString = "%s(%s)".formatted(function.name(), columnName);
        case MEAN -> aggregationString = "AVG(%s)".formatted(columnName);
        case LAST, FIRST -> aggregationString = "%s(%s, time)".formatted(function.name(), columnName);
        case SPREAD -> aggregationString = "MAX(%s) - MIN(%s)".formatted(columnName, columnName);
        case MEDIAN -> aggregationString = "percentile_cont(0.5) WITHIN GROUP (ORDER BY %s)".formatted(columnName);
        case MODE -> aggregationString = "mode() WITHIN GROUP (ORDER BY %s)".formatted(columnName);
        // Midpoint-rule: ∫v dt ≈ AVG(v) × Δt per time_bucket.
        // Cast to float8 so integer columns yield a numeric double result.
        case INTEGRAL -> aggregationString = "AVG(%s)::float8 * :timeInNanoseconds".formatted(columnName);
      }

      // handle filling - by default bucket_gapfill uses NULL filloption
      if (fillOption == FillOption.LINEAR) {
        aggregationString = "interpolate(%s) as value ".formatted(aggregationString);
      } else if (fillOption == FillOption.PREVIOUS) {
        aggregationString = "locf(%s) as value ".formatted(aggregationString);
      } else {
        aggregationString += " as value ";
      }

      queryString += aggregationString;
    } else {
      queryString = "SELECT time, %s ".formatted(columnName);
    }

    queryString += """
    FROM timeseries_data_points
    WHERE timeseries_id = :timeseriesId
      AND time >= :startTimeNano
      AND time <= :endTimeNano
    """;

    if (queryParams.getFunction().isPresent()) {
      queryString += " GROUP BY timestamp ORDER BY timestamp";
    } else {
      queryString += " ORDER BY time";
    }

    Query query = entityManager.createNativeQuery(queryString, TimeseriesDataPoint.class);

    if (timeSliceNanoseconds != null) {
      query.setParameter("timeInNanoseconds", timeSliceNanoseconds);
    }
    query.setParameter("timeseriesId", timeseriesId);
    query.setParameter("startTimeNano", queryParams.getStartTime());
    query.setParameter("endTimeNano", queryParams.getEndTime());

    return query;
  }

  private Query buildSelectAggregationFunctionQueryObject(
    int timeseriesId,
    DataPointValueType valueType,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    String columnName = getColumnName(valueType);

    String queryString = "";
    if (queryParams.getFunction().isPresent()) {
      AggregateFunction function = queryParams.getFunction().get();

      queryString = "SELECT 1 as timestamp, ";

      String aggregationString = "";
      switch (function) {
        case MAX, MIN, COUNT, SUM, STDDEV -> aggregationString = "%s(%s)".formatted(function.name(), columnName);
        case MEAN -> aggregationString = "AVG(%s)".formatted(columnName);
        case LAST, FIRST -> aggregationString = "%s(%s, time)".formatted(function.name(), columnName);
        case SPREAD -> aggregationString = "MAX(%s) - MIN(%s)".formatted(columnName, columnName);
        case MEDIAN -> aggregationString = "percentile_cont(0.5) WITHIN GROUP (ORDER BY %s)".formatted(columnName);
        case MODE -> aggregationString = "mode() WITHIN GROUP (ORDER BY %s)".formatted(columnName);
        case INTEGRAL -> {}
      }

      aggregationString += " as value ";

      queryString += aggregationString;
    } else {
      queryString = "SELECT time, %s ".formatted(columnName);
    }

    queryString += """
    FROM timeseries_data_points
    WHERE timeseries_id = :timeseriesId
      AND time >= :startTimeNano
      AND time <= :endTimeNano
    """;

    Query query = entityManager.createNativeQuery(queryString, TimeseriesDataPoint.class);

    query.setParameter("timeseriesId", timeseriesId);
    query.setParameter("startTimeNano", queryParams.getStartTime());
    query.setParameter("endTimeNano", queryParams.getEndTime());

    return query;
  }

  /**
   * Returns the latest data point strictly before {@code timeNano}, or empty when none exists.
   * Used by the live-window endpoint to find the left-flanking point for boundary interpolation.
   */
  @SuppressWarnings("unchecked")
  public Optional<TimeseriesDataPoint> findLatestBefore(int timeseriesId, DataPointValueType valueType, long timeNano) {
    String col = getColumnName(valueType);
    String sql = "SELECT time, " + col +
      " FROM timeseries_data_points" +
      " WHERE timeseries_id = :tsId AND time < :t" +
      " ORDER BY time DESC LIMIT 1";
    List<TimeseriesDataPoint> rows = entityManager
      .createNativeQuery(sql, TimeseriesDataPoint.class)
      .setParameter("tsId", timeseriesId)
      .setParameter("t", timeNano)
      .getResultList();
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /**
   * Returns the earliest data point strictly after {@code timeNano}, or empty when none exists.
   * Used by the live-window endpoint to find the right-flanking point for boundary interpolation.
   */
  @SuppressWarnings("unchecked")
  public Optional<TimeseriesDataPoint> findEarliestAfter(int timeseriesId, DataPointValueType valueType, long timeNano) {
    String col = getColumnName(valueType);
    String sql = "SELECT time, " + col +
      " FROM timeseries_data_points" +
      " WHERE timeseries_id = :tsId AND time > :t" +
      " ORDER BY time ASC LIMIT 1";
    List<TimeseriesDataPoint> rows = entityManager
      .createNativeQuery(sql, TimeseriesDataPoint.class)
      .setParameter("tsId", timeseriesId)
      .setParameter("t", timeNano)
      .getResultList();
    return rows.isEmpty() ? Optional.empty() : Optional.of(rows.get(0));
  }

  /**
   * Pre-aggregation query for LTTB pre-reduction on large windows.
   * Buckets the time range into {@code (endNs-startNs)/bucketNs} buckets and returns
   * AVG per bucket. Only meaningful for numeric types (Double, Integer).
   * Reduces DB→Java data transfer by (rawRowCount / numBuckets) before LTTB runs.
   */
  @Timed(value = "shepard.timeseries-data-point.preagg-query")
  @SuppressWarnings("unchecked")
  public List<TimeseriesDataPoint> queryPreAggregated(
    int timeseriesId,
    DataPointValueType valueType,
    long startNs,
    long endNs,
    long bucketNs
  ) {
    String col = getColumnName(valueType);
    String sql = """
      SELECT time_bucket(:bucketNs, time) AS timestamp,
             AVG(%s) AS value
      FROM timeseries_data_points
      WHERE timeseries_id = :timeseriesId
        AND time >= :startNs
        AND time <= :endNs
      GROUP BY timestamp
      ORDER BY timestamp
      """.formatted(col);
    return entityManager
      .createNativeQuery(sql, TimeseriesDataPoint.class)
      .setParameter("bucketNs", bucketNs)
      .setParameter("timeseriesId", timeseriesId)
      .setParameter("startNs", startNs)
      .setParameter("endNs", endNs)
      .getResultList();
  }

  /**
   * Returns the time_bucket interval (ns) to use for pre-aggregation before LTTB,
   * targeting {@code target*5} buckets so LTTB has 5× visual headroom.
   * Returns 0 when the window is too small to benefit (bucket < 1 ms).
   */
  public static long choosePreaggBucketNs(long windowNs, int target) {
    if (target <= 0 || windowNs <= 0) return 0L;
    long bucketNs = windowNs / ((long) target * 5);
    return bucketNs >= 1_000_000L ? bucketNs : 0L;
  }

  /**
   * Threshold above which the {@code timeseries_hourly} continuous aggregate
   * is preferred over raw-point queries: one hour expressed in nanoseconds.
   */
  static final long CAGG_THRESHOLD_NS = 3_600_000_000_000L;

  /**
   * Queries the {@code timeseries_hourly} continuous aggregate (TS-OPT3).
   *
   * <p>This view stores one pre-aggregated row per ({@code timeseries_id}, 1-hour bucket),
   * so fetching a 6-month overview reads at most ~4 380 rows instead of the raw tens of
   * millions.  The returned points use the bucket start-time as their timestamp and the
   * per-bucket average as their value.
   *
   * <p>Only {@link DataPointValueType#Double} and {@link DataPointValueType#Integer} are
   * supported (the view only aggregates numeric columns).  For other types an empty list
   * is returned so the caller can fall back to the raw path.
   *
   * <p><strong>Cold-cache caveat:</strong> The refresh policy populates the last 25 hours
   * on a 1-hour schedule.  For a brand-new channel, or for data older than 7 days that
   * has not yet been materialized, the CAgg may return no rows.  Callers should treat an
   * empty result as a signal to fall back to {@link #queryPreAggregated} rather than
   * surfacing an empty chart.
   *
   * @param timeseriesId internal timeseries row ID (matches {@code timeseries_hourly.timeseries_id})
   * @param valueType    the value type of the channel; non-numeric types return empty
   * @param startNs      window start in nanoseconds since epoch (inclusive)
   * @param endNs        window end in nanoseconds since epoch (inclusive)
   * @param maxPoints    the maximum number of points the caller wants; used only to
   *                     decide whether this method should be called — routing logic lives
   *                     in {@link de.dlr.shepard.data.timeseries.services.TimeseriesService}
   * @return list of hourly-bucketed data points, possibly empty on cold cache
   */
  @Timed(value = "shepard.timeseries-data-point.cagg-query")
  @SuppressWarnings("unchecked")
  public List<TimeseriesDataPoint> queryCagg(
    int timeseriesId,
    DataPointValueType valueType,
    long startNs,
    long endNs,
    int maxPoints
  ) {
    if (valueType != DataPointValueType.Double && valueType != DataPointValueType.Integer) {
      return Collections.emptyList();
    }

    // Select avg_double for Double channels, avg_int for Integer channels.
    // Both are pre-computed by V1.12.1 timeseries_hourly.
    String avgCol = (valueType == DataPointValueType.Double) ? "avg_double" : "avg_int";

    String sql = """
      SELECT hour_bucket AS timestamp,
             %s AS value
      FROM timeseries_hourly
      WHERE timeseries_id = :timeseriesId
        AND hour_bucket >= :startNs
        AND hour_bucket <= :endNs
      ORDER BY hour_bucket
      """.formatted(avgCol);

    return entityManager
      .createNativeQuery(sql, TimeseriesDataPoint.class)
      .setParameter("timeseriesId", timeseriesId)
      .setParameter("startNs", startNs)
      .setParameter("endNs", endNs)
      .getResultList();
  }

  /**
   * Returns {@code true} when the CAgg path should be preferred for the given window
   * and target point count.
   *
   * <p>The heuristic: if the nanoseconds per desired point exceeds 1 hour, one row from
   * the hourly CAgg represents at most one "pixel" on the chart, so the CAgg is both
   * cheaper and sufficient in resolution.
   *
   * @param windowNs  query window length in nanoseconds
   * @param maxPoints number of points the caller wants (LTTB target)
   * @return {@code true} when CAgg routing is preferred
   */
  public static boolean shouldUseCagg(long windowNs, int maxPoints) {
    if (maxPoints <= 0 || windowNs <= 0) return false;
    return (windowNs / maxPoints) > CAGG_THRESHOLD_NS;
  }

  /**
   * Computes the trapezoidal integral ∫v dt over [startNs, endNs] for the whole range,
   * returning a single {@link TimeseriesDataPoint} at the midpoint timestamp.
   *
   * <p>Formula per pair of adjacent points (t_i, v_i) and (t_{i+1}, v_{i+1}):
   * area = 0.5 × (v_i + v_{i+1}) × (t_{i+1} − t_i)
   *
   * <p>The result value is in nanosecond·value units (e.g. ns·°C for temperature).
   * Returns a single-element list with value = 0.0 when no data points exist in the range.
   *
   * <p>Only valid for {@link DataPointValueType#Double} and {@link DataPointValueType#Integer};
   * Boolean and String are rejected upstream by {@code assertCorrectValueTypesForAggregation}.
   *
   * @param timeseriesId the internal timeseries row ID
   * @param valueType    the numeric column to integrate
   * @param startNs      window start in nanoseconds since epoch (inclusive)
   * @param endNs        window end in nanoseconds since epoch (inclusive)
   * @return single-element list containing the integrated value at the midpoint timestamp
   */
  @SuppressWarnings("unchecked")
  @Timed(value = "shepard.timeseries-data-point.integral-query")
  List<TimeseriesDataPoint> queryIntegralWhole(
    int timeseriesId,
    DataPointValueType valueType,
    long startNs,
    long endNs
  ) {
    String col = getColumnName(valueType);
    // Cast value column to float8 so integer series produce a double result,
    // consistent with other numeric aggregation outputs.
    String sql =
      """
      SELECT
        (:startNs + :endNs) / 2 AS time,
        COALESCE(SUM(0.5 * (v + v_next) * (t_next - t)), 0.0) AS value
      FROM (
        SELECT
          time AS t,
          %s::float8 AS v,
          LEAD(time)          OVER (ORDER BY time) AS t_next,
          LEAD(%s::float8)    OVER (ORDER BY time) AS v_next
        FROM timeseries_data_points
        WHERE timeseries_id = :timeseriesId
          AND time >= :startNs
          AND time <= :endNs
      ) sub
      WHERE t_next IS NOT NULL
      """.formatted(col, col);
    return entityManager
      .createNativeQuery(sql, TimeseriesDataPoint.class)
      .setParameter("startNs", startNs)
      .setParameter("endNs", endNs)
      .setParameter("timeseriesId", timeseriesId)
      .getResultList();
  }

  private String getColumnName(DataPointValueType valueType) {
    return switch (valueType) {
      case Double -> "double_value";
      case Integer -> "int_value";
      case String -> "string_value";
      case Boolean -> "boolean_value";
    };
  }

  /**
   * Throw when trying to access unsupported aggregation function.
   */
  private void assertNotIntegral(Optional<AggregateFunction> function) {
    if (function.isPresent() && function.get() == AggregateFunction.INTEGRAL) {
      throw new InvalidRequestException("Aggregation function 'integral' is currently not implemented.");
    }
  }

  /**
   * Throw when trying to use aggregation functions with boolean or string value
   * types.
   * COUNT, FIRST and LAST can be allowed for all data types.
   */
  private void assertCorrectValueTypesForAggregation(
    Optional<AggregateFunction> function,
    DataPointValueType valueType
  ) {
    if (
      (valueType == DataPointValueType.Boolean || valueType == DataPointValueType.String) &&
      (function.isPresent() &&
        function.get() != AggregateFunction.COUNT &&
        function.get() != AggregateFunction.FIRST &&
        function.get() != AggregateFunction.LAST)
    ) {
      throw new InvalidRequestException(
        "Cannot execute aggregation functions on data points of type boolean or string."
      );
    }
  }

  /**
   * Throw when trying to use gap filling with unsupported value types boolean or
   * string.
   */
  private void assertCorrectValueTypesForFillOption(Optional<FillOption> fillOption, DataPointValueType valueType) {
    if (
      (valueType == DataPointValueType.Boolean || valueType == DataPointValueType.String) && (fillOption.isPresent())
    ) {
      throw new InvalidRequestException("Cannot use gap filling options on data points of type boolean or string.");
    }
  }

  /**
   * Throw when trying to use fill option without specifying the timeSlice value
   */
  private void assertTimeIntervalForFillOption(Optional<Long> timeSliceNanoseconds, Optional<FillOption> fillOption) {
    if (timeSliceNanoseconds.isEmpty() && fillOption.isPresent()) {
      throw new InvalidRequestException("Cannot use gap filling option when no grouping interval is specified.");
    }
  }

  /**
   * Throw when trying to use fill option or grouping when no aggregation function
   * is set.
   */
  private void assertAggregationSetForFillOrGrouping(
    Optional<AggregateFunction> function,
    Optional<Long> timeSliceNanoseconds,
    Optional<FillOption> fillOption
  ) {
    if (function.isEmpty() && (fillOption.isPresent() || timeSliceNanoseconds.isPresent())) {
      throw new InvalidRequestException(
        "Cannot use gap filling option or grouping of data when no aggregation function is specified."
      );
    }
  }
}
