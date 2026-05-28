package de.dlr.shepard.plugin.fileformat.thermography;

import de.dlr.shepard.plugin.fileformat.thermography.omezarr.OmeZarrStoreWriter;
import de.dlr.shepard.plugin.fileformat.thermography.spi.DerivedDatasetWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * High-level wiring of the tier-2 OTvis flow: extract → write OME-Zarr →
 * notify the sink.
 *
 * <p>Per CLAUDE.md "secondary writes are fire-and-forget" the pipeline
 * never throws past its public surface for a recoverable issue: extractor
 * exceptions, writer exceptions, sink exceptions all collapse into a
 * {@link PipelineResult} carrying {@code success = false} and a
 * descriptive {@code partialReason}. The only way the pipeline returns
 * {@code success = true} is when:
 *
 * <ol>
 *   <li>The extractor produced an {@link ExtractedFrames} bundle (it may
 *       carry its own {@code partialReason} — that propagates into the
 *       pipeline result).</li>
 *   <li>The OME-Zarr writer produced a non-null store URL.</li>
 *   <li>The sink's {@code writeOmeZarrDataset} call did not throw.</li>
 * </ol>
 *
 * <p>The sink's call is the boundary between this plugin and the Shepard
 * runtime; this pipeline does not own the runtime's persistence
 * decisions. If the runtime sink chooses to swallow its own errors, the
 * pipeline still reports {@code success = true} because the OME-Zarr
 * store was written successfully (the audit trail of what the runtime
 * did with it lives in the runtime's {@code :Activity} stream, not here).
 *
 * <p><b>Threading:</b> instances are thread-safe iff the underlying
 * extractor, writer, and sink are. The supplied implementations are all
 * stateless or carry only immutable internal state.
 */
public final class OTvisTier2Pipeline {

    private final OTvisFrameExtractor extractor;
    private final OmeZarrStoreWriter writer;
    private final DerivedDatasetWriter sink;

    /**
     * @param extractor frame extractor (typically a fresh
     *                  {@link OTvisFrameExtractor})
     * @param writer    OME-Zarr v3 store writer (typically a fresh
     *                  {@link OmeZarrStoreWriter})
     * @param sink      runtime-supplied derived-dataset writer; the
     *                  pipeline calls
     *                  {@link DerivedDatasetWriter#writeOmeZarrDataset}
     *                  once with the resulting store URL on success
     */
    public OTvisTier2Pipeline(
            OTvisFrameExtractor extractor,
            OmeZarrStoreWriter writer,
            DerivedDatasetWriter sink) {
        if (extractor == null || writer == null || sink == null) {
            throw new IllegalArgumentException(
                    "extractor, writer, sink must all be non-null");
        }
        this.extractor = extractor;
        this.writer = writer;
        this.sink = sink;
    }

    /**
     * Run the full extract → write → notify chain on one
     * {@code .OTvis} file.
     *
     * @param otvisFile               path to the source {@code .OTvis}
     *                                 tar; must exist and be readable
     * @param omeZarrRoot             directory under which the
     *                                 {@code thermography/} store prefix
     *                                 is created
     * @param parentDataObjectAppId   appId of the parent {@code
     *                                 DataObject} (propagated to the
     *                                 sink)
     * @param fileReferenceAppId      appId of the source {@code
     *                                 FileReference} (propagated to the
     *                                 sink and used as the in-store
     *                                 directory key)
     * @return a {@link PipelineResult}; never {@code null}
     */
    public PipelineResult run(
            Path otvisFile,
            Path omeZarrRoot,
            String parentDataObjectAppId,
            String fileReferenceAppId) {

        Map<String, String> annotations = new LinkedHashMap<>();
        annotations.put("kind", "thermography-omezarr");
        annotations.put("derivedFrom",
                fileReferenceAppId == null ? "" : fileReferenceAppId);

        // 1. Extraction. The extractor's only documented "throw past public
        // surface" condition is "the input isn't a tar at all"; we catch
        // any IOException defensively.
        ExtractedFrames frames;
        try {
            frames = extractor.extract(otvisFile);
        } catch (IOException | RuntimeException e) {
            return new PipelineResult(
                    false,
                    null,
                    "extractor failed: " + e.getMessage(),
                    annotations);
        }

        // 2. Annotation enrichment from the extracted bundle.
        if (!frames.lockInResult.isEmpty()) {
            annotations.put("amplitudeShape",
                    frames.lockInResult.size() + "," + frames.height + "," + frames.width);
            annotations.put("phaseShape",
                    frames.lockInResult.size() + "," + frames.height + "," + frames.width);
        }
        if (!frames.rawCalibrated.isEmpty()) {
            annotations.put("rawShape",
                    frames.rawCalibrated.size() + "," + frames.height + "," + frames.width);
        }
        annotations.put("calibrationApplied",
                Boolean.toString(frames.calibrationLut != null
                        && !frames.rawCalibrated.isEmpty()));

        // 3. OME-Zarr write.
        String storeUrl;
        try {
            storeUrl = writer.write(frames, omeZarrRoot, fileReferenceAppId);
        } catch (IOException | RuntimeException e) {
            return new PipelineResult(
                    false,
                    null,
                    "ome-zarr write failed: " + e.getMessage(),
                    annotations);
        }

        // 4. Sink notification. Secondary write — never lets a sink
        // exception fail the pipeline (the store IS on disk, so the
        // extract+write half is durable).
        String sinkReason = null;
        try {
            sink.writeOmeZarrDataset(
                    parentDataObjectAppId,
                    fileReferenceAppId,
                    storeUrl,
                    annotations);
        } catch (RuntimeException e) {
            sinkReason = "sink failed: " + e.getMessage();
        }

        // 5. Build the result. Carry the extractor's partialReason and
        // the sink reason both into the pipeline's partialReason.
        String partialReason = combineReasons(
                degradedReason(frames),
                sinkReason);

        return new PipelineResult(true, storeUrl, partialReason, annotations);
    }

    /**
     * Build a human-readable "this was degraded" string from a
     * non-failed but lossy extraction.
     */
    private static String degradedReason(ExtractedFrames frames) {
        StringBuilder sb = new StringBuilder();
        if (frames.partialReason != null) {
            sb.append(frames.partialReason);
        }
        if (frames.calibrationLut == null && !frames.rawCalibrated.isEmpty()) {
            appendReason(sb,
                    "calibration LUT missing — raw frames written without"
                            + " calibration applied");
        }
        if (!frames.rawCalibrated.isEmpty() && frames.calibrationLut == null) {
            // Already covered above; the duplicate guard is harmless.
        }
        if (frames.rawCalibrated.isEmpty()) {
            appendReason(sb, "no raw frames extracted");
        }
        if (frames.lockInResult.isEmpty()) {
            appendReason(sb, "no lock-in frames extracted");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    private static String combineReasons(String a, String b) {
        if (a == null && b == null) return null;
        if (a == null) return b;
        if (b == null) return a;
        return a + "; " + b;
    }

    private static void appendReason(StringBuilder sb, String reason) {
        if (sb.length() > 0) sb.append("; ");
        sb.append(reason);
    }
}
