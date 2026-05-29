package de.dlr.shepard.v2.scenegraph.io;

import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;

/**
 * SCENEGRAPH-REST-1 — response IO for a {@link Joint} node.
 *
 * <p>{@code type} is serialised as a String (the enum name) for the
 * same stability reason as {@link CoordinateFrameIO#kind()}.
 */
public record JointIO(
  String appId,
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

  /** Construct from an OGM entity. */
  public JointIO(Joint e) {
    this(
      e.getAppId(),
      e.getName(),
      e.getParentFrameAppId(),
      e.getChildFrameAppId(),
      e.getAxisX(),
      e.getAxisY(),
      e.getAxisZ(),
      e.getLimitMin(),
      e.getLimitMax(),
      e.getType() != null ? e.getType().name() : JointType.FIXED.name(),
      e.getHomeAngle()
    );
  }
}
