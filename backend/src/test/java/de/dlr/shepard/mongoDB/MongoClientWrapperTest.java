package de.dlr.shepard.mongoDB;

import static org.junit.Assert.assertEquals;
import org.junit.jupiter.api.Test;

public class MongoClientWrapperTest {

  @Test
  public void determineDatabaseNameWithDefaultDatabaseNameTest() {
    String databaseName = MongoClientWrapper.determineDatabaseName("mongodb://mongo:shepard@[::1]:27017");
    assertEquals(databaseName, "database");
  }

  @Test
  public void determineDatabaseNameWithCustomDatabaseNameTest() {
    String databaseName = MongoClientWrapper.determineDatabaseName(
      "mongodb://mongo:shepard@[::1]:27017/myVeryCoolDatabaseName"
    );
    assertEquals(databaseName, "myVeryCoolDatabaseName");
  }
}
