package de.dlr.shepard.v2.watches.io;

import de.dlr.shepard.v2.watches.entities.Watch;

/** POST /v2/collections/{collectionAppId}/watched-containers body shape. */
public record CreateWatchIO(
  Watch.Kind containerKind,
  String containerAppId
) {}
