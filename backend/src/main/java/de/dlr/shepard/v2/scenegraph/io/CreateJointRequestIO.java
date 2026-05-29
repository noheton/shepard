package de.dlr.shepard.v2.scenegraph.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SCENEGRAPH-REST-1 — {@code POST /v2/scene-graphs/{appId}/joints} request body.
 *
 * <p>{@code parentFrameAppId} + {@code childFrameAppId} are required —
 * a joint without endpoints is meaningless. {@code type} defaults to
 * {@link JointType#FIXED} when null. Axis and limits default to zero
 * when omitted.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CreateJointRequestIO {

  @Schema(description = "Short human-readable joint label.") private String name;

  @Schema(description = "appId of the parent CoordinateFrame.", required = true) private String parentFrameAppId;
  @Schema(description = "appId of the child CoordinateFrame.", required = true) private String childFrameAppId;

  @Schema(description = "x-component of unit axis vector.") private Double axisX;
  @Schema(description = "y-component of unit axis vector.") private Double axisY;
  @Schema(description = "z-component of unit axis vector.") private Double axisZ;

  @Schema(description = "Minimum joint position.") private Double limitMin;
  @Schema(description = "Maximum joint position.") private Double limitMax;

  @Schema(description = "Joint discriminator (REVOLUTE / PRISMATIC / FIXED / CONTINUOUS).")
  private JointType type;

  @Schema(description = "Home position the joint snaps to on load.") private Double homeAngle;
}
