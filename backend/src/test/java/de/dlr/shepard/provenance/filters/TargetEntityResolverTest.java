package de.dlr.shepard.provenance.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Back-compat unit tests for the deprecated static surface of
 * {@link TargetEntityResolver}. The instance-form tests (covering the
 * PROV-V1-NUMERIC-LOOKUP path) live in {@link TargetEntityResolverInstanceTest}
 * and the parser internals live in {@link PathTargetParserTest}.
 *
 * <p>Pre-RDM-004 these assertions covered the production resolver; post-fix
 * they cover only the deprecated static API kept for callers that haven't
 * migrated to the CDI bean.
 */
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
  }

  @Test
  void v1NumericPathReturnsEmptyOnStaticSurface() {
    // The deprecated static resolver has no DAO access, so numeric ids
    // can't be resolved to appIds — return empty. The instance form
    // (TargetEntityResolverInstanceTest) covers the real fix.
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
  void v2KebabCaseDataObjectPath() {
    // Kebab-case is the v2 convention: /v2/collections/<C>/data-objects/<D>
    Optional<TargetEntityResolver.TargetRef> r = TargetEntityResolver.resolve("/v2/data-objects/" + UUID1);
    assertTrue(r.isPresent());
    assertEquals("DataObject", r.get().kind());
    assertEquals(UUID1, r.get().appId());
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
    assertEquals("DataObject", TargetEntityResolver.plural("data-objects"));
    assertEquals("FileBundle", TargetEntityResolver.plural("bundles"));
    assertEquals("FileReference", TargetEntityResolver.plural("filereferences"));
    assertEquals("FileReference", TargetEntityResolver.plural("file-references"));
    assertEquals("FileGroup", TargetEntityResolver.plural("filegroups"));
    assertEquals("TimeseriesReference", TargetEntityResolver.plural("timeseriesreferences"));
    assertEquals("TimeseriesReference", TargetEntityResolver.plural("timeseries-references"));
    assertEquals("VideoReference", TargetEntityResolver.plural("videoreferences"));
    assertEquals("VideoReference", TargetEntityResolver.plural("video-references"));
    assertEquals("ApiKey", TargetEntityResolver.plural("apikeys"));
    assertEquals("LabJournalEntry", TargetEntityResolver.plural("lab-journal-entries"));
    assertEquals("CollectionProperties", TargetEntityResolver.plural("properties"));
    assertEquals("ShepardTemplate", TargetEntityResolver.plural("templates"));
    assertEquals("Snapshot", TargetEntityResolver.plural("snapshots"));
    assertEquals("Watch", TargetEntityResolver.plural("watches"));
    assertEquals("Notification", TargetEntityResolver.plural("notifications"));
  }

  @Test
  void unknownPluralReturnsNullNotTitleCased() {
    // PROV-RESOLVER-PATHWALK behaviour change — the old resolver title-cased
    // unknown plurals so any path ending in /things/{uuid} stamped
    // targetKind=Thing. With right-to-left walk that fallback would pollute
    // targetKind on verb-shaped segments (/payload, /diff, /detect-anomalies).
    // Unknown plurals must now return null.
    assertNull(TargetEntityResolver.plural("things"));
    assertNull(TargetEntityResolver.plural("stories"));
    assertNull(TargetEntityResolver.plural("payload"));
    assertNull(TargetEntityResolver.plural("diff"));
    assertNull(TargetEntityResolver.plural("detect-anomalies"));
  }

  @Test
  void caseInsensitivePluralLookup() {
    assertEquals("Collection", TargetEntityResolver.plural("COLLECTIONS"));
    assertEquals("DataObject", TargetEntityResolver.plural("DataObjects"));
    assertEquals("DataObject", TargetEntityResolver.plural("Data-Objects"));
  }

  @Test
  void nestedPathTakesDeepestUuidPair() {
    // /v2/collections/{coll-uuid}/dataobjects/{do-uuid} → target is the DataObject.
    // RDM-004 bucket B fix: previously this stamped the DataObject KIND but
    // the Collection's UUID as the appId (last-UUID logic) — wrong target.
    String coll = "018f9c5a-7e26-7000-a000-000000000010";
    String dobj = "018f9c5a-7e26-7000-a000-000000000020";
    Optional<TargetEntityResolver.TargetRef> r = TargetEntityResolver.resolve(
      "/v2/collections/" + coll + "/dataobjects/" + dobj
    );
    assertTrue(r.isPresent());
    assertEquals("DataObject", r.get().kind());
    assertEquals(dobj, r.get().appId());
  }

  @Test
  void verbSuffixPathLandsOnDeepestPair() {
    // /v2/timeseries-references/<id>/detect-anomalies — tail segment is a verb
    // (no id), walk left to (timeseries-references, <id>).
    String ref = "018f9c5a-7e26-7000-a000-000000000030";
    Optional<TargetEntityResolver.TargetRef> r = TargetEntityResolver.resolve(
      "/v2/timeseries-references/" + ref + "/detect-anomalies"
    );
    assertTrue(r.isPresent());
    assertEquals("TimeseriesReference", r.get().kind());
    assertEquals(ref, r.get().appId());
  }

  @Test
  void uuidWithoutHyphensIsNotMatched() {
    // Strict RFC 4122 hyphen-canonical only.
    assertTrue(TargetEntityResolver.resolve("/v2/collections/018f9c5a7e267000a000000000000001").isEmpty());
  }
}
