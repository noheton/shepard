package de.dlr.shepard.provenance.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class TargetEntityResolverTest {

  // Canonical UUID v7 example — the f4b6a3 generator's shape.
  private static final String UUID1 = "018f9c5a-7e26-7000-a000-000000000001";

  @Test
  void emptyPathReturnsEmpty() {
    assertTrue(TargetEntityResolver.resolve(null).isEmpty());
    assertTrue(TargetEntityResolver.resolve("").isEmpty());
    assertTrue(TargetEntityResolver.resolve("   ").isEmpty());
  }

  @Test
  void pathWithoutTrailingUuidReturnsEmpty() {
    assertTrue(TargetEntityResolver.resolve("/v2/collections").isEmpty());
    assertTrue(TargetEntityResolver.resolve("/v2/provenance/activities").isEmpty());
    // Legacy numeric-id paths (the /shepard/api/ surface) — not UUIDs, no match.
    assertTrue(TargetEntityResolver.resolve("/shepard/api/collections/42").isEmpty());
  }

  @Test
  void v2CollectionPath() {
    Optional<TargetEntityResolver.TargetRef> r = TargetEntityResolver.resolve("/v2/collections/" + UUID1);
    assertTrue(r.isPresent());
    assertEquals("Collection", r.get().kind());
    assertEquals(UUID1, r.get().appId());
  }

  @Test
  void v2DataObjectPath() {
    Optional<TargetEntityResolver.TargetRef> r = TargetEntityResolver.resolve("/v2/dataobjects/" + UUID1);
    assertTrue(r.isPresent());
    assertEquals("DataObject", r.get().kind());
  }

  @Test
  void leadingSlashIsOptional() {
    Optional<TargetEntityResolver.TargetRef> a = TargetEntityResolver.resolve("/v2/collections/" + UUID1);
    Optional<TargetEntityResolver.TargetRef> b = TargetEntityResolver.resolve("v2/collections/" + UUID1);
    assertEquals(a, b);
  }

  @Test
  void pluralMappingCoversKnownTypes() {
    assertEquals("Collection", TargetEntityResolver.plural("collections"));
    assertEquals("DataObject", TargetEntityResolver.plural("dataobjects"));
    assertEquals("FileBundle", TargetEntityResolver.plural("filebundles"));
    assertEquals("FileBundle", TargetEntityResolver.plural("filereferences"));
    assertEquals("FileGroup", TargetEntityResolver.plural("filegroups"));
    assertEquals("TimeseriesReference", TargetEntityResolver.plural("timeseries"));
    assertEquals("VideoReference", TargetEntityResolver.plural("videos"));
    assertEquals("ApiKey", TargetEntityResolver.plural("apikeys"));
    assertEquals("LabJournalEntry", TargetEntityResolver.plural("lab-journal-entries"));
    assertEquals("CollectionProperties", TargetEntityResolver.plural("properties"));
    assertEquals("ShepardTemplate", TargetEntityResolver.plural("templates"));
  }

  @Test
  void unknownPluralFallsBackToTitleCasedSingular() {
    // E.g. /v2/things/{uuid} — never heard of "thing", so resolver yields "Thing".
    assertEquals("Thing", TargetEntityResolver.plural("things"));
    assertEquals("Story", TargetEntityResolver.plural("stories"));
  }

  @Test
  void caseInsensitivePluralLookup() {
    assertEquals("Collection", TargetEntityResolver.plural("COLLECTIONS"));
    assertEquals("DataObject", TargetEntityResolver.plural("DataObjects"));
  }

  @Test
  void nestedPathTakesLastUuid() {
    // /v2/collections/{coll-uuid}/dataobjects/{do-uuid} → target is the DataObject
    String coll = "018f9c5a-7e26-7000-a000-000000000010";
    String dobj = "018f9c5a-7e26-7000-a000-000000000020";
    Optional<TargetEntityResolver.TargetRef> r = TargetEntityResolver.resolve("/v2/collections/" + coll + "/dataobjects/" + dobj);
    assertTrue(r.isPresent());
    assertEquals("DataObject", r.get().kind());
    assertEquals(dobj, r.get().appId());
  }

  @Test
  void uuidWithoutHyphensIsNotMatched() {
    // Strict RFC 4122 hyphen-canonical only.
    assertTrue(TargetEntityResolver.resolve("/v2/collections/018f9c5a7e267000a000000000000001").isEmpty());
  }
}
