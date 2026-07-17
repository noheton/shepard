package de.dlr.shepard.v2.quality.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-DQR-EVALUATE-BARE-LIST — envelope returned by
 * {@code POST /v2/collections/{collectionAppId}/dqr/evaluate}.
 *
 * <p>{@code total} is exact when {@code truncated} is {@code false}. When
 * {@code truncated} is {@code true}, {@code total} is approximate
 * ({@code maxItems + 1}, i.e. a lower bound) — the service stops evaluating
 * after {@code maxItems + 1} results to bound heap (APISIMP-DQR-EVAL-INMEM),
 * so the true count is unknown. Callers wanting more results increase
 * {@code ?maxItems=} (capped at 5 000) or wait for the async variant.
 */
@Schema(description = "Envelope returned by POST /v2/collections/{collectionAppId}/dqr/evaluate, containing all DQR results with truncation metadata.")
public record DQRResultsIO(
    List<DQRResultIO> results,
    boolean truncated,
    long total
) {}
