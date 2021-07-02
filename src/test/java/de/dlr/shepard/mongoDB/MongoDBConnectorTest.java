package de.dlr.shepard.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.bson.Document;
import org.bson.codecs.configuration.CodecRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import de.dlr.shepard.BaseTestCase;

public class MongoDBConnectorTest extends BaseTestCase {

	@Mock
	private MongoClient mongoClient;
	@Mock
	private MongoDatabase database;
	@Mock
	private CodecRegistry pojoCodecRegistry;
	@InjectMocks
	private MongoDBConnector mongoDBConnector;

	@Test
	public void testGetInstance() {
		var actual = MongoDBConnector.getInstance();
		assertNotNull(actual);

		var second = MongoDBConnector.getInstance();
		assertEquals(actual, second);
	}

	@Test
	public void createCollectionTest() {
		mongoDBConnector.createCollection("Test");
		verify(database).createCollection("Test");
	}

	@Test
	public void getCollectionTest() {
		@SuppressWarnings("unchecked")
		MongoCollection<Document> result = mock(MongoCollection.class);
		when(database.getCollection("Test")).thenReturn(result);
		var actual = mongoDBConnector.getCollection("Test");

		assertEquals(result, actual);
	}

}
