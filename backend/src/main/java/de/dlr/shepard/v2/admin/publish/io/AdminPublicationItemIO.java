package de.dlr.shepard.v2.admin.publish.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.publish.entities.Publication;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * RDM-003 — per-PID row in the admin-wide publications list.
 *
 * <p>Carries every operator-relevant field from the {@link Publication}
 * entity. Mirrors {@link de.dlr.shepard.v2.publish.io.PublicationIO}
 * but targets the admin audit surface rather than the per-entity
 * publishing surface — the admin variant does not need a resolver URL
 * (admin can construct it) but adds {@link #mintedByUserAppId} for
 * the operator audit trail.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "AdminPublicationItem", description = "RDM-003 per-PID admin audit row.")
public record AdminPublicationItemIO(
  @Schema(required = true, description = "UUID v7 appId of this Publication row.") String appId,
  @Schema(required = true, description = "The minted persistent identifier.") String pid,
  @Schema(description = "URL-segment of the entity kind (e.g. 'data-objects').") String entityKind,
  @Schema(description = "AppId of the published entity.") String entityAppId,
  @Schema(required = true, description = "Wall-clock at which the PID was minted.") Instant mintedAt,
  @Schema(description = "Identifier of the minter adapter that produced this row (e.g. 'local', 'epic', 'datacite').") String minterId,
  @Schema(description = "Username of the user who triggered the publish call.") String publishedBy,
  @Schema(required = true, description = "1-based version ordinal among all Publications for this entity.") Integer versionNumber,
  @Schema(
    description = "null on active Publications; 'retired' after DELETE /v2/{kind}/{appId}/publish. " +
    "KIP1f mutability marker — never deleted, always auditable."
  )
  String digitalObjectMutability
) {
  /**
   * Project a {@link Publication} entity onto the admin wire shape.
   *
   * @param p the persisted Publication (never null — callers should guard)
   */
  public static AdminPublicationItemIO from(Publication p) {
    if (p == null) return null;
    Integer version = p.getVersionNumber();
    if (version == null || version < 1) version = 1;
    return new AdminPublicationItemIO(
      p.getAppId(),
      p.getPid(),
      p.getEntityKind(),
      p.getEntityAppId(),
      p.getMintedAt() == null ? null : Instant.ofEpochMilli(p.getMintedAt()),
      p.getMinterId(),
      p.getPublishedBy(),
      version,
      p.getDigitalObjectMutability()
    );
  }
}
