package de.dlr.shepard.common.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class MongoClientWrapperTest {

  @Test
  public void determineDatabaseNameThrowsWhenNoDatabaseNameTest() {
    assertThrows(
      IllegalStateException.class,
      () -> MongoClientWrapper.determineDatabaseName("mongodb://mongo:shepard@[::1]:27017")
    );
  }

  @Test
  public void determineDatabaseNameWithCustomDatabaseNameTest() {
    assertEquals(
      MongoClientWrapper.determineDatabaseName("mongodb://mongo:shepard@[::1]:27017/myVeryCoolDatabaseName"),
      "myVeryCoolDatabaseName"
    );
  }
}
