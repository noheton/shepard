package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.Collection;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "Collection")
public class CollectionIO extends AbstractDataObjectIO {

  @Schema(readOnly = true)
  private long[] dataObjectIds;

  @Schema(readOnly = true)
  private long[] incomingIds;

  public CollectionIO(Collection collection) {
    super(collection);
    this.dataObjectIds = extractShepardIds(collection.getDataObjects());
    this.incomingIds = extractShepardIds(collection.getIncoming());
  }
}
