package de.dlr.shepard.v2.importer.io;

import de.dlr.shepard.v2.importer.entities.ImportDiagnosticEvent;
import de.dlr.shepard.v2.importer.services.ImportDiagnosticsLog;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * IMP-DIAG — request/response shapes for the diagnostic REST surface.
 */
public final class ImportDiagnosticsIO {

  private ImportDiagnosticsIO() {}

  /**
   * Response item for a single diagnostic event.
   *
   * @param runId       run identifier (= ImportLock.lockId)
   * @param timestamp   ISO-8601 UTC timestamp of the event
   * @param level       {@code INFO / WARN / ERROR}
   * @param phase       {@code WARMUP / DO_CREATE / REF_ATTACH / FILE_UPLOAD / COMPLETE}
   * @param entityAppId appId of the related DO/container; {@code null} for run-level events
   * @param message     human-readable description
   * @param attributes  structured key-value context
   */
  @Schema(description = "A single import diagnostic event (INFO/WARN/ERROR) logged during an import run.")
  public record EventIO(
    String runId,
    String timestamp,
    String level,
    String phase,
    String entityAppId,
    String message,
    Map<String, Object> attributes
  ) {
    /** Convert a domain event to its REST representation. */
    public static EventIO from(ImportDiagnosticEvent e) {
      return new EventIO(
        e.runId(),
        toIso(e.timestamp()),
        e.level(),
        e.phase(),
        e.entityAppId(),
        e.message(),
        e.attributes()
      );
    }

    private static String toIso(Instant instant) {
      if (instant == null) return null;
      return DateTimeFormatter.ISO_INSTANT.format(instant.atZone(ZoneOffset.UTC));
    }
  }

  /**
   * Response item for a run summary in {@code GET /v2/import/runs}.
   *
   * @param runId        run identifier
   * @param startedAt    ISO-8601 UTC timestamp of the first event
   * @param lastEventAt  ISO-8601 UTC timestamp of the most recent event
   * @param lastLevel    most-severe level seen ({@code INFO / WARN / ERROR})
   */
  @Schema(description = "Summary metadata for a completed or in-progress import run.")
  public record RunSummaryIO(
    String runId,
    String startedAt,
    String lastEventAt,
    String lastLevel
  ) {
    /** Convert a run-metadata record to its REST representation. */
    public static RunSummaryIO from(ImportDiagnosticsLog.RunMeta meta) {
      return new RunSummaryIO(
        meta.runId(),
        toIso(meta.startedAt()),
        toIso(meta.lastEventAt()),
        meta.lastLevel()
      );
    }

    private static String toIso(Instant instant) {
      if (instant == null) return null;
      return DateTimeFormatter.ISO_INSTANT.format(instant.atZone(ZoneOffset.UTC));
    }
  }

  /**
   * Request body for {@code POST /v2/import/diagnostics/{runId}/events}.
   *
   * <p>Used by the external Python importer to push DO_CREATE / REF_ATTACH /
   * FILE_UPLOAD phase events that cannot be captured directly in the Java service.
   *
   * @param level       required: {@code INFO / WARN / ERROR}
   * @param phase       required: {@code WARMUP / DO_CREATE / REF_ATTACH /
   *                    FILE_UPLOAD / COMPLETE}
   * @param entityAppId optional: appId of the DO or container this event relates to
   * @param message     required: human-readable description
   * @param attributes  optional: structured context key-value pairs
   */
  @Schema(description = "Request body for posting a single diagnostic event from an external importer process.")
  public record IngestEventIO(
    String level,
    String phase,
    String entityAppId,
    String message,
    Map<String, Object> attributes
  ) {}

  /**
   * Request body for {@code POST /v2/import/diagnostics/{runId}/events/batch}.
   *
   * <p>Allows the Python importer to push multiple events in a single HTTP call,
   * reducing overhead during high-volume DO_CREATE / FILE_UPLOAD phases.
   *
   * @param events list of events to append; must not be empty
   */
  @Schema(description = "Request body for posting a batch of diagnostic events from an external importer process in a single call.")
  public record BatchIngestIO(
    List<IngestEventIO> events
  ) {}
}
