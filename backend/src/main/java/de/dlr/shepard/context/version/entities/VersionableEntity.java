package de.dlr.shepard.context.version.entities;

import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class VersionableEntity extends BasicEntity {

  @Index
  private Long shepardId;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  protected VersionableEntity(long id) {
    super(id);
  }

  @Override
  public long getNumericId() {
    return getShepardId();
  }
}
