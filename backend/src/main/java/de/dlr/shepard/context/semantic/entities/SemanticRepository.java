package de.dlr.shepard.context.semantic.entities;

import de.dlr.shepard.common.neo4j.entities.BasicEntity;
import de.dlr.shepard.context.semantic.SemanticRepositoryType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class SemanticRepository extends BasicEntity {

  private SemanticRepositoryType type;

  private String endpoint;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public SemanticRepository(long id) {
    super(id);
  }
}
