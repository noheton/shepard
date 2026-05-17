package de.dlr.shepard.auth.users.services;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.ReplaceOptions.createReplaceOptions;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Set;
import org.bson.Document;
import org.bson.types.Binary;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

/**
 * U1e — stores user avatars in MongoDB collection {@code userAvatars}.
 *
 * <p>Document shape: {@code { _id: userAppId, data: BinData, mimeType: String,
 * sizeBytes: int, uploadedAt: Date }}.
 *
 * <p>No Neo4j migration needed — the collection is owned by this service;
 * appId is the document key (same approach as StructuredData containers).
 */
@ApplicationScoped
public class UserAvatarService {

  static final String COLLECTION = "userAvatars";
  static final long MAX_BYTES = 2 * 1024 * 1024L; // 2 MB
  static final Set<String> ALLOWED_MIME_TYPES = Set.of(
      "image/jpeg", "image/png", "image/gif", "image/webp");

  @Inject
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  private MongoCollection<Document> collection() {
    return mongoDatabase.getCollection(COLLECTION);
  }

  /**
   * Upserts an avatar for the given user. Reads up to {@link #MAX_BYTES} from the
   * stream and stores the raw bytes. Returns false if the stream exceeds the size limit.
   */
  public boolean upsert(String userAppId, String mimeType, InputStream in) {
    byte[] bytes;
    try {
      bytes = in.readNBytes((int) MAX_BYTES + 1);
    } catch (IOException e) {
      Log.warnf("avatar upsert: IO error reading bytes for %s: %s", userAppId, e.getMessage());
      return false;
    }
    if (bytes.length > MAX_BYTES) {
      return false;
    }

    Document doc = new Document()
        .append("_id", userAppId)
        .append("data", new Binary(bytes))
        .append("mimeType", mimeType)
        .append("sizeBytes", bytes.length)
        .append("uploadedAt", new Date());

    collection().replaceOne(eq("_id", userAppId), doc, createReplaceOptions().upsert(true));
    return true;
  }

  /** Returns the avatar document for the given user appId, or null if none exists. */
  public Document find(String userAppId) {
    return collection().find(eq("_id", userAppId)).first();
  }

  /** Deletes the avatar for the given user appId. Returns true if a document was deleted. */
  public boolean delete(String userAppId) {
    return collection().deleteOne(eq("_id", userAppId)).getDeletedCount() > 0;
  }
}
