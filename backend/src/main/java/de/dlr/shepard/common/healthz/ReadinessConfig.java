package de.dlr.shepard.common.healthz;

import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ReadinessConfig {

  @ConfigProperty(name = "shepard.health.readiness.max-staleness", defaultValue = "PT30S")
  Duration maxStaleness;

  public long maxStalenessMs() {
    return maxStaleness.toMillis();
  }
}
