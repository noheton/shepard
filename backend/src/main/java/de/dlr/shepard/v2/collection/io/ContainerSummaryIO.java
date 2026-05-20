package de.dlr.shepard.v2.collection.io;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Lightweight container summary returned by
 * {@code GET /v2/collections/{appId}/referenced-containers}.
 */
public class ContainerSummaryIO {

  @Schema(description = "Neo4j OGM id — use for legacy navigation routes.")
  private long id;

  @Schema(description = "Application-level UUID-v7 identifier.")
  private String appId;

  @Schema(description = "Display name of the container.")
  private String name;

  @Schema(
    description = "Container type.",
    enumeration = { "TIMESERIES", "FILE", "STRUCTUREDDATA", "BASIC" }
  )
  private String containerType;

  public ContainerSummaryIO() {}

  public ContainerSummaryIO(long id, String appId, String name, String containerType) {
    this.id = id;
    this.appId = appId;
    this.name = name;
    this.containerType = containerType;
  }

  public long getId() { return id; }
  public void setId(long id) { this.id = id; }

  public String getAppId() { return appId; }
  public void setAppId(String appId) { this.appId = appId; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getContainerType() { return containerType; }
  public void setContainerType(String containerType) { this.containerType = containerType; }
}
