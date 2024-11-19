package de.dlr.shepard.healthz;

import de.dlr.shepard.neo4j.NeoConnector;
import de.dlr.shepard.util.IConnector;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class NeoHealthCheck implements HealthCheck {

  private static IConnector neo4j = NeoConnector.getInstance();

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.named("Neo4J connection health check").status(neo4j.alive()).build();
  }
}
