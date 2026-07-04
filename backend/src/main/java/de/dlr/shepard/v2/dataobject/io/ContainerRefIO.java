package de.dlr.shepard.v2.dataobject.io;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * REF-1 — compact reference-to-container summary embedded inside
 * {@link DataObjectDetailV2IO#getContainers()}. Each entry describes one
 * reference node (by its {@code referenceAppId}) and the container it points
 * to (by {@code containerAppId} / {@code containerName}).
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

  @Schema(
    readOnly = true,
    nullable = true,
    description = "UUID v7 of the reference node. Null on pre-L2a rows."
  )
  private String referenceAppId;

  public ContainerRefIO(String containerAppId, String containerName, String referenceAppId) {
    this.containerAppId = containerAppId;
    this.containerName = containerName;
    this.referenceAppId = referenceAppId;
  }
}
