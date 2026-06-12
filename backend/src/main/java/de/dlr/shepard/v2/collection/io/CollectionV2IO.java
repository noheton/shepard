package de.dlr.shepard.v2.collection.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.dlr.shepard.context.collection.entities.Collection;
import de.dlr.shepard.context.collection.io.CollectionIO;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * V2-only Collection response shape — suppresses the Neo4j internal {@code id}
 * (numeric node-id) from the wire shape.
 *
 * <p>{@link CollectionIO} is shared with the frozen v1
 * {@code /shepard/api/} surface and must not be modified. This thin sidecar
 * adds {@code @JsonIgnoreProperties({"id"})} so that
 * {@code GET /v2/collections} and {@code GET /v2/collections/{appId}}
 * never expose the substrate-internal id. All other {@link CollectionIO}
 * fields are unchanged.
 *
 * <p>Follows the same sidecar pattern as {@link de.dlr.shepard.v2.containers.io.BasicContainerV2IO}.
 */
@JsonIgnoreProperties({"id"})
@NoArgsConstructor
@Schema(name = "CollectionV2", description = "Collection response (v2) — id field suppressed; use appId.")
public class CollectionV2IO extends CollectionIO {

  public CollectionV2IO(Collection collection) {
    super(collection);
  }
}
