package de.dlr.shepard.v2.importer.io;

import de.dlr.shepard.v2.importer.entities.ImportLock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * IMP-LOCK — request / response shapes for lock REST endpoints.
 */
public final class ImportLockIO {

  private ImportLockIO() {}

  /**
   * Response body for {@code GET /v2/import/lock} and all mutating lock endpoints.
   *
   * @param lockId               public lock identifier (UUID v7)
   * @param status               one of RUNNING / COMPLETED / FAILED / CANCELLED / ABANDONED
   * @param startedAt            ISO-8601 UTC timestamp of lock acquisition
   * @param startedBy            username who acquired the lock
   * @param targetCollectionAppId appId of the collection being imported into
   * @param lastHeartbeatAt      ISO-8601 UTC timestamp of the last heartbeat, or {@code null}
   * @param errorMessage         set on FAILED; {@code null} otherwise
   */
  @Schema(description = "Current status of the collection-level import lock, including timing and error information.")
  public record LockStatusIO(
    String lockId,
    String status,
    String startedAt,
    String startedBy,
    String targetCollectionAppId,
    String lastHeartbeatAt,
    String errorMessage
  ) {
    /** Build a {@code LockStatusIO} from an {@link ImportLock} entity. */
    public static LockStatusIO from(ImportLock lock) {
      return new LockStatusIO(
        lock.getLockId(),
        lock.getStatus(),
        toIso(lock.getStartedAt()),
        lock.getStartedBy(),
        lock.getTargetCollectionAppId(),
        lock.getLastHeartbeatAt() != null ? toIso(lock.getLastHeartbeatAt()) : null,
        lock.getErrorMessage()
      );
    }

    private static String toIso(Long epochMs) {
      if (epochMs == null) return null;
      return DateTimeFormatter.ISO_INSTANT.format(
        Instant.ofEpochMilli(epochMs).atZone(ZoneOffset.UTC)
      );
    }
  }

  /**
   * Request body for {@code POST /v2/import/lock} (acquire).
   *
   * @param targetCollectionAppId appId of the collection being imported into; required
   */
  @Schema(description = "Request body for acquiring the collection import lock before starting an import run.")
  public record AcquireRequestIO(
    String targetCollectionAppId
  ) {}

  /**
   * Request body for {@code POST /v2/import/lock/{lockId}/abandon} (error termination).
   *
   * @param errorMessage human-readable error description; required
   */
  @Schema(description = "Request body for abandoning (error-terminating) the current import lock with an error message.")
  public record AbandonRequestIO(
    String errorMessage
  ) {}
}
