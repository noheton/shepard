package de.dlr.shepard.plugins.importer.runs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * IMP1a / PR-2 — status-enum smoke tests. Trivial in isolation
 * but they pin the {@link ImporterRunStatus#isTerminal()} contract
 * the reaper, GC sweep, and cancellation paths depend on.
 */
final class ImporterRunStatusTest {

  @Test
  void pending_and_running_are_not_terminal() {
    assertThat(ImporterRunStatus.PENDING.isTerminal()).isFalse();
    assertThat(ImporterRunStatus.RUNNING.isTerminal()).isFalse();
  }

  @Test
  void succeeded_failed_and_cancelled_are_terminal() {
    assertThat(ImporterRunStatus.SUCCEEDED.isTerminal()).isTrue();
    assertThat(ImporterRunStatus.FAILED.isTerminal()).isTrue();
    assertThat(ImporterRunStatus.CANCELLED.isTerminal()).isTrue();
  }

  @Test
  void enum_contains_all_five_expected_values() {
    // Guards against an accidental enum reorder or new state being
    // added without updating isTerminal(). The order itself doesn't
    // matter; the closed-set composition does.
    assertThat(ImporterRunStatus.values()).hasSize(5);
    assertThat(ImporterRunStatus.values())
      .containsExactlyInAnyOrder(
        ImporterRunStatus.PENDING,
        ImporterRunStatus.RUNNING,
        ImporterRunStatus.SUCCEEDED,
        ImporterRunStatus.FAILED,
        ImporterRunStatus.CANCELLED
      );
  }
}
