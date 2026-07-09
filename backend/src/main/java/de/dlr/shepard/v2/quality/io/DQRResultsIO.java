package de.dlr.shepard.v2.quality.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-DQR-EVALUATE-BARE-LIST — envelope returned by
 * {@code POST /v2/collections/{collectionAppId}/dqr/evaluate}.
 *
 * <p>{@code total} is the uncapped result count; when {@code truncated} is
 * {@code true} the caller should increase {@code ?limit=} or add async evaluation.
 */
@Schema(description = "Envelope returned by POST /v2/collections/{collectionAppId}/dqr/evaluate, containing all DQR results with truncation metadata.")
public record DQRResultsIO(
    List<DQRResultIO> results,
    boolean truncated,
    long total
) {}
