package de.dlr.shepard.data.timeseries.sql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.BaseTestCase;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

/**
 * P10a — unit tests for {@link SqlQueryCompiler}.
 *
 * <p>These tests exercise the compiler independently of Bean Validation and the REST layer, so
 * the compiler's own structural checks (null/missing fields) must work standalone.
 */
class SqlQueryCompilerTest extends BaseTestCase {

  @InjectMocks
  SqlQueryCompiler compiler;

  // --- Helper builders ---

  private SqlQuerySpec.TimeBetween tb(String start, String end) {
    return new SqlQuerySpec.TimeBetween(start, end);
  }

  private SqlQuerySpec.WhereClause where(SqlQuerySpec.TimeBetween tb) {
    return new SqlQuerySpec.WhereClause(tb, null, null);
  }

  private SqlQuerySpec.WhereClause where(SqlQuerySpec.TimeBetween tb, List<Long> containerIds) {
    return new SqlQuerySpec.WhereClause(tb, containerIds, null);
  }

  private SqlQuerySpec simpleSpec() {
    return new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points",
        where(tb("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z")),
        null, null, null);
  }

  // --- Tests ---

  @Test
  void missingTimeBetween_throwsIllegalArgumentException() {
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points",
        new SqlQuerySpec.WhereClause(null, null, null),
        null, null, null);

    assertThrows(IllegalArgumentException.class, () -> compiler.compile(spec, Set.of()));
  }

  @Test
  void unknownColumn_throwsIllegalArgumentException() {
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("nonexistent", null, null)),
        "timeseries_data_points",
        where(tb("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z")),
        null, null, null);

    assertThrows(IllegalArgumentException.class, () -> compiler.compile(spec, Set.of()));
  }

  @Test
  void unknownTable_throwsIllegalArgumentException() {
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "users",
        where(tb("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z")),
        null, null, null);

    assertThrows(IllegalArgumentException.class, () -> compiler.compile(spec, Set.of()));
  }

  @Test
  void basicQuery_compilesToParameterizedSql() {
    SqlQuerySpec spec = simpleSpec();
    PreparedStatementSpec result = compiler.compile(spec, Set.of(1L, 2L));

    String sql = result.sql();
    // SQL must not contain any user-supplied strings inlined
    assertFalse(sql.contains("2026-01-01T00:00:00Z"), "ISO-8601 instant must not appear in SQL");
    // SQL must use ? placeholders for values
    assertTrue(sql.contains("?"), "SQL must contain ? placeholders");
    // time range placeholders must be present
    assertTrue(sql.contains("time >= ?") && sql.contains("time <= ?"),
        "SQL must have time range placeholders");
    // container IN list
    assertTrue(sql.contains("container_id IN"), "SQL must have container_id IN clause");

    // Params: time_start, time_end, then container ids
    List<Object> params = result.params();
    assertTrue(params.get(0) instanceof Timestamp);
    assertTrue(params.get(1) instanceof Timestamp);
    assertEquals(Instant.parse("2026-01-01T00:00:00Z"),
        ((Timestamp) params.get(0)).toInstant());
    assertEquals(Instant.parse("2026-02-01T00:00:00Z"),
        ((Timestamp) params.get(1)).toInstant());
  }

  @Test
  void limitClause_appendedWhenPresent() {
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points",
        where(tb("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z")),
        null, null, 100);

    PreparedStatementSpec result = compiler.compile(spec, Set.of(1L));
    String sql = result.sql();

    assertTrue(sql.endsWith("LIMIT ?"), "SQL must end with LIMIT ?");
    // Last param should be the limit value
    Object lastParam = result.params().get(result.params().size() - 1);
    assertEquals(100, lastParam);
  }

  @Test
  void orderByAsc_compilesCorrectly() {
    SqlQuerySpec spec = new SqlQuerySpec(
        List.of(new SqlQuerySpec.SelectItem("time", null, null)),
        "timeseries_data_points",
        where(tb("2026-01-01T00:00:00Z", "2026-02-01T00:00:00Z")),
        null,
        List.of(new SqlQuerySpec.OrderByItem("time", "asc")),
        null);

    PreparedStatementSpec result = compiler.compile(spec, Set.of(1L));
    String sql = result.sql();

    assertTrue(sql.contains("ORDER BY time ASC"), "SQL must contain ORDER BY time ASC");
  }
}
