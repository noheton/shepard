package de.dlr.shepard.mongoDB;

import static com.mongodb.client.model.Filters.eq;

import java.io.InputStream;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import de.dlr.shepard.util.UUIDHelper;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class FileService {

	private static final int CHUNK_SIZE_BYTES = 1024 * 1024; // 1 MiB

	private MongoDBConnector mongoDBConnector = MongoDBConnector.getInstance();
	private UUIDHelper uuidHelper = new UUIDHelper();

	public String createFileContainer() {
		String oid = "FileContainer" + uuidHelper.getUUID().toString();
		mongoDBConnector.createCollection(oid);
		return oid;
	}

	public File createFile(String mongoid, String fileName, InputStream inputStream) {
		MongoCollection<Document> collection = mongoDBConnector.getDatabase().getCollection(mongoid);
		if (collection == null) {
			log.error("Could not find container with mongoid: {}", mongoid);
			return null;
		}
		GridFSBucket gridBucket = mongoDBConnector.createBucket();
		GridFSUploadOptions uploadOptions = new GridFSUploadOptions().chunkSizeBytes(CHUNK_SIZE_BYTES);
		String fileMongoId = gridBucket.uploadFromStream(fileName, inputStream, uploadOptions).toString();
		Document fileDBEntry = new Document("name", fileName).append("container", mongoid).append("FileMongoId",
				fileMongoId);
		collection.insertOne(fileDBEntry);
		String id = fileDBEntry.getObjectId("_id").toHexString();
		return new File(id, fileName);
	}

	public NamedInputStream getPayload(String containerId, String fileoid) {
		MongoCollection<Document> collection = mongoDBConnector.getDatabase().getCollection(containerId);
		if (collection == null) {
			log.error("Could not find container with mongoid: {}", containerId);
			return null;
		}
		ObjectId oid = new ObjectId(fileoid);
		var payloadDocument = collection.find(eq("_id", oid)).first();
		if (payloadDocument == null) {
			log.error("Could not find document with oid: {}", fileoid);
			return null;
		}
		ObjectId fileMongoId = new ObjectId(payloadDocument.getString("FileMongoId"));
		String name = payloadDocument.getString("name");
		GridFSBucket gridBucket = mongoDBConnector.createBucket();
		InputStream inputStream = gridBucket.openDownloadStream(fileMongoId);
		return new NamedInputStream(inputStream, name);
	}

	public File getFile(String containerId, String fileoid) {
		MongoCollection<Document> collection = mongoDBConnector.getDatabase().getCollection(containerId);
		if (collection == null) {
			log.error("Could not find container with mongoid: {}", containerId);
			return null;
		}
		var file = collection.find(eq("_id", new ObjectId(fileoid))).first();
		if (file == null) {
			log.error("Could not find file with oid: {}", fileoid);
			return null;
		}
		File ret = new File(fileoid, file.getString("name"));
		return ret;
	}

	public boolean deleteFileContainer(String mongoid) {
		MongoCollection<Document> toDelete = mongoDBConnector.getDatabase().getCollection(mongoid);
		if (toDelete == null) {
			log.error("Could not delete container with mongoid: {}", mongoid);
			return false;
		}
		GridFSBucket gridBucket = mongoDBConnector.createBucket();
		for (Document doc : toDelete.find()) {
			gridBucket.delete(new ObjectId(doc.get("FileMongoId").toString()));
		}
		toDelete.drop();
		return true;
	}
}
