package de.dlr.shepard.data.structureddata.services;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.data.structureddata.entities.StructuredData;
import de.dlr.shepard.data.structureddata.entities.StructuredDataPayload;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
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
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  @Test
  public void createStructuredDataContainerTest() {
    var actual = service.createStructuredDataContainer();
    verify(mongoDatabase).createCollection(collectionName.capture());
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
    when(mongoDatabase.getCollection("collection")).thenReturn(collection);
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
    when(mongoDatabase.getCollection("collection")).thenReturn(collection);
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
    when(mongoDatabase.getCollection("collection")).thenReturn(collection);
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

    when(mongoDatabase.getCollection("collection")).thenReturn(collection);
    doThrow(new MongoException("message")).when(collection).insertOne(any(Document.class));

    var expectedData = new StructuredData();
    expectedData.setName("name");

    assertThrows(InternalServerErrorException.class, () ->
      service.createStructuredData("collection", new StructuredDataPayload(expectedData, payload))
    );
  }

  @Test
  public void createStructuredDataTest_collectionIsNull() {
    String payload = "{\"a\":\"b\", \"c\":\"d\"}";
    String mongoId = "collection";

    var expectedData = new StructuredData();
    expectedData.setName("name");

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(mongoId);

    assertThrows(NotFoundException.class, () ->
      service.createStructuredData(mongoId, new StructuredDataPayload(expectedData, payload))
    );

    verify(collection, never()).insertOne(any(Document.class));
  }

  @Test
  public void createStructuredDataTest_invalidJson() {
    String payload = "invalid";

    when(mongoDatabase.getCollection("collection")).thenReturn(collection);

    var expectedData = new StructuredData();
    expectedData.setName("name");
    var newStrData = new StructuredDataPayload(expectedData, payload);
    assertThrows(InvalidBodyException.class, () -> service.createStructuredData("collection", newStrData));
    verify(collection, never()).insertOne(any(Document.class));
  }

  @Test
  public void deleteStructuredDataTest() {
    when(mongoDatabase.getCollection("collection")).thenReturn(collection);

    assertDoesNotThrow(() -> service.deleteStructuredDataContainer("collection"));
  }

  @Test
  public void deleteStructuredDataTest_collectionIsNull() {
    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection("collection");

    assertThrows(NotFoundException.class, () -> service.deleteStructuredDataContainer("collection"));
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

    when(mongoDatabase.getCollection("collection")).thenReturn(collection);
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

    when(mongoDatabase.getCollection("collection")).thenReturn(collection);
    when(collection.find(eq("_id", oid))).thenReturn(result);
    when(result.first()).thenReturn(doc);

    var actual = service.getPayload("collection", oid.toHexString());
    assertEquals(new StructuredDataPayload(new StructuredData(oid.toHexString(), null, null), doc.toJson()), actual);
  }

  @Test
  public void getPayloadTest_collectionIsNull() {
    ObjectId oid = new ObjectId();

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection("collection");

    assertThrows(NotFoundException.class, () -> service.getPayload("collection", oid.toHexString()));
  }

  @Test
  public void getPayloadTest_payloadIsNull() {
    ObjectId oid = new ObjectId();

    when(mongoDatabase.getCollection("collection")).thenReturn(collection);
    when(collection.find(eq("_id", oid))).thenReturn(result);
    when(result.first()).thenReturn(null);

    assertThrows(NotFoundException.class, () -> service.getPayload("collection", oid.toHexString()));
  }

  @Test
  public void deletePayloadTest() {
    String mongoOid = "60b73212cfa45d2d5baa795b";
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    when(mongoDatabase.getCollection(mongoOid)).thenReturn(collection);
    when(collection.findOneAndDelete(Filters.eq("_id", oid))).thenReturn(new Document());

    assertDoesNotThrow(() -> service.deletePayload(mongoOid, oid.toHexString()));
  }

  @Test
  public void deletePayloadTest_collectionIsNull() {
    String mongoOid = "60b73212cfa45d2d5baa795b";
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    doThrow(new IllegalArgumentException()).when(mongoDatabase).getCollection(mongoOid);

    assertThrows(NotFoundException.class, () -> service.deletePayload(mongoOid, oid.toHexString()));
  }

  @Test
  public void deletePayloadTest_notFound() {
    String mongoOid = "60b73212cfa45d2d5baa795b";
    ObjectId oid = new ObjectId("60b73212cfa45d2d5baa795c");

    when(mongoDatabase.getCollection(mongoOid)).thenReturn(collection);
    when(collection.findOneAndDelete(Filters.eq("_id", oid))).thenReturn(null);

    assertThrows(NotFoundException.class, () -> service.deletePayload(mongoOid, oid.toHexString()));
  }
}
