package de.dlr.shepard.v2.transform.krl.services;

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
import io.quarkus.arc.Unremovable;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.neo4j.ogm.session.Session;

/**
 * V2CONV-B5 — orchestrates a KRL interpret as a MAPPING_RECIPE transform.
 *
 * <p>This is the converged successor of the bespoke {@code KrlInterpretService}
 * (which served the now-removed {@code POST /v2/krl/interpret}). The logic is
 * unchanged in substance — resolve {@code .src} + URDF bytes, call the sidecar,
 * persist a derived joint-trajectory {@code TimeseriesReference} — but it is now
 * driven by the generic {@link de.dlr.shepard.spi.transform.TransformExecutor}
 * dispatch (the {@link KrlTrajectoryTransformExecutor}) instead of a dedicated
 * REST resource. The derived reference's appId is the materialize result.
 *
 * <ol>
 *   <li>Resolve {@code srcFileAppId} / {@code urdfFileAppId} / optional
 *       {@code datFileAppIds} → byte payloads.</li>
 *   <li>Resolve {@code targetDataObjectAppId} → owning Collection (for the
 *       implicit permission check via
 *       {@link TimeseriesReferenceService#createReference}).</li>
 *   <li>Call the sidecar via {@link KrlSidecarClient#interpret}.</li>
 *   <li>On success: parse the trajectory, persist channels into the bound
 *       {@code TimeseriesContainer}, create a {@code TimeseriesReference}
 *       linked to the target DataObject.</li>
 *   <li>Stamp a {@code :KrlTrajectoryActivity} (label + property overlay on the
 *       base {@code :Activity}) with USED edges to the inputs and a GENERATED
 *       edge to the derived trajectory — best-effort, fire-and-forget.</li>
 *   <li>Return the derived reference's appId.</li>
 * </ol>
 *
 * <p>{@code @ApplicationScoped} (not request-scoped like its predecessor): the
 * executor that drives it is a ServiceLoader POJO that looks this bean up lazily
 * via CDI inside the materialize request scope, so the service must be reachable
 * from the application-scoped context.
 */
@ApplicationScoped
@Unremovable
public class KrlTrajectoryService {

  /** Target-kind label for the recorded {@link Activity}. */
  static final String TARGET_KIND = "KrlTrajectory";

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
   * Top-level orchestration for a MAPPING_RECIPE-driven KRL interpret.
   *
   * @return the appId of the derived joint-trajectory {@link TimeseriesReference}
   * @throws BadRequestException on input validation / resolution failure
   * @throws NotFoundException on a missing container
   * @throws SidecarException on sidecar IO problems (mapped to 422 by the
   *         executor's TransformException wrapper)
   */
  public String interpret(KrlTrajectoryParams params, String agentUsername, String aiAgent) {
    long startedAt = System.currentTimeMillis();
    validate(params);

    // 1. Resolve file payloads.
    byte[] srcBytes = fetchBytes(params.srcFileAppId(), "srcFileAppId");
    byte[] urdfBytes = fetchBytes(params.urdfFileAppId(), "urdfFileAppId");
    Map<String, byte[]> datBytes = new HashMap<>();
    if (params.datFileAppIds() != null) {
      for (String datId : params.datFileAppIds()) {
        if (datId != null && !datId.isBlank()) {
          datBytes.put(datId, fetchBytes(datId, "datFileAppIds[]"));
        }
      }
    }

    // 2. Resolve target DataObject → its owning collection.
    DataObject target = resolveDataObject(params.targetDataObjectAppId());
    long targetDataObjectShepardId = target.getShepardId();
    long collectionShepardId = target.getCollection().getShepardId();

    // 3. Resolve the TimeseriesContainer the trajectory writes to.
    TimeseriesContainer container = timeseriesContainerService
      .getContainerByAppId(params.timeseriesContainerAppId());
    if (container == null) {
      throw new NotFoundException(
        "No TimeseriesContainer with appId " + params.timeseriesContainerAppId()
      );
    }
    long containerShepardId = container.getId();

    // 4. Call the sidecar.
    Map<String, Object> sidecarBody = buildSidecarBody(params, srcBytes, urdfBytes, datBytes);
    KrlSidecarClient.SidecarOutcome outcome = sidecar.interpret(sidecarBody);
    if (!outcome.isOk()) {
      throw new SidecarException(outcome);
    }

    // 5. Persist trajectory channels + reference.
    TrajectoryEnvelope traj = parseTrajectory(outcome.body());
    TimeseriesReference reference = persistTrajectory(
      traj, container, containerShepardId, collectionShepardId, targetDataObjectShepardId, params
    );

    // 6. Record :KrlTrajectoryActivity — best-effort per the secondary-write rule.
    IkStats stats = parseStats(outcome.body());
    int warningCount = countList(outcome.body(), "warnings");
    int unsupportedCount = countList(outcome.body(), "unsupportedConstructs");
    String interpreterVersion = asString(outcome.body().get("interpreterVersion"));
    recordActivity(
      params, agentUsername, aiAgent, startedAt, reference.getAppId(),
      interpreterVersion, stats, warningCount, unsupportedCount
    );

    return reference.getAppId();
  }

  // ── validation ────────────────────────────────────────────────────────────

  void validate(KrlTrajectoryParams params) {
    if (params == null) throw new BadRequestException("KRL trajectory params required");
    requireBlank("srcFileAppId", params.srcFileAppId());
    requireBlank("urdfFileAppId", params.urdfFileAppId());
    requireBlank("targetDataObjectAppId", params.targetDataObjectAppId());
    requireBlank("timeseriesContainerAppId", params.timeseriesContainerAppId());
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
    KrlTrajectoryParams params,
    byte[] srcBytes,
    byte[] urdfBytes,
    Map<String, byte[]> datBytes
  ) {
    Map<String, Object> body = new HashMap<>();
    body.put("srcFileAppId", params.srcFileAppId());
    body.put("urdfFileAppId", params.urdfFileAppId());
    body.put("srcContent", Base64.getEncoder().encodeToString(srcBytes));
    body.put("urdfContent", Base64.getEncoder().encodeToString(urdfBytes));
    if (!datBytes.isEmpty()) {
      Map<String, String> dat = new HashMap<>();
      datBytes.forEach((id, bytes) -> dat.put(id, Base64.getEncoder().encodeToString(bytes)));
      body.put("datContent", dat);
      body.put("datFileAppIds", new ArrayList<>(datBytes.keySet()));
    }
    return body;
  }

  // ── trajectory parsing + persistence ─────────────────────────────────────

  /**
   * Parses the sidecar's trajectory shape: {@code {timestamps: [...],
   * joints: [[j0_t0, j0_t1, ...], ...]}}.
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
    KrlTrajectoryParams params
  ) {
    List<Timeseries> channels = new ArrayList<>();
    long start = traj.timestamps.isEmpty() ? System.currentTimeMillis() : traj.timestamps.get(0);
    long end = traj.timestamps.isEmpty()
      ? start
      : traj.timestamps.get(traj.timestamps.size() - 1);

    String measurement = "krl_trajectory_" + params.srcFileAppId();
    String device = "krl-interpreter";
    String location = "offline";
    String symbolicName = params.srcFileAppId();

    for (int i = 0; i < traj.joints.size(); i++) {
      String field = "joint_" + i;
      Timeseries ts = new Timeseries(measurement, device, location, symbolicName, field);
      channels.add(ts);

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
    refIO.setName("KRL trajectory — " + params.srcFileAppId());
    refIO.setStart(start);
    refIO.setEnd(end);
    refIO.setTimeseries(channels);
    refIO.setTimeseriesContainerId(containerShepardId);

    return timeseriesReferenceService.createReference(collectionShepardId, dataObjectShepardId, refIO);
  }

  // ── response parsing ─────────────────────────────────────────────────────

  IkStats parseStats(Map<String, Object> body) {
    Object o = body.get("ikSolverStats");
    if (!(o instanceof Map<?, ?> m)) return IkStats.empty();
    return new IkStats(
      asDouble(m.get("meanCycleMs")),
      asDouble(m.get("p99CycleMs")),
      asDouble(m.get("maxResidualMeters")),
      asInteger(m.get("failedPoses")),
      asInteger(m.get("totalPoses")),
      asString(m.get("solverName")),
      asString(m.get("solverVersion"))
    );
  }

  private static int countList(Map<String, Object> body, String key) {
    Object o = body.get(key);
    return o instanceof List<?> list ? list.size() : 0;
  }

  // ── activity ─────────────────────────────────────────────────────────────

  /**
   * Records the {@code :KrlTrajectoryActivity} row. Best-effort — never
   * propagates the exception (secondary write).
   */
  void recordActivity(
    KrlTrajectoryParams params,
    String agentUsername,
    String aiAgent,
    long startedAt,
    String trajectoryAppId,
    String interpreterVersion,
    IkStats stats,
    int warningCount,
    int unsupportedCount
  ) {
    try {
      String sourceMode = (aiAgent != null && !aiAgent.isBlank()) ? "ai" : "human";
      String summary = "materialize KrlTrajectoryShape src=" + params.srcFileAppId();
      long endedAt = System.currentTimeMillis();
      Activity recorded = provenanceService.record(
        "EXECUTE",
        TARGET_KIND,
        trajectoryAppId,
        agentUsername,
        summary,
        "POST",
        "/v2/mappings/" + params.templateAppId() + "/materialize",
        200,
        startedAt,
        endedAt,
        null,
        sourceMode,
        (aiAgent == null || aiAgent.isBlank()) ? null : aiAgent
      );
      if (recorded == null || recorded.getAppId() == null) {
        return;
      }
      overlayKrlActivity(
        recorded.getAppId(), params, interpreterVersion, stats, warningCount, unsupportedCount);
    } catch (RuntimeException e) {
      Log.warnf(e, "KRL: provenance capture failed (best-effort)");
    }
  }

  /**
   * Adds the {@code :KrlTrajectoryActivity} label + KRL-specific properties to
   * the just-saved {@code :Activity}, and wires USED edges to the inputs. All
   * operations are idempotent and best-effort.
   */
  void overlayKrlActivity(
    String activityAppId,
    KrlTrajectoryParams params,
    String interpreterVersion,
    IkStats stats,
    int warningCount,
    int unsupportedCount
  ) {
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return;

    Map<String, Object> p = new HashMap<>();
    p.put("appId", activityAppId);
    p.put("srcFileAppId", params.srcFileAppId());
    p.put("urdfFileAppId", params.urdfFileAppId());
    p.put("interpreterVersion", interpreterVersion);
    p.put("ikSolverName", stats.solverName());
    p.put("ikSolverVersion", stats.solverVersion());
    p.put("ikMeanCycleMs", stats.meanCycleMs());
    p.put("ikP99CycleMs", stats.p99CycleMs());
    p.put("ikMaxResidualMeters", stats.maxResidualMeters());
    p.put("ikFailedPoses", stats.failedPoses());
    p.put("ikTotalPoses", stats.totalPoses());
    p.put("warningCount", warningCount);
    p.put("unsupportedConstructCount", unsupportedCount);

    String label =
      "MATCH (a:Activity {appId: $appId}) SET a:KrlTrajectoryActivity, " +
      "a.srcFileAppId = $srcFileAppId, " +
      "a.urdfFileAppId = $urdfFileAppId, " +
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
      session.query(label, p);
    } catch (RuntimeException ex) {
      Log.debugf(ex, "KRL: activity label overlay failed for %s (non-fatal)", activityAppId);
    }

    wireUsedEdge(session, activityAppId, params.srcFileAppId());
    wireUsedEdge(session, activityAppId, params.urdfFileAppId());
    if (params.datFileAppIds() != null) {
      for (String datId : params.datFileAppIds()) {
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

  // ── envelope + records ───────────────────────────────────────────────────

  record TrajectoryEnvelope(List<Long> timestamps, List<List<Double>> joints) {}

  /** Minimal IK-solver convergence stats parsed from the sidecar body (for the Activity overlay). */
  record IkStats(
    Double meanCycleMs,
    Double p99CycleMs,
    Double maxResidualMeters,
    Integer failedPoses,
    Integer totalPoses,
    String solverName,
    String solverVersion
  ) {
    static IkStats empty() {
      return new IkStats(null, null, null, null, null, null, null);
    }
  }

  /**
   * Thrown when the sidecar call cannot be honoured — wraps the discriminated
   * {@link KrlSidecarClient.SidecarOutcome} so the executor can map it to a
   * {@link de.dlr.shepard.spi.transform.TransformException}.
   */
  public static class SidecarException extends RuntimeException {
    private final transient KrlSidecarClient.SidecarOutcome outcome;

    public SidecarException(KrlSidecarClient.SidecarOutcome outcome) {
      super("KRL sidecar call failed: status=" + outcome.status() + " detail=" + outcome.errorDetail());
      this.outcome = outcome;
    }

    public KrlSidecarClient.SidecarOutcome getOutcome() {
      return outcome;
    }
  }
}
