package de.dlr.shepard.context.references.dataobject.io;

import de.dlr.shepard.context.references.basicreference.io.BasicReferenceIO;
import de.dlr.shepard.context.references.dataobject.entities.CollectionReference;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "CollectionReference")
public class CollectionReferenceIO extends BasicReferenceIO {

  @NotNull
  @Schema(required = true)
  private long referencedCollectionId;

  @Schema(nullable = true)
  private String relationship;

  public CollectionReferenceIO(CollectionReference ref) {
    super(ref);
    this.referencedCollectionId = ref.getReferencedCollection() != null
      ? ref.getReferencedCollection().getShepardId()
      : -1;
    this.relationship = ref.getRelationship();
  }
}
