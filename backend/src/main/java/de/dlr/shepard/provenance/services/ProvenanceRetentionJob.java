package de.dlr.shepard.provenance.services;

import de.dlr.shepard.provenance.daos.ActivityDAO;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Nightly retention job for {@link de.dlr.shepard.provenance.entities.Activity}
 * rows. Designed in {@code aidocs/55 §7}.
 *
 * <p>Runs once a day at 03:42 (server-local). Deletes activities with
 * {@code startedAtMillis < (now - retentionDays·day)}. Skipped when
 * {@code shepard.provenance.enabled=false} or when
 * {@code shepard.provenance.retention-days < 0} (the latter is the
 * "keep forever" escape hatch).
 *
 * <p>The job logs only the row count, not the individual deletions —
 * a 2-year retention on a typical install removes ~2-5k rows per
 * day, well within an INFO-level summary's noise budget.
 */
@ApplicationScoped
public class ProvenanceRetentionJob {

  static final long MILLIS_PER_DAY = 24L * 60 * 60 * 1000;

  @Inject
  ActivityDAO activityDAO;

  @ConfigProperty(name = "shepard.provenance.enabled", defaultValue = "true")
  boolean provenanceEnabled;

  @ConfigProperty(name = "shepard.provenance.retention-days", defaultValue = "730")
  long retentionDays;

  /**
   * Nightly run at 03:42. The cron-string is fixed; if an operator
   * wants a different window, the right control is the
   * {@code retention-days} config knob (zero / negative disables).
   */
  @Scheduled(cron = "0 42 3 * * ?")
  public void runNightly() {
    if (!provenanceEnabled) {
      Log.debug("Provenance retention skipped — shepard.provenance.enabled=false");
      return;
    }
    if (retentionDays < 0) {
      Log.debug("Provenance retention skipped — shepard.provenance.retention-days < 0 (keep forever)");
      return;
    }
    long cutoff = System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY;
    long removed;
    try {
      removed = activityDAO.deleteOlderThan(cutoff);
    } catch (RuntimeException e) {
      Log.warnf(e, "Provenance retention job failed (cutoff=%d)", cutoff);
      return;
    }
    if (removed > 0) {
      Log.infof("Provenance retention: removed %d activity row(s) older than %d days", removed, retentionDays);
    } else {
      Log.debugf("Provenance retention: 0 rows older than %d days", retentionDays);
    }
  }

  /**
   * Compute the cutoff timestamp without running the deletion. Exposed
   * for tests + the future {@code POST /v2/admin/provenance/retention/run}
   * endpoint.
   */
  public long currentCutoffMillis() {
    return System.currentTimeMillis() - retentionDays * MILLIS_PER_DAY;
  }
}
