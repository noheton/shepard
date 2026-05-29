package de.dlr.shepard.v2.scenegraph.io;

import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;

/**
 * SCENEGRAPH-REST-1 — request body for
 * {@code POST /v2/scene-graphs/{appId}/joints}.
 *
 * <p>{@code parentFrameAppId} and {@code childFrameAppId} are the
 * appIds of the parent and child {@link de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame}
 * nodes this joint connects. {@code type} is nullable (null treated
 * as {@link JointType#FIXED}).
 */
public record CreateJointIO(
  String name,
  String parentFrameAppId,
  String childFrameAppId,
  double axisX,
  double axisY,
  double axisZ,
  double limitMin,
  double limitMax,
  String type,
  double homeAngle
) {

  /**
   * Apply this IO onto a freshly-constructed {@link Joint} entity.
   */
  public Joint toEntity() {
    Joint joint = new Joint();
    joint.setName(name);
    joint.setParentFrameAppId(parentFrameAppId);
    joint.setChildFrameAppId(childFrameAppId);
    joint.setAxisX(axisX);
    joint.setAxisY(axisY);
    joint.setAxisZ(axisZ);
    joint.setLimitMin(limitMin);
    joint.setLimitMax(limitMax);
    joint.setType(resolveType(type));
    joint.setHomeAngle(homeAngle);
    return joint;
  }

  private static JointType resolveType(String t) {
    if (t == null || t.isBlank()) return JointType.FIXED;
    try {
      return JointType.valueOf(t.toUpperCase());
    } catch (IllegalArgumentException e) {
      return JointType.FIXED;
    }
  }
}
