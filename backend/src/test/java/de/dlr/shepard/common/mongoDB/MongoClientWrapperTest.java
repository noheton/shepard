package de.dlr.shepard.common.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

public class MongoClientWrapperTest {

  // ── determineDatabaseName ─────────────────────────────────────────────────

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

  // ── shouldWarnAboutFallback ───────────────────────────────────────────────

  /**
   * Resolved name is the fallback "database" AND no explicit config key is set:
   * operator has not configured the DB name → warn.
   */
  @Test
  public void shouldWarn_whenFallbackNameAndNoExplicitConfig() {
    assertTrue(MongoClientWrapper.shouldWarnAboutFallback("database", Optional.empty()));
  }

  /**
   * Resolved name is the fallback "database" AND the explicit config key is set to
   * a blank string (mis-configuration): treat as absent → warn.
   */
  @Test
  public void shouldWarn_whenFallbackNameAndExplicitConfigIsBlank() {
    assertTrue(MongoClientWrapper.shouldWarnAboutFallback("database", Optional.of("  ")));
  }

  /**
   * Resolved name is the fallback "database" BUT an explicit {@code quarkus.mongodb.database}
   * value is set: operator intentionally named the DB "database" → no warn.
   */
  @Test
  public void shouldNotWarn_whenFallbackNameButExplicitConfigIsSet() {
    assertFalse(MongoClientWrapper.shouldWarnAboutFallback("database", Optional.of("database")));
  }

  /**
   * Resolved name is a real DB name extracted from the connection string → no warn,
   * regardless of explicit config.
   */
  @Test
  public void shouldNotWarn_whenNonFallbackNameFromConnectionString() {
    assertFalse(MongoClientWrapper.shouldWarnAboutFallback("myVeryCoolDatabaseName", Optional.empty()));
  }

  /**
   * Resolved name is a real DB name AND explicit config is also set (redundant but
   * valid) → no warn.
   */
  @Test
  public void shouldNotWarn_whenNonFallbackNameAndExplicitConfigAlsoSet() {
    assertFalse(MongoClientWrapper.shouldWarnAboutFallback("production_db", Optional.of("production_db")));
  }
}
