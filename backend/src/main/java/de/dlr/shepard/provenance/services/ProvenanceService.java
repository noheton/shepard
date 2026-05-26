package de.dlr.shepard.provenance.services;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
import de.dlr.shepard.provenance.entities.ActivityActionKind;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Front door for provenance capture. The capture filter and any
 * service-layer hook (sTC ingest, migrations, future coordinator
 * steps per {@code aidocs/50}) call {@link #record} to land an
 * {@link Activity} row.
 *
 * <p>Designed in {@code aidocs/55 §4}. Writes are best-effort:
 * a Neo4j hiccup on the activity write does not fail the underlying
 * request — provenance is observability, not contract.
 */
@RequestScoped
public class ProvenanceService {

  private static final int MAX_SUMMARY_CHARS = 256;
  private static final int MAX_PATH_CHARS = 1024;

  @Inject
  ActivityDAO activityDAO;

  /**
   * Stamps the HMAC audit-chain fields immediately before persistence
   * (PR-3 of the SHACL changeover). Best-effort — see
   * {@link HmacChainService#stamp} for the never-block-the-write
   * contract.
   */
  @Inject
  HmacChainService hmacChainService;

  @ConfigProperty(name = "shepard.provenance.enabled", defaultValue = "true")
  boolean enabled;

  @ConfigProperty(name = "shepard.instance.id", defaultValue = "local")
  String originInstance;

  /**
   * Persist one activity row. Returns the saved entity on success or
   * {@code null} when capture is disabled or the write failed
   * (failure logged at {@code debug}).
   *
   * @param actionKind     CREATE / READ / UPDATE / DELETE / EXECUTE
   * @param targetKind     OGM label of the target entity (e.g.
   *                       {@code "Collection"}) — may be {@code null}
   * @param targetAppId    target entity's appId — may be {@code null}
   * @param agentUsername  acting Agent's username (always set in
   *                       authenticated contexts)
   * @param summary        short human-readable summary; truncated at
   *                       {@code MAX_SUMMARY_CHARS}
   * @param method         HTTP method (or pseudo-verb for non-REST
   *                       capture sites)
   * @param path           request path — truncated at
   *                       {@code MAX_PATH_CHARS}
   * @param status         resulting HTTP status (or pseudo-status for
   *                       non-REST capture sites)
   * @param startedAtMillis millis since epoch the activity began
   * @param endedAtMillis   millis since epoch the activity ended
   */
  public Activity record(
    String actionKind,
    String targetKind,
    String targetAppId,
    String agentUsername,
    String summary,
    String method,
    String path,
    Integer status,
    long startedAtMillis,
    long endedAtMillis
  ) {
    return record(actionKind, targetKind, targetAppId, agentUsername, summary, method, path, status,
      startedAtMillis, endedAtMillis, null);
  }

  /**
   * Extended overload that also stamps a {@code mirroredUserAppId} — the
   * {@code appId} of the {@code :MirroredUser} node representing the
   * source-side actor forwarded via {@code X-Source-User-*} headers.
   * {@code null} is safe; behaves identically to the 10-param variant.
   *
   * <p>Only the {@link de.dlr.shepard.provenance.filters.ProvenanceCaptureFilter}
   * currently passes a non-null value (PROV-USER-ENRICH). All other callers
   * use the 10-param overload and receive {@code null} transparently.
   *
   * @param mirroredUserAppId appId of the resolved {@code :MirroredUser} node,
   *                          or {@code null} when the request did not carry
   *                          {@code X-Source-User-*} headers
   */
  public Activity record(
    String actionKind,
    String targetKind,
    String targetAppId,
    String agentUsername,
    String summary,
    String method,
    String path,
    Integer status,
    long startedAtMillis,
    long endedAtMillis,
    String mirroredUserAppId
  ) {
    return record(actionKind, targetKind, targetAppId, agentUsername, summary, method, path, status,
      startedAtMillis, endedAtMillis, mirroredUserAppId, null, null);
  }

  /**
   * PROV1j (activity-layer) — extended overload that additionally stamps
   * {@code sourceMode} ({@code "human"} or {@code "ai"}) and {@code agentId}
   * (the value of the {@code X-AI-Agent} request header) on the
   * {@code :Activity} node.
   *
   * <p>Closes the EU AI Act Art. 50 per-artefact AI-visibility requirement
   * at the audit-log layer. Pre-PROV1j activities that lack these properties
   * are treated as {@code "human"} by consumers.
   *
   * <p>All other overloads delegate here with {@code sourceMode=null},
   * {@code agentId=null}; callers that pass {@code null} for both get
   * identical behaviour to the 11-param overload.
   *
   * @param sourceMode {@code "human"} or {@code "ai"} — {@code null} is
   *                   treated as absent (pre-PROV1j rows)
   * @param agentId    value of the {@code X-AI-Agent} header, or {@code null}
   */
  public Activity record(
    String actionKind,
    String targetKind,
    String targetAppId,
    String agentUsername,
    String summary,
    String method,
    String path,
    Integer status,
    long startedAtMillis,
    long endedAtMillis,
    String mirroredUserAppId,
    String sourceMode,
    String agentId
  ) {
    if (!enabled) return null;
    // NEO-AUDIT-015: app-layer validation — Community Edition cannot enforce
    // value constraints in the DB; reject unknown actionKind early.
    ActivityActionKind.validate(actionKind);
    try {
      Activity a = new Activity();
      a.setActionKind(actionKind);
      a.setTargetKind(targetKind);
      a.setTargetAppId(targetAppId);
      a.setAgentUsername(agentUsername);
      a.setSummary(truncate(summary, MAX_SUMMARY_CHARS));
      a.setMethod(method);
      a.setPath(truncate(path, MAX_PATH_CHARS));
      a.setStatus(status);
      a.setStartedAtMillis(startedAtMillis);
      a.setEndedAtMillis(endedAtMillis);
      a.setOriginInstance(originInstance);
      a.setMirroredUserAppId(mirroredUserAppId);
      a.setSourceMode(sourceMode);
      a.setAgentId(agentId);
      // PR-3 audit-chain stamp — best-effort, never blocks the write.
      hmacChainService.stamp(a);
      Activity saved = activityDAO.createOrUpdate(a);
      // NEO-AUDIT-001: wire PROV-O edges (WAS_ASSOCIATED_WITH / GENERATED / USED)
      // best-effort — failures are already swallowed inside wireEdges, but any
      // RuntimeException propagating here is also caught by the outer try/catch.
      activityDAO.wireEdges(saved, agentUsername, targetAppId, actionKind);
      return saved;
    } catch (RuntimeException e) {
      // Provenance is observability; never block the request on it.
      Log.debugf(e, "Provenance capture failed for %s %s -> %s", method, path, status);
      return null;
    }
  }

  /**
   * Read-side query helper. Casual wrapper over
   * {@link ActivityDAO#list} so callers don't import the DAO directly.
   */
  public List<Activity> list(
    String agentUsername,
    String targetKind,
    String targetAppId,
    Long sinceMillis,
    Long untilMillis,
    int limit
  ) {
    if (!enabled) return List.of();
    return activityDAO.list(agentUsername, targetKind, targetAppId, sinceMillis, untilMillis, limit);
  }

  public long count(String agentUsername, String targetKind, String targetAppId, Long sinceMillis, Long untilMillis) {
    if (!enabled) return 0L;
    return activityDAO.count(agentUsername, targetKind, targetAppId, sinceMillis, untilMillis);
  }

  /** {@code true} when provenance writes are enabled by config. */
  public boolean isEnabled() {
    return enabled;
  }

  private static String truncate(String s, int max) {
    if (s == null) return null;
    return s.length() <= max ? s : s.substring(0, max);
  }
}
