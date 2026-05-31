package de.dlr.shepard.v2.containers.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * UI21-SIZEBAR-DATA — lightweight cardinality summary for a container.
 *
 * <p>Returned by:
 * <ul>
 *   <li>{@code GET /v2/timeseries-containers/{id}/summary} — {@code cardinality} = channel count</li>
 *   <li>{@code GET /v2/file-containers/{id}/summary} — {@code cardinality} = file count</li>
 *   <li>{@code GET /v2/structured-data-containers/{id}/summary} — {@code cardinality} = record count</li>
 * </ul>
 *
 * <p>The field is {@code Integer} (nullable) rather than {@code int} to stay consistent with
 * the codebase rule "schema changes are additive and nullable."  A {@code null} value means
 * the count is unavailable (e.g. the backend could not compute it cheaply).
 */
@Schema(name = "ContainerCardinality")
public record ContainerCardinalityIO(

  @Schema(
    description = "Per-kind item count: channel count for TIMESERIES, " +
      "file count for FILE, record count for STRUCTUREDDATA. " +
      "Null if the count is unavailable.",
    nullable = true
  )
  Integer cardinality

) {}
