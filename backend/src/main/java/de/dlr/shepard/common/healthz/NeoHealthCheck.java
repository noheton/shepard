package de.dlr.shepard.common.healthz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class NeoHealthCheck extends AbstractDbReadinessCheck {

  @Inject
  NeoPinger pinger;

  @Inject
  ReadinessConfig config;

  @Override
  protected DbPinger pinger() {
    return pinger;
  }

  @Override
  protected ReadinessConfig config() {
    return config;
  }
}
