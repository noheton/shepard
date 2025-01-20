package de.dlr.shepard.context.collection.io;

import de.dlr.shepard.common.neo4j.io.AbstractDataObjectIO;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.collection.entities.Collection;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!super.equals(o)) return false;
    if (!(o instanceof CollectionIO)) return false;
    CollectionIO other = (CollectionIO) o;
    return (
      HasId.areEqualSets(dataObjectIds, other.dataObjectIds) && HasId.areEqualSets(incomingIds, other.incomingIds)
    );
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + HasId.hashcodeHelper(dataObjectIds);
    result = prime * result + HasId.hashcodeHelper(incomingIds);
    return result;
  }
}
