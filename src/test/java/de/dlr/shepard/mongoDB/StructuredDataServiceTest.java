package de.dlr.shepard.mongoDB;

import static com.mongodb.client.model.Filters.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.mongodb.MongoException;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import de.dlr.shepard.BaseTestCase;

public class StructuredDataServiceTest extends BaseTestCase {

	@Mock
	FindIterable<Document> result;

	@Mock
	private MongoCollection<Document> collection;

	@Mock
	private MongoDatabase database;

	@Mock
	private MongoDBConnector mongoDBConnector;

	@InjectMocks
	private StructuredDataService service;

	@Captor
	private ArgumentCaptor<String> collectionName;

	@BeforeEach
	public void setupConnector() {
		when(mongoDBConnector.getDatabase()).thenReturn(database);
	}

	@Test
	public void createStructuredDataContainerTest() {
		var actual = service.createStructuredDataContainer();
		verify(mongoDBConnector).createCollection(collectionName.capture());
		assertEquals(collectionName.getValue(), actual);
	}

	@Test
	public void createStructuredDataTest() {
		String payload = "{\"a\":\"b\", \"c\":\"d\"}";
		ObjectId oid = new ObjectId();

		when(database.getCollection("collection")).thenReturn(collection);
		doAnswer(new Answer<Void>() {

			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				Object[] args = invocation.getArguments();
				((Document) args[0]).append("_id", oid);
				return null; // void method, so return null
			}

		}).when(collection).insertOne(Document.parse(payload));

		var actual = service.createStructuredData("collection", new StructuredDataPayload(null, payload));
		assertEquals(new StructuredData(oid.toHexString()), actual);
	}

	@Test
	public void createStructuredDataTest_mongoError() {
		String payload = "{\"a\":\"b\", \"c\":\"d\"}";

		when(database.getCollection("collection")).thenReturn(collection);
		doThrow(new MongoException("message")).when(collection).insertOne(Document.parse(payload));

		var actual = service.createStructuredData("collection", new StructuredDataPayload(null, payload));
		assertNull(actual);
	}

	@Test
	public void createStructuredDataTest_collectionIsNull() {
		String payload = "{\"a\":\"b\", \"c\":\"d\"}";

		var actual = service.createStructuredData("collection", new StructuredDataPayload(null, payload));
		assertNull(actual);
		verify(collection, never()).insertOne(any());
	}

	@Test
	public void createStructuredDataTest_invalidJson() {
		String payload = "invalid";

		when(database.getCollection("collection")).thenReturn(collection);

		var actual = service.createStructuredData("collection", new StructuredDataPayload(null, payload));
		assertNull(actual);
		verify(collection, never()).insertOne(any());
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
		when(database.getCollection("collection")).thenReturn(null);

		var actual = service.deleteStructuredDataContainer("collection");
		assertFalse(actual);
	}

	@Test
	public void getPayloadTest() {
		ObjectId oid = new ObjectId();
		Document doc = new Document("a", "b");

		when(database.getCollection("collection")).thenReturn(collection);
		when(collection.find(eq("_id", oid))).thenReturn(result);
		when(result.first()).thenReturn(doc);

		var actual = service.getPayload("collection", oid.toHexString());

		assertEquals(new StructuredDataPayload(new StructuredData(oid.toHexString()), "{\"a\": \"b\"}"), actual);
	}

	@Test
	public void getPayloadTest_collectionIsNull() {
		ObjectId oid = new ObjectId();

		when(database.getCollection("collection")).thenReturn(null);

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

}
