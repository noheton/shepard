package de.dlr.shepard.v2.collectionwatchers.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.collectionwatchers.entities.CollectionWatcher;
import java.time.Instant;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * CW1 — wire shape for collection-watcher records.
 *
 * <p>Returned by GET /v2/collections/{collectionAppId}/watches and
 * GET /v2/collections/{collectionAppId}/watches/me.
 */
@Schema(description = "A user who is watching a collection for change notifications.")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectionWatcherIO(
  @Schema(description = "Stable application-level identifier for this collection-watcher record (UUID v7).", readOnly = true)
  String watcherAppId,
  @Schema(description = "Username of the watcher.")
  String username,
  @Schema(description = "Stable application-level identifier of the watched collection (UUID v7).")
  String collectionAppId,
  @Schema(description = "ISO 8601 UTC timestamp when this watch subscription was created.", example = "2026-06-01T00:00:00Z")
  String since
) {
  public static CollectionWatcherIO from(CollectionWatcher w) {
    return new CollectionWatcherIO(
      w.getAppId(),
      w.getUsername(),
      w.getCollectionAppId(),
      w.getSince() == null ? null : Instant.ofEpochMilli(w.getSince()).toString()
    );
  }
}
