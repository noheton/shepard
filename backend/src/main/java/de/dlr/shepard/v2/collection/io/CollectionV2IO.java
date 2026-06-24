package de.dlr.shepard.v2.collection.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.CollectionIO;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2-only Collection response shape — suppresses numeric Neo4j ids from the wire.
 *
 * <p>{@link CollectionIO} is shared with the frozen v1 {@code /shepard/api/} surface
 * and must not be modified. This thin sidecar extends {@code @JsonIgnoreProperties}
 * so that {@code GET /v2/collections} and {@code GET /v2/collections/{appId}}
 * never expose substrate-internal ids or deprecated numeric id arrays. Callers
 * use {@code appId} (UUID v7) and {@code defaultFileContainerAppId} instead.
 *
 * <p>APISIMP-COLL-IO-NUMERIC-ID-LEAK — suppressed in this class:
 * {@code dataObjectIds} (long[]), {@code incomingIds} (long[]),
 * {@code defaultFileContainerId} (Long, deprecated — use {@code defaultFileContainerAppId}).
 *
 * <p>Follows the same sidecar pattern as {@link de.dlr.shepard.v2.containers.io.BasicContainerV2IO}.
 */
@JsonIgnoreProperties({
  // Neo4j internal node id — always suppressed on v2 (use appId).
  "id",
  // APISIMP-COLL-IO-NUMERIC-ID-LEAK: inherited numeric Neo4j id arrays from CollectionIO.
  "dataObjectIds", "incomingIds",
  // Deprecated Long — replaced by defaultFileContainerAppId (UUID v7).
  "defaultFileContainerId"
})
@NoArgsConstructor
@Schema(name = "CollectionV2", description = "Collection response (v2) — numeric id fields suppressed; use appId.")
public class CollectionV2IO extends CollectionIO {

  public CollectionV2IO(Collection collection) {
    super(collection);
  }
}
