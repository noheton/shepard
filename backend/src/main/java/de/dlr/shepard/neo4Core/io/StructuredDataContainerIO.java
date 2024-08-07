package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.StructuredDataContainer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "StructuredDataContainer")
public class StructuredDataContainerIO extends BasicContainerIO {

  @Schema(readOnly = true)
  private String oid;

  public StructuredDataContainerIO(StructuredDataContainer container) {
    super(container);
    this.oid = container.getMongoId();
  }
}
