package de.dlr.shepard.v2.watches.io;

import de.dlr.shepard.v2.watches.entities.Watch;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Request body for {@code POST /v2/collections/{collectionAppId}/watched-containers}. */
@Schema(description = "Request body for subscribing to change notifications from a specific container within a collection.")
public record CreateWatchIO(
  Watch.Kind containerKind,
  String containerAppId
) {}
