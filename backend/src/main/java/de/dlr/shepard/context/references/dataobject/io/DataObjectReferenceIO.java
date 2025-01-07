package de.dlr.shepard.context.references.dataobject.io;

import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.dataobject.entities.DataObjectReference;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "DataObjectReference")
public class DataObjectReferenceIO extends BasicReferenceIO {

  @NotNull
  @Schema(required = true)
  private long referencedDataObjectId;

  @Schema(nullable = true)
  private String relationship;

  public DataObjectReferenceIO(DataObjectReference ref) {
    super(ref);
    this.referencedDataObjectId = ref.getReferencedDataObject() != null
      ? ref.getReferencedDataObject().getShepardId()
      : -1;
    this.relationship = ref.getRelationship();
  }
}
