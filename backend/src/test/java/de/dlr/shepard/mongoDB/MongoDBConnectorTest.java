package de.dlr.shepard.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import jakarta.inject.Inject;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class MongoDBConnectorTest {

  @InjectMock
  MongoClient mongoClient;

  @InjectMock
  MongoDatabase mongoDatabase;

  @Inject
  MongoDBConnector mongoDBConnector;

  @BeforeEach
  void setUp() {
    when(mongoClient.getDatabase("database")).thenReturn(mongoDatabase);
  }

  @Test
  public void createInstance_notNull() {
    assertNotNull(mongoClient);
  }

  @Test
  public void createCollectionTest() {
    mongoDBConnector.createCollection("Test");
    verify(mongoDatabase).createCollection("Test");
  }

  @Test
  public void getCollectionTest() {
    @SuppressWarnings("unchecked")
    MongoCollection<Document> result = mock(MongoCollection.class);
    when(mongoClient.getDatabase("database").getCollection("Test")).thenReturn(result);
    var actual = mongoDBConnector.getCollection("Test");

    assertEquals(result, actual);
  }

  @Test
  public void aliveTest() {
    when(mongoClient.getDatabase("database").runCommand(new Document("buildInfo", "1"))).thenReturn(
      new Document("ok", "1")
    );
    var actual = mongoDBConnector.alive();

    assertTrue(actual);
  }

  @Test
  public void aliveTestException() {
    when(mongoClient.getDatabase("database").runCommand(new Document("buildInfo", "1"))).thenThrow(
      new MongoException("Exception")
    );
    var actual = mongoDBConnector.alive();

    assertFalse(actual);
  }

  @Test
  public void aliveTestNotOk() {
    when(mongoClient.getDatabase("database").runCommand(new Document("buildInfo", "1"))).thenReturn(
      new Document("test", "123")
    );
    var actual = mongoDBConnector.alive();

    assertFalse(actual);
  }
}
