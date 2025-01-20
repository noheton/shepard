package de.dlr.shepard.common.neo4j.io;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import de.dlr.shepard.common.neo4j.entities.ContainerType;
import java.util.Arrays;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@Schema(name = "BasicContainer")
public class BasicContainerIO extends BasicEntityIO {

  private ContainerType type;

  public BasicContainerIO(BasicContainer container) {
    super(container);
    type = Arrays.stream(ContainerType.values())
      .filter(containerType -> containerType.getTypeName().equals(container.getClass().getSimpleName()))
      .findFirst()
      .orElse(ContainerType.BASIC);
  }
}
