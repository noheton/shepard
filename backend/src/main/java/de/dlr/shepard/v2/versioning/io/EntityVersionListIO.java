package de.dlr.shepard.v2.versioning.io;

import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * ENT1a wire shape for {@code GET /v2/{kind}/{appId}/versions}.
 *
 * <p>Wrapped in a top-level object (rather than a bare JSON array)
 * so future additive fields (e.g. {@code current: <appId>} once
 * ENT1c reconciles with KIP1h's "current publication" semantics)
 * don't break wire-shape compatibility.
 */
@Schema(name = "EntityVersionList", description = "ENT1a list response for the version-list endpoint.")
public record EntityVersionListIO(
  @Schema(required = true, description = "Versions ordered by versionOrdinal DESC (newest-first).") List<EntityVersionIO> versions
) {}
