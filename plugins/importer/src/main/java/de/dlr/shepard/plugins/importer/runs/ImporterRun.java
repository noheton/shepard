package de.dlr.shepard.plugins.importer.runs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * IMP1a / PR-2 — Postgres-backed importer-run row.
 *
 * <p>This is the {@code importer_run} table backing the importer
 * plugin's asynchronous lifecycle. Each row represents one
 * invocation of one source-adapter against one target collection.
 *
 * <p><b>Lineage with the JobService design.</b> The column set
 * here is the **first concrete instance** of the generic
 * {@code JobService} surface proposed in
 * {@code aidocs/platform/32-long-running-process-pattern.md §3}.
 * The names ({@code status}, {@code created_at},
 * {@code started_at}, {@code last_progress_at},
 * {@code finished_at}, {@code progress_total},
 * {@code progress_done}, {@code progress_message},
 * {@code error_class}, {@code error_message},
 * {@code result_url}, {@code result_metadata},
 * {@code request_payload}, {@code cancel_requested}) deliberately
 * match the kernel proposed in §3 so a future generic
 * {@code de.dlr.shepard.common.jobs.Job} entity can adopt this
 * table by renaming the underlying class without touching SQL or
 * wire shape. The importer-specific extras (the
 * {@code source_kind} discriminator + the encrypted
 * {@code source_config} blob + the {@code target_collection_app_id}
 * pointer) stay on the {@code ImporterRun} subclass — they don't
 * belong in the generic kernel.
 *
 * <p>See {@code aidocs/platform/32 §11} ("Backlog impact —
 * J1 JobService scaffolding") + the changelog entry queued for the
 * graduation pass.
 *
 * <p>The migration shipping this table is
 * {@code V1.11.1__add_importer_run_table.sql} in this module's
 * {@code src/main/resources/db/migration/}. Quarkus Flyway picks
 * up the file from the plugin JAR's classpath because the
 * backend's {@code quarkus.flyway.locations} includes
 * {@code db/migration} (default classpath location).
 */
@Entity
@Table(name = "importer_run")
public class ImporterRun {

  /**
   * UUID v7 — time-sortable, cheap on B-tree. Per
   * {@code aidocs/platform/25 §3} L2 design choice. We mint the
   * UUID at the service layer (not via Hibernate's UUID generator)
   * so the caller can know the id at submit time before the row
   * commits.
   */
  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  /**
   * Source-adapter discriminator. {@code text} on the SQL side so
   * adding a new kind is a no-DDL change.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "source_kind", nullable = false, length = 32)
  private ImporterSourceKind sourceKind;

  /**
   * Submitting principal (sub claim or API-key id). Permission
   * boundary key — only the principal can {@code GET}/{@code DELETE}
   * the row (admins post-A0 can list all).
   */
  @Column(name = "principal", nullable = false, length = 255)
  private String principal;

  /**
   * Target Collection on this (the local) instance, by appId.
   * Nullable for adapters that don't have a single target (e.g. a
   * dry-run validation that creates no rows); enforced non-null
   * at the service layer for actual imports.
   */
  @Column(name = "target_collection_app_id", length = 64)
  private String targetCollectionAppId;

  /**
   * Per JobService §3: {@code status} drives the state machine.
   * Indexed via {@code (principal, status)} + {@code (source_kind, status, finished_at)}.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 16)
  private ImporterRunStatus status;

  /**
   * Cooperative cancellation flag. Per JobService §5 the
   * {@code DELETE /v2/imports/{id}} handler sets this to
   * {@code true} when the row is {@code RUNNING}; the adapter
   * polls and aborts cleanly at its next checkpoint. We do NOT
   * interrupt the worker thread — see JobService §5 cancellation
   * paragraph for rationale.
   */
  @Column(name = "cancel_requested", nullable = false)
  private boolean cancelRequested;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  /**
   * Heartbeat for the stale-job reaper. Adapters must call
   * {@code recordProgress(...)} at least once every
   * {@code stale-after / 2}; the reaper transitions any
   * {@code RUNNING} row whose {@code last_progress_at} is older
   * than {@code stale-after} to {@code FAILED} with
   * {@code error_class=JOB_STALLED}.
   */
  @Column(name = "last_progress_at")
  private Instant lastProgressAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  /**
   * Kind-specific count; nullable when the adapter cannot
   * estimate (e.g. an unbounded source stream).
   */
  @Column(name = "progress_total")
  private Long progressTotal;

  /**
   * Always {@code <= progress_total} when both are non-null.
   * Defaults to {@code 0L} at submit time.
   */
  @Column(name = "progress_done", nullable = false)
  private long progressDone;

  @Column(name = "progress_message", columnDefinition = "TEXT")
  private String progressMessage;

  /**
   * Set only when {@code status = FAILED}. Internal exception
   * class or a stable code ({@code JOB_STALLED},
   * {@code JOB_CANCELLED}).
   */
  @Column(name = "error_class", length = 255)
  private String errorClass;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  /**
   * Where the success payload lives. For the importer the result
   * is the appId of the freshly-populated target collection; for
   * future adapters that produce an exportable blob this is a
   * presigned S3 URL (P12).
   */
  @Column(name = "result_url", columnDefinition = "TEXT")
  private String resultUrl;

  /**
   * Kind-specific. For {@code DLR_V5_SHEPARD} this carries
   * counts of imported DataObjects, files, and timeseries channels.
   * Stored as a JSON string in a jsonb column. Parsed by the
   * runner when reading; never introspected on the SQL side.
   */
  @Column(name = "result_metadata", columnDefinition = "jsonb")
  private String resultMetadata;

  /**
   * The original request payload (source URL, target collection
   * id, adapter-specific options). Stored as JSON in a jsonb
   * column. <b>Sensitive fields ({@code apiKey}, {@code password})
   * are redacted by the service layer before insert</b>; the
   * cleartext credentials live in {@code source_config} and are
   * encrypted-at-rest (PR-3 — stub uses
   * {@code ${SHEPARD_INSTANCE_SECRET}} as the key seed).
   */
  @Column(name = "request_payload", columnDefinition = "jsonb")
  private String requestPayload;

  /**
   * Adapter credentials, encrypted-at-rest by the service layer.
   * PR-2 stores the plaintext JSON as a stub; PR-3 ships the
   * AES-GCM cipher keyed off {@code ${SHEPARD_INSTANCE_SECRET}}.
   * Vault / KMS integration is a follow-up.
   */
  @Column(name = "source_config", columnDefinition = "jsonb")
  private String sourceConfig;

  /**
   * Default constructor — Hibernate requires this. Initialises
   * the row in {@code PENDING} status with {@code createdAt=now}
   * and {@code progressDone=0}.
   */
  public ImporterRun() {
    this.status = ImporterRunStatus.PENDING;
    this.createdAt = Instant.now();
    this.progressDone = 0L;
    this.cancelRequested = false;
  }

  // ====================== getters & setters ======================
  // Plain JavaBean shape — we'd prefer Lombok @Data but the plugin
  // pom doesn't pull lombok at compile time today, and the entity
  // is small enough to justify hand-rolled accessors. Adding
  // lombok-provided to the pom is a follow-up.

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public ImporterSourceKind getSourceKind() {
    return sourceKind;
  }

  public void setSourceKind(ImporterSourceKind sourceKind) {
    this.sourceKind = sourceKind;
  }

  public String getPrincipal() {
    return principal;
  }

  public void setPrincipal(String principal) {
    this.principal = principal;
  }

  public String getTargetCollectionAppId() {
    return targetCollectionAppId;
  }

  public void setTargetCollectionAppId(String targetCollectionAppId) {
    this.targetCollectionAppId = targetCollectionAppId;
  }

  public ImporterRunStatus getStatus() {
    return status;
  }

  public void setStatus(ImporterRunStatus status) {
    this.status = status;
  }

  public boolean isCancelRequested() {
    return cancelRequested;
  }

  public void setCancelRequested(boolean cancelRequested) {
    this.cancelRequested = cancelRequested;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getLastProgressAt() {
    return lastProgressAt;
  }

  public void setLastProgressAt(Instant lastProgressAt) {
    this.lastProgressAt = lastProgressAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public Long getProgressTotal() {
    return progressTotal;
  }

  public void setProgressTotal(Long progressTotal) {
    this.progressTotal = progressTotal;
  }

  public long getProgressDone() {
    return progressDone;
  }

  public void setProgressDone(long progressDone) {
    this.progressDone = progressDone;
  }

  public String getProgressMessage() {
    return progressMessage;
  }

  public void setProgressMessage(String progressMessage) {
    this.progressMessage = progressMessage;
  }

  public String getErrorClass() {
    return errorClass;
  }

  public void setErrorClass(String errorClass) {
    this.errorClass = errorClass;
  }

  public String getErrorMessage() {
    return errorMessage;
  }

  public void setErrorMessage(String errorMessage) {
    this.errorMessage = errorMessage;
  }

  public String getResultUrl() {
    return resultUrl;
  }

  public void setResultUrl(String resultUrl) {
    this.resultUrl = resultUrl;
  }

  public String getResultMetadata() {
    return resultMetadata;
  }

  public void setResultMetadata(String resultMetadata) {
    this.resultMetadata = resultMetadata;
  }

  public String getRequestPayload() {
    return requestPayload;
  }

  public void setRequestPayload(String requestPayload) {
    this.requestPayload = requestPayload;
  }

  public String getSourceConfig() {
    return sourceConfig;
  }

  public void setSourceConfig(String sourceConfig) {
    this.sourceConfig = sourceConfig;
  }
}
