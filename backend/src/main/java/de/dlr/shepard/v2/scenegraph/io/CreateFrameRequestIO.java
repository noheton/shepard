package de.dlr.shepard.v2.scenegraph.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SCENEGRAPH-REST-1 — {@code POST /v2/scene-graphs/{appId}/frames} request body.
 *
 * <p>{@code parentFrameAppId} may be omitted to add the first / root
 * frame of an empty scene — the service will then set the scene's
 * {@code rootFrameAppId} to the created frame's appId.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateFrameRequestIO {

  @Schema(description = "Short human-readable frame label.", example = "tool0") private String name;

  @Schema(description = "appId of the parent frame; omit to add the root frame of an empty scene.")
  private String parentFrameAppId;

  @Schema(description = "Translation x relative to parent (m).") private Double x;
  @Schema(description = "Translation y relative to parent (m).") private Double y;
  @Schema(description = "Translation z relative to parent (m).") private Double z;

  @Schema(description = "Rotation about local x-axis (roll) in radians.") private Double rx;
  @Schema(description = "Rotation about local y-axis (pitch) in radians.") private Double ry;
  @Schema(description = "Rotation about local z-axis (yaw) in radians.") private Double rz;

  @Schema(description = "Frame discriminator. Defaults to FRAME when null.")
  private FrameKind kind;
}
