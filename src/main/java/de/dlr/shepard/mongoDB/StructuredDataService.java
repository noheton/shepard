package de.dlr.shepard.mongoDB;

import static com.mongodb.client.model.Filters.eq;

import java.util.UUID;

import org.bson.Document;
import org.bson.json.JsonParseException;
import org.bson.types.ObjectId;

import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.util.DateHelper;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StructuredDataService {

	private static final String ID_ATTR = "_id";
	private static final String META_OBJECT = "_meta";

	private MongoDBConnector mongoDBConnector = MongoDBConnector.getInstance();
	private DateHelper dateHelper = new DateHelper();

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
			throw new InvalidBodyException("The specified payload is not json parsable");
		}
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
		var payloadDocument = collection.find(eq(ID_ATTR, new ObjectId(oid))).first();
		if (payloadDocument == null) {
			log.error("Could not find document with oid: {}", oid);
			return null;
		}
		var structuredDataDocument = payloadDocument.get(META_OBJECT, Document.class);
		var structuredData = structuredDataDocument != null ? new StructuredData(structuredDataDocument)
				: new StructuredData();
		structuredData.setOid(oid);
		var payload = new StructuredDataPayload(structuredData, payloadDocument.toJson());
		return payload;
	}

	public boolean deletePayload(String mongoid, String oid) {
		MongoCollection<Document> collection = mongoDBConnector.getDatabase().getCollection(mongoid);
		if (collection == null) {
			log.error("Could not find container with mongoid: {}", mongoid);
			return false;
		}
		var result = collection.deleteOne(eq(ID_ATTR, new ObjectId(oid)));
		if (!result.wasAcknowledged()) {
			log.error("Could not delete document with oid: {}", oid);
			return false;
		}
		return true;
	}

}
