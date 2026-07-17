package de.dlr.shepard.v2.snapshot.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-SNAPSHOT-LIST-TOTAL — response envelope for
 * {@code GET /v2/snapshots} (the cross-collection snapshot list).
 *
 * <p>Replaces the misleading {@code PagedResponseIO} envelope whose
 * {@code total} was the unfiltered DB count while {@code items} were
 * permission-filtered. Callers building "page N of M" UIs would compute a
 * wrong page count from the old {@code total}.
 *
 * <p>Navigation model: request with {@code ?page=0&pageSize=50}; when
 * {@code hasMore=true} increment {@code page} and request again. No grand
 * total is reported because computing a permission-accurate total across all
 * collections is O(collection-count) and the autocomplete use-case (the
 * {@code SNAPSHOT-LIST-1-FE} diff picker) only needs to know whether to
 * show a "load more" affordance.
 */
@Schema(
    name = "SnapshotListPage",
    description =
        "One offset-paginated page of snapshot summaries, filtered to the caller's readable collections."
)
public record SnapshotListPageIO<T>(
    @Schema(description = "Snapshot summaries on this page, newest first, filtered to collections the caller can Read.") List<T> items,
    @Schema(description = "Zero-based page index (mirrors ?page=).") int page,
    @Schema(description = "Page size honoured by the server (mirrors ?pageSize=, clamped to [1,200]).") int pageSize,
    @Schema(description =
        "True when the server fetched a full page from the database, indicating more rows likely exist. "
        + "Use to drive a 'Load more' button or infinite-scroll trigger.")
    boolean hasMore
) {}
