package de.dlr.shepard.common.mongoDB;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Wrapper for Quarkus MongoDB client.
 *
 * Enables consistent injection of the correct MongoDB database.
 */
@ApplicationScoped
public class MongoClientWrapper {

  @ConfigProperty(name = "quarkus.mongodb.connection-string")
  String connectionStringProperty;

  /**
   * Explicit database name override. When set, it takes precedence over the path
   * segment in the connection string and suppresses the fallback warning.
   */
  @ConfigProperty(name = "quarkus.mongodb.database")
  Optional<String> explicitDatabaseName;

  @Inject
  MongoClient mongoClient;

  private MongoDatabase mongoDatabase;

  private static final String DEFAULT_DATABASE_NAME = "database";

  /**
   * Retrieve MongoDB database name from connection string and initialize a MongoDatabase object after injection.
   *
   * This is needed since the function relies on an injection of the 'connectionStringProperty'.
   */
  @PostConstruct
  public void init() {
    String databaseName = determineDatabaseName(connectionStringProperty);
    if (shouldWarnAboutFallback(databaseName, explicitDatabaseName)) {
      Log.warn(
        "MongoDB database name not explicitly configured — using fallback 'database'. " +
        "Set quarkus.mongodb.connection-string to include a DB path segment or set " +
        "quarkus.mongodb.database explicitly."
      );
    }
    try {
      this.mongoDatabase = mongoClient.getDatabase(databaseName);
    } catch (IllegalArgumentException e) {
      Log.error("Could not get MongoDB database because of invalid database name: " + databaseName);
      throw e;
    }
  }

  protected static String determineDatabaseName(String connectionString) {
    String dbName = new ConnectionString(connectionString).getDatabase();
    if (dbName == null || dbName.isBlank()) {
      // No DB segment in the URI — caller (init) will emit the operator warning.
      return DEFAULT_DATABASE_NAME;
    }
    return dbName;
  }

  /**
   * Returns {@code true} when the resolved database name is the silent fallback
   * {@code "database"} AND no explicit {@code quarkus.mongodb.database} override
   * is configured.  When the operator has set the explicit key the fallback is
   * intentional and no warning is needed.
   *
   * <p>Extracted as a static helper so it can be tested without a running MongoDB
   * or CDI container.
   *
   * @param resolvedName     name returned by {@link #determineDatabaseName}
   * @param explicitOverride value of the {@code quarkus.mongodb.database} config key
   * @return {@code true} when the operator warning should be emitted
   */
  static boolean shouldWarnAboutFallback(String resolvedName, Optional<String> explicitOverride) {
    if (!DEFAULT_DATABASE_NAME.equals(resolvedName)) {
      return false;
    }
    // Explicit override present and non-blank → operator knew what they were doing.
    return explicitOverride.isEmpty() || explicitOverride.get().isBlank();
  }

  /**
   * Producer injection function to return the initialized MongoDatabase object.
   *
   * The MongoDatabase object can be injected in other classes similar to the following example.
   * Keep in mind that the string 'mongoDatabase' shall not be changed in order to make the injection work.
   * <pre>
   * {@code
   * @Inject
   * @Named("mongoDatabase")
   * MongoDatabase mongoDatabase;
   * }
   * </pre>
   */
  @Produces
  @Named("mongoDatabase")
  public MongoDatabase getMongoDatabase() {
    return this.mongoDatabase;
  }
}
