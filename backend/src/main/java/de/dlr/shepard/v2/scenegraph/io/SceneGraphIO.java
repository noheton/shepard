package de.dlr.shepard.v2.scenegraph.io;

import java.util.List;

/**
 * SCENEGRAPH-REST-1 — composite response for
 * {@code GET /v2/scene-graphs/{appId}}.
 *
 * <p>Returns the full scene graph in a single payload: the scene
 * header, all frames, and all joints. Clients do not need multiple
 * calls to assemble the tree.
 */
public record SceneGraphIO(
  DigitalTwinSceneIO scene,
  List<CoordinateFrameIO> frames,
  List<JointIO> joints
) {}
