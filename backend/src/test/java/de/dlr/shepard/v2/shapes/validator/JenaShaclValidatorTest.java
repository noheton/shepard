package de.dlr.shepard.v2.shapes.validator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JenaShaclValidatorTest {

  JenaShaclValidator validator;

  @BeforeEach
  void setUp() {
    validator = new JenaShaclValidator();
  }

  // ─── Conformant cases ──────────────────────────────────────────────

  @Test
  void conformantDataReportsConformsTrue() {
    String data =
      "@prefix ex: <http://example.org/> .\n" +
      "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
      "ex:Alice a ex:Person ;\n" +
      "  ex:name \"Alice\"^^xsd:string .\n";
    String shape =
      "@prefix sh: <http://www.w3.org/ns/shacl#> .\n" +
      "@prefix ex: <http://example.org/> .\n" +
      "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
      "ex:PersonShape a sh:NodeShape ;\n" +
      "  sh:targetClass ex:Person ;\n" +
      "  sh:property [ sh:path ex:name ; sh:minCount 1 ; sh:datatype xsd:string ] .\n";

    var report = validator.validate(data, shape);

    assertThat(report.conforms()).isTrue();
    assertThat(report.parseError()).isNull();
    assertThat(report.engineError()).isNull();
    assertThat(report.findings()).isEmpty();
  }

  @Test
  void emptyShapeGraphIsConformant() {
    String data = "@prefix ex: <http://example.org/> . ex:Bob a ex:Person .";
    String shape = "@prefix sh: <http://www.w3.org/ns/shacl#> .";

    var report = validator.validate(data, shape);

    assertThat(report.conforms()).isTrue();
    assertThat(report.findings()).isEmpty();
  }

  // ─── Non-conformant cases ─────────────────────────────────────────

  @Test
  void missingRequiredPropertyProducesFinding() {
    String data = "@prefix ex: <http://example.org/> . ex:Carol a ex:Person .";
    String shape =
      "@prefix sh: <http://www.w3.org/ns/shacl#> .\n" +
      "@prefix ex: <http://example.org/> .\n" +
      "ex:PersonShape a sh:NodeShape ;\n" +
      "  sh:targetClass ex:Person ;\n" +
      "  sh:property [ sh:path ex:name ; sh:minCount 1 ;\n" +
      "                sh:message \"name is required\" ] .\n";

    var report = validator.validate(data, shape);

    assertThat(report.conforms()).isFalse();
    assertThat(report.findings()).hasSize(1);
    var f = report.findings().get(0);
    assertThat(f.focusNode()).isEqualTo("http://example.org/Carol");
    assertThat(f.severity()).isEqualTo("Violation");
    assertThat(f.message()).isEqualTo("name is required");
  }

  @Test
  void violatingPatternIsReported() {
    String data =
      "@prefix ex: <http://example.org/> .\n" +
      "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
      "ex:Bad a ex:NCR ; ex:ncrId \"NOT-A-NCR\"^^xsd:string .\n";
    String shape =
      "@prefix sh: <http://www.w3.org/ns/shacl#> .\n" +
      "@prefix ex: <http://example.org/> .\n" +
      "@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n" +
      "ex:NCRShape a sh:NodeShape ; sh:targetClass ex:NCR ;\n" +
      "  sh:property [ sh:path ex:ncrId ; sh:datatype xsd:string ;\n" +
      "                sh:pattern \"^NCR-[0-9]{4}-[0-9]{4,}$\" ] .\n";

    var report = validator.validate(data, shape);

    assertThat(report.conforms()).isFalse();
    assertThat(report.findings()).hasSize(1);
    assertThat(report.findings().get(0).value()).isEqualTo("NOT-A-NCR");
  }

  // ─── Failure modes ────────────────────────────────────────────────

  @Test
  void malformedDataTurtleSurfacesParseError() {
    var report = validator.validate("this is not turtle", "");

    assertThat(report.conforms()).isFalse();
    assertThat(report.parseError()).contains("data graph parse error");
    assertThat(report.engineError()).isNull();
    assertThat(report.findings()).isEmpty();
  }

  @Test
  void malformedShapeTurtleSurfacesParseError() {
    var report = validator.validate("@prefix ex: <http://example.org/> . ex:x a ex:Y .", "garbage");

    assertThat(report.conforms()).isFalse();
    assertThat(report.parseError()).contains("shape graph parse error");
  }

  @Test
  void nullInputsAreRejected() {
    assertThat(validator.validate(null, "").parseError()).isNotNull();
    assertThat(validator.validate("", null).parseError()).isNotNull();
  }
}
