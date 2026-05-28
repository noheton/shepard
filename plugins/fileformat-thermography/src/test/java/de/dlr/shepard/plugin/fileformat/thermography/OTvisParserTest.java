package de.dlr.shepard.plugin.fileformat.thermography;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Integration test against the real sample
 * {@code sample_S4_M13_L18_F4.OTvis} fixture. Loads the fixture from
 * the classpath ({@code src/test/resources/}) and asserts the full set
 * of tier-1 annotations lands on a recording {@link FileParserPlugin.AnnotationWriter}.
 */
class OTvisParserTest {

    /**
     * Recording stub of the annotation-writer callback. We keep one
     * structured list (for ordering / counting) and one map (for value
     * assertions on the most-recent value per predicate).
     */
    static final class RecordingWriter implements FileParserPlugin.AnnotationWriter {
        record Entry(String subject, String predicate, String value) {}

        final List<Entry> entries = new ArrayList<>();
        final Map<String, String> byPredicate = new LinkedHashMap<>();
        final Map<String, String> subjectByPredicate = new LinkedHashMap<>();

        @Override
        public void write(String subjectAppId, String predicate, String value) {
            entries.add(new Entry(subjectAppId, predicate, value));
            byPredicate.put(predicate, value);
            subjectByPredicate.put(predicate, subjectAppId);
        }
    }

    private byte[] sampleBytes;

    @BeforeEach
    void loadFixture() throws IOException {
        try (InputStream in = OTvisParserTest.class.getResourceAsStream("/sample_S4_M13_L18_F4.OTvis")) {
            assertThat(in)
                    .as("fixture sample_S4_M13_L18_F4.OTvis must be on the test classpath")
                    .isNotNull();
            sampleBytes = in.readAllBytes();
        }
        assertThat(sampleBytes.length).isGreaterThan(1_000_000);
    }

    @Test
    void acceptsByExtensionRegardlessOfMimeType() {
        OTvisParser parser = new OTvisParser();
        assertThat(parser.accepts("application/octet-stream", "S4_M13_L18_F4.OTvis")).isTrue();
        assertThat(parser.accepts(null, "S4_M13_L18_F4.OTvis")).isTrue();
        assertThat(parser.accepts(null, "S4_M13_L18_F4.otvis")).isTrue();
    }

    @Test
    void acceptsByMimeTypeWhenExtensionMissing() {
        OTvisParser parser = new OTvisParser();
        assertThat(parser.accepts("application/x-tar", "noext")).isTrue();
    }

    @Test
    void rejectsUnrelatedFiles() {
        OTvisParser parser = new OTvisParser();
        assertThat(parser.accepts("application/pdf", "report.pdf")).isFalse();
        assertThat(parser.accepts(null, null)).isFalse();
    }

    @Test
    void emitsAtLeast15AnnotationsForRealFixture() {
        RecordingWriter writer = new RecordingWriter();
        FileParserPlugin.ParseContext ctx = ctx(sampleBytes, "S4_M13_L18_F4.OTvis",
                Optional.of("do-appid-7777"),
                Optional.of("fileref-appid-9999"),
                writer);

        int count = new OTvisParser().parse(ctx);

        assertThat(count).isGreaterThanOrEqualTo(15);
        assertThat(writer.entries).hasSizeGreaterThanOrEqualTo(15);
    }

    @Test
    void emitsAllExpectedThermographyAnnotationsFromRealFixture() {
        RecordingWriter writer = new RecordingWriter();
        FileParserPlugin.ParseContext ctx = ctx(sampleBytes, "S4_M13_L18_F4.OTvis",
                Optional.of("do-appid-7777"),
                Optional.of("fileref-appid-9999"),
                writer);

        new OTvisParser().parse(ctx);

        assertThat(writer.byPredicate)
                .containsEntry(ThermographyAnnotations.FRAME_RATE_HZ, "30")
                .containsEntry(ThermographyAnnotations.INTEGRATION_TIME_S, "0.007")
                .containsEntry(ThermographyAnnotations.EXCITATION_DEVICE, "halogen")
                .containsEntry(ThermographyAnnotations.EXCITATION_FREQUENCY_HZ, "0.015")
                .containsEntry(ThermographyAnnotations.EXCITATION_AMPLITUDE_PCT, "70.00")
                .containsEntry(ThermographyAnnotations.EXCITATION_SIGNAL_TYPE, "sine")
                .containsEntry(ThermographyAnnotations.RECORDING_TYPE, "evaluation")
                .containsEntry(ThermographyAnnotations.RESOLUTION, "1024x768")
                .containsEntry(ThermographyAnnotations.CONDITIONING_PERIODS, "1")
                .containsEntry(ThermographyAnnotations.ACQUISITION_PERIODS, "2")
                .containsEntry(ThermographyAnnotations.CAMPAIGN, "MFFD")
                .containsEntry(ThermographyAnnotations.MODULE_NAME, "OTvis")
                .containsEntry(ThermographyAnnotations.CREATING_VERSION, "7.0.425.8903");
        assertThat(writer.byPredicate.get(ThermographyAnnotations.CREATED_AT))
                .as("CreationDate normalised to ISO-8601 UTC")
                .startsWith("2023-07-02T06:55:41.414");
    }

    @Test
    void emitsMffdGridAnnotationsFromFilename() {
        RecordingWriter writer = new RecordingWriter();
        FileParserPlugin.ParseContext ctx = ctx(sampleBytes, "S4_M13_L18_F4.OTvis",
                Optional.of("do-appid-7777"),
                Optional.of("fileref-appid-9999"),
                writer);

        new OTvisParser().parse(ctx);

        assertThat(writer.byPredicate)
                .containsEntry(ThermographyAnnotations.MFFD_SECTION, "S4")
                .containsEntry(ThermographyAnnotations.MFFD_MODULE,  "M13")
                .containsEntry(ThermographyAnnotations.MFFD_LAYER,   "L18")
                .containsEntry(ThermographyAnnotations.MFFD_FRAME,   "F4");

        // Grid annotations are anchored on the parent DataObject, not the FileReference.
        assertThat(writer.subjectByPredicate.get(ThermographyAnnotations.MFFD_SECTION))
                .isEqualTo("do-appid-7777");
        // Acquisition annotations are anchored on the FileReference.
        assertThat(writer.subjectByPredicate.get(ThermographyAnnotations.FRAME_RATE_HZ))
                .isEqualTo("fileref-appid-9999");
    }

    @Test
    void skipsMffdGridWhenFilenamePatternUnmatched() {
        RecordingWriter writer = new RecordingWriter();
        FileParserPlugin.ParseContext ctx = ctx(sampleBytes, "non-matching-name.OTvis",
                Optional.of("do-appid-7777"),
                Optional.of("fileref-appid-9999"),
                writer);

        new OTvisParser().parse(ctx);

        assertThat(writer.byPredicate).doesNotContainKey(ThermographyAnnotations.MFFD_SECTION);
        // Acquisition metadata still emits.
        assertThat(writer.byPredicate).containsKey(ThermographyAnnotations.FRAME_RATE_HZ);
    }

    @Test
    void emptyBytesEmitNothing() {
        RecordingWriter writer = new RecordingWriter();
        FileParserPlugin.ParseContext ctx = ctx(new byte[0], "S4_M13_L18_F4.OTvis",
                Optional.of("do-appid-7777"),
                Optional.of("fileref-appid-9999"),
                writer);
        assertThat(new OTvisParser().parse(ctx)).isZero();
        assertThat(writer.entries).isEmpty();
    }

    @Test
    void corruptArchiveDoesNotThrow() {
        byte[] garbage = "not a tar file at all".getBytes();
        RecordingWriter writer = new RecordingWriter();
        FileParserPlugin.ParseContext ctx = ctx(garbage, "S4_M13_L18_F4.OTvis",
                Optional.of("do-appid-7777"),
                Optional.of("fileref-appid-9999"),
                writer);
        // Filename-derived MFFD grid annotations still emit even when the
        // tar payload is unreadable — partial success is the design.
        int count = new OTvisParser().parse(ctx);
        assertThat(count).isEqualTo(4);
        assertThat(writer.byPredicate).containsKey(ThermographyAnnotations.MFFD_SECTION);
    }

    @Test
    void emitsNothingWhenNoSubjectAvailable() {
        RecordingWriter writer = new RecordingWriter();
        FileParserPlugin.ParseContext ctx = ctx(sampleBytes, "S4_M13_L18_F4.OTvis",
                Optional.empty(), Optional.empty(), writer);
        assertThat(new OTvisParser().parse(ctx)).isZero();
        assertThat(writer.entries).isEmpty();
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private static FileParserPlugin.ParseContext ctx(
            byte[] bytes, String filename,
            Optional<String> doId, Optional<String> refId,
            FileParserPlugin.AnnotationWriter writer) {
        return new FileParserPlugin.ParseContext() {
            @Override public byte[] bytes() { return bytes; }
            @Override public String filename() { return filename; }
            @Override public Optional<String> parentDataObjectAppId() { return doId; }
            @Override public Optional<String> fileReferenceAppId() { return refId; }
            @Override public FileParserPlugin.AnnotationWriter annotations() { return writer; }
        };
    }
}
