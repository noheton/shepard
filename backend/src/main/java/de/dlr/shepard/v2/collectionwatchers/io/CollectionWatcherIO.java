package de.dlr.shepard.v2.collectionwatchers.io;

import com.fasterxml.jackson.annotation.JsonInclude;
import de.dlr.shepard.v2.collectionwatchers.entities.CollectionWatcher;

/**
 * CW1 — wire shape for collection-watcher records.
 *
 * <p>Returned by GET /v2/collections/{collectionAppId}/watches and
 * GET /v2/collections/{collectionAppId}/watches/me.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CollectionWatcherIO(
  String watcherAppId,
  String username,
  String collectionAppId,
  Long since
) {
  public static CollectionWatcherIO from(CollectionWatcher w) {
    return new CollectionWatcherIO(
      w.getAppId(),
      w.getUsername(),
      w.getCollectionAppId(),
      w.getSince()
    );
  }
}
