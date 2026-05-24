package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ac.simons.neo4j.migrations.core.MigrationState;
import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.ValidationResult;
import ac.simons.neo4j.migrations.core.ValidationResult.Outcome;
import de.dlr.shepard.common.healthz.MigrationChainInspector.ChainSnapshot;
import de.dlr.shepard.common.healthz.MigrationChainInspector.ChainSnapshot.PendingEntry;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MigrationChainReadinessCheck} — focused on
 * the CDI / readiness-response surface and the cache window. The
 * chain-comparison logic is covered by
 * {@link MigrationChainInspectorTest}.
 */
public class MigrationChainReadinessCheckTest {

  @Test
  public void call_returnsUpWhenChainIsHealthy() {
    ChainSnapshot snapshot = new ChainSnapshot(List.of(
      new PendingEntry("60", MigrationState.APPLIED),
      new PendingEntry("61", MigrationState.APPLIED)
    ));
    Migrations m = stubMigrations(validationResult(Outcome.VALID, List.of()));
    MigrationChainReadinessCheck check = checkWith(m, 30_000L, snapshot);

    HealthCheckResponse resp = check.call();

    assertEquals(MigrationChainReadinessCheck.CHECK_NAME, resp.getName());
    assertEquals(HealthCheckResponse.Status.UP, resp.getStatus());
    Map<String, Object> data = resp.getData().orElseThrow();
    assertEquals("VALID", data.get("outcome"));
    assertEquals(30_000L, data.get("maxStalenessMs"));
  }

  @Test
  public void call_returnsDownWithPendingVersionsAndErrorMessage() {
    ChainSnapshot snapshot = new ChainSnapshot(List.of(
      new PendingEntry("60", MigrationState.APPLIED),
      new PendingEntry("61", MigrationState.PENDING)
    ));
    Migrations m = stubMigrations(validationResult(
      Outcome.INCOMPLETE_MIGRATIONS,
      List.of("V61 not applied")
    ));
    MigrationChainReadinessCheck check = checkWith(m, 30_000L, snapshot);

    HealthCheckResponse resp = check.call();

    assertEquals(HealthCheckResponse.Status.DOWN, resp.getStatus());
    Map<String, Object> data = resp.getData().orElseThrow();
    assertEquals("INCOMPLETE_MIGRATIONS", data.get("outcome"));
    assertEquals("61", data.get("pendingVersions"));
    String warnings = (String) data.get("warnings");
    assertNotNull(warnings);
    assertTrue(warnings.contains("V61 not applied"));
    String errorMessage = (String) data.get("errorMessage");
    assertNotNull(errorMessage);
    assertTrue(errorMessage.contains("61"));
    assertTrue(errorMessage.contains("runbooks/migration-chain-integrity.md"));
  }

  @Test
  public void call_returnsDownWithCheckFailedWhenMigrationsThrows() {
    Migrations m = mock(Migrations.class);
    when(m.validate()).thenThrow(new IllegalStateException("conn refused"));
    MigrationChainReadinessCheck check = checkWith(m, 30_000L, new ChainSnapshot(List.of()));

    HealthCheckResponse resp = check.call();

    assertEquals(HealthCheckResponse.Status.DOWN, resp.getStatus());
    Map<String, Object> data = resp.getData().orElseThrow();
    assertEquals("CHECK_FAILED", data.get("outcome"));
    assertTrue(((String) data.get("errorMessage")).contains("conn refused"));
  }

  @Test
  public void call_returnsDownWhenMigrationsInstanceIsNull() {
    // The PostConstruct init() catches Exceptions and leaves
    // `migrations` null on driver-creation failure. call() must still
    // return a sensible DOWN, not NPE.
    MigrationChainReadinessCheck check = checkWith(null, 30_000L, new ChainSnapshot(List.of()));

    HealthCheckResponse resp = check.call();

    assertEquals(HealthCheckResponse.Status.DOWN, resp.getStatus());
    assertEquals("CHECK_FAILED", resp.getData().orElseThrow().get("outcome"));
  }

  @Test
  public void call_cachesWithinStalenessWindow() {
    ChainSnapshot snapshot = new ChainSnapshot(List.of(
      new PendingEntry("60", MigrationState.APPLIED)
    ));
    Migrations m = stubMigrations(validationResult(Outcome.VALID, List.of()));
    MigrationChainReadinessCheck check = checkWith(m, 60_000L, snapshot);

    check.call();
    check.call();
    check.call();

    // Three successive calls within 60s should hit the cache after
    // the first — validate() should not be invoked three times.
    verify(m, times(1)).validate();
  }

  @Test
  public void call_reEvaluatesWhenStalenessIsExceeded() {
    // maxStaleness=-1 means every call's "(now - checkedAt) > -1" is
    // true, so the cache never satisfies and every probe re-runs.
    // Easier to assert than wall-clock sleeps.
    ChainSnapshot snapshot = new ChainSnapshot(List.of(
      new PendingEntry("60", MigrationState.APPLIED)
    ));
    Migrations m = stubMigrations(validationResult(Outcome.VALID, List.of()));
    MigrationChainReadinessCheck check = checkWith(m, -1L, snapshot);

    check.call();
    check.call();
    check.call();

    verify(m, atLeastOnce()).validate();
    verify(m, times(3)).validate();
  }

  // ------------------------------------------------------------------ helpers

  private static MigrationChainReadinessCheck checkWith(
    Migrations migrations,
    long maxStalenessMs,
    ChainSnapshot snapshot
  ) {
    ReadinessConfig cfg = mock(ReadinessConfig.class);
    when(cfg.maxStalenessMs()).thenReturn(maxStalenessMs);
    Function<Migrations, ChainSnapshot> fn = mm -> snapshot;
    MigrationChainInspector inspector = new MigrationChainInspector(System::currentTimeMillis, fn);
    return MigrationChainReadinessCheck.forTest(migrations, cfg, inspector);
  }

  private static Migrations stubMigrations(ValidationResult v) {
    Migrations m = mock(Migrations.class);
    when(m.validate()).thenReturn(v);
    return m;
  }

  private static ValidationResult validationResult(Outcome outcome, List<String> warnings) {
    ValidationResult v = mock(ValidationResult.class);
    when(v.isValid()).thenReturn(outcome == Outcome.VALID);
    when(v.getOutcome()).thenReturn(outcome);
    when(v.getWarnings()).thenReturn(warnings);
    return v;
  }
}
