package de.dlr.shepard.timeseries.migration.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import de.dlr.shepard.timeseries.migration.model.MigrationState;
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
  public void orchestrateMigrations_MigrationModeEnabledNoMigrationNeeded_terminate() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.NotNeeded);

    boolean actual = timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(0)).runMigrations();
    assertEquals(true, actual);
  }

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "true")
  public void orchestrateMigrations_MigrationModeEnabledMigrationNeeded_dontTerminate() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.Needed);

    boolean actual = timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(1)).runMigrations();
    assertEquals(false, actual);
  }

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "true")
  public void orchestrateMigrations_MigrationModeEnabledMigrationErrors_dontTerminate() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.HasErrors);

    boolean actual = timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(0)).runMigrations();
    assertEquals(false, actual);
  }

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "false")
  public void orchestrateMigrations_MigrationModeDisabledNoMigrationNeeded_dontTerminate() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.NotNeeded);

    boolean actual = timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(0)).runMigrations();
    assertEquals(false, actual);
  }

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "false")
  public void orchestrateMigrations_MigrationModeDisabledMigrationNeeded_terminate() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.Needed);

    boolean actual = timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(0)).runMigrations();
    assertEquals(true, actual);
  }

  @Test
  @TestConfigProperty(key = "shepard.migration-mode.enabled", value = "false")
  public void orchestrateMigrations_MigrationModeDisabledMigrationErrors_terminate() {
    when(timeseriesMigrationService.getMigrationState()).thenReturn(MigrationState.HasErrors);

    boolean actual = timeseriesMigrationInitService.orchestrateMigrations();

    verify(timeseriesMigrationService, times(0)).runMigrations();
    assertEquals(true, actual);
  }
}
