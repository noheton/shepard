package de.dlr.shepard.v2.shapes.resources;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.v2.shapes.builder.ShaclShapeBuilder;
import de.dlr.shepard.v2.shapes.io.ShapeBuildRequestIO;
import de.dlr.shepard.v2.shapes.io.ShapeBuildRequestIO.InMemberIO;
import de.dlr.shepard.v2.shapes.io.ShapeBuildRequestIO.PropertyIO;
import de.dlr.shepard.v2.shapes.io.ShapeBuildResponseIO;
import de.dlr.shepard.v2.shapes.validator.JenaShaclValidator;
import jakarta.ws.rs.core.Response;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShapesBuildRestTest {

  static final String SHEPARD = "http://semantics.dlr.de/shepard#";
  static final String XSD_STRING = "http://www.w3.org/2001/XMLSchema#string";

  ShapesBuildRest rest;

  @BeforeEach
  void setUp() {
    rest = new ShapesBuildRest();
    rest.builder = new ShaclShapeBuilder();
  }

  @Test
  void buildReturns200AndTurtleForValidDsl() {
    var body = new ShapeBuildRequestIO(
      SHEPARD + "DataObjectShape",
      SHEPARD + "DataObject",
      false,
      List.of(
        new PropertyIO(
          SHEPARD + "status",
          XSD_STRING,
          1,
          1,
          List.of(new InMemberIO("DRAFT", "LITERAL", null), new InMemberIO("READY", "LITERAL", null)),
          null
        )
      )
    );

    Response r = rest.build(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapeBuildResponseIO) r.getEntity();
    assertThat(io.error()).isNull();
    assertThat(io.shapeIri()).isEqualTo(SHEPARD + "DataObjectShape");
    assertThat(io.shapeGraph()).contains("sh:NodeShape");
    assertThat(io.shapeGraph()).contains(SHEPARD + "status");
    assertThat(io.shapeGraph()).contains("sh:in");
  }

  @Test
  void buildMintsDeterministicIriWhenShapeIriOmitted() {
    var body = new ShapeBuildRequestIO(null, null, false, List.of(new PropertyIO(SHEPARD + "name", null, 1, null, null, null)));

    Response r = rest.build(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapeBuildResponseIO) r.getEntity();
    assertThat(io.shapeIri()).isEqualTo(ShaclShapeBuilder.DEFAULT_SHAPE_IRI);
    assertThat(io.shapeGraph()).contains(ShaclShapeBuilder.DEFAULT_SHAPE_IRI);
  }

  @Test
  void buildEmitsClosedTrueWhenClosed() {
    var body = new ShapeBuildRequestIO(SHEPARD + "S", null, true, List.of());

    Response r = rest.build(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapeBuildResponseIO) r.getEntity();
    assertThat(io.shapeGraph()).contains("sh:closed true");
  }

  @Test
  void buildTreatsNullPropertiesAsEmpty() {
    var body = new ShapeBuildRequestIO(SHEPARD + "S", null, false, null);

    Response r = rest.build(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapeBuildResponseIO) r.getEntity();
    assertThat(io.shapeGraph()).contains("sh:NodeShape");
  }

  @Test
  void buildIriMemberRendersAsAngleBracketIri() {
    var body = new ShapeBuildRequestIO(
      SHEPARD + "S",
      null,
      false,
      List.of(
        new PropertyIO(
          SHEPARD + "material",
          null,
          null,
          null,
          List.of(new InMemberIO(SHEPARD + "CF-PEEK", "IRI", null)),
          null
        )
      )
    );

    Response r = rest.build(body);

    var io = (ShapeBuildResponseIO) r.getEntity();
    assertThat(io.shapeGraph()).contains("<" + SHEPARD + "CF-PEEK>");
  }

  @Test
  void buildReturns400OnNullBody() {
    Response r = rest.build(null);

    assertThat(r.getStatus()).isEqualTo(400);
    var io = (ShapeBuildResponseIO) r.getEntity();
    assertThat(io.error()).isNotNull();
    assertThat(io.shapeGraph()).isNull();
  }

  @Test
  void buildReturns400OnBlankPredicatePath() {
    var body = new ShapeBuildRequestIO(
      SHEPARD + "S",
      null,
      false,
      List.of(new PropertyIO("   ", XSD_STRING, 1, 1, null, null))
    );

    Response r = rest.build(body);

    assertThat(r.getStatus()).isEqualTo(400);
    var io = (ShapeBuildResponseIO) r.getEntity();
    assertThat(io.error()).isNotNull();
    assertThat(io.shapeGraph()).isNull();
  }

  @Test
  void compiledShapeRoundTripsThroughValidator() {
    // The whole point of B6: a shape composed in the editor must actually
    // validate real data. Compile, then run the Jena validator against a
    // conformant + a non-conformant data graph.
    var body = new ShapeBuildRequestIO(
      SHEPARD + "S",
      SHEPARD + "DataObject",
      false,
      List.of(new PropertyIO(SHEPARD + "name", XSD_STRING, 1, 1, null, null))
    );
    var io = (ShapeBuildResponseIO) rest.build(body).getEntity();
    var validator = new JenaShaclValidator();

    var conformant = validator.validate(
      "@prefix s: <" + SHEPARD + "> . s:a a s:DataObject ; s:name \"x\" .",
      io.shapeGraph()
    );
    assertThat(conformant.conforms()).isTrue();

    var missing = validator.validate(
      "@prefix s: <" + SHEPARD + "> . s:b a s:DataObject .",
      io.shapeGraph()
    );
    assertThat(missing.conforms()).isFalse();
    assertThat(missing.findings()).isNotEmpty();
  }

  @Test
  void inMemberKindParsingIsCaseTolerantAndDefaultsToLiteral() {
    var body = new ShapeBuildRequestIO(
      SHEPARD + "S",
      null,
      false,
      List.of(
        new PropertyIO(
          SHEPARD + "p",
          null,
          null,
          null,
          // lower-case "iri", and a null kind (→ LITERAL)
          List.of(new InMemberIO(SHEPARD + "Term", "iri", null), new InMemberIO("plain", null, null)),
          null
        )
      )
    );

    var io = (ShapeBuildResponseIO) rest.build(body).getEntity();
    assertThat(io.shapeGraph()).contains("<" + SHEPARD + "Term>");
    assertThat(io.shapeGraph()).contains("\"plain\"^^<" + XSD_STRING + ">");
  }
}
