package de.dlr.shepard.v2.admin.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * One row in the {@code GET /v2/admin/permission-audit} response —
 * a {@code BasicEntity} that has no {@code :has_permissions} edge and
 * therefore would be denied by the C3-fixed {@code getRoles}
 * fail-closed default.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PermissionAuditEntry")
public class PermissionAuditEntryIO {

  @Schema(required = true, description = "Neo4j-side numeric id of the orphan entity.")
  private long id;

  @Schema(required = false, nullable = true, description = "appId (UUID v7) if populated by L2a/L2b.")
  private String appId;

  @Schema(
    required = true,
    description = "The :BasicEntity sub-label list (e.g. ['Collection', 'BasicEntity']) — useful for triage."
  )
  private java.util.List<String> labels;

  @Schema(required = false, nullable = true, description = "Entity name if any — passed through from the Named mixin.")
  private String name;
}
