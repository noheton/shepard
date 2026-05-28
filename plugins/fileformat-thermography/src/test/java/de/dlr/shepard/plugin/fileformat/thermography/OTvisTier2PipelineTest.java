package de.dlr.shepard.plugin.fileformat.thermography;

import static org.assertj.core.api.Assertions.assertThat;

import de.dlr.shepard.plugin.fileformat.thermography.omezarr.OmeZarrStoreWriter;
import de.dlr.shepard.plugin.fileformat.thermography.spi.DerivedDatasetWriter;
import de.dlr.shepard.plugin.fileformat.thermography.spi.NoOpDerivedDatasetWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end test for {@link OTvisTier2Pipeline} against the bundled
 * MFFD fixture. Exercises three paths:
 *
 * <ol>
 *   <li><b>Happy</b> — feed the real fixture, assert success=true,
 *       partialReason=null, store present on disk, sink notified once
 *       with the expected annotation bag.</li>
 *   <li><b>Hard failure</b> — feed a corrupted byte blob, assert
 *       success=false, sink NEVER notified.</li>
 *   <li><b>Degraded</b> — feed a tar missing the calibration LUT; assert
 *       success=true but partialReason mentions calibration was
 *       skipped.</li>
 * </ol>
 */
final class OTvisTier2PipelineTest {

    private static final String FIXTURE = "/sample_S4_M13_L18_F4.OTvis";
    private static final String PARENT_DO_APP_ID =
            "0192d8ce-0000-7000-8000-deadbeef0001";
    private static final String FILE_REF_APP_ID =
            "0192d8ce-0000-7000-8000-deadbeef0002";

    @Test
    @DisplayName("happy path: full MFFD fixture → success=true, sink notified once, store on disk")
    void happyPath(@TempDir Path tempDir) throws IOException {
        Path otvisFile = copyFixtureTo(tempDir.resolve("sample.OTvis"));
        Path omeZarrRoot = tempDir.resolve("zarr");
        NoOpDerivedDatasetWriter sink = new NoOpDerivedDatasetWriter();

        OTvisTier2Pipeline pipeline = new OTvisTier2Pipeline(
                new OTvisFrameExtractor(),
                new OmeZarrStoreWriter(),
                sink);

        PipelineResult result = pipeline.run(
                otvisFile, omeZarrRoot, PARENT_DO_APP_ID, FILE_REF_APP_ID);

        assertThat(result.success).isTrue();
        assertThat(result.partialReason).isNull();
        assertThat(result.storeUrl)
                .isNotNull()
                .startsWith("file:")
                .contains("thermography/" + FILE_REF_APP_ID);

        // Store root exists on disk.
        Path storeRoot = omeZarrRoot.resolve("thermography").resolve(FILE_REF_APP_ID);
        assertThat(storeRoot.resolve("zarr.json")).isRegularFile();
        assertThat(storeRoot.resolve("amplitude/zarr.json")).isRegularFile();
        assertThat(storeRoot.resolve("phase/zarr.json")).isRegularFile();
        assertThat(storeRoot.resolve("raw/zarr.json")).isRegularFile();
        assertThat(storeRoot.resolve("calibration/zarr.json")).isRegularFile();

        // Sink notified exactly once with the right args.
        assertThat(sink.calls()).hasSize(1);
        NoOpDerivedDatasetWriter.Call call = sink.calls().get(0);
        assertThat(call.parentDataObjectAppId).isEqualTo(PARENT_DO_APP_ID);
        assertThat(call.fileReferenceAppId).isEqualTo(FILE_REF_APP_ID);
        assertThat(call.storeUrl).isEqualTo(result.storeUrl);
        assertThat(call.annotations)
                .containsEntry("kind", "thermography-omezarr")
                .containsEntry("derivedFrom", FILE_REF_APP_ID)
                .containsEntry("amplitudeShape", "1,768,1024")
                .containsEntry("phaseShape", "1,768,1024")
                .containsEntry("rawShape", "1,768,1024")
                .containsEntry("calibrationApplied", "true");
    }

    @Test
    @DisplayName("negative path: corrupted tar → success=false, sink NEVER invoked")
    void corruptedTarFailsCleanly(@TempDir Path tempDir) throws IOException {
        // 32 KiB of pseudo-random junk: not a tar at all.
        Path junkFile = tempDir.resolve("junk.OTvis");
        byte[] junk = new byte[32 * 1024];
        for (int i = 0; i < junk.length; i++) {
            junk[i] = (byte) ((i * 1103515245 + 12345) & 0xFF);
        }
        Files.write(junkFile, junk);

        NoOpDerivedDatasetWriter sink = new NoOpDerivedDatasetWriter();
        OTvisTier2Pipeline pipeline = new OTvisTier2Pipeline(
                new OTvisFrameExtractor(),
                new OmeZarrStoreWriter(),
                sink);

        PipelineResult result = pipeline.run(
                junkFile, tempDir.resolve("zarr"),
                PARENT_DO_APP_ID, FILE_REF_APP_ID);

        assertThat(result.success).isFalse();
        assertThat(result.storeUrl).isNull();
        assertThat(result.partialReason)
                .isNotNull()
                .containsAnyOf("extractor failed", "ome-zarr write failed");

        // Sink was NEVER invoked — that's the load-bearing invariant.
        assertThat(sink.calls()).isEmpty();
    }

    @Test
    @DisplayName("degraded path: tar missing calibration → success=true with partialReason explaining the skip")
    void degradedNoCalibration(@TempDir Path tempDir) throws IOException {
        // Repack the fixture without sequence1/calibration.bin so the
        // extractor returns an ExtractedFrames bundle with calibrationLut
        // = null but the lock-in and raw frames intact.
        byte[] fixtureBytes;
        try (InputStream in = OTvisTier2PipelineTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(in)
                    .as("fixture %s must be on classpath", FIXTURE)
                    .isNotNull();
            fixtureBytes = OTvisFrameExtractor.readAllBytes(in);
        }
        Map<String, byte[]> streams = OTvisFrameExtractor.readTarLenient(
                new java.io.ByteArrayInputStream(fixtureBytes));
        streams.remove("sequence1/calibration.bin");

        // Decode and verify our expectation: calibrationLut is null and
        // the lock-in frames are intact. The raw uint16 frame in
        // sequence1/f<N>.bin requires the calibration LUT to be decoded
        // (see OTvisFrameExtractor.decodeStreamPair) and is therefore
        // dropped with a "no calibration LUT is available" reason. That
        // is the exact "degraded" shape we want the pipeline to surface.
        ExtractedFrames preview = OTvisFrameExtractor.decodeFromStreams(streams);
        assertThat(preview.calibrationLut).isNull();
        assertThat(preview.lockInResult).isNotEmpty();
        assertThat(preview.rawCalibrated).isEmpty();
        assertThat(preview.partialReason)
                .as("extractor should explain why raw frames were dropped")
                .isNotNull();

        // Build a synthetic .OTvis tar with the calibration entry
        // removed. We feed the decoded streams back through a tar
        // archiver so the pipeline's tar reader path executes.
        Path strippedTar = tempDir.resolve("nocal.OTvis");
        writeTar(strippedTar, streams);

        NoOpDerivedDatasetWriter sink = new NoOpDerivedDatasetWriter();
        OTvisTier2Pipeline pipeline = new OTvisTier2Pipeline(
                new OTvisFrameExtractor(),
                new OmeZarrStoreWriter(),
                sink);

        PipelineResult result = pipeline.run(
                strippedTar, tempDir.resolve("zarr"),
                PARENT_DO_APP_ID, FILE_REF_APP_ID);

        assertThat(result.success).isTrue();
        assertThat(result.storeUrl).isNotNull();
        assertThat(result.partialReason)
                .isNotNull()
                .contains("calibration");
        assertThat(result.annotations).containsEntry("calibrationApplied", "false");

        // Sink was still notified — degradation is not failure.
        assertThat(sink.calls()).hasSize(1);
    }

    @Test
    @DisplayName("sink exception is swallowed: success=true, partialReason carries the reason")
    void sinkExceptionDoesNotFailPipeline(@TempDir Path tempDir) throws IOException {
        Path otvisFile = copyFixtureTo(tempDir.resolve("sample.OTvis"));
        DerivedDatasetWriter throwingSink = (a, b, c, d) -> {
            throw new RuntimeException("simulated sink failure");
        };

        OTvisTier2Pipeline pipeline = new OTvisTier2Pipeline(
                new OTvisFrameExtractor(),
                new OmeZarrStoreWriter(),
                throwingSink);

        PipelineResult result = pipeline.run(
                otvisFile, tempDir.resolve("zarr"),
                PARENT_DO_APP_ID, FILE_REF_APP_ID);

        // Store IS on disk — the sink only owns the runtime side.
        assertThat(result.success).isTrue();
        assertThat(result.storeUrl).isNotNull();
        assertThat(result.partialReason)
                .isNotNull()
                .contains("simulated sink failure");
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private static Path copyFixtureTo(Path target) throws IOException {
        try (InputStream in = OTvisTier2PipelineTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(in)
                    .as("fixture %s must be on classpath", FIXTURE)
                    .isNotNull();
            Files.copy(in, target);
        }
        return target;
    }

    /**
     * Write a minimal POSIX tar of the named entries to {@code target}.
     * Used by the degraded test to repack the fixture without the
     * calibration LUT. Uses commons-compress (already on the classpath
     * via the extractor's reader).
     */
    private static void writeTar(Path target, Map<String, byte[]> entries) throws IOException {
        try (var out = Files.newOutputStream(target);
             var tar = new org.apache.commons.compress.archivers.tar.TarArchiveOutputStream(out)) {
            tar.setLongFileMode(org.apache.commons.compress.archivers.tar.TarArchiveOutputStream.LONGFILE_POSIX);
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                var entry = new org.apache.commons.compress.archivers.tar.TarArchiveEntry(e.getKey());
                entry.setSize(e.getValue().length);
                tar.putArchiveEntry(entry);
                tar.write(e.getValue());
                tar.closeArchiveEntry();
            }
            tar.finish();
        }
    }
}
