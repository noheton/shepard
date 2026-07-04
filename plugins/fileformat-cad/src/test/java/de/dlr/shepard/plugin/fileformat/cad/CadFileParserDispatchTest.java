package de.dlr.shepard.plugin.fileformat.cad;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.spi.fileparser.FileParserPlugin;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Dispatch-path proof for RESEED-FIND-FILEPARSER-DISPATCH.
 *
 * <p>Where {@link CadFileParserPluginTest} unit-tests the parser in isolation, this
 * test exercises the real {@link CadFileParserPlugin} through a faithful copy of the
 * {@code FileParserRegistry.runAll(...)} dispatch loop — the exact selection +
 * annotation-buffer contract the backend uses on singleton file upload. It proves
 * that an uploaded {@code .step} file flows through {@code accepts(...) → parse(...)}
 * and yields the expected {@code urn:shepard:cad:*} annotations anchored on the newly
 * created FileReference's appId.
 *
 * <p>The registry's DB-persistence half (write → reload → attach → save) is covered by
 * {@code de.dlr.shepard.spi.fileparser.FileParserRegistryTest} in the backend module;
 * this test owns the "real parser, real bytes, real predicates" half so the two
 * together cover the full upload-to-annotation chain.
 */
class CadFileParserDispatchTest {

  /** Mirrors the registry's per-annotation buffer record. */
  private record Written(String subjectAppId, String predicate, String value) {}

  /**
   * Faithful copy of {@code FileParserRegistry.runAll} dispatch semantics: only fire a
   * parser when {@code accepts(null, filename)} is true, buffer every emitted
   * annotation, and never let a parser exception escape (fire-and-forget).
   */
  private static List<Written> dispatch(
      FileParserPlugin parser, byte[] bytes, String filename, String fileRefAppId, String parentDoAppId) {
    List<Written> buffer = new ArrayList<>();
    if (bytes == null || filename == null) return buffer;
    try {
      if (!parser.accepts(null, filename)) return buffer;
      FileParserPlugin.ParseContext ctx = new FileParserPlugin.ParseContext() {
        public byte[] bytes() { return bytes; }
        public String filename() { return filename; }
        public Optional<String> parentDataObjectAppId() { return Optional.ofNullable(parentDoAppId); }
        public Optional<String> fileReferenceAppId() { return Optional.ofNullable(fileRefAppId); }
        public FileParserPlugin.AnnotationWriter annotations() {
          return (s, p, v) -> {
            if (s != null && p != null && v != null) buffer.add(new Written(s, p, v));
          };
        }
      };
      parser.parse(ctx);
    } catch (RuntimeException ignored) {
      // fire-and-forget: secondary-write failures never propagate
    }
    return buffer;
  }

  @Test
  void uploadedStepFile_dispatchesToCadParser_andYieldsCadAnnotations() {
    // A minimal but valid ISO-10303-21 (STEP) part, as a .step upload would arrive.
    String step =
        "ISO-10303-21;\n"
            + "HEADER;\n"
            + "FILE_DESCRIPTION(('AFP ply layup'),'2;1');\n"
            + "FILE_NAME('frame.step','2026-06-10T00:00:00',('flo'),('DLR'),'','CATIA V5','');\n"
            + "FILE_SCHEMA(('AP242_MANAGED_MODEL_BASED_3D_ENGINEERING_MIM_LF'));\n"
            + "ENDSEC;\n"
            + "DATA;\n#1=PRODUCT('UPPER_FUSELAGE','Upper Fuselage','',(#2));\nENDSEC;\n"
            + "END-ISO-10303-21;\n";
    byte[] bytes = step.getBytes(StandardCharsets.ISO_8859_1);

    List<Written> emitted =
        dispatch(new CadFileParserPlugin(), bytes, "frame.step", "fileref-appid-7", "do-appid-1");

    // The upload must produce at least the format tag, anchored on the FileReference appId.
    assertThat(emitted).isNotEmpty();
    assertThat(emitted).allMatch(w -> "fileref-appid-7".equals(w.subjectAppId()));
    assertThat(emitted).allMatch(w -> w.predicate().startsWith("urn:shepard:cad:"));
    assertThat(emitted)
        .anyMatch(w -> CadAnnotations.FORMAT.equals(w.predicate()) && "step".equals(w.value()));
    // The STEP schema must be surfaced as a urn:shepard:cad:step_schema annotation.
    assertThat(emitted)
        .anyMatch(
            w ->
                CadAnnotations.STEP_SCHEMA.equals(w.predicate())
                    && w.value().contains("AP242"));
  }

  @Test
  void unrecognisedExtension_dispatchesNothing() {
    List<Written> emitted =
        dispatch(new CadFileParserPlugin(), "x,y,z\n1,2,3\n".getBytes(StandardCharsets.UTF_8),
            "data.csv", "ref", "do");
    assertThat(emitted).isEmpty();
  }
}
