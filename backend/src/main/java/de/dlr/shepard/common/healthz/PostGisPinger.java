package de.dlr.shepard.common.healthz;

import de.dlr.shepard.common.configuration.feature.toggles.SpatialDataFeatureToggle;
import io.quarkus.hibernate.orm.PersistenceUnit;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class PostGisPinger implements DbPinger {

  private final DbHealthState state = new DbHealthState();

  @Inject
  @PersistenceUnit("spatial")
  Instance<EntityManager> spatialEntityManager;

  @Override
  public String name() {
    return "postgis";
  }

  @Override
  public DbHealthState state() {
    return state;
  }

  @Override
  public boolean isRequired() {
    return SpatialDataFeatureToggle.isActive();
  }

  @Override
  public boolean ping() {
    if (!isRequired()) {
      state.recordSuccess(0L);
      return true;
    }
    long start = System.nanoTime();
    try {
      if (!spatialEntityManager.isResolvable()) {
        long latency = (System.nanoTime() - start) / 1_000_000L;
        state.recordFailure(latency, new IllegalStateException("spatial persistence unit not resolvable"));
        return false;
      }
      spatialEntityManager.get().createNativeQuery("SELECT 1").getSingleResult();
      long latency = (System.nanoTime() - start) / 1_000_000L;
      state.recordSuccess(latency);
      return true;
    } catch (Exception e) {
      long latency = (System.nanoTime() - start) / 1_000_000L;
      state.recordFailure(latency, e);
      Log.debugf("PostGIS ping failed: %s", e.toString());
      return false;
    }
  }
}
