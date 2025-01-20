package de.dlr.shepard.context.references.basicreference.io;

import de.dlr.shepard.common.neo4j.io.BasicEntityIO;
import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@NoArgsConstructor
@Schema(name = "BasicReference")
public class BasicReferenceIO extends BasicEntityIO {

  @Schema(readOnly = true)
  private long dataObjectId;

  @Schema(readOnly = true)
  private String type;

  public BasicReferenceIO(BasicReference ref) {
    super(ref);
    this.type = ref.getType();
    this.dataObjectId = ref.getDataObject().getShepardId();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null) return false;
    if (!super.equals(o)) return false;
    if (this.getClass() != o.getClass()) return false;
    BasicReferenceIO other = (BasicReferenceIO) o;
    return (dataObjectId == other.dataObjectId && Objects.equals(type, other.type));
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((Long) dataObjectId).hashCode();
    result = prime * result + Objects.hashCode(type);
    return result;
  }
}
