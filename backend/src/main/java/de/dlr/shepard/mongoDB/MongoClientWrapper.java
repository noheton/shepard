package de.dlr.shepard.mongoDB;

import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import jakarta.inject.Named;
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
      Log.warn(
        "Could not retrieve a MongoDB database name from the connection string. Using fallback default database name: 'database'."
      );
      return DEFAULT_DATABASE_NAME;
    }
    return dbName;
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
