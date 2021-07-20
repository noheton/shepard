package de.dlr.shepard.mongoDB;

import static com.mongodb.client.model.Filters.eq;

import java.io.InputStream;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.model.GridFSUploadOptions;

import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.UUIDHelper;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class FileService {

	private static final int CHUNK_SIZE_BYTES = 1024 * 1024; // 1 MiB
	private static final String ID_ATTR = "_id";
	private static final String FILENAME_ATTR = "name";
	private static final String CONTAINER_ATTR = "container";
	private static final String FILEID_ATTR = "FileMongoId";
	private static final String CREATEDAT_ATTR = "createdAt";

	private MongoDBConnector mongoDBConnector = MongoDBConnector.getInstance();
	private UUIDHelper uuidHelper = new UUIDHelper();
	private DateHelper dateHelper = new DateHelper();

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
		var createdAt = dateHelper.getDate();
		var doc = new Document(FILENAME_ATTR, fileName).append(CONTAINER_ATTR, mongoid).append(FILEID_ATTR, fileMongoId)
				.append(CREATEDAT_ATTR, createdAt);
		collection.insertOne(doc);
		var oid = doc.getObjectId(ID_ATTR).toHexString();
		var file = new File(oid, createdAt, fileName);
		return file;
	}

	public NamedInputStream getPayload(String containerId, String fileoid) {
		MongoCollection<Document> collection = mongoDBConnector.getDatabase().getCollection(containerId);
		if (collection == null) {
			log.error("Could not find container with mongoid: {}", containerId);
			return null;
		}
		var oid = new ObjectId(fileoid);
		var payloadDocument = collection.find(eq(ID_ATTR, oid)).first();
		if (payloadDocument == null) {
			log.error("Could not find document with oid: {}", fileoid);
			return null;
		}
		var fileId = new ObjectId(payloadDocument.getString(FILEID_ATTR));
		var filename = payloadDocument.getString(FILENAME_ATTR);
		GridFSBucket gridBucket = mongoDBConnector.createBucket();
		InputStream inputStream = gridBucket.openDownloadStream(fileId);
		return new NamedInputStream(inputStream, filename);
	}

	public File getFile(String containerId, String fileoid) {
		MongoCollection<Document> collection = mongoDBConnector.getDatabase().getCollection(containerId);
		if (collection == null) {
			log.error("Could not find container with mongoid: {}", containerId);
			return null;
		}
		var doc = collection.find(eq(ID_ATTR, new ObjectId(fileoid))).first();
		if (doc == null) {
			log.error("Could not find file with oid: {}", fileoid);
			return null;
		}
		var file = new File(fileoid, doc.getDate(CREATEDAT_ATTR), doc.getString(FILENAME_ATTR));
		return file;
	}

	public boolean deleteFileContainer(String mongoid) {
		MongoCollection<Document> toDelete = mongoDBConnector.getDatabase().getCollection(mongoid);
		if (toDelete == null) {
			log.error("Could not delete container with mongoid: {}", mongoid);
			return false;
		}
		GridFSBucket gridBucket = mongoDBConnector.createBucket();
		for (Document doc : toDelete.find()) {
			gridBucket.delete(new ObjectId(doc.getString(FILEID_ATTR)));
		}
		toDelete.drop();
		return true;
	}

	public boolean deleteFile(String mongoId, String fileoid) {
		MongoCollection<Document> collection = mongoDBConnector.getDatabase().getCollection(mongoId);
		if (collection == null) {
			log.error("Could not find container with mongoid: {}", mongoId);
			return false;
		}
		var doc = collection.findOneAndDelete(eq(ID_ATTR, new ObjectId(fileoid)));
		if (doc == null) {
			log.error("Could not find and delete file with oid: {}", fileoid);
			return false;
		}
		var gridBucket = mongoDBConnector.createBucket();
		gridBucket.delete(new ObjectId(doc.getString(FILEID_ATTR)));
		return true;
	}

}
