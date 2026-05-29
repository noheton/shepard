package de.dlr.shepard.v2.scenegraph.services;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.scenegraph.daos.CoordinateFrameDAO;
import de.dlr.shepard.v2.scenegraph.daos.DigitalTwinSceneDAO;
import de.dlr.shepard.v2.scenegraph.daos.JointDAO;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.io.CreateFrameIO;
import de.dlr.shepard.v2.scenegraph.io.CreateJointIO;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneIO;
import de.dlr.shepard.v2.scenegraph.io.PatchFrameIO;
import de.dlr.shepard.v2.scenegraph.io.SceneGraphIO;
import de.dlr.shepard.v2.scenegraph.io.CoordinateFrameIO;
import de.dlr.shepard.v2.scenegraph.io.DigitalTwinSceneIO;
import de.dlr.shepard.v2.scenegraph.io.JointIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * SCENEGRAPH-REST-1 — service layer for scene-graph CRUD.
 *
 * <p>Writes the {@code [:HAS_FRAME]}, {@code [:HAS_JOINT]},
 * {@code [:HAS_PARENT_FRAME]}, {@code [:JOINT_PARENT]}, and
 * {@code [:JOINT_CHILD]} Neo4j relationship edges via
 * {@code Session.query()} (parametrised Cypher with {@code MERGE}
 * to stay idempotent). Records one {@link de.dlr.shepard.provenance.entities.Activity}
 * per mutating call via {@link ProvenanceService}.
 *
 * <p>Provenance writes are best-effort — see "Always: secondary writes
 * are fire-and-forget" in {@code CLAUDE.md}. An exception on
 * {@code ProvenanceService.record()} is swallowed and logged at
 * {@code DEBUG}; the primary operation is unaffected.
 *
 * <p>Permissions note: The REST resource ({@code SceneGraphRest})
 * gates all callers on {@code @Authenticated}. Per-scene ownership
 * checks (tied to a DataObject) are deferred to
 * {@code SCENEGRAPH-PERM-1} — see the resource Javadoc.
 */
@ApplicationScoped
public class SceneGraphService {

  @Inject
  DigitalTwinSceneDAO sceneDAO;

  @Inject
  CoordinateFrameDAO frameDAO;

  @Inject
  JointDAO jointDAO;

  @Inject
  ProvenanceService provenanceService;

  // ─── Scene CRUD ──────────────────────────────────────────────────────────────

  /**
   * Create a new empty {@link DigitalTwinScene}.
   *
   * @param body    creation IO (name, description, sourceFileAppId — all nullable)
   * @param userId  authenticated caller for provenance capture
   * @return the persisted scene with its minted {@code appId}
   */
  public DigitalTwinScene createScene(CreateSceneIO body, String userId) {
    DigitalTwinScene scene = (body != null) ? body.toEntity() : new DigitalTwinScene();
    sceneDAO.createOrUpdate(scene);
    recordActivity("CREATE", "DigitalTwinScene", scene.getAppId(), userId,
      "POST /v2/scene-graphs — created scene " + scene.getAppId(), "POST",
      "v2/scene-graphs", 201);
    return scene;
  }

  /**
   * Load a scene graph: the {@link DigitalTwinScene} header plus all
   * its {@link CoordinateFrame}s and {@link Joint}s.
   *
   * @param sceneAppId the scene's {@code appId}
   * @return a {@link SceneGraphIO} or {@code null} if not found
   */
  public SceneGraphIO getScene(String sceneAppId) {
    DigitalTwinScene scene = findSceneByAppId(sceneAppId);
    if (scene == null) return null;

    Collection<CoordinateFrame> frames = findFramesBySceneAppId(sceneAppId);
    Collection<Joint> joints = findJointsBySceneAppId(sceneAppId);

    return new SceneGraphIO(
      new DigitalTwinSceneIO(scene),
      frames.stream().map(CoordinateFrameIO::new).toList(),
      joints.stream().map(JointIO::new).toList()
    );
  }

  /**
   * Delete a scene and all its frames + joints.
   *
   * <p>Deletes all {@code :CoordinateFrame} and {@code :Joint} nodes
   * reachable from the scene via {@code [:HAS_FRAME]} /
   * {@code [:HAS_JOINT]} edges, then deletes the scene node itself.
   * Idempotent: safe to call even if the scene is already partially
   * deleted.
   *
   * @param sceneAppId the scene's {@code appId}
   * @param userId     authenticated caller for provenance capture
   * @return {@code true} if the scene existed and was deleted
   */
  public boolean deleteScene(String sceneAppId, String userId) {
    DigitalTwinScene scene = findSceneByAppId(sceneAppId);
    if (scene == null) return false;

    // Detach-delete all frames and joints reachable from this scene.
    Session live = liveSession();
    live.query(
      "MATCH (s:DigitalTwinScene {appId: $sceneAppId})-[:HAS_FRAME]->(f:CoordinateFrame) DETACH DELETE f",
      Map.of("sceneAppId", sceneAppId)
    );
    live.query(
      "MATCH (s:DigitalTwinScene {appId: $sceneAppId})-[:HAS_JOINT]->(j:Joint) DETACH DELETE j",
      Map.of("sceneAppId", sceneAppId)
    );

    if (scene.getId() != null) {
      sceneDAO.deleteByNeo4jId(scene.getId());
    }

    recordActivity("DELETE", "DigitalTwinScene", sceneAppId, userId,
      "DELETE /v2/scene-graphs/" + sceneAppId, "DELETE",
      "v2/scene-graphs/" + sceneAppId, 204);
    return true;
  }

  // ─── Frame CRUD ───────────────────────────────────────────────────────────────

  /**
   * Add a {@link CoordinateFrame} to an existing scene.
   *
   * <p>Writes {@code (scene)-[:HAS_FRAME]->(frame)} and, if
   * {@code parentFrameAppId} is non-null,
   * {@code (frame)-[:HAS_PARENT_FRAME]->(parent)}.
   *
   * @param sceneAppId  the owning scene's {@code appId}
   * @param body        frame creation IO
   * @param userId      authenticated caller
   * @return the persisted frame, or {@code null} if the scene was not found
   */
  public CoordinateFrame addFrame(String sceneAppId, CreateFrameIO body, String userId) {
    if (findSceneByAppId(sceneAppId) == null) return null;

    CoordinateFrame frame = body.toEntity();
    frameDAO.createOrUpdate(frame);

    Session live = liveSession();
    live.query(
      "MATCH (s:DigitalTwinScene {appId: $sceneAppId}), (f:CoordinateFrame {appId: $frameAppId}) " +
      "MERGE (s)-[:HAS_FRAME]->(f)",
      Map.of("sceneAppId", sceneAppId, "frameAppId", frame.getAppId())
    );

    if (frame.getParentFrameAppId() != null && !frame.getParentFrameAppId().isBlank()) {
      live.query(
        "MATCH (child:CoordinateFrame {appId: $childAppId}), (parent:CoordinateFrame {appId: $parentAppId}) " +
        "MERGE (child)-[:HAS_PARENT_FRAME]->(parent)",
        Map.of("childAppId", frame.getAppId(), "parentAppId", frame.getParentFrameAppId())
      );
    }

    recordActivity("CREATE", "CoordinateFrame", frame.getAppId(), userId,
      "POST /v2/scene-graphs/" + sceneAppId + "/frames — created frame " + frame.getAppId(),
      "POST", "v2/scene-graphs/" + sceneAppId + "/frames", 201);
    return frame;
  }

  /**
   * RFC 7396 merge-patch a {@link CoordinateFrame}.
   *
   * <p>Only non-null fields in {@code body} are applied. If
   * {@code parentFrameAppId} changes, the old
   * {@code [:HAS_PARENT_FRAME]} edge is removed and the new one
   * written.
   *
   * @param sceneAppId   the owning scene's {@code appId}
   * @param frameAppId   the frame's {@code appId}
   * @param body         patch IO (all nullable)
   * @param userId       authenticated caller
   * @return the updated frame, or {@code null} if frame not found
   */
  public CoordinateFrame patchFrame(String sceneAppId, String frameAppId, PatchFrameIO body, String userId) {
    CoordinateFrame frame = findFrameByAppId(frameAppId);
    if (frame == null) return null;

    // Validate frame belongs to this scene (relationship check).
    if (!frameBelongsToScene(sceneAppId, frameAppId)) return null;

    boolean parentChanged = false;
    String oldParent = frame.getParentFrameAppId();

    if (body != null) {
      if (body.name() != null) frame.setName(body.name());
      if (body.x() != null) frame.setX(body.x());
      if (body.y() != null) frame.setY(body.y());
      if (body.z() != null) frame.setZ(body.z());
      if (body.rx() != null) frame.setRx(body.rx());
      if (body.ry() != null) frame.setRy(body.ry());
      if (body.rz() != null) frame.setRz(body.rz());
      if (body.parentFrameAppId() != null) {
        String newParent = body.parentFrameAppId().isBlank() ? null : body.parentFrameAppId();
        parentChanged = !java.util.Objects.equals(oldParent, newParent);
        frame.setParentFrameAppId(newParent);
      }
    }

    frameDAO.createOrUpdate(frame);

    if (parentChanged) {
      Session live = liveSession();
      // Remove old parent edge
      if (oldParent != null) {
        live.query(
          "MATCH (child:CoordinateFrame {appId: $childAppId})-[r:HAS_PARENT_FRAME]->(:CoordinateFrame) DELETE r",
          Map.of("childAppId", frameAppId)
        );
      }
      // Write new parent edge
      String newParent = frame.getParentFrameAppId();
      if (newParent != null) {
        live.query(
          "MATCH (child:CoordinateFrame {appId: $childAppId}), (parent:CoordinateFrame {appId: $parentAppId}) " +
          "MERGE (child)-[:HAS_PARENT_FRAME]->(parent)",
          Map.of("childAppId", frameAppId, "parentAppId", newParent)
        );
      }
    }

    recordActivity("UPDATE", "CoordinateFrame", frameAppId, userId,
      "PATCH /v2/scene-graphs/" + sceneAppId + "/frames/" + frameAppId,
      "PATCH", "v2/scene-graphs/" + sceneAppId + "/frames/" + frameAppId, 200);
    return frame;
  }

  /**
   * Delete a {@link CoordinateFrame} from a scene.
   *
   * <p>Removes the frame node and all its relationships. Does NOT
   * recursively delete child frames — callers must delete children
   * first (or use the scene-delete endpoint which does a bulk purge).
   *
   * @param sceneAppId  the owning scene's {@code appId}
   * @param frameAppId  the frame's {@code appId}
   * @param userId      authenticated caller
   * @return {@code true} if the frame existed and was deleted
   */
  public boolean deleteFrame(String sceneAppId, String frameAppId, String userId) {
    CoordinateFrame frame = findFrameByAppId(frameAppId);
    if (frame == null) return false;
    if (!frameDeleteBelongsToScene(sceneAppId, frameAppId)) return false;

    if (frame.getId() != null) {
      frameDAO.deleteByNeo4jId(frame.getId());
    }

    recordActivity("DELETE", "CoordinateFrame", frameAppId, userId,
      "DELETE /v2/scene-graphs/" + sceneAppId + "/frames/" + frameAppId,
      "DELETE", "v2/scene-graphs/" + sceneAppId + "/frames/" + frameAppId, 204);
    return true;
  }

  // ─── Joint CRUD ───────────────────────────────────────────────────────────────

  /**
   * Add a {@link Joint} to an existing scene.
   *
   * <p>Writes {@code (scene)-[:HAS_JOINT]->(joint)},
   * {@code (joint)-[:JOINT_PARENT]->(parentFrame)}, and
   * {@code (joint)-[:JOINT_CHILD]->(childFrame)}.
   *
   * @param sceneAppId  the owning scene's {@code appId}
   * @param body        joint creation IO
   * @param userId      authenticated caller
   * @return the persisted joint, or {@code null} if scene not found
   */
  public Joint addJoint(String sceneAppId, CreateJointIO body, String userId) {
    if (findSceneByAppId(sceneAppId) == null) return null;

    Joint joint = body.toEntity();
    jointDAO.createOrUpdate(joint);

    Session live = liveSession();
    live.query(
      "MATCH (s:DigitalTwinScene {appId: $sceneAppId}), (j:Joint {appId: $jointAppId}) " +
      "MERGE (s)-[:HAS_JOINT]->(j)",
      Map.of("sceneAppId", sceneAppId, "jointAppId", joint.getAppId())
    );

    if (joint.getParentFrameAppId() != null && !joint.getParentFrameAppId().isBlank()) {
      live.query(
        "MATCH (j:Joint {appId: $jointAppId}), (f:CoordinateFrame {appId: $frameAppId}) " +
        "MERGE (j)-[:JOINT_PARENT]->(f)",
        Map.of("jointAppId", joint.getAppId(), "frameAppId", joint.getParentFrameAppId())
      );
    }

    if (joint.getChildFrameAppId() != null && !joint.getChildFrameAppId().isBlank()) {
      live.query(
        "MATCH (j:Joint {appId: $jointAppId}), (f:CoordinateFrame {appId: $frameAppId}) " +
        "MERGE (j)-[:JOINT_CHILD]->(f)",
        Map.of("jointAppId", joint.getAppId(), "frameAppId", joint.getChildFrameAppId())
      );
    }

    recordActivity("CREATE", "Joint", joint.getAppId(), userId,
      "POST /v2/scene-graphs/" + sceneAppId + "/joints — created joint " + joint.getAppId(),
      "POST", "v2/scene-graphs/" + sceneAppId + "/joints", 201);
    return joint;
  }

  // ─── lookup helpers ───────────────────────────────────────────────────────────

  /**
   * Find a scene by its {@code appId}. Returns {@code null} if not found.
   */
  public DigitalTwinScene findSceneByAppId(String appId) {
    Filter filter = new Filter("appId", ComparisonOperator.EQUALS, appId);
    Collection<DigitalTwinScene> results = sceneDAO.findMatching(filter);
    return results.isEmpty() ? null : results.iterator().next();
  }

  /**
   * Find a frame by its {@code appId}. Returns {@code null} if not found.
   */
  public CoordinateFrame findFrameByAppId(String appId) {
    Filter filter = new Filter("appId", ComparisonOperator.EQUALS, appId);
    Collection<CoordinateFrame> results = frameDAO.findMatching(filter);
    return results.isEmpty() ? null : results.iterator().next();
  }

  /**
   * Find all frames belonging to a scene via {@code [:HAS_FRAME]} edges.
   */
  public Collection<CoordinateFrame> findFramesBySceneAppId(String sceneAppId) {
    Session live = liveSession();
    return (Collection<CoordinateFrame>) live.query(
      CoordinateFrame.class,
      "MATCH (:DigitalTwinScene {appId: $sceneAppId})-[:HAS_FRAME]->(f:CoordinateFrame) RETURN f",
      Map.of("sceneAppId", sceneAppId)
    );
  }

  /**
   * Find all joints belonging to a scene via {@code [:HAS_JOINT]} edges.
   */
  public Collection<Joint> findJointsBySceneAppId(String sceneAppId) {
    Session live = liveSession();
    return (Collection<Joint>) live.query(
      Joint.class,
      "MATCH (:DigitalTwinScene {appId: $sceneAppId})-[:HAS_JOINT]->(j:Joint) RETURN j",
      Map.of("sceneAppId", sceneAppId)
    );
  }

  // ─── private helpers ──────────────────────────────────────────────────────────

  /**
   * Check whether a frame is connected to a scene via
   * {@code [:HAS_FRAME]} in Neo4j.
   */
  private boolean frameDeleteBelongsToScene(String sceneAppId, String frameAppId) {
    Session live = liveSession();
    Iterable<Map<String, Object>> result = live.query(
      "MATCH (:DigitalTwinScene {appId: $sceneAppId})-[:HAS_FRAME]->(f:CoordinateFrame {appId: $frameAppId}) " +
      "RETURN count(f) AS cnt",
      Map.of("sceneAppId", sceneAppId, "frameAppId", frameAppId)
    );
    for (Map<String, Object> row : result) {
      Object cnt = row.get("cnt");
      if (cnt instanceof Number n) return n.longValue() > 0;
    }
    return false;
  }

  /**
   * Used by patchFrame — same query, kept as a named method so tests
   * can verify both code paths.
   */
  private boolean frameBelongsToScene(String sceneAppId, String frameAppId) {
    return frameDeleteBelongsToScene(sceneAppId, frameAppId);
  }

  private Session liveSession() {
    return NeoConnector.getInstance().getNeo4jSession();
  }

  /**
   * Best-effort provenance record. Per "Always: secondary writes are
   * fire-and-forget" in {@code CLAUDE.md}: exceptions are swallowed
   * and logged at {@code DEBUG}; the caller's primary operation is
   * unaffected.
   */
  private void recordActivity(
    String actionKind,
    String targetKind,
    String targetAppId,
    String userId,
    String summary,
    String method,
    String path,
    int status
  ) {
    try {
      long now = System.currentTimeMillis();
      provenanceService.record(actionKind, targetKind, targetAppId, userId,
        summary, method, path, status, now, now);
    } catch (RuntimeException e) {
      Log.debugf(e, "SCENEGRAPH-REST-1: provenance capture skipped for %s %s", targetKind, targetAppId);
    }
  }
}
