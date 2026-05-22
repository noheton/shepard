package de.dlr.shepard.plugins.importer.runs;

import com.github.f4b6a3.uuid.UuidCreator;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * IMP1a / PR-2 — Service surface for the {@code importer_run}
 * lifecycle. The single point of write to the table; the future
 * orchestrator (PR-4) and the REST resource (PR-4) both go
 * through here.
 *
 * <p>The method shapes deliberately mirror the
 * {@code JobService} interface proposed in
 * {@code aidocs/platform/32 §4}:
 *
 * <pre>
 *   submit(...)              ── PENDING row insert
 *   markStarted(id)          ── claim transition (PENDING → RUNNING)
 *   recordProgress(id, ...)  ── heartbeat + progress fields
 *   markSucceeded(id, ...)   ── RUNNING → SUCCEEDED
 *   markFailed(id, ...)      ── any non-terminal → FAILED
 *   markCancelled(id)        ── PENDING → CANCELLED (immediate)
 *   requestCancellation(id)  ── RUNNING → set cancel_requested flag
 *   getRun(id, principal)    ── permission-checked read
 *   listRuns(...)            ── permission-checked list
 * </pre>
 *
 * <p>Every write method is {@code @Transactional} — the row must
 * commit together with whatever caller-side bookkeeping the
 * service does (currently none, since PR-2 has no caller; PR-4
 * adds the REST layer + the optional collection-linking).
 *
 * <p>Permission boundary: only the submitting {@code principal}
 * can {@code GET} or mutate via cancellation. Admins (post-A0)
 * can list / read all — gated on the
 * {@code instance-admin} role at the REST layer in PR-4. This
 * service exposes a {@code getRunForAdmin(id)} variant that
 * skips the principal check; the REST resource is the only
 * caller permitted to use it.
 */
@ApplicationScoped
public class ImporterRunService {

  @Inject
  ImporterRunRepository repository;

  // ====================== submission ======================

  /**
   * Insert a fresh {@code PENDING} row and return the assigned id.
   * The caller can synchronously share the id with the client
   * (e.g. via the {@code Location} header in the
   * {@code 202 Accepted} response PR-4 ships).
   *
   * <p>Sensitive fields ({@code apiKey}, {@code password}) should
   * be redacted from {@code requestPayload} before calling — the
   * cleartext lives in {@code sourceConfig}, which PR-3 will
   * encrypt at rest. PR-2 stores both verbatim as a stub.
   *
   * @param sourceKind          which adapter (REQUIRED)
   * @param principal           sub or API-key id (REQUIRED)
   * @param targetCollectionAppId Collection on this instance (nullable)
   * @param requestPayload      original request body, secrets redacted (nullable)
   * @param sourceConfig        adapter credentials (nullable; will be
   *                            encrypted at rest in PR-3)
   * @return the freshly-inserted {@link ImporterRun}, ready for
   *         the REST layer to serialise back to the client.
   * @throws NullPointerException if {@code sourceKind} or
   *         {@code principal} is null.
   * @throws IllegalArgumentException if {@code principal} is blank.
   */
  @Transactional
  public ImporterRun submit(
    ImporterSourceKind sourceKind,
    String principal,
    String targetCollectionAppId,
    String requestPayload,
    String sourceConfig
  ) {
    Objects.requireNonNull(sourceKind, "sourceKind");
    Objects.requireNonNull(principal, "principal");
    if (principal.isBlank()) {
      throw new IllegalArgumentException("principal must not be blank");
    }
    var run = new ImporterRun();
    run.setId(UuidCreator.getTimeOrderedEpoch());
    run.setSourceKind(sourceKind);
    run.setPrincipal(principal);
    run.setTargetCollectionAppId(targetCollectionAppId);
    run.setRequestPayload(requestPayload);
    run.setSourceConfig(sourceConfig);
    // createdAt + status + progressDone + cancelRequested already
    // initialised by the ImporterRun no-arg constructor.
    repository.persist(run);
    Log.infof(
      "IMP1a: submitted run id=%s kind=%s principal=%s target=%s",
      run.getId(),
      sourceKind,
      principal,
      targetCollectionAppId
    );
    return run;
  }

  // ====================== state transitions ======================

  /**
   * Claim transition — {@code PENDING → RUNNING}. PR-4 will call
   * this from the scheduler inside a
   * {@code SELECT ... FOR UPDATE SKIP LOCKED} envelope to avoid
   * double-claim across multiple backend pods. PR-2 ships the
   * single-row transition only.
   *
   * @return {@code true} if the transition fired; {@code false}
   *         if the row was already non-{@code PENDING} (claimed by
   *         a faster sibling, or cancelled before pickup).
   */
  @Transactional
  public boolean markStarted(UUID runId) {
    var run = repository.findById(runId);
    if (run == null) {
      return false;
    }
    if (run.getStatus() != ImporterRunStatus.PENDING) {
      return false;
    }
    var now = Instant.now();
    run.setStatus(ImporterRunStatus.RUNNING);
    run.setStartedAt(now);
    run.setLastProgressAt(now);
    return true;
  }

  /**
   * Heartbeat + progress update. The adapter must call this at
   * least once every {@code stale-after / 2} or the reaper will
   * mark the row {@code FAILED} with
   * {@code error_class=JOB_STALLED}. {@code done} / {@code total}
   * are advisory and may be {@code null} when unknown; {@code message}
   * carries a human-readable status line.
   *
   * <p>Silently no-ops if the row is missing or no longer
   * {@code RUNNING} — the caller (which is the adapter's own
   * loop) doesn't need to react to that.
   */
  @Transactional
  public void recordProgress(UUID runId, Long done, Long total, String message) {
    var run = repository.findById(runId);
    if (run == null || run.getStatus() != ImporterRunStatus.RUNNING) {
      return;
    }
    if (done != null) {
      run.setProgressDone(done);
    }
    if (total != null) {
      run.setProgressTotal(total);
    }
    if (message != null) {
      run.setProgressMessage(message);
    }
    run.setLastProgressAt(Instant.now());
  }

  /**
   * Success transition — any non-terminal status → {@code SUCCEEDED}.
   * {@code resultUrl} is opaque to this service (for the importer
   * it's the appId of the freshly-populated target collection); the
   * field is rendered to clients verbatim.
   */
  @Transactional
  public void markSucceeded(UUID runId, String resultUrl, String resultMetadata) {
    var run = repository.findById(runId);
    if (run == null || run.getStatus().isTerminal()) {
      return;
    }
    var now = Instant.now();
    run.setStatus(ImporterRunStatus.SUCCEEDED);
    run.setFinishedAt(now);
    run.setLastProgressAt(now);
    run.setResultUrl(resultUrl);
    run.setResultMetadata(resultMetadata);
    Log.infof("IMP1a: run %s SUCCEEDED (resultUrl=%s)", runId, resultUrl);
  }

  /**
   * Failure transition. {@code errorClass} carries a stable
   * code ({@code JOB_STALLED}, {@code JOB_CANCELLED}) or the
   * thrown exception's class name; {@code errorMessage} is the
   * redacted detail. Callers must redact secrets before calling.
   */
  @Transactional
  public void markFailed(UUID runId, String errorClass, String errorMessage) {
    var run = repository.findById(runId);
    if (run == null || run.getStatus().isTerminal()) {
      return;
    }
    var now = Instant.now();
    run.setStatus(ImporterRunStatus.FAILED);
    run.setFinishedAt(now);
    run.setLastProgressAt(now);
    run.setErrorClass(errorClass);
    run.setErrorMessage(errorMessage);
    Log.warnf(
      "IMP1a: run %s FAILED (errorClass=%s message=%s)",
      runId,
      errorClass,
      errorMessage
    );
  }

  /**
   * Cancellation while still {@code PENDING}. The row's status
   * goes straight to {@code CANCELLED} (no {@code RUNNING}
   * detour). The REST layer in PR-4 picks between this method
   * and {@link #requestCancellation(UUID)} based on the row's
   * current status.
   */
  @Transactional
  public void markCancelled(UUID runId) {
    var run = repository.findById(runId);
    if (run == null || run.getStatus().isTerminal()) {
      return;
    }
    if (run.getStatus() != ImporterRunStatus.PENDING) {
      // Use requestCancellation(...) for RUNNING rows — never
      // force a terminal transition out of band.
      return;
    }
    var now = Instant.now();
    run.setStatus(ImporterRunStatus.CANCELLED);
    run.setFinishedAt(now);
    Log.infof("IMP1a: run %s CANCELLED (was PENDING)", runId);
  }

  /**
   * Request cooperative cancellation on a {@code RUNNING} row.
   * The adapter polls {@code isCancelRequested()} at its next
   * checkpoint and aborts cleanly; the actual transition to
   * {@code CANCELLED} happens in the adapter via
   * {@link #markFailed(UUID, String, String)} with
   * {@code errorClass=JOB_CANCELLED} (or a dedicated
   * markCancelledFromRunner once that shape lands).
   *
   * <p>Silently no-ops if the row is missing, already-cancelled,
   * or non-{@code RUNNING}. The REST layer should pick between
   * this and {@link #markCancelled(UUID)} based on the row's
   * current status.
   */
  @Transactional
  public void requestCancellation(UUID runId) {
    var run = repository.findById(runId);
    if (run == null || run.getStatus().isTerminal()) {
      return;
    }
    if (run.getStatus() != ImporterRunStatus.RUNNING) {
      return;
    }
    run.setCancelRequested(true);
    Log.infof("IMP1a: run %s cancel_requested=true", runId);
  }

  // ====================== reads ======================

  /**
   * Single-row read, permission-checked against {@code principal}.
   * Returns {@link Optional#empty()} both for "row does not exist"
   * and for "row exists but belongs to a different principal" —
   * we deliberately do not leak the row's existence to a caller
   * who doesn't own it.
   */
  public Optional<ImporterRun> getRun(UUID runId, String principal) {
    Objects.requireNonNull(principal, "principal");
    var run = repository.findById(runId);
    if (run == null || !principal.equals(run.getPrincipal())) {
      return Optional.empty();
    }
    return Optional.of(run);
  }

  /**
   * Admin variant — skips the principal check. The REST resource
   * is the only legitimate caller and must gate its endpoint on
   * {@code @RolesAllowed("instance-admin")}.
   */
  public Optional<ImporterRun> getRunForAdmin(UUID runId) {
    return repository.findOne(runId);
  }

  /**
   * Paged "my runs" listing.
   *
   * @param statusFilter optional; null lists all of the caller's
   *                     runs regardless of status.
   * @param page         0-indexed.
   * @param size         clamped to {@code 1..100} by the REST
   *                     layer in PR-4 (Hibernate's
   *                     {@code page(...)} accepts any non-negative
   *                     size — the cap is a defence-in-depth
   *                     concern, not a hard-error at this layer).
   */
  public List<ImporterRun> listMyRuns(
    String principal,
    ImporterRunStatus statusFilter,
    int page,
    int size
  ) {
    Objects.requireNonNull(principal, "principal");
    return repository.listByPrincipal(principal, statusFilter, page, size);
  }
}
