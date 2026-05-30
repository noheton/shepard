package de.dlr.shepard.v2.krl.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.common.exceptions.InvalidPathException;
import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.DateHelper;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.data.timeseries.io.TimeseriesContainerIO;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.krl.io.KrlInterpretRequestIO;
import de.dlr.shepard.v2.krl.io.KrlInterpretResponseIO;
import jakarta.ws.rs.BadRequestException;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.neo4j.ogm.session.Session;

/**
 * KRL-INTERPRETER-05 — service-layer tests for {@link KrlInterpretService}.
 *
 * <p>Mock-based, no Quarkus boot. Covers the orchestration composition:
 * input validation, file payload resolution, sidecar invocation, error
 * propagation, trajectory persistence, and provenance recording.
 */
class KrlInterpretServiceTest {

  static final String SRC_APP_ID = "018f9c5a-7e26-7000-a000-000000000001";
  static final String URDF_APP_ID = "018f9c5a-7e26-7000-a000-000000000002";
  static final String DO_APP_ID = "018f9c5a-7e26-7000-a000-000000000003";
  static final String TSC_APP_ID = "018f9c5a-7e26-7000-a000-000000000004";
  static final String SCENE_APP_ID = "018f9c5a-7e26-7000-a000-000000000005";
  static final String ACTIVITY_APP_ID = "018f9c5a-7e26-7000-a000-0000000000ff";
  static final long DO_OGM_ID = 101L;
  static final long COLL_OGM_ID = 42L;
  static final long TSC_OGM_ID = 77L;

  private KrlInterpretService service;
  private KrlSidecarClient sidecar;
  private SingletonFileReferenceService fileReferenceService;
  private TimeseriesContainerService timeseriesContainerService;
  private TimeseriesService timeseriesService;
  private TimeseriesReferenceService timeseriesReferenceService;
  private DataObjectService dataObjectService;
  private EntityIdResolver entityIdResolver;
  private ProvenanceService provenanceService;
  private DateHelper dateHelper;
  private DataObject targetDataObject;
  private TimeseriesContainer container;

  @BeforeEach
  void setUp() {
    service = new KrlInterpretService();
    sidecar = mock(KrlSidecarClient.class);
    fileReferenceService = mock(SingletonFileReferenceService.class);
    timeseriesContainerService = mock(TimeseriesContainerService.class);
    timeseriesService = mock(TimeseriesService.class);
    timeseriesReferenceService = mock(TimeseriesReferenceService.class);
    dataObjectService = mock(DataObjectService.class);
    entityIdResolver = mock(EntityIdResolver.class);
    provenanceService = mock(ProvenanceService.class);
    dateHelper = mock(DateHelper.class);

    service.sidecar = sidecar;
    service.fileReferenceService = fileReferenceService;
    service.timeseriesContainerService = timeseriesContainerService;
    service.timeseriesService = timeseriesService;
    service.timeseriesReferenceService = timeseriesReferenceService;
    service.dataObjectService = dataObjectService;
    service.entityIdResolver = entityIdResolver;
    service.provenanceService = provenanceService;
    service.dateHelper = dateHelper;

    Collection coll = new Collection();
    coll.setShepardId(COLL_OGM_ID);
    targetDataObject = new DataObject();
    targetDataObject.setShepardId(DO_OGM_ID);
    targetDataObject.setCollection(coll);

    container = new TimeseriesContainer(TSC_OGM_ID);
    container.setAppId(TSC_APP_ID);

    lenient().when(fileReferenceService.getPayload(SRC_APP_ID))
      .thenReturn(new NamedInputStream("oid-src", new ByteArrayInputStream("DEF MAIN()".getBytes()), "main.src", 10L));
    lenient().when(fileReferenceService.getPayload(URDF_APP_ID))
      .thenReturn(new NamedInputStream("oid-urdf", new ByteArrayInputStream("<robot/>".getBytes()), "arm.urdf", 9L));
    lenient().when(entityIdResolver.resolveLong(DO_APP_ID)).thenReturn(DO_OGM_ID);
    lenient().when(dataObjectService.getDataObject(DO_OGM_ID)).thenReturn(targetDataObject);
    lenient().when(timeseriesContainerService.getContainerByAppId(TSC_APP_ID)).thenReturn(container);

    TimeseriesReference savedRef = new TimeseriesReference();
    savedRef.setShepardId(999L);
    savedRef.setAppId("018f9c5a-7e26-7000-a000-0000000000ab");
    lenient().when(timeseriesReferenceService.createReference(eq(COLL_OGM_ID), eq(DO_OGM_ID), any(TimeseriesReferenceIO.class)))
      .thenReturn(savedRef);

    Activity recorded = new Activity();
    recorded.setAppId(ACTIVITY_APP_ID);
    lenient().when(provenanceService.record(
      anyString(), anyString(), any(), any(), anyString(), anyString(),
      anyString(), any(), anyLong(), anyLong(), any(), any(), any()
    )).thenReturn(recorded);
  }

  // ── validation ─────────────────────────────────────────────────────────────

  @Test
  void validate_nullBody_throwsBadRequest() {
    assertThrows(BadRequestException.class, () -> service.interpret(null, "alice", null));
  }

  @Test
  void validate_missingSrc_throwsBadRequest() {
    KrlInterpretRequestIO req = makeRequest();
    req.setSrcFileAppId(null);
    assertThrows(BadRequestException.class, () -> service.interpret(req, "alice", null));
  }

  @Test
  void validate_missingUrdf_throwsBadRequest() {
    KrlInterpretRequestIO req = makeRequest();
    req.setUrdfFileAppId("");
    assertThrows(BadRequestException.class, () -> service.interpret(req, "alice", null));
  }

  @Test
  void validate_missingTarget_throwsBadRequest() {
    KrlInterpretRequestIO req = makeRequest();
    req.setTargetDataObjectAppId(null);
    assertThrows(BadRequestException.class, () -> service.interpret(req, "alice", null));
  }

  @Test
  void validate_missingTsContainer_noLongerThrows_autoMints() {
    // KRL-INTERPRETER-05-FOLLOWUP-AUTO-CONTAINER: null container triggers auto-mint,
    // not a BadRequestException.  The auto-mint path uses findKrlTrajectoriesContainerForDataObject
    // (returns empty) then createContainer.
    when(timeseriesContainerService.findKrlTrajectoriesContainerForDataObject(DO_APP_ID,
      KrlInterpretService.KRL_TRAJECTORIES_CONTAINER_NAME)).thenReturn(Optional.empty());
    when(timeseriesContainerService.createContainer(any(TimeseriesContainerIO.class))).thenReturn(container);
    when(sidecar.interpret(any())).thenReturn(KrlSidecarClient.SidecarOutcome.ok(sidecarOk()));

    KrlInterpretRequestIO req = makeRequest();
    req.setTimeseriesContainerAppId(null);
    // Must NOT throw — auto-mint path takes over.
    KrlInterpretResponseIO response = runWithStaticNeo(req, "alice", null);
    assertNotNull(response);
    verify(timeseriesContainerService, times(1)).createContainer(any(TimeseriesContainerIO.class));
  }

  @Test
  void resolveOrCreate_existingContainer_reusedNotCreated() {
    // When an existing "KRL Trajectories" container is found under the DO,
    // createContainer must NOT be called.
    when(timeseriesContainerService.findKrlTrajectoriesContainerForDataObject(DO_APP_ID,
      KrlInterpretService.KRL_TRAJECTORIES_CONTAINER_NAME)).thenReturn(Optional.of(container));
    when(sidecar.interpret(any())).thenReturn(KrlSidecarClient.SidecarOutcome.ok(sidecarOk()));

    KrlInterpretRequestIO req = makeRequest();
    req.setTimeseriesContainerAppId(null);
    KrlInterpretResponseIO response = runWithStaticNeo(req, "alice", null);
    assertNotNull(response);
    verify(timeseriesContainerService, never()).createContainer(any());
  }

  // ── sidecar error mapping ──────────────────────────────────────────────────

  @Test
  void sidecarUnreachable_propagatesAsSidecarException() {
    when(sidecar.interpret(any())).thenReturn(
      KrlSidecarClient.SidecarOutcome.unreachable("connection refused"));
    KrlInterpretService.SidecarException ex = assertThrows(
      KrlInterpretService.SidecarException.class,
      () -> runWithStaticNeo(makeRequest(), "alice", null)
    );
    assertEquals(KrlSidecarClient.SidecarOutcome.Status.UNREACHABLE, ex.getOutcome().status());
  }

  @Test
  void sidecarTimeout_propagatesAsSidecarException() {
    when(sidecar.interpret(any())).thenReturn(
      KrlSidecarClient.SidecarOutcome.timeout("deadline exceeded"));
    KrlInterpretService.SidecarException ex = assertThrows(
      KrlInterpretService.SidecarException.class,
      () -> runWithStaticNeo(makeRequest(), "alice", null)
    );
    assertEquals(KrlSidecarClient.SidecarOutcome.Status.TIMEOUT, ex.getOutcome().status());
  }

  @Test
  void sidecarHardStop_propagatesWith501() {
    when(sidecar.interpret(any())).thenReturn(
      KrlSidecarClient.SidecarOutcome.sidecarError(501, "SPS construct on line 42"));
    KrlInterpretService.SidecarException ex = assertThrows(
      KrlInterpretService.SidecarException.class,
      () -> runWithStaticNeo(makeRequest(), "alice", null)
    );
    assertEquals(501, ex.getOutcome().sidecarStatus().intValue());
  }

  // ── happy path ─────────────────────────────────────────────────────────────

  @Test
  void happyPath_createsTrajectoryReference_andRecordsActivity() {
    when(sidecar.interpret(any())).thenReturn(KrlSidecarClient.SidecarOutcome.ok(sidecarOk()));
    KrlInterpretResponseIO response = runWithStaticNeo(makeRequest(), "alice", null);

    assertNotNull(response);
    assertEquals("018f9c5a-7e26-7000-a000-0000000000ab", response.getTrajectoryAppId());
    assertEquals(ACTIVITY_APP_ID, response.getActivityAppId());
    assertEquals("0.1.0", response.getInterpreterVersion());
    assertEquals(2, response.getWarnings().size());
    assertEquals(1, response.getUnsupportedConstructs().size());
    assertEquals("ikpy", response.getIkSolverStats().getSolverName());

    // Trajectory channels created (joint_0, joint_1, joint_2) → 3 saveDataPoints calls.
    verify(timeseriesService, times(3))
      .saveDataPoints(eq(TSC_OGM_ID), any(), any());

    // TimeseriesReference creation lands once with the right collection + DO ids.
    verify(timeseriesReferenceService, times(1))
      .createReference(eq(COLL_OGM_ID), eq(DO_OGM_ID), any(TimeseriesReferenceIO.class));

    // Activity recorded with sourceMode=human (no X-AI-Agent header).
    verify(provenanceService, times(1)).record(
      eq("EXECUTE"), eq("KrlInterpret"), anyString(), eq("alice"), anyString(),
      eq("POST"), eq("/v2/krl/interpret"), eq(201), anyLong(), anyLong(),
      any(), eq("human"), eq(null)
    );
  }

  @Test
  void happyPath_withAiAgentHeader_recordsAiSourceMode() {
    when(sidecar.interpret(any())).thenReturn(KrlSidecarClient.SidecarOutcome.ok(sidecarOk()));
    runWithStaticNeo(makeRequest(), "alice", "claude-opus-4-7");

    verify(provenanceService, times(1)).record(
      eq("EXECUTE"), eq("KrlInterpret"), anyString(), eq("alice"), anyString(),
      eq("POST"), eq("/v2/krl/interpret"), eq(201), anyLong(), anyLong(),
      any(), eq("ai"), eq("claude-opus-4-7")
    );
  }

  @Test
  void emptyTrajectory_stillCreatesReference_andNoDataPointsWritten() {
    Map<String, Object> body = sidecarOk();
    body.put("trajectory", Map.of("timestamps", List.of(), "joints", List.of()));
    when(sidecar.interpret(any())).thenReturn(KrlSidecarClient.SidecarOutcome.ok(body));

    KrlInterpretResponseIO response = runWithStaticNeo(makeRequest(), "alice", null);

    assertNotNull(response.getTrajectoryAppId());
    verify(timeseriesService, never()).saveDataPoints(anyLong(), any(), any());
    verify(timeseriesReferenceService, times(1))
      .createReference(eq(COLL_OGM_ID), eq(DO_OGM_ID), any(TimeseriesReferenceIO.class));
  }

  @Test
  void datFiles_areFetchedAndForwarded() {
    when(sidecar.interpret(any())).thenReturn(KrlSidecarClient.SidecarOutcome.ok(sidecarOk()));
    when(fileReferenceService.getPayload("dat-1"))
      .thenReturn(new NamedInputStream("oid-dat", new ByteArrayInputStream("DAT".getBytes()), "main.dat", 3L));

    KrlInterpretRequestIO req = makeRequest();
    req.setDatFileAppIds(List.of("dat-1"));
    runWithStaticNeo(req, "alice", null);

    verify(fileReferenceService, times(1)).getPayload("dat-1");
  }

  @Test
  void unknownDataObjectAppId_throwsBadRequest() {
    when(entityIdResolver.resolveLong(DO_APP_ID))
      .thenThrow(new jakarta.ws.rs.NotFoundException("nope"));
    assertThrows(BadRequestException.class,
      () -> service.interpret(makeRequest(), "alice", null));
  }

  @Test
  void unknownContainerAppId_throwsBadRequest() {
    // When an explicit containerAppId is supplied but getContainerByAppId throws
    // InvalidPathException (not found), resolveOrCreateKrlContainer wraps it as
    // BadRequestException.
    when(timeseriesContainerService.getContainerByAppId(TSC_APP_ID))
      .thenThrow(new InvalidPathException("TimeseriesContainer with appId '" + TSC_APP_ID + "' not found"));
    when(sidecar.interpret(any())).thenReturn(KrlSidecarClient.SidecarOutcome.ok(sidecarOk()));
    assertThrows(BadRequestException.class,
      () -> service.interpret(makeRequest(), "alice", null));
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  /**
   * Wraps the call in a MockedStatic of NeoConnector so the overlay
   * cypher path picks up a real (mocked) session and doesn't NPE.
   */
  private KrlInterpretResponseIO runWithStaticNeo(KrlInterpretRequestIO req, String user, String agent) {
    Session session = mock(Session.class);
    NeoConnector connector = mock(NeoConnector.class);
    when(connector.getNeo4jSession()).thenReturn(session);
    try (MockedStatic<NeoConnector> ms = mockStatic(NeoConnector.class)) {
      ms.when(NeoConnector::getInstance).thenReturn(connector);
      return service.interpret(req, user, agent);
    }
  }

  private KrlInterpretRequestIO makeRequest() {
    KrlInterpretRequestIO req = new KrlInterpretRequestIO();
    req.setSrcFileAppId(SRC_APP_ID);
    req.setUrdfFileAppId(URDF_APP_ID);
    req.setSceneAppId(SCENE_APP_ID);
    req.setTargetDataObjectAppId(DO_APP_ID);
    req.setTimeseriesContainerAppId(TSC_APP_ID);
    req.setTimeStep(0.01);
    return req;
  }

  private Map<String, Object> sidecarOk() {
    Map<String, Object> body = new HashMap<>();
    body.put("interpreterVersion", "0.1.0");
    body.put("warnings", List.of(
      Map.of("line", 12, "message", "WAIT FOR skipped offline", "severity", "WARN"),
      Map.of("line", 24, "message", "BCO treated as Wait(0)", "severity", "INFO")
    ));
    body.put("unsupportedConstructs", List.of(
      Map.of("construct", "INTERRUPT", "line", 7, "reason", "HARD-STOP at tier-1")
    ));
    body.put("ikSolverStats", Map.of(
      "meanCycleMs", 12.4, "p99CycleMs", 38.1,
      "maxResidualMeters", 0.00041, "maxResidualRadians", 0.0008,
      "failedPoses", 0, "totalPoses", 1872,
      "solverName", "ikpy", "solverVersion", "3.4.2"
    ));
    Map<String, Object> traj = new HashMap<>();
    traj.put("timestamps", List.of(0L, 10L, 20L));
    traj.put("joints", List.of(
      List.of(0.0, 0.1, 0.2),
      List.of(0.0, 0.05, 0.1),
      List.of(0.5, 0.5, 0.5)
    ));
    body.put("trajectory", traj);
    return body;
  }
}
