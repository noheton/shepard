package de.dlr.shepard.mongoDB;

import com.mongodb.ConnectionString;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Service to retrieve the database name of the MongoDB instance.
 */
@ApplicationScoped
public class MongoDBDatabaseNameService {

  @ConfigProperty(name = "quarkus.mongodb.connection-string")
  private String connectionStringProperty;

  private String databaseName;

  @PostConstruct
  public void init() {
    String dbName = new ConnectionString(connectionStringProperty).getDatabase();
    if (dbName != null && !dbName.isBlank()) {
      this.databaseName = dbName;
    } else {
      Log.warn(
        "Could not retrieve a MongoDB database name from the connection string. Using fallback default database name: 'database'."
      );
      this.databaseName = "database";
    }
  }

  public String getName() {
    return this.databaseName;
  }
}
