package de.dlr.shepard.mongoDB;

import static com.mongodb.client.model.Filters.eq;

import java.util.UUID;

import org.bson.Document;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class StructuredDataService {

	private MongoDBConnector mongoDBConnector = MongoDBConnector.getInstance();

	public String createStructuredDataContainer() {
		String mongoid = "StructuredDataContainer" + UUID.randomUUID().toString();
		mongoDBConnector.createCollection(mongoid);
		return mongoid;
	}

	public StructuredData createStructuredData(String mongoid, StructuredDataPayload payload) {
		MongoCollection<Document> collection = mongoDBConnector.getDatabase().getCollection(mongoid);
		if (collection == null) {
			log.error("Could not find container with mongoid: {}", mongoid);
			return null;
		}
		Document toInsert;
		try {
			toInsert = Document.parse(payload.getPayload());
		} catch (JsonParseException e) {
			log.error("Could not parse json: {}", payload.getPayload());
			return null;
		}
		try {
			collection.insertOne(toInsert);
		} catch (MongoException e) {
			log.error("Could not write to mongodb: {}", e.toString());
			return null;
		}
		ObjectId oid = toInsert.getObjectId("_id");
		return new StructuredData(oid.toHexString());
	}

	public boolean deleteStructuredDataContainer(String mongoid) {
		MongoCollection<Document> toDelete = mongoDBConnector.getDatabase().getCollection(mongoid);
		if (toDelete == null) {
			log.error("Could not delete container with mongoid: {}", mongoid);
			return false;
		}
		toDelete.drop();
		return true;
	}

	public StructuredDataPayload getPayload(String mongoid, String oid) {
		MongoCollection<Document> collection = mongoDBConnector.getDatabase().getCollection(mongoid);
		if (collection == null) {
			log.error("Could not find container with mongoid: {}", mongoid);
			return null;
		}
		var payloadDocument = collection.find(eq("_id", new ObjectId(oid))).first();
		if (payloadDocument == null) {
			log.error("Could not find document with oid: {}", oid);
			return null;
		}
		StructuredDataPayload payload = new StructuredDataPayload(new StructuredData(oid), payloadDocument.toJson());
		return payload;
	}

	public boolean deletePayload(String mongoid, String oid) {
		MongoCollection<Document> collection = mongoDBConnector.getDatabase().getCollection(mongoid);
		if (collection == null) {
			log.error("Could not find container with mongoid: {}", mongoid);
			return false;
		}
		var result = collection.deleteOne(eq("_id", new ObjectId(oid)));
		if (!result.wasAcknowledged()) {
			log.error("Could not delete document with oid: {}", oid);
			return false;
		}
		return true;
	}

}
