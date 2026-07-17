package de.dlr.shepard.v2.semantic.io;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticRepository;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-SEMIO-NUMERIC-ID — verifies that {@link SemanticAnnotationV2IO} maps
 * entity fields correctly and omits numeric Neo4j ids from the wire shape.
 */
class SemanticAnnotationV2IOTest {

  @Test
  void constructor_mapsAllStringFields() {
    var ann = new SemanticAnnotation(99L);
    ann.setAppId("019506b4-dc55-7c92-b4e1-bf94db37e5b9");
    ann.setPropertyIRI("http://example.org/prop");
    ann.setPropertyName("propName");
    ann.setValueIRI("http://example.org/val");
    ann.setValueName("valName");
    ann.setNumericValue(3.14);
    ann.setUnitIRI("http://qudt.org/vocab/unit/M");

    var propRepo = new SemanticRepository();
    propRepo.setAppId("prop-repo-appid");
    ann.setPropertyRepository(propRepo);

    var valRepo = new SemanticRepository();
    valRepo.setAppId("val-repo-appid");
    ann.setValueRepository(valRepo);

    var io = new SemanticAnnotationV2IO(ann);

    assertThat(io.getAppId()).isEqualTo("019506b4-dc55-7c92-b4e1-bf94db37e5b9");
    assertThat(io.getPropertyIRI()).isEqualTo("http://example.org/prop");
    assertThat(io.getPropertyName()).isEqualTo("propName");
    assertThat(io.getValueIRI()).isEqualTo("http://example.org/val");
    assertThat(io.getValueName()).isEqualTo("valName");
    assertThat(io.getNumericValue()).isEqualTo(3.14);
    assertThat(io.getUnitIRI()).isEqualTo("http://qudt.org/vocab/unit/M");
    assertThat(io.getPropertyVocabularyEntryAppId()).isEqualTo("prop-repo-appid");
    assertThat(io.getValueVocabularyEntryAppId()).isEqualTo("val-repo-appid");
  }

  @Test
  void constructor_handlesNullRepositories() {
    var ann = new SemanticAnnotation(1L);
    ann.setPropertyIRI("http://p.org/x");
    ann.setValueIRI("http://v.org/x");
    // No propertyRepository or valueRepository set

    var io = new SemanticAnnotationV2IO(ann);

    assertThat(io.getPropertyVocabularyEntryAppId()).isNull();
    assertThat(io.getValueVocabularyEntryAppId()).isNull();
  }

  @Test
  void noArgConstructor_createsEmptyInstance() {
    var io = new SemanticAnnotationV2IO();
    assertThat(io.getPropertyIRI()).isNull();
    assertThat(io.getValueIRI()).isNull();
  }
}
