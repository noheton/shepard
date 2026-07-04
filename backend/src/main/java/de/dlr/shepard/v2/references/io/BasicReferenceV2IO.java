package de.dlr.shepard.v2.references.io;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * APISIMP-BASICREF-DATAOBJECTID — base IO class for all /v2/ reference surfaces.
 *
 * <p>Suppresses the two inherited numeric-id fields that must not cross the
 * /v2/ wire boundary:
 * <ul>
 *   <li>{@code id} — Neo4j internal node-id from
 *       {@link de.dlr.shepard.common.neo4j.io.BasicEntityIO}</li>
 *   <li>{@code dataObjectId} — numeric parent-DataObject FK from
 *       {@link BasicReferenceIO}</li>
 * </ul>
 * /v2/ callers address entities exclusively via {@code appId} (UUID v7).
 * The numeric identifiers remain as internal DAO handles but are hidden from
 * the wire shape.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@JsonIgnoreProperties({ "id", "dataObjectId" })
@Schema(name = "BasicReferenceV2", description = "Base v2 reference shape: id and dataObjectId suppressed from wire.")
public class BasicReferenceV2IO extends BasicReferenceIO {

  public BasicReferenceV2IO(BasicReference ref) {
    super(ref);
  }
}
