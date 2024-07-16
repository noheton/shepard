package de.dlr.shepard.mongoDB;

import static org.bson.codecs.configuration.CodecRegistries.fromProviders;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import de.dlr.shepard.util.IConnector;
import de.dlr.shepard.util.PropertiesHelper;
import java.util.concurrent.TimeUnit;
import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.codecs.pojo.PojoCodecProvider;

/**
 * Connector for read and write access to the Mongo database. The class
 * represents the lowest level of data access to the Influx database.
 *
 */
public class MongoDBConnector implements IConnector {

  private static MongoDBConnector instance;
  private MongoClient mongoClient;
  private MongoDatabase database;
  private CodecRegistry pojoCodecRegistry = fromRegistries(
    MongoClientSettings.getDefaultCodecRegistry(),
    fromProviders(PojoCodecProvider.builder().automatic(true).build())
  );

  /**
   * Private constructor
   */
  private MongoDBConnector() {}

  /**
   * For development reasons, there should always be just one MongoDBConnector
   * instance.
   *
   * @return The one and only MongoDBConnector instance.
   */
  public static MongoDBConnector getInstance() {
    if (instance == null) {
      instance = new MongoDBConnector();
    }
    return instance;
  }

  /**
   * Establishes a connection to the Mongo server by using the URL saved in the
   * config.properties file returned by the DatabaseHelper.
   *
   */
  @Override
  public boolean connect() {
    PropertiesHelper helper = new PropertiesHelper();
    String username = helper.getProperty("mongo.username");
    String password = helper.getProperty("mongo.password");
    String authDB = helper.getProperty("mongo.authDB");
    String host = helper.getProperty("mongo.host");

    MongoCredential credential = MongoCredential.createCredential(username, authDB, password.toCharArray());

    ConnectionString connectionString = new ConnectionString("mongodb://" + host);

    MongoClientSettings settings = MongoClientSettings.builder()
      .credential(credential)
      .applyConnectionString(connectionString)
      .applyToClusterSettings(builder -> builder.serverSelectionTimeout(2, TimeUnit.SECONDS))
      .codecRegistry(pojoCodecRegistry)
      .build();

    mongoClient = MongoClients.create(settings);
    database = mongoClient.getDatabase("database");
    return true;
  }

  @Override
  public boolean disconnect() {
    if (mongoClient != null) mongoClient.close();
    return true;
  }

  @Override
  public boolean alive() {
    Document result;
    try {
      result = database.runCommand(new Document("buildInfo", "1"));
    } catch (MongoException ex) {
      return false;
    }
    return result.containsKey("ok");
  }

  public MongoDatabase getDatabase() {
    if (mongoClient == null) connect();
    return database;
  }

  public void createCollection(String name) {
    this.database.createCollection(name);
  }

  public MongoCollection<Document> getCollection(String name) {
    if (mongoClient == null) connect();
    return database.getCollection(name);
  }

  public GridFSBucket createBucket() {
    if (mongoClient == null) connect();
    return GridFSBuckets.create(database);
  }
}
