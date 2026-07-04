package de.dlr.shepard.plugin.fileformat.cad;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class StepP21ParserTest {

  private final StepP21Parser parser = new StepP21Parser();

  private static final String MINIMAL_STEP =
      "ISO-10303-21;\n"
          + "HEADER;\n"
          + "FILE_DESCRIPTION(('Test part'),'2;1');\n"
          + "FILE_NAME('test.stp','2024-01-15T10:00:00',('John Doe'),('DLR ZLP'),\n"
          + "  'CATIA V5','','');\n"
          + "FILE_SCHEMA(('AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF { 1 0 10303 442 1 1 1 }'));\n"
          + "ENDSEC;\n"
          + "DATA;\n"
          + "#10=PRODUCT('UPPER_SHELL','Upper fuselage shell',$,(#20));\n"
          + "#30=MATERIAL('CF/LMPAEK',$,$);\n"
          + "ENDSEC;\n"
          + "END-ISO-10303-21;\n";

  @Test
  void accepts_validMagic() {
    byte[] bytes = MINIMAL_STEP.getBytes(StandardCharsets.ISO_8859_1);
    assertThat(parser.accepts(bytes)).isTrue();
  }

  @Test
  void rejects_nonStepBytes() {
    assertThat(parser.accepts("PKnotastepfile".getBytes())).isFalse();
    assertThat(parser.accepts(new byte[0])).isFalse();
    assertThat(parser.accepts(null)).isFalse();
  }

  @Test
  void parse_extractsSchemaAndProduct() {
    byte[] bytes = MINIMAL_STEP.getBytes(StandardCharsets.ISO_8859_1);
    Map<String, String> result = parser.parse(bytes);

    assertThat(result.get(CadAnnotations.STEP_SCHEMA))
        .startsWith("AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF");
    assertThat(result.get(CadAnnotations.PRODUCT_NAME)).isEqualTo("UPPER_SHELL");
    assertThat(result.get(CadAnnotations.MATERIAL)).isEqualTo("CF/LMPAEK");
    assertThat(result.get(CadAnnotations.FORMAT)).isEqualTo("step");
  }

  @Test
  void parse_extractsFileNameFields() {
    byte[] bytes = MINIMAL_STEP.getBytes(StandardCharsets.ISO_8859_1);
    Map<String, String> result = parser.parse(bytes);

    assertThat(result.get(CadAnnotations.CREATED_AT)).isEqualTo("2024-01-15T10:00:00");
    assertThat(result.get(CadAnnotations.ORGANISATION)).isEqualTo("DLR ZLP");
    assertThat(result.get(CadAnnotations.APPLICATION)).isEqualTo("CATIA V5");
  }

  @Test
  void parse_returnsEmptyMapForNonStep() {
    assertThat(parser.parse("not a step file".getBytes())).isEmpty();
    assertThat(parser.parse(null)).isEmpty();
  }

  @Test
  void parse_countsCompositePlies() {
    String stepWithPlies = "ISO-10303-21;\nHEADER;\nFILE_SCHEMA(('AP242'));\nENDSEC;\nDATA;\n"
        + "#1=COMPOSITE_CURVE_SEGMENT($,$,$);\n"
        + "#2=COMPOSITE_CURVE_SEGMENT($,$,$);\n"
        + "#3=COMPOSITE_SURFACE($);\n"
        + "ENDSEC;\nEND-ISO-10303-21;\n";
    Map<String, String> result = parser.parse(stepWithPlies.getBytes(StandardCharsets.ISO_8859_1));
    assertThat(result.get(CadAnnotations.PLY_COUNT)).isEqualTo("3");
  }
}
