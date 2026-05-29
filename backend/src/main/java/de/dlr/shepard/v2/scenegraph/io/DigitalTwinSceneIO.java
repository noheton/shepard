package de.dlr.shepard.v2.scenegraph.io;

import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;

/**
 * SCENEGRAPH-REST-1 — response IO for a {@link DigitalTwinScene} node.
 *
 * <p>All fields are nullable; a freshly-created scene with only a
 * {@code name} will return {@code null} for the optional fields.
 */
public record DigitalTwinSceneIO(
  String appId,
  String name,
  String description,
  String sourceFileAppId,
  String rootFrameAppId
) {

  /** Construct from an OGM entity. */
  public DigitalTwinSceneIO(DigitalTwinScene e) {
    this(e.getAppId(), e.getName(), e.getDescription(), e.getSourceFileAppId(), e.getRootFrameAppId());
  }
}
