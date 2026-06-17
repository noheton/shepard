package de.dlr.shepard.v2.file.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link FileReferenceV2Rest} after APISIMP-FILE-PATH-RETIRE-2.
 *
 * <p>All REST endpoints now return 410 Gone — the live-endpoint tests are
 * replaced with tombstone-verification tests asserting exactly that. The
 * {@code parseRange} and {@code jsonNodeToMap} helper tests are retained
 * verbatim because those methods are kept for test-compat.
 */
class FileReferenceV2RestTest {

  FileReferenceV2Rest resource;

  @BeforeEach
  void setUp() {
    resource = new FileReferenceV2Rest();
  }

  // ─── POST /v2/files — tombstone (APISIMP-KIND-DISCRIMINATOR-2) ───────────

  @Test
  void createSingleton_returns410() {
    assertEquals(410, resource.createSingleton(null, null, null, null).getStatus());
  }

  // ─── GET /v2/files/by-data-object/{id} — tombstone ───────────────────────

  @Test
  void listByDataObject_returns410() {
    assertEquals(410, resource.listByDataObject("any-do-id", null).getStatus());
  }

  // ─── GET /v2/files/{appId} — tombstone ───────────────────────────────────

  @Test
  void getSingleton_returns410() {
    assertEquals(410, resource.getSingleton("any-id", null).getStatus());
  }

  // ─── GET /v2/files/{appId}/content — tombstone ───────────────────────────

  @Test
  void getContent_returns410() {
    assertEquals(410, resource.getContent("any-id", null, null).getStatus());
  }

  @Test
  void getContent_returns410WithRangeHeader() {
    assertEquals(410, resource.getContent("any-id", "bytes=0-1023", null).getStatus());
  }

  // ─── PATCH /v2/files/{appId} — tombstone ─────────────────────────────────

  @Test
  void patchSingleton_returns410() {
    assertEquals(410, resource.patchSingleton("any-id", null, null).getStatus());
  }

  // ─── DELETE /v2/files/{appId} — tombstone ────────────────────────────────

  @Test
  void deleteSingleton_returns410() {
    assertEquals(410, resource.deleteSingleton("any-id", null).getStatus());
  }

  // ─── parseRange (static helper, kept for test-compat) ────────────────────

  @Test
  void parseRange_acceptsClosedRange() {
    var actual = FileReferenceV2Rest.parseRange("bytes=2-4", 10L);
    assertNotNull(actual);
    assertEquals(2L, actual[0]);
    assertEquals(4L, actual[1]);
  }

  @Test
  void parseRange_acceptsOpenEnd() {
    var actual = FileReferenceV2Rest.parseRange("bytes=2-", 10L);
    assertNotNull(actual);
    assertEquals(2L, actual[0]);
    assertEquals(9L, actual[1]);
  }

  @Test
  void parseRange_rejectsSuffix() {
    assertNull(FileReferenceV2Rest.parseRange("bytes=-3", 10L));
  }

  @Test
  void parseRange_rejectsMultiRange() {
    assertNull(FileReferenceV2Rest.parseRange("bytes=0-1,3-4", 10L));
  }

  @Test
  void parseRange_rejectsMissingBytesPrefix() {
    assertNull(FileReferenceV2Rest.parseRange("kilobytes=0-3", 10L));
  }

  @Test
  void parseRange_rejectsStartGteTotal() {
    assertNull(FileReferenceV2Rest.parseRange("bytes=10-15", 10L));
  }

  @Test
  void parseRange_rejectsEndLessThanStart() {
    assertNull(FileReferenceV2Rest.parseRange("bytes=5-2", 10L));
  }

  @Test
  void parseRange_rejectsGarbage() {
    assertNull(FileReferenceV2Rest.parseRange("bytes=abc-def", 10L));
    assertNull(FileReferenceV2Rest.parseRange("bytes=", 10L));
    assertNull(FileReferenceV2Rest.parseRange("", 10L));
    assertNull(FileReferenceV2Rest.parseRange(null, 10L));
  }

  @Test
  void parseRange_clampsEndAtTotalMinusOne() {
    var actual = FileReferenceV2Rest.parseRange("bytes=2-999", 10L);
    assertNotNull(actual);
    assertEquals(9L, actual[1]);
  }

  // ─── jsonNodeToMap (package-private helper, kept for test-compat) ─────────

  @Test
  void jsonNodeToMap_preservesScalars() throws Exception {
    JsonNode body = new ObjectMapper().readTree(
      "{\"s\":\"x\",\"i\":42,\"d\":3.14,\"b\":true,\"n\":null,\"obj\":{\"k\":\"v\",\"e\":null}}"
    );
    Map<String, Object> out = resource.jsonNodeToMap(body);
    assertEquals("x", out.get("s"));
    assertEquals(42L, out.get("i"));
    assertEquals(3.14, (double) out.get("d"), 0.0001);
    assertEquals(true, out.get("b"));
    assertTrue(out.containsKey("n"));
    assertNull(out.get("n"));
    @SuppressWarnings("unchecked")
    Map<String, Object> sub = (Map<String, Object>) out.get("obj");
    assertEquals("v", sub.get("k"));
    assertNull(sub.get("e"));
  }
}
