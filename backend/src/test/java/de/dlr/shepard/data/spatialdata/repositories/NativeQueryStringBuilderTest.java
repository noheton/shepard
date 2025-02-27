package de.dlr.shepard.data.spatialdata.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.data.spatialdata.io.FilterCondition;
import de.dlr.shepard.data.spatialdata.io.Operator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

public class NativeQueryStringBuilderTest {

  private final String[] ALL_COLUMNS_STRING = new String[] { "*" };

  @Test
  public void build_simpleSelectQuery_success() {
    var builder = new NativeQueryStringBuilder();
    var current = builder.select("table_name", ALL_COLUMNS_STRING).build();

    var expected = "SELECT * FROM table_name;";

    assertEquals(expected, current);
  }

  @Test
  public void build_simpleSelectQueryWithColumns_success() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", new String[] { "column1", "column2" })
      .addLimitClause(null)
      .build();

    var expected = "SELECT column1, column2 FROM table_name;";

    assertEquals(expected, current);
  }

  @Test
  public void build_addOneCondition_success() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addWhereCondition("id", 1)
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id = 1;";

    assertEquals(expected, current);
  }

  @Test
  public void build_addTwoConditions_success() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addWhereCondition("id", 1)
      .addWhereCondition("role", "assistant")
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id = 1 AND role = 'assistant';";

    assertEquals(expected, current);
  }

  @Test
  public void build_addTimeCondition_success() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addWhereCondition("id", 1)
      .addTimeCondition("time", 123l, 234l)
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id = 1 AND time > 123 AND time < 234;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passOnlyTimestampStart_success() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addTimeCondition("time", 123l, null)
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND time > 123;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passOnlyTimestampEnd_success() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addTimeCondition("time", null, 234l)
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND time < 234;";

    assertEquals(expected, current);
  }

  @Test
  public void build_addJsonCondition_success() {
    var jsonFilter = new HashMap<String, Object>();
    jsonFilter.put("track", 2);

    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addWhereCondition("id", 1)
      .addJsonContainsCondition("meta", jsonFilter)
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id = 1 AND meta @> '{\"track\":2}';";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNullValueInCondition_isIgnored() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addWhereCondition("id", null)
      .build();

    var expected = "SELECT * FROM table_name;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNullValueInSecondCondition_isIgnored() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addWhereCondition("id", 1)
      .addJsonContainsCondition("meta", Collections.emptyMap())
      .addTimeCondition("time", null, null)
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id = 1;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passEmptyLimit_success() {
    var current = new NativeQueryStringBuilder().select("table_name", ALL_COLUMNS_STRING).addLimitClause(null).build();

    var expected = "SELECT * FROM table_name;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNonEmptyLimit_success() {
    var current = new NativeQueryStringBuilder().select("table_name", ALL_COLUMNS_STRING).addLimitClause(3).build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 LIMIT 3;";

    assertEquals(expected, current);
  }

  @Test
  public void build_addBSGeometryCondition_success() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addBSGeometryCondition()
      .build();

    var expected =
      "SELECT * FROM table_name WHERE 1 = 1 AND ST_3DDWithin(position, ST_MakePoint(:x1, :y1, :z1), :radius);";

    assertEquals(expected, current);
  }

  @Test
  public void build_addAABBGeometryCondition_success() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addAABBGeometryCondition()
      .build();

    var expected =
      "SELECT * FROM table_name WHERE 1 = 1 AND position &&& ST_3DMakeBox(ST_MakePoint(:x1, :y1, :z1), ST_MakePoint(:x2, :y2, :z2));";

    assertEquals(expected, current);
  }

  @Test
  public void build_addKNNGeometryCondition_success() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addKNNGeometryCondition()
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 ORDER BY position <<->> ST_MakePoint(:x1, :y1, :z1) LIMIT :k;";

    assertEquals(expected, current);
  }

  @Test
  public void build_addJsonFilterConditions_success() {
    var current = new NativeQueryStringBuilder()
      .select("table_name", ALL_COLUMNS_STRING)
      .addJsonFilterConditions(
        "measurements",
        List.of(
          new FilterCondition("key", Operator.EQUALS, 5),
          new FilterCondition("key1,subkey1", Operator.LESS_THAN, 20),
          new FilterCondition("key2,subkey2,subsubkey2", Operator.GREATER_THAN, 10)
        )
      )
      .build();

    var expected =
      "SELECT * FROM table_name WHERE 1 = 1 AND jsonb_typeof(measurements #> '{key}') = 'number' AND (measurements #>> '{key}')::NUMERIC = 5.0 AND jsonb_typeof(measurements #> '{key1,subkey1}') = 'number' AND (measurements #>> '{key1,subkey1}')::NUMERIC < 20.0 AND jsonb_typeof(measurements #> '{key2,subkey2,subsubkey2}') = 'number' AND (measurements #>> '{key2,subkey2,subsubkey2}')::NUMERIC > 10.0;";
    assertEquals(expected, current);
  }

  public void build_passEmptySkip_success() {
    var current = new NativeQueryStringBuilder().select("table_name", ALL_COLUMNS_STRING).addSkipClause(null).build();

    var expected = "SELECT * FROM table_name;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNonEmptySkip_success() {
    var current = new NativeQueryStringBuilder().select("table_name", ALL_COLUMNS_STRING).addSkipClause(3).build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id % 3 = 0;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNonEmptySkipWith0_success() {
    var current = new NativeQueryStringBuilder().select("table_name", ALL_COLUMNS_STRING).addSkipClause(0).build();

    var expected = "SELECT * FROM table_name;";

    assertEquals(expected, current);
  }
}
