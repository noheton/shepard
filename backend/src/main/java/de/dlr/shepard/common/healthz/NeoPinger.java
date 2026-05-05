package de.dlr.shepard.common.healthz;

import de.dlr.shepard.common.neo4j.NeoConnector;
import de.dlr.shepard.common.util.IConnector;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class NeoPinger implements DbPinger {

  private final DbHealthState state = new DbHealthState();

  IConnector connector() {
    return NeoConnector.getInstance();
  }

  @Override
  public String name() {
    return "neo4j";
  }

  @Override
  public DbHealthState state() {
    return state;
  }

  @Override
  public boolean ping() {
    long start = System.nanoTime();
    try {
      boolean alive = connector().alive();
      long latency = (System.nanoTime() - start) / 1_000_000L;
      if (alive) {
        state.recordSuccess(latency);
        return true;
      }
      state.recordFailure(latency, new RuntimeException("Neo4J reported not alive"));
      return false;
    } catch (Exception e) {
      long latency = (System.nanoTime() - start) / 1_000_000L;
      state.recordFailure(latency, e);
      Log.debugf("Neo4J ping failed: %s", e.toString());
      return false;
    }
  }
}
