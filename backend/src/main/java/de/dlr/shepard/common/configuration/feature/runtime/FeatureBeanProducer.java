package de.dlr.shepard.common.configuration.feature.runtime;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class FeatureBeanProducer {

  @Produces
  @ApplicationScoped
  @ConditionalOnFeature("versioning")
  public VersioningFeature versioningFeature(
    @ConfigProperty(name = "shepard.features.versioning.enabled", defaultValue = "true") boolean enabled
  ) {
    return new VersioningFeature(enabled);
  }
}
