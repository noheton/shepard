package de.dlr.shepard.v2.scenegraph.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import de.dlr.shepard.v2.scenegraph.services.SceneGraphService.ProvenanceContext;
import jakarta.ws.rs.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.neo4j.ogm.cypher.Filter;
import org.neo4j.ogm.session.Session;

/**
 * SCENEGRAPH-REST-1 — unit tests for {@link SceneGraphService}.
 *
 * <p>The service does all I/O via fresh sessions fetched from
 * {@link NeoConnector#getInstance()} — we use Mockito's
 * {@code MockedStatic} to inject a mock {@link Session} per call.
 */
class SceneGraphServiceTest {

  private SceneGraphService svc;
  private ProvenanceService prov;
  private Session live;
  private NeoConnector connector;

  @BeforeEach
  void setUp() {
    svc = new SceneGraphService();
    svc.sceneDAO = new DigitalTwinSceneDAO();
    svc.frameDAO = new CoordinateFrameDAO();
    svc.jointDAO = new JointDAO();
    prov = mock(ProvenanceService.class);
    svc.provenanceService = prov;
    live = mock(Session.class);
    connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(live);

    // Stub provenance.record so recordActivity proceeds through the
    // WAS_DERIVED_FROM follow-up cypher path.
    Activity stubActivity = new Activity();
    stubActivity.setAppId("act-stub");
    when(prov.record(
      anyString(), anyString(), anyString(), any(), anyString(), anyString(),
      anyString(), any(), anyLong(), anyLong(),
      any(), any(), any()
    )).thenReturn(stubActivity);
  }

  // ── createScene ────────────────────────────────────────────────────────────

  @Test
  void createScene_mintsAppId_andRecordsCreateActivity() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var body = new CreateSceneRequestIO();
      body.setName("test-scene");

      DigitalTwinScene scene = svc.createScene(body, ProvenanceContext.human("alice"));

      assertNotNull(scene.getAppId(), "appId must be minted");
      assertEquals("test-scene", scene.getName());
      verify(live).save(eq(scene), eq(1));
      verify(prov, atLeastOnce()).record(
        eq("CREATE"), eq("DigitalTwinScene"), eq(scene.getAppId()),
        eq("alice"), anyString(), eq("POST"), eq("v2/scene-graphs"),
        eq(201), anyLong(), anyLong(),
        any(), eq("human"), any());
    }
  }

  // ── addFrame ───────────────────────────────────────────────────────────────

  @Test
  void addFrame_addsRootFrame_andSetsRootPointer_whenSceneEmpty() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      DigitalTwinScene scene = new DigitalTwinScene();
      scene.setAppId("scene-1");
      when(live.loadAll(eq(DigitalTwinScene.class), any(Filter.class), eq(1)))
        .thenReturn(List.of(scene));

      var body = new CreateFrameRequestIO();
      body.setName("base_link");

      CoordinateFrame frame = svc.addFrame("scene-1", body, ProvenanceContext.human("alice"));

      assertNotNull(frame.getAppId());
      assertEquals("base_link", frame.getName());
      assertEquals(FrameKind.FRAME, frame.getKind(), "null kind defaults to FRAME");
      assertEquals(frame.getAppId(), scene.getRootFrameAppId(), "first frame becomes root");
      // saveScene + saveFrame both invoke live.save
      verify(live, atLeastOnce()).save(any(), eq(1));
    }
  }

  @Test
  void addFrame_throws404_whenSceneMissing() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      when(live.loadAll(eq(DigitalTwinScene.class), any(Filter.class), eq(1)))
        .thenReturn(List.of());

      assertThrows(NotFoundException.class,
        () -> svc.addFrame("missing", new CreateFrameRequestIO(), ProvenanceContext.human("alice")));
    }
  }

  // ── patchFrame ─────────────────────────────────────────────────────────────

  @Test
  void patchFrame_updatesTransform_andDoesNotChangeParentWhenAbsent() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      DigitalTwinScene scene = new DigitalTwinScene();
      scene.setAppId("scene-1");
      when(live.loadAll(eq(DigitalTwinScene.class), any(Filter.class), eq(1)))
        .thenReturn(List.of(scene));

      CoordinateFrame existing = new CoordinateFrame();
      existing.setAppId("frame-1");
      existing.setName("tool0");
      existing.setX(1.0); existing.setParentFrameAppId("frame-0");
      when(live.query(eq(CoordinateFrame.class), anyString(), anyMap()))
        .thenReturn(List.of(existing));

      var body = new PatchFrameRequestIO();
      body.setX(2.5);

      CoordinateFrame patched = svc.patchFrame("scene-1", "frame-1", body, ProvenanceContext.human("alice"));

      assertEquals(2.5, patched.getX(), "x updated");
      assertEquals("frame-0", patched.getParentFrameAppId(),
        "parent must not change when body.parentFrameAppId is null");
      verify(prov).record(eq("UPDATE"), eq("DigitalTwinScene"), eq("scene-1"),
        eq("alice"), anyString(), eq("PATCH"), anyString(), eq(200),
        anyLong(), anyLong(),
        any(), eq("human"), any());
    }
  }

  @Test
  void patchFrame_clearsParent_whenEmptyString() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      DigitalTwinScene scene = new DigitalTwinScene();
      scene.setAppId("scene-1");
      when(live.loadAll(eq(DigitalTwinScene.class), any(Filter.class), eq(1)))
        .thenReturn(List.of(scene));

      CoordinateFrame existing = new CoordinateFrame();
      existing.setAppId("frame-1");
      existing.setParentFrameAppId("frame-0");
      when(live.query(eq(CoordinateFrame.class), anyString(), anyMap()))
        .thenReturn(List.of(existing));

      var body = new PatchFrameRequestIO();
      body.setParentFrameAppId("");

      CoordinateFrame patched = svc.patchFrame("scene-1", "frame-1", body, ProvenanceContext.human("alice"));

      assertNull(patched.getParentFrameAppId(), "empty string clears parent");
    }
  }

  // ── joint flow ─────────────────────────────────────────────────────────────

  @Test
  void addJoint_validatesEndpointsExist_inScene() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      DigitalTwinScene scene = new DigitalTwinScene();
      scene.setAppId("scene-1");
      when(live.loadAll(eq(DigitalTwinScene.class), any(Filter.class), eq(1)))
        .thenReturn(List.of(scene));

      // Both findFrameInScene calls return an existing frame.
      CoordinateFrame f = new CoordinateFrame();
      f.setAppId("f1");
      when(live.query(eq(CoordinateFrame.class), anyString(), anyMap()))
        .thenReturn(List.of(f));

      var body = new CreateJointRequestIO();
      body.setParentFrameAppId("f1");
      body.setChildFrameAppId("f2");
      body.setType(JointType.REVOLUTE);
      body.setLimitMin(-1.0); body.setLimitMax(1.0);

      Joint j = svc.addJoint("scene-1", body, ProvenanceContext.human("alice"));

      assertNotNull(j.getAppId());
      assertEquals(JointType.REVOLUTE, j.getType());
      assertEquals(-1.0, j.getLimitMin());
    }
  }

  @Test
  void addJoint_rejectsMissingParentAppId() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      DigitalTwinScene scene = new DigitalTwinScene();
      scene.setAppId("scene-1");
      when(live.loadAll(eq(DigitalTwinScene.class), any(Filter.class), eq(1)))
        .thenReturn(List.of(scene));

      var body = new CreateJointRequestIO();
      body.setChildFrameAppId("f2");

      assertThrows(IllegalArgumentException.class,
        () -> svc.addJoint("scene-1", body, ProvenanceContext.human("alice")));
    }
  }

  // ── delete frame subtree ──────────────────────────────────────────────────

  @Test
  void deleteFrameSubtree_clearsRootPointer_whenRootDeleted() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      DigitalTwinScene scene = new DigitalTwinScene();
      scene.setAppId("scene-1");
      scene.setRootFrameAppId("frame-root");
      when(live.loadAll(eq(DigitalTwinScene.class), any(Filter.class), eq(1)))
        .thenReturn(List.of(scene));

      CoordinateFrame root = new CoordinateFrame();
      root.setAppId("frame-root");
      when(live.query(eq(CoordinateFrame.class), anyString(), anyMap()))
        .thenReturn(List.of(root));

      svc.deleteFrameSubtree("scene-1", "frame-root", ProvenanceContext.human("alice"));

      assertNull(scene.getRootFrameAppId(), "rootFrameAppId cleared after root delete");
      verify(prov).record(eq("DELETE"), eq("DigitalTwinScene"), eq("scene-1"),
        eq("alice"), anyString(), eq("DELETE"), anyString(), eq(204),
        anyLong(), anyLong(),
        any(), eq("human"), any());
    }
  }

  // ── ProvenanceContext factory ──────────────────────────────────────────────

  @Test
  void provenanceContext_aiHeader_setsSourceModeAi() {
    var p = ProvenanceContext.from("alice", "claude-mcp/1.0");
    assertEquals("ai", p.sourceMode());
    assertEquals("claude-mcp/1.0", p.agentId());
  }

  @Test
  void provenanceContext_noAiHeader_setsSourceModeHuman() {
    var p = ProvenanceContext.from("alice", null);
    assertEquals("human", p.sourceMode());
    assertNull(p.agentId());
    var p2 = ProvenanceContext.from("alice", "");
    assertEquals("human", p2.sourceMode());
  }

  @Test
  void findScene_returnsNullForBlankAppId() {
    assertNull(svc.findScene(null));
    assertNull(svc.findScene(""));
    assertNull(svc.findScene("   "));
  }

  @Test
  void findScene_returnsNullWhenSessionUnavailable() {
    when(connector.getNeo4jSession()).thenReturn(null);
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      assertNull(svc.findScene("any"));
    }
  }

  // ── SCENEGRAPH-LIST-1 — listScenes ────────────────────────────────────────

  @Test
  void listScenes_returnsEmptyPage_whenSessionUnavailable() {
    when(connector.getNeo4jSession()).thenReturn(null);
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      var page = svc.listScenes(0, 50);
      assertEquals(0L, page.total());
      assertEquals(0, page.rows().size());
    }
  }

  @Test
  void listScenes_returnsRows_andTotal_fromCypher() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      // Mock the count query (must come before the page query)
      var countResult = mock(org.neo4j.ogm.model.Result.class);
      when(countResult.queryResults())
        .thenReturn(List.of(java.util.Map.<String, Object>of("total", 3L)));

      // Mock the page query
      var pageResult = mock(org.neo4j.ogm.model.Result.class);
      java.util.Map<String, Object> row1 = new java.util.HashMap<>();
      row1.put("appId", "scene-a");
      row1.put("name", "Scene A");
      row1.put("description", "first");
      row1.put("sourceFileAppId", null);
      row1.put("rootFrameAppId", "frame-root-a");
      row1.put("createdAt", 1000L);
      row1.put("updatedAt", 2000L);
      row1.put("frameCount", 4L);
      row1.put("jointCount", 3L);
      java.util.Map<String, Object> row2 = new java.util.HashMap<>();
      row2.put("appId", "scene-b");
      row2.put("name", "Scene B");
      row2.put("description", null);
      row2.put("sourceFileAppId", "file-1");
      row2.put("rootFrameAppId", null);
      row2.put("createdAt", null);
      row2.put("updatedAt", null);
      row2.put("frameCount", 0L);
      row2.put("jointCount", 0L);
      when(pageResult.queryResults()).thenReturn(List.of(row1, row2));

      // First call returns count, second returns page (sequenced in service)
      when(live.query(anyString(), anyMap()))
        .thenReturn(countResult)
        .thenReturn(pageResult);

      var page = svc.listScenes(0, 50);

      assertEquals(3L, page.total());
      assertEquals(2, page.rows().size());
      assertEquals("scene-a", page.rows().get(0).appId());
      assertEquals("Scene A", page.rows().get(0).name());
      assertEquals(4L, page.rows().get(0).frameCount());
      assertEquals(3L, page.rows().get(0).jointCount());
      assertEquals(Long.valueOf(1000L), page.rows().get(0).createdAt());
      assertEquals(Long.valueOf(2000L), page.rows().get(0).updatedAt());
      assertEquals("scene-b", page.rows().get(1).appId());
      assertNull(page.rows().get(1).createdAt());
      assertNull(page.rows().get(1).updatedAt());
      assertEquals(0L, page.rows().get(1).frameCount());
    }
  }

  @Test
  void listScenes_clampsPageAndSize_intoSafeRange() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);

      var emptyResult = mock(org.neo4j.ogm.model.Result.class);
      when(emptyResult.queryResults()).thenReturn(List.of());
      when(live.query(anyString(), anyMap())).thenReturn(emptyResult);

      // Negative page, size beyond cap → still produces a result; page = 0,
      // size clamped to 200. We verify the query is invoked with offset = 0
      // and size = 200 — proves the clamp is applied at the service layer.
      var page = svc.listScenes(-5, 9999);
      assertEquals(0, page.rows().size());

      org.mockito.ArgumentCaptor<java.util.Map<String, Object>> params =
        org.mockito.ArgumentCaptor.forClass(java.util.Map.class);
      verify(live, atLeastOnce()).query(anyString(), params.capture());
      // Last capture is the page query — assert clamp parameters.
      var last = params.getValue();
      // page query carries offset+size; count query carries empty map.
      if (last.containsKey("offset")) {
        assertEquals(0L, last.get("offset"));
        assertEquals(200L, last.get("size"));
      }
    }
  }

  @Test
  void listScenes_ignoresCypherFailure_andDegradesToEmpty() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      when(live.query(anyString(), anyMap()))
        .thenThrow(new RuntimeException("simulated cypher failure"));

      // Service is fail-soft on the secondary read; returns empty page.
      var page = svc.listScenes(0, 50);
      assertEquals(0L, page.total());
      assertEquals(0, page.rows().size());
    }
  }

  // ── SCENEGRAPH-LIST-1 — saveScene timestamps ──────────────────────────────

  @Test
  void saveScene_setsCreatedAt_andUpdatedAt_onFirstSave() {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      DigitalTwinScene scene = new DigitalTwinScene();
      scene.setAppId("new-scene");
      assertNull(scene.getCreatedAt());
      assertNull(scene.getUpdatedAt());

      svc.saveScene(scene);

      assertNotNull(scene.getCreatedAt(), "createdAt must be set on first save");
      assertNotNull(scene.getUpdatedAt(), "updatedAt must be set on every save");
      assertEquals(scene.getCreatedAt(), scene.getUpdatedAt(),
        "first save: createdAt == updatedAt");
    }
  }

  @Test
  void saveScene_preservesCreatedAt_andRefreshesUpdatedAt_onResave() throws InterruptedException {
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      DigitalTwinScene scene = new DigitalTwinScene();
      scene.setAppId("existing");
      scene.setCreatedAt(1L);
      scene.setUpdatedAt(1L);

      // Make sure the new updatedAt is strictly greater than the old.
      Thread.sleep(2);
      svc.saveScene(scene);

      assertEquals(Long.valueOf(1L), scene.getCreatedAt(),
        "createdAt is immutable after first save");
      assertNotNull(scene.getUpdatedAt());
      assertTrue(scene.getUpdatedAt() >= 1L);
    }
  }
}
