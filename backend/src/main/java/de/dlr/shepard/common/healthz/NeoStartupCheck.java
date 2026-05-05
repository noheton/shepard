package de.dlr.shepard.common.healthz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Startup;

@Startup
@ApplicationScoped
public class NeoStartupCheck extends AbstractDbStartupCheck {

  @Inject
  NeoPinger pinger;

  @Override
  protected DbPinger pinger() {
    return pinger;
  }
}
