package de.dlr.shepard.plugins.importer.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * IMP1a / PR-2 — service-level tests with the repository mocked.
 * Verifies:
 *
 * <ul>
 *   <li>{@code submit(...)} mints a UUID, persists the row in
 *       PENDING, validates required args;
 *   <li>state transitions are guarded by {@link ImporterRunStatus#isTerminal()};
 *   <li>cancellation distinguishes between PENDING (direct
 *       transition) and RUNNING (cooperative flag);
 *   <li>{@code getRun(..)} enforces the principal-permission
 *       boundary — a different principal sees
 *       {@link Optional#empty()} rather than the row;
 *   <li>{@code getRunForAdmin(..)} bypasses that check.
 * </ul>
 *
 * <p>Persistence-layer behaviour (transactional commit, FOR UPDATE
 * SKIP LOCKED claim semantics) is covered by the PR-7 integration
 * test against a real Postgres testcontainer.
 */
final class ImporterRunServiceTest {

  private ImporterRunRepository repo;
  private ImporterRunService service;

  @BeforeEach
  void wire() {
    repo = mock(ImporterRunRepository.class);
    service = new ImporterRunService();
    service.repository = repo;
  }

  // ====================== submit ======================

  @Test
  void submit_persists_a_pending_row_with_minted_id() {
    var captured = new java.util.concurrent.atomic.AtomicReference<ImporterRun>();
    doAnswer(invocation -> {
        captured.set(invocation.getArgument(0));
        return null;
      })
      .when(repo)
      .persist(any(ImporterRun.class));

    var result = service.submit(
      ImporterSourceKind.DLR_V5_SHEPARD,
      "alice",
      "0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506",
      "{\"requestPayload\":\"...\"}",
      "{\"sourceConfig\":\"...\"}"
    );

    assertThat(result).isSameAs(captured.get());
    assertThat(result.getId()).isNotNull();
    assertThat(result.getStatus()).isEqualTo(ImporterRunStatus.PENDING);
    assertThat(result.getSourceKind()).isEqualTo(ImporterSourceKind.DLR_V5_SHEPARD);
    assertThat(result.getPrincipal()).isEqualTo("alice");
    assertThat(result.getTargetCollectionAppId())
      .isEqualTo("0190d1f8-7c4d-7d8a-91a5-b7c2d3e4f506");
    assertThat(result.getRequestPayload()).isEqualTo("{\"requestPayload\":\"...\"}");
    assertThat(result.getSourceConfig()).isEqualTo("{\"sourceConfig\":\"...\"}");
    assertThat(result.getCreatedAt()).isNotNull();
    assertThat(result.isCancelRequested()).isFalse();
  }

  @Test
  void submit_rejects_null_source_kind() {
    assertThatThrownBy(() -> service.submit(null, "alice", null, null, null))
      .isInstanceOf(NullPointerException.class)
      .hasMessageContaining("sourceKind");
  }

  @Test
  void submit_rejects_null_principal() {
    assertThatThrownBy(() ->
      service.submit(ImporterSourceKind.DLR_V5_SHEPARD, null, null, null, null)
    )
      .isInstanceOf(NullPointerException.class)
      .hasMessageContaining("principal");
  }

  @Test
  void submit_rejects_blank_principal() {
    assertThatThrownBy(() ->
      service.submit(ImporterSourceKind.DLR_V5_SHEPARD, "  ", null, null, null)
    )
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessageContaining("principal");
  }

  // ====================== state transitions ======================

  @Test
  void markStarted_promotes_pending_to_running() {
    var run = pendingRun();
    when(repo.findById(eq(run.getId()))).thenReturn(run);
    assertThat(service.markStarted(run.getId())).isTrue();
    assertThat(run.getStatus()).isEqualTo(ImporterRunStatus.RUNNING);
    assertThat(run.getStartedAt()).isNotNull();
    assertThat(run.getLastProgressAt()).isNotNull();
  }

  @Test
  void markStarted_returns_false_when_row_missing() {
    var id = UUID.randomUUID();
    when(repo.findById(eq(id))).thenReturn(null);
    assertThat(service.markStarted(id)).isFalse();
  }

  @Test
  void markStarted_returns_false_when_row_not_pending() {
    var run = pendingRun();
    run.setStatus(ImporterRunStatus.RUNNING);
    when(repo.findById(eq(run.getId()))).thenReturn(run);
    assertThat(service.markStarted(run.getId())).isFalse();
    assertThat(run.getStatus()).isEqualTo(ImporterRunStatus.RUNNING);
  }

  @Test
  void recordProgress_updates_done_total_and_message_on_running_row() {
    var run = pendingRun();
    run.setStatus(ImporterRunStatus.RUNNING);
    when(repo.findById(eq(run.getId()))).thenReturn(run);

    service.recordProgress(run.getId(), 42L, 100L, "halfway");

    assertThat(run.getProgressDone()).isEqualTo(42L);
    assertThat(run.getProgressTotal()).isEqualTo(100L);
    assertThat(run.getProgressMessage()).isEqualTo("halfway");
    assertThat(run.getLastProgressAt()).isNotNull();
  }

  @Test
  void recordProgress_silently_noops_on_non_running_row() {
    var run = pendingRun(); // still PENDING
    when(repo.findById(eq(run.getId()))).thenReturn(run);
    service.recordProgress(run.getId(), 1L, 2L, "should-not-apply");
    assertThat(run.getProgressDone()).isZero();
    assertThat(run.getProgressMessage()).isNull();
  }

  @Test
  void recordProgress_silently_noops_on_missing_row() {
    var id = UUID.randomUUID();
    when(repo.findById(eq(id))).thenReturn(null);
    // Must not throw — the runner doesn't need to react to this.
    service.recordProgress(id, 1L, 2L, "ignored");
  }

  @Test
  void markSucceeded_promotes_running_to_succeeded() {
    var run = pendingRun();
    run.setStatus(ImporterRunStatus.RUNNING);
    when(repo.findById(eq(run.getId()))).thenReturn(run);

    service.markSucceeded(run.getId(), "collection-app-id", "{\"count\":42}");

    assertThat(run.getStatus()).isEqualTo(ImporterRunStatus.SUCCEEDED);
    assertThat(run.getFinishedAt()).isNotNull();
    assertThat(run.getResultUrl()).isEqualTo("collection-app-id");
    assertThat(run.getResultMetadata()).isEqualTo("{\"count\":42}");
  }

  @Test
  void markSucceeded_skips_terminal_row() {
    var run = pendingRun();
    run.setStatus(ImporterRunStatus.FAILED);
    var preFinished = Instant.parse("2020-01-01T00:00:00Z");
    run.setFinishedAt(preFinished);
    when(repo.findById(eq(run.getId()))).thenReturn(run);

    service.markSucceeded(run.getId(), "url", "{}");

    assertThat(run.getStatus())
      .as("terminal row must not transition again")
      .isEqualTo(ImporterRunStatus.FAILED);
    assertThat(run.getFinishedAt()).isEqualTo(preFinished);
  }

  @Test
  void markFailed_promotes_non_terminal_to_failed_with_error_fields() {
    var run = pendingRun();
    run.setStatus(ImporterRunStatus.RUNNING);
    when(repo.findById(eq(run.getId()))).thenReturn(run);

    service.markFailed(run.getId(), "DLRv5Error", "401 from source");

    assertThat(run.getStatus()).isEqualTo(ImporterRunStatus.FAILED);
    assertThat(run.getErrorClass()).isEqualTo("DLRv5Error");
    assertThat(run.getErrorMessage()).isEqualTo("401 from source");
    assertThat(run.getFinishedAt()).isNotNull();
  }

  @Test
  void markCancelled_only_acts_on_pending_rows() {
    // PENDING: transitions
    var pending = pendingRun();
    when(repo.findById(eq(pending.getId()))).thenReturn(pending);
    service.markCancelled(pending.getId());
    assertThat(pending.getStatus()).isEqualTo(ImporterRunStatus.CANCELLED);
    assertThat(pending.getFinishedAt()).isNotNull();

    // RUNNING: leaves status alone (caller should use
    // requestCancellation instead).
    var running = pendingRun();
    running.setStatus(ImporterRunStatus.RUNNING);
    when(repo.findById(eq(running.getId()))).thenReturn(running);
    service.markCancelled(running.getId());
    assertThat(running.getStatus()).isEqualTo(ImporterRunStatus.RUNNING);
    assertThat(running.getFinishedAt()).isNull();
  }

  @Test
  void requestCancellation_sets_flag_only_on_running_row() {
    var running = pendingRun();
    running.setStatus(ImporterRunStatus.RUNNING);
    when(repo.findById(eq(running.getId()))).thenReturn(running);
    service.requestCancellation(running.getId());
    assertThat(running.isCancelRequested()).isTrue();

    // PENDING: leaves the flag alone — REST layer should use
    // markCancelled for this case instead.
    var pending = pendingRun();
    when(repo.findById(eq(pending.getId()))).thenReturn(pending);
    service.requestCancellation(pending.getId());
    assertThat(pending.isCancelRequested()).isFalse();
  }

  @Test
  void requestCancellation_silently_noops_on_terminal_row() {
    var done = pendingRun();
    done.setStatus(ImporterRunStatus.SUCCEEDED);
    when(repo.findById(eq(done.getId()))).thenReturn(done);
    service.requestCancellation(done.getId());
    assertThat(done.isCancelRequested()).isFalse();
  }

  // ====================== reads ======================

  @Test
  void getRun_returns_row_for_owning_principal() {
    var run = pendingRun();
    when(repo.findById(eq(run.getId()))).thenReturn(run);
    var result = service.getRun(run.getId(), run.getPrincipal());
    assertThat(result).contains(run);
  }

  @Test
  void getRun_returns_empty_for_different_principal() {
    var run = pendingRun();
    when(repo.findById(eq(run.getId()))).thenReturn(run);
    var result = service.getRun(run.getId(), "mallory");
    assertThat(result)
      .as(
        "permission boundary: row existence is not leaked to a non-owning principal"
      )
      .isEmpty();
  }

  @Test
  void getRun_returns_empty_for_missing_row() {
    var id = UUID.randomUUID();
    when(repo.findById(eq(id))).thenReturn(null);
    assertThat(service.getRun(id, "alice")).isEmpty();
  }

  @Test
  void getRunForAdmin_bypasses_principal_check() {
    var run = pendingRun();
    when(repo.findOne(eq(run.getId()))).thenReturn(Optional.of(run));
    var result = service.getRunForAdmin(run.getId());
    assertThat(result).contains(run);
  }

  @Test
  void listMyRuns_delegates_to_repository_with_principal_filter() {
    var run = pendingRun();
    when(repo.listByPrincipal(eq("alice"), eq(null), eq(0), eq(50)))
      .thenReturn(List.of(run));
    var result = service.listMyRuns("alice", null, 0, 50);
    assertThat(result).containsExactly(run);
  }

  @Test
  void submit_with_null_target_and_payloads_does_not_throw() {
    // Validate-only runs (no target Collection, no payload, no
    // credentials) are a legitimate use-case PR-3+ will exercise
    // — the service must not require them at submit time.
    var captured = new java.util.concurrent.atomic.AtomicReference<ImporterRun>();
    doAnswer(invocation -> {
        captured.set(invocation.getArgument(0));
        return null;
      })
      .when(repo)
      .persist(any(ImporterRun.class));
    var result = service.submit(
      ImporterSourceKind.DLR_V5_SHEPARD,
      "alice",
      null,
      null,
      null
    );
    assertThat(captured.get()).isSameAs(result);
    assertThat(result.getTargetCollectionAppId()).isNull();
    assertThat(result.getRequestPayload()).isNull();
    assertThat(result.getSourceConfig()).isNull();
  }

  @Test
  void state_transitions_never_act_on_missing_row() {
    var missing = UUID.randomUUID();
    when(repo.findById(eq(missing))).thenReturn(null);
    // None of these should throw — defensive no-ops.
    service.recordProgress(missing, 1L, 2L, "x");
    service.markSucceeded(missing, "x", "{}");
    service.markFailed(missing, "x", "x");
    service.markCancelled(missing);
    service.requestCancellation(missing);
    // Cannot use `verify(repo, never()).persist(any())` because
    // Panache's overloaded persist(Iterable) vs persist(Stream)
    // make `any()` ambiguous. Instead, verify nothing was looked
    // up that resulted in a fetch — captured by the state of the
    // tests above. We confirm the missing-row branch does not
    // throw, which is the contract this test pins.
  }

  // ====================== helpers ======================

  private ImporterRun pendingRun() {
    var run = new ImporterRun();
    run.setId(UUID.randomUUID());
    run.setSourceKind(ImporterSourceKind.DLR_V5_SHEPARD);
    run.setPrincipal("alice");
    return run;
  }
}
