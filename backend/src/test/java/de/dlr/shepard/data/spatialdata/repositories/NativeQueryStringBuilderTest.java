package de.dlr.shepard.data.spatialdata.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

public class NativeQueryStringBuilderTest {

  @Test
  public void build_simpleSelectQuery_success() {
    var builder = new NativeQueryStringBuilder();
    var current = builder.select("select * from table_name").build();

    var expected = "select * from table_name";

    assertEquals(expected, current);
  }

  @Test
  public void build_addOneCondition_success() {
    var current = new NativeQueryStringBuilder().select("select * from table_name").addCondition("id", 1).build();

    var expected = "select * from table_name where 1 = 1 and id = 1";

    assertEquals(expected, current);
  }

  @Test
  public void build_addTwoConditions_success() {
    var current = new NativeQueryStringBuilder()
      .select("select * from table_name")
      .addCondition("id", 1)
      .addCondition("role", "assistant")
      .build();

    var expected = "select * from table_name where 1 = 1 and id = 1 and role = 'assistant'";

    assertEquals(expected, current);
  }

  @Test
  public void build_addTimeCondition_success() {
    var current = new NativeQueryStringBuilder()
      .select("select * from table_name")
      .addCondition("id", 1)
      .addTimeCondition("time", 123l, 234l)
      .build();

    var expected = "select * from table_name where 1 = 1 and id = 1 and time > 123 and time < 234";

    assertEquals(expected, current);
  }

  @Test
  public void build_passOnlyTimestampStart_success() {
    var current = new NativeQueryStringBuilder()
      .select("select * from table_name")
      .addTimeCondition("time", 123l, null)
      .build();

    var expected = "select * from table_name where 1 = 1 and time > 123";

    assertEquals(expected, current);
  }

  @Test
  public void build_passOnlyTimestampEnd_success() {
    var current = new NativeQueryStringBuilder()
      .select("select * from table_name")
      .addTimeCondition("time", null, 234l)
      .build();

    var expected = "select * from table_name where 1 = 1 and time < 234";

    assertEquals(expected, current);
  }

  @Test
  public void build_addJsonCondition_success() {
    var jsonFilter = new HashMap<String, Object>();
    jsonFilter.put("track", 2);

    var current = new NativeQueryStringBuilder()
      .select("select * from table_name")
      .addCondition("id", 1)
      .addJsonCondition("meta", jsonFilter)
      .build();

    var expected = "select * from table_name where 1 = 1 and id = 1 and meta @> '{\"track\":2}'";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNullValueInCondition_isIgnored() {
    var current = new NativeQueryStringBuilder().select("select * from table_name").addCondition("id", null).build();

    var expected = "select * from table_name";

    assertEquals(expected, current);
  }

  @Test
  public void build_passNullValueInSecondCondition_isIgnored() {
    var current = new NativeQueryStringBuilder()
      .select("select * from table_name")
      .addCondition("id", 1)
      .addJsonCondition("meta", null)
      .addTimeCondition("time", null, null)
      .build();

    var expected = "select * from table_name where 1 = 1 and id = 1";

    assertEquals(expected, current);
  }
}
