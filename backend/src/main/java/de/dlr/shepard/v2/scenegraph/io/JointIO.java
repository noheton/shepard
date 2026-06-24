package de.dlr.shepard.v2.scenegraph.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SCENEGRAPH-REST-1 — wire shape for {@link Joint}.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A kinematic joint connecting two CoordinateFrames in a DigitalTwinScene.")
public class JointIO {

  @Schema(description = "UUID v7 appId of this joint.") private String appId;
  @Schema(description = "Short human-readable joint label.", example = "joint_1") private String name;

  @Schema(description = "appId of the parent (proximal) CoordinateFrame.") private String parentFrameAppId;
  @Schema(description = "appId of the child (distal) CoordinateFrame driven by this joint.")
  private String childFrameAppId;

  @Schema(description = "x-component of unit axis vector in the parent frame.") private Double axisX;
  @Schema(description = "y-component of unit axis vector.") private Double axisY;
  @Schema(description = "z-component of unit axis vector.") private Double axisZ;

  @Schema(description = "Minimum joint position (rad for REVOLUTE/CONTINUOUS, m for PRISMATIC).")
  private Double limitMin;
  @Schema(description = "Maximum joint position (same units as limitMin).") private Double limitMax;

  @Schema(description = "Joint discriminator. Null defaults to FIXED.")
  private JointType type;

  @Schema(description = "Home position (same units as limitMin).") private Double homeAngle;

  public JointIO(Joint j) {
    this.appId = j.getAppId();
    this.name = j.getName();
    this.parentFrameAppId = j.getParentFrameAppId();
    this.childFrameAppId = j.getChildFrameAppId();
    this.axisX = j.getAxisX();
    this.axisY = j.getAxisY();
    this.axisZ = j.getAxisZ();
    this.limitMin = j.getLimitMin();
    this.limitMax = j.getLimitMax();
    this.type = j.getType();
    this.homeAngle = j.getHomeAngle();
  }
}
