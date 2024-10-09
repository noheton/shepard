package de.dlr.shepard.mongoDB;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import org.bson.Document;
import org.bson.types.ObjectId;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import static com.mongodb.client.model.Filters.eq;

import de.dlr.shepard.util.DateHelper;
import de.dlr.shepard.util.UUIDHelper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.xml.bind.DatatypeConverter;

@RequestScoped
public class FileService {

  private static final int CHUNK_SIZE_BYTES = 1024 * 1024; // 1 MiB
  private static final String ID_ATTR = "_id";
  private static final String FILENAME_ATTR = "name";
  private static final String FILEID_ATTR = "FileMongoId";
  private static final String CREATEDAT_ATTR = "createdAt";
  private static final String MD5_ATTR = "md5";

  @Inject
  MongoClient mongoClient;

  @Inject
  MongoDBDatabaseNameService mongoDBNameService;

  private UUIDHelper uuidHelper;
  private DateHelper dateHelper;

  FileService() {}

  @Inject
  public FileService(UUIDHelper uuidHelper, DateHelper dateHelper) {
    this.uuidHelper = uuidHelper;
    this.dateHelper = dateHelper;
  }

  public String createFileContainer() {
    String oid = "FileContainer" + uuidHelper.getUUID().toString();
    mongoClient.getDatabase(mongoDBNameService.getName()).createCollection(oid);
    return oid;
  }

  public ShepardFile createFile(String mongoid, String fileName, InputStream inputStream) {
    MongoCollection<Document> collection;
    try {
      collection = mongoClient.getDatabase(mongoDBNameService.getName()).getCollection(mongoid);
    } catch (IllegalArgumentException e) {
      Log.errorf("Could not find container with mongoid: %s", mongoid);
      return null;
    }

    MessageDigest md;
    try {
      md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      Log.error("No Such Algorithm while uploading file");
      return null;
    }
    DigestInputStream dis = new DigestInputStream(inputStream, md);
    String fileMongoId = createBucket()
      .withChunkSizeBytes(CHUNK_SIZE_BYTES)
      .uploadFromStream(fileName, dis)
      .toHexString();
    var file = new ShepardFile(dateHelper.getDate(), fileName, DatatypeConverter.printHexBinary(md.digest()));
    var doc = toDocument(file).append(FILEID_ATTR, fileMongoId);
    collection.insertOne(doc);
    file.setOid(doc.getObjectId(ID_ATTR).toHexString());
    return file;
  }

  public NamedInputStream getPayload(String containerId, String fileoid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoClient.getDatabase(mongoDBNameService.getName()).getCollection(containerId);
    } catch (IllegalArgumentException e) {
      Log.errorf("Could not find container with mongoid: %s", containerId);
      return null;
    }
    var oid = new ObjectId(fileoid);
    var payloadDocument = collection.find(eq(ID_ATTR, oid)).first();
    if (payloadDocument == null) {
      Log.errorf("Could not find document with oid: %s", fileoid);
      return null;
    }
    var fileId = new ObjectId(payloadDocument.getString(FILEID_ATTR));
    var filename = payloadDocument.getString(FILENAME_ATTR);
    var gridBucket = createBucket();
    var gridFsFile = gridBucket.find(eq(ID_ATTR, fileId)).first();
    var inputStream = gridBucket.openDownloadStream(fileId);

    return new NamedInputStream(fileoid, inputStream, filename, gridFsFile.getLength());
  }

  public ShepardFile getFile(String containerId, String fileoid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoClient.getDatabase(mongoDBNameService.getName()).getCollection(containerId);
    } catch (IllegalArgumentException e) {
      Log.errorf("Could not find container with mongoid: %s", containerId);
      return null;
    }
    var doc = collection.find(eq(ID_ATTR, new ObjectId(fileoid))).first();
    if (doc == null) {
      Log.errorf("Could not find file with oid: %s", fileoid);
      return null;
    }
    return toShepardFile(doc);
  }

  public boolean deleteFileContainer(String mongoid) {
    MongoCollection<Document> toDelete;
    try {
      toDelete = mongoClient.getDatabase(mongoDBNameService.getName()).getCollection(mongoid);
    } catch (IllegalArgumentException e) {
      Log.errorf("Could not delete container with mongoid: %s", mongoid);
      return false;
    }
    GridFSBucket gridBucket = createBucket();
    for (Document doc : toDelete.find()) {
      gridBucket.delete(new ObjectId(doc.getString(FILEID_ATTR)));
    }
    toDelete.drop();
    return true;
  }

  public boolean deleteFile(String mongoId, String fileoid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoClient.getDatabase(mongoDBNameService.getName()).getCollection(mongoId);
    } catch (IllegalArgumentException e) {
      Log.errorf("Could not find container with mongoid: %s", mongoId);
      return false;
    }
    var doc = collection.findOneAndDelete(eq(ID_ATTR, new ObjectId(fileoid)));
    if (doc == null) {
      Log.warnf("Could not find and delete file with oid: %s", fileoid);
      return true;
    }
    var gridBucket = createBucket();
    gridBucket.delete(new ObjectId(doc.getString(FILEID_ATTR)));
    return true;
  }

  private static ShepardFile toShepardFile(Document doc) {
    var file = new ShepardFile(
      doc.getObjectId(ID_ATTR).toHexString(),
      doc.getDate(CREATEDAT_ATTR),
      doc.getString(FILENAME_ATTR),
      doc.getString(MD5_ATTR)
    );
    return file;
  }

  private static Document toDocument(ShepardFile file) {
    var doc = new Document()
      .append(CREATEDAT_ATTR, file.getCreatedAt())
      .append(FILENAME_ATTR, file.getFilename())
      .append(MD5_ATTR, file.getMd5());
    if (file.getOid() != null) doc.append(ID_ATTR, new ObjectId(file.getOid()));
    return doc;
  }

  public GridFSBucket createBucket() {
    return GridFSBuckets.create(mongoClient.getDatabase(mongoDBNameService.getName()));
  }
}
