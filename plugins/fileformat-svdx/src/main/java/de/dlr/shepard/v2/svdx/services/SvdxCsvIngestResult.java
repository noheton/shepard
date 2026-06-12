package de.dlr.shepard.v2.svdx.services;

/**
 * V2CONV-A7 — plain result shape for {@link SvdxCsvIngestionService#ingest}.
 *
 * <p>Replaces the former {@code SvdxIngestResponseIO} REST body. The
 * {@link de.dlr.shepard.v2.svdx.transform.SvdxCsvTransformExecutor} only needs the
 * derived {@code timeseriesReferenceAppId} to build its
 * {@link de.dlr.shepard.spi.transform.TransformResult#reference}; the remaining
 * counts are surfaced for logging / diagnosability and so a future admin surface
 * can report them without re-querying.
 *
 * @param timeseriesReferenceAppId      appId of the (new or replayed) derived TimeseriesReference
 * @param timeseriesReferenceShepardId  numeric Shepard id of that reference
 * @param timeseriesContainerAppId      appId of the container the channels landed in (may be null)
 * @param channelCount                  count of channels carrying ≥1 data point
 * @param rowCount                      per-channel sample count, max across channels
 * @param unmatchedChannelCount         count of channels with no SVDX-manifest metadata
 * @param idempotentReplay              true when the call short-circuited on an existing reference
 */
public record SvdxCsvIngestResult(
    String timeseriesReferenceAppId,
    long timeseriesReferenceShepardId,
    String timeseriesContainerAppId,
    int channelCount,
    int rowCount,
    int unmatchedChannelCount,
    boolean idempotentReplay) {}
