package de.dlr.shepard.healthz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class TimescaleDBHealthCheck implements HealthCheck {

  @Inject
  EntityManager entityManager;

  @Override
  public HealthCheckResponse call() {
    try {
      entityManager.createNativeQuery("SELECT 1").getSingleResult();
      return HealthCheckResponse.up("TimescaleDB connection health check");
    } catch (Exception e) {
      return HealthCheckResponse.down("TimescaleDB connection health check");
    }
  }
}
