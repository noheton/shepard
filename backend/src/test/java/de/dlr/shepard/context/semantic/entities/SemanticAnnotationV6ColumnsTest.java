package de.dlr.shepard.context.semantic.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * SEMA-V6-001 — unit tests for the eight new v6 fields on
 * {@link SemanticAnnotation}.
 *
 * <p>These tests verify:
 * <ul>
 *   <li>Getter / setter round-trip for all 8 fields.</li>
 *   <li>Legacy annotations (no v6 fields set) default to null — no NPE.</li>
 *   <li>hashCode and equals are sensitive to the new fields (two annotations
 *       that differ only in a v6 field are NOT equal).</li>
 *   <li>The {@code source} (TPL4 backfill tag) and {@code sourceMode}
 *       (f(ai)² r provenance mode) are independent, orthogonal fields.</li>
 * </ul>
 *
 * <p>The existing {@link SemanticAnnotationTest#equalsContract()} (EqualsVerifier)
 * is the canonical completeness gate for hashCode / equals correctness across
 * all fields; these tests add explicit regression coverage for the SEMA-V6-001
 * surface specifically.
 */
class SemanticAnnotationV6ColumnsTest {

  // ─── helper ──────────────────────────────────────────────────────────────

  private SemanticAnnotation fullV6() {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setPropertyName("prop");
    a.setPropertyIRI("http://schema.org/name");
    a.setValueName("val");
    a.setValueIRI("http://schema.org/Thing");
    // v6 fields
    a.setSubjectKind("DataObject");
    a.setSubjectAppId("019e3c96-0000-7000-c000-000000000001");
    a.setVocabularyId("019e3c96-0000-7000-d000-000000000001");
    a.setSourceMode("human");
    a.setSourceActivityAppId("019e3c96-0000-7000-e000-000000000001");
    a.setValidFromMillis(1_700_000_000_000L);
    a.setValidUntilMillis(1_800_000_000_000L);
    a.setConfidence(0.95);
    return a;
  }

  // ─── round-trip ──────────────────────────────────────────────────────────

  @Test
  void allV6FieldsRoundTrip() {
    SemanticAnnotation a = fullV6();

    assertEquals("DataObject", a.getSubjectKind());
    assertEquals("019e3c96-0000-7000-c000-000000000001", a.getSubjectAppId());
    assertEquals("019e3c96-0000-7000-d000-000000000001", a.getVocabularyId());
    assertEquals("human", a.getSourceMode());
    assertEquals("019e3c96-0000-7000-e000-000000000001", a.getSourceActivityAppId());
    assertEquals(Long.valueOf(1_700_000_000_000L), a.getValidFromMillis());
    assertEquals(Long.valueOf(1_800_000_000_000L), a.getValidUntilMillis());
    assertEquals(Double.valueOf(0.95), a.getConfidence());
  }

  // ─── legacy-null defaults ─────────────────────────────────────────────────

  @Test
  void legacyAnnotationHasNullV6Fields() {
    SemanticAnnotation a = new SemanticAnnotation();
    a.setPropertyIRI("http://schema.org/name");
    a.setValueIRI("http://schema.org/Thing");

    assertNull(a.getSubjectKind(),          "subjectKind should default null");
    assertNull(a.getSubjectAppId(),         "subjectAppId should default null");
    assertNull(a.getVocabularyId(),         "vocabularyId should default null");
    assertNull(a.getSourceMode(),           "sourceMode should default null");
    assertNull(a.getSourceActivityAppId(),  "sourceActivityAppId should default null");
    assertNull(a.getValidFromMillis(),      "validFromMillis should default null");
    assertNull(a.getValidUntilMillis(),     "validUntilMillis should default null");
    assertNull(a.getConfidence(),           "confidence should default null");
  }

  // ─── equals / hashCode sensitivity ────────────────────────────────────────

  @Test
  void annotationsWithDifferentSubjectAppIdAreNotEqual() {
    SemanticAnnotation a = fullV6();
    SemanticAnnotation b = fullV6();
    b.setSubjectAppId("019e3c96-0000-7000-c000-000000000099");

    assertNotEquals(a, b, "Different subjectAppId must produce unequal annotations");
    assertNotEquals(a.hashCode(), b.hashCode(), "Different subjectAppId must produce different hashCodes");
  }

  @Test
  void annotationsWithDifferentSourceModeAreNotEqual() {
    SemanticAnnotation a = fullV6();
    SemanticAnnotation b = fullV6();
    b.setSourceMode("ai");

    assertNotEquals(a, b, "Different sourceMode must produce unequal annotations");
  }

  @Test
  void annotationsWithDifferentConfidenceAreNotEqual() {
    SemanticAnnotation a = fullV6();
    SemanticAnnotation b = fullV6();
    b.setConfidence(0.5);

    assertNotEquals(a, b, "Different confidence must produce unequal annotations");
  }

  // ─── source vs sourceMode independence ───────────────────────────────────

  @Test
  void tpl4SourceFieldAndSourceModeAreOrthogonal() {
    // `source` = TPL4 backfill tag; `sourceMode` = f(ai)²r provenance mode.
    // Setting one must not affect the other.
    SemanticAnnotation a = new SemanticAnnotation();
    a.setSource("attributes-backfill");    // TPL4 tag
    a.setSourceMode("human");              // v6 provenance mode

    assertEquals("attributes-backfill", a.getSource(),     "source (TPL4) must be independent");
    assertEquals("human",               a.getSourceMode(), "sourceMode (v6) must be independent");
  }
}
