package de.dlr.shepard.common.healthz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ac.simons.neo4j.migrations.core.MigrationState;
import ac.simons.neo4j.migrations.core.Migrations;
import ac.simons.neo4j.migrations.core.ValidationResult;
import ac.simons.neo4j.migrations.core.ValidationResult.Outcome;
import de.dlr.shepard.common.healthz.MigrationChainInspector.ChainSnapshot;
import de.dlr.shepard.common.healthz.MigrationChainInspector.ChainSnapshot.PendingEntry;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MigrationChainInspector}. The inspector
 * receives a snapshot via an extractor {@code Function} so tests
 * never need to construct the library's sealed {@link
 * ac.simons.neo4j.migrations.core.MigrationChain}; mocked
 * {@link Migrations} drives only {@code validate()}.
 */
public class MigrationChainInspectorTest {

  private static final long FIXED_NOW = 1_700_000_000_000L;

  @Test
  public void inspect_returnsHealthyWhenValidAndNoPending() {
    ChainSnapshot snapshot = new ChainSnapshot(List.of(
      new PendingEntry("60", MigrationState.APPLIED),
      new PendingEntry("61", MigrationState.APPLIED),
      new PendingEntry("63", MigrationState.APPLIED)
    ));
    Migrations m = stubMigrations(validationResult(Outcome.VALID, List.of()));
    MigrationChainInspector inspector = inspectorWith(snapshot);

    MigrationChainStatus s = inspector.inspect(m);

    assertTrue(s.healthy(), () -> "expected healthy, got: " + s);
    assertEquals("VALID", s.outcome());
    assertTrue(s.pendingVersions().isEmpty());
    assertTrue(s.warnings().isEmpty());
    assertNull(s.errorMessage());
    assertEquals(FIXED_NOW, s.checkedAtEpochMs());
  }

  @Test
  public void inspect_returnsUnhealthyWhenValidationReportsDifferentContent() {
    ChainSnapshot snapshot = new ChainSnapshot(List.of(
      new PendingEntry("60", MigrationState.APPLIED),
      new PendingEntry("61", MigrationState.PENDING),
      new PendingEntry("63", MigrationState.APPLIED)
    ));
    Migrations m = stubMigrations(validationResult(
      Outcome.DIFFERENT_CONTENT,
      List.of("Checksum of V61 does not match local file")
    ));
    MigrationChainInspector inspector = inspectorWith(snapshot);

    MigrationChainStatus s = inspector.inspect(m);

    assertFalse(s.healthy());
    assertEquals("DIFFERENT_CONTENT", s.outcome());
    assertEquals(List.of("61"), s.pendingVersions());
    assertEquals(1, s.warnings().size());
    assertNotNull(s.errorMessage());
    assertTrue(
      s.errorMessage().contains("61"),
      () -> "expected error message to name the pending version, got: " + s.errorMessage()
    );
    assertTrue(
      s.errorMessage().contains("docs/admin/runbooks/migration-chain-integrity.md"),
      () -> "expected error message to point at the runbook, got: " + s.errorMessage()
    );
  }

  @Test
  public void inspect_returnsUnhealthyWhenChainHasPendingEvenIfValidationSaysValid() {
    // Defensive: if the library ever reports VALID with PENDING elements
    // (e.g. brand-new database before first apply), still flip DOWN —
    // the readiness signal means "ready to serve", and a pending
    // migration on a serving instance is by definition a defect.
    ChainSnapshot snapshot = new ChainSnapshot(List.of(
      new PendingEntry("64", MigrationState.PENDING)
    ));
    Migrations m = stubMigrations(validationResult(Outcome.VALID, List.of()));
    MigrationChainInspector inspector = inspectorWith(snapshot);

    MigrationChainStatus s = inspector.inspect(m);

    assertFalse(s.healthy(), "pending migrations must fail readiness even when validation reports VALID");
    assertEquals(List.of("64"), s.pendingVersions());
  }

  @Test
  public void inspect_capturesExceptionsAsCheckFailed() {
    Migrations m = mock(Migrations.class);
    when(m.validate()).thenThrow(new IllegalStateException("driver gone"));
    MigrationChainInspector inspector = inspectorWith(new ChainSnapshot(List.of()));

    MigrationChainStatus s = inspector.inspect(m);

    assertFalse(s.healthy());
    assertEquals("CHECK_FAILED", s.outcome());
    assertTrue(s.errorMessage().contains("driver gone"));
    assertTrue(s.errorMessage().contains("IllegalStateException"));
  }

  @Test
  public void inspect_nullMigrationsIsCheckFailed() {
    MigrationChainInspector inspector = inspectorWith(new ChainSnapshot(List.of()));

    MigrationChainStatus s = inspector.inspect(null);

    assertFalse(s.healthy());
    assertEquals("CHECK_FAILED", s.outcome());
    assertNotNull(s.errorMessage());
  }

  @Test
  public void inspect_unhealthy_lists_all_pending_in_chain_order() {
    ChainSnapshot snapshot = new ChainSnapshot(List.of(
      new PendingEntry("60", MigrationState.APPLIED),
      new PendingEntry("61", MigrationState.PENDING),
      new PendingEntry("62", MigrationState.PENDING),
      new PendingEntry("63", MigrationState.APPLIED)
    ));
    Migrations m = stubMigrations(validationResult(
      Outcome.INCOMPLETE_MIGRATIONS,
      List.of("Migration V62 has not been applied yet")
    ));
    MigrationChainInspector inspector = inspectorWith(snapshot);

    MigrationChainStatus s = inspector.inspect(m);

    assertFalse(s.healthy());
    assertEquals(List.of("61", "62"), s.pendingVersions());
    assertTrue(s.errorMessage().contains("61"));
    assertTrue(s.errorMessage().contains("62"));
  }

  // ------------------------------------------------------------------ helpers

  private static MigrationChainInspector inspectorWith(ChainSnapshot snapshot) {
    Function<Migrations, ChainSnapshot> fn = m -> snapshot;
    return new MigrationChainInspector(() -> FIXED_NOW, fn);
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
