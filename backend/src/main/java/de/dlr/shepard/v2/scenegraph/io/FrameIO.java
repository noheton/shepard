package de.dlr.shepard.v2.scenegraph.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SCENEGRAPH-REST-1 — wire shape for {@link CoordinateFrame}.
 *
 * <p>Carries the appId (UUID v7), name, parent appId pointer, the
 * six-scalar local transform (translation x/y/z metres, rotation
 * rx/ry/rz radians), and the discriminator {@link FrameKind}. Fields
 * mirror the entity 1:1 — the IO is a wire boundary, not a different
 * shape.
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A coordinate frame in a DigitalTwinScene's frame tree. " +
  "Six-scalar local transform relative to parent: translation (x,y,z) in metres, " +
  "rotation (rx,ry,rz) in radians (Euler).")
public class FrameIO {

  @Schema(description = "UUID v7 appId of this frame.", example = "019e7243-f995-7914-be80-53e367aa5172")
  private String appId;

  @Schema(description = "Short human-readable frame label.", example = "tool0")
  private String name;

  @Schema(description = "appId of the parent frame, or null for a root frame.")
  private String parentFrameAppId;

  @Schema(description = "Translation x relative to parent (metres).") private Double x;
  @Schema(description = "Translation y relative to parent (metres).") private Double y;
  @Schema(description = "Translation z relative to parent (metres).") private Double z;

  @Schema(description = "Rotation about local x-axis (roll) in radians.") private Double rx;
  @Schema(description = "Rotation about local y-axis (pitch) in radians.") private Double ry;
  @Schema(description = "Rotation about local z-axis (yaw) in radians.") private Double rz;

  @Schema(description = "Frame discriminator. Null defaults to FRAME.")
  private FrameKind kind;

  public FrameIO(CoordinateFrame f) {
    this.appId = f.getAppId();
    this.name = f.getName();
    this.parentFrameAppId = f.getParentFrameAppId();
    this.x = f.getX();
    this.y = f.getY();
    this.z = f.getZ();
    this.rx = f.getRx();
    this.ry = f.getRy();
    this.rz = f.getRz();
    this.kind = f.getKind();
  }
}
