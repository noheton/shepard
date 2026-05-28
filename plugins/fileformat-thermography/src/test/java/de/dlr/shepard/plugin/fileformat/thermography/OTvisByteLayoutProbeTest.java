package de.dlr.shepard.plugin.fileformat.thermography;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Sanity guardrail for the OTvis tier-2 byte-layout hypotheses captured in
 * {@code docs/byte-layout-notes.md}. Tests against the MFFD fixture
 * {@code sample_S4_M13_L18_F4.OTvis}.
 *
 * <p>The point of this class is NOT to be a frame extractor — that's
 * {@code OTVIS-TIER2-EXTRACTOR}, the next row. The point is to fail loudly
 * if any of the documented assumptions break: header is 28 B, magic is
 * the 16-char ASCII identifier, payload size is exactly width × height ×
 * bytesPerPixel(dataFormat), calibration LUT is 65,536 little-endian
 * floats covering absolute zero to ~382 °C with monotonic increase.
 *
 * <p>Skipped tests document layouts that could not be confirmed against
 * the single available fixture (INFL-compressed frames, multi-frame
 * files, per-frame c&lt;N&gt;.bin overrides). Do NOT enable them by
 * inventing thresholds — they should remain skipped until a fixture
 * with the respective shape is added.
 */
final class OTvisByteLayoutProbeTest {

    private static final String FIXTURE = "/sample_S4_M13_L18_F4.OTvis";

    // Per-stream layout claims from byte-layout-notes.md.
    private static final String STREAM_SEQ0_F0       = "sequence0/f0.bin";
    private static final String STREAM_SEQ1_RAW      = "sequence1/f4294983590.bin";
    private static final String STREAM_SEQ1_CALIB    = "sequence1/calibration.bin";

    private static final int EXPECTED_WIDTH  = 1024;
    private static final int EXPECTED_HEIGHT = 768;
    private static final int HEADER_BYTES    = 28;
    private static final int CALIB_FLOATS    = 65_536;
    private static final int CALIB_BYTES     = CALIB_FLOATS * 4;

    // DataFormat codes per Rev H §"Frame-Datei".
    private static final int DF_UINT8         = 0;
    private static final int DF_UINT16        = 2;
    private static final int DF_FLOAT32       = 5;
    private static final int DF_COMPLEX_FLOAT = 13;
    private static final int DF_BGR_TRUECOLOR = 24;

    /** Identifier observed in the MFFD fixture; treated as the magic. */
    private static final String EXPECTED_MAGIC = "DIFFJPBG00000001";

    // ─── helpers ────────────────────────────────────────────────────────────

    private static Map<String, byte[]> readAllStreams() throws IOException {
        Map<String, byte[]> out = new HashMap<>();
        try (InputStream in = OTvisByteLayoutProbeTest.class.getResourceAsStream(FIXTURE);
                TarArchiveInputStream tar = new TarArchiveInputStream(in)) {
            assertThat(in).as("fixture %s must be on the classpath", FIXTURE).isNotNull();
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                if (!entry.isFile()) continue;
                String name = entry.getName();
                if (name == null) continue;
                ByteArrayOutputStream buf = new ByteArrayOutputStream(
                        entry.getSize() > 0 && entry.getSize() < Integer.MAX_VALUE
                                ? (int) entry.getSize()
                                : 8192);
                byte[] chunk = new byte[8192];
                int n;
                while ((n = tar.read(chunk)) >= 0) {
                    buf.write(chunk, 0, n);
                }
                out.put(name, buf.toByteArray());
            }
        }
        return out;
    }

    private static int bytesPerPixel(int dataFormat) {
        return switch (dataFormat) {
            case DF_UINT8 -> 1;
            case DF_UINT16 -> 2;
            case DF_FLOAT32 -> 4;
            case DF_COMPLEX_FLOAT -> 8;
            case DF_BGR_TRUECOLOR -> 3;
            default -> throw new IllegalArgumentException("unknown DataFormat " + dataFormat);
        };
    }

    private record Header(String identifier, int width, int height, int dataFormat) {}

    private static Header decodeHeader(byte[] blob) {
        ByteBuffer bb = ByteBuffer.wrap(blob, 0, HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        byte[] id = new byte[16];
        bb.get(id);
        int width = bb.getInt();
        int height = bb.getInt();
        int dfmt = bb.getInt();
        return new Header(new String(id, StandardCharsets.US_ASCII), width, height, dfmt);
    }

    // ─── confirmed-layout tests ─────────────────────────────────────────────

    @Test
    @DisplayName("tar walk: all three binary streams are present at the expected sizes")
    void tarLayoutMatchesSpec() throws IOException {
        Map<String, byte[]> streams = readAllStreams();
        assertThat(streams).containsKeys(STREAM_SEQ0_F0, STREAM_SEQ1_RAW, STREAM_SEQ1_CALIB);
        assertThat(streams.get(STREAM_SEQ0_F0)).hasSize(6_291_484);
        assertThat(streams.get(STREAM_SEQ1_RAW)).hasSize(1_572_892);
        assertThat(streams.get(STREAM_SEQ1_CALIB)).hasSize(CALIB_BYTES);
        // Tier-1 also expects content.xml at the root.
        assertThat(streams).containsKey("content.xml");
    }

    @Test
    @DisplayName("sequence0/f0.bin = 1024x768 complex-float lock-in frame; amp + phase in plausible ranges")
    void sequence0LockInComplexFrame() throws IOException {
        byte[] blob = readAllStreams().get(STREAM_SEQ0_F0);
        assertThat(blob).as("stream must be present").isNotNull();

        Header h = decodeHeader(blob);
        assertThat(h.identifier()).isEqualTo(EXPECTED_MAGIC);
        assertThat(h.width()).isEqualTo(EXPECTED_WIDTH);
        assertThat(h.height()).isEqualTo(EXPECTED_HEIGHT);
        assertThat(h.dataFormat())
                .as("Edevis lock-in result frames are complex float (DataFormat 13)")
                .isEqualTo(DF_COMPLEX_FLOAT);

        int expectedPayload = EXPECTED_WIDTH * EXPECTED_HEIGHT * bytesPerPixel(h.dataFormat());
        assertThat(blob.length - HEADER_BYTES).isEqualTo(expectedPayload);

        ByteBuffer bb = ByteBuffer.wrap(blob, HEADER_BYTES, expectedPayload).order(ByteOrder.LITTLE_ENDIAN);
        int n = EXPECTED_WIDTH * EXPECTED_HEIGHT;
        double minAmp = Double.POSITIVE_INFINITY, maxAmp = Double.NEGATIVE_INFINITY, sumAmp = 0.0;
        double minPhase = Double.POSITIVE_INFINITY, maxPhase = Double.NEGATIVE_INFINITY;
        int finiteCount = 0;
        for (int i = 0; i < n; i++) {
            float re = bb.getFloat();
            float im = bb.getFloat();
            double amp = Math.hypot(re, im);
            double phase = Math.atan2(im, re);
            if (Double.isFinite(amp) && Double.isFinite(phase)) {
                finiteCount++;
                sumAmp += amp;
                if (amp < minAmp) minAmp = amp;
                if (amp > maxAmp) maxAmp = amp;
                if (phase < minPhase) minPhase = phase;
                if (phase > maxPhase) maxPhase = phase;
            }
        }
        double meanAmp = sumAmp / finiteCount;

        assertThat(finiteCount).as("all pixels should be finite").isEqualTo(n);
        // Empirical envelopes from probe (see byte-layout-notes.md §2.3); widened
        // to a generous sanity range — any reasonable lock-in result on a CFRP
        // shell at ~0.015 Hz excitation will land here.
        assertThat(minAmp).isPositive();
        assertThat(maxAmp).isLessThan(1.0e6);  // not a wild overflow
        assertThat(meanAmp).isBetween(0.01, 1.0e5);
        // Phase MUST be inside (-pi, +pi] by definition of atan2.
        assertThat(minPhase).isGreaterThanOrEqualTo(-Math.PI - 1e-6);
        assertThat(maxPhase).isLessThanOrEqualTo(Math.PI + 1e-6);
        // For our single-frequency excitation the spread is narrow; we don't
        // assert the narrowness empirically (other captures may differ) but we
        // do assert that both bounds are real numbers.
        assertThat(minPhase).isFinite();
        assertThat(maxPhase).isFinite();
    }

    @Test
    @DisplayName("sequence1/f<N>.bin = 1024x768 uint16 raw frame; calibration LUT maps to plausible room-T window")
    void sequence1RawFrameCalibratesToCelsius() throws IOException {
        Map<String, byte[]> streams = readAllStreams();
        byte[] frame = streams.get(STREAM_SEQ1_RAW);
        byte[] calBlob = streams.get(STREAM_SEQ1_CALIB);
        assertThat(frame).isNotNull();
        assertThat(calBlob).isNotNull();

        Header h = decodeHeader(frame);
        assertThat(h.identifier()).isEqualTo(EXPECTED_MAGIC);
        assertThat(h.width()).isEqualTo(EXPECTED_WIDTH);
        assertThat(h.height()).isEqualTo(EXPECTED_HEIGHT);
        assertThat(h.dataFormat())
                .as("the second sequence in this fixture is a raw uint16 reference frame")
                .isEqualTo(DF_UINT16);

        int expectedPayload = EXPECTED_WIDTH * EXPECTED_HEIGHT * bytesPerPixel(h.dataFormat());
        assertThat(frame.length - HEADER_BYTES).isEqualTo(expectedPayload);

        // Decode calibration LUT first.
        ByteBuffer cb = ByteBuffer.wrap(calBlob).order(ByteOrder.LITTLE_ENDIAN);
        float[] lut = new float[CALIB_FLOATS];
        for (int i = 0; i < CALIB_FLOATS; i++) lut[i] = cb.getFloat();

        // LUT shape: monotonic non-decreasing, starts at absolute zero, max ~382 degC.
        assertThat(lut[0]).isCloseTo(-273.15f, org.assertj.core.data.Offset.offset(0.05f));
        assertThat(lut[CALIB_FLOATS - 1]).isBetween(300.0f, 500.0f);
        // Sampled-monotonic check (every 1024 entries).
        for (int i = 1024; i < CALIB_FLOATS; i += 1024) {
            assertThat(lut[i]).as("LUT must be non-decreasing at index %d", i).isGreaterThanOrEqualTo(lut[i - 1024]);
        }

        // Apply LUT to raw payload, compute min/max/mean degC.
        ByteBuffer pb = ByteBuffer.wrap(frame, HEADER_BYTES, expectedPayload).order(ByteOrder.LITTLE_ENDIAN);
        int n = EXPECTED_WIDTH * EXPECTED_HEIGHT;
        float minT = Float.POSITIVE_INFINITY, maxT = Float.NEGATIVE_INFINITY;
        double sumT = 0.0;
        for (int i = 0; i < n; i++) {
            int raw = pb.getShort() & 0xFFFF;
            float t = lut[raw];
            if (t < minT) minT = t;
            if (t > maxT) maxT = t;
            sumT += t;
        }
        float meanT = (float) (sumT / n);

        // MFFD shop-floor room temperature window. The fixture captures a
        // reference frame before excitation kicks in, so the entire image
        // should sit at ~22-25 degC. Use a generous envelope so a slightly
        // warmer or cooler shop run still passes.
        assertThat(minT).as("min temperature plausible").isBetween(10.0f, 40.0f);
        assertThat(maxT).as("max temperature plausible (no hot spot in this frame)").isBetween(10.0f, 60.0f);
        assertThat(meanT).as("mean temperature room-ish").isBetween(15.0f, 35.0f);
    }

    @Test
    @DisplayName("calibration.bin = exactly 65536 little-endian floats, no header (Rev H spec)")
    void calibrationBinIsBareLut() throws IOException {
        byte[] cal = readAllStreams().get(STREAM_SEQ1_CALIB);
        assertThat(cal).hasSize(CALIB_BYTES);

        ByteBuffer cb = ByteBuffer.wrap(cal).order(ByteOrder.LITTLE_ENDIAN);
        float first = cb.getFloat();
        // Absolute zero anchor.
        assertThat(first).isCloseTo(-273.15f, org.assertj.core.data.Offset.offset(0.05f));

        // No 16-byte ASCII identifier prefix (would have made the file 262160 B
        // and the leading float wouldn't be -273.15).
        // (Implicit assertion: the size check + the leading-float check together
        // exclude any plausible header.)
    }

    // ─── unconfirmed layouts — DELIBERATELY SKIPPED ─────────────────────────

    @Test
    @Disabled("UNCONFIRMED: HIDDEN TOC tar entry holds two Int64 (offset of content.xml, end of binary"
            + " data) per Rev H §Datei-Aufbau. commons-compress's TarArchiveInputStream skips this entry"
            + " (type 4 block-device); a raw byte-walk is needed to read the actual 16 bytes. Not"
            + " required for tier-2 correctness — used only as a content.xml seek hint. Enable when a"
            + " test for the raw-tar walker is added.")
    @DisplayName("HIDDEN TOC payload = (int64 contentXmlOffset, int64 endOfBinaryData)")
    void hiddenTocSpecialFile() {
        // No code; intentionally @Disabled.
    }

    @Test
    @Disabled("UNCONFIRMED: INFL (differential 16-bit) compression per Rev H pages 3-4. The MFFD"
            + " fixture's content.xml carries no Cmpr attribute and the payload sizes match the"
            + " uncompressed math exactly. Add a fixture with a Cmpr=\"INFL\" frame before enabling"
            + " this test; until then, faking thresholds against synthetic data would pass for the"
            + " wrong reason.")
    @DisplayName("INFL-compressed frame round-trips to within float32 epsilon of expected pixels")
    void inflCompressedFrameDecodes() {
        // No code; intentionally @Disabled.
    }

    @Test
    @Disabled("UNCONFIRMED: per-frame calibration override files c<FrameID>.bin. Rev H mentions them"
            + " but the MFFD fixture has none. Layout is presumed identical to calibration.bin (65536"
            + " floats, no header) but this cannot be verified without a fixture that ships one."
            + " Enable once such a fixture is added.")
    @DisplayName("per-frame c<N>.bin overrides the sequence calibration.bin")
    void perFrameCalibrationOverride() {
        // No code; intentionally @Disabled.
    }

    @Test
    @Disabled("UNCONFIRMED: multi-frame f<N>.bin files (multiple 28-byte headers stacked in one file)."
            + " Rev H sample content.xml shows FrameCount=2 in one Sequence, suggesting frame stacking"
            + " is allowed, but our fixture stores exactly one frame per file. Cross-reference path"
            + " is via content.xml/Sequence[id]/FrameInfo/Frame[@TarFileHeaderDataOffset]; the test"
            + " should seek into the tar at each offset and validate the header chain. Needs a"
            + " stacked fixture.")
    @DisplayName("multi-frame f<N>.bin: header chain matches content.xml TarFileHeaderDataOffset list")
    void multiFrameFileWalks() {
        // No code; intentionally @Disabled.
    }
}
