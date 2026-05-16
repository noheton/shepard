package de.dlr.shepard.common.healthz;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Periodically re-pings any DB whose {@link DbHealthState} has gone stale.
 *
 * <p>Each pinger runs in its own virtual thread so a single slow DB
 * (e.g. a timing-out PostGIS probe) cannot delay recovery of the others.
 * Total wall-clock time per recovery tick is {@code max(per-db latency)}
 * rather than {@code sum(per-db latency)}.
 *
 * <p>A CDI request context is activated per virtual thread so that
 * {@code TimescalePinger} and {@code PostGisPinger}, which inject JPA
 * {@code EntityManager} instances, resolve correctly off the scheduler thread.
 */
@ApplicationScoped
public class DbRecoveryScheduler {

  @Inject
  Instance<DbPinger> pingers;

  @Inject
  ReadinessConfig readinessConfig;

  @Inject
  RequestContextController requestContextController;

  @Scheduled(every = "{shepard.health.recovery.interval}")
  void attemptRecovery() {
    long maxStaleness = readinessConfig.maxStalenessMs();

    // Collect pingers that need a recovery probe before spawning threads.
    List<DbPinger> stale = new ArrayList<>();
    for (DbPinger pinger : pingers) {
      try {
        if (!pinger.isRequired()) {
          continue;
        }
        if (pinger.state().isFreshWithin(maxStaleness)) {
          continue;
        }
        stale.add(pinger);
      } catch (Exception e) {
        Log.warnf(e, "Recovery: could not determine staleness for %s", safeName(pinger));
      }
    }

    if (stale.isEmpty()) {
      return;
    }

    // Probe each stale DB in its own virtual thread — parallel recovery.
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (DbPinger pinger : stale) {
      CompletableFuture<Void> f = new CompletableFuture<>();
      futures.add(f);
      Thread.ofVirtual()
        .name("db-recovery-" + pinger.name())
        .start(() -> {
          try {
            recoverOne(pinger, maxStaleness);
          } finally {
            f.complete(null);
          }
        });
    }

    // Block the scheduler tick until all recovery probes complete so the
    // scheduler does not pile up overlapping ticks on slow DBs.
    try {
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    } catch (Exception e) {
      Log.warnf(e, "Recovery: unexpected error waiting for parallel probes");
    }
  }

  private void recoverOne(DbPinger pinger, long maxStaleness) {
    boolean ctxActivated = false;
    try {
      ctxActivated = requestContextController.activate();
    } catch (Exception e) {
      Log.debugf(e, "Recovery: could not activate request context for %s", pinger.name());
    }
    try {
      DbHealthState state = pinger.state();
      boolean wasUp = state.hasEverBeenUp();
      Log.infof("Recovery: re-pinging %s (ageMs=%d, maxStalenessMs=%d)", pinger.name(), state.ageMs(), maxStaleness);
      boolean ok = pinger.ping();
      if (ok) {
        if (wasUp) {
          Log.infof("Recovery: %s is back UP after staleness", pinger.name());
        } else {
          Log.infof("Recovery: %s is now UP for the first time", pinger.name());
        }
      } else {
        Log.warnf("Recovery: %s still DOWN after re-ping", pinger.name());
      }
    } catch (Exception e) {
      Log.warnf(e, "Recovery: pinger %s threw during recovery attempt", pinger.name());
    } finally {
      if (ctxActivated) {
        try {
          requestContextController.deactivate();
        } catch (Exception e) {
          Log.debugf(e, "Recovery: failed to deactivate request context for %s", pinger.name());
        }
      }
    }
  }

  private static String safeName(DbPinger pinger) {
    try {
      return pinger.name();
    } catch (Exception e) {
      return "<unknown>";
    }
  }
}
