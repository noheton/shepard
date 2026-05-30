package de.dlr.shepard.v2.krl.services;

import de.dlr.shepard.common.identifier.EntityIdResolver;
import de.dlr.shepard.common.mongoDB.NamedInputStream;
import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.context.collection.entities.DataObject;
import de.dlr.shepard.context.collection.services.DataObjectService;
import de.dlr.shepard.context.references.file.services.SingletonFileReferenceService;
import de.dlr.shepard.context.references.timeseriesreference.io.TimeseriesReferenceIO;
import de.dlr.shepard.context.references.timeseriesreference.model.TimeseriesReference;
import de.dlr.shepard.context.references.timeseriesreference.services.TimeseriesReferenceService;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesDataPoint;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import de.dlr.shepard.data.timeseries.services.TimeseriesService;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.services.ProvenanceService;
import de.dlr.shepard.v2.krl.io.KrlIkStatsIO;
import de.dlr.shepard.v2.krl.io.KrlInterpretRequestIO;
import de.dlr.shepard.v2.krl.io.KrlInterpretResponseIO;
import de.dlr.shepard.v2.krl.io.KrlUnsupportedConstructIO;
import de.dlr.shepard.v2.krl.io.KrlWarningIO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.neo4j.ogm.session.Session;

/**
 * KRL-INTERPRETER-05 — orchestrates the interpret request.
 *
 * <ol>
 *   <li>Resolve {@code srcFileAppId} / {@code urdfFileAppId} / optional
 *       {@code datFileAppIds} → byte payloads.</li>
 *   <li>Resolve {@code targetDataObjectAppId} → owning Collection (for
 *       the implicit permission check via
 *       {@link TimeseriesReferenceService#createReference}).</li>
 *   <li>Call the sidecar via {@link KrlSidecarClient#interpret}.</li>
 *   <li>On success: parse the trajectory, persist channels into the
 *       configured {@code TimeseriesContainer}, create a
 *       {@code TimeseriesReference} linked to the target DataObject.</li>
 *   <li>Stamp a {@code :KrlInterpretActivity} (label + property
 *       overlay on the base {@code :Activity}) with the §7.1 edge set
 *       — {@code USED → src / urdf / dat / scene}, {@code GENERATED →
 *       trajectory TimeseriesReference}, {@code WAS_ASSOCIATED_WITH →
 *       :User}. The handler hands off {@code PROP_SKIP_CAPTURE} so the
 *       capture filter does not emit a duplicate generic Activity.</li>
 *   <li>Return the response envelope.</li>
 * </ol>
 *
 * <p>All secondary writes (Activity, KRL-label overlay, USED edges)
 * are wrapped in try/catch per the "secondary writes are
 * fire-and-forget" rule — a Neo4j hiccup on the audit overlay never
 * fails the primary trajectory write.
 */
@RequestScoped
public class KrlInterpretService {

  /** Target-kind label for the recorded {@link Activity}. */
  static final String TARGET_KIND = "KrlInterpret";

  /** Predicate constant used to annotate joint channels per the preselection principle. */
  public static final String JOINT_PREDICATE = "urn:shepard:urdf:joint";

  @Inject KrlSidecarClient sidecar;
  @Inject SingletonFileReferenceService fileReferenceService;
  @Inject TimeseriesContainerService timeseriesContainerService;
  @Inject TimeseriesService timeseriesService;
  @Inject TimeseriesReferenceService timeseriesReferenceService;
  @Inject DataObjectService dataObjectService;
  @Inject EntityIdResolver entityIdResolver;
  @Inject ProvenanceService provenanceService;

  /**
   * Top-level orchestration. Throws a {@link BadRequestException} on
   * input validation failure, {@link NotFoundException} on missing
   * resources, {@link SidecarException} on sidecar IO problems
   * (mapped to 502 / 504 by the resource layer).
   */
  public KrlInterpretResponseIO interpret(
    KrlInterpretRequestIO request,
    String agentUsername,
    String aiAgent
  ) {
    long startedAt = System.currentTimeMillis();
    validate(request);

    // 1. Resolve file payloads.
    byte[] srcBytes = fetchBytes(request.getSrcFileAppId(), "srcFileAppId");
    byte[] urdfBytes = fetchBytes(request.getUrdfFileAppId(), "urdfFileAppId");
    Map<String, byte[]> datBytes = new HashMap<>();
    if (request.getDatFileAppIds() != null) {
      for (String datId : request.getDatFileAppIds()) {
        if (datId != null && !datId.isBlank()) {
          datBytes.put(datId, fetchBytes(datId, "datFileAppIds[]"));
        }
      }
    }

    // 2. Resolve target DataObject → its owning collection.
    DataObject target = resolveDataObject(request.getTargetDataObjectAppId());
    long targetDataObjectShepardId = target.getShepardId();
    long collectionShepardId = target.getCollection().getShepardId();

    // 3. Resolve the TimeseriesContainer the trajectory writes to.
    TimeseriesContainer container = timeseriesContainerService
      .getContainerByAppId(request.getTimeseriesContainerAppId());
    if (container == null) {
      throw new NotFoundException(
        "No TimeseriesContainer with appId " + request.getTimeseriesContainerAppId()
      );
    }
    long containerShepardId = container.getId();

    // 4. Call the sidecar.
    Map<String, Object> sidecarBody = buildSidecarBody(request, srcBytes, urdfBytes, datBytes);
    KrlSidecarClient.SidecarOutcome outcome = sidecar.interpret(sidecarBody);
    if (!outcome.isOk()) {
      throw new SidecarException(outcome);
    }

    // 5. Persist trajectory channels + reference.
    TrajectoryEnvelope traj = parseTrajectory(outcome.body());
    TimeseriesReference reference = persistTrajectory(
      traj,
      container,
      containerShepardId,
      collectionShepardId,
      targetDataObjectShepardId,
      request
    );

    // 6. Build response.
    KrlIkStatsIO stats = parseStats(outcome.body());
    List<KrlWarningIO> warnings = parseWarnings(outcome.body());
    List<KrlUnsupportedConstructIO> unsupported = parseUnsupported(outcome.body());
    String interpreterVersion = asString(outcome.body().get("interpreterVersion"));

    // 7. Record :KrlInterpretActivity — best-effort per the secondary-write rule.
    String activityAppId = recordActivity(
      request, agentUsername, aiAgent, startedAt, reference.getAppId(),
      interpreterVersion, stats, warnings.size(), unsupported.size()
    );

    return KrlInterpretResponseIO.builder()
      .trajectoryAppId(reference.getAppId())
      .activityAppId(activityAppId)
      .warnings(warnings)
      .unsupportedConstructs(unsupported)
      .ikSolverStats(stats)
      .interpreterVersion(interpreterVersion)
      .build();
  }

  // ── validation ────────────────────────────────────────────────────────────

  void validate(KrlInterpretRequestIO request) {
    if (request == null) throw new BadRequestException("request body required");
    requireBlank("srcFileAppId", request.getSrcFileAppId());
    requireBlank("urdfFileAppId", request.getUrdfFileAppId());
    requireBlank("targetDataObjectAppId", request.getTargetDataObjectAppId());
    requireBlank("timeseriesContainerAppId", request.getTimeseriesContainerAppId());
  }

  private static void requireBlank(String name, String value) {
    if (value == null || value.isBlank()) {
      throw new BadRequestException(name + " is required");
    }
  }

  // ── helpers ───────────────────────────────────────────────────────────────

  byte[] fetchBytes(String fileReferenceAppId, String fieldName) {
    NamedInputStream named;
    try {
      named = fileReferenceService.getPayload(fileReferenceAppId);
    } catch (NotFoundException nfe) {
      throw new BadRequestException(fieldName + " " + fileReferenceAppId + " not found");
    }
    try (InputStream is = named.getInputStream()) {
      return is.readAllBytes();
    } catch (IOException e) {
      throw new SidecarException(KrlSidecarClient.SidecarOutcome.unreachable(
        "Failed to read payload for " + fieldName + ": " + e.getMessage()));
    }
  }

  DataObject resolveDataObject(String appId) {
    long ogmId;
    try {
      ogmId = entityIdResolver.resolveLong(appId);
    } catch (NotFoundException nfe) {
      throw new BadRequestException("targetDataObjectAppId " + appId + " not found");
    }
    DataObject lookup;
    try {
      lookup = dataObjectService.getDataObject(ogmId);
    } catch (RuntimeException e) {
      throw new BadRequestException(
        "targetDataObjectAppId " + appId + " not found or not accessible: " + e.getMessage());
    }
    if (lookup == null || lookup.getCollection() == null) {
      throw new BadRequestException(
        "targetDataObjectAppId " + appId + " has no owning Collection");
    }
    return lookup;
  }

  Map<String, Object> buildSidecarBody(
    KrlInterpretRequestIO request,
    byte[] srcBytes,
    byte[] urdfBytes,
    Map<String, byte[]> datBytes
  ) {
    Map<String, Object> body = new HashMap<>();
    body.put("srcFileAppId", request.getSrcFileAppId());
    body.put("urdfFileAppId", request.getUrdfFileAppId());
    body.put("srcContent", Base64.getEncoder().encodeToString(srcBytes));
    body.put("urdfContent", Base64.getEncoder().encodeToString(urdfBytes));
    if (!datBytes.isEmpty()) {
      Map<String, String> dat = new HashMap<>();
      datBytes.forEach((id, bytes) -> dat.put(id, Base64.getEncoder().encodeToString(bytes)));
      body.put("datContent", dat);
      body.put("datFileAppIds", new ArrayList<>(datBytes.keySet()));
    }
    if (request.getSceneAppId() != null) body.put("sceneAppId", request.getSceneAppId());
    if (request.getBaseFrame() != null) body.put("baseFrame", request.getBaseFrame());
    if (request.getToolFrame() != null) body.put("toolFrame", request.getToolFrame());
    if (request.getSeedPose() != null) body.put("seedPose", request.getSeedPose());
    if (request.getTimeStep() != null) body.put("timeStep", request.getTimeStep());
    if (request.getOptions() != null) body.put("options", request.getOptions());
    return body;
  }

  // ── trajectory parsing + persistence ─────────────────────────────────────

  /**
   * Parses the sidecar's trajectory shape: {@code {timestamps: [...],
   * joints: [[j0_t0, j0_t1, ...], ...]}}.
   *
   * <p>If the sidecar omits {@code trajectory}, the channel list is
   * empty — the TimeseriesReference still lands so the activity edge
   * has a target. This is the documented degraded shape (e.g. when the
   * sidecar returned only warnings + an empty trajectory).
   */
  TrajectoryEnvelope parseTrajectory(Map<String, Object> body) {
    Object t = body.get("trajectory");
    if (!(t instanceof Map<?, ?> trajMap)) return new TrajectoryEnvelope(List.of(), List.of());
    Object tsObj = trajMap.get("timestamps");
    Object jointsObj = trajMap.get("joints");
    List<Long> timestamps = new ArrayList<>();
    if (tsObj instanceof List<?> list) {
      for (Object o : list) {
        if (o instanceof Number n) timestamps.add(n.longValue());
      }
    }
    List<List<Double>> joints = new ArrayList<>();
    if (jointsObj instanceof List<?> outer) {
      for (Object row : outer) {
        List<Double> series = new ArrayList<>();
        if (row instanceof List<?> inner) {
          for (Object o : inner) {
            if (o instanceof Number n) series.add(n.doubleValue());
          }
        }
        joints.add(series);
      }
    }
    return new TrajectoryEnvelope(timestamps, joints);
  }

  TimeseriesReference persistTrajectory(
    TrajectoryEnvelope traj,
    TimeseriesContainer container,
    long containerShepardId,
    long collectionShepardId,
    long dataObjectShepardId,
    KrlInterpretRequestIO request
  ) {
    List<Timeseries> channels = new ArrayList<>();
    long start = traj.timestamps.isEmpty() ? System.currentTimeMillis() : traj.timestamps.get(0);
    long end = traj.timestamps.isEmpty()
      ? start
      : traj.timestamps.get(traj.timestamps.size() - 1);

    String measurement = "krl_trajectory_" + request.getSrcFileAppId();
    String device = "krl-interpreter";
    String location = "offline";
    String symbolicName = request.getSrcFileAppId();

    for (int i = 0; i < traj.joints.size(); i++) {
      String field = "joint_" + i;
      Timeseries ts = new Timeseries(measurement, device, location, symbolicName, field);
      channels.add(ts);

      // Persist data points for the channel — best-effort per joint.
      List<Double> samples = traj.joints.get(i);
      List<TimeseriesDataPoint> points = new ArrayList<>(samples.size());
      for (int k = 0; k < samples.size() && k < traj.timestamps.size(); k++) {
        points.add(new TimeseriesDataPoint(traj.timestamps.get(k), samples.get(k)));
      }
      try {
        if (!points.isEmpty()) {
          timeseriesService.saveDataPoints(containerShepardId, ts, points);
        }
      } catch (RuntimeException ex) {
        Log.warnf(ex, "KRL: failed to write trajectory channel joint_%d to container %s — "
          + "channel registered on reference but data points missing", i, container.getAppId());
      }
    }

    TimeseriesReferenceIO refIO = new TimeseriesReferenceIO();
    refIO.setName("KRL trajectory — " + request.getSrcFileAppId());
    refIO.setStart(start);
    refIO.setEnd(end);
    refIO.setTimeseries(channels);
    refIO.setTimeseriesContainerId(containerShepardId);

    return timeseriesReferenceService.createReference(collectionShepardId, dataObjectShepardId, refIO);
  }

  // ── response parsing ─────────────────────────────────────────────────────

  KrlIkStatsIO parseStats(Map<String, Object> body) {
    Object o = body.get("ikSolverStats");
    if (!(o instanceof Map<?, ?> m)) return new KrlIkStatsIO();
    KrlIkStatsIO stats = new KrlIkStatsIO();
    stats.setMeanCycleMs(asDouble(m.get("meanCycleMs")));
    stats.setP99CycleMs(asDouble(m.get("p99CycleMs")));
    stats.setMaxResidualMeters(asDouble(m.get("maxResidualMeters")));
    stats.setMaxResidualRadians(asDouble(m.get("maxResidualRadians")));
    stats.setFailedPoses(asInteger(m.get("failedPoses")));
    stats.setTotalPoses(asInteger(m.get("totalPoses")));
    stats.setSolverName(asString(m.get("solverName")));
    stats.setSolverVersion(asString(m.get("solverVersion")));
    return stats;
  }

  List<KrlWarningIO> parseWarnings(Map<String, Object> body) {
    List<KrlWarningIO> out = new ArrayList<>();
    Object o = body.get("warnings");
    if (o instanceof List<?> list) {
      for (Object e : list) {
        if (e instanceof Map<?, ?> m) {
          out.add(new KrlWarningIO(
            asInteger(m.get("line")),
            asString(m.get("message")),
            asString(m.get("severity"))
          ));
        }
      }
    }
    return out;
  }

  List<KrlUnsupportedConstructIO> parseUnsupported(Map<String, Object> body) {
    List<KrlUnsupportedConstructIO> out = new ArrayList<>();
    Object o = body.get("unsupportedConstructs");
    if (o instanceof List<?> list) {
      for (Object e : list) {
        if (e instanceof Map<?, ?> m) {
          out.add(new KrlUnsupportedConstructIO(
            asString(m.get("construct")),
            asInteger(m.get("line")),
            asString(m.get("reason"))
          ));
        }
      }
    }
    return out;
  }

  // ── activity ─────────────────────────────────────────────────────────────

  /**
   * Records the {@code :KrlInterpretActivity} row. Best-effort — returns
   * {@code null} (no activityAppId in response) if the secondary write
   * fails, but never propagates the exception.
   */
  String recordActivity(
    KrlInterpretRequestIO request,
    String agentUsername,
    String aiAgent,
    long startedAt,
    String trajectoryAppId,
    String interpreterVersion,
    KrlIkStatsIO stats,
    int warningCount,
    int unsupportedCount
  ) {
    try {
      String sourceMode = (aiAgent != null && !aiAgent.isBlank()) ? "ai" : "human";
      String summary = "POST /v2/krl/interpret src=" + request.getSrcFileAppId();
      long endedAt = System.currentTimeMillis();
      Activity recorded = provenanceService.record(
        "EXECUTE",
        TARGET_KIND,
        trajectoryAppId,
        agentUsername,
        summary,
        "POST",
        "/v2/krl/interpret",
        201,
        startedAt,
        endedAt,
        null,
        sourceMode,
        (aiAgent == null || aiAgent.isBlank()) ? null : aiAgent
      );
      if (recorded == null || recorded.getAppId() == null) {
        return null;
      }
      overlayKrlActivity(
        recorded.getAppId(), request, trajectoryAppId, interpreterVersion, stats,
        warningCount, unsupportedCount);
      return recorded.getAppId();
    } catch (RuntimeException e) {
      Log.warnf(e, "KRL: provenance capture failed (best-effort)");
      return null;
    }
  }

  /**
   * Adds the second {@code :KrlInterpretActivity} label and the
   * KRL-specific properties to the just-saved {@code :Activity} node,
   * and wires the supplementary {@code USED} edges to source / URDF /
   * scene / dat FileReferences. All operations are idempotent.
   *
   * <p>The base {@code USED → trajectoryAppId} edge is wired by
   * {@link de.dlr.shepard.provenance.daos.ActivityDAO#wireEdges} via
   * the {@code targetAppId} we passed; this overlay only adds the
   * supplementary edges for inputs (src / urdf / scene / dat) that the
   * standard wiring does not know about.
   */
  void overlayKrlActivity(
    String activityAppId,
    KrlInterpretRequestIO request,
    String trajectoryAppId,
    String interpreterVersion,
    KrlIkStatsIO stats,
    int warningCount,
    int unsupportedCount
  ) {
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return;

    Map<String, Object> params = new HashMap<>();
    params.put("appId", activityAppId);
    params.put("srcFileAppId", request.getSrcFileAppId());
    params.put("urdfFileAppId", request.getUrdfFileAppId());
    params.put("sceneAppId", request.getSceneAppId());
    params.put("interpreterVersion", interpreterVersion);
    params.put("ikSolverName", stats.getSolverName());
    params.put("ikSolverVersion", stats.getSolverVersion());
    params.put("ikMeanCycleMs", stats.getMeanCycleMs());
    params.put("ikP99CycleMs", stats.getP99CycleMs());
    params.put("ikMaxResidualMeters", stats.getMaxResidualMeters());
    params.put("ikFailedPoses", stats.getFailedPoses());
    params.put("ikTotalPoses", stats.getTotalPoses());
    params.put("warningCount", warningCount);
    params.put("unsupportedConstructCount", unsupportedCount);

    String label =
      "MATCH (a:Activity {appId: $appId}) SET a:KrlInterpretActivity, " +
      "a.srcFileAppId = $srcFileAppId, " +
      "a.urdfFileAppId = $urdfFileAppId, " +
      "a.sceneAppId = $sceneAppId, " +
      "a.interpreterVersion = $interpreterVersion, " +
      "a.ikSolverName = $ikSolverName, " +
      "a.ikSolverVersion = $ikSolverVersion, " +
      "a.ikMeanCycleMs = $ikMeanCycleMs, " +
      "a.ikP99CycleMs = $ikP99CycleMs, " +
      "a.ikMaxResidualMeters = $ikMaxResidualMeters, " +
      "a.ikFailedPoses = $ikFailedPoses, " +
      "a.ikTotalPoses = $ikTotalPoses, " +
      "a.warningCount = $warningCount, " +
      "a.unsupportedConstructCount = $unsupportedConstructCount";
    try {
      session.query(label, params);
    } catch (RuntimeException ex) {
      Log.debugf(ex, "KRL: activity label overlay failed for %s (non-fatal)", activityAppId);
    }

    wireUsedEdge(session, activityAppId, request.getSrcFileAppId());
    wireUsedEdge(session, activityAppId, request.getUrdfFileAppId());
    if (request.getSceneAppId() != null) {
      wireUsedEdge(session, activityAppId, request.getSceneAppId());
    }
    if (request.getDatFileAppIds() != null) {
      for (String datId : request.getDatFileAppIds()) {
        wireUsedEdge(session, activityAppId, datId);
      }
    }
  }

  private static void wireUsedEdge(Session session, String activityAppId, String targetAppId) {
    if (targetAppId == null || targetAppId.isBlank()) return;
    try {
      session.query(
        "MATCH (a:Activity {appId: $activityAppId}) " +
        "MATCH (t {appId: $targetAppId}) " +
        "MERGE (a)-[:USED]->(t)",
        Map.of("activityAppId", activityAppId, "targetAppId", targetAppId)
      );
    } catch (RuntimeException ex) {
      Log.debugf(ex, "KRL: USED edge wiring failed (%s -> %s)", activityAppId, targetAppId);
    }
  }

  // ── type coercion ────────────────────────────────────────────────────────

  private static String asString(Object o) { return o == null ? null : String.valueOf(o); }
  private static Double asDouble(Object o) {
    if (o instanceof Number n) return n.doubleValue();
    if (o instanceof String s) try { return Double.parseDouble(s); } catch (NumberFormatException e) { return null; }
    return null;
  }
  private static Integer asInteger(Object o) {
    if (o instanceof Number n) return n.intValue();
    if (o instanceof String s) try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    return null;
  }

  // ── envelope + exception ─────────────────────────────────────────────────

  record TrajectoryEnvelope(List<Long> timestamps, List<List<Double>> joints) {}

  /**
   * Thrown when the sidecar call cannot be honoured — wraps the
   * discriminated {@link KrlSidecarClient.SidecarOutcome} so the
   * resource layer can map it to 502 / 504 per design §7.3.
   */
  public static class SidecarException extends RuntimeException {
    private final KrlSidecarClient.SidecarOutcome outcome;

    public SidecarException(KrlSidecarClient.SidecarOutcome outcome) {
      super(String.format(Locale.ROOT, "KRL sidecar call failed: status=%s detail=%s",
        outcome.status(), outcome.errorDetail()));
      this.outcome = outcome;
    }

    public KrlSidecarClient.SidecarOutcome getOutcome() {
      return outcome;
    }
  }
}
