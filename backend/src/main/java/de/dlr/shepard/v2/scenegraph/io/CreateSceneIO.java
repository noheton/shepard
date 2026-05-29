package de.dlr.shepard.v2.scenegraph.io;

import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;

/**
 * SCENEGRAPH-REST-1 — request body for
 * {@code POST /v2/scene-graphs}.
 *
 * <p>All fields are nullable — the minimal create call is an empty
 * body (the server mints an {@code appId} and creates a nameless
 * empty scene).
 */
public record CreateSceneIO(
  String name,
  String description,
  String sourceFileAppId
) {

  /**
   * Apply the non-null fields of this IO onto a freshly-constructed
   * {@link DigitalTwinScene} entity.
   */
  public DigitalTwinScene toEntity() {
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setName(name);
    scene.setDescription(description);
    scene.setSourceFileAppId(sourceFileAppId);
    return scene;
  }
}
