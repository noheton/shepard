package de.dlr.shepard.data.timeseries.migration.services;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.data.timeseries.migration.model.MigrationState;
import io.quarkus.test.InjectMock;
import io.quarkus.test.component.QuarkusComponentTest;
import io.quarkus.test.component.TestConfigProperty;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusComponentTest
public class TimeseriesMigrationInitServiceTest {

  @Inject
  TimeseriesMigrationInitService timeseriesMigrationInitService;

  @InjectMock
  TimeseriesMigrationService timeseriesMigrationService;

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "true")
  public void orchestrateMigrations_noMigrationNeeded_dontRunMigrations() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.NotNeeded);

    timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(0)).runMigrations();
  }

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "true")
  public void orchestrateMigrations_migrationNeeded_runMigrations() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.Needed);

    timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(1)).runMigrations();
  }

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "true")
  public void orchestrateMigrations_migrationErrors_runMigrations() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.HasErrors);

    timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(1)).runMigrations();
  }

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "false")
  public void orchestrateMigrations_migrationModeDisabledNoMigrationNeeded_dontRunMigrations() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.NotNeeded);

    timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(0)).runMigrations();
  }

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "false")
  public void orchestrateMigrations_migrationModeDisabledMigrationNeeded_dontRunMigrations() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.Needed);

    timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(0)).runMigrations();
  }

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "false")
  public void orchestrateMigrations_migrationModeDisabledMigrationErrors_dontRunMigrations() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.HasErrors);

    timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(0)).runMigrations();
  }
}
