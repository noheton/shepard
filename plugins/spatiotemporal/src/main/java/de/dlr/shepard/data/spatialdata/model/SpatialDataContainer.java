package de.dlr.shepard.data.spatialdata.model;

import de.dlr.shepard.common.neo4j.entities.BasicContainer;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SpatialDataContainer extends BasicContainer {

  /**
   * For testing purposes only.
   *
   * @param id identifies the entity
   */
  public SpatialDataContainer(long id) {
    super(id);
  }
}
