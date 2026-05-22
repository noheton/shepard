package de.dlr.shepard.v2.shapes.resources;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.v2.shapes.io.ShapeValidationReportIO;
import de.dlr.shepard.v2.shapes.io.ShapeValidationRequestIO;
import de.dlr.shepard.v2.shapes.validator.JenaShaclValidator;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShapesValidateRestTest {

  ShapesValidateRest rest;

  @BeforeEach
  void setUp() {
    rest = new ShapesValidateRest();
    rest.validator = new JenaShaclValidator();
  }

  @Test
  void validateReturns200WithConformsTrueOnGoodPayload() {
    var body = new ShapeValidationRequestIO(
      "@prefix ex: <http://example.org/> . ex:A a ex:Person ; ex:name \"Ann\" .",
      "@prefix sh: <http://www.w3.org/ns/shacl#> ." +
      "@prefix ex: <http://example.org/> ." +
      "ex:S a sh:NodeShape ; sh:targetClass ex:Person ;" +
      "  sh:property [ sh:path ex:name ; sh:minCount 1 ] ."
    );

    Response r = rest.validate(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapeValidationReportIO) r.getEntity();
    assertThat(io.conforms()).isTrue();
  }

  @Test
  void validateReturns200WithConformsFalseOnNonConformantPayload() {
    var body = new ShapeValidationRequestIO(
      "@prefix ex: <http://example.org/> . ex:B a ex:Person .",
      "@prefix sh: <http://www.w3.org/ns/shacl#> ." +
      "@prefix ex: <http://example.org/> ." +
      "ex:S a sh:NodeShape ; sh:targetClass ex:Person ;" +
      "  sh:property [ sh:path ex:name ; sh:minCount 1 ] ."
    );

    Response r = rest.validate(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapeValidationReportIO) r.getEntity();
    assertThat(io.conforms()).isFalse();
    assertThat(io.findings()).isNotEmpty();
  }

  @Test
  void validateReturns200WithParseErrorOnBadTurtle() {
    // Per the contract documented on the endpoint: bad Turtle is a
    // 200 with parseError set, NOT a 400. Lets the MCP tool branch
    // on parseError independently from request-malformed.
    var body = new ShapeValidationRequestIO("not turtle at all", "@prefix sh: <http://www.w3.org/ns/shacl#> .");

    Response r = rest.validate(body);

    assertThat(r.getStatus()).isEqualTo(200);
    var io = (ShapeValidationReportIO) r.getEntity();
    assertThat(io.conforms()).isFalse();
    assertThat(io.parseError()).isNotNull();
  }

  @Test
  void validateReturns400OnNullBody() {
    Response r = rest.validate(null);

    assertThat(r.getStatus()).isEqualTo(400);
  }
}
