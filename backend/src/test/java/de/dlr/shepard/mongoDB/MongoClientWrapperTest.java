package de.dlr.shepard.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

public class MongoClientWrapperTest {

  @Test
  public void determineDatabaseNameWithDefaultDatabaseNameTest() {
    assertEquals(MongoClientWrapper.determineDatabaseName("mongodb://mongo:shepard@[::1]:27017"), "database");
  }

  @Test
  public void determineDatabaseNameWithCustomDatabaseNameTest() {
    assertEquals(
      MongoClientWrapper.determineDatabaseName("mongodb://mongo:shepard@[::1]:27017/myVeryCoolDatabaseName"),
      "myVeryCoolDatabaseName"
    );
  }
}
