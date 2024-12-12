package de.dlr.shepard.configuration.feature.toggles;

public class MigrationModeToggle {

  private static final String MIGRATION_MODE_PROPERTY = "shepard.migration-mode.enabled";

  public static boolean isActive() {
    return TogglePropertyUtil.isToggleEnabled(MIGRATION_MODE_PROPERTY);
  }
}
