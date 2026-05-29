package de.dlr.shepard.v2.scenegraph.io;

import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;

/**
 * SCENEGRAPH-REST-1 — response IO for a {@link CoordinateFrame} node.
 *
 * <p>{@code kind} is serialised as a String (the enum name) so the
 * response shape stays stable even as the {@link FrameKind} enum grows
 * additive values in later phases.
 */
public record CoordinateFrameIO(
  String appId,
  String name,
  String parentFrameAppId,
  double x,
  double y,
  double z,
  double rx,
  double ry,
  double rz,
  String kind
) {

  /** Construct from an OGM entity. */
  public CoordinateFrameIO(CoordinateFrame e) {
    this(
      e.getAppId(),
      e.getName(),
      e.getParentFrameAppId(),
      e.getX(),
      e.getY(),
      e.getZ(),
      e.getRx(),
      e.getRy(),
      e.getRz(),
      e.getKind() != null ? e.getKind().name() : FrameKind.FRAME.name()
    );
  }
}
