package de.dlr.shepard.common.healthz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Startup;

@Startup
@ApplicationScoped
public class PostGisStartupCheck extends AbstractDbStartupCheck {

  @Inject
  PostGisPinger pinger;

  @Override
  protected DbPinger pinger() {
    return pinger;
  }
}
