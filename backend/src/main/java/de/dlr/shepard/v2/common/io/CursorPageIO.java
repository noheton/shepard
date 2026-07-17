package de.dlr.shepard.v2.common.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response envelope for cursor-based (time-window) pagination.
 *
 * <p>Unlike offset-page pagination, cursor endpoints do not know the total DB
 * count. Navigation is driven by {@code hasMore} + {@code nextCursor}:
 * pass {@code nextCursor} as {@code ?until=} on the next request to walk
 * backward in time through an append-only event stream.
 */
@Schema(name = "CursorPage", description = "A cursor-paginated window of items.")
public record CursorPageIO<T>(
    @Schema(description = "Items in this window, sorted most-recent first.") List<T> items,
    @Schema(description = "Maximum window size honoured by the server (mirrors ?pageSize=).") int pageSize,
    @Schema(description = "True when the window is full and more rows may exist.") boolean hasMore,
    @Schema(description = "Epoch-ms of the oldest item in this window. Pass as ?until= on the next request. Null when hasMore=false.") Long nextCursor
) {}
