package de.dlr.shepard.v2.publish.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.publish.entities.Publication;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * KIP1a wire shape returned by {@code POST /v2/{kind}/{appId}/publish}.
 *
 * <p>Carries the freshly-minted PID + the mint timestamp + the
 * minter id that produced the row + the resolver URL clients should
 * use to dereference the PID at this shepard instance.
 *
 * <p>Designed in {@code aidocs/66 §4.1}. KIP1h additively grew the
 * record with {@link #versionNumber} — the Phase-1 version number
 * the {@code PublishService} computed before minting. KIP1f
 * additively grew the record with {@link #digitalObjectMutability}
 * — {@code null} on active Publications, {@code "retired"} after a
 * {@code DELETE /v2/{kind}/{appId}/publish} retire call. Clients
 * that don't parse these fields ignore them per the
 * additive-wire-shape convention.
 *
 * <p>The {@link #from(Publication, String)} factory defaults
 * {@code versionNumber} to {@code 1} on pre-KIP1h rows where the
 * property is null (V31 backfill sets the persisted value to 1; the
 * Optional in the JSON response normalises the missing-property case
 * for any rows that slipped through).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(name = "Publication", description = "KIP1a publication record per aidocs/66.")
public record PublicationIO(
  @Schema(required = true, description = "Application-level identifier of the Publication row (UUID v7).")
  String appId,
  @Schema(required = true, description = "The minted persistent identifier — string the active minter returned.")
  String pid,
  @Schema(required = true, description = "Server-side wall-clock at the moment the minter returned.")
  Instant mintedAt,
  @Schema(required = true, description = "Identifier of the minter that produced this row (e.g. 'local', 'epic').")
  String minterId,
  @Schema(
    required = true,
    description = "The fully-qualified URL where this PID can be dereferenced at this shepard instance — points at the public /v2/.well-known/kip resolver."
  )
  String resolverUrl,
  @Schema(description = "Username of the publisher.") String publishedBy,
  @Schema(description = "URL-segment of the published entity's kind (e.g. 'data-objects').") String entityKind,
  @Schema(description = "AppId of the published entity.") String entityAppId,
  @Schema(
    required = true,
    description = "KIP1h Phase-1 version number. 1-based ordinal of this Publication among the entity's :Publication rows."
  )
  Integer versionNumber,
  @Schema(
    description = "KIP1f mutability marker. null on active Publications; 'retired' after DELETE /v2/{kind}/{appId}/publish. " +
    "Read-only — the client cannot set this field; use the DELETE endpoint to retire."
  )
  String digitalObjectMutability
) {
  /**
   * Project a {@link Publication} onto the wire shape, computing the
   * resolver URL from the supplied base. Pre-KIP1h rows that have a
   * null {@code versionNumber} surface as {@code 1} in the wire
   * shape (the V31 backfill writes 1 onto every legacy row; this
   * Optional-default catches anything that slipped past the
   * migration).
   *
   * @param p           the persisted Publication
   * @param resolverUrl the {@code <shepard.url>/v2/.well-known/kip/{pid-suffix}}
   *                    URL clients should use to dereference the PID
   */
  public static PublicationIO from(Publication p, String resolverUrl) {
    if (p == null) return null;
    Integer version = p.getVersionNumber();
    if (version == null || version < 1) version = 1;
    return new PublicationIO(
      p.getAppId(),
      p.getPid(),
      p.getMintedAt() == null ? null : Instant.ofEpochMilli(p.getMintedAt()),
      p.getMinterId(),
      resolverUrl,
      p.getPublishedBy(),
      p.getEntityKind(),
      p.getEntityAppId(),
      version,
      p.getDigitalObjectMutability()
    );
  }
}
