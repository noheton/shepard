package de.dlr.shepard.auth.users.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import java.io.ByteArrayInputStream;
import java.util.Arrays;
import org.bson.Document;
import org.bson.types.Binary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * U1e — unit tests for {@link UserAvatarService}.
 *
 * <p>Instantiates the service directly with a mocked {@code MongoDatabase};
 * no Quarkus CDI layer.
 */
@SuppressWarnings("unchecked")
class UserAvatarServiceTest {

  private UserAvatarService service;
  private MongoDatabase db;
  private MongoCollection<Document> collection;

  @BeforeEach
  void setUp() {
    service = new UserAvatarService();
    db = mock(MongoDatabase.class);
    collection = mock(MongoCollection.class);
    service.mongoDatabase = db;
    when(db.getCollection(UserAvatarService.COLLECTION)).thenReturn(collection);
  }

  @Test
  void upsert_smallImage_returnsTrue() {
    byte[] bytes = new byte[100];
    Arrays.fill(bytes, (byte) 0xFF);

    when(collection.replaceOne(any(), any(Document.class), any(ReplaceOptions.class)))
        .thenReturn(null);

    boolean result = service.upsert("user-app-id-1", "image/jpeg",
        new ByteArrayInputStream(bytes));

    assertTrue(result);
    verify(collection).replaceOne(any(), any(Document.class), any(ReplaceOptions.class));
  }

  @Test
  void upsert_tooLarge_returnsFalse() {
    byte[] bytes = new byte[(int) UserAvatarService.MAX_BYTES + 1];

    boolean result = service.upsert("user-app-id-2", "image/png",
        new ByteArrayInputStream(bytes));

    assertFalse(result);
  }

  @Test
  void find_existingUser_returnsDocument() {
    Document doc = new Document()
        .append("_id", "user-app-id-3")
        .append("data", new Binary(new byte[]{1, 2, 3}))
        .append("mimeType", "image/jpeg");

    FindIterable<Document> iterable = mock(FindIterable.class);
    when(collection.find(any(org.bson.conversions.Bson.class))).thenReturn(iterable);
    when(iterable.first()).thenReturn(doc);

    Document result = service.find("user-app-id-3");

    assertNotNull(result);
    assertEquals("image/jpeg", result.getString("mimeType"));
  }

  @Test
  void find_noAvatar_returnsNull() {
    FindIterable<Document> iterable = mock(FindIterable.class);
    when(collection.find(any(org.bson.conversions.Bson.class))).thenReturn(iterable);
    when(iterable.first()).thenReturn(null);

    Document result = service.find("user-app-id-4");

    assertNull(result);
  }

  @Test
  void delete_existingAvatar_returnsTrue() {
    DeleteResult deleteResult = mock(DeleteResult.class);
    when(deleteResult.getDeletedCount()).thenReturn(1L);
    when(collection.deleteOne(any())).thenReturn(deleteResult);

    boolean result = service.delete("user-app-id-5");

    assertTrue(result);
  }

  @Test
  void delete_noAvatar_returnsFalse() {
    DeleteResult deleteResult = mock(DeleteResult.class);
    when(deleteResult.getDeletedCount()).thenReturn(0L);
    when(collection.deleteOne(any())).thenReturn(deleteResult);

    boolean result = service.delete("user-app-id-6");

    assertFalse(result);
  }
}
