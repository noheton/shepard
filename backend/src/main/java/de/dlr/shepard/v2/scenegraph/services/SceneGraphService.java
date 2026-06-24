package de.dlr.shepard.v2.scenegraph.services;

import de.dlr.shepard.common.identifier.AppIdGenerator;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.scenegraph.daos.CoordinateFrameDAO;
import de.dlr.shepard.v2.scenegraph.daos.DigitalTwinSceneDAO;
import de.dlr.shepard.v2.scenegraph.daos.JointDAO;
import de.dlr.shepard.v2.scenegraph.entities.CoordinateFrame;
import de.dlr.shepard.v2.scenegraph.entities.DigitalTwinScene;
import de.dlr.shepard.v2.scenegraph.entities.FrameKind;
import de.dlr.shepard.v2.scenegraph.entities.Joint;
import de.dlr.shepard.v2.scenegraph.entities.JointType;
import de.dlr.shepard.v2.scenegraph.io.CreateFrameRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateJointRequestIO;
import de.dlr.shepard.v2.scenegraph.io.CreateSceneRequestIO;
import de.dlr.shepard.v2.scenegraph.io.PatchFrameRequestIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.ogm.cypher.ComparisonOperator;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.model.Result;
import org.neo4j.ogm.session.Session;

/**
 * SCENEGRAPH-REST-1 — service-layer write coordinator for
 * {@code :DigitalTwinScene} + {@code :CoordinateFrame} + {@code :Joint}.
 *
 * <p>Owns the raw-Cypher MERGE / DELETE edge writes that the
 * DT1-PHASE-0 DAOs deliberately don't do (the scaffold ships pure
 * data; this row writes the edges). Each mutation also records a
 * PROV-O {@link Activity} via {@link ProvenanceService#record} and
 * wires a supplementary {@code :WAS_DERIVED_FROM} edge between the
 * current and previous {@code :Activity} for the same scene — giving
 * the audit trail a proper graph-walk for "what changed this scene
 * over time".
 *
 * <h2>Session strategy</h2>
 * <p>The DT1-PHASE-0 DAOs inherit {@code GenericDAO}'s constructor-time
 * cached session, which can be null at SCENEGRAPH-REST-1's first call
 * if Quarkus built the bean before {@code SessionFactory} finished
 * booting (CHOKE-03 / JupyterConfig pattern). To dodge that without
 * mutating the substrate scaffold, every read + write in this service
 * fetches {@link NeoConnector#getNeo4jSession()} per call — the same
 * pattern {@code JupyterConfigDAO} uses. The DT1-DAO-FRESH-SESSION
 * backlog row stays open as a clean-up: once the DAOs themselves carry
 * the fresh-session pattern, this service can shed the per-call fetch
 * and lean on the DAOs again.
 *
 * <h2>PROV-O edges per mutation</h2>
 * <ul>
 *   <li>{@link ProvenanceService#record} writes
 *       {@code (:Activity)-[:WAS_ASSOCIATED_WITH]->(:User)} +
 *       {@code (:Activity)-[:GENERATED|USED]->(:BasicEntity)} via
 *       {@code ActivityDAO.wireEdges}.</li>
 *   <li>This service additionally writes
 *       {@code (:Activity)-[:WAS_DERIVED_FROM]->(:Activity)} linking the
 *       new activity to the most-recent prior activity for the same
 *       scene's appId — giving the time-ordered audit chain an explicit
 *       edge that's traversable in a single Cypher hop. Fire-and-forget;
 *       failures are logged and swallowed per the secondary-write rule.</li>
 * </ul>
 */
@ApplicationScoped
public class SceneGraphService {

  @Inject DigitalTwinSceneDAO sceneDAO;
  @Inject CoordinateFrameDAO frameDAO;
  @Inject JointDAO jointDAO;
  @Inject ProvenanceService provenanceService;

  /**
   * SCENEGRAPH-LIST-1 — paginated page-result for {@code GET /v2/scene-graphs}.
   * Carries the row count (for envelope {@code total}) and per-row frame and
   * joint counts (for the list-item shape). All values come from a single
   * Cypher round-trip so this scales without an N+1.
   */
  public record SceneListRow(
    String appId,
    String name,
    String description,
    String sourceFileAppId,
    String rootFrameAppId,
    Long createdAt,
    Long updatedAt,
    long frameCount,
    long jointCount
  ) {}

  /** SCENEGRAPH-LIST-1 — page envelope (rows + total). */
  public record SceneListPage(List<SceneListRow> rows, long total) {}

  // ── Reads ─────────────────────────────────────────────────────────────────

  /** Load a scene by appId; {@code null} if not found. Fresh session per call. */
  public DigitalTwinScene findScene(String appId) {
    if (appId == null || appId.isBlank()) return null;
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return null;
    Collection<DigitalTwinScene> matches = live.loadAll(
      DigitalTwinScene.class,
      new Filter("appId", ComparisonOperator.EQUALS, appId),
      1
    );
    return matches == null || matches.isEmpty() ? null : matches.iterator().next();
  }

  /**
   * SCENEGRAPH-LIST-1 — paginated list of {@code :DigitalTwinScene} rows with
   * per-row frame + joint counts. Matches the existing scaffold posture (see
   * the class Javadoc on {@code SceneGraphRest}): no per-scene permission
   * gate is applied — the entire scene catalogue is visible to every
   * authenticated caller until {@code SCENEGRAPH-PERMS-1} ships.
   *
   * <p>Two Cypher round-trips: a count query for the envelope {@code total}
   * and a page query that aggregates frame + joint counts in the same
   * traversal so this scales without an N+1 walk over the {@code :HAS_FRAME}
   * / {@code :HAS_JOINT} edges. Ordered by {@code updatedAt DESC, appId
   * ASC} so "most recently touched" appears first; ties break deterministically.
   *
   * @param page zero-based page index (clamped to {@code >= 0}).
   * @param size page size (clamped into {@code [1, 200]}).
   * @return a {@link SceneListPage} with the rows for this page and the
   *         total number of scenes (across all pages).
   */
  public SceneListPage listScenes(int page, int size) {
    int safePage = Math.max(page, 0);
    int safeSize = Math.min(Math.max(size, 1), 200);
    long offset = (long) safePage * (long) safeSize;

    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return new SceneListPage(List.of(), 0L);

    // 1 — total
    long total = 0L;
    try {
      Result totalResult = live.query(
        "MATCH (s:DigitalTwinScene) RETURN count(s) AS total", Map.of()
      );
      if (totalResult != null) {
        for (Map<String, Object> row : totalResult.queryResults()) {
          Object t = row.get("total");
          if (t instanceof Number n) {
            total = n.longValue();
            break;
          }
        }
      }
    } catch (RuntimeException e) {
      Log.warnf(e, "SCENEGRAPH list: count query failed");
    }

    // 2 — page with frame + joint counts (aggregated in one round-trip)
    String cypher =
      "MATCH (s:DigitalTwinScene) " +
      "WITH s ORDER BY coalesce(s.updatedAt, 0) DESC, s.appId ASC SKIP $offset LIMIT $size " +
      "OPTIONAL MATCH (s)-[:HAS_FRAME]->(f:CoordinateFrame) " +
      "WITH s, count(DISTINCT f) AS frameCount " +
      "OPTIONAL MATCH (s)-[:HAS_JOINT]->(j:Joint) " +
      "RETURN s.appId AS appId, s.name AS name, s.description AS description, " +
      "       s.sourceFileAppId AS sourceFileAppId, s.rootFrameAppId AS rootFrameAppId, " +
      "       s.createdAt AS createdAt, s.updatedAt AS updatedAt, " +
      "       frameCount AS frameCount, count(DISTINCT j) AS jointCount";
    List<SceneListRow> rows = new ArrayList<>();
    try {
      Result result = live.query(
        cypher, Map.of("offset", offset, "size", (long) safeSize)
      );
      if (result != null) {
        for (Map<String, Object> row : result.queryResults()) {
          rows.add(new SceneListRow(
            asString(row.get("appId")),
            asString(row.get("name")),
            asString(row.get("description")),
            asString(row.get("sourceFileAppId")),
            asString(row.get("rootFrameAppId")),
            asNullableLong(row.get("createdAt")),
            asNullableLong(row.get("updatedAt")),
            asLong(row.get("frameCount")),
            asLong(row.get("jointCount"))
          ));
        }
      }
    } catch (RuntimeException e) {
      Log.warnf(e, "SCENEGRAPH list: page query failed");
    }

    return new SceneListPage(rows, total);
  }

  private static String asString(Object v) {
    return v == null ? null : v.toString();
  }

  private static long asLong(Object v) {
    return v instanceof Number n ? n.longValue() : 0L;
  }

  private static Long asNullableLong(Object v) {
    return v instanceof Number n ? n.longValue() : null;
  }

  /** Load all frames in a scene (walks the :HAS_FRAME edge). */
  public List<CoordinateFrame> findFramesForScene(String sceneAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return List.of();
    String cypher =
      "MATCH (:DigitalTwinScene {appId: $sceneAppId})-[:HAS_FRAME]->(f:CoordinateFrame) " +
      "RETURN f";
    Iterable<CoordinateFrame> rows = live.query(
      CoordinateFrame.class, cypher, Map.of("sceneAppId", sceneAppId));
    List<CoordinateFrame> out = new ArrayList<>();
    if (rows != null) rows.forEach(out::add);
    return out;
  }

  /** Load all joints in a scene (walks the :HAS_JOINT edge). */
  public List<Joint> findJointsForScene(String sceneAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return List.of();
    String cypher =
      "MATCH (:DigitalTwinScene {appId: $sceneAppId})-[:HAS_JOINT]->(j:Joint) " +
      "RETURN j";
    Iterable<Joint> rows = live.query(Joint.class, cypher, Map.of("sceneAppId", sceneAppId));
    List<Joint> out = new ArrayList<>();
    if (rows != null) rows.forEach(out::add);
    return out;
  }

  // ── Writes ────────────────────────────────────────────────────────────────

  /** Create an empty scene; mints an appId; records a CREATE Activity. */
  public DigitalTwinScene createScene(CreateSceneRequestIO body, ProvenanceContext prov) {
    long startedAt = System.currentTimeMillis();
    DigitalTwinScene scene = new DigitalTwinScene();
    scene.setAppId(AppIdGenerator.next());
    if (body != null) {
      scene.setName(body.getName());
      scene.setDescription(body.getDescription());
      scene.setSourceFileAppId(body.getSourceFileAppId());
    }
    saveScene(scene);
    String summary = "POST /v2/scene-graphs — created scene "
      + (scene.getName() == null ? scene.getAppId() : scene.getName());
    recordActivity("CREATE", scene.getAppId(), prov, summary,
      "POST", "v2/scene-graphs", 201, startedAt);
    return scene;
  }

  /** Add a frame to a scene; if the scene has no root yet, this becomes the root. */
  public CoordinateFrame addFrame(String sceneAppId, CreateFrameRequestIO body, ProvenanceContext prov) {
    long startedAt = System.currentTimeMillis();
    DigitalTwinScene scene = requireScene(sceneAppId);
    if (body.getParentFrameAppId() != null && findFrameInScene(sceneAppId, body.getParentFrameAppId()) == null) {
      throw new NotFoundException("parentFrameAppId not found in scene: " + body.getParentFrameAppId());
    }

    CoordinateFrame frame = new CoordinateFrame();
    frame.setAppId(AppIdGenerator.next());
    frame.setName(body.getName());
    frame.setParentFrameAppId(body.getParentFrameAppId());
    if (body.getX() != null) frame.setX(body.getX());
    if (body.getY() != null) frame.setY(body.getY());
    if (body.getZ() != null) frame.setZ(body.getZ());
    if (body.getRx() != null) frame.setRx(body.getRx());
    if (body.getRy() != null) frame.setRy(body.getRy());
    if (body.getRz() != null) frame.setRz(body.getRz());
    frame.setKind(body.getKind() == null ? FrameKind.FRAME : body.getKind());
    saveFrame(frame);

    mergeSceneHasFrame(sceneAppId, frame.getAppId());
    if (body.getParentFrameAppId() != null) {
      mergeFrameHasParent(frame.getAppId(), body.getParentFrameAppId());
    }
    if (scene.getRootFrameAppId() == null) {
      scene.setRootFrameAppId(frame.getAppId());
      saveScene(scene);
    }

    String summary = "POST /v2/scene-graphs/" + sceneAppId + "/frames — added frame "
      + (frame.getName() == null ? frame.getAppId() : frame.getName());
    recordActivity("UPDATE", sceneAppId, prov, summary,
      "POST", "v2/scene-graphs/" + sceneAppId + "/frames", 201, startedAt);
    return frame;
  }

  /** Patch a single frame's mutable fields. */
  public CoordinateFrame patchFrame(
    String sceneAppId, String frameAppId, PatchFrameRequestIO body, ProvenanceContext prov
  ) {
    long startedAt = System.currentTimeMillis();
    requireScene(sceneAppId);
    CoordinateFrame frame = findFrameInScene(sceneAppId, frameAppId);
    if (frame == null) {
      throw new NotFoundException("frame not in scene: " + frameAppId);
    }
    if (body.getName() != null) frame.setName(body.getName());
    if (body.getX() != null) frame.setX(body.getX());
    if (body.getY() != null) frame.setY(body.getY());
    if (body.getZ() != null) frame.setZ(body.getZ());
    if (body.getRx() != null) frame.setRx(body.getRx());
    if (body.getRy() != null) frame.setRy(body.getRy());
    if (body.getRz() != null) frame.setRz(body.getRz());
    if (body.getKind() != null) frame.setKind(body.getKind());

    String newParent = body.getParentFrameAppId();
    if (newParent != null) {
      // Empty string means "make this a root frame": clear the parent pointer
      // and drop the :HAS_PARENT_FRAME edge.
      if (newParent.isBlank()) {
        if (frame.getParentFrameAppId() != null) {
          deleteFrameParentEdge(frame.getAppId(), frame.getParentFrameAppId());
        }
        frame.setParentFrameAppId(null);
      } else {
        if (findFrameInScene(sceneAppId, newParent) == null) {
          throw new NotFoundException("parentFrameAppId not found in scene: " + newParent);
        }
        // Replace the old edge atomically: delete + merge in one call.
        replaceFrameParentEdge(frame.getAppId(), frame.getParentFrameAppId(), newParent);
        frame.setParentFrameAppId(newParent);
      }
    }
    saveFrame(frame);

    String summary = "PATCH /v2/scene-graphs/" + sceneAppId + "/frames/" + frameAppId;
    recordActivity("UPDATE", sceneAppId, prov, summary,
      "PATCH", "v2/scene-graphs/" + sceneAppId + "/frames/" + frameAppId, 200, startedAt);
    return frame;
  }

  /**
   * Delete a frame and every descendant via :HAS_PARENT_FRAME chain
   * (hard delete; the scaffold has no soft-delete field — see class
   * Javadoc). Also detaches the frame from the scene by dropping the
   * :HAS_FRAME edge. Any joints whose parent or child frame is in the
   * removed subtree are also deleted.
   */
  public void deleteFrameSubtree(String sceneAppId, String frameAppId, ProvenanceContext prov) {
    long startedAt = System.currentTimeMillis();
    DigitalTwinScene scene = requireScene(sceneAppId);
    if (findFrameInScene(sceneAppId, frameAppId) == null) {
      throw new NotFoundException("frame not in scene: " + frameAppId);
    }
    Session live = NeoConnector.getInstance().getNeo4jSession();

    // Detach all frames reachable from frameAppId via :HAS_PARENT_FRAME*
    // plus the root itself, and any Joints touching those frames.
    String cypher =
      "MATCH (root:CoordinateFrame {appId: $rootAppId}) " +
      "OPTIONAL MATCH (descendant:CoordinateFrame)-[:HAS_PARENT_FRAME*0..]->(root) " +
      "WITH collect(DISTINCT root) + collect(DISTINCT descendant) AS allFrames " +
      "UNWIND [f IN allFrames WHERE f IS NOT NULL] AS f " +
      "OPTIONAL MATCH (j:Joint) WHERE j.parentFrameAppId = f.appId OR j.childFrameAppId = f.appId " +
      "DETACH DELETE j " +
      "WITH f " +
      "DETACH DELETE f";
    live.query(cypher, Map.of("rootAppId", frameAppId));

    // If the scene's rootFrameAppId pointed at the deleted subtree's root,
    // clear it.
    if (frameAppId.equals(scene.getRootFrameAppId())) {
      scene.setRootFrameAppId(null);
      saveScene(scene);
    }

    String summary = "DELETE /v2/scene-graphs/" + sceneAppId + "/frames/" + frameAppId + " — subtree removed";
    recordActivity("DELETE", sceneAppId, prov, summary,
      "DELETE", "v2/scene-graphs/" + sceneAppId + "/frames/" + frameAppId, 204, startedAt);
  }

  /** Register a new joint between two existing frames in the scene. */
  public Joint addJoint(String sceneAppId, CreateJointRequestIO body, ProvenanceContext prov) {
    long startedAt = System.currentTimeMillis();
    requireScene(sceneAppId);
    if (body.getParentFrameAppId() == null || body.getParentFrameAppId().isBlank()) {
      throw new IllegalArgumentException("parentFrameAppId is required");
    }
    if (body.getChildFrameAppId() == null || body.getChildFrameAppId().isBlank()) {
      throw new IllegalArgumentException("childFrameAppId is required");
    }
    if (findFrameInScene(sceneAppId, body.getParentFrameAppId()) == null) {
      throw new NotFoundException("parentFrameAppId not in scene: " + body.getParentFrameAppId());
    }
    if (findFrameInScene(sceneAppId, body.getChildFrameAppId()) == null) {
      throw new NotFoundException("childFrameAppId not in scene: " + body.getChildFrameAppId());
    }

    Joint joint = new Joint();
    joint.setAppId(AppIdGenerator.next());
    joint.setName(body.getName());
    joint.setParentFrameAppId(body.getParentFrameAppId());
    joint.setChildFrameAppId(body.getChildFrameAppId());
    if (body.getAxisX() != null) joint.setAxisX(body.getAxisX());
    if (body.getAxisY() != null) joint.setAxisY(body.getAxisY());
    if (body.getAxisZ() != null) joint.setAxisZ(body.getAxisZ());
    if (body.getLimitMin() != null) joint.setLimitMin(body.getLimitMin());
    if (body.getLimitMax() != null) joint.setLimitMax(body.getLimitMax());
    if (body.getHomeAngle() != null) joint.setHomeAngle(body.getHomeAngle());
    joint.setType(body.getType() == null ? JointType.FIXED : body.getType());
    saveJoint(joint);

    mergeSceneHasJoint(sceneAppId, joint.getAppId());
    mergeJointEndpoints(joint.getAppId(), body.getParentFrameAppId(), body.getChildFrameAppId());

    String summary = "POST /v2/scene-graphs/" + sceneAppId + "/joints — registered joint "
      + (joint.getName() == null ? joint.getAppId() : joint.getName());
    recordActivity("UPDATE", sceneAppId, prov, summary,
      "POST", "v2/scene-graphs/" + sceneAppId + "/joints", 201, startedAt);
    return joint;
  }

  /** Delete a joint from a scene. */
  public void deleteJoint(String sceneAppId, String jointAppId, ProvenanceContext prov) {
    long startedAt = System.currentTimeMillis();
    requireScene(sceneAppId);
    Joint joint = findJointInScene(sceneAppId, jointAppId);
    if (joint == null) {
      throw new NotFoundException("joint not in scene: " + jointAppId);
    }
    Session live = NeoConnector.getInstance().getNeo4jSession();
    live.query(
      "MATCH (j:Joint {appId: $jointAppId}) DETACH DELETE j",
      Map.of("jointAppId", jointAppId)
    );

    String summary = "DELETE /v2/scene-graphs/" + sceneAppId + "/joints/" + jointAppId;
    recordActivity("DELETE", sceneAppId, prov, summary,
      "DELETE", "v2/scene-graphs/" + sceneAppId + "/joints/" + jointAppId, 204, startedAt);
  }

  // ── Internal helpers ──────────────────────────────────────────────────────

  DigitalTwinScene requireScene(String appId) {
    DigitalTwinScene scene = findScene(appId);
    if (scene == null) throw new NotFoundException("scene not found: " + appId);
    return scene;
  }

  CoordinateFrame findFrameInScene(String sceneAppId, String frameAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return null;
    String cypher =
      "MATCH (:DigitalTwinScene {appId: $sceneAppId})-[:HAS_FRAME]->(f:CoordinateFrame {appId: $frameAppId}) " +
      "RETURN f LIMIT 1";
    Iterable<CoordinateFrame> rows = live.query(
      CoordinateFrame.class, cypher,
      Map.of("sceneAppId", sceneAppId, "frameAppId", frameAppId));
    if (rows == null) return null;
    var it = rows.iterator();
    return it.hasNext() ? it.next() : null;
  }

  Joint findJointInScene(String sceneAppId, String jointAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return null;
    String cypher =
      "MATCH (:DigitalTwinScene {appId: $sceneAppId})-[:HAS_JOINT]->(j:Joint {appId: $jointAppId}) " +
      "RETURN j LIMIT 1";
    Iterable<Joint> rows = live.query(
      Joint.class, cypher,
      Map.of("sceneAppId", sceneAppId, "jointAppId", jointAppId));
    if (rows == null) return null;
    var it = rows.iterator();
    return it.hasNext() ? it.next() : null;
  }

  void saveScene(DigitalTwinScene scene) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException("Neo4j session unavailable — cannot persist :DigitalTwinScene");
    }
    long now = System.currentTimeMillis();
    if (scene.getCreatedAt() == null) scene.setCreatedAt(now);
    scene.setUpdatedAt(now);
    live.save(scene, 1);
  }

  void saveFrame(CoordinateFrame frame) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException("Neo4j session unavailable — cannot persist :CoordinateFrame");
    }
    live.save(frame, 1);
  }

  void saveJoint(Joint joint) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) {
      throw new IllegalStateException("Neo4j session unavailable — cannot persist :Joint");
    }
    live.save(joint, 1);
  }

  // ── Edge MERGE / DELETE helpers ───────────────────────────────────────────

  void mergeSceneHasFrame(String sceneAppId, String frameAppId) {
    runEdgeCypher(
      "MATCH (s:DigitalTwinScene {appId: $s}) MATCH (f:CoordinateFrame {appId: $f}) " +
      "MERGE (s)-[:HAS_FRAME]->(f)",
      Map.of("s", sceneAppId, "f", frameAppId));
  }

  void mergeSceneHasJoint(String sceneAppId, String jointAppId) {
    runEdgeCypher(
      "MATCH (s:DigitalTwinScene {appId: $s}) MATCH (j:Joint {appId: $j}) " +
      "MERGE (s)-[:HAS_JOINT]->(j)",
      Map.of("s", sceneAppId, "j", jointAppId));
  }

  void mergeFrameHasParent(String childAppId, String parentAppId) {
    runEdgeCypher(
      "MATCH (c:CoordinateFrame {appId: $c}) MATCH (p:CoordinateFrame {appId: $p}) " +
      "MERGE (c)-[:HAS_PARENT_FRAME]->(p)",
      Map.of("c", childAppId, "p", parentAppId));
  }

  void deleteFrameParentEdge(String childAppId, String parentAppId) {
    runEdgeCypher(
      "MATCH (c:CoordinateFrame {appId: $c})-[r:HAS_PARENT_FRAME]->(p:CoordinateFrame {appId: $p}) DELETE r",
      Map.of("c", childAppId, "p", parentAppId));
  }

  void replaceFrameParentEdge(String childAppId, String oldParentAppId, String newParentAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return;
    // Atomic delete-then-merge in one Cypher round-trip.
    String cypher =
      "MATCH (c:CoordinateFrame {appId: $c}) " +
      "OPTIONAL MATCH (c)-[r:HAS_PARENT_FRAME]->(:CoordinateFrame) DELETE r " +
      "WITH c " +
      "MATCH (p:CoordinateFrame {appId: $newP}) " +
      "MERGE (c)-[:HAS_PARENT_FRAME]->(p)";
    Map<String, Object> params = new HashMap<>();
    params.put("c", childAppId);
    params.put("oldP", oldParentAppId);
    params.put("newP", newParentAppId);
    try {
      live.query(cypher, params);
    } catch (RuntimeException e) {
      Log.warnf(e, "SCENEGRAPH: replaceFrameParentEdge failed for child=%s newParent=%s",
        childAppId, newParentAppId);
    }
  }

  void mergeJointEndpoints(String jointAppId, String parentFrameAppId, String childFrameAppId) {
    runEdgeCypher(
      "MATCH (j:Joint {appId: $j}) " +
      "MATCH (pf:CoordinateFrame {appId: $pf}) " +
      "MATCH (cf:CoordinateFrame {appId: $cf}) " +
      "MERGE (j)-[:JOINT_PARENT]->(pf) " +
      "MERGE (j)-[:JOINT_CHILD]->(cf)",
      Map.of("j", jointAppId, "pf", parentFrameAppId, "cf", childFrameAppId));
  }

  private void runEdgeCypher(String cypher, Map<String, ?> params) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return;
    try {
      live.query(cypher, params);
    } catch (RuntimeException e) {
      // Edge writes are best-effort; the scalar appId pointer on the entity is
      // the SoT, and a missing edge is correctable by a Cypher patch later.
      Log.warnf(e, "SCENEGRAPH edge write failed: %s", cypher);
    }
  }

  // ── PROV-O capture ────────────────────────────────────────────────────────

  /**
   * Carries cross-cutting auth + AI-header context from the REST / MCP
   * boundary into the service layer so PROV-O capture sees both the
   * authenticated user and the {@code X-AI-Agent} marker.
   */
  public record ProvenanceContext(String username, String agentId, String sourceMode) {
    public static ProvenanceContext human(String username) {
      return new ProvenanceContext(username, null, "human");
    }
    public static ProvenanceContext ai(String username, String agentId) {
      return new ProvenanceContext(username, agentId, "ai");
    }
    public static ProvenanceContext from(String username, String agentHeader) {
      if (agentHeader != null && !agentHeader.isBlank()) {
        return ai(username, agentHeader);
      }
      return human(username);
    }
  }

  /**
   * Record one {@link Activity} for a scene-graph mutation. Best-effort —
   * never propagates exceptions per the secondary-write rule.
   *
   * <p>Wires a supplementary {@code (:Activity)-[:WAS_DERIVED_FROM]->(:Activity)}
   * edge to the previous activity for the same scene's appId so the
   * audit chain is traversable as a graph, not just by timestamp sort.
   */
  void recordActivity(
    String actionKind, String sceneAppId, ProvenanceContext prov, String summary,
    String method, String path, int status, long startedAt
  ) {
    try {
      long endedAt = System.currentTimeMillis();
      Activity activity = provenanceService.record(
        actionKind,
        "DigitalTwinScene",
        sceneAppId,
        prov == null ? null : prov.username(),
        summary,
        method,
        path,
        status,
        startedAt,
        endedAt,
        null,
        prov == null ? null : prov.sourceMode(),
        prov == null ? null : prov.agentId()
      );
      if (activity != null && activity.getAppId() != null) {
        wireDerivedFromPrior(activity.getAppId(), sceneAppId);
      }
    } catch (RuntimeException e) {
      Log.debugf(e, "SCENEGRAPH provenance capture skipped for scene=%s", sceneAppId);
    }
  }

  /**
   * Link the just-saved activity to the most-recent prior activity for
   * the same scene. Best-effort.
   */
  void wireDerivedFromPrior(String activityAppId, String sceneAppId) {
    Session live = NeoConnector.getInstance().getNeo4jSession();
    if (live == null) return;
    String cypher =
      "MATCH (now:Activity {appId: $newApp}) " +
      "MATCH (prior:Activity {targetAppId: $sceneAppId}) " +
      "WHERE prior.appId <> $newApp " +
      "WITH now, prior ORDER BY prior.startedAtMillis DESC LIMIT 1 " +
      "MERGE (now)-[:WAS_DERIVED_FROM]->(prior)";
    try {
      live.query(cypher, Map.of("newApp", activityAppId, "sceneAppId", sceneAppId));
    } catch (RuntimeException e) {
      Log.debugf(e, "SCENEGRAPH WAS_DERIVED_FROM edge failed: activity=%s scene=%s",
        activityAppId, sceneAppId);
    }
  }
}
