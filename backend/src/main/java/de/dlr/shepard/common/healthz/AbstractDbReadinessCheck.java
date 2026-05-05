package de.dlr.shepard.common.healthz;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

abstract class AbstractDbReadinessCheck implements HealthCheck {

  protected abstract DbPinger pinger();

  protected abstract ReadinessConfig config();

  @Override
  public HealthCheckResponse call() {
    DbPinger p = pinger();
    HealthCheckResponseBuilder b = HealthCheckResponse.named(p.name() + "-readiness");
    if (!p.isRequired()) {
      return b.up().withData("required", false).build();
    }
    long maxStaleness = config().maxStalenessMs();
    boolean fresh = p.state().isFreshWithin(maxStaleness);
    if (!fresh) {
      p.ping();
      fresh = p.state().isFreshWithin(maxStaleness);
    }
    p.state().writeData(b);
    b.withData("required", true);
    b.withData("maxStalenessMs", maxStaleness);
    return b.status(fresh).build();
  }
}
