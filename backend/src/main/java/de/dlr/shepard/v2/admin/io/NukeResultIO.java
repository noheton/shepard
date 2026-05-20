package de.dlr.shepard.v2.admin.io;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Response body for {@code POST /v2/admin/instance/nuke}. */
@Data
@AllArgsConstructor
@Schema(name = "NukeResult")
public class NukeResultIO {

  @Schema(description = "Neo4j data nodes deleted (Collections, Containers, DataObjects, References, etc.).")
  private long deletedNeo4jNodes;

  @Schema(description = "MongoDB collections dropped (file + structured-data container payloads).")
  private int deletedMongoCollections;

  @Schema(description = "Timeseries channel records deleted from Postgres.")
  private long deletedTimeseriesRecords;

  @Schema(description = "Provenance Activity nodes deleted from Neo4j.")
  private long deletedActivities;

  @Schema(description = "Human-readable summary of what was preserved.")
  private String message;
}
