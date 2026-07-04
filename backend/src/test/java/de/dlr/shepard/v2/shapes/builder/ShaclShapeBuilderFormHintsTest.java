package de.dlr.shepard.v2.shapes.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;

/**
 * BTKVS-B2 — the FORM-DASH-VOCAB builder slice: {@code sh:pattern},
 * {@code sh:targetNode}, the three hint layers (core SHACL, DASH, minted
 * residue) and the domain-side cell-mapping annotations, plus
 * {@code sh:PropertyGroup} emission (doc 125 §4).
 */
class ShaclShapeBuilderFormHintsTest {

  final ShaclShapeBuilder builder = new ShaclShapeBuilder();

  static final String SHAPE_IRI = "urn:btkvs:shape:docket-general";
  static final String GROUP_IRI = "urn:btkvs:group:identity";

  static ShapeSpec docketGeneralShape() {
    var docketId = new PropertyShapeSpec(
      "urn:shepard:attribute:docket_id",
      "http://www.w3.org/2001/XMLSchema#string",
      1,
      1,
      null,
      null,
      "^[A-Z][0-9]{3}$",
      new FormHintSpec(
        "Docket ID",
        "Internal docket identifier, one capital letter + three digits.",
        1.0,
        GROUP_IRI,
        null,
        ShaclShapeBuilder.DASH + "TextFieldEditor",
        null,
        "I123",
        null,
        new FormHintSpec.CellMappingSpec("Laufzettel C-C bzw C-C-SiC", "K1")
      )
    );
    var project = new PropertyShapeSpec(
      "urn:shepard:attribute:project",
      "http://www.w3.org/2001/XMLSchema#string",
      1,
      null,
      null,
      null,
      null,
      new FormHintSpec(
        "Project",
        null,
        2.0,
        GROUP_IRI,
        null,
        ShaclShapeBuilder.DASH + "TextFieldEditor",
        null,
        null,
        null,
        new FormHintSpec.CellMappingSpec(null, "C4")
      )
    );
    var comments = new PropertyShapeSpec(
      "urn:shepard:attribute:comments",
      "http://www.w3.org/2001/XMLSchema#string",
      null,
      null,
      null,
      null,
      null,
      new FormHintSpec("Comments", null, 6.0, null, null, null, Boolean.FALSE, null, null, null)
    );
    return new ShapeSpec(
      SHAPE_IRI,
      null,
      false,
      List.of(docketId, project, comments),
      List.of(new GroupSpec(GROUP_IRI, "Identity", 1.0)),
      "urn:shepard:instance:candidate"
    );
  }

  @Test
  void emitsPatternAndTargetNode() {
    String ttl = builder.toTurtle(docketGeneralShape());
    assertThat(ttl).contains("sh:pattern \"^[A-Z][0-9]{3}$\"");
    assertThat(ttl).contains("sh:targetNode <urn:shepard:instance:candidate>");
  }

  @Test
  void emitsCoreShaclHintCharacteristics() {
    String ttl = builder.toTurtle(docketGeneralShape());
    assertThat(ttl).contains("sh:name \"Docket ID\"");
    assertThat(ttl).contains("sh:description \"Internal docket identifier, one capital letter + three digits.\"");
    assertThat(ttl).contains("sh:order 1");
    assertThat(ttl).contains("sh:group <" + GROUP_IRI + ">");
  }

  @Test
  void emitsDashEditorAndSingleLine() {
    String ttl = builder.toTurtle(docketGeneralShape());
    assertThat(ttl).contains("dash:editor <http://datashapes.org/dash#TextFieldEditor>");
    assertThat(ttl).contains("dash:singleLine false");
  }

  @Test
  void emitsMintedResidueAndCellMappings() {
    String ttl = builder.toTurtle(docketGeneralShape());
    assertThat(ttl).contains("<urn:shepard:form:placeholder> \"I123\"");
    assertThat(ttl).contains("<urn:btkvs:sheet> \"Laufzettel C-C bzw C-C-SiC\"");
    assertThat(ttl).contains("<urn:btkvs:cell-mapping> \"K1\"");
    assertThat(ttl).contains("<urn:btkvs:cell-mapping> \"C4\"");
  }

  @Test
  void emitsPropertyGroupBlock() {
    String ttl = builder.toTurtle(docketGeneralShape());
    assertThat(ttl).contains("<" + GROUP_IRI + "> a sh:PropertyGroup");
    assertThat(ttl).contains("rdfs:label \"Identity\"");
  }

  @Test
  void outputIsDeterministic() {
    assertThat(builder.toTurtle(docketGeneralShape())).isEqualTo(builder.toTurtle(docketGeneralShape()));
  }

  @Test
  void outputParsesAsTurtle() {
    Model m = builder.toModel(docketGeneralShape());
    assertThat(m.size()).isGreaterThan(10);
  }

  @Test
  void emptyHintsEmitNothingExtra() {
    var spec = new ShapeSpec(
      SHAPE_IRI,
      null,
      false,
      List.of(
        new PropertyShapeSpec(
          "urn:shepard:attribute:plain",
          null,
          null,
          null,
          null,
          null,
          null,
          new FormHintSpec(null, null, null, null, null, null, null, null, null, null)
        )
      )
    );
    String ttl = builder.toTurtle(spec);
    assertThat(ttl).doesNotContain("sh:name").doesNotContain("dash:editor").doesNotContain("sh:group");
  }

  @Test
  void blankGroupIriIsRejected() {
    var spec = new ShapeSpec(SHAPE_IRI, null, false, List.of(), List.of(new GroupSpec(" ", "Bad", 1.0)), null);
    assertThatThrownBy(() -> builder.toTurtle(spec)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void preB2CompatibilityConstructorStillCompilesPlainShapes() {
    var spec = new ShapeSpec(SHAPE_IRI, null, false, List.of(PropertyShapeSpec.of("urn:shepard:attribute:x")));
    String ttl = builder.toTurtle(spec);
    assertThat(ttl).contains("sh:path <urn:shepard:attribute:x>");
    assertThat(ttl).doesNotContain("sh:targetNode");
  }
}
