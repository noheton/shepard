package de.dlr.shepard.data.spatialdata.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.data.spatialdata.io.FilterCondition;
import java.util.List;
import java.util.Map;

public class NativeQueryStringBuilder {

  private String selectString = "";

  private StringBuilder whereConditions = new StringBuilder();

  private String timeCondition = "";

  private StringBuilder jsonConditions = new StringBuilder();

  private StringBuilder measurementsFilterConditions = new StringBuilder();

  private String geometryFilterCondition = "";

  private String limitClause = "";

  private String skipClause = "";

  public NativeQueryStringBuilder select(String tableName, String[] columns) {
    selectString = String.format("SELECT %s FROM %s".formatted(String.join(", ", columns), tableName));
    return this;
  }

  public NativeQueryStringBuilder addWhereCondition(String parameterName, Object value) {
    if (value == null) return this;
    if (value.getClass() == String.class) {
      whereConditions.append(String.format(" AND %s = '%s'", parameterName, value));
    } else {
      whereConditions.append(String.format(" AND %s = %s", parameterName, value));
    }
    return this;
  }

  public NativeQueryStringBuilder addTimeCondition(String parameterName, Long timestampStart, Long timestampEnd) {
    if (timestampStart == null && timestampEnd == null) return this;
    var timeQuery = new StringBuilder();
    if (timestampStart != null) {
      timeQuery.append(String.format(" AND %s > %s", parameterName, timestampStart));
    }
    if (timestampEnd != null) {
      timeQuery.append(String.format(" AND %s < %s", parameterName, timestampEnd));
    }
    timeCondition = timeQuery.toString();
    return this;
  }

  public NativeQueryStringBuilder addJsonContainsCondition(String parameterName, Map<String, Object> filter) {
    if (filter.isEmpty()) return this;
    try {
      var mapper = new ObjectMapper();
      var filterAsString = mapper.writeValueAsString(filter);
      jsonConditions.append(String.format(" AND %s @> '%s'", parameterName, filterAsString));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  public NativeQueryStringBuilder addKNNGeometryCondition() {
    geometryFilterCondition = " ORDER BY position <<->> ST_MakePoint(:x1, :y1, :z1) LIMIT :k";
    return this;
  }

  public NativeQueryStringBuilder addAABBGeometryCondition() {
    geometryFilterCondition =
      " AND position &&& ST_3DMakeBox(ST_MakePoint(:x1, :y1, :z1), ST_MakePoint(:x2, :y2, :z2))";
    return this;
  }

  public NativeQueryStringBuilder addBSGeometryCondition() {
    geometryFilterCondition = " AND ST_3DDWithin(position, ST_MakePoint(:x1, :y1, :z1), :radius)";
    return this;
  }

  public NativeQueryStringBuilder addJsonFilterConditions(
    String parameterName,
    List<FilterCondition> measurementsFilter
  ) {
    if (measurementsFilter.isEmpty()) return this;
    measurementsFilter.forEach(filterCondition ->
      measurementsFilterConditions.append(
        String.format(
          " AND jsonb_typeof(%s #> '{%s}') = 'number' AND (%s #>> '{%s}')::NUMERIC %s %s",
          parameterName,
          filterCondition.getKey(),
          parameterName,
          filterCondition.getKey(),
          filterCondition.getOperator().getOperatorString(),
          filterCondition.getValue()
        )
      )
    );
    return this;
  }

  public NativeQueryStringBuilder addLimitClause(Integer limit) {
    if (limit == null) return this;
    limitClause = String.format(" LIMIT %d", limit);
    return this;
  }

  public NativeQueryStringBuilder addSkipClause(Integer skip) {
    if (skip == null) return this;
    skipClause = String.format(" AND id %% %d = 0", skip);
    return this;
  }

  public String build() {
    var res = String.join(
      "",
      selectString,
      " WHERE 1 = 1",
      whereConditions.toString(),
      timeCondition,
      jsonConditions.toString(),
      measurementsFilterConditions.toString(),
      geometryFilterCondition,
      skipClause,
      limitClause,
      ";"
    );
    return res;
  }
}
