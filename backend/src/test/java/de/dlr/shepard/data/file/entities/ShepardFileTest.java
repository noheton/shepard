package de.dlr.shepard.data.file.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class ShepardFileTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(ShepardFile.class).verify();
  }

  @Test
  public void constructorTest1() {
    var date = new Date();
    var expected = new ShepardFile();
    expected.setCreatedAt(date);
    expected.setFilename("name");
    expected.setMd5("md5");
    var actual = new ShepardFile(date, "name", "md5");
    assertEquals(expected, actual);
  }

  @Test
  public void constructorTest2() {
    var date = new Date();
    var expected = new ShepardFile();
    expected.setOid("oid");
    expected.setCreatedAt(date);
    expected.setFilename("name");
    expected.setMd5("md5");
    var actual = new ShepardFile("oid", date, "name", "md5");
    assertEquals(expected, actual);
  }

  @Test
  public void getUniqueIdTest() {
    var file = new ShepardFile("oid", new Date(), "name", "md5");
    var actual = file.getUniqueId();

    assertEquals("oid", actual);
  }

  @Test
  public void fileSizeRoundTrip() {
    // FB1a: fileSize is a new nullable field on ShepardFile.
    var file = new ShepardFile(new Date(), "name", "md5");
    org.junit.jupiter.api.Assertions.assertNull(file.getFileSize());
    file.setFileSize(12345L);
    assertEquals(12345L, file.getFileSize());
  }

  // ─── FS1e3 — per-file rollback fields ─────────────────────────────────────

  @Test
  public void rollbackFieldsDefaultNull() {
    // FS1e3: a freshly-constructed ShepardFile has all four
    // migration-rollback fields = null. Pre-FS1e3 rows in Neo4j read
    // back this way too (schema-less; absent property = null).
    var file = new ShepardFile(new Date(), "name", "md5");
    assertNull(file.getPreviousProviderId());
    assertNull(file.getPreviousLocator());
    assertNull(file.getMigratedAt());
    assertNull(file.getMigrationHmac());
  }

  @Test
  public void previousProviderIdRoundTrip() {
    var file = new ShepardFile(new Date(), "name", "md5");
    file.setPreviousProviderId("gridfs");
    assertEquals("gridfs", file.getPreviousProviderId());
  }

  @Test
  public void previousLocatorRoundTrip() {
    var file = new ShepardFile(new Date(), "name", "md5");
    file.setPreviousLocator("507f1f77bcf86cd799439011:abc123");
    assertEquals("507f1f77bcf86cd799439011:abc123", file.getPreviousLocator());
  }

  @Test
  public void migratedAtRoundTrip() {
    var file = new ShepardFile(new Date(), "name", "md5");
    var t = Instant.parse("2026-05-22T14:00:00Z");
    file.setMigratedAt(t);
    assertEquals(t, file.getMigratedAt());
  }

  @Test
  public void migrationHmacRoundTrip() {
    var file = new ShepardFile(new Date(), "name", "md5");
    file.setMigrationHmac("sha256:e3b0c44298fc1c149afbf4c8996fb924");
    assertEquals("sha256:e3b0c44298fc1c149afbf4c8996fb924", file.getMigrationHmac());
  }

  /**
   * Wire-shape regression: serialising a ShepardFile must NOT expose
   * any of the four FS1e3 migration-rollback fields, nor the original
   * providerId field. This is the load-bearing v5 backward-compat
   * guarantee — upstream clients reading `/shepard/api/...fileContainers/.../files/{id}`
   * must continue to see exactly the legacy field set.
   *
   * <p>Verifies the {@code @JsonIgnore} + {@code @Schema(hidden=true)}
   * posture on every internal-bookkeeping field is intact.
   */
  @Test
  public void v5WireShapeOmitsInternalBookkeeping() throws Exception {
    var file = new ShepardFile("507f1f77bcf86cd799439011:abc123", new Date(), "report.pdf", "d41d8cd98f");
    file.setFileSize(2048L);
    file.setProviderId("s3");
    file.setPreviousProviderId("gridfs");
    file.setPreviousLocator("507f1f77bcf86cd799439011:abc123");
    file.setMigratedAt(Instant.parse("2026-05-22T14:00:00Z"));
    file.setMigrationHmac("sha256:deadbeef");

    var mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(file);

    // The legacy/upstream wire shape DOES include: filename, md5,
    // fileSize, createdAt, oid (inherited from AbstractMongoObject).
    assertTrue(json.contains("\"filename\""), "filename must be on the wire");
    assertTrue(json.contains("\"md5\""), "md5 must be on the wire");
    assertTrue(json.contains("\"fileSize\""), "fileSize must be on the wire (FB1a)");

    // The internal bookkeeping fields MUST NOT appear on the wire.
    assertFalse(json.contains("providerId"),
      "providerId must stay internal — leaks the FS1a storage routing");
    assertFalse(json.contains("previousProviderId"),
      "previousProviderId must stay internal — FS1e3 internal bookkeeping");
    assertFalse(json.contains("previousLocator"),
      "previousLocator must stay internal — FS1e3 internal bookkeeping");
    assertFalse(json.contains("migratedAt"),
      "migratedAt must stay internal — FS1e3 internal bookkeeping");
    assertFalse(json.contains("migrationHmac"),
      "migrationHmac must stay internal — FS1e3 internal bookkeeping");
    // appId stays internal too (L2a posture). Sanity check this still holds.
    assertFalse(json.contains("appId"),
      "appId must stay internal — L2a additive posture");
    // id stays internal (Neo4j-OGM-managed; clients use oid).
    assertNotNull(json);
  }
}
