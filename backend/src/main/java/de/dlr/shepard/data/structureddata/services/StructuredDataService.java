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
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import java.util.UUID;
import org.bson.Document;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;

@RequestScoped
public class StructuredDataService {

  @Inject
  private DateHelper dateHelper;

  @Inject
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  private static final String ID_ATTR = "_id";
  private static final String META_OBJECT = "_meta";

  public String createStructuredDataContainer() {
    String mongoId = "StructuredDataContainer" + UUID.randomUUID().toString();
    mongoDatabase.createCollection(mongoId);
    return mongoId;
  }

  public StructuredData createStructuredData(String mongoId, StructuredDataPayload payload) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDatabase.getCollection(mongoId);
    } catch (IllegalArgumentException e) {
      String errorMsg = String.format("Could not find container with mongoId: %s", mongoId);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
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
      String errorMsg = String.format("Could not write to mongodb: %s", e.toString());
      Log.error(errorMsg);
      throw new InternalServerErrorException(errorMsg);
    }
    structuredData.setOid(toInsert.getObjectId(ID_ATTR).toHexString());
    return structuredData;
  }

  public void deleteStructuredDataContainer(String mongoId) {
    MongoCollection<Document> toDelete;
    try {
      toDelete = mongoDatabase.getCollection(mongoId);
    } catch (IllegalArgumentException e) {
      String errorMsg = String.format("Could not delete container with mongoId: %s", mongoId);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    toDelete.drop();
  }

  public StructuredDataPayload getPayload(String mongoId, String oid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDatabase.getCollection(mongoId);
    } catch (IllegalArgumentException e) {
      String errorMsg = String.format("Could not find container with mongoId: %s", mongoId);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    Document payloadDocument;
    try {
      payloadDocument = collection.find(eq(ID_ATTR, new ObjectId(oid))).first();
      if (payloadDocument == null) {
        String errorMsg = String.format("Could not find document with oid: %s", oid);
        Log.error(errorMsg);
        throw new NotFoundException(errorMsg);
      }
    } catch (Exception e) {
      String errorMsg = String.format("Could not find document with oid: %s", oid);
      Log.error(e);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    var structuredDataDocument = payloadDocument.get(META_OBJECT, Document.class);
    var structuredData = structuredDataDocument != null
      ? new StructuredData(structuredDataDocument)
      : new StructuredData();
    structuredData.setOid(oid);
    var payload = new StructuredDataPayload(structuredData, payloadDocument.toJson());
    return payload;
  }

  public void deletePayload(String mongoId, String oid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDatabase.getCollection(mongoId);
    } catch (IllegalArgumentException e) {
      String errorMsg = String.format("Could not find container with mongoId: %s", mongoId);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    var doc = collection.findOneAndDelete(eq(ID_ATTR, new ObjectId(oid)));
    if (doc == null) {
      String errorMsg = String.format("Could not delete document with oid: %s", oid);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
  }
}
