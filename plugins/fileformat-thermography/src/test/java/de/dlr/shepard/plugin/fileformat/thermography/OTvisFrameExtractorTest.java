package de.dlr.shepard.plugin.fileformat.thermography;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests for {@link OTvisFrameExtractor}.
 *
 * <p>The happy-path test reads the MFFD sample fixture and verifies the
 * same numerical invariants the layout-probe test covers, this time via
 * the {@code extract(...)} public surface. The two degraded tests
 * fabricate tar variants by repacking the fixture (missing
 * {@code content.xml}; truncated {@code calibration.bin}). The negative
 * test feeds non-tar bytes and verifies {@link IOException} propagation.
 */
final class OTvisFrameExtractorTest {

    private static final String FIXTURE = "/sample_S4_M13_L18_F4.OTvis";

    private static final int EXPECTED_WIDTH = 1024;
    private static final int EXPECTED_HEIGHT = 768;
    private static final String EXPECTED_MAGIC = "DIFFJPBG00000001";

    private static byte[] fixtureBytes() throws IOException {
        try (InputStream in = OTvisFrameExtractorTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(in).as("fixture %s must be on the classpath", FIXTURE).isNotNull();
            return OTvisFrameExtractor.readAllBytes(in);
        }
    }

    private static Map<String, byte[]> fixtureStreams() throws IOException {
        try (InputStream in = OTvisFrameExtractorTest.class.getResourceAsStream(FIXTURE)) {
            return new LinkedHashMap<>(OTvisFrameExtractor.readTarLenient(in));
        }
    }

    // ─── happy path ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("extract(InputStream) on the MFFD fixture decodes width=1024 height=768 with one lock-in + one raw frame")
    void extractFromMffdFixture() throws IOException {
        OTvisFrameExtractor extractor = new OTvisFrameExtractor();
        ExtractedFrames frames;
        try (InputStream in = OTvisFrameExtractorTest.class.getResourceAsStream(FIXTURE)) {
            frames = extractor.extract(in);
        }

        assertThat(frames.width).isEqualTo(EXPECTED_WIDTH);
        assertThat(frames.height).isEqualTo(EXPECTED_HEIGHT);

        // lock-in (sequence0/f0.bin)
        assertThat(frames.lockInResult).hasSizeGreaterThanOrEqualTo(1);
        LockInResultFrame lir = frames.lockInResult.get(0);
        assertThat(lir.amplitude).hasSize(EXPECTED_WIDTH * EXPECTED_HEIGHT);
        assertThat(lir.phase).hasSize(EXPECTED_WIDTH * EXPECTED_HEIGHT);
        assertThat(lir.header.magicAscii()).isEqualTo(EXPECTED_MAGIC);

        // Every phase pixel is finite and strictly inside [-pi, +pi]
        // (Java's Math.atan2 returns within [-pi, +pi]; we use a tiny
        // epsilon for the float32 cast safety net).
        float minPhase = Float.POSITIVE_INFINITY, maxPhase = Float.NEGATIVE_INFINITY;
        for (float p : lir.phase) {
            assertThat(Float.isFinite(p)).isTrue();
            if (p < minPhase) minPhase = p;
            if (p > maxPhase) maxPhase = p;
        }
        assertThat(minPhase).isGreaterThanOrEqualTo((float) -Math.PI);
        assertThat(maxPhase).isLessThanOrEqualTo((float) Math.PI);

        // raw calibrated (sequence1/f<N>.bin)
        assertThat(frames.rawCalibrated).hasSizeGreaterThanOrEqualTo(1);
        RawCalibratedFrame rcf = frames.rawCalibrated.get(0);
        assertThat(rcf.temperatureCelsius).hasSize(EXPECTED_WIDTH * EXPECTED_HEIGHT);
        // MFFD shop room temperature window from byte-layout-notes §2.3.
        for (float t : rcf.temperatureCelsius) {
            assertThat(t).isBetween(20.0f, 30.0f);
        }

        // calibration LUT
        assertThat(frames.calibrationLut).isNotNull();
        assertThat(frames.calibrationLut.isMonotonic()).isTrue();
        assertThat(frames.calibrationLut.min()).isLessThan(-250.0f);
        assertThat(frames.calibrationLut.max()).isGreaterThan(350.0f);

        // No partial issues on the clean fixture.
        assertThat(frames.partialReason).isNull();
    }

    // ─── degraded inputs ───────────────────────────────────────────────────────

    @Test
    @DisplayName("missing content.xml → partialReason mentions it; frames still decode")
    void missingContentXmlDegrades() throws IOException {
        Map<String, byte[]> streams = fixtureStreams();
        assertThat(streams).containsKey("content.xml");
        streams.remove("content.xml");

        byte[] repacked = OTvisFrameExtractor.repackTar(streams);
        OTvisFrameExtractor extractor = new OTvisFrameExtractor();
        ExtractedFrames frames = extractor.extract(new ByteArrayInputStream(repacked));

        assertThat(frames.partialReason)
                .as("partialReason should record the missing content.xml")
                .isNotNull()
                .contains("content.xml");
        // Frame decoding does not depend on content.xml so the rest still works.
        assertThat(frames.lockInResult).hasSizeGreaterThanOrEqualTo(1);
        assertThat(frames.rawCalibrated).hasSizeGreaterThanOrEqualTo(1);
        assertThat(frames.calibrationLut).isNotNull();
    }

    @Test
    @DisplayName("truncated calibration.bin → rawCalibrated empty, partialReason flags calibration")
    void truncatedCalibrationDegrades() throws IOException {
        Map<String, byte[]> streams = fixtureStreams();
        // Find and truncate the sequence-wide calibration.bin.
        String calKey = null;
        for (String k : streams.keySet()) {
            if (k.endsWith("calibration.bin")) {
                calKey = k;
                break;
            }
        }
        assertThat(calKey).as("fixture must contain a calibration.bin").isNotNull();
        byte[] full = streams.get(calKey);
        byte[] truncated = new byte[full.length / 2];
        System.arraycopy(full, 0, truncated, 0, truncated.length);
        streams.put(calKey, truncated);

        byte[] repacked = OTvisFrameExtractor.repackTar(streams);
        OTvisFrameExtractor extractor = new OTvisFrameExtractor();
        ExtractedFrames frames = extractor.extract(new ByteArrayInputStream(repacked));

        // The corrupt LUT must not produce rawCalibrated frames.
        assertThat(frames.rawCalibrated).isEmpty();
        // The lock-in result frame is independent of the LUT, so it still decodes.
        assertThat(frames.lockInResult).hasSizeGreaterThanOrEqualTo(1);
        // partialReason must mention the calibration trouble.
        assertThat(frames.partialReason)
                .as("partialReason must flag the calibration issue")
                .isNotNull()
                .containsIgnoringCase("calibration");
    }

    // ─── negative: not a tar ───────────────────────────────────────────────────

    @Test
    @DisplayName("non-tar bytes throw IOException (not RuntimeException)")
    void nonTarThrowsIOException() {
        byte[] junk = "not a tar archive — definitely not".getBytes(StandardCharsets.UTF_8);
        OTvisFrameExtractor extractor = new OTvisFrameExtractor();
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> extractor.extract(new ByteArrayInputStream(junk)));
    }

    @Test
    @DisplayName("empty input throws IOException")
    void emptyInputThrowsIOException() {
        OTvisFrameExtractor extractor = new OTvisFrameExtractor();
        assertThatExceptionOfType(IOException.class)
                .isThrownBy(() -> extractor.extract(new ByteArrayInputStream(new byte[0])));
    }

    // ─── extractor-internal pure helpers ───────────────────────────────────────

    @Test
    @DisplayName("readHeader on a sequence0/f0.bin slice matches the byte-layout-notes spec")
    void readHeaderMatchesSpec() throws IOException {
        Map<String, byte[]> streams = OTvisFrameExtractor.readTarLenient(
                new ByteArrayInputStream(fixtureBytes()));
        byte[] f0 = streams.get("sequence0/f0.bin");
        assertThat(f0).isNotNull();
        RecurringHeader header = OTvisFrameExtractor.readHeader(f0);
        assertThat(header.magicAscii()).isEqualTo(EXPECTED_MAGIC);
        assertThat(header.width()).isEqualTo(EXPECTED_WIDTH);
        assertThat(header.height()).isEqualTo(EXPECTED_HEIGHT);
        assertThat(header.dataFormat()).isEqualTo(OTvisFrameExtractor.DF_COMPLEX_FLOAT);
    }

    @Test
    @DisplayName("bytesPerPixel covers every documented DataFormat; unknown throws")
    void bytesPerPixelTable() {
        assertThat(OTvisFrameExtractor.bytesPerPixel(OTvisFrameExtractor.DF_UINT8)).isEqualTo(1);
        assertThat(OTvisFrameExtractor.bytesPerPixel(OTvisFrameExtractor.DF_UINT16)).isEqualTo(2);
        assertThat(OTvisFrameExtractor.bytesPerPixel(OTvisFrameExtractor.DF_FLOAT32)).isEqualTo(4);
        assertThat(OTvisFrameExtractor.bytesPerPixel(OTvisFrameExtractor.DF_COMPLEX_FLOAT)).isEqualTo(8);
        assertThat(OTvisFrameExtractor.bytesPerPixel(OTvisFrameExtractor.DF_BGR_TRUECOLOR)).isEqualTo(3);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> OTvisFrameExtractor.bytesPerPixel(99));
    }

    @Test
    @DisplayName("CalibrationLut basic invariants — wrong length rejected; toArray is defensive")
    void calibrationLutInvariants() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new CalibrationLut(new float[10]));

        float[] payload = new float[CalibrationLut.LUT_SIZE];
        for (int i = 0; i < payload.length; i++) payload[i] = -273.15f + 0.01f * i;
        CalibrationLut lut = new CalibrationLut(payload);
        assertThat(lut.isMonotonic()).isTrue();
        assertThat(lut.min()).isCloseTo(-273.15f, org.assertj.core.data.Offset.offset(0.01f));
        assertThat(lut.celsiusFor(0)).isCloseTo(-273.15f, org.assertj.core.data.Offset.offset(0.01f));
        // Mutating the returned array does not affect the LUT.
        float[] copy = lut.toArray();
        copy[0] = 0.0f;
        assertThat(lut.celsiusFor(0)).isCloseTo(-273.15f, org.assertj.core.data.Offset.offset(0.01f));
    }
}
