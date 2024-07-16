package de.dlr.shepard.neo4Core.io;

import de.dlr.shepard.neo4Core.entities.BasicContainer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "BasicContainer")
public class BasicContainerIO extends BasicEntityIO {

  public BasicContainerIO(BasicContainer container) {
    super(container);
  }
}
