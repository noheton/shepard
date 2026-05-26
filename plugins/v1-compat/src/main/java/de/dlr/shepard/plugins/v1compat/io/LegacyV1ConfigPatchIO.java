package de.dlr.shepard.plugins.v1compat.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * V1COMPAT.0/V1C1 — RFC 7396 merge-patch body for
 * {@code PATCH /v2/admin/legacy/v1/config}.
 *
 * <p>{@code Boolean} (boxed) so a missing-from-JSON value reads as
 * {@code null} (per RFC 7396 "absent = leave alone"), distinct from
 * an explicit {@code false} payload. Each field is independently
 * nullable: a caller patching only {@code enabled} leaves
 * {@code suppressDeprecationHeaders} untouched.
 *
 * @param enabled                    flip the master toggle; null = leave alone
 * @param suppressDeprecationHeaders flip header suppression; null = leave alone
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LegacyV1ConfigPatchIO(Boolean enabled, Boolean suppressDeprecationHeaders) {

  /** Convenience ctor for callers that only patch {@code enabled} (Phase 1 compat). */
  public LegacyV1ConfigPatchIO(Boolean enabled) {
    this(enabled, null);
  }
}
