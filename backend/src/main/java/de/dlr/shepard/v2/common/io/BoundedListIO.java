package de.dlr.shepard.v2.common.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response envelope for depth-bounded graph traversal results (e.g. predecessor-chain,
 * successor-chain). Unlike paginated responses, these results are bounded by a caller-supplied
 * {@code depth} parameter rather than a page cursor or offset.
 *
 * <p>Callers should not treat the result as a page — there is no "next page" and the
 * {@code appliedDepth} field reflects what the server actually honoured (which may be less
 * than the requested depth if the graph is shallower or the server's max-depth cap applies).
 */
@Schema(name = "BoundedList", description = "A depth-bounded traversal result (not paginated).")
public record BoundedListIO<T>(
    @Schema(description = "Items reachable within appliedDepth hops, ordered by traversal distance.") List<T> items,
    @Schema(description = "Depth cap actually applied by the server (≤ the requested depth and ≤ the server max of 50).") int appliedDepth
) {}
