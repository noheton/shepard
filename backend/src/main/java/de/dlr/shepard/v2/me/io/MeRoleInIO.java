package de.dlr.shepard.v2.me.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Wire shape for {@code GET /v2/me/role-in/{collectionAppId}} — the
 * data behind the "Role in current context" header chip designed in
 * {@code aidocs/51 §9.4} (U1c2).
 *
 * <p>Fields mirror the existing {@code PermissionsService.Roles}
 * shape with a flat boolean for {@code isInstanceAdmin} appended so
 * the UI can render the elevated-context chip alongside the
 * per-Collection role chip in a single fetch.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "MeRoleIn")
public class MeRoleInIO {

  @Schema(required = true, description = "Collection appId the role applies to.")
  private String collectionAppId;

  @Schema(required = true, description = "Caller can read entities under this Collection.")
  private boolean read;

  @Schema(required = true, description = "Caller can create / update entities under this Collection.")
  private boolean write;

  @Schema(
    required = true,
    description = "Caller can edit permissions / delete on this Collection (also implies an owner-like UX role)."
  )
  private boolean manage;

  @Schema(
    required = true,
    description = "Caller carries the `instance-admin` role (dual-sourced from IdP claim AND/OR `:HAS_ROLE` " +
    "edge, deduped on the JWT principal). The role chip surfaces this as a side-by-side chip so admins " +
    "can tell they're operating in an elevated context."
  )
  private boolean isInstanceAdmin;
}
