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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.hibernate.exception.DataException;
import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;

@RequestScoped
public class TimeseriesDataPointRepository {

  private final int INSERT_BATCH_SIZE = 20000;

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

  @Timed(value = "shepard.timeseries-data-point.compression")
  public void compressAllChunks() {
    var sqlString = "SELECT compress_chunk(c) FROM show_chunks('timeseries_data_points') c;";
    Query query = entityManager.createNativeQuery(sqlString);
    query.getResultList();
  }

  @Timed(value = "shepard.timeseries-data-point.query")
  public List<TimeseriesDataPoint> queryDataPoints(
    int timeseriesId,
    DataPointValueType valueType,
    TimeseriesDataPointsQueryParams queryParams
  ) {
    assertNotIntegral(queryParams.getFunction());
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
    assertNotIntegral(queryParams.getFunction());
    assertCorrectValueTypesForAggregation(queryParams.getFunction(), valueType);

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
        case INTEGRAL -> {}
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
