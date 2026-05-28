package de.dlr.shepard.plugin.fileformat.thermography;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Immutable outcome of one {@link OTvisTier2Pipeline#run} invocation.
 *
 * <p>Mirrors the {@code partialReason} convention from
 * {@link ExtractedFrames}: {@link #success} {@code = true} alone means
 * "tier-2 produced an OME-Zarr store and notified the sink", but the
 * extraction may still have been degraded (missing calibration, empty
 * raw frames) — that's surfaced via {@link #partialReason}.
 *
 * <ul>
 *   <li>{@code success = true, partialReason = null} — fully lossless.</li>
 *   <li>{@code success = true, partialReason != null} — store was
 *       written and the sink was notified, but the inputs were
 *       degraded.</li>
 *   <li>{@code success = false, partialReason != null} — the pipeline
 *       did not produce a store (extractor threw, writer threw, or the
 *       inputs were too broken to encode). The sink was NOT invoked.</li>
 * </ul>
 *
 * <p>The annotations map carries the cross-reference bag the runtime
 * persists on the derived dataset entity (see
 * {@link de.dlr.shepard.plugin.fileformat.thermography.spi.DerivedDatasetWriter}).
 * On a failed run the map may still carry the {@code derivedFrom} +
 * {@code kind} keys so an audit consumer can record that an attempt was
 * made.
 */
public final class PipelineResult {

    public final boolean success;
    public final String storeUrl;
    public final String partialReason;
    public final Map<String, String> annotations;

    PipelineResult(
            boolean success,
            String storeUrl,
            String partialReason,
            Map<String, String> annotations) {
        this.success = success;
        this.storeUrl = storeUrl;
        this.partialReason = partialReason;
        this.annotations = annotations == null
                ? Collections.unmodifiableMap(new LinkedHashMap<>())
                : Collections.unmodifiableMap(new LinkedHashMap<>(annotations));
    }
}
