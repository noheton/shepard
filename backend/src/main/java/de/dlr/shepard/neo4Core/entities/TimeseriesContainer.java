package de.dlr.shepard.neo4Core.entities;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class TimeseriesContainer extends BasicContainer {

  private String database;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public TimeseriesContainer(long id) {
    super(id);
  }
}
