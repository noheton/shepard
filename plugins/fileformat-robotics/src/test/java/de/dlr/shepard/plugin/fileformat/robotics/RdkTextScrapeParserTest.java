package de.dlr.shepard.plugin.fileformat.robotics;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.spi.fileparser.FileParserPlugin;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link RdkTextScrapeParser} driven by synthetic
 * fixtures. The real-file acceptance check lives in
 * {@link RdkTextScrapeParserMFZFixtureTest} (opt-in via env var).
 */
class RdkTextScrapeParserTest {

    static final class RecordingWriter implements FileParserPlugin.AnnotationWriter {
        record Entry(String subject, String predicate, String value) {}

        final List<Entry> entries = new ArrayList<>();
        final Map<String, String> singleByPredicate = new LinkedHashMap<>();
        /** All values per predicate (for multi-valued ones). */
        final Map<String, List<String>> allByPredicate = new LinkedHashMap<>();

        @Override
        public void write(String subjectAppId, String predicate, String value) {
            entries.add(new Entry(subjectAppId, predicate, value));
            singleByPredicate.put(predicate, value);
            allByPredicate.computeIfAbsent(predicate, k -> new ArrayList<>()).add(value);
        }
    }

    // ── accepts() ─────────────────────────────────────────────────────────

    @Test
    void acceptsRdkExtensionCaseInsensitively() {
        RdkTextScrapeParser parser = new RdkTextScrapeParser();
        assertThat(parser.accepts("application/octet-stream", "MFZ.rdk")).isTrue();
        assertThat(parser.accepts(null, "Station.RDK")).isTrue();
        assertThat(parser.accepts(null, "cell.rDk")).isTrue();
    }

    @Test
    void rejectsUnrelatedFiles() {
        RdkTextScrapeParser parser = new RdkTextScrapeParser();
        assertThat(parser.accepts("application/pdf", "report.pdf")).isFalse();
        assertThat(parser.accepts(null, null)).isFalse();
        assertThat(parser.accepts(null, "station.xit")).isFalse();
    }

    // ── full synthetic happy path ─────────────────────────────────────────

    @Test
    void emitsAllEightPredicatesAgainstSyntheticFixture() {
        // Build a fixture matching the MFZ.rdk string layout: the
        // VCP_SOURCE_DIRECTORY marker immediately precedes the program
        // source path; the same 127.0.0.1 endpoint appears twice (the
        // real file repeats it across sections); 3 .dae + 2 .stp refs.
        byte[] bytes = SyntheticRdkBuilder.build(Arrays.asList(
                "Station File 01.01",
                "lvl",
                "MFZ",
                "VCP_PRIMARY_ROBOT",
                "R20",
                "VCP_SOURCE_DIRECTORY",
                "D:/MFFD/RoboDK/Ply 1-15",
                "VCP_RETRACT",
                "VCP_SECONDARY_ROBOT",
                "5.5.3",
                "WIN64",
                "FrameChkptStart",
                "World",
                "D:/git/RoboticsAPI_common/de.dlr.bt.au.robotcell.mfz/resources/models/MFZ_Grundkonstruktion.dae",
                "D:/git/RoboticsAPI_common/de.dlr.bt.au.robotcell.mfz/resources/models/MFZ_Mittelschiene.dae",
                "D:/git/RoboticsAPI_common/de.dlr.bt.au.robotcell.mfz/resources/models/MFZ_Mittelschiene_Schlitten1.dae",
                "127.0.0.1",
                "R20_MFZDriver",
                "127.0.0.1", // duplicate — de-duplicates as single API endpoint
                "D:/MFFD/CAD/MTLH_MultiTape Schneideinheit.CATProduct.stp",
                "D:/MFFD/CAD/Vermessung_GRU.CATProduct.stp",
                "Base_measured",
                "Station File End"
        ));

        RecordingWriter writer = new RecordingWriter();
        FileParserPlugin.ParseContext ctx = ctx(bytes, "MFZ.rdk",
                Optional.of("do-appid-AAAA"),
                Optional.of("fileref-appid-BBBB"),
                List.of(),
                writer);

        int emitted = new RdkTextScrapeParser().parse(ctx);

        // 4 single-valued + 1 marker-anchored + 3 cad + 2 step = 10
        assertThat(emitted).isEqualTo(10);
        assertThat(writer.singleByPredicate)
                .containsEntry(RdkAnnotations.APP_VERSION, "5.5.3")
                .containsEntry(RdkAnnotations.PLATFORM, "WIN64")
                .containsEntry(RdkAnnotations.PROGRAM_SOURCE, "D:/MFFD/RoboDK/Ply 1-15")
                .containsEntry(RdkAnnotations.API_ENDPOINT, "127.0.0.1")
                .containsEntry(RdkAnnotations.ROBOT_CONTROLLER, "R20_MFZDriver");

        assertThat(writer.allByPredicate.get(RdkAnnotations.CAD_REF))
                .containsExactly(
                        "D:/git/RoboticsAPI_common/de.dlr.bt.au.robotcell.mfz/resources/models/MFZ_Grundkonstruktion.dae",
                        "D:/git/RoboticsAPI_common/de.dlr.bt.au.robotcell.mfz/resources/models/MFZ_Mittelschiene.dae",
                        "D:/git/RoboticsAPI_common/de.dlr.bt.au.robotcell.mfz/resources/models/MFZ_Mittelschiene_Schlitten1.dae");
        assertThat(writer.allByPredicate.get(RdkAnnotations.STEP_REF))
                .containsExactly(
                        "D:/MFFD/CAD/MTLH_MultiTape Schneideinheit.CATProduct.stp",
                        "D:/MFFD/CAD/Vermessung_GRU.CATProduct.stp");

        // All annotations are anchored on the FileReference subject.
        for (RecordingWriter.Entry e : writer.entries) {
            assertThat(e.subject()).isEqualTo("fileref-appid-BBBB");
        }
    }

    // ── classifier edge cases ─────────────────────────────────────────────

    @Test
    void rejectsVcpStringsAsRobotController() {
        // VCP_PRIMARY_ROBOT ends in ROBOT not Driver, and would not
        // match; but VCP_PRIMARY_DRIVER would — assert the leading
        // VCP_ guard rejects it.
        byte[] bytes = SyntheticRdkBuilder.build(List.of(
                "VCP_PRIMARY_DRIVER", "R20_MFZDriver"
        ));
        RecordingWriter writer = new RecordingWriter();
        new RdkTextScrapeParser().parse(ctx(bytes, "x.rdk",
                Optional.empty(), Optional.of("ref"), List.of(), writer));
        assertThat(writer.singleByPredicate)
                .containsEntry(RdkAnnotations.ROBOT_CONTROLLER, "R20_MFZDriver");
    }

    @Test
    void acceptsBothStpAndStepExtensions() {
        byte[] bytes = SyntheticRdkBuilder.build(List.of(
                "geometry.stp", "lower-case.step", "Mixed.STP"
        ));
        RecordingWriter writer = new RecordingWriter();
        new RdkTextScrapeParser().parse(ctx(bytes, "x.rdk",
                Optional.empty(), Optional.of("ref"), List.of(), writer));
        assertThat(writer.allByPredicate.get(RdkAnnotations.STEP_REF))
                .containsExactly("geometry.stp", "lower-case.step", "Mixed.STP");
    }

    @Test
    void acceptsApiEndpointWithPort() {
        byte[] bytes = SyntheticRdkBuilder.build(List.of(
                "192.168.10.5:20500"
        ));
        RecordingWriter writer = new RecordingWriter();
        new RdkTextScrapeParser().parse(ctx(bytes, "x.rdk",
                Optional.empty(), Optional.of("ref"), List.of(), writer));
        assertThat(writer.singleByPredicate)
                .containsEntry(RdkAnnotations.API_ENDPOINT, "192.168.10.5:20500");
    }

    @Test
    void programSourceFallsBackToNullWhenMarkerMissing() {
        byte[] bytes = SyntheticRdkBuilder.build(List.of(
                "D:/MFFD/RoboDK/Ply 1-15", "5.5.3", "WIN64"
        ));
        RecordingWriter writer = new RecordingWriter();
        new RdkTextScrapeParser().parse(ctx(bytes, "x.rdk",
                Optional.empty(), Optional.of("ref"), List.of(), writer));
        assertThat(writer.singleByPredicate).doesNotContainKey(RdkAnnotations.PROGRAM_SOURCE);
        assertThat(writer.singleByPredicate)
                .containsEntry(RdkAnnotations.APP_VERSION, "5.5.3")
                .containsEntry(RdkAnnotations.PLATFORM, "WIN64");
    }

    // ── degenerate inputs ─────────────────────────────────────────────────

    @Test
    void emptyBytesEmitNothing() {
        RecordingWriter writer = new RecordingWriter();
        int emitted = new RdkTextScrapeParser().parse(ctx(new byte[0], "x.rdk",
                Optional.empty(), Optional.of("ref"), List.of(), writer));
        assertThat(emitted).isZero();
        assertThat(writer.entries).isEmpty();
    }

    @Test
    void corruptZlibStreamDoesNotThrow() {
        // Header bytes valid but the rest is garbage → inflate fails.
        byte[] bytes = new byte[] {
                0x03, 0x25, 0x10, (byte) 0xA5,
                'n','o','t',' ','z','l','i','b'
        };
        RecordingWriter writer = new RecordingWriter();
        int emitted = new RdkTextScrapeParser().parse(ctx(bytes, "x.rdk",
                Optional.empty(), Optional.of("ref"), List.of(), writer));
        assertThat(emitted).isZero();
        assertThat(writer.entries).isEmpty();
    }

    @Test
    void emitsNothingWhenNoSubjectAvailable() {
        byte[] bytes = SyntheticRdkBuilder.build(List.of("5.5.3", "WIN64"));
        RecordingWriter writer = new RecordingWriter();
        int emitted = new RdkTextScrapeParser().parse(ctx(bytes, "x.rdk",
                Optional.empty(), Optional.empty(), List.of(), writer));
        assertThat(emitted).isZero();
    }

    @Test
    void fallsBackToDataObjectSubjectWhenFileReferenceMissing() {
        byte[] bytes = SyntheticRdkBuilder.build(List.of("5.5.3", "WIN64"));
        RecordingWriter writer = new RecordingWriter();
        new RdkTextScrapeParser().parse(ctx(bytes, "x.rdk",
                Optional.of("do-fallback"), Optional.empty(), List.of(), writer));
        for (RecordingWriter.Entry e : writer.entries) {
            assertThat(e.subject()).isEqualTo("do-fallback");
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────

    static FileParserPlugin.ParseContext ctx(
            byte[] bytes, String filename,
            Optional<String> doId, Optional<String> refId,
            List<FileParserPlugin.SiblingFile> siblings,
            FileParserPlugin.AnnotationWriter writer) {
        return new FileParserPlugin.ParseContext() {
            @Override public byte[] bytes() { return bytes; }
            @Override public String filename() { return filename; }
            @Override public Optional<String> parentDataObjectAppId() { return doId; }
            @Override public Optional<String> fileReferenceAppId() { return refId; }
            @Override public List<FileParserPlugin.SiblingFile> siblingFiles() { return siblings; }
            @Override public FileParserPlugin.AnnotationWriter annotations() { return writer; }
        };
    }
}
