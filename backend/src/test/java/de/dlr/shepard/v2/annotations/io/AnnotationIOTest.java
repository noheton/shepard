package de.dlr.shepard.v2.annotations.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import org.junit.jupiter.api.Test;

/**
 * APISIMP-ANNOT-LEGACY-FIELDS-DROP — verifies that the four deprecated legacy
 * field names ({@code propertyName}, {@code propertyIri}, {@code valueName},
 * {@code valueIri}) are absent from the serialised JSON shape of
 * {@link AnnotationIO}.
 */
class AnnotationIOTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Test
  void legacyFieldNamesAbsentFromJson() throws Exception {
    var entity = new SemanticAnnotation(1L);
    entity.setAppId("ann-001");
    entity.setPropertyName("material");
    entity.setPropertyIRI("http://example.org/material");
    entity.setValueName("CF/LMPAEK");

    String json = MAPPER.writeValueAsString(new AnnotationIO(entity));

    assertThat(json).doesNotContain("\"propertyName\"");
    assertThat(json).doesNotContain("\"propertyIri\"");
    assertThat(json).doesNotContain("\"valueName\"");
    assertThat(json).doesNotContain("\"valueIri\"");
  }

  @Test
  void v6CanonicalFieldsPresentInJson() throws Exception {
    var entity = new SemanticAnnotation(1L);
    entity.setAppId("ann-002");
    entity.setPropertyName("material");
    entity.setPropertyIRI("http://example.org/material");
    entity.setValueName("CF/LMPAEK");

    String json = MAPPER.writeValueAsString(new AnnotationIO(entity));

    assertThat(json).contains("\"predicateLabel\"");
    assertThat(json).contains("\"predicateIri\"");
    assertThat(json).contains("\"objectLiteral\"");
  }
}
