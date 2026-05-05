package de.dlr.shepard.common.healthz;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.Startup;

@Startup
@ApplicationScoped
public class MongoStartupCheck extends AbstractDbStartupCheck {

  @Inject
  MongoPinger pinger;

  @Override
  protected DbPinger pinger() {
    return pinger;
  }
}
