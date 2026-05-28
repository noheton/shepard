package de.dlr.shepard.plugin.fileformat.thermography.omezarr;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.fileformat.thermography.CalibrationLut;
import de.dlr.shepard.plugin.fileformat.thermography.ExtractedFrames;
import de.dlr.shepard.plugin.fileformat.thermography.LockInResultFrame;
import de.dlr.shepard.plugin.fileformat.thermography.OTvisFrameExtractor;
import de.dlr.shepard.plugin.fileformat.thermography.RawCalibratedFrame;
import de.dlr.shepard.plugin.fileformat.thermography.RecurringHeader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link OmeZarrStoreWriter} against the MFFD sample fixture.
 *
 * <p>The tests read the resulting {@code zarr.json} files via raw string
 * matchers rather than a JSON dependency — this keeps the plugin's
 * dependency footprint to {@code commons-compress + junit + assertj}
 * (matching the existing extractor tests) and validates the writer's
 * byte-deterministic output.
 */
final class OmeZarrStoreWriterTest {

    private static final String FIXTURE = "/sample_S4_M13_L18_F4.OTvis";
    private static final String FIXTURE_FILE_REF_APP_ID =
            "0192d8ce-0000-7000-8000-000000000001";

    @Test
    @DisplayName("write(fixture) produces a v3 store with amplitude, phase, raw, calibration arrays")
    void writeFixtureProducesV3Store(@TempDir Path tempDir) throws IOException {
        ExtractedFrames frames = extractFixture();

        OmeZarrStoreWriter writer = new OmeZarrStoreWriter();
        String storeUrl = writer.write(frames, tempDir, FIXTURE_FILE_REF_APP_ID);

        Path storeRoot = tempDir.resolve("thermography").resolve(FIXTURE_FILE_REF_APP_ID);
        assertThat(storeRoot).isDirectory();

        // Canonical URL is the file:// URI of the store root, ending with /
        assertThat(storeUrl)
                .startsWith("file:")
                .contains("thermography/" + FIXTURE_FILE_REF_APP_ID);

        // Root group is v3.
        Path rootJson = storeRoot.resolve("zarr.json");
        assertThat(rootJson).isRegularFile();
        String rootDoc = Files.readString(rootJson);
        assertThat(rootDoc)
                .contains("\"zarr_format\":3")
                .contains("\"node_type\":\"group\"")
                .contains("\"kind\":\"thermography-omezarr\"")
                .contains("\"width\":1024")
                .contains("\"height\":768");

        // Amplitude array: 1 x 768 x 1024 float32 little-endian.
        assertArrayJsonV3(
                storeRoot.resolve("amplitude").resolve("zarr.json"),
                /*shape=*/ "[1,768,1024]",
                /*chunkShape=*/ "[1,768,1024]",
                /*dims=*/ "[\"t\",\"y\",\"x\"]");

        // Phase array: same shape.
        assertArrayJsonV3(
                storeRoot.resolve("phase").resolve("zarr.json"),
                "[1,768,1024]",
                "[1,768,1024]",
                "[\"t\",\"y\",\"x\"]");

        // Raw array: 1 x 768 x 1024 (one raw frame in the MFFD fixture).
        assertArrayJsonV3(
                storeRoot.resolve("raw").resolve("zarr.json"),
                "[1,768,1024]",
                "[1,768,1024]",
                "[\"t\",\"y\",\"x\"]");

        // Calibration LUT: 65536 float32, one chunk.
        assertArrayJsonV3(
                storeRoot.resolve("calibration").resolve("zarr.json"),
                "[65536]",
                "[65536]",
                "[\"idx\"]");

        // Chunk files exist at the expected default keys (c/<i>/<j>/<k>).
        Path amplitudeChunk = storeRoot.resolve("amplitude/c/0/0/0");
        Path phaseChunk = storeRoot.resolve("phase/c/0/0/0");
        Path rawChunk = storeRoot.resolve("raw/c/0/0/0");
        Path calChunk = storeRoot.resolve("calibration/c/0");
        assertThat(amplitudeChunk).isRegularFile();
        assertThat(phaseChunk).isRegularFile();
        assertThat(rawChunk).isRegularFile();
        assertThat(calChunk).isRegularFile();

        // Chunk byte length = product(chunk_shape) * 4 bytes (float32).
        long pixelChunkBytes = 1L * 768L * 1024L * 4L;
        assertThat(Files.size(amplitudeChunk)).isEqualTo(pixelChunkBytes);
        assertThat(Files.size(phaseChunk)).isEqualTo(pixelChunkBytes);
        assertThat(Files.size(rawChunk)).isEqualTo(pixelChunkBytes);
        assertThat(Files.size(calChunk)).isEqualTo(65536L * 4L);

        // Round-trip the amplitude chunk's first pixel — bytes should be
        // little-endian float32. We re-decode by hand and check it matches
        // the in-memory amplitude[0].
        byte[] chunkBytes = Files.readAllBytes(amplitudeChunk);
        float firstPixelOnDisk = ByteBuffer.wrap(chunkBytes)
                .order(ByteOrder.LITTLE_ENDIAN)
                .getFloat(0);
        float firstPixelInMemory = frames.lockInResult.get(0).amplitude[0];
        assertThat(firstPixelOnDisk).isEqualTo(firstPixelInMemory);
    }

    @Test
    @DisplayName("write skips missing sub-arrays without breaking the store")
    void writeSkipsMissingSubArrays(@TempDir Path tempDir) throws IOException {
        // Empty bundle — no lock-in, no raw, no calibration.
        ExtractedFrames empty = new ExtractedFramesBuilder().build();

        OmeZarrStoreWriter writer = new OmeZarrStoreWriter();
        String url = writer.write(empty, tempDir, FIXTURE_FILE_REF_APP_ID);

        Path storeRoot = tempDir.resolve("thermography").resolve(FIXTURE_FILE_REF_APP_ID);
        assertThat(storeRoot).isDirectory();
        assertThat(storeRoot.resolve("zarr.json")).isRegularFile();
        assertThat(storeRoot.resolve("amplitude")).doesNotExist();
        assertThat(storeRoot.resolve("phase")).doesNotExist();
        assertThat(storeRoot.resolve("raw")).doesNotExist();
        assertThat(storeRoot.resolve("calibration")).doesNotExist();
        assertThat(url).startsWith("file:");
    }

    @Test
    @DisplayName("null inputs throw IOException with a descriptive message")
    void rejectsNullInputs() {
        OmeZarrStoreWriter writer = new OmeZarrStoreWriter();
        assertNullCheck(() -> writer.write(null, Path.of("/tmp"), "x"), "frames");
        assertNullCheck(() -> writer.write(new ExtractedFramesBuilder().build(), null, "x"),
                "rootPath");
        assertNullCheck(
                () -> writer.write(new ExtractedFramesBuilder().build(), Path.of("/tmp"), null),
                "fileReferenceAppId");
        assertNullCheck(
                () -> writer.write(new ExtractedFramesBuilder().build(), Path.of("/tmp"), ""),
                "fileReferenceAppId");
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static void assertNullCheck(IoCallable call, String fieldName) {
        try {
            call.call();
            throw new AssertionError("expected IOException for null/blank " + fieldName);
        } catch (IOException e) {
            assertThat(e).hasMessageContaining(fieldName);
        }
    }

    @FunctionalInterface
    private interface IoCallable {
        void call() throws IOException;
    }

    private static void assertArrayJsonV3(
            Path zarrJsonPath, String expectedShape, String expectedChunkShape,
            String expectedDimensionNames) throws IOException {
        assertThat(zarrJsonPath).isRegularFile();
        String doc = Files.readString(zarrJsonPath);
        assertThat(doc)
                .contains("\"zarr_format\":3")
                .contains("\"node_type\":\"array\"")
                .contains("\"data_type\":\"float32\"")
                .contains("\"shape\":" + expectedShape)
                .contains("\"chunk_shape\":" + expectedChunkShape)
                .contains("\"dimension_names\":" + expectedDimensionNames)
                .contains("\"name\":\"bytes\"")
                .contains("\"endian\":\"little\"")
                .contains("\"name\":\"regular\"")
                .contains("\"separator\":\"/\"");
    }

    private static ExtractedFrames extractFixture() throws IOException {
        OTvisFrameExtractor extractor = new OTvisFrameExtractor();
        try (InputStream in = OmeZarrStoreWriterTest.class.getResourceAsStream(FIXTURE)) {
            return extractor.extract(in);
        }
    }

    /**
     * Local helper for building synthetic {@link ExtractedFrames} bundles
     * for the negative-path / degraded tests. Mirrors the package-private
     * constructor in the production class.
     */
    static final class ExtractedFramesBuilder {
        private List<LockInResultFrame> lockIn = List.of();
        private List<RawCalibratedFrame> raw = List.of();
        private CalibrationLut lut;
        private int width = 0;
        private int height = 0;
        private String partialReason;

        ExtractedFramesBuilder lockIn(List<LockInResultFrame> v) {
            this.lockIn = v;
            return this;
        }

        ExtractedFramesBuilder raw(List<RawCalibratedFrame> v) {
            this.raw = v;
            return this;
        }

        ExtractedFramesBuilder lut(CalibrationLut v) {
            this.lut = v;
            return this;
        }

        ExtractedFramesBuilder size(int w, int h) {
            this.width = w;
            this.height = h;
            return this;
        }

        ExtractedFramesBuilder partialReason(String reason) {
            this.partialReason = reason;
            return this;
        }

        ExtractedFrames build() {
            // Reflective construction — the production constructor is
            // package-private and lives in the same package as the
            // OTvisFrameExtractor. We use the public extractor's
            // decodeFromStreams entry by feeding an empty stream map; that
            // yields a partialReason-bearing empty bundle, which is what
            // we want for the negative-path tests.
            if (lockIn.isEmpty() && raw.isEmpty() && lut == null) {
                return OTvisFrameExtractor.decodeFromStreams(java.util.Map.of());
            }
            // For richer fixtures we'd need a public factory; the
            // current OME-Zarr test surface only exercises the empty case
            // (covered above) and the fixture case (real data via
            // extractFixture()).
            throw new UnsupportedOperationException(
                    "non-empty synthetic ExtractedFrames not needed for current tests");
        }
    }

    /** Reference dummy header used only to keep constructor invariants happy. */
    @SuppressWarnings("unused")
    private static RecurringHeader dummyHeader(int w, int h) {
        byte[] magic = new byte[16];
        java.util.Arrays.fill(magic, (byte) 'X');
        return new RecurringHeader(magic, w, h, 13);
    }
}
