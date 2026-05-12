package de.dlr.shepard.v2.admin.io;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.util.Date;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * One row in the {@code GET /v2/admin/instance-admins} response.
 * Carries the user's username, the source(s) of the grant, and the
 * audit metadata recorded on the {@code :HAS_ROLE} edge (for
 * Neo4j-sourced grants).
 *
 * <p>Designed in {@code aidocs/51 §6} — the CLI's table render reads
 * exactly this shape.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "InstanceAdminGrant")
public class InstanceAdminGrantIO {

  @Schema(required = true, description = "The user's username (the canonical user identifier).")
  private String username;

  /**
   * Source of the grant. One of:
   * <ul>
   *   <li>{@code "Neo4j"} — shepard-internal {@code :HAS_ROLE} edge
   *   <li>{@code "IdP"} — the role-string in the IdP claim mapped
   *       to {@code instance-admin}
   *   <li>{@code "both"} — both sources agree (the auto-cleanup
   *       sweeper will demote the Neo4j edge once observed)
   * </ul>
   */
  @Schema(required = true, example = "Neo4j", description = "Grant source: Neo4j / IdP / both")
  private String source;

  @Schema(
    required = false,
    nullable = true,
    description = "Username (or 'bootstrap') that granted the role. Null for IdP-only grants."
  )
  private String grantedBy;

  @JsonFormat(shape = JsonFormat.Shape.STRING)
  @Schema(
    required = false,
    nullable = true,
    format = "date-time",
    description = "When the grant was created. Null for IdP-only grants."
  )
  private Date grantedAt;
}
