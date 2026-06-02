package de.dlr.shepard.plugin.fileformat.svdx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SvdxManifestParserTest {

    private static final String FREF_APPID = "01990000-0000-7000-8000-000000000001";
    private static final String DO_APPID = "01990000-0000-7000-8000-0000000000d0";

    @Test
    void acceptsExtensionAndMimeType() {
        SvdxManifestParser p = new SvdxManifestParser();
        assertThat(p.accepts(null, "Scope Project_AutoSave_18_26_04.svdx")).isTrue();
        assertThat(p.accepts(null, "trace.SVDX")).isTrue();
        assertThat(p.accepts("application/vnd.beckhoff.scope+svdx", "unknown")).isTrue();
        assertThat(p.accepts("text/plain", "doc.txt")).isFalse();
        assertThat(p.accepts(null, "image.png")).isFalse();
        assertThat(p.accepts(null, null)).isFalse();
    }

    @Test
    void emitsManifestAnnotationsHappyPath() throws Exception {
        byte[] svdx = SyntheticSvdxBuilder.build(
            4096, SyntheticSvdxBuilder.exampleXmlTwoChannels());

        RecordingWriter w = new RecordingWriter();
        FixtureContext ctx = new FixtureContext(svdx, "trace.svdx", DO_APPID, FREF_APPID, w, List.of());

        int n = new SvdxManifestParser().parse(ctx);

        assertThat(n).isGreaterThanOrEqualTo(10);
        // formatVersion always first.
        assertThat(w.predicates()).contains(SvdxAnnotations.FORMAT_VERSION);
        assertThat(w.valueFor(SvdxAnnotations.FORMAT_VERSION)).startsWith("0x");
        assertThat(w.valueFor(SvdxAnnotations.PROJECT_GUID))
            .isEqualTo("61ededc3-4d5a-4502-823a-263c661a692f");
        assertThat(w.valueFor(SvdxAnnotations.DATA_POOL_GUID))
            .isEqualTo("ec7a812f-62f8-497d-9a0d-92f60425dd87");
        assertThat(w.valueFor(SvdxAnnotations.CHANNEL_COUNT)).isEqualTo("2");
        assertThat(w.allValuesFor(SvdxAnnotations.SYMBOL_NAME))
            .contains("GVL_IO_US_Endeffektor.aTemperatureAnalogIntput1",
                      "RobotData.rRoboPosA");
        assertThat(w.allValuesFor(SvdxAnnotations.DATA_TYPE))
            .containsExactlyInAnyOrder("INT16", "REAL32");
        // Subject is the FileReference appId on every write.
        assertThat(w.subjects()).containsOnly(FREF_APPID);
    }

    @Test
    void emitsCompanionCsvWhenSiblingPresent() throws Exception {
        byte[] svdx = SyntheticSvdxBuilder.build(
            2048, SyntheticSvdxBuilder.exampleXmlTwoChannels());
        String csvAppId = "01990000-0000-7000-8000-0000000000ff";

        RecordingWriter w = new RecordingWriter();
        FixtureContext ctx = new FixtureContext(svdx,
            "Scope Project_AutoSave_19_04_29.svdx",
            DO_APPID, FREF_APPID, w,
            List.of(new FileParserPlugin.SiblingFile(
                "Scope Project_AutoSave_19_04_29.csv", csvAppId)));

        new SvdxManifestParser().parse(ctx);

        assertThat(w.valueFor(SvdxAnnotations.COMPANION_CSV)).isEqualTo(csvAppId);
    }

    @Test
    void prefersParsedCsvWhenBothSiblingsPresent() throws Exception {
        byte[] svdx = SyntheticSvdxBuilder.build(
            2048, SyntheticSvdxBuilder.exampleXmlEmpty());
        String exactAppId = "01990000-0000-7000-8000-aaaaaaaaaaaa";
        String parsedAppId = "01990000-0000-7000-8000-bbbbbbbbbbbb";

        RecordingWriter w = new RecordingWriter();
        FixtureContext ctx = new FixtureContext(svdx,
            "Scope Project_AutoSave_19_04_29.svdx",
            DO_APPID, FREF_APPID, w,
            List.of(
                new FileParserPlugin.SiblingFile(
                    "Scope Project_AutoSave_19_04_29.csv", exactAppId),
                new FileParserPlugin.SiblingFile(
                    "Scope Project_AutoSave_19_04_29_parsed.csv", parsedAppId)
            ));

        new SvdxManifestParser().parse(ctx);

        assertThat(w.valueFor(SvdxAnnotations.COMPANION_CSV)).isEqualTo(parsedAppId);
    }

    @Test
    void omitsCompanionCsvWhenNoSibling() throws Exception {
        byte[] svdx = SyntheticSvdxBuilder.build(
            128, SyntheticSvdxBuilder.exampleXmlEmpty());
        RecordingWriter w = new RecordingWriter();
        FixtureContext ctx = new FixtureContext(svdx, "lonely.svdx", DO_APPID, FREF_APPID, w, List.of());

        new SvdxManifestParser().parse(ctx);

        assertThat(w.predicates()).doesNotContain(SvdxAnnotations.COMPANION_CSV);
    }

    @Test
    void emitsOnlyFormatVersionWhenManifestCorrupt() throws Exception {
        // Build a header that decodes, but the "XML" is non-parseable garbage.
        byte[] svdx = SyntheticSvdxBuilder.build(64, "<<<garbage not xml>>>");
        RecordingWriter w = new RecordingWriter();
        FixtureContext ctx = new FixtureContext(svdx, "trace.svdx", DO_APPID, FREF_APPID, w, List.of());

        int n = new SvdxManifestParser().parse(ctx);

        // At minimum the formatVersion annotation is emitted; manifest
        // parse failure is logged and swallowed.
        assertThat(n).isGreaterThanOrEqualTo(1);
        assertThat(w.predicates()).contains(SvdxAnnotations.FORMAT_VERSION);
        assertThat(w.predicates()).doesNotContain(SvdxAnnotations.CHANNEL_COUNT);
    }

    @Test
    void returnsZeroOnTooShortFile() {
        byte[] tiny = new byte[10];
        RecordingWriter w = new RecordingWriter();
        FixtureContext ctx = new FixtureContext(tiny, "t.svdx", DO_APPID, FREF_APPID, w, List.of());

        assertThat(new SvdxManifestParser().parse(ctx)).isZero();
        assertThat(w.predicates()).isEmpty();
    }

    @Test
    void returnsZeroOnNonSvdxMagic() throws Exception {
        // Bytes 9..11 don't match the 0x96 0x0c 0x00 marker.
        byte[] bogus = new byte[1024];
        bogus[9] = 0x11; bogus[10] = 0x22; bogus[11] = 0x33;
        RecordingWriter w = new RecordingWriter();
        FixtureContext ctx = new FixtureContext(bogus, "weird.svdx", DO_APPID, FREF_APPID, w, List.of());

        assertThat(new SvdxManifestParser().parse(ctx)).isZero();
        assertThat(w.predicates()).isEmpty();
    }

    @Test
    void returnsZeroWhenNoFileReferenceAppIdProvided() throws Exception {
        byte[] svdx = SyntheticSvdxBuilder.build(64, SyntheticSvdxBuilder.exampleXmlEmpty());
        RecordingWriter w = new RecordingWriter();
        FixtureContext ctx = new FixtureContext(svdx, "x.svdx", DO_APPID, null, w, List.of());

        assertThat(new SvdxManifestParser().parse(ctx)).isZero();
        assertThat(w.predicates()).isEmpty();
    }

    @Test
    void emitsChannelCountZeroForEmptyProject() throws Exception {
        byte[] svdx = SyntheticSvdxBuilder.build(128, SyntheticSvdxBuilder.exampleXmlEmpty());
        RecordingWriter w = new RecordingWriter();
        FixtureContext ctx = new FixtureContext(svdx, "empty.svdx", DO_APPID, FREF_APPID, w, List.of());

        new SvdxManifestParser().parse(ctx);

        assertThat(w.valueFor(SvdxAnnotations.CHANNEL_COUNT)).isEqualTo("0");
        assertThat(w.valueFor(SvdxAnnotations.PROJECT_NAME)).isEqualTo("Empty Project");
    }

    @Test
    void idempotentAcrossRepeatedParses() throws Exception {
        byte[] svdx = SyntheticSvdxBuilder.build(
            2048, SyntheticSvdxBuilder.exampleXmlTwoChannels());

        RecordingWriter w1 = new RecordingWriter();
        RecordingWriter w2 = new RecordingWriter();
        SvdxManifestParser parser = new SvdxManifestParser();
        FixtureContext c1 = new FixtureContext(svdx, "x.svdx", DO_APPID, FREF_APPID, w1, List.of());
        FixtureContext c2 = new FixtureContext(svdx, "x.svdx", DO_APPID, FREF_APPID, w2, List.of());

        int n1 = parser.parse(c1);
        int n2 = parser.parse(c2);

        assertThat(n1).isEqualTo(n2);
        assertThat(w1.predicates()).containsExactlyElementsOf(w2.predicates());
    }

    // ---------------------------------------------------------------- helpers

    static final class RecordingWriter implements FileParserPlugin.AnnotationWriter {
        record Entry(String subject, String predicate, String value) {}
        private final List<Entry> entries = new ArrayList<>();

        @Override
        public void write(String subject, String predicate, String value) {
            entries.add(new Entry(subject, predicate, value));
        }

        List<String> predicates() {
            return entries.stream().map(Entry::predicate).toList();
        }
        List<String> subjects() {
            return entries.stream().map(Entry::subject).distinct().toList();
        }
        String valueFor(String predicate) {
            return entries.stream()
                .filter(e -> e.predicate().equals(predicate))
                .map(Entry::value)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no value for " + predicate));
        }
        List<String> allValuesFor(String predicate) {
            return entries.stream()
                .filter(e -> e.predicate().equals(predicate))
                .map(Entry::value)
                .toList();
        }
    }

    static final class FixtureContext implements FileParserPlugin.ParseContext {
        private final byte[] bytes;
        private final String filename;
        private final String doAppId;
        private final String frefAppId;
        private final FileParserPlugin.AnnotationWriter writer;
        private final List<FileParserPlugin.SiblingFile> siblings;

        FixtureContext(byte[] bytes, String filename, String doAppId, String frefAppId,
                       FileParserPlugin.AnnotationWriter writer,
                       List<FileParserPlugin.SiblingFile> siblings) {
            this.bytes = bytes;
            this.filename = filename;
            this.doAppId = doAppId;
            this.frefAppId = frefAppId;
            this.writer = writer;
            this.siblings = siblings;
        }
        @Override public byte[] bytes() { return bytes; }
        @Override public String filename() { return filename; }
        @Override public Optional<String> parentDataObjectAppId() { return Optional.ofNullable(doAppId); }
        @Override public Optional<String> fileReferenceAppId() { return Optional.ofNullable(frefAppId); }
        @Override public List<FileParserPlugin.SiblingFile> siblingFiles() { return siblings; }
        @Override public FileParserPlugin.AnnotationWriter annotations() { return writer; }
    }
}
