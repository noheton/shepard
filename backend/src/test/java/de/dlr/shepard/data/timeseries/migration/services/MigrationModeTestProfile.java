package de.dlr.shepard.data.timeseries.migration.services;

import io.quarkus.test.junit.QuarkusTestProfile;
import java.util.Collections;
import java.util.Map;

public class MigrationModeTestProfile implements QuarkusTestProfile {

  @Override
  public Map<String, String> getConfigOverrides() {
    return Collections.singletonMap("shepard.migration-mode.enabled", "true");
  }
}
