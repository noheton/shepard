package de.dlr.shepard.plugin.fileformat.thermography.spi;

import java.util.Map;

/**
 * SPI extension point for tier-2 OTvis frame extraction.
 *
 * <p>The plugin module only declares the contract; the implementation is
 * supplied by the Shepard runtime when the plugin is wired in (post
 * Jandex-hang resolution, row {@code OTVIS-WIRE-AGGREGATOR-1}). Until that
 * lands the runtime side is exercised by the test-only
 * {@code NoOpDerivedDatasetWriter}.
 *
 * <p>The writer is invoked by {@link
 * de.dlr.shepard.plugin.fileformat.thermography.OTvisTier2Pipeline} once
 * the tier-2 OME-Zarr store has been persisted (either to a local temp
 * directory in tests, or to the Garage S3 bucket in production per
 * {@code aidocs/ops/vis-s2-garage-omezarr-storage-policy.md}). The runtime
 * impl is expected to:
 *
 * <ol>
 *   <li>Create a new SD-payload entity attached to the parent
 *       {@code DataObject} carrying {@code storeUrl} as the body and the
 *       {@code annotations} bag as cross-reference annotations.</li>
 *   <li>Persist the required back-pointer
 *       {@code urn:shepard:thermography:derivedFrom = fileReferenceAppId}
 *       so the derived dataset is discoverable from the source
 *       {@code FileReference} via the standard semantic-annotation
 *       surface.</li>
 *   <li>Treat the write as a secondary effect — per CLAUDE.md
 *       "secondary writes are fire-and-forget" the runtime must catch and
 *       log any persistence failure rather than propagating it back into
 *       the parse pipeline.</li>
 * </ol>
 *
 * <p>Annotation keys callers should expect on the {@code annotations} bag
 * (the runtime may persist additional keys, but these are the minimum the
 * pipeline contract guarantees):
 * <ul>
 *   <li>{@code derivedFrom} — appId of the source FileReference</li>
 *   <li>{@code kind} — always {@code "thermography-omezarr"} for this
 *       pipeline (used by readers to dispatch to the right viewer
 *       plugin)</li>
 *   <li>{@code amplitudeShape} — comma-separated dimension sizes for the
 *       amplitude array, or absent if no lock-in frames were extracted</li>
 *   <li>{@code phaseShape} — comma-separated dimension sizes for the
 *       phase array, or absent if no lock-in frames were extracted</li>
 *   <li>{@code rawShape} — comma-separated dimension sizes for the raw
 *       array, or absent if no raw frames were extracted</li>
 *   <li>{@code calibrationApplied} — {@code "true"} when the calibration
 *       LUT was found and applied to raw frames; {@code "false"}
 *       otherwise</li>
 * </ul>
 */
public interface DerivedDatasetWriter {

    /**
     * Persist a derived OME-Zarr dataset describing tier-2 thermography
     * frames.
     *
     * @param parentDataObjectAppId appId of the {@code DataObject} that
     *                               holds the {@code FileReference} whose
     *                               {@code .OTvis} was parsed. Never
     *                               {@code null}.
     * @param fileReferenceAppId    appId of the source {@code
     *                               FileReference}. Never {@code null}.
     * @param storeUrl              the canonical URL of the OME-Zarr root
     *                               (e.g. signed Garage S3 URL,
     *                               {@code s3://shepard-payloads/...}, or a
     *                               local {@code file:///...} URL in
     *                               tests). Never {@code null}.
     * @param annotations           cross-reference annotations to attach
     *                               to the derived dataset entity. Keys
     *                               documented in the class javadoc.
     *                               Never {@code null}; may be empty.
     */
    void writeOmeZarrDataset(
            String parentDataObjectAppId,
            String fileReferenceAppId,
            String storeUrl,
            Map<String, String> annotations);
}
