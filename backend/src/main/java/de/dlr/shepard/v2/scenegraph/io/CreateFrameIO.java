package de.dlr.shepard.v2.scenegraph.io;

import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;

/**
 * SCENEGRAPH-REST-1 — request body for
 * {@code POST /v2/scene-graphs/{appId}/frames}.
 *
 * <p>{@code parentFrameAppId} is nullable (null = root frame for
 * this scene). {@code kind} is nullable (null treated as
 * {@link FrameKind#FRAME} by the service layer).
 *
 * <p>Translation/rotation fields default to {@code 0.0} (identity
 * transform) when omitted from the JSON body.
 */
public record CreateFrameIO(
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

  /**
   * Apply this IO onto a freshly-constructed {@link CoordinateFrame}
   * entity. The {@code kind} string is resolved to {@link FrameKind}
   * (falls back to {@link FrameKind#FRAME} on unknown or null values).
   */
  public CoordinateFrame toEntity() {
    CoordinateFrame frame = new CoordinateFrame();
    frame.setName(name);
    frame.setParentFrameAppId(parentFrameAppId);
    frame.setX(x);
    frame.setY(y);
    frame.setZ(z);
    frame.setRx(rx);
    frame.setRy(ry);
    frame.setRz(rz);
    frame.setKind(resolveKind(kind));
    return frame;
  }

  private static FrameKind resolveKind(String k) {
    if (k == null || k.isBlank()) return FrameKind.FRAME;
    try {
      return FrameKind.valueOf(k.toUpperCase());
    } catch (IllegalArgumentException e) {
      return FrameKind.FRAME;
    }
  }
}
