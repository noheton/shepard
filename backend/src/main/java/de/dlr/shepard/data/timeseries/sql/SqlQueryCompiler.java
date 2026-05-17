package de.dlr.shepard.data.timeseries.sql;

import de.dlr.shepard.data.timeseries.sql.AllowedColumn.AggFunction;
import de.dlr.shepard.data.timeseries.sql.SqlQuerySpec.FilterItem;
import de.dlr.shepard.data.timeseries.sql.SqlQuerySpec.GroupByItem;
import de.dlr.shepard.data.timeseries.sql.SqlQuerySpec.OrderByItem;
import de.dlr.shepard.data.timeseries.sql.SqlQuerySpec.SelectItem;
import jakarta.enterprise.context.ApplicationScoped;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * P10a — compiles a {@link SqlQuerySpec} and a set of allowed container IDs into a
 * {@link PreparedStatementSpec} with only {@code ?} placeholders; no user-supplied strings
 * are ever inlined into the SQL.
 *
 * <p>Column and table names are resolved exclusively via {@link AllowedTable} and
 * {@link AllowedColumn} enums. Aggregation functions via {@link AggFunction}. Values are bound
 * as JDBC parameters. Aliases are validated against a strict regex before being written.
 *
 * <p>See {@code aidocs/platform/29-p10-implementation-design.md §6} for the injection-safety
 * requirement (C5 coupling). The negative test suite in {@code SqlTimeseriesInjectionTest}
 * is the machine-checkable guarantee that this property holds.
 */
@ApplicationScoped
public class SqlQueryCompiler {

  /** Regex for safe SQL identifiers used as aliases. Matches {@code [A-Za-z_][A-Za-z0-9_]{0,62}}. */
  private static final Pattern SAFE_ALIAS = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,62}");

  private static final Set<String> ALLOWED_FILTER_OPS = Set.of("eq", "ne", "lt", "lte", "gt", "gte", "in", "between");

  /**
   * Compiles a {@link SqlQuerySpec} and the caller-supplied {@code allowedContainerIds} set
   * (already filtered by {@code PermissionsService.filterAllowedForUser}) into a
   * {@link PreparedStatementSpec}.
   *
   * @param spec               the validated DSL spec (Bean Validation already applied at REST layer;
   *                           the compiler re-validates structural invariants independently)
   * @param allowedContainerIds the permission-gated container IDs to include in the SQL IN-list
   * @throws IllegalArgumentException on any structural violation (unknown table, unknown column,
   *                                  invalid alias, missing time_between, unknown operator, etc.)
   */
  public PreparedStatementSpec compile(SqlQuerySpec spec, Set<Long> allowedContainerIds) {
    if (spec == null) {
      throw new IllegalArgumentException("SqlQuerySpec must not be null");
    }
    if (spec.where() == null || spec.where().timeBetween() == null) {
      throw new IllegalArgumentException("where.time_between is required");
    }
    if (spec.where().timeBetween().start() == null || spec.where().timeBetween().end() == null) {
      throw new IllegalArgumentException("where.time_between.start and .end are required");
    }

    // Validate + resolve table — only the sqlName() is used from here on
    AllowedTable table = AllowedTable.fromDsl(spec.from());

    List<Object> params = new ArrayList<>();
    StringBuilder sql = new StringBuilder();

    // Track aliases defined in SELECT/GROUP BY so ORDER BY can reference them
    Map<String, String> aliasToSqlExpr = new HashMap<>();

    // --- SELECT ---
    sql.append("SELECT ");
    List<SelectItem> selectItems = spec.select();
    if (selectItems == null || selectItems.isEmpty()) {
      throw new IllegalArgumentException("select list must not be empty");
    }
    for (int i = 0; i < selectItems.size(); i++) {
      if (i > 0) {
        sql.append(", ");
      }
      SelectItem item = selectItems.get(i);
      String sqlExpr = buildSelectExpr(item, aliasToSqlExpr);
      sql.append(sqlExpr);
    }

    // --- FROM ---
    sql.append(" FROM ").append(table.sqlName());

    // --- WHERE ---
    sql.append(" WHERE time >= ? AND time <= ?");
    // Parse and bind time_between as Timestamps — never inline the string
    Instant start = parseInstant(spec.where().timeBetween().start(), "where.time_between.start");
    Instant end = parseInstant(spec.where().timeBetween().end(), "where.time_between.end");
    params.add(Timestamp.from(start));
    params.add(Timestamp.from(end));

    // container_id IN (?, ?, ...) — uses only allowedContainerIds, never raw user input
    if (!allowedContainerIds.isEmpty()) {
      sql.append(" AND container_id IN (");
      boolean first = true;
      for (Long id : allowedContainerIds) {
        if (!first) {
          sql.append(", ");
        }
        sql.append("?");
        params.add(id);
        first = false;
      }
      sql.append(")");
    }

    // Additional filters
    if (spec.where().filters() != null) {
      for (FilterItem filter : spec.where().filters()) {
        appendFilter(sql, params, filter);
      }
    }

    // --- GROUP BY ---
    if (spec.groupBy() != null && !spec.groupBy().isEmpty()) {
      sql.append(" GROUP BY ");
      for (int i = 0; i < spec.groupBy().size(); i++) {
        if (i > 0) {
          sql.append(", ");
        }
        GroupByItem item = spec.groupBy().get(i);
        String sqlExpr = buildGroupByExpr(item, aliasToSqlExpr);
        sql.append(sqlExpr);
      }
    }

    // --- ORDER BY ---
    if (spec.orderBy() != null && !spec.orderBy().isEmpty()) {
      sql.append(" ORDER BY ");
      for (int i = 0; i < spec.orderBy().size(); i++) {
        if (i > 0) {
          sql.append(", ");
        }
        OrderByItem item = spec.orderBy().get(i);
        sql.append(buildOrderByExpr(item, aliasToSqlExpr));
      }
    }

    // --- LIMIT ---
    if (spec.limit() != null) {
      sql.append(" LIMIT ?");
      params.add(spec.limit());
    }

    return new PreparedStatementSpec(sql.toString(), params);
  }

  /** Builds the SQL expression for a SELECT item. Validates column and alias. */
  private String buildSelectExpr(SelectItem item, Map<String, String> aliasToSqlExpr) {
    if (item.col() == null) {
      throw new IllegalArgumentException("select item must have a 'col' field");
    }
    AllowedColumn col = AllowedColumn.fromDsl(item.col());

    if (item.agg() != null) {
      // Aggregation: AGG(col) AS alias — both agg and alias are validated
      AggFunction agg = AggFunction.fromDsl(item.agg());
      String alias = validateAlias(item.as(), "select.as");
      String expr = agg.sqlName() + "(" + col.sqlName() + ") AS " + alias;
      aliasToSqlExpr.put(alias, agg.sqlName() + "(" + col.sqlName() + ")");
      return expr;
    } else if (item.as() != null) {
      // Column with alias
      String alias = validateAlias(item.as(), "select.as");
      String expr = col.sqlName() + " AS " + alias;
      aliasToSqlExpr.put(alias, col.sqlName());
      return expr;
    } else {
      // Plain column
      return col.sqlName();
    }
  }

  /** Builds the SQL expression for a GROUP BY item. Validates column/alias and time_bucket duration. */
  private String buildGroupByExpr(GroupByItem item, Map<String, String> aliasToSqlExpr) {
    if (item.timeBucket() != null) {
      // time_bucket('X seconds', col) AS alias
      if (item.col() == null) {
        throw new IllegalArgumentException("group_by time_bucket item must have a 'col' field");
      }
      AllowedColumn col = AllowedColumn.fromDsl(item.col());
      // Parse as ISO-8601 Duration — the duration string itself is NEVER inlined; only the
      // computed numeric value (total seconds) is embedded in the SQL literal string.
      // This is safe because Duration.parse() validates the format strictly, and we only
      // use the resulting Long numeric value, not any substring of the user input.
      Duration duration = parseDuration(item.timeBucket(), "group_by.time_bucket");
      long totalSeconds = duration.getSeconds();
      if (totalSeconds <= 0) {
        throw new IllegalArgumentException("group_by.time_bucket duration must be positive");
      }
      // Build: time_bucket('X seconds', col) AS alias
      // totalSeconds is a Long from Duration.getSeconds() — not a user string
      String bucketExpr = "time_bucket('" + totalSeconds + " seconds', " + col.sqlName() + ")";

      if (item.as() != null) {
        String alias = validateAlias(item.as(), "group_by.as");
        aliasToSqlExpr.put(alias, bucketExpr);
        return bucketExpr + " AS " + alias;
      }
      return bucketExpr;
    } else if (item.col() != null) {
      AllowedColumn col = AllowedColumn.fromDsl(item.col());
      if (item.as() != null) {
        String alias = validateAlias(item.as(), "group_by.as");
        aliasToSqlExpr.put(alias, col.sqlName());
        return col.sqlName() + " AS " + alias;
      }
      return col.sqlName();
    } else {
      throw new IllegalArgumentException("group_by item must have either 'col' or 'time_bucket'");
    }
  }

  /** Builds the SQL expression for an ORDER BY item. Resolves against alias map or column enum. */
  private String buildOrderByExpr(OrderByItem item, Map<String, String> aliasToSqlExpr) {
    if (item.col() == null) {
      throw new IllegalArgumentException("order_by item must have a 'col' field");
    }
    // Resolve: try alias map first, then fall back to column allow-list
    String colSql;
    if (aliasToSqlExpr.containsKey(item.col())) {
      // It's a known alias — validate the alias name itself against the safe regex
      validateAlias(item.col(), "order_by.col (alias)");
      colSql = item.col(); // Use alias name in ORDER BY (referencing the SELECT alias)
    } else {
      // Must be a real column
      colSql = AllowedColumn.fromDsl(item.col()).sqlName();
    }

    String dir = "ASC";
    if (item.dir() != null) {
      if ("asc".equalsIgnoreCase(item.dir())) {
        dir = "ASC";
      } else if ("desc".equalsIgnoreCase(item.dir())) {
        dir = "DESC";
      } else {
        throw new IllegalArgumentException("order_by.dir must be 'asc' or 'desc', got: " + item.dir());
      }
    }
    return colSql + " " + dir;
  }

  /** Appends a WHERE filter clause and binds values as parameters. */
  private void appendFilter(StringBuilder sql, List<Object> params, FilterItem filter) {
    if (filter.col() == null) {
      throw new IllegalArgumentException("filter item must have a 'col' field");
    }
    if (filter.op() == null) {
      throw new IllegalArgumentException("filter item must have an 'op' field");
    }
    if (!ALLOWED_FILTER_OPS.contains(filter.op())) {
      throw new IllegalArgumentException("filter operator not allowed: " + filter.op());
    }

    AllowedColumn col = AllowedColumn.fromDsl(filter.col());
    String op = filter.op();

    switch (op) {
      case "eq" -> {
        sql.append(" AND ").append(col.sqlName()).append(" = ?");
        params.add(filter.value());
      }
      case "ne" -> {
        sql.append(" AND ").append(col.sqlName()).append(" <> ?");
        params.add(filter.value());
      }
      case "lt" -> {
        sql.append(" AND ").append(col.sqlName()).append(" < ?");
        params.add(filter.value());
      }
      case "lte" -> {
        sql.append(" AND ").append(col.sqlName()).append(" <= ?");
        params.add(filter.value());
      }
      case "gt" -> {
        sql.append(" AND ").append(col.sqlName()).append(" > ?");
        params.add(filter.value());
      }
      case "gte" -> {
        sql.append(" AND ").append(col.sqlName()).append(" >= ?");
        params.add(filter.value());
      }
      case "in" -> {
        if (!(filter.value() instanceof List<?> inValues)) {
          throw new IllegalArgumentException("filter 'in' operator requires an array value");
        }
        if (inValues.isEmpty()) {
          throw new IllegalArgumentException("filter 'in' operator requires a non-empty array");
        }
        sql.append(" AND ").append(col.sqlName()).append(" IN (");
        for (int i = 0; i < inValues.size(); i++) {
          if (i > 0) {
            sql.append(", ");
          }
          sql.append("?");
          params.add(inValues.get(i));
        }
        sql.append(")");
      }
      case "between" -> {
        if (!(filter.value() instanceof List<?> betweenValues) || betweenValues.size() != 2) {
          throw new IllegalArgumentException("filter 'between' operator requires an array of exactly 2 values");
        }
        sql.append(" AND ").append(col.sqlName()).append(" BETWEEN ? AND ?");
        params.add(betweenValues.get(0));
        params.add(betweenValues.get(1));
      }
      default -> throw new IllegalArgumentException("Unexpected filter operator: " + op);
    }
  }

  /**
   * Validates an alias against the {@link #SAFE_ALIAS} regex.
   *
   * @throws IllegalArgumentException if the alias is null, empty, or contains unsafe characters
   */
  private String validateAlias(String alias, String fieldName) {
    if (alias == null || alias.isEmpty()) {
      throw new IllegalArgumentException(fieldName + " alias must not be null or empty");
    }
    if (!SAFE_ALIAS.matcher(alias).matches()) {
      throw new IllegalArgumentException(
          fieldName + " alias contains unsafe characters: " + alias);
    }
    return alias;
  }

  /**
   * Parses an ISO-8601 instant string. Throws {@link IllegalArgumentException} (not a parse
   * exception) so the REST layer converts it cleanly to a 400.
   */
  private Instant parseInstant(String value, String fieldName) {
    try {
      return Instant.parse(value);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          fieldName + " is not a valid ISO-8601 instant: " + value, e);
    }
  }

  /**
   * Parses an ISO-8601 duration string (e.g. {@code "PT1H"}). Throws {@link IllegalArgumentException}
   * so the REST layer converts it cleanly to a 400.
   */
  private Duration parseDuration(String value, String fieldName) {
    try {
      return Duration.parse(value);
    } catch (Exception e) {
      throw new IllegalArgumentException(
          fieldName + " is not a valid ISO-8601 duration: " + value, e);
    }
  }
}
