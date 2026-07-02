package de.dlr.shepard.context.snapshot.io;

import de.dlr.shepard.context.snapshot.entities.Snapshot;
import java.time.Instant;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2c — wire shape for the snapshot-pinned DataObject list endpoint.
 *
 * <p>Returned by
 * {@code GET /v2/collections/{collectionAppId}/snapshots/{snapshotAppId}/data-objects}.
 * Contains snapshot metadata (for caller convenience — avoids a second
 * round-trip to {@code GET /v2/snapshots/{appId}}) plus a paginated slice of
 * the DataObject {@code appId} strings that were captured in the snapshot.
 *
 * <p>Only entries whose {@code entityAppId} resolves to a live (non-deleted)
 * {@code :DataObject} node are included in {@code dataObjectAppIds}.
 * Collections, References, and soft-deleted entities are excluded.
 *
 * <p>Pagination: use {@code ?page=} (0-based) and {@code ?pageSize=} (default 500,
 * max 2000) to page through large snapshots. {@code totalDataObjects} always
 * reflects the full count before slicing.
 *
 * <p>Cross-references: {@code aidocs/41} §4.2 + §5; {@code aidocs/16} V2c +
 * APISIMP-SNAPSHOT-PINNED-DO-UNCAPPED.
 */
@Schema(name = "SnapshotDataObjects")
public record SnapshotDataObjectsIO(
  @Schema(description = "appId of the snapshot.")
  String snapshotAppId,

  @Schema(description = "appId of the Collection the snapshot was taken of.")
  String collectionAppId,

  @Schema(description = "User-visible label of the snapshot.")
  String snapshotName,

  @Schema(description = "ISO-8601 instant at which the snapshot was captured.")
  Instant snapshotCapturedAt,

  @Schema(description = "Total number of VersionableEntity entries captured in this snapshot (includes Collections, DataObjects, References, etc.).")
  int totalEntries,

  @Schema(description = "Total number of DataObject appIds in this snapshot across all pages.")
  int totalDataObjects,

  @Schema(description = "Current page index (0-based).")
  int page,

  @Schema(description = "Maximum number of DataObject appIds returned per page.")
  int pageSize,

  @Schema(description = "DataObject appIds in the requested page window (subset of all captured DataObjects; ordered by appId ascending).")
  List<String> dataObjectAppIds
) {
  /**
   * Constructs a paginated {@code SnapshotDataObjectsIO} from a persisted
   * {@link Snapshot} entity and the already-sliced DataObject appId list.
   *
   * @param snapshot             the persisted snapshot.
   * @param dataObjectAppIds     the sliced (paged) DataObject appId list.
   * @param totalDataObjects     total count before slicing.
   * @param page                 0-based page index.
   * @param pageSize             page size used for slicing.
   */
  public SnapshotDataObjectsIO(
      Snapshot snapshot,
      List<String> dataObjectAppIds,
      int totalDataObjects,
      int page,
      int pageSize) {
    this(
      snapshot.getAppId(),
      snapshot.getCollection() != null ? snapshot.getCollection().getAppId() : null,
      snapshot.getName(),
      Instant.ofEpochMilli(snapshot.getSnapshotCapturedAtMs()),
      snapshot.getEntryCount(),
      totalDataObjects,
      page,
      pageSize,
      dataObjectAppIds
    );
  }
}
