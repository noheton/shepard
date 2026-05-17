package de.dlr.shepard.context.snapshot.io;

import com.fasterxml.jackson.annotation.JsonProperty;
import de.dlr.shepard.context.snapshot.entities.Snapshot;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2b — wire shape for a {@link Snapshot} in REST responses.
 *
 * <p>All fields are read-only (server-managed). The create body carries
 * only {@code name} and {@code description}; the full IO shape is returned
 * after creation (201) and on GET reads.
 *
 * <p>Cross-references: {@code aidocs/41} §5; {@code aidocs/16} V2b.
 */
@Schema(name = "Snapshot")
public record SnapshotIO(
  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(readOnly = true, description = "Application-level UUID v7 identifier of the snapshot.")
  String appId,

  @Schema(description = "User-visible label, e.g. \"v1.0 — campaign close\".")
  String name,

  @Schema(description = "Optional free-text description.")
  String description,

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(readOnly = true, description = "ISO-8601 instant at which the snapshot was captured.")
  Instant snapshotCapturedAt,

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(readOnly = true, description = "Username of the caller who created the snapshot.")
  String snapshotCreatedByUsername,

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(readOnly = true, description = "appId of the root Collection that was walked when the snapshot was created.")
  String collectionAppId,

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @Schema(readOnly = true, description = "Number of SnapshotEntry rows captured (equals the number of distinct VersionableEntity nodes in scope at snapshot time).")
  int entryCount
) {
  /**
   * Constructs a {@code SnapshotIO} from a persisted {@link Snapshot} entity.
   *
   * @param s the persisted snapshot.
   */
  public SnapshotIO(Snapshot s) {
    this(
      s.getAppId(),
      s.getName(),
      s.getDescription(),
      Instant.ofEpochMilli(s.getSnapshotCapturedAtMs()),
      s.getSnapshotCreatedByUsername(),
      s.getCollection() != null ? s.getCollection().getAppId() : null,
      s.getEntryCount()
    );
  }
}
