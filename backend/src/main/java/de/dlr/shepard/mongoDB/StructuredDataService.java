package de.dlr.shepard.mongoDB;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.util.DateHelper;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;

@Slf4j
@RequestScoped
public class StructuredDataService {

  private static final String ID_ATTR = "_id";
  private static final String META_OBJECT = "_meta";
  private MongoDBConnector mongoDBConnector;
  private DateHelper dateHelper;

  StructuredDataService() {}

  @Inject
  public StructuredDataService(MongoDBConnector mongoDBConnector, DateHelper dateHelper) {
    this.mongoDBConnector = mongoDBConnector;
    this.dateHelper = dateHelper;
  }

  public String createStructuredDataContainer() {
    String mongoid = "StructuredDataContainer" + UUID.randomUUID().toString();
    mongoDBConnector.createCollection(mongoid);
    return mongoid;
  }

  public StructuredData createStructuredData(String mongoid, StructuredDataPayload payload) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDBConnector.getCollection(mongoid);
    } catch (IllegalArgumentException e) {
      log.error("Could not find container with mongoid: {}", mongoid);
      return null;
    }
    Document toInsert;
    try {
      toInsert = Document.parse(payload.getPayload());
    } catch (JsonParseException e) {
      log.error("Could not parse json: {}", payload.getPayload());
      throw new InvalidBodyException("The specified payload is not json parsable");
    }

    // Remove fields beginning with an underscore (protected)
    var forbidden = toInsert.keySet().stream().filter(k -> k.startsWith("_")).toList();
    forbidden.forEach(toInsert::remove);

    // Add meta data
    var newName = payload.getStructuredData() != null ? payload.getStructuredData().getName() : null;
    var structuredData = new StructuredData(newName, dateHelper.getDate());
    toInsert.append(META_OBJECT, structuredData);
    try {
      collection.insertOne(toInsert);
    } catch (MongoException e) {
      log.error("Could not write to mongodb: {}", e.toString());
      return null;
    }
    structuredData.setOid(toInsert.getObjectId(ID_ATTR).toHexString());
    return structuredData;
  }

  public boolean deleteStructuredDataContainer(String mongoid) {
    MongoCollection<Document> toDelete;
    try {
      toDelete = mongoDBConnector.getCollection(mongoid);
    } catch (IllegalArgumentException e) {
      log.error("Could not delete container with mongoid: {}", mongoid);
      return false;
    }
    toDelete.drop();
    return true;
  }

  public StructuredDataPayload getPayload(String mongoid, String oid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDBConnector.getCollection(mongoid);
    } catch (IllegalArgumentException e) {
      log.error("Could not find container with mongoid: {}", mongoid);
      return null;
    }
    var payloadDocument = collection.find(eq(ID_ATTR, new ObjectId(oid))).first();
    if (payloadDocument == null) {
      log.error("Could not find document with oid: {}", oid);
      return null;
    }
    var structuredDataDocument = payloadDocument.get(META_OBJECT, Document.class);
    var structuredData = structuredDataDocument != null
      ? new StructuredData(structuredDataDocument)
      : new StructuredData();
    structuredData.setOid(oid);
    var payload = new StructuredDataPayload(structuredData, payloadDocument.toJson());
    return payload;
  }

  public boolean deletePayload(String mongoid, String oid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDBConnector.getCollection(mongoid);
    } catch (IllegalArgumentException e) {
      log.error("Could not find container with mongoid: {}", mongoid);
      return false;
    }
    var doc = collection.findOneAndDelete(eq(ID_ATTR, new ObjectId(oid)));
    if (doc == null) {
      log.warn("Could not find and delete document with oid: {}", oid);
      return true;
    }
    return true;
  }
}
