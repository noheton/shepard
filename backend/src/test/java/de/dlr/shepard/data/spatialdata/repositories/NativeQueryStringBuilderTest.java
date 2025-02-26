package de.dlr.shepard.data.spatialdata.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class NativeQueryStringBuilderTest {

  @Test
  public void build_simpleSelectQuery_success() {
    var builder = new NativeQueryStringBuilder();
    var current = builder.select("SELECT * FROM table_name").build();

    var expected = "SELECT * FROM table_name;";

    assertEquals(expected, current);
  }

  @Test
  public void build_addOneCondition_success() {
    var current = new NativeQueryStringBuilder().select("SELECT * FROM table_name").addWhereCondition("id", 1).build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id = 1;";

    assertEquals(expected, current);
  }

  @Test
  public void build_addTwoConditions_success() {
    var current = new NativeQueryStringBuilder()
      .select("SELECT * FROM table_name")
      .addWhereCondition("id", 1)
      .addWhereCondition("role", "assistant")
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id = 1 AND role = 'assistant';";

    assertEquals(expected, current);
  }

  @Test
  public void build_addTimeCondition_success() {
    var current = new NativeQueryStringBuilder()
      .select("SELECT * FROM table_name")
      .addWhereCondition("id", 1)
      .addTimeCondition("time", 123l, 234l)
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id = 1 AND time > 123 AND time < 234;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passOnlyTimestampStart_success() {
    var current = new NativeQueryStringBuilder()
      .select("SELECT * FROM table_name")
      .addTimeCondition("time", 123l, null)
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND time > 123;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passOnlyTimestampEnd_success() {
    var current = new NativeQueryStringBuilder()
      .select("SELECT * FROM table_name")
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
      .select("SELECT * FROM table_name")
      .addWhereCondition("id", 1)
      .addJsonContainsCondition("meta", jsonFilter)
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id = 1 AND meta @> '{\"track\":2}';";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNullValueInCondition_isIgnored() {
    var current = new NativeQueryStringBuilder()
      .select("SELECT * FROM table_name")
      .addWhereCondition("id", null)
      .build();

    var expected = "SELECT * FROM table_name;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNullValueInSecondCondition_isIgnored() {
    var current = new NativeQueryStringBuilder()
      .select("SELECT * FROM table_name")
      .addWhereCondition("id", 1)
      .addJsonContainsCondition("meta", null)
      .addTimeCondition("time", null, null)
      .build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id = 1;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passEmptyLimit_success() {
    var current = new NativeQueryStringBuilder().select("SELECT * FROM table_name").addLimitClause(null).build();

    var expected = "SELECT * FROM table_name;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNonEmptyLimit_success() {
    var current = new NativeQueryStringBuilder().select("SELECT * FROM table_name").addLimitClause(3).build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 LIMIT 3;";

    assertEquals(expected, current);
  }

  @Test
  public void build_addBSGeometryCondition_success() {
    var current = new NativeQueryStringBuilder().select("SELECT * FROM table_name").addBSGeometryCondition().build();

    var expected =
      "SELECT * FROM table_name WHERE 1 = 1 AND ST_3DDWithin(position, ST_MakePoint(:x1, :y1, :z1), :radius);";

    assertEquals(expected, current);
  }

  @Test
  public void build_addAABBGeometryCondition_success() {
    var current = new NativeQueryStringBuilder().select("SELECT * FROM table_name").addAABBGeometryCondition().build();

    var expected =
      "SELECT * FROM table_name WHERE 1 = 1 AND position &&& ST_3DMakeBox(ST_MakePoint(:x1, :y1, :z1), ST_MakePoint(:x2, :y2, :z2));";

    assertEquals(expected, current);
  }

  @Test
  public void build_addKNNGeometryCondition_success() {
    var current = new NativeQueryStringBuilder().select("SELECT * FROM table_name").addKNNGeometryCondition().build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 ORDER BY position <<->> ST_MakePoint(:x1, :y1, :z1) LIMIT :k;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passEmptySkip_success() {
    var current = new NativeQueryStringBuilder().select("SELECT * FROM table_name").addSkipClause(null).build();

    var expected = "SELECT * FROM table_name;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNonEmptySkip_success() {
    var current = new NativeQueryStringBuilder().select("SELECT * FROM table_name").addSkipClause(3).build();

    var expected = "SELECT * FROM table_name WHERE 1 = 1 AND id % 3 = 0;";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNonEmptySkipWith0_success() {
    var current = new NativeQueryStringBuilder().select("SELECT * FROM table_name").addSkipClause(0).build();

    var expected = "SELECT * FROM table_name;";

    assertEquals(expected, current);
  }
}
