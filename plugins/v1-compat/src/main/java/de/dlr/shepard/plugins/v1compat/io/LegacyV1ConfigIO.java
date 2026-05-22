package de.dlr.shepard.plugins.v1compat.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.plugins.v1compat.entities.LegacyV1Config;
import java.util.Date;

/**
 * V1COMPAT.0 — JSON shape returned by
 * {@code GET /v2/admin/legacy/v1/config}. Phase 1 minimal shape:
 * exposes the runtime-mutable {@code enabled} flag plus the audit
 * triple (last update timestamp, last updater) so operators can see
 * who flipped the surface when.
 *
 * <p>{@code @JsonInclude(NON_NULL)} so {@code updatedAt} /
 * {@code updatedBy} drop out of the JSON until the first flip
 * happens — a fresh singleton stays minimal.
 *
 * @param enabled    runtime-mutable master toggle (default true)
 * @param appId      singleton appId so a client can correlate audit
 *                   entries to this exact row
 * @param updatedAt  most recent PATCH timestamp; null until first
 *                   flip
 * @param updatedBy  most recent PATCH actor sub; null until first
 *                   flip
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LegacyV1ConfigIO(boolean enabled, String appId, Date updatedAt, String updatedBy) {
  /** Project an entity onto the IO record. */
  public static LegacyV1ConfigIO from(LegacyV1Config cfg) {
    Long updated = cfg.getUpdatedAt();
    return new LegacyV1ConfigIO(
      cfg.isEnabled(),
      cfg.getAppId(),
      updated == null ? null : new Date(updated),
      cfg.getUpdatedBy()
    );
  }
}
