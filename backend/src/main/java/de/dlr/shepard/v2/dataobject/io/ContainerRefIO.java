package de.dlr.shepard.v2.dataobject.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * REF-1 — compact reference-to-container summary embedded inside
 * {@link DataObjectDetailV2IO#getContainers()}. Each entry describes one
 * reference node (by its {@code referenceId} / {@code referenceAppId}) and
 * the container it points to (by {@code containerAppId} / {@code containerName}).
 *
 * <p>The {@code referenceId} is the OGM-internal long id of the reference
 * node (e.g. {@code :TimeseriesReference}) used by the upstream
 * {@code /shepard/api/…} payload endpoints. {@code referenceAppId} is the
 * UUID v7 equivalent (may be {@code null} on pre-L2a rows).
 *
 * <p>The {@code containerId} is the OGM-internal long id of the container
 * node itself (e.g. {@code :TimeseriesContainer}), used by
 * {@code /v2/timeseries-containers/{containerId}/stats|chart-view} and the
 * upstream {@code /shepard/api/timeseriesContainers/{containerId}/available}
 * endpoint. MCP and analytics clients use this to avoid a separate lookup.
 */
@Data
@NoArgsConstructor
@Schema(
  name = "ContainerRef",
  description = "Compact link from a DataObject reference node to its container."
)
public class ContainerRefIO {

  @Schema(readOnly = true, description = "appId of the container (UUID v7).")
  private String containerAppId;

  @Schema(readOnly = true, description = "Human-readable name of the container.")
  private String containerName;

  @Schema(readOnly = true, description = "Legacy long OGM id of the container node. Used by /v2/timeseries-containers/{containerId}/stats and /shepard/api/timeseriesContainers/{containerId}/available.")
  private long containerId;

  @Schema(readOnly = true, description = "Legacy long id of the reference node (upstream-compat). Used by /shepard/api/.../timeseriesReferences/{referenceId}/payload.")
  private long referenceId;

  @Schema(
    readOnly = true,
    nullable = true,
    description = "UUID v7 of the reference node. Null on pre-L2a rows."
  )
  private String referenceAppId;

  public ContainerRefIO(String containerAppId, String containerName, long containerId, long referenceId, String referenceAppId) {
    this.containerAppId = containerAppId;
    this.containerName = containerName;
    this.containerId = containerId;
    this.referenceId = referenceId;
    this.referenceAppId = referenceAppId;
  }
}
