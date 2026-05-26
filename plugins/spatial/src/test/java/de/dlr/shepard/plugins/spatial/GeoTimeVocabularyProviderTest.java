package de.dlr.shepard.plugins.spatial;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.spi.semantic.VocabularyPredicateDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * SEMA-V6-009 — unit tests for {@link GeoTimeVocabularyProvider}.
 */
class GeoTimeVocabularyProviderTest {

  private final GeoTimeVocabularyProvider provider = new GeoTimeVocabularyProvider();

  @Test
  void vocabularyUri_is_geosparql_namespace() {
    assertThat(provider.vocabularyUri()).isEqualTo("http://www.opengis.net/ont/geosparql#");
  }

  @Test
  void vocabularyLabel_mentions_geosparql_and_owltime() {
    assertThat(provider.vocabularyLabel())
      .containsIgnoringCase("GeoSPARQL")
      .containsIgnoringCase("Time");
  }

  @Test
  void predicates_list_is_non_empty() {
    assertThat(provider.predicates()).isNotEmpty();
  }

  @Test
  void all_predicate_iris_are_non_blank_absolute_iris() {
    for (VocabularyPredicateDefinition def : provider.predicates()) {
      assertThat(def.iri())
        .as("predicate IRI should be absolute")
        .isNotBlank()
        .startsWith("http");
    }
  }

  @Test
  void all_predicate_labels_are_non_blank() {
    for (VocabularyPredicateDefinition def : provider.predicates()) {
      assertThat(def.label()).as("predicate label for %s", def.iri()).isNotBlank();
    }
  }

  @Test
  void all_predicate_expectedValueTypes_are_valid() {
    List<String> valid = List.of("LITERAL", "IRI", "CUSTOM");
    for (VocabularyPredicateDefinition def : provider.predicates()) {
      assertThat(valid).as("expectedValueType for %s", def.iri()).contains(def.expectedValueType());
    }
  }

  @Test
  void geosparql_predicates_are_present() {
    List<String> iris = provider.predicates().stream()
      .map(VocabularyPredicateDefinition::iri)
      .toList();

    assertThat(iris).contains(
      "http://www.opengis.net/ont/geosparql#hasGeometry",
      "http://www.opengis.net/ont/geosparql#asWKT",
      "http://www.opengis.net/ont/geosparql#sfWithin"
    );
  }

  @Test
  void owltime_predicates_are_present() {
    List<String> iris = provider.predicates().stream()
      .map(VocabularyPredicateDefinition::iri)
      .toList();

    assertThat(iris).contains(
      "http://www.w3.org/2006/time#hasBeginning",
      "http://www.w3.org/2006/time#hasEnd"
    );
  }

  @Test
  void asWKT_predicate_has_LITERAL_expectedValueType() {
    provider.predicates().stream()
      .filter(d -> d.iri().equals("http://www.opengis.net/ont/geosparql#asWKT"))
      .findFirst()
      .ifPresentOrElse(
        d -> assertThat(d.expectedValueType()).isEqualTo("LITERAL"),
        () -> org.junit.jupiter.api.Assertions.fail("asWKT predicate not found")
      );
  }

  @Test
  void hasGeometry_predicate_has_IRI_expectedValueType() {
    provider.predicates().stream()
      .filter(d -> d.iri().equals("http://www.opengis.net/ont/geosparql#hasGeometry"))
      .findFirst()
      .ifPresentOrElse(
        d -> assertThat(d.expectedValueType()).isEqualTo("IRI"),
        () -> org.junit.jupiter.api.Assertions.fail("hasGeometry predicate not found")
      );
  }

  @Test
  void no_duplicate_iris_in_predicate_list() {
    List<String> iris = provider.predicates().stream()
      .map(VocabularyPredicateDefinition::iri)
      .toList();

    assertThat(iris).doesNotHaveDuplicates();
  }
}
