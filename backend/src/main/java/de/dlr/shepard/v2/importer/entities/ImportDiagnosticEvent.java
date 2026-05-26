package de.dlr.shepard.v2.importer.entities;

import java.time.Instant;
import java.util.Map;

/**
 * IMP-DIAG — a single structured diagnostic event emitted during an import run.
 *
 * <p>Events are stored in-memory by {@link de.dlr.shepard.v2.importer.services.ImportDiagnosticsLog}
 * in a ring buffer keyed by {@link #runId()}.  The {@code runId} is the {@code lockId}
 * from {@link ImportLock} so that diagnostic events are naturally joined to the lock
 * lifecycle without introducing a parallel identifier.
 *
 * <p>Events are immutable records — all fields are set at construction time.
 *
 * @param runId       the {@code lockId} of the active {@link ImportLock}; never null
 * @param timestamp   when the event was emitted
 * @param level       severity: {@code "INFO"}, {@code "WARN"}, or {@code "ERROR"}
 * @param phase       import phase: {@code "WARMUP"}, {@code "DO_CREATE"},
 *                    {@code "REF_ATTACH"}, {@code "FILE_UPLOAD"}, or {@code "COMPLETE"}
 * @param entityAppId appId of the DataObject or Container this event relates to;
 *                    {@code null} for run-level events
 * @param message     human-readable description of the event
 * @param attributes  arbitrary structured key-value pairs for machine-readable context
 *                    (e.g. {@code "count"}, {@code "retryCount"}, {@code "errorCode"});
 *                    may be empty, never null
 */
public record ImportDiagnosticEvent(
  String runId,
  Instant timestamp,
  String level,
  String phase,
  String entityAppId,
  String message,
  Map<String, Object> attributes
) {

  /** Allowed level values. */
  public static final String LEVEL_INFO  = "INFO";
  public static final String LEVEL_WARN  = "WARN";
  public static final String LEVEL_ERROR = "ERROR";

  /** Allowed phase values. */
  public static final String PHASE_WARMUP      = "WARMUP";
  public static final String PHASE_DO_CREATE   = "DO_CREATE";
  public static final String PHASE_REF_ATTACH  = "REF_ATTACH";
  public static final String PHASE_FILE_UPLOAD = "FILE_UPLOAD";
  public static final String PHASE_COMPLETE    = "COMPLETE";

  /** Canonical constructor: normalises {@code attributes} to an unmodifiable map. */
  public ImportDiagnosticEvent {
    if (attributes == null) attributes = Map.of();
    else attributes = Map.copyOf(attributes);
  }
}
