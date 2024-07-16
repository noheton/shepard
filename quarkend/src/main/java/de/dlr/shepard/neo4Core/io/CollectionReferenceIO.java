package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.CollectionReference;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "CollectionReference")
public class CollectionReferenceIO extends BasicReferenceIO {

  @NotNull
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
