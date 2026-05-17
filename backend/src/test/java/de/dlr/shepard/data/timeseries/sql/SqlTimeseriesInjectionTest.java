package de.dlr.shepard.data.timeseries.sql;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.BaseTestCase;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

/**
 * P10a — SQL-injection regression tests for {@link SqlQueryCompiler}.
 *
 * <p>This is the C5 coupling test suite — the machine-checkable guarantee that no
 * user-supplied strings are inlined into the generated SQL.
 * See {@code aidocs/platform/29-p10-implementation-design.md §8} for the requirement.
 */
class SqlTimeseriesInjectionTest extends BaseTestCase {

  @InjectMocks
  SqlQueryCompiler compiler;

  private static final SqlQuerySpec.TimeBetween VALID_TB =
      new SqlQuerySpec.TimeBetween("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z");

  private SqlQuerySpec.WhereClause whereNoFilters() {
    return new SqlQuerySpec.WhereClause(VALID_TB, null, null);
  }

  @Test
  void selectColWithSqlInjection_rejected() {
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem(
            "value_double; DROP TABLE timeseries_data_points;", null, null)),
        "timeseries_data_points",
        whereNoFilters(),
        null, null, null);

    assertThrows(IllegalArgumentException.class, () -> compiler.compile(spec, Set.of(1L)));
  }

  @Test
  void fromWithComment_rejected() {
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points --",
        whereNoFilters(),
        null, null, null);

    assertThrows(IllegalArgumentException.class, () -> compiler.compile(spec, Set.of(1L)));
  }

  @Test
  void timeBucketWithInjection_rejected() {
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points",
        whereNoFilters(),
        List.of(new SqlQuerySpec.GroupByItem("time", "1 hour); DROP TABLE timeseries_data_points; --", "bucket")),
        null, null);

    // ISO-8601 Duration.parse() fails on the injection string
    assertThrows(IllegalArgumentException.class, () -> compiler.compile(spec, Set.of(1L)));
  }

  @Test
  void aliasWithUnsafeChars_rejected() {
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("value_double", "x; DROP TABLE foo", null)),
        "timeseries_data_points",
        whereNoFilters(),
        null, null, null);

    // Alias regex [A-Za-z_][A-Za-z0-9_]{0,62} rejects the semicolon
    assertThrows(IllegalArgumentException.class, () -> compiler.compile(spec, Set.of(1L)));
  }

  @Test
  void filterValueStringNotInlined() {
    // The filter value "0 OR 1=1" must appear in the params list, not in the SQL string
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("value_double", null, null)),
        "timeseries_data_points",
        new SqlQuerySpec.WhereClause(
            VALID_TB,
            null,
            List.of(new SqlQuerySpec.FilterItem("value_double", "gte", "0 OR 1=1"))),
        null, null, null);

    PreparedStatementSpec result = compiler.compile(spec, Set.of(1L));
    String sql = result.sql();

    // The injection string must NOT appear in the SQL
    assertFalse(sql.contains("OR 1=1"), "Injection payload must not appear in compiled SQL");
    // SQL must use >= ? placeholder
    assertTrue(sql.contains(">= ?"), "SQL must use parameterised >= ?");
    // The injection string must appear in params
    assertTrue(result.params().contains("0 OR 1=1"),
        "Injection value must be in bound params list");
  }
}
