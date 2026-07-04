package de.dlr.shepard.plugin.fileformat.cad;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.spi.fileparser.FileParserPlugin;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CadFileParserPluginTest {

  private final CadFileParserPlugin plugin = new CadFileParserPlugin();

  @Test
  void accepts_knownExtensions() {
    assertThat(plugin.accepts("application/octet-stream", "part.step")).isTrue();
    assertThat(plugin.accepts("application/octet-stream", "assembly.stp")).isTrue();
    assertThat(plugin.accepts("application/octet-stream", "model.3dxml")).isTrue();
    assertThat(plugin.accepts("application/octet-stream", "mesh.obj")).isTrue();
    assertThat(plugin.accepts("application/octet-stream", "part.jt")).isTrue();
  }

  @Test
  void accepts_unknownExtensionFalse() {
    assertThat(plugin.accepts("application/octet-stream", "data.csv")).isFalse();
    assertThat(plugin.accepts("application/octet-stream", null)).isFalse();
  }

  @Test
  void parse_stepFile_writesAnnotations() {
    String step = "ISO-10303-21;\nHEADER;\nFILE_SCHEMA(('AP242'));\nENDSEC;\n"
        + "DATA;\n#1=PRODUCT('PART','Test');\nENDSEC;\nEND-ISO-10303-21;\n";
    byte[] bytes = step.getBytes(StandardCharsets.ISO_8859_1);

    List<String[]> written = new ArrayList<>();
    FileParserPlugin.ParseContext ctx = mockCtx(bytes, "part.step", "ref-appid-123", written);

    int count = plugin.parse(ctx);
    assertThat(count).isGreaterThan(0);
    assertThat(written).anyMatch(a -> a[0].equals("ref-appid-123")
        && a[1].equals(CadAnnotations.FORMAT)
        && a[2].equals("step"));
  }

  @Test
  void parse_jtFile_writesFormatAnnotation() {
    byte[] jtBytes = {'#', '!', 'J', 'T', 0x00, 0x01, 0x02};
    List<String[]> written = new ArrayList<>();
    FileParserPlugin.ParseContext ctx = mockCtx(jtBytes, "model.jt", "ref-jt", written);

    int count = plugin.parse(ctx);
    assertThat(count).isEqualTo(1);
    assertThat(written.get(0)).containsExactly("ref-jt", CadAnnotations.FORMAT, "jt");
  }

  @Test
  void parse_returnsZeroWhenNoAnchor() {
    byte[] bytes = "ISO-10303-21;\n".getBytes();
    FileParserPlugin.ParseContext ctx = new FileParserPlugin.ParseContext() {
      public byte[] bytes() { return bytes; }
      public String filename() { return "test.step"; }
      public Optional<String> parentDataObjectAppId() { return Optional.empty(); }
      public Optional<String> fileReferenceAppId() { return Optional.empty(); }
      public FileParserPlugin.AnnotationWriter annotations() {
        return (s, p, v) -> {};
      }
    };
    assertThat(plugin.parse(ctx)).isEqualTo(0);
  }

  private FileParserPlugin.ParseContext mockCtx(
      byte[] bytes, String filename, String refAppId, List<String[]> sink) {
    return new FileParserPlugin.ParseContext() {
      public byte[] bytes() { return bytes; }
      public String filename() { return filename; }
      public Optional<String> parentDataObjectAppId() { return Optional.of("do-123"); }
      public Optional<String> fileReferenceAppId() { return Optional.of(refAppId); }
      public FileParserPlugin.AnnotationWriter annotations() {
        return (s, p, v) -> sink.add(new String[]{s, p, v});
      }
    };
  }
}
