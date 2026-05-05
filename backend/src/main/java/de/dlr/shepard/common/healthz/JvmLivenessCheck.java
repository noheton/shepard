package de.dlr.shepard.common.healthz;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Liveness intentionally does not depend on any database — a transient DB
 * outage must not cause Kubernetes to restart the pod.
 */
@Liveness
@ApplicationScoped
public class JvmLivenessCheck implements HealthCheck {

  @Override
  public HealthCheckResponse call() {
    return HealthCheckResponse.up("jvm-liveness");
  }
}
