package de.dlr.shepard.common.healthz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Startup;

@Startup
@ApplicationScoped
public class TimescaleStartupCheck extends AbstractDbStartupCheck {

  @Inject
  TimescalePinger pinger;

  @Override
  protected DbPinger pinger() {
    return pinger;
  }
}
