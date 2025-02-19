package de.dlr.shepard.common.configuration.feature.toggles;

public class LoadTestIngestionToggle {

  public static final String LOAD_TEST_INGESTION_PROPERTY = "shepard.load-test-data-ingestion.enabled";

  public static boolean isActive() {
    return TogglePropertyUtil.isToggleEnabled(LOAD_TEST_INGESTION_PROPERTY);
  }
}
