package de.dlr.shepard.context.snapshot.io;

import de.dlr.shepard.context.snapshot.entities.SnapshotEntry;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2b — wire shape for a single {@link SnapshotEntry} in the snapshot manifest.
 *
 * <p>The manifest endpoint ({@code GET /v2/snapshots/{appId}/manifest}) returns
 * a flat array of these records — one per entity captured in the snapshot.
 * Consumers can diff two manifests to determine what changed between snapshots.
 *
 * <p>Cross-references: {@code aidocs/41} §4.2; {@code aidocs/16} V2b.
 */
@Schema(name = "SnapshotEntry")
public record SnapshotEntryIO(
  @Schema(description = "appId of the VersionableEntity pinned by this entry.")
  String entityAppId,

  @Schema(description = "Revision counter the entity held at snapshot-capture time.")
  long revision
) {
  /**
   * Constructs a {@code SnapshotEntryIO} from a persisted {@link SnapshotEntry}.
   *
   * @param e the persisted entry.
   */
  public SnapshotEntryIO(SnapshotEntry e) {
    this(e.getEntityAppId(), e.getRevision());
  }
}
