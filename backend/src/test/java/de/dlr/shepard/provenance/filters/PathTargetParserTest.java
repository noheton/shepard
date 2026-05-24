package de.dlr.shepard.provenance.filters;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the pure {@link PathTargetParser}. No CDI; no DAO; just
 * string parsing — exercises the right-to-left walk that closes
 * PROV-RESOLVER-PATHWALK.
 */
class PathTargetParserTest {

  private static final String UUID_C = "018f9c5a-7e26-7000-a000-000000000010";
  private static final String UUID_D = "018f9c5a-7e26-7000-a000-000000000020";
  private static final String UUID_R = "018f9c5a-7e26-7000-a000-000000000030";

  @Test
  void emptyPathReturnsEmpty() {
    assertTrue(PathTargetParser.parse(null).isEmpty());
    assertTrue(PathTargetParser.parse("").isEmpty());
    assertTrue(PathTargetParser.parse("   ").isEmpty());
    assertTrue(PathTargetParser.parse("collections").isEmpty()); // single segment can't form a pair
  }

  @Test
  void v2CollectionRootResource() {
    // POST /v2/collections/<UUID> → (Collection, <UUID>)
    var r = PathTargetParser.parse("/v2/collections/" + UUID_C);
    assertTrue(r.isPresent());
    assertEquals("Collection", r.get().kind());
    assertEquals(UUID_C, r.get().idString());
    assertFalse(r.get().isNumeric());
  }

  @Test
  void v2NestedSubresourceLandsOnLeafNotParent() {
    // POST /v2/collections/<C>/data-objects/<D> → (DataObject, <D>) — NOT
    // (Collection, <C>) or (DataObject, <C>) like the pre-fix resolver.
    var r = PathTargetParser.parse("/v2/collections/" + UUID_C + "/data-objects/" + UUID_D);
    assertTrue(r.isPresent());
    assertEquals("DataObject", r.get().kind());
    assertEquals(UUID_D, r.get().idString());
  }

  @Test
  void v1NumericCollectionPath() {
    // POST /shepard/api/collections/42 → (Collection, "42", numeric=true)
    var r = PathTargetParser.parse("/shepard/api/collections/42");
    assertTrue(r.isPresent());
    assertEquals("Collection", r.get().kind());
    assertEquals("42", r.get().idString());
    assertTrue(r.get().isNumeric());
  }

  @Test
  void v1DeepNumericPathLandsOnLeaf() {
    // POST /shepard/api/collections/42/dataObjects/45/timeseriesReferences →
    // tail is plural-only (no id), walk left to (dataObjects, 45) →
    // (DataObject, "45").
    var r = PathTargetParser.parse("/shepard/api/collections/42/dataObjects/45/timeseriesReferences");
    assertTrue(r.isPresent());
    assertEquals("DataObject", r.get().kind());
    assertEquals("45", r.get().idString());
    assertTrue(r.get().isNumeric());
  }

  @Test
  void v1DeepNumericPathWithTrailingId() {
    // POST /shepard/api/collections/42/dataObjects/45/references/235/semanticAnnotations
    // tail is plural-only, walk left to (references, 235) → (BasicReference, "235").
    var r = PathTargetParser.parse("/shepard/api/collections/42/dataObjects/45/references/235/semanticAnnotations");
    assertTrue(r.isPresent());
    assertEquals("BasicReference", r.get().kind());
    assertEquals("235", r.get().idString());
    assertTrue(r.get().isNumeric());
  }

  @Test
  void v1DeleteAnnotationPath() {
    // DELETE /shepard/api/collections/42/dataObjects/45/references/235/semanticAnnotations/777
    // → (SemanticAnnotation, "777"), the deepest pair.
    var r = PathTargetParser.parse(
      "/shepard/api/collections/42/dataObjects/45/references/235/semanticAnnotations/777"
    );
    assertTrue(r.isPresent());
    assertEquals("SemanticAnnotation", r.get().kind());
    assertEquals("777", r.get().idString());
  }

  @Test
  void verbTailLandsOnDeepestPair() {
    // /v2/timeseries-references/<UUID>/detect-anomalies — "detect-anomalies"
    // is a verb segment with no id; walk left, take the timeseries-references
    // pair.
    var r = PathTargetParser.parse("/v2/timeseries-references/" + UUID_R + "/detect-anomalies");
    assertTrue(r.isPresent());
    assertEquals("TimeseriesReference", r.get().kind());
    assertEquals(UUID_R, r.get().idString());
  }

  @Test
  void payloadOidSegmentIsSkipped() {
    // /shepard/api/collections/42/dataObjects/45/fileReferences/100/payload/507f1f77bcf86cd799439011
    // The Mongo OID at the tail doesn't match UUID/numeric; payload is also
    // unknown. Walk left → (fileReferences, 100) → (FileReference, "100").
    var r = PathTargetParser.parse(
      "/shepard/api/collections/42/dataObjects/45/fileReferences/100/payload/507f1f77bcf86cd799439011"
    );
    assertTrue(r.isPresent());
    assertEquals("FileReference", r.get().kind());
    assertEquals("100", r.get().idString());
  }

  @Test
  void snapshotDiffPathLandsOnLeftSnapshot() {
    // /v2/snapshots/<A>/diff/<B> — neither (diff, B) nor (snapshots, B) work
    // because diff isn't a plural. Walk further left to (snapshots, A).
    String snapA = "018f9c5a-7e26-7000-a000-0000000000aa";
    String snapB = "018f9c5a-7e26-7000-a000-0000000000bb";
    var r = PathTargetParser.parse("/v2/snapshots/" + snapA + "/diff/" + snapB);
    assertTrue(r.isPresent());
    assertEquals("Snapshot", r.get().kind());
    assertEquals(snapA, r.get().idString());
  }

  @Test
  void pathWithNoKnownPluralReturnsEmpty() {
    // /v2/things/<UUID> — "things" isn't in the plural map; no title-case
    // fallback any more, so this returns empty (no targetKind pollution).
    var r = PathTargetParser.parse("/v2/things/" + UUID_C);
    assertTrue(r.isEmpty());
  }

  @Test
  void verbOnlyPathReturnsEmpty() {
    // PATCH /v2/admin/features/{flag-name} — "features" isn't mapped (it's
    // an admin toggle, not an entity). Tail isn't UUID/numeric either.
    var r = PathTargetParser.parse("/v2/admin/features/foo-bar-baz");
    assertTrue(r.isEmpty());
  }

  @Test
  void uuidWithoutHyphensIsNotMatched() {
    var r = PathTargetParser.parse("/v2/collections/018f9c5a7e267000a000000000000001");
    assertTrue(r.isEmpty());
  }

  @Test
  void leadingSlashOptional() {
    Optional<PathTargetParser.RawTarget> with = PathTargetParser.parse("/v2/collections/" + UUID_C);
    Optional<PathTargetParser.RawTarget> without = PathTargetParser.parse("v2/collections/" + UUID_C);
    assertEquals(with.get().idString(), without.get().idString());
    assertEquals(with.get().kind(), without.get().kind());
  }

  @Test
  void kebabAndCamelBothMapForCoreKinds() {
    assertEquals("DataObject", PathTargetParser.PLURAL_TO_KIND.get("dataobjects"));
    assertEquals("DataObject", PathTargetParser.PLURAL_TO_KIND.get("data-objects"));
    assertEquals("FileContainer", PathTargetParser.PLURAL_TO_KIND.get("filecontainers"));
    assertEquals("FileContainer", PathTargetParser.PLURAL_TO_KIND.get("file-containers"));
    assertEquals("TimeseriesContainer", PathTargetParser.PLURAL_TO_KIND.get("timeseriescontainers"));
    assertEquals("TimeseriesContainer", PathTargetParser.PLURAL_TO_KIND.get("timeseries-containers"));
    assertEquals("StructuredDataContainer", PathTargetParser.PLURAL_TO_KIND.get("structureddatacontainers"));
    assertEquals("StructuredDataContainer", PathTargetParser.PLURAL_TO_KIND.get("structured-data-containers"));
  }
}
