package de.dlr.shepard.v2.shapes.io;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.v2.shapes.validator.JenaShaclValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShapeValidationReportIOTest {

  @Test
  void conformantReportMapsThrough() {
    var src = new JenaShaclValidator.Report(true, null, null, List.of());

    var io = ShapeValidationReportIO.from(src);

    assertThat(io.conforms()).isTrue();
    assertThat(io.parseError()).isNull();
    assertThat(io.engineError()).isNull();
    assertThat(io.findings()).isEmpty();
  }

  @Test
  void parseErrorMapsThrough() {
    var src = JenaShaclValidator.Report.parseError("bad turtle");

    var io = ShapeValidationReportIO.from(src);

    assertThat(io.conforms()).isFalse();
    assertThat(io.parseError()).isEqualTo("bad turtle");
  }

  @Test
  void findingsMapToFindingIO() {
    var f = new JenaShaclValidator.Finding(
      "http://example.org/Alice",
      "http://example.org/name",
      "<missing>",
      "Violation",
      "name required"
    );
    var src = new JenaShaclValidator.Report(false, null, null, List.of(f));

    var io = ShapeValidationReportIO.from(src);

    assertThat(io.findings()).hasSize(1);
    var fio = io.findings().get(0);
    assertThat(fio.focusNode()).isEqualTo("http://example.org/Alice");
    assertThat(fio.resultPath()).isEqualTo("http://example.org/name");
    assertThat(fio.value()).isEqualTo("<missing>");
    assertThat(fio.severity()).isEqualTo("Violation");
    assertThat(fio.message()).isEqualTo("name required");
  }
}
