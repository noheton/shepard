package de.dlr.shepard.v2.admin.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * F3 — One row from the {@code permission_audit_log} Postgres table,
 * returned by {@code GET /v2/admin/permission-audit/log}.
 *
 * <p>Distinct from {@link PermissionAuditEntryIO} (which describes orphan
 * entities from the Neo4j graph). This class is the Postgres audit-trail shape.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "PermissionAuditLogEntry")
public class PermissionAuditLogEntryIO {

  @Schema(required = true, description = "Auto-incremented row id.")
  private long id;

  @Schema(required = true, description = "UTC timestamp when the change was recorded.")
  private String occurredAt;

  @Schema(required = true, description = "appId of the entity whose permissions changed.")
  private String entityAppId;

  @Schema(required = false, nullable = true, description = "Entity kind label, e.g. 'Collection'.")
  private String entityKind;

  @Schema(required = false, nullable = true, description = "Username of the actor who made the change.")
  private String actorUsername;

  @Schema(required = true, description = "Action type: GRANT, REVOKE, or UPDATE.")
  private String action;

  @Schema(required = false, nullable = true, description = "JSON detail of what changed (before/after).")
  private String detailJson;
}
