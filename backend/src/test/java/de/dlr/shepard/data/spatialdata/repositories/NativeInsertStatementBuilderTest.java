package de.dlr.shepard.data.spatialdata.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class NativeInsertStatementBuilderTest {

  @Test
  public void build_addOneLineOfValues_success() {
    NativeInsertStatementBuilder builder = new NativeInsertStatementBuilder();
    builder.insert("INSERT INTO table_name (column1, column2)").addValues("value1, value2");
    String result = builder.build();
    assertEquals("INSERT INTO table_name (column1, column2) VALUES (value1, value2);", result);
  }

  @Test
  public void build_multipleValuesAdded_success() {
    NativeInsertStatementBuilder builder = new NativeInsertStatementBuilder();
    builder.insert("INSERT INTO table_name (column1, column2)").addValues("value1, value2").addValues("value3, value4");
    String result = builder.build();
    assertEquals("INSERT INTO table_name (column1, column2) VALUES (value1, value2),(value3, value4);", result);
  }
}
