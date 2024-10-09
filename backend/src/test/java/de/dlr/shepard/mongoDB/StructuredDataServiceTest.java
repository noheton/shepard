package de.dlr.shepard.mongoDB;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.util.DateHelper;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

@QuarkusComponentTest
public class StructuredDataServiceTest extends BaseTestCase {

  @InjectMock
  DateHelper dateHelper;

  @InjectMock
  FindIterable<Document> result;

  @InjectMock
  MongoCollection<Document> collection;

  @Inject
  StructuredDataService service;

  @Captor
  private ArgumentCaptor<String> collectionName;

  @InjectMock
  MongoClient mongoClient;

  @InjectMock
  MongoDBDatabaseNameService mongoDBNameService;

  @InjectMock
  private MongoDatabase database;

  @BeforeEach
  public void setupMongoDBClient() {
    when(mongoDBNameService.getName()).thenReturn("database");
    when(mongoClient.getDatabase(anyString())).thenReturn(database);
  }

  @Test
  public void createStructuredDataContainerTest() {
    var actual = service.createStructuredDataContainer();
    verify(database).createCollection(collectionName.capture());
    assertEquals(collectionName.getValue(), actual);
  }

  @Test
  public void createStructuredDataTest() {
    String payload = "{\"a\":\"b\", \"c\":\"d\"}";
    Date date = new Date();
    ObjectId oid = new ObjectId();
    StructuredData data = new StructuredData("name", date);
    Document toInsert = Document.parse(payload);
    toInsert.append("_meta", data);

    when(dateHelper.getDate()).thenReturn(date);
    when(database.getCollection("collection")).thenReturn(collection);
    doAnswer(
      new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          ((Document) args[0]).append("_id", oid);
          return null; // void method, so return null
        }
      }
    )
      .when(collection)
      .insertOne(toInsert);

    var expectedData = new StructuredData();
    expectedData.setName("name");
    var actual = service.createStructuredData("collection", new StructuredDataPayload(expectedData, payload));
    assertEquals(new StructuredData(oid.toHexString(), date, "name"), actual);
  }

  @Test
  public void createStructuredDataTest_forbiddenKeys() {
    String payload = "{\"_a\":\"b\", \"c\":\"d\"}";
    String payloadCleaned = "{\"c\":\"d\"}";
    Date date = new Date();
    ObjectId oid = new ObjectId();
    StructuredData data = new StructuredData("name", date);
    Document toInsert = Document.parse(payloadCleaned);
    toInsert.append("_meta", data);

    when(dateHelper.getDate()).thenReturn(date);
    when(database.getCollection("collection")).thenReturn(collection);
    doAnswer(
      new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          ((Document) args[0]).append("_id", oid);
          return null; // void method, so return null
        }
      }
    )
      .when(collection)
      .insertOne(toInsert);

    var expectedData = new StructuredData();
    expectedData.setName("name");
    var actual = service.createStructuredData("collection", new StructuredDataPayload(expectedData, payload));
    assertEquals(new StructuredData(oid.toHexString(), date, "name"), actual);
  }

  @Test
  public void createStructuredDataTest_noStructuredData() {
    String payload = "{\"a\":\"b\", \"c\":\"d\"}";
    Date date = new Date();
    ObjectId oid = new ObjectId();
    StructuredData data = new StructuredData(null, date);
    Document toInsert = Document.parse(payload);
    toInsert.append("_meta", data);

    when(dateHelper.getDate()).thenReturn(date);
    when(database.getCollection("collection")).thenReturn(collection);
    doAnswer(
      new Answer<Void>() {
        @Override
        public Void answer(InvocationOnMock invocation) throws Throwable {
          Object[] args = invocation.getArguments();
          ((Document) args[0]).append("_id", oid);
          return null; // void method, so return null
        }
      }
    )
      .when(collection)
      .insertOne(toInsert);

    var actual = service.createStructuredData("collection", new StructuredDataPayload(null, payload));
    assertEquals(new StructuredData(oid.toHexString(), date, null), actual);
  }

  @Test
  public void createStructuredDataTest_mongoError() {
    String payload = "{\"a\":\"b\", \"c\":\"d\"}";

    when(database.getCollection("collection")).thenReturn(collection);
    doThrow(new MongoException("message")).when(collection).insertOne(any(Document.class));

    var expectedData = new StructuredData();
    expectedData.setName("name");
    var actual = service.createStructuredData("collection", new StructuredDataPayload(expectedData, payload));
    assertNull(actual);
  }

  @Test
  public void createStructuredDataTest_collectionIsNull() {
    String payload = "{\"a\":\"b\", \"c\":\"d\"}";
    String mongoId = "collection";

    var expectedData = new StructuredData();
    expectedData.setName("name");

    doThrow(new IllegalArgumentException()).when(database).getCollection(mongoId);

    var actual = service.createStructuredData(mongoId, new StructuredDataPayload(expectedData, payload));
    assertNull(actual);
    verify(collection, never()).insertOne(any(Document.class));
  }

  @Test
  public void createStructuredDataTest_invalidJson() {
    String payload = "invalid";

    when(database.getCollection("collection")).thenReturn(collection);

    var expectedData = new StructuredData();
    expectedData.setName("name");
    var newStrData = new StructuredDataPayload(expectedData, payload);
    assertThrows(InvalidBodyException.class, () -> service.createStructuredData("collection", newStrData));
    verify(collection, never()).insertOne(any(Document.class));
  }

  @Test
  public void deleteStructuredDataTest() {
    when(database.getCollection("collection")).thenReturn(collection);

    var actual = service.deleteStructuredDataContainer("collection");
    assertTrue(actual);
    verify(collection).drop();
  }

  @Test
  public void deleteStructuredDataTest_collectionIsNull() {
    doThrow(new IllegalArgumentException()).when(database).getCollection("collection");

    var actual = service.deleteStructuredDataContainer("collection");
    assertFalse(actual);
  }

  @Test
  public void getPayloadTest() {
    Date date = new Date();
    StructuredData data = new StructuredData(null, date, "name");
    ObjectId oid = new ObjectId();
    Document doc = new Document("_id", oid);
    doc.append("a", "b");
    Document sd = new Document();
    sd.append("name", data.getName());
    sd.append("createdAt", data.getCreatedAt());
    doc.append("_meta", sd);

    when(database.getCollection("collection")).thenReturn(collection);
    when(collection.find(eq("_id", oid))).thenReturn(result);
    when(result.first()).thenReturn(doc);

    var actual = service.getPayload("collection", oid.toHexString());
    assertEquals(new StructuredDataPayload(new StructuredData(oid.toHexString(), date, "name"), doc.toJson()), actual);
  }

  @Test
  public void getPayloadTest_noMeta() {
    ObjectId oid = new ObjectId();
    Document doc = new Document("_id", oid);
    doc.append("a", "b");

    when(database.getCollection("collection")).thenReturn(collection);
    when(collection.find(eq("_id", oid))).thenReturn(result);
    when(result.first()).thenReturn(doc);

    var actual = service.getPayload("collection", oid.toHexString());
    assertEquals(new StructuredDataPayload(new StructuredData(oid.toHexString(), null, null), doc.toJson()), actual);
  }

  @Test
  public void getPayloadTest_collectionIsNull() {
    ObjectId oid = new ObjectId();

    doThrow(new IllegalArgumentException()).when(database).getCollection("collection");

    var actual = service.getPayload("collection", oid.toHexString());
    assertNull(actual);
  }

  @Test
  public void getPayloadTest_payloadIsNull() {
    ObjectId oid = new ObjectId();

    when(database.getCollection("collection")).thenReturn(collection);
    when(collection.find(eq("_id", oid))).thenReturn(result);
    when(result.first()).thenReturn(null);

    var actual = service.getPayload("collection", oid.toHexString());
    assertNull(actual);
  }

  @Test
  public void deletePayloadTest() {
    String mongoOid = "60b73212cfa45d2d5baa795b";
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    when(database.getCollection(mongoOid)).thenReturn(collection);
    when(collection.findOneAndDelete(Filters.eq("_id", oid))).thenReturn(new Document());

    var result = service.deletePayload(mongoOid, oid.toHexString());
    assertTrue(result);
  }

  @Test
  public void deletePayloadTest_collectionIsNull() {
    String mongoOid = "60b73212cfa45d2d5baa795b";
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    doThrow(new IllegalArgumentException()).when(database).getCollection(mongoOid);

    var result = service.deletePayload(mongoOid, oid.toHexString());
    assertFalse(result);
  }

  @Test
  public void deletePayloadTest_notFound() {
    String mongoOid = "60b73212cfa45d2d5baa795b";
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    when(database.getCollection(mongoOid)).thenReturn(collection);
    when(collection.findOneAndDelete(Filters.eq("_id", oid))).thenReturn(null);

    var result = service.deletePayload(mongoOid, oid.toHexString());
    assertTrue(result);
  }
}
