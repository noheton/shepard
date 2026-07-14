package de.dlr.shepard.plugins.v1compat.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugins.v1compat.entities.LegacyV1Config;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V1COMPAT.0/V1C1 — JSON shape returned by
 * {@code GET /v2/admin/legacy/v1/config}. Phase 2 shape: exposes the
 * runtime-mutable {@code enabled} and {@code suppressDeprecationHeaders}
 * flags, the {@code stripAppIdFromResponses} field (declared; wiring
 * deferred to V1C2), plus the audit triple.
 *
 * <p>{@code @JsonInclude(NON_NULL)} so {@code updatedAt} /
 * {@code updatedBy} drop out of the JSON until the first flip
 * happens — a fresh singleton stays minimal.
 *
 * @param enabled                    runtime-mutable master toggle (default true)
 * @param suppressDeprecationHeaders when true, filter skips the three
 *                                   Deprecation/Link/X-Shepard-Legacy headers
 *                                   (default false)
 * @param stripAppIdFromResponses    declared; behaviour wiring deferred to V1C2
 *                                   (default false)
 * @param appId                      singleton appId so a client can correlate
 *                                   audit entries to this exact row
 * @param updatedAt                  most recent PATCH timestamp; null until first
 *                                   flip
 * @param updatedBy                  most recent PATCH actor sub; null until first
 *                                   flip
 */
@Schema(name = "LegacyV1ConfigIO", description = "Legacy v1 compat plugin runtime config returned by GET /v2/admin/legacy/v1/config.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LegacyV1ConfigIO(
  boolean enabled,
  boolean suppressDeprecationHeaders,
  boolean stripAppIdFromResponses,
  String appId,
  String updatedAt,
  String updatedBy
) {
  /** Project an entity onto the IO record. */
  public static LegacyV1ConfigIO from(LegacyV1Config cfg) {
    Long updated = cfg.getUpdatedAt();
    return new LegacyV1ConfigIO(
      cfg.isEnabled(),
      cfg.isSuppressDeprecationHeaders(),
      cfg.isStripAppIdFromResponses(),
      cfg.getAppId(),
      updated == null ? null : Instant.ofEpochMilli(updated).toString(),
      cfg.getUpdatedBy()
    );
  }
}
