package de.dlr.shepard.common.healthz;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

abstract class AbstractDbStartupCheck implements HealthCheck {

  protected abstract DbPinger pinger();

  @Override
  public HealthCheckResponse call() {
    DbPinger p = pinger();
    HealthCheckResponseBuilder b = HealthCheckResponse.named(p.name() + "-startup");
    if (!p.isRequired()) {
      return b.up().withData("required", false).build();
    }
    boolean ok = p.state().hasEverBeenUp() || p.ping();
    p.state().writeData(b);
    b.withData("required", true);
    return b.status(ok).build();
  }
}
