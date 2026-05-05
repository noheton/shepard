package de.dlr.shepard.common.healthz;

import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class DbRecoveryScheduler {

  @Inject
  Instance<DbPinger> pingers;

  @Inject
  ReadinessConfig readinessConfig;

  @Scheduled(every = "{shepard.health.recovery.interval}")
  void attemptRecovery() {
    long maxStaleness = readinessConfig.maxStalenessMs();
    for (DbPinger pinger : pingers) {
      try {
        if (!pinger.isRequired()) {
          continue;
        }
        DbHealthState state = pinger.state();
        if (state.isFreshWithin(maxStaleness)) {
          continue;
        }
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
        Log.warnf(e, "Recovery: pinger %s threw during recovery attempt", safeName(pinger));
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
