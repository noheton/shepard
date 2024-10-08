package de.dlr.shepard.mongoDB;

import com.mongodb.ConnectionString;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import de.dlr.shepard.util.IConnector;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.Document;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Connector for read and write access to the Mongo database. The class
 * represents the lowest level of data access to the mongo database.
 *
 */
@ApplicationScoped
public class MongoDBConnector implements IConnector {

  private MongoClient mongoClient;

  MongoDBConnector() {}

  @ConfigProperty(name = "quarkus.mongodb.connection_string")
  private String connectionStringProperty;

  private ConnectionString connectionString;

  @Inject
  public MongoDBConnector(MongoClient client) {
    this.mongoClient = client;
  }

  @Override
  public boolean connect() {
    this.connectionString = new ConnectionString(connectionStringProperty);
    return true;
  }

  @Override
  public boolean disconnect() {
    // Nothing to do here
    return true;
  }

  @Override
  public boolean alive() {
    Document result;
    try {
      result = getDatabase().runCommand(new Document("buildInfo", "1"));
    } catch (MongoException ex) {
      return false;
    }
    return result.containsKey("ok");
  }

  public void createCollection(String name) {
    getDatabase().createCollection(name);
  }

  public MongoCollection<Document> getCollection(String name) {
    if (mongoClient == null) connect();
    return getDatabase().getCollection(name);
  }

  public GridFSBucket createBucket() {
    if (mongoClient == null) connect();
    return GridFSBuckets.create(getDatabase());
  }

  private MongoDatabase getDatabase() {
    String databaseName = connectionString.getDatabase();
    if (databaseName == null) {
      databaseName = "database";
    }
    return mongoClient.getDatabase(databaseName);
  }
}
