package de.dlr.shepard.context.semantic.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * TPL3a-lite — unit tests for {@link OntologyAlignment}.
 *
 * <p>The entity is a simple data carrier with no custom equals/hashCode
 * override (Lombok @Data covers it). The tests here verify the getter
 * round-trip and the {@link OntologyAlignment#getUniqueId()} contract.
 */
class OntologyAlignmentTest {

  @Test
  void getUniqueId_returnsIdAsString_whenIdSet() {
    OntologyAlignment a = new OntologyAlignment();
    // Simulate what OGM assigns
    a.setId(42L);
    assertEquals("42", a.getUniqueId(), "getUniqueId() must stringify the internal id");
  }

  @Test
  void getUniqueId_returnsNull_whenIdNull() {
    OntologyAlignment a = new OntologyAlignment();
    assertNull(a.getUniqueId(), "getUniqueId() must be null when id is not yet assigned");
  }

  @Test
  void fieldRoundTrip() {
    OntologyAlignment a = new OntologyAlignment();
    a.setAppId("test-appid");
    a.setShepardConcept("Collection");
    a.setUpperOntologyUri("http://purl.obolibrary.org/obo/IAO_0000100");
    a.setRelationshipType("rdfs:subClassOf");
    a.setConfidence("HIGH");
    a.setSource("aidocs/semantics/96-upper-ontology-alignment.md");
    a.setCreatedAt(1_000_000L);

    assertEquals("test-appid", a.getAppId());
    assertEquals("Collection", a.getShepardConcept());
    assertEquals("http://purl.obolibrary.org/obo/IAO_0000100", a.getUpperOntologyUri());
    assertEquals("rdfs:subClassOf", a.getRelationshipType());
    assertEquals("HIGH", a.getConfidence());
    assertEquals("aidocs/semantics/96-upper-ontology-alignment.md", a.getSource());
    assertEquals(1_000_000L, a.getCreatedAt());
  }
}
