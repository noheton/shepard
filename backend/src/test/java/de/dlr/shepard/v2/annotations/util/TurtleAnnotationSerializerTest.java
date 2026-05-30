package de.dlr.shepard.v2.annotations.util;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SHAPES-V-PREFILL-2-RDF-ENDPOINT — unit tests for {@link TurtleAnnotationSerializer}.
 *
 * <p>Verifies the Turtle §3.3 shape: one flat triple + OA block per annotation,
 * prefix block emitted once, correct handling of literal vs IRI objects,
 * and escape logic for special characters.
 */
class TurtleAnnotationSerializerTest {

  // ─── toTurtle (single annotation) ────────────────────────────────────────

  @Test
  void toTurtle_includesPrefixBlock() {
    var a = makeAnnotation("ann-1", "do-1", "DataObject",
      "http://example.org/material", "CF/LMPAEK");

    String turtle = TurtleAnnotationSerializer.toTurtle(a);

    assertThat(turtle).contains("@prefix oa:");
    assertThat(turtle).contains("@prefix prov:");
    assertThat(turtle).contains("@prefix rdf:");
    assertThat(turtle).contains("@prefix sh:");
    assertThat(turtle).contains("@prefix shepard:");
  }

  @Test
  void toTurtle_includesFlatTriple() {
    var a = makeAnnotation("ann-1", "do-1", "DataObject",
      "http://example.org/material", "CF/LMPAEK");

    String turtle = TurtleAnnotationSerializer.toTurtle(a);

    // Flat triple shape: <subjectIri> <predicateIri> "objectValue" .
    assertThat(turtle).contains("http://example.org/material");
    assertThat(turtle).contains("\"CF/LMPAEK\"");
    assertThat(turtle).contains("https://shepard.dlr.de/v2/dataobjects/do-1");
  }

  @Test
  void toTurtle_includesOaAnnotationFrame() {
    var a = makeAnnotation("ann-1", "do-1", "DataObject",
      "http://example.org/material", "CF/LMPAEK");

    String turtle = TurtleAnnotationSerializer.toTurtle(a);

    assertThat(turtle).contains("oa:Annotation");
    assertThat(turtle).contains("oa:hasTarget");
    assertThat(turtle).contains("oa:hasBody");
  }

  @Test
  void toTurtle_withIriObject_usesAngleBrackets() {
    var a = new SemanticAnnotation();
    a.setAppId("ann-2");
    a.setSubjectAppId("do-2");
    a.setSubjectKind("DataObject");
    a.setPropertyIRI("http://example.org/type");
    a.setValueIRI("http://example.org/materials/LMPAEK");

    String turtle = TurtleAnnotationSerializer.toTurtle(a);

    assertThat(turtle).contains("<http://example.org/materials/LMPAEK>");
    assertThat(turtle).doesNotContain("\"http://example.org/materials/LMPAEK\"");
  }

  @Test
  void toTurtle_withActivityAppId_includesProvWasGeneratedBy() {
    var a = makeAnnotation("ann-3", "do-3", "DataObject",
      "http://example.org/p", "value");
    a.setSourceActivityAppId("act-42");

    String turtle = TurtleAnnotationSerializer.toTurtle(a);

    assertThat(turtle).contains("prov:wasGeneratedBy");
    assertThat(turtle).contains("shepard:Activity/act-42");
  }

  @Test
  void toTurtle_withoutActivityAppId_omitsProvWasGeneratedBy() {
    var a = makeAnnotation("ann-4", "do-4", "DataObject",
      "http://example.org/p", "value");
    // sourceActivityAppId intentionally null

    String turtle = TurtleAnnotationSerializer.toTurtle(a);

    assertThat(turtle).doesNotContain("prov:wasGeneratedBy");
  }

  // ─── toAggregatedTurtle ───────────────────────────────────────────────────

  @Test
  void toAggregatedTurtle_emptyList_returnsPrefixOnlyDocument() {
    String turtle = TurtleAnnotationSerializer.toAggregatedTurtle(List.of());

    assertThat(turtle).contains("@prefix oa:");
    assertThat(turtle).doesNotContain("oa:Annotation");
    assertThat(turtle).doesNotContain("oa:hasTarget");
  }

  @Test
  void toAggregatedTurtle_twoAnnotations_emitsPrefixBlockOnce() {
    var a1 = makeAnnotation("ann-a", "do-x", "DataObject",
      "http://example.org/p1", "v1");
    var a2 = makeAnnotation("ann-b", "do-x", "DataObject",
      "http://example.org/p2", "v2");

    String turtle = TurtleAnnotationSerializer.toAggregatedTurtle(List.of(a1, a2));

    // Prefix block appears exactly once
    assertThat(countOccurrences(turtle, "@prefix oa:")).isEqualTo(1);
    assertThat(countOccurrences(turtle, "@prefix shepard:")).isEqualTo(1);

    // Both predicate IRIs present
    assertThat(turtle).contains("http://example.org/p1");
    assertThat(turtle).contains("http://example.org/p2");

    // Two OA annotation blocks
    assertThat(countOccurrences(turtle, "oa:Annotation")).isEqualTo(2);
  }

  @Test
  void toAggregatedTurtle_oneAnnotation_isEquivalentToToTurtle() {
    var a = makeAnnotation("ann-single", "do-single", "DataObject",
      "http://example.org/mat", "Titanium");

    String aggregated = TurtleAnnotationSerializer.toAggregatedTurtle(List.of(a));
    String single = TurtleAnnotationSerializer.toTurtle(a);

    // Both must contain the same logical content (ignoring trailing newline differences)
    assertThat(aggregated.trim()).isEqualTo(single.trim());
  }

  // ─── escapeTurtleLiteral ──────────────────────────────────────────────────

  @Test
  void escapeTurtleLiteral_escapesDoubleQuotes() {
    String result = TurtleAnnotationSerializer.escapeTurtleLiteral("He said \"hello\"");
    assertThat(result).isEqualTo("He said \\\"hello\\\"");
  }

  @Test
  void escapeTurtleLiteral_escapesBackslash() {
    String result = TurtleAnnotationSerializer.escapeTurtleLiteral("path\\to\\file");
    assertThat(result).isEqualTo("path\\\\to\\\\file");
  }

  @Test
  void escapeTurtleLiteral_escapesNewline() {
    String result = TurtleAnnotationSerializer.escapeTurtleLiteral("line1\nline2");
    assertThat(result).isEqualTo("line1\\nline2");
  }

  @Test
  void escapeTurtleLiteral_escapesCarriageReturn() {
    String result = TurtleAnnotationSerializer.escapeTurtleLiteral("a\rb");
    assertThat(result).isEqualTo("a\\rb");
  }

  // ─── subject IRI forms ────────────────────────────────────────────────────

  @Test
  void toTurtle_collectionSubject_usesCollectionsPath() {
    var a = makeAnnotation("ann-c", "coll-1", "Collection",
      "http://example.org/license", "CC-BY-4.0");

    String turtle = TurtleAnnotationSerializer.toTurtle(a);

    assertThat(turtle).contains("https://shepard.dlr.de/v2/collections/coll-1");
  }

  @Test
  void toTurtle_nullSubjectKind_usesEntitiesPath() {
    var a = new SemanticAnnotation();
    a.setAppId("ann-e");
    a.setSubjectAppId("ent-1");
    a.setSubjectKind(null);  // unknown kind
    a.setPropertyIRI("http://example.org/p");
    a.setValueName("v");

    String turtle = TurtleAnnotationSerializer.toTurtle(a);

    assertThat(turtle).contains("https://shepard.dlr.de/v2/entities/ent-1");
  }

  // ─── helpers ─────────────────────────────────────────────────────────────

  private static SemanticAnnotation makeAnnotation(
    String appId, String subjectAppId, String subjectKind,
    String predicateIri, String value
  ) {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setAppId(appId);
    a.setSubjectAppId(subjectAppId);
    a.setSubjectKind(subjectKind);
    a.setPropertyIRI(predicateIri);
    a.setValueName(value);
    a.setSourceMode("human");
    a.setConfidence(1.0);
    return a;
  }

  private static int countOccurrences(String haystack, String needle) {
    int count = 0;
    int idx = 0;
    while ((idx = haystack.indexOf(needle, idx)) != -1) {
      count++;
      idx += needle.length();
    }
    return count;
  }
}
