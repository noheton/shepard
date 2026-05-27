package de.dlr.shepard.data.file.services;

import static com.mongodb.client.model.Filters.eq;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import de.dlr.shepard.common.exceptions.InvalidRequestException;
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
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@RequestScoped
public class FileService {

  private static final int CHUNK_SIZE_BYTES = 1024 * 1024; // 1 MiB

  /**
   * MONGO-AUDIT-2026-05-24-012 — configurable upper bound for file uploads
   * to MongoDB-backed storage (GridFS). Files whose declared size exceeds
   * this limit are rejected with a 400 {@link InvalidRequestException} before
   * any bytes are written to GridFS, preventing a single large upload from
   * saturating the MongoDB substrate.
   *
   * <p>Set to {@code 0} to disable the check (unrestricted uploads).
   *
   * <p>Note on the two-layer model: this cap prevents oversized files from
   * reaching GridFS. It does NOT prevent the bytes from touching the Quarkus
   * host's local temp-file filesystem, because the multipart layer writes the
   * upload to a temp file before the handler runs. To reject oversized
   * requests before they hit disk, additionally set
   * {@code quarkus.http.limits.max-body-size} in
   * {@code application.properties}.
   */
  @ConfigProperty(name = "shepard.mongo.file.max-bytes", defaultValue = "2147483648")
  long mongoFileMaxBytes;

  private static final String ID_ATTR = "_id";
  private static final String FILENAME_ATTR = "name";
  // MONGO-AUDIT-007: Stored as a plain hex String, not as ObjectId — GridFS _id is ObjectId.
  // Cast to new ObjectId(fileMongoId) before using in $lookup aggregations (see MongoSchemaInitializer).
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
   * MONGO-AUDIT-2026-05-24-012 — reject an upload that exceeds
   * {@code shepard.mongo.file.max-bytes} before any GridFS write occurs.
   *
   * <p>Pass {@code declaredSize <= 0} when size is unknown (the check is
   * then skipped, preserving backward compatibility for callers that only
   * have a raw {@link InputStream}).
   *
   * @param declaredSize caller-declared file size in bytes; {@code <= 0} skips the check.
   * @throws InvalidRequestException (HTTP 400) when the declared size exceeds the cap.
   */
  void enforceFileSizeCap(long declaredSize) {
    if (mongoFileMaxBytes > 0 && declaredSize > mongoFileMaxBytes) {
      throw new InvalidRequestException(
        "File exceeds the maximum allowed size of " + mongoFileMaxBytes + " bytes"
      );
    }
  }

  /**
   * PV1a — result record returned by {@link #createFileWithSha256}. Carries
   * the persisted {@link ShepardFile} alongside the SHA-256 hex digest of the
   * uploaded bytes.
   *
   * @param file   the persisted ShepardFile (non-null).
   * @param sha256 SHA-256 upper-case hex digest of the uploaded bytes (non-null).
   */
  public record FileCreateResult(ShepardFile file, String sha256) {}

  /**
   * Creates a new file in file container and returns the SHA-256 digest of
   * the payload alongside the persisted {@link ShepardFile}.
   *
   * <p>Two {@link DigestInputStream}s are chained: the inner one computes
   * SHA-256, the outer one computes MD5 (stored in the bookkeeping document
   * for backward-compat). Bytes flow through both digests in a single pass
   * as GridFS reads the stream.
   *
   * <p>MONGO-AUDIT-2026-05-24-012: when {@code declaredSize > 0} and exceeds
   * {@code shepard.mongo.file.max-bytes}, an {@link InvalidRequestException}
   * is thrown before any GridFS write occurs.
   *
   * @param mongoId      the MongoDB collection ID of the file container.
   * @param fileName     the file name to store.
   * @param inputStream  the payload bytes.
   * @param declaredSize caller-declared file size in bytes; {@code <= 0} skips the size cap check.
   * @return a {@link FileCreateResult} with the saved file and its SHA-256.
   * @throws InvalidRequestException                    if the declared size exceeds the cap.
   * @throws jakarta.ws.rs.NotFoundException            if the container is not found.
   * @throws jakarta.ws.rs.InternalServerErrorException if a digest algorithm is unavailable.
   */
  public FileCreateResult createFileWithSha256(String mongoId, String fileName, InputStream inputStream, long declaredSize) {
    enforceFileSizeCap(declaredSize);
    return createFileWithSha256Internal(mongoId, fileName, inputStream);
  }

  /**
   * Backward-compatible overload that skips the size cap check.
   * Prefer {@link #createFileWithSha256(String, String, InputStream, long)} when
   * the caller knows the file size.
   *
   * @param mongoId     the MongoDB collection ID of the file container.
   * @param fileName    the file name to store.
   * @param inputStream the payload bytes.
   * @return a {@link FileCreateResult} with the saved file and its SHA-256.
   * @throws jakarta.ws.rs.NotFoundException            if the container is not found.
   * @throws jakarta.ws.rs.InternalServerErrorException if a digest algorithm is unavailable.
   */
  public FileCreateResult createFileWithSha256(String mongoId, String fileName, InputStream inputStream) {
    return createFileWithSha256Internal(mongoId, fileName, inputStream);
  }

  private FileCreateResult createFileWithSha256Internal(String mongoId, String fileName, InputStream inputStream) {
    MongoCollection<Document> collection;
    try {
      collection = mongoDatabase.getCollection(mongoId);
    } catch (IllegalArgumentException e) {
      String errorMsg = "Could not find container with mongoId: %s".formatted(mongoId);
      Log.error(errorMsg);
      throw new jakarta.ws.rs.NotFoundException(errorMsg);
    }

    MessageDigest md5Md;
    MessageDigest sha256Md;
    try {
      md5Md = MessageDigest.getInstance("MD5");
      sha256Md = MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException e) {
      String errorMsg = "No Such Algorithm while uploading file";
      Log.error(errorMsg);
      throw new InternalServerErrorException(errorMsg);
    }

    // Chain: bytes → sha256DigestInputStream → md5DigestInputStream → GridFS bucket
    DigestInputStream sha256Dis = new DigestInputStream(inputStream, sha256Md);
    DigestInputStream md5Dis = new DigestInputStream(sha256Dis, md5Md);

    var bucket = createBucket();
    ObjectId fileId = bucket.withChunkSizeBytes(CHUNK_SIZE_BYTES).uploadFromStream(fileName, md5Dis);
    String fileMongoId = fileId.toHexString();
    var gridFsFile = bucket.find(eq(ID_ATTR, fileId)).first();
    Long fileSize = gridFsFile != null ? gridFsFile.getLength() : null;
    var file = new ShepardFile(dateHelper.getDate(), fileName, DatatypeConverter.printHexBinary(md5Md.digest()));
    file.setFileSize(fileSize);
    var doc = toDocument(file).append(FILEID_ATTR, fileMongoId);
    collection.insertOne(doc);
    file.setOid(doc.getObjectId(ID_ATTR).toHexString());

    String sha256Hex = DatatypeConverter.printHexBinary(sha256Md.digest());
    return new FileCreateResult(file, sha256Hex);
  }

  /**
   * Creates a new file in file container with an explicit size cap check.
   *
   * <p>MONGO-AUDIT-2026-05-24-012: when {@code declaredSize > 0} and exceeds
   * {@code shepard.mongo.file.max-bytes}, an {@link InvalidRequestException}
   * is thrown before any GridFS write occurs.
   *
   * @param mongoId      the MongoDB collection ID of the file container.
   * @param fileName     the file name to store.
   * @param inputStream  the payload bytes.
   * @param declaredSize caller-declared file size in bytes; {@code <= 0} skips the size cap check.
   * @return ShepardFile
   * @throws InvalidRequestException if the declared size exceeds the cap.
   */
  public ShepardFile createFile(String mongoId, String fileName, InputStream inputStream, long declaredSize) {
    enforceFileSizeCap(declaredSize);
    return createFileInternal(mongoId, fileName, inputStream);
  }

  /**
   * Creates a new file in file container.
   * Prefer {@link #createFile(String, String, InputStream, long)} when the caller
   * knows the file size so the upload size cap can be enforced pre-stream.
   *
   * @param mongoId
   * @param fileName
   * @param inputStream
   * @return ShepardFile
   */
  public ShepardFile createFile(String mongoId, String fileName, InputStream inputStream) {
    return createFileInternal(mongoId, fileName, inputStream);
  }

  private ShepardFile createFileInternal(String mongoId, String fileName, InputStream inputStream) {
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
   * Deletes all files from the GridFS bucket for this container, then drops the
   * container collection.
   *
   * <p>MONGO-AUDIT-2026-05-24-006: The previous implementation iterated the
   * cursor and issued one {@code GridFSBucket.delete} per document, interleaving
   * cursor round-trips with delete operations. The replacement materialises the
   * full cursor into a list first (one pass), then iterates the list to issue
   * deletes. This separates cursor reads from write operations, reducing
   * cursor-hold time and avoiding N interleaved round-trips. Driver 5.1.x does
   * not expose a {@code delete(List&lt;ObjectId&gt;)} overload; a future
   * {@code fs.files.deleteMany($in)} + {@code fs.chunks.deleteMany($in)} bypass
   * would reduce this to O(2) round-trips but is tracked separately.
   *
   * @param mongoId the MongoDB collection ID of the file container.
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
    // MONGO-AUDIT-2026-05-24-006: materialise all GridFS ObjectIds in one cursor
    // pass, then delete from the list — avoids N interleaved cursor round-trips.
    List<ObjectId> fileIds = toDelete.find()
        .map(doc -> new ObjectId(doc.getString(FILEID_ATTR)))
        .into(new ArrayList<>());
    GridFSBucket gridBucket = createBucket();
    fileIds.forEach(gridBucket::delete);
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
