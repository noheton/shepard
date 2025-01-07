package de.dlr.shepard.data.semantic.entities;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import java.util.Objects;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.neo4j.ogm.annotation.GeneratedValue;
import org.neo4j.ogm.annotation.Id;
import org.neo4j.ogm.annotation.NodeEntity;
import org.neo4j.ogm.annotation.Relationship;

@NodeEntity
@Data
@NoArgsConstructor
public class SemanticAnnotation implements HasId {

  @Id
  @GeneratedValue
  private Long id;

  private String name;

  private String propertyIRI;

  private String valueIRI;

  @ToString.Exclude
  @Relationship(type = Constants.PROPERTY_REPOSITORY)
  private SemanticRepository propertyRepository;

  @ToString.Exclude
  @Relationship(type = Constants.VALUE_REPOSITORY)
  private SemanticRepository valueRepository;

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public SemanticAnnotation(long id) {
    this.id = id;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    var result = Objects.hash(id, name, propertyIRI, valueIRI);
    result = prime * result + HasId.hashcodeHelper(propertyRepository);
    result = prime * result + HasId.hashcodeHelper(valueRepository);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (!(obj instanceof SemanticAnnotation)) return false;
    SemanticAnnotation other = (SemanticAnnotation) obj;
    return (
      Objects.equals(id, other.id) &&
      Objects.equals(name, other.name) &&
      Objects.equals(propertyIRI, other.propertyIRI) &&
      Objects.equals(valueIRI, other.valueIRI) &&
      HasId.equalsHelper(propertyRepository, other.propertyRepository) &&
      HasId.equalsHelper(valueRepository, other.valueRepository)
    );
  }

  @Override
  public String getUniqueId() {
    return id.toString();
  }
}
