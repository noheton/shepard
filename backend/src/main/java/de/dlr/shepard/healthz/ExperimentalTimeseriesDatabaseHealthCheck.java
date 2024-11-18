package de.dlr.shepard.healthz;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@IfBuildProperty(name = "shepard.experimental-timeseries.enabled", stringValue = "true")
@ApplicationScoped
public class ExperimentalTimeseriesDatabaseHealthCheck implements HealthCheck {

  @Inject
  EntityManager entityManager;

  @Override
  public HealthCheckResponse call() {
    try {
      entityManager.createNativeQuery("SELECT 1").getSingleResult();
      return HealthCheckResponse.up("TimescaleDB connection health check");
    } catch (PersistenceException e) {
      return HealthCheckResponse.down("TimescaleDB connection health check");
    }
  }
}
