package de.dlr.shepard.v2.transform.urscript.services;

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
 * URSCRIPT-TRAJECTORY-1 — orchestrates a URScript interpret as a MAPPING_RECIPE
 * transform.
 *
 * <p>The UR-robot sibling of {@code KrlTrajectoryService}: resolves the
 * {@code .urscript}/{@code .script} + URDF bytes, calls the URScript interpreter
 * sidecar, and persists a derived joint-trajectory {@code TimeseriesReference}.
 * Driven by the generic {@link de.dlr.shepard.spi.transform.TransformExecutor}
 * dispatch (the {@link de.dlr.shepard.v2.transform.urscript.UrScriptTrajectoryTransformExecutor})
 * via {@code POST /v2/mappings/{templateAppId}/materialize}.
 *
 * <ol>
 *   <li>Resolve {@code urscriptFileAppId} / {@code urdfFileAppId} → byte payloads.</li>
 *   <li>Resolve {@code targetDataObjectAppId} → owning Collection.</li>
 *   <li>Call the sidecar via {@link UrScriptSidecarClient#interpret}.</li>
 *   <li>Parse the trajectory, persist channels into the bound
 *       {@code TimeseriesContainer}, create a {@code TimeseriesReference}.</li>
 *   <li>Stamp a {@code :UrScriptTrajectoryActivity} (best-effort, fire-and-forget).</li>
 *   <li>Return the derived reference's appId.</li>
 * </ol>
 *
 * <p>{@code @ApplicationScoped}: the executor is a ServiceLoader POJO that looks
 * this bean up lazily via CDI inside the materialize request scope.
 */
@ApplicationScoped
public class UrScriptTrajectoryService {

  /** Target-kind label for the recorded {@link Activity}. */
  static final String TARGET_KIND = "UrScriptTrajectory";

  @Inject UrScriptSidecarClient sidecar;
  @Inject SingletonFileReferenceService fileReferenceService;
  @Inject TimeseriesContainerService timeseriesContainerService;
  @Inject TimeseriesService timeseriesService;
  @Inject TimeseriesReferenceService timeseriesReferenceService;
  @Inject DataObjectService dataObjectService;
  @Inject EntityIdResolver entityIdResolver;
  @Inject ProvenanceService provenanceService;

  /**
   * Top-level orchestration for a MAPPING_RECIPE-driven URScript interpret.
   *
   * @return the appId of the derived joint-trajectory {@link TimeseriesReference}
   * @throws BadRequestException on input validation / resolution failure
   * @throws NotFoundException on a missing container
   * @throws SidecarException on sidecar IO problems (mapped to 422 by the
   *         executor's TransformException wrapper)
   */
  public String interpret(UrScriptTrajectoryParams params, String agentUsername, String aiAgent) {
    long startedAt = System.currentTimeMillis();
    validate(params);

    // 1. Resolve file payloads.
    byte[] urscriptBytes = fetchBytes(params.urscriptFileAppId(), "urscriptFileAppId");
    byte[] urdfBytes = fetchBytes(params.urdfFileAppId(), "urdfFileAppId");

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
    Map<String, Object> sidecarBody = buildSidecarBody(params, urscriptBytes, urdfBytes);
    UrScriptSidecarClient.SidecarOutcome outcome = sidecar.interpret(sidecarBody);
    if (!outcome.isOk()) {
      throw new SidecarException(outcome);
    }

    // 5. Persist trajectory channels + reference.
    TrajectoryEnvelope traj = parseTrajectory(outcome.body());
    TimeseriesReference reference = persistTrajectory(
      traj, container, containerShepardId, collectionShepardId, targetDataObjectShepardId, params
    );

    // 6. Record :UrScriptTrajectoryActivity — best-effort per the secondary-write rule.
    int warningCount = countList(outcome.body(), "warnings");
    String interpreterVersion = asString(outcome.body().get("interpreterVersion"));
    recordActivity(
      params, agentUsername, aiAgent, startedAt, reference.getAppId(),
      interpreterVersion, warningCount
    );

    return reference.getAppId();
  }

  // ── validation ────────────────────────────────────────────────────────────

  void validate(UrScriptTrajectoryParams params) {
    if (params == null) throw new BadRequestException("URScript trajectory params required");
    requireBlank("urscriptFileAppId", params.urscriptFileAppId());
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
      throw new SidecarException(UrScriptSidecarClient.SidecarOutcome.unreachable(
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
    UrScriptTrajectoryParams params,
    byte[] urscriptBytes,
    byte[] urdfBytes
  ) {
    Map<String, Object> body = new HashMap<>();
    body.put("urscriptFileAppId", params.urscriptFileAppId());
    body.put("urdfFileAppId", params.urdfFileAppId());
    body.put("urscriptContent", Base64.getEncoder().encodeToString(urscriptBytes));
    body.put("urdfContent", Base64.getEncoder().encodeToString(urdfBytes));
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
    UrScriptTrajectoryParams params
  ) {
    List<Timeseries> channels = new ArrayList<>();
    long start = traj.timestamps.isEmpty() ? System.currentTimeMillis() : traj.timestamps.get(0);
    long end = traj.timestamps.isEmpty()
      ? start
      : traj.timestamps.get(traj.timestamps.size() - 1);

    String measurement = "urscript_trajectory_" + params.urscriptFileAppId();
    String device = "urscript-interpreter";
    String location = "offline";
    String symbolicName = params.urscriptFileAppId();

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
        Log.warnf(ex, "URScript: failed to write trajectory channel joint_%d to container %s — "
          + "channel registered on reference but data points missing", i, container.getAppId());
      }
    }

    TimeseriesReferenceIO refIO = new TimeseriesReferenceIO();
    refIO.setName("URScript trajectory — " + params.urscriptFileAppId());
    refIO.setStart(start);
    refIO.setEnd(end);
    refIO.setTimeseries(channels);
    refIO.setTimeseriesContainerId(containerShepardId);

    return timeseriesReferenceService.createReference(collectionShepardId, dataObjectShepardId, refIO);
  }

  // ── response parsing ─────────────────────────────────────────────────────

  private static int countList(Map<String, Object> body, String key) {
    Object o = body.get(key);
    return o instanceof List<?> list ? list.size() : 0;
  }

  // ── activity ─────────────────────────────────────────────────────────────

  /**
   * Records the {@code :UrScriptTrajectoryActivity} row. Best-effort — never
   * propagates the exception (secondary write).
   */
  void recordActivity(
    UrScriptTrajectoryParams params,
    String agentUsername,
    String aiAgent,
    long startedAt,
    String trajectoryAppId,
    String interpreterVersion,
    int warningCount
  ) {
    try {
      String sourceMode = (aiAgent != null && !aiAgent.isBlank()) ? "ai" : "human";
      String summary = "materialize UrScriptTrajectoryShape urscript=" + params.urscriptFileAppId();
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
      overlayUrScriptActivity(recorded.getAppId(), params, interpreterVersion, warningCount);
    } catch (RuntimeException e) {
      Log.warnf(e, "URScript: provenance capture failed (best-effort)");
    }
  }

  /**
   * Adds the {@code :UrScriptTrajectoryActivity} label + URScript-specific properties
   * to the just-saved {@code :Activity}, and wires USED edges to the inputs.
   * All operations are idempotent and best-effort.
   */
  void overlayUrScriptActivity(
    String activityAppId,
    UrScriptTrajectoryParams params,
    String interpreterVersion,
    int warningCount
  ) {
    Session session = NeoConnector.getInstance().getNeo4jSession();
    if (session == null) return;

    Map<String, Object> p = new HashMap<>();
    p.put("appId", activityAppId);
    p.put("urscriptFileAppId", params.urscriptFileAppId());
    p.put("urdfFileAppId", params.urdfFileAppId());
    p.put("interpreterVersion", interpreterVersion);
    p.put("warningCount", warningCount);

    String label =
      "MATCH (a:Activity {appId: $appId}) SET a:UrScriptTrajectoryActivity, " +
      "a.urscriptFileAppId = $urscriptFileAppId, " +
      "a.urdfFileAppId = $urdfFileAppId, " +
      "a.interpreterVersion = $interpreterVersion, " +
      "a.warningCount = $warningCount";
    try {
      session.query(label, p);
    } catch (RuntimeException ex) {
      Log.debugf(ex, "URScript: activity label overlay failed for %s (non-fatal)", activityAppId);
    }

    wireUsedEdge(session, activityAppId, params.urscriptFileAppId());
    wireUsedEdge(session, activityAppId, params.urdfFileAppId());
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
      Log.debugf(ex, "URScript: USED edge wiring failed (%s -> %s)", activityAppId, targetAppId);
    }
  }

  // ── type coercion ────────────────────────────────────────────────────────

  private static String asString(Object o) { return o == null ? null : String.valueOf(o); }

  // ── envelope + records ───────────────────────────────────────────────────

  record TrajectoryEnvelope(List<Long> timestamps, List<List<Double>> joints) {}

  /**
   * Thrown when the sidecar call cannot be honoured — wraps the discriminated
   * {@link UrScriptSidecarClient.SidecarOutcome} so the executor can map it to a
   * {@link de.dlr.shepard.spi.transform.TransformException}.
   */
  public static class SidecarException extends RuntimeException {
    private final transient UrScriptSidecarClient.SidecarOutcome outcome;

    public SidecarException(UrScriptSidecarClient.SidecarOutcome outcome) {
      super("URScript sidecar call failed: status=" + outcome.status() + " detail=" + outcome.errorDetail());
      this.outcome = outcome;
    }

    public UrScriptSidecarClient.SidecarOutcome getOutcome() {
      return outcome;
    }
  }
}
