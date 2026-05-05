package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;

public class MongoPingerTest {

  @Test
  public void successfulPing_recordsSuccess() {
    MongoDatabase db = mock(MongoDatabase.class);
    Document ok = new Document("ok", 1.0);
    when(db.runCommand(any(Document.class))).thenReturn(ok);

    MongoPinger p = new MongoPinger();
    p.mongoDatabase = db;

    assertTrue(p.ping());
    assertTrue(p.state().hasEverBeenUp());
  }

  @Test
  public void mongoException_recordsFailure() {
    MongoDatabase db = mock(MongoDatabase.class);
    when(db.runCommand(any(Document.class))).thenThrow(new MongoException("down"));

    MongoPinger p = new MongoPinger();
    p.mongoDatabase = db;

    assertFalse(p.ping());
    assertFalse(p.state().hasEverBeenUp());
  }

  @Test
  public void responseWithoutOkKey_recordsFailure() {
    MongoDatabase db = mock(MongoDatabase.class);
    when(db.runCommand(any(Document.class))).thenReturn(new Document("err", "x"));

    MongoPinger p = new MongoPinger();
    p.mongoDatabase = db;

    assertFalse(p.ping());
  }
}
