package de.dlr.shepard.context.references.uri.entities;

import de.dlr.shepard.context.references.basicreference.entities.BasicReference;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.neo4j.ogm.annotation.NodeEntity;

@NodeEntity
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class URIReference extends BasicReference {

  private String uri;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public URIReference(long id) {
    super(id);
  }
}
