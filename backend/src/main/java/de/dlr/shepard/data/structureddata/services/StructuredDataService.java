package de.dlr.shepard.data.structureddata.services;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.UUID;
import org.bson.Document;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;

@RequestScoped
public class StructuredDataService {

  private static final String ID_ATTR = "_id";
  private static final String META_OBJECT = "_meta";
  private DateHelper dateHelper;

  StructuredDataService() {}

  @Inject
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  @Inject
  public StructuredDataService(DateHelper dateHelper) {
    this.dateHelper = dateHelper;
  }

  public String createStructuredDataContainer() {
    String mongoid = "StructuredDataContainer" + UUID.randomUUID().toString();
    mongoDatabase.createCollection(mongoid);
    return mongoid;
  }

  public StructuredData createStructuredData(String mongoid, StructuredDataPayload payload) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDatabase.getCollection(mongoid);
    } catch (IllegalArgumentException e) {
      Log.errorf("Could not find container with mongoid: %s", mongoid);
      return null;
    }
    Document toInsert;
    try {
      toInsert = Document.parse(payload.getPayload());
    } catch (JsonParseException e) {
      Log.errorf("Could not parse json: %s", payload.getPayload());
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
      Log.errorf("Could not write to mongodb: %s", e.toString());
      return null;
    }
    structuredData.setOid(toInsert.getObjectId(ID_ATTR).toHexString());
    return structuredData;
  }

  public boolean deleteStructuredDataContainer(String mongoid) {
    MongoCollection<Document> toDelete;
    try {
      toDelete = mongoDatabase.getCollection(mongoid);
    } catch (IllegalArgumentException e) {
      Log.errorf("Could not delete container with mongoid: %s", mongoid);
      return false;
    }
    toDelete.drop();
    return true;
  }

  public StructuredDataPayload getPayload(String mongoid, String oid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDatabase.getCollection(mongoid);
    } catch (IllegalArgumentException e) {
      Log.errorf("Could not find container with mongoid: %s", mongoid);
      return null;
    }
    var payloadDocument = collection.find(eq(ID_ATTR, new ObjectId(oid))).first();
    if (payloadDocument == null) {
      Log.errorf("Could not find document with oid: %s", oid);
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
      collection = mongoDatabase.getCollection(mongoid);
    } catch (IllegalArgumentException e) {
      Log.errorf("Could not find container with mongoid: %s", mongoid);
      return false;
    }
    var doc = collection.findOneAndDelete(eq(ID_ATTR, new ObjectId(oid)));
    if (doc == null) {
      Log.warnf("Could not find and delete document with oid: %s", oid);
      return true;
    }
    return true;
  }
}
