package de.dlr.shepard.data.spatialdata.repositories;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.data.spatialdata.io.FilterCondition;
import jakarta.ws.rs.BadRequestException;
import java.util.List;
import java.util.Map;

public class NativeQueryStringBuilder {

  private StringBuilder query = new StringBuilder();
  private boolean whereClauseAdded = false;

  public NativeQueryStringBuilder select(String tableName, String[] columns) {
    query.append("SELECT %s FROM %s".formatted(String.join(", ", columns), tableName));
    return this;
  }

  public NativeQueryStringBuilder addWhereCondition(String parameterName, Object value) {
    if (value == null) return this;
    addWhereClauseIfNecessary();
    if (value.getClass() == String.class) {
      query.append(String.format(" AND %s = '%s'", parameterName, value));
    } else {
      query.append(String.format(" AND %s = %s", parameterName, value));
    }
    return this;
  }

  public NativeQueryStringBuilder addTimeCondition(String parameterName, Long timestampStart, Long timestampEnd) {
    if (timestampStart == null && timestampEnd == null) return this;
    addWhereClauseIfNecessary();
    if (timestampStart != null) {
      query.append(String.format(" AND %s > %s", parameterName, timestampStart));
    }
    if (timestampEnd != null) {
      query.append(String.format(" AND %s < %s", parameterName, timestampEnd));
    }
    return this;
  }

  public NativeQueryStringBuilder addJsonContainsCondition(String parameterName, Map<String, Object> filter) {
    if (filter.isEmpty()) return this;
    addWhereClauseIfNecessary();

    try {
      var mapper = new ObjectMapper();
      var filterAsString = mapper.writeValueAsString(filter);
      query.append(String.format(" AND %s @> '%s'", parameterName, filterAsString));
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return this;
  }

  public NativeQueryStringBuilder addLimitClause(Integer limit) {
    if (limit == null) return this;
    addWhereClauseIfNecessary();
    query.append(String.format(" LIMIT %d", limit));
    return this;
  }

  public NativeQueryStringBuilder addKNNGeometryCondition() {
    addWhereClauseIfNecessary();
    query.append(" ORDER BY position <<->> ST_MakePoint(:x1, :y1, :z1) LIMIT :k");
    return this;
  }

  public NativeQueryStringBuilder addAABBGeometryCondition() {
    addWhereClauseIfNecessary();
    query.append(" AND position &&& ST_3DMakeBox(ST_MakePoint(:x1, :y1, :z1), ST_MakePoint(:x2, :y2, :z2))");
    return this;
  }

  public NativeQueryStringBuilder addBSGeometryCondition() {
    addWhereClauseIfNecessary();
    query.append(" AND ST_3DDWithin(position, ST_MakePoint(:x1, :y1, :z1), :radius)");
    return this;
  }

  public NativeQueryStringBuilder addSkipClause(Integer skip) {
    if (skip == null) return this;
    if (skip <= 0) throw new BadRequestException("Skip parameter must be greater than 0");
    addWhereClauseIfNecessary();
    query.append(String.format(" AND id %% %d = 0", skip));
    return this;
  }

  public String build() {
    return query.append(";").toString();
  }

  private void addWhereClauseIfNecessary() {
    if (whereClauseAdded) return;
    query.append(" WHERE 1 = 1");
    whereClauseAdded = true;
  }

  public NativeQueryStringBuilder addJsonFilterConditions(
    String parameterName,
    List<FilterCondition> measurementsFilter
  ) {
    if (measurementsFilter.isEmpty()) return this;
    addWhereClauseIfNecessary();
    measurementsFilter.forEach(filterCondition ->
      query.append(
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
}
