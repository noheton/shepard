package de.dlr.shepard.context.snapshot.io;

import de.dlr.shepard.context.snapshot.entities.Snapshot;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * SNAPSHOT-LIST-1-REST — list-row shape for the global snapshot list
 * endpoint {@code GET /v2/snapshots}.
 *
 * <p>Trimmed to the fields a picker needs: appId, name, capture time,
 * parent collection's appId + name. {@code entryCount} is omitted from
 * the list row to keep the payload small at picker scale; callers who
 * want the full metadata follow up with {@code GET /v2/snapshots/{appId}}.
 */
@Schema(name = "SnapshotListItem")
public record SnapshotListItemIO(
  @Schema(readOnly = true, description = "Application-level UUID v7 identifier.")
  String appId,

  @Schema(description = "User-visible label, e.g. \"v1.0 — campaign close\".")
  String name,

  @Schema(readOnly = true, description = "ISO-8601 instant at which the snapshot was captured.")
  Instant createdAt,

  @Schema(readOnly = true, description = "appId of the root Collection.")
  String collectionAppId,

  @Schema(readOnly = true, description = "Name of the root Collection (snapshotted at list time, may drift from the live entity).")
  String collectionName
) {
  public static SnapshotListItemIO from(Snapshot s) {
    String collAppId = s.getCollection() != null ? s.getCollection().getAppId() : null;
    String collName = s.getCollection() != null ? s.getCollection().getName() : null;
    return new SnapshotListItemIO(
      s.getAppId(),
      s.getName(),
      Instant.ofEpochMilli(s.getSnapshotCapturedAtMs()),
      collAppId,
      collName
    );
  }
}
