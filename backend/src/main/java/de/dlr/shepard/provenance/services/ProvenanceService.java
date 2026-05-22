package de.dlr.shepard.provenance.services;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import de.dlr.shepard.provenance.entities.Activity;
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
    if (!enabled) return null;
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
      // PR-3 audit-chain stamp — best-effort, never blocks the write.
      hmacChainService.stamp(a);
      return activityDAO.createOrUpdate(a);
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
