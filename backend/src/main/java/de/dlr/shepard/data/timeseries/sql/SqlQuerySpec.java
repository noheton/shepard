package de.dlr.shepard.data.timeseries.sql;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * P10a — immutable JSON DSL record for {@code POST /v2/sql/timeseries}.
 *
 * <p>Represents the request body grammar defined in
 * {@code aidocs/platform/29-p10-implementation-design.md §2.1}. Bean Validation annotations
 * are evaluated by JAX-RS on inbound requests; the compiler also performs its own structural
 * validation so unit tests that bypass the REST layer get the same rejection semantics.
 */
public record SqlQuerySpec(
    @NotEmpty List<SelectItem> select,
    @NotNull String from,
    @NotNull @Valid WhereClause where,
    @JsonProperty("group_by") List<GroupByItem> groupBy,
    @JsonProperty("order_by") List<OrderByItem> orderBy,
    @Positive @Max(1_000_000) Integer limit) {

  @JsonCreator
  public SqlQuerySpec(
      @JsonProperty("select") @NotEmpty List<SelectItem> select,
      @JsonProperty("from") @NotNull String from,
      @JsonProperty("where") @NotNull @Valid WhereClause where,
      @JsonProperty("group_by") List<GroupByItem> groupBy,
      @JsonProperty("order_by") List<OrderByItem> orderBy,
      @JsonProperty("limit") @Positive @Max(1_000_000) Integer limit) {
    this.select = select;
    this.from = from;
    this.where = where;
    this.groupBy = groupBy;
    this.orderBy = orderBy;
    this.limit = limit;
  }

  /**
   * An item in the {@code select} list. Either:
   * <ul>
   *   <li>{@code {"col": "time"}} — plain column select</li>
   *   <li>{@code {"col": "time", "as": "ts"}} — column with alias</li>
   *   <li>{@code {"agg": "avg", "col": "value_double", "as": "avg_value"}} — aggregation</li>
   * </ul>
   */
  public record SelectItem(
      @JsonProperty("col") String col,
      @JsonProperty("as") String as,
      @JsonProperty("agg") String agg) {

    @JsonCreator
    public SelectItem(
        @JsonProperty("col") String col,
        @JsonProperty("as") String as,
        @JsonProperty("agg") String agg) {
      this.col = col;
      this.as = as;
      this.agg = agg;
    }
  }

  /**
   * The mandatory time-range constraint: {@code time >= start AND time <= end}.
   * Both values are ISO-8601 instant strings (e.g. {@code "2026-01-01T00:00:00Z"}).
   */
  public record TimeBetween(
      @NotNull @JsonProperty("start") String start,
      @NotNull @JsonProperty("end") String end) {

    @JsonCreator
    public TimeBetween(
        @JsonProperty("start") @NotNull String start,
        @JsonProperty("end") @NotNull String end) {
      this.start = start;
      this.end = end;
    }
  }

  /**
   * A single predicate filter applied after the mandatory time-between gate.
   * {@code op} ∈ {@code {eq, ne, lt, lte, gt, gte, in, between}}.
   */
  public record FilterItem(
      @JsonProperty("col") String col,
      @JsonProperty("op") String op,
      @JsonProperty("value") Object value) {

    @JsonCreator
    public FilterItem(
        @JsonProperty("col") String col,
        @JsonProperty("op") String op,
        @JsonProperty("value") Object value) {
      this.col = col;
      this.op = op;
      this.value = value;
    }
  }

  /**
   * The {@code where} clause. {@code timeBetween} is required; the rest are optional.
   */
  public record WhereClause(
      @NotNull @Valid @JsonProperty("time_between") TimeBetween timeBetween,
      @JsonProperty("container_id_in") List<Long> containerIdIn,
      @JsonProperty("filters") List<FilterItem> filters) {

    @JsonCreator
    public WhereClause(
        @JsonProperty("time_between") @NotNull @Valid TimeBetween timeBetween,
        @JsonProperty("container_id_in") List<Long> containerIdIn,
        @JsonProperty("filters") List<FilterItem> filters) {
      this.timeBetween = timeBetween;
      this.containerIdIn = containerIdIn;
      this.filters = filters;
    }
  }

  /**
   * An item in the {@code group_by} list. Either:
   * <ul>
   *   <li>{@code {"col": "timeseries_id"}} — plain column grouping</li>
   *   <li>{@code {"time_bucket": "PT1H", "col": "time", "as": "bucket"}} — TimescaleDB
   *       {@code time_bucket()} grouping</li>
   * </ul>
   */
  public record GroupByItem(
      @JsonProperty("col") String col,
      @JsonProperty("time_bucket") String timeBucket,
      @JsonProperty("as") String as) {

    @JsonCreator
    public GroupByItem(
        @JsonProperty("col") String col,
        @JsonProperty("time_bucket") String timeBucket,
        @JsonProperty("as") String as) {
      this.col = col;
      this.timeBucket = timeBucket;
      this.as = as;
    }
  }

  /**
   * An item in the {@code order_by} list. {@code dir} ∈ {@code {asc, desc}}.
   */
  public record OrderByItem(
      @JsonProperty("col") String col,
      @JsonProperty("dir") String dir) {

    @JsonCreator
    public OrderByItem(
        @JsonProperty("col") String col,
        @JsonProperty("dir") String dir) {
      this.col = col;
      this.dir = dir;
    }
  }
}
