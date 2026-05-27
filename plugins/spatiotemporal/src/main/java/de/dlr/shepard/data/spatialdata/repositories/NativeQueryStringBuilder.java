package de.dlr.shepard.data.spatialdata.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.data.spatialdata.io.FilterCondition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NativeQueryStringBuilder {

  private String selectString = "";

  private StringBuilder whereConditions = new StringBuilder();

  private String timeCondition = "";

  private StringBuilder jsonConditions = new StringBuilder();

  private StringBuilder measurementsFilterConditions = new StringBuilder();

  private String geometryFilterCondition = "";
  private Map<String, Object> queryParameters = new HashMap<>();

  private String limitClause = "";

  private String skipClause = "";

  public Map<String, Object> getQueryParameters() {
    return queryParameters;
  }

  public NativeQueryStringBuilder select(String tableName, String[] columns) {
    selectString = "SELECT %s FROM %s".formatted(String.join(", ", columns), tableName).formatted();
    return this;
  }

  public NativeQueryStringBuilder addWhereCondition(String parameterName, Object value) {
    if (value == null) return this;
    if (value.getClass() == String.class) {
      whereConditions.append(" AND %s = '%s'".formatted(parameterName, value));
    } else {
      whereConditions.append(" AND %s = %s".formatted(parameterName, value));
    }
    return this;
  }

  public NativeQueryStringBuilder addTimeCondition(String parameterName, Long timestampStart, Long timestampEnd) {
    if (timestampStart == null && timestampEnd == null) return this;
    var timeQuery = new StringBuilder();
    if (timestampStart != null) {
      timeQuery.append(" AND %s >= %s".formatted(parameterName, timestampStart));
    }
    if (timestampEnd != null) {
      timeQuery.append(" AND %s <= %s".formatted(parameterName, timestampEnd));
    }
    timeCondition = timeQuery.toString();
    return this;
  }

  public NativeQueryStringBuilder addJsonContainsCondition(String parameterName, Map<String, Object> filter) {
    if (filter.isEmpty()) return this;
    try {
      var mapper = new ObjectMapper();
      var filterAsString = mapper.writeValueAsString(filter);
      jsonConditions.append(" AND %s @> '%s'".formatted(parameterName, filterAsString));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  public NativeQueryStringBuilder addKNNGeometryCondition(double x1, double y1, double z1, int k) {
    geometryFilterCondition = " ORDER BY position <<->> ST_MakePoint(:x1, :y1, :z1) LIMIT :k";
    queryParameters.put("x1", x1);
    queryParameters.put("y1", y1);
    queryParameters.put("z1", z1);
    queryParameters.put("k", k);
    return this;
  }

  public NativeQueryStringBuilder addAABBGeometryCondition(
    double x1,
    double y1,
    double z1,
    double x2,
    double y2,
    double z2
  ) {
    geometryFilterCondition =
      " AND position &&& ST_3DMakeBox(ST_MakePoint(:x1, :y1, :z1), ST_MakePoint(:x2, :y2, :z2))";
    queryParameters.put("x1", x1);
    queryParameters.put("y1", y1);
    queryParameters.put("z1", z1);
    queryParameters.put("x2", x2);
    queryParameters.put("y2", y2);
    queryParameters.put("z2", z2);
    return this;
  }

  public NativeQueryStringBuilder addBSGeometryCondition(double x1, double y1, double z1, double radius) {
    geometryFilterCondition = " AND ST_3DDWithin(position, ST_MakePoint(:x1, :y1, :z1), :radius)";
    queryParameters.put("x1", x1);
    queryParameters.put("y1", y1);
    queryParameters.put("z1", z1);
    queryParameters.put("radius", radius);
    return this;
  }

  public NativeQueryStringBuilder addJsonFilterConditions(
    String parameterName,
    List<FilterCondition> measurementsFilter
  ) {
    if (measurementsFilter.isEmpty()) return this;
    measurementsFilter.forEach(filterCondition ->
      measurementsFilterConditions.append(
        " AND jsonb_typeof(%s #> '{%s}') = 'number' AND (%s #>> '{%s}')::NUMERIC %s %s".formatted(
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
    limitClause = " LIMIT %d".formatted(limit);
    return this;
  }

  public NativeQueryStringBuilder addSkipClause(Integer skip) {
    if (skip == null) return this;
    skipClause = " AND id %% %d = 0".formatted(skip);
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
