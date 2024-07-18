package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.BasicReference;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
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
}
