package de.dlr.shepard.v2.dataobject.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * DB-OPT5 — unit tests for {@link DataObjectListFieldFilter}.
 *
 * <p>Covers field-name validation, the three filter modes (explicit
 * {@code ?fields=}, default-trim, opt-back-in {@code ?include=full}),
 * whitespace tolerance, and the identity-always-included guarantee.
 */
class DataObjectListFieldFilterTest {

  static final ObjectMapper MAPPER = new ObjectMapper();

  /** Build a representative IO with non-trivial values across every field. */
  static DataObjectListItemV2IO makeItem() {
    Collection coll = new Collection();
    coll.setShepardId(42L);
    DataObject d = new DataObject();
    d.setShepardId(84L);
    d.setAppId("018f9c5a-7e26-7000-a000-000000000020");
    d.setName("sensor-track-1");
    d.setDescription("Long markdown description with reasonable size");
    d.setAttributes(Map.of("bench", "P8", "propellant", "LOX/LH2"));
    d.setCollection(coll);
    return new DataObjectListItemV2IO(d, 3L, 5L, 2L);
  }

  @Test
  void knownFieldsContainsCoreSet() {
    Set<String> known = DataObjectListFieldFilter.knownFields();
    // Identity
    assertTrue(known.contains("id"));
    assertTrue(known.contains("appId"));
    assertTrue(known.contains("name"));
    // Inherited content
    assertTrue(known.contains("description"));
    assertTrue(known.contains("attributes"));
    assertTrue(known.contains("status"));
    // v2 counts
    assertTrue(known.contains("timeseriesCount"));
    assertTrue(known.contains("fileCount"));
    assertTrue(known.contains("structuredDataCount"));
    // Deprecated int siblings still discoverable for explicit ?fields= callers
    assertTrue(known.contains("timeseriesReferenceCount"));
    assertTrue(known.contains("fileBundleCount"));
    assertTrue(known.contains("structuredDataReferenceCount"));
  }

  @Test
  void parseFieldsHandlesWhitespaceAndEmpty() {
    assertEquals(Set.of("appId", "name", "createdAt"),
      DataObjectListFieldFilter.parseFields(" appId , name,  createdAt "));
    assertTrue(DataObjectListFieldFilter.parseFields("").isEmpty());
    assertTrue(DataObjectListFieldFilter.parseFields(null).isEmpty());
  }

  @Test
  void firstUnknownFieldFindsOffender() {
    assertNull(DataObjectListFieldFilter.firstUnknownField("appId,name"));
    assertEquals("foo", DataObjectListFieldFilter.firstUnknownField("appId,foo,name"));
    assertNull(DataObjectListFieldFilter.firstUnknownField(""));
    assertNull(DataObjectListFieldFilter.firstUnknownField(null));
  }

  @Test
  void writerDefaultTrimDropsHeavyFields() throws Exception {
    ObjectWriter w = DataObjectListFieldFilter.writerFor(MAPPER, null, false);
    String json = w.writeValueAsString(makeItem());
    assertFalse(json.contains("\"description\""));
    assertFalse(json.contains("\"attributes\""));
    assertFalse(json.contains("\"timeseriesReferenceCount\""));
    assertFalse(json.contains("\"fileBundleCount\""));
    assertFalse(json.contains("\"structuredDataReferenceCount\""));
    // v2 counts + identity remain
    assertTrue(json.contains("\"timeseriesCount\""));
    assertTrue(json.contains("\"appId\""));
    assertTrue(json.contains("\"name\""));
  }

  @Test
  void writerIncludeFullKeepsEverything() throws Exception {
    ObjectWriter w = DataObjectListFieldFilter.writerFor(MAPPER, null, true);
    String json = w.writeValueAsString(makeItem());
    assertTrue(json.contains("\"description\""));
    assertTrue(json.contains("\"attributes\""));
    assertTrue(json.contains("\"timeseriesReferenceCount\""));
  }

  @Test
  void writerExplicitFieldsLimitsToRequested() throws Exception {
    ObjectWriter w = DataObjectListFieldFilter.writerFor(MAPPER, "appId,name,timeseriesCount", false);
    String json = w.writeValueAsString(makeItem());
    assertTrue(json.contains("\"appId\""));
    assertTrue(json.contains("\"name\""));
    assertTrue(json.contains("\"timeseriesCount\""));
    assertFalse(json.contains("\"description\""));
    assertFalse(json.contains("\"fileCount\""));
    assertFalse(json.contains("\"createdAt\""));
  }

  @Test
  void writerExplicitFieldsAlwaysIncludesIdentity() throws Exception {
    // Caller asks ONLY for timeseriesCount; id, appId, name must be there as identity.
    ObjectWriter w = DataObjectListFieldFilter.writerFor(MAPPER, "timeseriesCount", false);
    String json = w.writeValueAsString(makeItem());
    assertTrue(json.contains("\"appId\""));
    assertTrue(json.contains("\"name\""));
    assertTrue(json.contains("\"timeseriesCount\""));
  }

  @Test
  void writerReturnsNullOnUnknownField() {
    assertNull(DataObjectListFieldFilter.writerFor(MAPPER, "appId,bogusField,name", false));
  }

  @Test
  void writerBlankFieldsEqualsDefaultTrim() throws Exception {
    ObjectWriter wBlank = DataObjectListFieldFilter.writerFor(MAPPER, "", false);
    ObjectWriter wNull = DataObjectListFieldFilter.writerFor(MAPPER, null, false);
    String a = wBlank.writeValueAsString(makeItem());
    String b = wNull.writeValueAsString(makeItem());
    assertEquals(b, a);
  }

  @Test
  void defaultTrimMeasurablySmallerThanFullWithRealisticPayload() throws Exception {
    // Synthesise a realistic IO with a 600-char description and 10 attributes.
    Collection coll = new Collection();
    coll.setShepardId(42L);
    DataObject d = new DataObject();
    d.setShepardId(84L);
    d.setAppId("018f9c5a-7e26-7000-a000-000000000020");
    d.setName("AFP-track-001");
    d.setDescription("X".repeat(600));
    java.util.Map<String, String> attrs = new java.util.HashMap<>();
    for (int i = 0; i < 10; i++) attrs.put("attr_key_" + i, "attr_value_" + i + "_descriptive_text");
    d.setAttributes(attrs);
    d.setCollection(coll);
    DataObjectListItemV2IO item = new DataObjectListItemV2IO(d, 3L, 5L, 2L);

    String trimmed = DataObjectListFieldFilter.writerFor(MAPPER, null, false).writeValueAsString(item);
    String full = DataObjectListFieldFilter.writerFor(MAPPER, null, true).writeValueAsString(item);
    String panel = DataObjectListFieldFilter.writerFor(MAPPER,
      "id,appId,name,status,createdAt,referenceIds,childrenIds,incomingIds,timeseriesCount,fileCount,structuredDataCount,timeBoundsStart,timeBoundsEnd",
      false
    ).writeValueAsString(item);
    String minimal = DataObjectListFieldFilter.writerFor(MAPPER, "appId,name,createdAt", false).writeValueAsString(item);

    double trimmedPct = 100.0 * (full.length() - trimmed.length()) / full.length();
    double panelPct = 100.0 * (full.length() - panel.length()) / full.length();
    double minimalPct = 100.0 * (full.length() - minimal.length()) / full.length();
    System.out.printf(
      "DB-OPT5 measurement (1 DO, 600-char description, 12-attr map): full=%d B, default-trim=%d B (%.1f%% smaller), panel-fields=%d B (%.1f%% smaller), minimal=%d B (%.1f%% smaller)%n",
      full.length(), trimmed.length(), trimmedPct, panel.length(), panelPct, minimal.length(), minimalPct
    );

    // DB-OPT5 design goal: ≥ 50% smaller on the default-trim path.
    assertTrue(trimmed.length() < full.length() / 2,
      "default-trim must be < 50% of full payload (got " + trimmed.length() + " vs " + full.length() + ")");
    // Minimal must be ≥ 75% smaller (target gate for minimal mode).
    assertTrue(minimal.length() < full.length() / 4,
      "minimal fields must be < 25% of full payload (got " + minimal.length() + " vs " + full.length() + ")");
    // Always-included identity present in both
    assertTrue(trimmed.contains("\"appId\""));
    assertNotNull(full);
  }
}
