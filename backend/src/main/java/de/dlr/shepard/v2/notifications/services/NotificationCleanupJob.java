package de.dlr.shepard.v2.notifications.services;

import de.dlr.shepard.v2.notifications.daos.NotificationDAO;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * NTF1a — nightly sweep that removes expired {@link de.dlr.shepard.v2.notifications.entities.Notification}
 * nodes from Neo4j.
 *
 * <p>Runs at 04:17 (server-local, offset from {@link de.dlr.shepard.provenance.services.ProvenanceRetentionJob}
 * at 03:42 to avoid concurrent Neo4j write storms). Only nodes with a non-null
 * {@code expiresAtMillis} in the past are deleted; permanent notifications
 * ({@code expiresAtMillis == null}) are left untouched.
 */
@ApplicationScoped
public class NotificationCleanupJob {

  @Inject
  NotificationDAO dao;

  @Scheduled(cron = "0 17 4 * * ?")
  public void runNightly() {
    long cutoff = System.currentTimeMillis();
    long removed;
    try {
      removed = dao.deleteExpiredBefore(cutoff);
    } catch (RuntimeException e) {
      Log.warnf(e, "NTF1a: notification cleanup failed");
      return;
    }
    if (removed > 0) {
      Log.infof("NTF1a: notification cleanup removed %d expired notification(s)", removed);
    } else {
      Log.debugf("NTF1a: notification cleanup — 0 expired notifications found");
    }
  }
}
