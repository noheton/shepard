package de.dlr.shepard.data.file.services;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.UUIDHelper;
import de.dlr.shepard.data.file.entities.ShepardFile;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.xml.bind.DatatypeConverter;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.bson.Document;
import org.bson.types.ObjectId;

@RequestScoped
public class FileService {

  private static final int CHUNK_SIZE_BYTES = 1024 * 1024; // 1 MiB
  private static final String ID_ATTR = "_id";
  private static final String FILENAME_ATTR = "name";
  private static final String FILEID_ATTR = "FileMongoId";
  private static final String CREATEDAT_ATTR = "createdAt";
  private static final String MD5_ATTR = "md5";
  private static final String FILESIZE_ATTR = "fileSize";

  @Inject
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  @Inject
  UUIDHelper uuidHelper;

  @Inject
  DateHelper dateHelper;

  public String createFileContainer() {
    String oid = "FileContainer" + uuidHelper.getUUID().toString();
    mongoDatabase.createCollection(oid);
    return oid;
  }

  /**
   * Creates a new file in file container
   *
   * @param mongoId
   * @param fileName
   * @param inputStream
   * @return ShepardFile
   */
  public ShepardFile createFile(String mongoId, String fileName, InputStream inputStream) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDatabase.getCollection(mongoId);
    } catch (IllegalArgumentException e) {
      String errorMsg = "Could not find container with mongoId: %s".formatted(mongoId);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }

    MessageDigest md;
    try {
      md = MessageDigest.getInstance("MD5");
    } catch (NoSuchAlgorithmException e) {
      String errorMsg = "No Such Algorithm while uploading file";
      Log.error(errorMsg);
      throw new InternalServerErrorException(errorMsg);
    }

    DigestInputStream dis = new DigestInputStream(inputStream, md);
    var bucket = createBucket();
    ObjectId fileId = bucket.withChunkSizeBytes(CHUNK_SIZE_BYTES).uploadFromStream(fileName, dis);
    String fileMongoId = fileId.toHexString();
    // FB1a: GridFS knows the final payload size; persist it so byte
    // totals are available without re-reading every blob.
    var gridFsFile = bucket.find(eq(ID_ATTR, fileId)).first();
    Long fileSize = gridFsFile != null ? gridFsFile.getLength() : null;
    var file = new ShepardFile(dateHelper.getDate(), fileName, DatatypeConverter.printHexBinary(md.digest()));
    file.setFileSize(fileSize);
    var doc = toDocument(file).append(FILEID_ATTR, fileMongoId);
    collection.insertOne(doc);
    file.setOid(doc.getObjectId(ID_ATTR).toHexString());
    return file;
  }

  /**
   * Returns the payload as an InputStream for a given file.
   *
   * @param containerId
   * @param fileOid
   * @return NamedInputStream
   * @throws NotFoundException if container could not be found by MongoId or document could be found by Oid
   */
  public NamedInputStream getPayload(String containerId, String fileOid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDatabase.getCollection(containerId);
    } catch (IllegalArgumentException e) {
      String errorMsg = "Could not find container with mongoId: %s".formatted(containerId);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    var oid = new ObjectId(fileOid);
    var payloadDocument = collection.find(eq(ID_ATTR, oid)).first();
    if (payloadDocument == null) {
      String errorMsg = "Could not find document with oid: %s".formatted(fileOid);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    var fileId = new ObjectId(payloadDocument.getString(FILEID_ATTR));
    var filename = payloadDocument.getString(FILENAME_ATTR);
    var gridBucket = createBucket();
    var gridFsFile = gridBucket.find(eq(ID_ATTR, fileId)).first();
    var inputStream = gridBucket.openDownloadStream(fileId);

    return new NamedInputStream(fileOid, inputStream, filename, gridFsFile.getLength());
  }

  /**
   * Returns a ShepardFile for a given container and oid
   *
   * @param containerId
   * @param fileOid
   * @return ShepardFile
   * @throws NotFoundException if container could not be found by MongoId or file could be found by Oid
   */
  public ShepardFile getFile(String containerId, String fileOid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDatabase.getCollection(containerId);
    } catch (IllegalArgumentException e) {
      String errorMsg = "Could not find container with mongoId: %s".formatted(containerId);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    var doc = collection.find(eq(ID_ATTR, new ObjectId(fileOid))).first();
    if (doc == null) {
      String errorMsg = "Could not find file with oid: %s".formatted(fileOid);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    return toShepardFile(doc);
  }

  /**
   *
   * @param mongoId
   * @throws NotFoundException if deleting container fails
   */
  public void deleteFileContainer(String mongoId) {
    MongoCollection<Document> toDelete;
    try {
      toDelete = mongoDatabase.getCollection(mongoId);
    } catch (IllegalArgumentException e) {
      String errorMsg = "Could not delete container with mongoid: %s".formatted(mongoId);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    GridFSBucket gridBucket = createBucket();
    for (Document doc : toDelete.find()) {
      gridBucket.delete(new ObjectId(doc.getString(FILEID_ATTR)));
    }
    toDelete.drop();
  }

  public void deleteFile(String mongoId, String fileOid) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDatabase.getCollection(mongoId);
    } catch (IllegalArgumentException e) {
      String errorMsg = "Could not find container with mongoId: %s".formatted(mongoId);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    var doc = collection.findOneAndDelete(eq(ID_ATTR, new ObjectId(fileOid)));
    if (doc == null) {
      String errorMsg = "Could not find and delete file with oid: %s".formatted(fileOid);
      Log.error(errorMsg);
      throw new NotFoundException(errorMsg);
    }
    var gridBucket = createBucket();
    gridBucket.delete(new ObjectId(doc.getString(FILEID_ATTR)));
  }

  private static ShepardFile toShepardFile(Document doc) {
    var file = new ShepardFile(
      doc.getObjectId(ID_ATTR).toHexString(),
      doc.getDate(CREATEDAT_ATTR),
      doc.getString(FILENAME_ATTR),
      doc.getString(MD5_ATTR)
    );
    // FB1a: pre-FB1a rows have no fileSize attr; getLong returns null in that case.
    file.setFileSize(doc.getLong(FILESIZE_ATTR));
    return file;
  }

  private static Document toDocument(ShepardFile file) {
    var doc = new Document()
      .append(CREATEDAT_ATTR, file.getCreatedAt())
      .append(FILENAME_ATTR, file.getFilename())
      .append(MD5_ATTR, file.getMd5());
    if (file.getFileSize() != null) doc.append(FILESIZE_ATTR, file.getFileSize());
    if (file.getOid() != null) doc.append(ID_ATTR, new ObjectId(file.getOid()));
    return doc;
  }

  private GridFSBucket createBucket() {
    return GridFSBuckets.create(mongoDatabase);
  }
}
