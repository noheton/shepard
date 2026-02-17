package de.dlr.shepard.common.neo4j.entities;

import de.dlr.shepard.common.util.Constants;
import de.dlr.shepard.common.util.HasId;
import de.dlr.shepard.context.semantic.HasAnnotation;
import de.dlr.shepard.context.semantic.entities.SemanticAnnotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.neo4j.ogm.annotation.Relationship;

@Getter
@Setter
@ToString
@NoArgsConstructor
public class BasicEntity extends AbstractEntity implements HasAnnotation {

  protected String name;

  @ToString.Exclude
  @Relationship(type = Constants.HAS_ANNOTATION)
  private List<SemanticAnnotation> annotations = new ArrayList<>();

  /**
   * For testing purposes only
   *
   * @param id identifies the entity
   */
  public BasicEntity(long id) {
    super(id);
  }

  public void addAnnotation(SemanticAnnotation annotation) {
    annotations.add(annotation);
  }

  public long getNumericId() {
    return getId();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + Objects.hash(id, deleted, createdAt, updatedAt, name);
    result = prime * result + HasId.hashcodeHelper(createdBy);
    result = prime * result + HasId.hashcodeHelper(updatedBy);
    result = prime * result + HasId.hashcodeHelper(annotations);
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof BasicEntity)) return false;
    BasicEntity other = (BasicEntity) obj;
    return (
      Objects.equals(id, other.id) &&
      deleted == other.deleted &&
      Objects.equals(createdAt, other.createdAt) &&
      Objects.equals(updatedAt, other.updatedAt) &&
      HasId.equalsHelper(createdBy, other.createdBy) &&
      HasId.equalsHelper(updatedBy, other.updatedBy) &&
      Objects.equals(name, other.name) &&
      HasId.areEqualSetsByUniqueId(annotations, other.annotations)
    );
  }
}
