package de.dlr.shepard.data.file.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.GridFSFindIterable;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.mongodb.client.model.Filters;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.common.util.UUIDHelper;
import de.dlr.shepard.data.file.entities.ShepardFile;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.NotFoundException;
import jakarta.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@QuarkusComponentTest
public class FileServiceTest {

  @InjectMock
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  @InjectMock
  private DateHelper dateHelper;

  @InjectMock
  private UUIDHelper uuidhelper;

  @Inject
  FileService fileService;

  @InjectMock
  private MongoCollection<Document> collection;

  @InjectMock
  private GridFSBucket gridBucket;

  @BeforeEach
  public void setupMongoDBClient() {
    when(GridFSBuckets.create(mongoDatabase)).thenReturn(gridBucket);
  }

  @BeforeAll
  public static void setupGridFSBucket() {
    mockStatic(GridFSBuckets.class);
  }

  @Test
  public void createFileContainerTest() {
    var uuid = UUID.randomUUID();
    when(uuidhelper.getUUID()).thenReturn(uuid);
    var result = fileService.createFileContainer();
    var expectedUUID = "FileContainer" + uuid.toString();
    assertEquals(expectedUUID, result);
    verify(mongoDatabase).createCollection(expectedUUID);
  }

  @Test
  public void getExistingFileTest() {
    String containerId = "FileContainerdc824045-9137-4051-8981-c528e6b91fbe";
    ObjectId fileoid = new ObjectId("60b73212cfa45d2d5baa795d");
    String name = "name";
    String md5 = "md5";
    Long fileSize = 4096L;
    Date date = new Date();
    Document file = mock(Document.class);
    @SuppressWarnings("unchecked")
    FindIterable<Document> collectionReturn = mock(FindIterable.class);

    when(mongoDatabase.getCollection(containerId)).thenReturn(collection);
    when(collection.find(Filters.eq("_id", fileoid))).thenReturn(collectionReturn);
    when(collectionReturn.first()).thenReturn(file);
    when(file.getObjectId("_id")).thenReturn(fileoid);
    when(file.getString("name")).thenReturn(name);
    when(file.getDate("createdAt")).thenReturn(date);
    when(file.getString("md5")).thenReturn(md5);
    when(file.getLong("fileSize")).thenReturn(fileSize);

    var expected = new ShepardFile(fileoid.toHexString(), date, name, md5);
    expected.setFileSize(fileSize);
    var result = fileService.getFile(containerId, fileoid.toHexString());
    assertEquals(expected, result);
    assertEquals(fileSize, result.getFileSize());
  }

  @Test
  public void getExistingFileTest_legacyFileSizeNull() {
    // FB1a: pre-FB1a rows have no fileSize attr; round-trip stays null.
    String containerId = "FileContainerdc824045-9137-4051-8981-c528e6b91fbe";
    ObjectId fileoid = new ObjectId("60b73212cfa45d2d5baa795d");
    Date date = new Date();
    Document file = mock(Document.class);
    @SuppressWarnings("unchecked")
    FindIterable<Document> collectionReturn = mock(FindIterable.class);

    when(mongoDatabase.getCollection(containerId)).thenReturn(collection);
    when(collection.find(Filters.eq("_id", fileoid))).thenReturn(collectionReturn);
    when(collectionReturn.first()).thenReturn(file);
    when(file.getObjectId("_id")).thenReturn(fileoid);
    when(file.getString("name")).thenReturn("name");
    when(file.getDate("createdAt")).thenReturn(date);
    when(file.getString("md5")).thenReturn("md5");
    when(file.getLong("fileSize")).thenReturn(null);

    var result = fileService.getFile(containerId, fileoid.toHexString());
    org.junit.jupiter.api.Assertions.assertNull(result.getFileSize());
  }

  @Test
  public void getNonExistingContainerFileTest() {
    String nonExistingContainerId = "FileContainer123";
    String fileoid = "60b73212cfa45d2d5baa795d";

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(nonExistingContainerId);

    assertThrows(NotFoundException.class, () -> fileService.getFile(nonExistingContainerId, fileoid));
  }

  @Test
  public void getNonExistingFileTest() {
    String containerId = "FileContainerdc824045-9137-4051-8981-c528e6b91fbe";
    String nonExistingFileoid = "60b73212cfa45d2d5baa795b";
    @SuppressWarnings("unchecked")
    FindIterable<Document> collectionReturn = mock(FindIterable.class);

    when(mongoDatabase.getCollection(containerId)).thenReturn(collection);
    when(collection.find(Filters.eq("_id", new ObjectId(nonExistingFileoid)))).thenReturn(collectionReturn);
    when(collectionReturn.first()).thenReturn(null);
    assertThrows(NotFoundException.class, () -> fileService.getFile(containerId, nonExistingFileoid));
  }

  @Test
  public void createFileTest() throws NoSuchAlgorithmException, IOException {
    ObjectId oid = new ObjectId();
    ObjectId moid = new ObjectId();
    String fileName = "fileName";
    String md5 = DatatypeConverter.printHexBinary(MessageDigest.getInstance("MD5").digest());
    Long fileSize = 8192L;
    InputStream inputStream = mock(InputStream.class);
    String mongoid = "mongoid";
    Date date = new Date();

    when(dateHelper.getDate()).thenReturn(date);
    when(mongoDatabase.getCollection(mongoid)).thenReturn(collection);

    when(gridBucket.withChunkSizeBytes(1024 * 1024)).thenReturn(gridBucket);
    when(gridBucket.uploadFromStream(eq(fileName), any(DigestInputStream.class))).thenReturn(oid);
    // FB1a: createFile looks up the just-uploaded GridFS file to record its length.
    GridFSFindIterable filesCollectionReturn = mock(GridFSFindIterable.class);
    GridFSFile gridFsFile = mock(GridFSFile.class);
    when(gridBucket.find(Filters.eq("_id", oid))).thenReturn(filesCollectionReturn);
    when(filesCollectionReturn.first()).thenReturn(gridFsFile);
    when(gridFsFile.getLength()).thenReturn(fileSize);
    doAnswer(
      new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          ((Document) args[0]).append("_id", moid);
          return null;
        }
      }
    )
      .when(collection)
      .insertOne(any(Document.class));

    var newFile = new Document("_id", moid)
      .append("name", fileName)
      .append("FileMongoId", oid.toHexString())
      .append("createdAt", date)
      .append("md5", md5)
      .append("fileSize", fileSize);
    var expected = new ShepardFile(moid.toHexString(), date, fileName, md5);
    expected.setFileSize(fileSize);
    var result = fileService.createFile(mongoid, fileName, inputStream);
    assertEquals(expected, result);
    assertEquals(fileSize, result.getFileSize());
    verify(collection).insertOne(newFile);
  }

  @Test
  public void createFileTest_missingGridFsFileLeavesFileSizeNull() throws NoSuchAlgorithmException {
    // FB1a defensive path: if the bucket lookup turns up nothing
    // (shouldn't happen in practice, but Mongo's find().first() is nullable),
    // we should still persist the ShepardFile rather than NPE.
    ObjectId oid = new ObjectId();
    ObjectId moid = new ObjectId();
    String fileName = "fileName";
    InputStream inputStream = mock(InputStream.class);
    String mongoid = "mongoid";

    when(dateHelper.getDate()).thenReturn(new Date());
    when(mongoDatabase.getCollection(mongoid)).thenReturn(collection);
    when(gridBucket.withChunkSizeBytes(1024 * 1024)).thenReturn(gridBucket);
    when(gridBucket.uploadFromStream(eq(fileName), any(DigestInputStream.class))).thenReturn(oid);
    GridFSFindIterable filesCollectionReturn = mock(GridFSFindIterable.class);
    when(gridBucket.find(Filters.eq("_id", oid))).thenReturn(filesCollectionReturn);
    when(filesCollectionReturn.first()).thenReturn(null);
    doAnswer(
      new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          ((Document) invocation.getArguments()[0]).append("_id", moid);
          return null;
        }
      }
    )
      .when(collection)
      .insertOne(any(Document.class));

    var result = fileService.createFile(mongoid, fileName, inputStream);
    org.junit.jupiter.api.Assertions.assertNull(result.getFileSize());
  }

  @Test
  public void createFileWithSha256_returnsSha256OfEmptyStream() throws NoSuchAlgorithmException {
    // SHA-256 of the empty byte sequence is the well-known constant:
    // E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855
    final String sha256OfEmpty = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
    ObjectId oid = new ObjectId();
    ObjectId moid = new ObjectId();
    String fileName = "empty.bin";
    InputStream emptyStream = InputStream.nullInputStream();
    String mongoid = "mongoid";

    when(dateHelper.getDate()).thenReturn(new Date());
    when(mongoDatabase.getCollection(mongoid)).thenReturn(collection);
    when(gridBucket.withChunkSizeBytes(1024 * 1024)).thenReturn(gridBucket);
    when(gridBucket.uploadFromStream(eq(fileName), any(DigestInputStream.class))).thenReturn(oid);
    GridFSFindIterable filesCollectionReturn = mock(GridFSFindIterable.class);
    when(gridBucket.find(com.mongodb.client.model.Filters.eq("_id", oid))).thenReturn(filesCollectionReturn);
    when(filesCollectionReturn.first()).thenReturn(null); // size not critical for this test
    doAnswer(
      new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          ((Document) invocation.getArguments()[0]).append("_id", moid);
          return null;
        }
      }
    )
      .when(collection)
      .insertOne(any(Document.class));

    FileService.FileCreateResult result = fileService.createFileWithSha256(mongoid, fileName, emptyStream);
    assertEquals(sha256OfEmpty, result.sha256());
  }

  @Test
  public void createFileWithSha256_returnsSha256AlongsideShepardFile() throws NoSuchAlgorithmException {
    ObjectId oid = new ObjectId();
    ObjectId moid = new ObjectId();
    String fileName = "data.bin";
    InputStream emptyStream = InputStream.nullInputStream();
    String mongoid = "mongoid";
    Long fileSize = 0L;

    when(dateHelper.getDate()).thenReturn(new Date());
    when(mongoDatabase.getCollection(mongoid)).thenReturn(collection);
    when(gridBucket.withChunkSizeBytes(1024 * 1024)).thenReturn(gridBucket);
    when(gridBucket.uploadFromStream(eq(fileName), any(DigestInputStream.class))).thenReturn(oid);
    GridFSFindIterable filesCollectionReturn = mock(GridFSFindIterable.class);
    GridFSFile gridFsFile = mock(GridFSFile.class);
    when(gridBucket.find(com.mongodb.client.model.Filters.eq("_id", oid))).thenReturn(filesCollectionReturn);
    when(filesCollectionReturn.first()).thenReturn(gridFsFile);
    when(gridFsFile.getLength()).thenReturn(fileSize);
    doAnswer(
      new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          ((Document) invocation.getArguments()[0]).append("_id", moid);
          return null;
        }
      }
    )
      .when(collection)
      .insertOne(any(Document.class));

    FileService.FileCreateResult result = fileService.createFileWithSha256(mongoid, fileName, emptyStream);
    org.junit.jupiter.api.Assertions.assertNotNull(result.file());
    assertEquals(fileName, result.file().getFilename());
    assertEquals(fileSize, result.file().getFileSize());
    org.junit.jupiter.api.Assertions.assertNotNull(result.sha256());
    assertEquals(64, result.sha256().length()); // SHA-256 hex is always 64 chars
  }

  @Test
  public void createNonExistingMongoidFileTest() {
    String fileName = "fileName";
    InputStream inputStream = mock(InputStream.class);
    String nonExistingMongoid = "mongoid";

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(nonExistingMongoid);

    assertThrows(NotFoundException.class, () -> fileService.createFile(nonExistingMongoid, fileName, inputStream));
  }

  @Test
  public void deleteExistingFileContainerTest() {
    String existingMongoOid = "60b73212cfa45d2d5baa795d";
    ObjectId oid = new ObjectId();
    @SuppressWarnings("unchecked")
    FindIterable<Document> collectionReturn = mock(FindIterable.class);
    Document doc = mock(Document.class);
    mockIterable(collectionReturn, doc);

    when(mongoDatabase.getCollection(existingMongoOid)).thenReturn(collection);
    when(collection.find()).thenReturn(collectionReturn);
    when(doc.getString("FileMongoId")).thenReturn(oid.toHexString());

    assertDoesNotThrow(() -> fileService.deleteFileContainer(existingMongoOid));
    verify(gridBucket).delete(oid);
  }

  @Test
  public void deleteNonExistingFileContainerTest() {
    String nonExistingMongoOid = "60b73212cfa45d2d5baa795d";

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(nonExistingMongoOid);

    assertThrows(NotFoundException.class, () -> fileService.deleteFileContainer(nonExistingMongoOid));
  }

  @Test
  public void getPayloadTest() {
    String containerId = "4";
    String fileoid = "60b73212cfa45d2d5baa795d";
    String fileName = "FileName";
    Long fileSize = 123L;
    @SuppressWarnings("unchecked")
    FindIterable<Document> collectionReturn = mock(FindIterable.class);
    Document doc = mock(Document.class);
    ObjectId oid = new ObjectId();
    GridFSDownloadStream stream = mock(GridFSDownloadStream.class);
    GridFSFindIterable filesCollectionReturn = mock(GridFSFindIterable.class);
    GridFSFile gridFsFile = mock(GridFSFile.class);

    when(mongoDatabase.getCollection(containerId)).thenReturn(collection);
    when(collection.find(Filters.eq("_id", new ObjectId(fileoid)))).thenReturn(collectionReturn);
    when(collectionReturn.first()).thenReturn(doc);
    when(doc.getString("FileMongoId")).thenReturn(oid.toHexString());
    when(doc.getString("name")).thenReturn(fileName);
    when(gridBucket.openDownloadStream(oid)).thenReturn(stream);
    when(gridBucket.find(Filters.eq("_id", oid))).thenReturn(filesCollectionReturn);
    when(filesCollectionReturn.first()).thenReturn(gridFsFile);
    when(gridFsFile.getLength()).thenReturn(fileSize);

    var expected = new NamedInputStream(fileoid, stream, fileName, fileSize);
    var result = fileService.getPayload(containerId, fileoid);
    assertEquals(expected, result);
  }

  @Test
  public void getPayloadNonExistingContainerIdTest() {
    String nonExistingContainerId = "4";
    String fileoid = "60b73212cfa45d2d5baa795d";

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(nonExistingContainerId);

    assertThrows(NotFoundException.class, () -> fileService.getPayload(nonExistingContainerId, fileoid));
  }

  @Test
  public void getPayloadNonExistingFileOidTest() {
    String containerId = "4";
    String fileoid = "60b73212cfa45d2d5baa795d";

    when(mongoDatabase.getCollection(containerId)).thenReturn(collection);
    @SuppressWarnings("unchecked")
    FindIterable<Document> emptyCollectionReturn = mock(FindIterable.class);
    when(collection.find(Filters.eq("_id", new ObjectId(fileoid)))).thenReturn(emptyCollectionReturn);
    when(emptyCollectionReturn.first()).thenReturn(null);

    assertThrows(NotFoundException.class, () -> fileService.getPayload(containerId, fileoid));
  }

  @Test
  public void deletePayloadTest() {
    String fileOid = "60b73212cfa45d2d5baa795a";
    String mongoOid = "60b73212cfa45d2d5baa795b";
    Document doc = mock(Document.class);
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    when(mongoDatabase.getCollection(mongoOid)).thenReturn(collection);
    when(collection.findOneAndDelete(Filters.eq("_id", oid))).thenReturn(doc);
    when(doc.getString("FileMongoId")).thenReturn(fileOid);

    assertDoesNotThrow(() -> fileService.deleteFile(mongoOid, oid.toHexString()));
    verify(gridBucket).delete(new ObjectId(fileOid));
  }

  @Test
  public void deletePayloadTest_collectionIsNull() {
    String mongoOid = "60b73212cfa45d2d5baa795b";
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(mongoOid);

    assertThrows(NotFoundException.class, () -> fileService.deleteFile(mongoOid, oid.toHexString()));
  }

  @Test
  public void deletePayloadTest_documentIsNull() {
    String mongoOid = "60b73212cfa45d2d5baa795b";
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    when(mongoDatabase.getCollection(mongoOid)).thenReturn(collection);
    when(collection.findOneAndDelete(Filters.eq("_id", oid))).thenReturn(null);

    assertThrows(NotFoundException.class, () -> fileService.deleteFile(mongoOid, oid.toHexString()));
  }

  /**
   * From https://www.batey.info/mocking-iterable-objects-generically.html
   */
  @SuppressWarnings("unchecked")
  private static void mockIterable(FindIterable<Document> iterable, Document... values) {
    MongoCursor<Document> mockIterator = mock(MongoCursor.class);
    when(iterable.iterator()).thenReturn(mockIterator);

    if (values.length == 0) {
      when(mockIterator.hasNext()).thenReturn(false);
    } else if (values.length == 1) {
      when(mockIterator.hasNext()).thenReturn(true, false);
      when(mockIterator.next()).thenReturn(values[0]);
    } else {
      // build boolean array for hasNext()
      Boolean[] hasNextResponses = new Boolean[values.length];
      for (int i = 0; i < hasNextResponses.length - 1; i++) {
        hasNextResponses[i] = true;
      }
      hasNextResponses[hasNextResponses.length - 1] = false;
      when(mockIterator.hasNext()).thenReturn(true, hasNextResponses);
      Document[] valuesMinusTheFirst = Arrays.copyOfRange(values, 1, values.length);
      when(mockIterator.next()).thenReturn(values[0], valuesMinusTheFirst);
    }
  }
}
