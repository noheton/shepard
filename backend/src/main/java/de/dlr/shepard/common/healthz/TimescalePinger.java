package de.dlr.shepard.common.healthz;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;

@ApplicationScoped
public class TimescalePinger implements DbPinger {

  private final DbHealthState state = new DbHealthState();

  @Inject
  EntityManager entityManager;

  @Override
  public String name() {
    return "timescaledb";
  }

  @Override
  public DbHealthState state() {
    return state;
  }

  @Override
  public boolean ping() {
    long start = System.nanoTime();
    try {
      entityManager.createNativeQuery("SELECT 1").getSingleResult();
      long latency = (System.nanoTime() - start) / 1_000_000L;
      state.recordSuccess(latency);
      return true;
    } catch (Exception e) {
      long latency = (System.nanoTime() - start) / 1_000_000L;
      state.recordFailure(latency, e);
      Log.debugf("TimescaleDB ping failed: %s", e.toString());
      return false;
    }
  }
}
