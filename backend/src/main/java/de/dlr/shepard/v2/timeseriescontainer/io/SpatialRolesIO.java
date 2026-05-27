package de.dlr.shepard.v2.timeseriescontainer.io;

import java.util.UUID;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * TS-AXIS-AUTO — response shape for
 * {@code GET /v2/timeseries-containers/{containerId}/channels/spatial-roles}.
 *
 * <p>Each non-null field carries the {@code shepardId} of the channel annotated
 * for that role. Null means "no annotation found for this role."
 */
@Schema(description = "Per-role channel assignments for the Trace3D view recipe builder.")
public record SpatialRolesIO(

  @Schema(description = "shepardId of the channel assigned to the X spatial axis.", nullable = true)
  UUID x,

  @Schema(description = "shepardId of the channel assigned to the Y spatial axis.", nullable = true)
  UUID y,

  @Schema(description = "shepardId of the channel assigned to the Z spatial axis.", nullable = true)
  UUID z,

  @Schema(description = "shepardId of the channel assigned to Euler A (rotation around world Z).", nullable = true)
  UUID rot_a,

  @Schema(description = "shepardId of the channel assigned to Euler B (rotation around world Y).", nullable = true)
  UUID rot_b,

  @Schema(description = "shepardId of the channel assigned to Euler C (rotation around world X).", nullable = true)
  UUID rot_c

) {}
