package de.dlr.shepard.common.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MongoSchemaInitializer}.
 *
 * <p>Validates the structure of each generated {@code $jsonSchema} validator
 * {@link Document} without requiring a running MongoDB instance.
 * MONGO-AUDIT-2026-05-24-005.
 */
public class MongoSchemaInitializerTest {

  // ── userAvatars validator ──────────────────────────────────────────────────

  @Test
  public void userAvatarsValidator_hasJsonSchemaKey() {
    Document validator = MongoSchemaInitializer.buildUserAvatarsValidator();
    assertTrue(validator.containsKey("$jsonSchema"), "Top-level key must be $jsonSchema");
  }

  @Test
  public void userAvatarsValidator_bsonTypeIsObject() {
    Document schema = getSchema(MongoSchemaInitializer.buildUserAvatarsValidator());
    assertEquals("object", schema.getString("bsonType"));
  }

  @Test
  public void userAvatarsValidator_requiredFieldsArePresent() {
    Document schema = getSchema(MongoSchemaInitializer.buildUserAvatarsValidator());
    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) schema.get("required");
    assertNotNull(required);
    assertTrue(required.contains("_id"), "must require _id");
    assertTrue(required.contains("data"), "must require data");
    assertTrue(required.contains("mimeType"), "must require mimeType");
    assertTrue(required.contains("sizeBytes"), "must require sizeBytes");
    assertTrue(required.contains("uploadedAt"), "must require uploadedAt");
  }

  @Test
  public void userAvatarsValidator_idIsBsonString() {
    Document props = getProperties(MongoSchemaInitializer.buildUserAvatarsValidator());
    assertEquals("string", getDoc(props, "_id").getString("bsonType"));
  }

  @Test
  public void userAvatarsValidator_dataIsBinData() {
    Document props = getProperties(MongoSchemaInitializer.buildUserAvatarsValidator());
    assertEquals("binData", getDoc(props, "data").getString("bsonType"));
  }

  @Test
  public void userAvatarsValidator_mimeTypeIsString() {
    Document props = getProperties(MongoSchemaInitializer.buildUserAvatarsValidator());
    Document mimeType = getDoc(props, "mimeType");
    assertEquals("string", mimeType.getString("bsonType"));
  }

  @Test
  public void userAvatarsValidator_mimeTypeEnumContainsAllowedTypes() {
    Document props = getProperties(MongoSchemaInitializer.buildUserAvatarsValidator());
    Document mimeType = getDoc(props, "mimeType");
    @SuppressWarnings("unchecked")
    List<String> enumValues = (List<String>) mimeType.get("enum");
    assertNotNull(enumValues);
    assertTrue(enumValues.contains("image/jpeg"));
    assertTrue(enumValues.contains("image/png"));
    assertTrue(enumValues.contains("image/gif"));
    assertTrue(enumValues.contains("image/webp"));
  }

  @Test
  public void userAvatarsValidator_sizeBytesIsInt() {
    Document props = getProperties(MongoSchemaInitializer.buildUserAvatarsValidator());
    assertEquals("int", getDoc(props, "sizeBytes").getString("bsonType"));
  }

  @Test
  public void userAvatarsValidator_uploadedAtIsDate() {
    Document props = getProperties(MongoSchemaInitializer.buildUserAvatarsValidator());
    assertEquals("date", getDoc(props, "uploadedAt").getString("bsonType"));
  }

  // ── _shepard_files validator ───────────────────────────────────────────────

  @Test
  public void shepardFilesValidator_hasJsonSchemaKey() {
    Document validator = MongoSchemaInitializer.buildShepardFilesValidator();
    assertTrue(validator.containsKey("$jsonSchema"));
  }

  @Test
  public void shepardFilesValidator_requiredFieldsArePresent() {
    Document schema = getSchema(MongoSchemaInitializer.buildShepardFilesValidator());
    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) schema.get("required");
    assertNotNull(required);
    assertTrue(required.contains("_id"), "must require _id");
    assertTrue(required.contains("createdAt"), "must require createdAt");
    assertTrue(required.contains("name"), "must require name");
    assertTrue(required.contains("md5"), "must require md5");
    assertTrue(required.contains("FileMongoId"), "must require FileMongoId");
  }

  @Test
  public void shepardFilesValidator_fileSizeIsNotRequired() {
    // Pre-FB1a rows omit fileSize — it must not appear in required.
    Document schema = getSchema(MongoSchemaInitializer.buildShepardFilesValidator());
    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) schema.get("required");
    assertFalse(required.contains("fileSize"), "fileSize must NOT be in required (pre-FB1a rows omit it)");
  }

  @Test
  public void shepardFilesValidator_idIsObjectId() {
    Document props = getProperties(MongoSchemaInitializer.buildShepardFilesValidator());
    assertEquals("objectId", getDoc(props, "_id").getString("bsonType"));
  }

  @Test
  public void shepardFilesValidator_createdAtIsDate() {
    Document props = getProperties(MongoSchemaInitializer.buildShepardFilesValidator());
    assertEquals("date", getDoc(props, "createdAt").getString("bsonType"));
  }

  @Test
  public void shepardFilesValidator_nameIsString() {
    Document props = getProperties(MongoSchemaInitializer.buildShepardFilesValidator());
    assertEquals("string", getDoc(props, "name").getString("bsonType"));
  }

  @Test
  public void shepardFilesValidator_md5IsString() {
    Document props = getProperties(MongoSchemaInitializer.buildShepardFilesValidator());
    assertEquals("string", getDoc(props, "md5").getString("bsonType"));
  }

  @Test
  public void shepardFilesValidator_fileMongoIdIsString() {
    Document props = getProperties(MongoSchemaInitializer.buildShepardFilesValidator());
    assertEquals("string", getDoc(props, "FileMongoId").getString("bsonType"));
  }

  @Test
  public void shepardFilesValidator_fileSizeIsLongWhenPresent() {
    Document props = getProperties(MongoSchemaInitializer.buildShepardFilesValidator());
    assertEquals("long", getDoc(props, "fileSize").getString("bsonType"));
  }

  // ── _shepard_videos validator ──────────────────────────────────────────────

  @Test
  public void shepardVideosValidator_hasJsonSchemaKey() {
    Document validator = MongoSchemaInitializer.buildShepardVideosValidator();
    assertTrue(validator.containsKey("$jsonSchema"));
  }

  @Test
  public void shepardVideosValidator_sameShapeAsShepardFiles() {
    // _shepard_videos uses the same FileService bookkeeping shape as _shepard_files
    Document filesValidator = MongoSchemaInitializer.buildShepardFilesValidator();
    Document videosValidator = MongoSchemaInitializer.buildShepardVideosValidator();
    // Both must have the same required fields and property types.
    Document filesSchema = getSchema(filesValidator);
    Document videosSchema = getSchema(videosValidator);
    assertEquals(filesSchema.get("required").toString(), videosSchema.get("required").toString());
    assertEquals(
      getProperties(filesValidator).keySet(),
      getProperties(videosValidator).keySet()
    );
  }

  @Test
  public void shepardVideosValidator_fileSizeIsNotRequired() {
    Document schema = getSchema(MongoSchemaInitializer.buildShepardVideosValidator());
    @SuppressWarnings("unchecked")
    List<String> required = (List<String>) schema.get("required");
    assertFalse(required.contains("fileSize"), "fileSize must NOT be in required");
  }

  // ── collection name constants ──────────────────────────────────────────────

  @Test
  public void collectionNameConstants_areCorrect() {
    assertEquals("userAvatars", MongoSchemaInitializer.USER_AVATARS);
    assertEquals("_shepard_files", MongoSchemaInitializer.SHEPARD_FILES);
    assertEquals("_shepard_videos", MongoSchemaInitializer.SHEPARD_VIDEOS);
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  /** Extract the inner schema document from a {@code { "$jsonSchema": ... }} wrapper. */
  private static Document getSchema(Document validator) {
    return (Document) validator.get("$jsonSchema");
  }

  /** Extract the {@code properties} sub-document from a schema document. */
  private static Document getProperties(Document validator) {
    return (Document) getSchema(validator).get("properties");
  }

  /** Get a named property sub-document from the {@code properties} map. */
  private static Document getDoc(Document properties, String fieldName) {
    Document prop = (Document) properties.get(fieldName);
    assertNotNull(prop, "property '" + fieldName + "' must be defined in schema");
    return prop;
  }
}
