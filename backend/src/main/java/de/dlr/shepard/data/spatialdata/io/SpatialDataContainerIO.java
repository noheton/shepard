package de.dlr.shepard.data.spatialdata.io;

import de.dlr.shepard.common.neo4j.io.BasicContainerIO;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Getter
@Setter
@NoArgsConstructor
@Schema(name = "SpatialDataContainer")
public class SpatialDataContainerIO extends BasicContainerIO {

  public SpatialDataContainerIO(SpatialDataContainer container) {
    super(container);
  }

  public static SpatialDataContainerIO fromEntity(SpatialDataContainer entity) {
    return new SpatialDataContainerIO(entity);
  }

  public static List<SpatialDataContainerIO> fromEntities(List<SpatialDataContainer> entities) {
    return entities.stream().map(SpatialDataContainerIO::fromEntity).collect(Collectors.toList());
  }
}
