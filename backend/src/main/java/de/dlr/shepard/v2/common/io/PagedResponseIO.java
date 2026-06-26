package de.dlr.shepard.v2.common.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "PagedResponse", description = "A page of items with pagination metadata.")
public record PagedResponseIO<T>(
    @Schema(description = "Items on this page.") List<T> items,
    @Schema(description = "Total item count before paging.") long total,
    @Schema(description = "Zero-based page index returned.") int page,
    @Schema(description = "Maximum items per page honoured by the server.") int pageSize
) {}
