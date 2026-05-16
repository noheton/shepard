package de.dlr.shepard.common.healthz;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.RequestContextController;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fires an initial connectivity probe to every registered {@link DbPinger} in
 * parallel after the CDI container has started.
 *
 * <p><strong>Ordering guarantee:</strong> Quarkus fires {@code StartupEvent} to
 * CDI {@code @ApplicationScoped} observers strictly <em>after</em> the
 * {@code @Startup} lifecycle hook in {@code ShepardMain.init()} completes.
 * That means Neo4j migrations have already run before this warmer executes —
 * safe to probe all DBs concurrently without racing the migration sequence.
 *
 * <p><strong>Parallelism:</strong> each pinger runs in its own Java 21 virtual
 * thread so total startup wait is bounded by {@code max(per-db latency)} rather
 * than {@code sum(per-db latency)}.  Each individual probe is bounded by the
 * DB driver's own socket/connect timeout (configured at the data-source level,
 * e.g. {@code %prod.quarkus.datasource.jdbc.connect-timeout}).  This warmer
 * does not impose an additional application-level deadline on top of the
 * driver's own; doing so correctly would require interrupting virtual threads,
 * which {@link CompletableFuture#orTimeout} cannot do.
 *
 * <p><strong>Fail-soft:</strong> a pinger that times out or throws only marks
 * its own {@link DbHealthState} DOWN — it does not abort startup.  The existing
 * {@code @Startup} {@link org.eclipse.microprofile.health.HealthCheck} beans
 * remain the authoritative readiness signal for the orchestrator.
 *
 * <p><strong>Request-context:</strong> {@code TimescalePinger} and
 * {@code PostGisPinger} inject JPA {@code EntityManager} instances that may
 * require an active CDI request context.  We activate one per virtual thread
 * via {@link RequestContextController} (the same pattern used by
 * {@code PermissionsCacheWarmer}).
 */
@ApplicationScoped
public class DbConnectivityWarmer {

  @Inject
  Instance<DbPinger> pingers;

  @Inject
  RequestContextController requestContextController;

  /** Tracks the combined future so tests can await completion. */
  private volatile CompletableFuture<Void> warmingFuture = CompletableFuture.completedFuture(null);

  /**
   * Observed by the CDI container after {@code ShepardMain.init()} completes.
   */
  void onStart(@Observes StartupEvent event) {
    List<DbPinger> required = new ArrayList<>();
    for (DbPinger p : pingers) {
      if (p.isRequired()) {
        required.add(p);
      }
    }

    if (required.isEmpty()) {
      Log.debug("DbConnectivityWarmer: no required pingers — nothing to pre-warm");
      return;
    }

    Log.infof(
      "DbConnectivityWarmer: pre-warming %d DB connections in parallel",
      required.size()
    );

    // Launch one virtual thread per DB — total wall-clock wait is max(latencies)
    // not sum(latencies).  Each thread activates its own CDI request context so
    // JPA EntityManagers in TimescalePinger / PostGisPinger resolve correctly.
    List<CompletableFuture<Void>> futures = new ArrayList<>();
    for (DbPinger pinger : required) {
      CompletableFuture<Void> f = new CompletableFuture<>();
      futures.add(f);
      Thread.ofVirtual()
        .name("db-warmer-" + pinger.name())
        .start(() -> {
          try {
            warmOne(pinger);
          } finally {
            f.complete(null);
          }
        });
    }

    CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    warmingFuture = all;

    // The futures are fire-and-forget: we do NOT block the startup thread.
    // The orchestrator's readiness probe will observe the individual DbHealthState
    // outcomes via the @Startup HealthCheck beans.
  }

  /** Package-visible for tests. */
  CompletableFuture<Void> warmingFuture() {
    return warmingFuture;
  }

  private void warmOne(DbPinger pinger) {
    boolean ctxActivated = false;
    try {
      ctxActivated = requestContextController.activate();
    } catch (Exception e) {
      Log.debugf(e, "DbConnectivityWarmer: could not activate request context for %s", pinger.name());
    }
    try {
      Log.debugf("DbConnectivityWarmer: probing %s", pinger.name());
      // Per-probe timeout is enforced by the DB driver's own socket/connect
      // timeout (e.g. quarkus.datasource.jdbc.connect-timeout).  Imposing an
      // additional application-level deadline would require interrupting virtual
      // threads, which CompletableFuture.orTimeout cannot do.
      boolean ok = pinger.ping();
      if (ok) {
        Log.infof("DbConnectivityWarmer: %s is UP", pinger.name());
      } else {
        Log.warnf("DbConnectivityWarmer: %s is DOWN after initial probe", pinger.name());
      }
    } catch (Exception e) {
      Log.warnf(e, "DbConnectivityWarmer: probe threw for %s", pinger.name());
    } finally {
      if (ctxActivated) {
        try {
          requestContextController.deactivate();
        } catch (Exception e) {
          Log.debugf(e, "DbConnectivityWarmer: failed to deactivate request context for %s", pinger.name());
        }
      }
    }
  }
}
