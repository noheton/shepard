package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.FileContainer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "FileContainer")
public class FileContainerIO extends BasicContainerIO {

  @Schema(readOnly = true)
  private String oid;

  public FileContainerIO(FileContainer container) {
    super(container);
    this.oid = container.getMongoId();
  }
}
