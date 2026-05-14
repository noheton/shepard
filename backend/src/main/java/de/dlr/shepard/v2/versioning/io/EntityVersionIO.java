package de.dlr.shepard.v2.versioning.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.versioning.EntityVersion;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * ENT1a wire shape for a single {@link EntityVersion} row returned by
 * {@code GET /v2/{kind}/{appId}/versions} and friends.
 *
 * <p>Strips Neo4j-OGM internals (the {@code Long id}, the linked
 * {@code Permissions} graph) and serialises the version's stable
 * scalar fields. Per-version ACL is queried via the sibling
 * {@code GET /v2/{kind}/{appId}/versions/{label}/permissions}
 * endpoint (not inlined here so the version-list response stays cheap).
 *
 * <p>{@code createdAt} is rendered as an ISO-8601 instant on the wire;
 * the underlying property is epoch-millis. {@code versionOrdinal} is
 * an {@code int} (always set; never null) so list-sorting on the
 * frontend is deterministic without re-parsing the label.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "EntityVersion", description = "ENT1a EntityVersion row per aidocs/16 ENT1a.")
public record EntityVersionIO(
  @Schema(required = true, description = "Application-level identifier of the version (UUID v7).") String appId,
  @Schema(
    required = true,
    description = "User-facing version label (default: 'v<ordinal>'; may be user-supplied e.g. '1.0.0-rc.1')."
  )
  String versionLabel,
  @Schema(required = true, description = "Monotonic-per-parent ordinal — the 'true' sort order.") int versionOrdinal,
  @Schema(required = true, description = "Server-side wall-clock at version creation.") Instant createdAt,
  @Schema(required = true, description = "Username of the creator.") String createdBy,
  @Schema(required = true, description = "Parent entity kind ('collection' or 'data-object').") String parentEntityKind,
  @Schema(required = true, description = "AppId of the parent entity.") String parentEntityAppId,
  @Schema(description = "Optional release note for this version.") String note
) {
  /**
   * Project an {@link EntityVersion} onto the wire shape. Returns
   * {@code null} when the input is {@code null} (so the resource
   * layer can pass through "absent" cleanly).
   */
  public static EntityVersionIO from(EntityVersion v) {
    if (v == null) return null;
    return new EntityVersionIO(
      v.getAppId(),
      v.getVersionLabel(),
      v.getVersionOrdinal(),
      v.getCreatedAt() == null ? null : Instant.ofEpochMilli(v.getCreatedAt()),
      v.getCreatedBy(),
      v.getParentEntityKind(),
      v.getParentEntityAppId(),
      nullIfBlank(v.getNote())
    );
  }

  private static String nullIfBlank(String s) {
    return s == null || s.isBlank() ? null : s;
  }
}
