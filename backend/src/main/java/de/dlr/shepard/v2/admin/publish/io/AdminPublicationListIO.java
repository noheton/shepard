package de.dlr.shepard.v2.admin.publish.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * RDM-003 — paginated envelope returned by
 * {@code GET /v2/admin/publications}.
 *
 * <p>The page/size/total fields mirror the pattern used by other
 * paginated admin endpoints (e.g. the permission-audit-log list).
 */
@Schema(name = "AdminPublicationList", description = "RDM-003 paginated envelope for instance-wide PID audit list.")
public record AdminPublicationListIO(
  @Schema(required = true, description = "Publications on this page, ordered mintedAt DESC.") List<AdminPublicationItemIO> items,
  @Schema(required = true, description = "0-based page index supplied by the caller.") int page,
  @Schema(required = true, description = "Page size supplied by the caller.") int size,
  @Schema(required = true, description = "Total count of :Publication rows across the instance (regardless of page).") long totalCount
) {}
