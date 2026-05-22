package de.dlr.shepard.plugins.v1compat.io;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * V1COMPAT.0 — RFC 7396 merge-patch body for
 * {@code PATCH /v2/admin/legacy/v1/config}. Phase 1 minimal shape:
 * only the {@code enabled} flag is patchable.
 *
 * <p>{@code Boolean} (boxed) so a missing-from-JSON value reads as
 * {@code null} (per RFC 7396 "absent = leave alone"), distinct from
 * an explicit {@code "enabled":false} payload.
 *
 * <p>Phase 2 may add fields; Phase 1 deliberately keeps the patch
 * shape minimal — adding new patchable fields is additive (any
 * legacy field omission stays no-op).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LegacyV1ConfigPatchIO(Boolean enabled) {}
