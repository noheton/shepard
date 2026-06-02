package de.dlr.shepard.plugin.fileformat.svdx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Regression test against a real MFFD AFP ultrasonic-spot-welding SVDX
 * file from the DLR ZLP campaign on 2023-03-20.
 *
 * <p>The fixture (~7 MB) is NOT checked into git. The test reads it
 * from {@code /mnt/pve/unas/dump/dataset/Punktschweißungen/} when
 * present and self-skips otherwise so CI passes on workers that don't
 * have the NAS mount.
 */
class SvdxParserMFFDFixtureTest {

    private static final Path SMALLEST_FILE = Path.of(
        "/mnt/pve/unas/dump/dataset/Punktschweißungen/Scope Project_AutoSave_18_26_04.svdx");

    @Test
    void extractsManifestFromRealCampaignFile() throws Exception {
        assumeTrue(Files.exists(SMALLEST_FILE),
            "real MFFD fixture absent on this worker; skipping");

        byte[] bytes = Files.readAllBytes(SMALLEST_FILE);

        RecordingWriter w = new RecordingWriter();
        FileParserPlugin.ParseContext ctx = new RealCampaignContext(bytes,
            SMALLEST_FILE.getFileName().toString(), w);

        int n = new SvdxManifestParser().parse(ctx);

        assertThat(n).as("at least envelope + manifest annotations").isGreaterThan(20);
        // Format-version: every campaign file is 0x0000_0000_000c_96XX where
        // XX is the per-session build byte (0x71, 0x73, 0x6d observed).
        assertThat(w.valueFor(SvdxAnnotations.FORMAT_VERSION))
            .matches("0x0+c96[0-9a-f]{2}");
        // Channel count: 46 was the ground truth on the 18_26_04 file.
        assertThat(w.valueFor(SvdxAnnotations.CHANNEL_COUNT)).isEqualTo("46");
        // AmsNetId observed on every channel of this campaign.
        assertThat(w.allValuesFor(SvdxAnnotations.AMS_NET_ID))
            .containsExactly("169.254.165.182.1.1");
        // Port observed on every channel.
        assertThat(w.allValuesFor(SvdxAnnotations.PORT)).contains("851");
        // Channel name we know to be present from CSV ground truth.
        assertThat(w.allValuesFor(SvdxAnnotations.SYMBOL_NAME))
            .contains("GVL_IO_US_Endeffektor.aTemperatureAnalogIntput1",
                      "RobotData.rRoboPosA");
        // DataTypes seen in this campaign.
        assertThat(w.allValuesFor(SvdxAnnotations.DATA_TYPE))
            .contains("INT16", "REAL32");
    }

    static final class RecordingWriter implements FileParserPlugin.AnnotationWriter {
        record Entry(String subject, String predicate, String value) {}
        private final List<Entry> entries = new ArrayList<>();

        @Override public void write(String s, String p, String v) {
            entries.add(new Entry(s, p, v));
        }
        String valueFor(String predicate) {
            return entries.stream()
                .filter(e -> e.predicate().equals(predicate))
                .map(Entry::value).findFirst()
                .orElseThrow(() -> new AssertionError("no value for " + predicate));
        }
        List<String> allValuesFor(String predicate) {
            return entries.stream()
                .filter(e -> e.predicate().equals(predicate))
                .map(Entry::value).toList();
        }
    }

    static final class RealCampaignContext implements FileParserPlugin.ParseContext {
        private final byte[] bytes;
        private final String filename;
        private final FileParserPlugin.AnnotationWriter w;

        RealCampaignContext(byte[] bytes, String filename, FileParserPlugin.AnnotationWriter w) {
            this.bytes = bytes;
            this.filename = filename;
            this.w = w;
        }
        @Override public byte[] bytes() { return bytes; }
        @Override public String filename() { return filename; }
        @Override public Optional<String> parentDataObjectAppId() {
            return Optional.of("01990000-0000-7000-8000-000000000d0d");
        }
        @Override public Optional<String> fileReferenceAppId() {
            return Optional.of("01990000-0000-7000-8000-000000000ef0");
        }
        @Override public FileParserPlugin.AnnotationWriter annotations() { return w; }
    }
}
