package de.dlr.shepard.context.collection.io;

import de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO;
import de.dlr.shepard.context.collection.entities.Collection;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "Collection")
public class CollectionIO extends AbstractDataObjectIO {

  @Schema(readOnly = true, required = true)
  private long[] dataObjectIds;

  @Schema(readOnly = true, required = true)
  private long[] incomingIds;

  public CollectionIO(Collection collection) {
    super(collection);
    this.dataObjectIds = extractShepardIds(collection.getDataObjects());
    this.incomingIds = extractShepardIds(collection.getIncoming());
  }
}
