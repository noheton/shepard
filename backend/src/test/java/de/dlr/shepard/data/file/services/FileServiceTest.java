package de.dlr.shepard.data.file.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
  private FileService fileService;

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

    var expected = new ShepardFile(fileoid.toHexString(), date, name, md5);
    var result = fileService.getFile(containerId, fileoid.toHexString());
    assertEquals(expected, result);
  }

  @Test
  public void getNonExistingContainerFileTest() {
    String nonExistingContainerId = "FileContainer123";
    String fileoid = "60b73212cfa45d2d5baa795d";

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(nonExistingContainerId);

    var result = fileService.getFile(nonExistingContainerId, fileoid);
    assertNull(result);
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
    var result = fileService.getFile(containerId, nonExistingFileoid);
    assertNull(result);
  }

  @Test
  public void createFileTest() throws NoSuchAlgorithmException, IOException {
    ObjectId oid = new ObjectId();
    ObjectId moid = new ObjectId();
    String fileName = "fileName";
    String md5 = DatatypeConverter.printHexBinary(MessageDigest.getInstance("MD5").digest());
    InputStream inputStream = mock(InputStream.class);
    String mongoid = "mongoid";
    Date date = new Date();

    when(dateHelper.getDate()).thenReturn(date);
    when(mongoDatabase.getCollection(mongoid)).thenReturn(collection);

    when(gridBucket.withChunkSizeBytes(1024 * 1024)).thenReturn(gridBucket);
    when(gridBucket.uploadFromStream(eq(fileName), any(DigestInputStream.class))).thenReturn(oid);
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
      .append("md5", md5);
    var expected = new ShepardFile(moid.toHexString(), date, fileName, md5);
    var result = fileService.createFile(mongoid, fileName, inputStream);
    assertEquals(expected, result);
    verify(collection).insertOne(newFile);
  }

  @Test
  public void createNonExistingMongoidFileTest() {
    String fileName = "fileName";
    InputStream inputStream = mock(InputStream.class);
    String nonExistingMongoid = "mongoid";

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(nonExistingMongoid);

    var result = fileService.createFile(nonExistingMongoid, fileName, inputStream);
    assertNull(result);
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

    var result = fileService.deleteFileContainer(existingMongoOid);
    assertTrue(result);
    verify(gridBucket).delete(oid);
  }

  @Test
  public void deleteNonExistingFileContainerTest() {
    String nonExistingMongoOid = "60b73212cfa45d2d5baa795d";

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(nonExistingMongoOid);

    var result = fileService.deleteFileContainer(nonExistingMongoOid);
    assertFalse(result);
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

    var result = fileService.getPayload(nonExistingContainerId, fileoid);
    assertNull(result);
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

    var result = fileService.getPayload(containerId, fileoid);
    assertNull(result);
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

    var result = fileService.deleteFile(mongoOid, oid.toHexString());
    assertTrue(result);
    verify(gridBucket).delete(new ObjectId(fileOid));
  }

  @Test
  public void deletePayloadTest_collectionIsNull() {
    String mongoOid = "60b73212cfa45d2d5baa795b";
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(mongoOid);

    var result = fileService.deleteFile(mongoOid, oid.toHexString());
    assertFalse(result);
  }

  @Test
  public void deletePayloadTest_documentIsNull() {
    String mongoOid = "60b73212cfa45d2d5baa795b";
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    when(mongoDatabase.getCollection(mongoOid)).thenReturn(collection);
    when(collection.findOneAndDelete(Filters.eq("_id", oid))).thenReturn(null);

    var result = fileService.deleteFile(mongoOid, oid.toHexString());
    assertTrue(result);
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
