package de.dlr.shepard.v2.containers.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * UI21-SIZEBAR-DATA — lightweight cardinality summary for a container.
 *
 * <p>Returned by the {@code GET /v2/{kind}-containers/{id}/summary} endpoints.
 * The {@code cardinality} field is the per-kind item count:
 *
 * <ul>
 *   <li>TimeseriesContainer — number of distinct channels (TimescaleDB rows with this containerId)</li>
 *   <li>FileContainer       — number of files (Neo4j {@code :ShepardFile} nodes linked via
 *       {@code file_in_container})</li>
 *   <li>StructuredDataContainer — number of structured-data payloads (Neo4j
 *       {@code :StructuredData} nodes linked via {@code structureddata_in_container})</li>
 * </ul>
 *
 * <p>The {@code lastUpdated} field is an ISO-8601 instant captured at query time (i.e.
 * "now" for live containers). Future implementations may supply the most-recent-write
 * timestamp from the underlying substrate; for now it is the request instant.
 *
 * <p>This payload is intentionally tiny — it exists solely to drive the sizebar
 * bar-chart in the frontend container list, so the response is cheap to produce and
 * cheap to parse.
 */
@Schema(name = "ContainerCardinalitySummary",
  description = "Lightweight per-kind cardinality summary for the sizebar metric.")
public record ContainerCardinalitySummaryIO(

  @Schema(
    description = "Number of items in this container (channels / files / payloads depending on kind).",
    required = true,
    minimum = "0"
  )
  long cardinality,

  @Schema(
    description = "ISO-8601 instant at which this summary was captured.",
    required = true,
    example = "2026-05-29T10:15:30Z"
  )
  String lastUpdated
) {}
