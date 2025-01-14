package de.dlr.shepard.context.version.entities;

import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.common.util.Constants;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.Index;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class VersionableEntity extends BasicEntity {

  @Index
  private Long shepardId;

  @Relationship(type = Constants.HAS_VERSION)
  protected Version version;

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
