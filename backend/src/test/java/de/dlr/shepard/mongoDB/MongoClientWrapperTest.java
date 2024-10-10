package de.dlr.shepard.mongoDB;

import static org.junit.Assert.assertEquals;

import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class MongoClientWrapperTest {

  @Inject
  MongoClientWrapper mongoClientWrapper;

  @Test
  public void determineDatabaseNameWithDefaultDatabaseNameTest() {
    assertEquals(mongoClientWrapper.determineDatabaseName("mongodb://mongo:shepard@[::1]:27017"), "database");
  }

  @Test
  public void determineDatabaseNameWithCustomDatabaseNameTest() {
    assertEquals(
      mongoClientWrapper.determineDatabaseName("mongodb://mongo:shepard@[::1]:27017/myVeryCoolDatabaseName"),
      "myVeryCoolDatabaseName"
    );
  }
}
