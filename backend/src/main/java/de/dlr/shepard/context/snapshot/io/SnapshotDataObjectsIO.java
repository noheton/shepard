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
 * round-trip to {@code GET /v2/snapshots/{appId}}) plus the filtered list
 * of DataObject {@code appId} strings that were captured in the snapshot.
 *
 * <p>Only entries whose {@code entityAppId} resolves to a live (non-deleted)
 * {@code :DataObject} node are included in {@code dataObjectAppIds}.
 * Collections, References, and soft-deleted entities are excluded.
 *
 * <p>Cross-references: {@code aidocs/41} §4.2 + §5; {@code aidocs/16} V2c.
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

  @Schema(description = "appIds of the DataObject nodes captured in this snapshot (subset of totalEntries; ordered by appId ascending).")
  List<String> dataObjectAppIds
) {
  /**
   * Constructs a {@code SnapshotDataObjectsIO} from a persisted {@link Snapshot}
   * entity and the filtered DataObject appId list.
   *
   * @param snapshot        the persisted snapshot.
   * @param dataObjectAppIds the filtered list of DataObject appIds.
   */
  public SnapshotDataObjectsIO(Snapshot snapshot, List<String> dataObjectAppIds) {
    this(
      snapshot.getAppId(),
      snapshot.getCollection() != null ? snapshot.getCollection().getAppId() : null,
      snapshot.getName(),
      Instant.ofEpochMilli(snapshot.getSnapshotCapturedAtMs()),
      snapshot.getEntryCount(),
      dataObjectAppIds
    );
  }
}
