package de.dlr.shepard.neo4Core.entities;

import java.util.Map;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.Properties;

@Data
@ToString(callSuper = true)
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public abstract class AbstractDataObject extends VersionableEntity {

  private String description;

  @ToString.Exclude
  @Properties(delimiter = "||")
  private Map<String, String> attributes;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  protected AbstractDataObject(long id) {
    super(id);
  }
}
