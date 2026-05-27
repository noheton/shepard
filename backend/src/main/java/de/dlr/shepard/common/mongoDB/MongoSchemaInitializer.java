package de.dlr.shepard.common.mongoDB;

import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.CreateCollectionOptions;
import com.mongodb.client.model.ValidationAction;
import com.mongodb.client.model.ValidationLevel;
import com.mongodb.client.model.ValidationOptions;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;

/**
 * MONGO-AUDIT-2026-05-24-005 — applies {@code $jsonSchema} validators to the
 * three fixed-name singleton-schema MongoDB collections at application startup.
 *
 * <p><strong>Collections covered:</strong>
 * <ul>
 *   <li>{@code userAvatars} — user avatar documents
 *       ({@code _id}, {@code data}, {@code mimeType}, {@code sizeBytes},
 *       {@code uploadedAt}).</li>
 *   <li>{@code _shepard_files} — shared-namespace singleton-FileReference
 *       bookkeeping docs ({@code _id}, {@code createdAt}, {@code name},
 *       {@code md5}, {@code FileMongoId}; {@code fileSize} is optional because
 *       pre-FB1a rows omit it — see {@link de.dlr.shepard.data.file.services.FileService}).</li>
 *   <li>{@code _shepard_videos} — video-plugin singleton container docs
 *       (same bookkeeping shape as {@code _shepard_files}, written via
 *       {@link de.dlr.shepard.storage.gridfs.GridFsFileStorage}).</li>
 * </ul>
 *
 * <p><strong>Validation action — {@code warn} (not {@code error}):</strong>
 * The first pass uses {@code validationAction: "warn"} so that existing data
 * that pre-dates this validator does not break on read-back.  Once existing
 * data has been confirmed clean on all deployed instances, switch each
 * collection to {@code validationAction: "error"}.
 *
 * TODO: switch all three validators to {@code validationAction: "error"} once
 * existing data confirmed clean (post-MONGO-AUDIT-2026-05-24-005 follow-up).
 *
 * <p><strong>Idempotency:</strong> calling {@code collMod} with a validator on
 * a collection that already has a validator replaces it atomically — safe to
 * run on every restart.  Collections that do not yet exist are created with the
 * validator via {@link MongoDatabase#createCollection(String, CreateCollectionOptions)}.
 *
 * <p><strong>Fail-soft:</strong> a failure on one collection is logged and the
 * initializer continues to the next.  Schema validation is a data-quality aid,
 * not a startup-blocking invariant; the existing {@code MigrationsRunner} already
 * provides the fail-fast gate for structural changes.
 *
 * <p><strong>Per-container FileContainer* collections:</strong> explicitly out of
 * scope.  Their names are dynamically generated UUIDs; applying validators to
 * dynamically-named collections is a different scope (they also share the same
 * bookkeeping shape as {@code _shepard_files}, so once the singleton case is
 * proven out this can be extended).
 */
@ApplicationScoped
public class MongoSchemaInitializer {

  // ── collection names ──────────────────────────────────────────────────────

  /** Singleton avatar collection ({@link de.dlr.shepard.auth.users.services.UserAvatarService}). */
  static final String USER_AVATARS = "userAvatars";

  /**
   * Shared singleton-FileReference bookkeeping collection
   * ({@link de.dlr.shepard.context.references.file.services.SingletonFileReferenceService}).
   */
  static final String SHEPARD_FILES = "_shepard_files";

  /**
   * Video-plugin singleton container — same bookkeeping shape as
   * {@link #SHEPARD_FILES}, written via {@link de.dlr.shepard.storage.gridfs.GridFsFileStorage}.
   * ({@link de.dlr.shepard.context.references.videostreamreference.services.VideoStreamReferenceService#VIDEO_CONTAINER}).
   */
  static final String SHEPARD_VIDEOS = "_shepard_videos";

  // ── injection ─────────────────────────────────────────────────────────────

  @Inject
  @Named("mongoDatabase")
  MongoDatabase mongoDatabase;

  // ── startup hook ──────────────────────────────────────────────────────────

  /**
   * Observed after {@code ShepardMain.init()} completes (Quarkus fires
   * {@code StartupEvent} to CDI {@code @ApplicationScoped} observers strictly
   * after the {@code @Startup} lifecycle hook).
   */
  void onStart(@Observes StartupEvent event) {
    Log.info("MongoSchemaInitializer: applying $jsonSchema validators to singleton collections");
    applyValidator(USER_AVATARS, buildUserAvatarsValidator());
    applyValidator(SHEPARD_FILES, buildShepardFilesValidator());
    applyValidator(SHEPARD_VIDEOS, buildShepardVideosValidator());
    Log.info("MongoSchemaInitializer: done");
  }

  // ── per-collection validators ─────────────────────────────────────────────

  /**
   * Builds the {@code $jsonSchema} validator for the {@code userAvatars} collection.
   *
   * <p>Document shape (from {@link de.dlr.shepard.auth.users.services.UserAvatarService#upsert}):
   * <pre>
   * {
   *   _id:        string   (user appId — UUID v7)
   *   data:       binData  (raw image bytes, ≤ 2 MiB)
   *   mimeType:   string   (image/jpeg | image/png | image/gif | image/webp)
   *   sizeBytes:  int      (byte count of data)
   *   uploadedAt: date
   * }
   * </pre>
   *
   * @return the validator {@link Document}
   */
  static Document buildUserAvatarsValidator() {
    return new Document(
      "$jsonSchema",
      new Document()
        .append("bsonType", "object")
        .append("required", List.of("_id", "data", "mimeType", "sizeBytes", "uploadedAt"))
        .append(
          "properties",
          new Document()
            .append("_id", new Document("bsonType", "string").append("description", "user appId (UUID v7)"))
            .append("data", new Document("bsonType", "binData").append("description", "raw image bytes"))
            .append(
              "mimeType",
              new Document("bsonType", "string")
                .append("description", "MIME type of the image")
                .append("enum", List.of("image/jpeg", "image/png", "image/gif", "image/webp"))
            )
            .append(
              "sizeBytes",
              new Document("bsonType", "int").append("description", "byte count of data").append("minimum", 0)
            )
            .append("uploadedAt", new Document("bsonType", "date").append("description", "upload timestamp"))
        )
    );
  }

  /**
   * Builds the {@code $jsonSchema} validator for the {@code _shepard_files} collection.
   *
   * <p>Document shape (from {@link de.dlr.shepard.data.file.services.FileService#toDocument}
   * plus the {@code FileMongoId} append in {@code createFile}):
   * <pre>
   * {
   *   _id:         objectId  (auto-generated by insertOne)
   *   createdAt:   date
   *   name:        string    (original filename)
   *   md5:         string    (upper-case hex MD5 of file bytes)
   *   FileMongoId: string    (hex string of the GridFS fs.files ObjectId)
   *   fileSize:    long      (optional — pre-FB1a rows omit this field)
   * }
   * </pre>
   *
   * @return the validator {@link Document}
   */
  static Document buildShepardFilesValidator() {
    return buildFileContainerValidator();
  }

  /**
   * Builds the {@code $jsonSchema} validator for the {@code _shepard_videos} collection.
   *
   * <p>Same bookkeeping shape as {@code _shepard_files} — written via
   * {@link de.dlr.shepard.storage.gridfs.GridFsFileStorage#put} which delegates to
   * {@link de.dlr.shepard.data.file.services.FileService#createFile}.
   *
   * @return the validator {@link Document}
   */
  static Document buildShepardVideosValidator() {
    return buildFileContainerValidator();
  }

  /**
   * Shared validator body for the FileService bookkeeping shape.
   *
   * <p>Note: {@code fileSize} is intentionally absent from {@code required} because
   * pre-FB1a documents omit it — see the comment in
   * {@link de.dlr.shepard.data.file.services.FileService#toShepardFile}.
   *
   * @return the validator {@link Document}
   */
  static Document buildFileContainerValidator() {
    return new Document(
      "$jsonSchema",
      new Document()
        .append("bsonType", "object")
        .append("required", List.of("_id", "createdAt", "name", "md5", "FileMongoId"))
        .append(
          "properties",
          new Document()
            .append("_id", new Document("bsonType", "objectId").append("description", "document ObjectId"))
            .append("createdAt", new Document("bsonType", "date").append("description", "upload timestamp"))
            .append("name", new Document("bsonType", "string").append("description", "original filename"))
            .append(
              "md5",
              new Document("bsonType", "string").append("description", "upper-case hex MD5 of the file bytes")
            )
            .append(
              "FileMongoId",
              new Document("bsonType", "string").append("description", "hex ObjectId of the GridFS fs.files entry")
            )
            .append(
              "fileSize",
              new Document("bsonType", "long").append("description", "file size in bytes (optional — absent on pre-FB1a rows)")
            )
        )
    );
  }

  // ── idempotent apply ──────────────────────────────────────────────────────

  /**
   * Applies the given validator to the named collection.
   *
   * <ul>
   *   <li>If the collection already exists, uses {@code collMod} to install /
   *       replace the validator (idempotent).</li>
   *   <li>If the collection does not yet exist, creates it with the validator
   *       and {@code warn} action pre-configured.</li>
   * </ul>
   *
   * <p>Failures are logged and swallowed — schema validation is a data-quality
   * aid, not a startup-blocking invariant.
   *
   * @param collectionName the target collection name
   * @param validator      the {@code $jsonSchema} validator {@link Document}
   */
  void applyValidator(String collectionName, Document validator) {
    try {
      List<String> existing = mongoDatabase.listCollectionNames().into(new ArrayList<>());
      if (existing.contains(collectionName)) {
        // Collection already exists — update (replace) its validator.
        // TODO: switch validationAction to "error" once existing data confirmed clean.
        mongoDatabase.runCommand(
          new Document("collMod", collectionName)
            .append("validator", validator)
            .append("validationLevel", "strict")
            .append("validationAction", "warn") // TODO: switch to "error" once existing data confirmed clean
        );
        Log.infof("MongoSchemaInitializer: applied $jsonSchema validator (warn) to existing collection '%s'", collectionName);
      } else {
        // Collection does not yet exist — create it with the validator.
        // TODO: switch validationAction to "error" once existing data confirmed clean.
        mongoDatabase.createCollection(
          collectionName,
          new CreateCollectionOptions().validationOptions(
            new ValidationOptions()
              .validator(validator)
              .validationLevel(ValidationLevel.STRICT)
              .validationAction(ValidationAction.WARN) // TODO: switch to "error" once existing data confirmed clean
          )
        );
        Log.infof(
          "MongoSchemaInitializer: created collection '%s' with $jsonSchema validator (warn)", collectionName
        );
      }
    } catch (Exception e) {
      Log.warnf(
        "MongoSchemaInitializer: failed to apply $jsonSchema validator to '%s' — %s (non-fatal, continuing)",
        collectionName,
        e.getMessage()
      );
    }
  }
}
